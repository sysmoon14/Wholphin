package com.github.sysmoon.wholphin.services.hilt

import android.content.Context
import androidx.datastore.core.DataStore
import com.github.sysmoon.wholphin.BuildConfig
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.preferences.updateInterfacePreferences
import com.github.sysmoon.wholphin.services.SeerrApi
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.RememberTabManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun clientInfo(
        @ApplicationContext context: Context,
    ): ClientInfo =
        ClientInfo(
            name = context.getString(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
        )

    @Provides
    @Singleton
    fun deviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo = androidDevice(context)

    @StandardOkHttpClient
    @Provides
    @Singleton
    fun okHttpClient() =
        OkHttpClient
            .Builder()
            .apply {
                // TODO user agent, timeouts, logging, etc
            }.build()

    @AuthOkHttpClient
    @Provides
    @Singleton
    fun authOkHttpClient(
        serverRepository: ServerRepository,
        @StandardOkHttpClient okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ) = okHttpClient
        .newBuilder()
        .addInterceptor {
            val request = it.request()
            // When URL has ApiKey= (e.g. user-select backdrop with per-user token), authenticate using that token.
            // Strip any existing Authorization (current user's token) and send the URL's token in the header instead,
            // so the server gets the correct user (e.g. username-only / Quick Connect users may require header auth).
            val urlString = request.url.toString()
            val hasApiKeyInUrl = urlString.contains("ApiKey=", ignoreCase = true)
            val tokenFromUrl: String? =
                if (hasApiKeyInUrl) {
                    request.url.queryParameterNames
                        .firstOrNull { it.equals("ApiKey", ignoreCase = true) }
                        ?.let { request.url.queryParameter(it) }
                } else null
            val newRequest =
                if (tokenFromUrl != null) {
                    request
                        .newBuilder()
                        .removeHeader("Authorization")
                        .addHeader(
                            "Authorization",
                            AuthorizationHeaderBuilder.buildHeader(
                                clientName = clientInfo.name,
                                clientVersion = clientInfo.version,
                                deviceId = deviceInfo.id,
                                deviceName = deviceInfo.name,
                                accessToken = tokenFromUrl,
                            ),
                        )
                        .build()
                } else if (hasApiKeyInUrl) {
                    request.newBuilder().removeHeader("Authorization").build()
                } else {
                    serverRepository.currentUser.value?.accessToken?.let { token ->
                        request
                            .newBuilder()
                            .addHeader(
                                "Authorization",
                                AuthorizationHeaderBuilder.buildHeader(
                                    clientName = clientInfo.name,
                                    clientVersion = clientInfo.version,
                                    deviceId = deviceInfo.id,
                                    deviceName = deviceInfo.name,
                                    accessToken = token,
                                ),
                            ).build()
                    } ?: request
                }
            // Debug: log exact request for image auth (e.g. second-user 401). Redact token values in URL.
            val logUrl = urlString.replace(Regex("([?&]ApiKey=)[^&]*", RegexOption.IGNORE_CASE), "$1***")
            Timber.d(
                "[AuthInterceptor] %s %s | hasApiKeyInUrl=%s tokenFromUrl=%s action=%s | outgoing hasAuthHeader=%s",
                request.method,
                logUrl,
                hasApiKeyInUrl,
                tokenFromUrl != null,
                when {
                    tokenFromUrl != null -> "useUrlTokenInHeader"
                    hasApiKeyInUrl -> "stripAuth"
                    newRequest.header("Authorization") != null -> "addAuth"
                    else -> "noAuth"
                },
                newRequest.header("Authorization") != null,
            )
            it.proceed(newRequest)
        }.build()

    @Provides
    @Singleton
    fun okHttpFactory(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) = OkHttpFactory(okHttpClient)

    @Provides
    @Singleton
    fun jellyfin(
        okHttpFactory: OkHttpFactory,
        @ApplicationContext context: Context,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ): Jellyfin =
        createJellyfin {
            this.context = context
            this.clientInfo = clientInfo
            this.deviceInfo = deviceInfo
            apiClientFactory = okHttpFactory
            socketConnectionFactory = okHttpFactory
            minimumServerVersion = Jellyfin.minimumVersion
        }

    @Provides
    @Singleton
    fun apiClient(jellyfin: Jellyfin) = jellyfin.createApi()

    /**
     * Implementation of [RememberTabManager] which remembers by server, user, & item
     */
    @Provides
    @Singleton
    fun rememberTabManager(
        serverRepository: ServerRepository,
        appPreference: DataStore<AppPreferences>,
        @IoCoroutineScope scope: CoroutineScope,
    ) = object : RememberTabManager {
        fun key(itemId: String) = "${serverRepository.currentServer.value?.id}_${serverRepository.currentUser.value?.id}_$itemId"

        override fun getRememberedTab(
            preferences: UserPreferences,
            itemId: String,
            defaultTab: Int,
        ): Int {
            if (preferences.appPreferences.interfacePreferences.rememberSelectedTab) {
                return preferences.appPreferences.interfacePreferences
                    .getRememberedTabsOrDefault(key(itemId), defaultTab)
            } else {
                return defaultTab
            }
        }

        override fun saveRememberedTab(
            preferences: UserPreferences,
            itemId: String,
            tabIndex: Int,
        ) {
            if (preferences.appPreferences.interfacePreferences.rememberSelectedTab) {
                scope.launch(ExceptionHandler()) {
                    appPreference.updateData {
                        preferences.appPreferences.updateInterfacePreferences {
                            putRememberedTabs(key(itemId), tabIndex)
                        }
                    }
                }
            }
        }
    }

    @Provides
    @Singleton
    @IoCoroutineScope
    fun ioCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun seerrApi(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) = SeerrApi(okHttpClient)
}
