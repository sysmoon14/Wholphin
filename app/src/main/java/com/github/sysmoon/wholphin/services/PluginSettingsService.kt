package com.github.sysmoon.wholphin.services

import com.github.sysmoon.wholphin.services.hilt.AuthOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches merged settings (global + user) from the Wholphin Companion plugin.
 * Returns null if the plugin is not available or the endpoint returns an error.
 */
@Singleton
class PluginSettingsService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Fetches settings from GET /Wholphin/Settings.
         * @param userId Optional Jellyfin user ID; if null, plugin may resolve from request context.
         * @return Parsed response or null on non-200, 404, or parse error.
         */
        suspend fun fetchSettings(userId: UUID?): PluginSettingsResponse? =
            withContext(Dispatchers.IO) {
                try {
                    val baseUrl = api.baseUrl
                    val accessToken = api.accessToken
                    if (baseUrl.isNullOrBlank() || accessToken.isNullOrBlank()) {
                        Timber.d(
                            "PluginSettingsService: No base URL or access token, skipping fetch",
                        )
                        return@withContext null
                    }
                    val base = baseUrl.toHttpUrlOrNull()
                    if (base == null) {
                        Timber.w("PluginSettingsService: Invalid baseUrl=%s", baseUrl)
                        return@withContext null
                    }
                    val url =
                        base
                            .newBuilder()
                            .addPathSegment("Wholphin")
                            .addPathSegment("Settings")
                            .apply {
                                if (userId != null) {
                                    addQueryParameter("userId", userId.toString().replace("-", ""))
                                }
                            }
                            .build()
                    Timber.d("PluginSettingsService: Fetching settings from %s", url)
                    val authHeader =
                        AuthorizationHeaderBuilder.buildHeader(
                            clientName = clientInfo.name,
                            clientVersion = clientInfo.version,
                            deviceId = deviceInfo.id,
                            deviceName = deviceInfo.name,
                            accessToken = accessToken,
                        )
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .addHeader("Authorization", authHeader)
                            .addHeader("X-Emby-Token", accessToken)
                            .get()
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.d(
                                "PluginSettingsService: Settings response status=%s",
                                response.code,
                            )
                            return@withContext null
                        }
                        val body = response.body.string()
                        if (body.isBlank()) {
                            Timber.w("PluginSettingsService: Empty settings response")
                            return@withContext null
                        }
                        val parsed = json.decodeFromString(PluginSettingsResponse.serializer(), body)
                        Timber.d(
                            "PluginSettingsService: Parsed settings globalKeys=%s userPresent=%s",
                            parsed.global.keys.toList(),
                            parsed.user != null,
                        )
                        parsed
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "PluginSettingsService: Error fetching settings")
                    null
                }
            }
    }

/** Plugin may send "Global"/"User" (PascalCase) or "global"/"user" (lowercase). */
@Serializable
data class PluginSettingsResponse(
    @SerialName("Global") @JsonNames("global") val global: Map<String, String> = emptyMap(),
    @SerialName("User") @JsonNames("user") val user: UserSettingsResponse? = null,
)

/** Accepts both snake_case and camelCase JSON keys (plugin may send either). */
@Serializable
data class UserSettingsResponse(
    @JsonNames("maxHomepageItems") val max_homepage_items: String? = null,
    @JsonNames("rewatchNextUp") val rewatch_next_up: String? = null,
    @JsonNames("combineContinueNext") val combine_continue_next: String? = null,
    @JsonNames("backdropDisplay") val backdrop_display: String? = null,
    @JsonNames("playThemeMusic") val play_theme_music: String? = null,
    @JsonNames("rememberSelectedTab") val remember_selected_tab: String? = null,
    @JsonNames("appTheme") val app_theme: String? = null,
    @JsonNames("showClock") val show_clock: String? = null,
    @JsonNames("combinedSearchResults") val combined_search_results: String? = null,
    @JsonNames("navDrawerSwitchOnFocus") val nav_drawer_switch_on_focus: String? = null,
    @JsonNames("skipForwardPreference") val skip_forward_preference: String? = null,
    @JsonNames("skipBackPreference") val skip_back_preference: String? = null,
    @JsonNames("skipBackOnResumePreference") val skip_back_on_resume_preference: String? = null,
    @JsonNames("hideControllerTimeout") val hide_controller_timeout: String? = null,
    @JsonNames("seekBarSteps") val seek_bar_steps: String? = null,
    @JsonNames("playbackDebugInfo") val playback_debug_info: String? = null,
    @JsonNames("globalContentScale") val global_content_scale: String? = null,
    @JsonNames("oneClickPause") val one_click_pause: String? = null,
    @JsonNames("autoPlayNext") val auto_play_next: String? = null,
    @JsonNames("autoPlayNextDelay") val auto_play_next_delay: String? = null,
    @JsonNames("showNextUpWhen") val show_next_up_when: String? = null,
    @JsonNames("passOutProtection") val pass_out_protection: String? = null,
    @JsonNames("skipIntroBehavior") val skip_intro_behavior: String? = null,
    @JsonNames("skipOutroBehavior") val skip_outro_behavior: String? = null,
    @JsonNames("skipCommercialsBehavior") val skip_commercials_behavior: String? = null,
    @JsonNames("skipPreviewsBehavior") val skip_previews_behavior: String? = null,
    @JsonNames("skipRecapBehavior") val skip_recap_behavior: String? = null,
    @JsonNames("showDetails") val show_details: String? = null,
    @JsonNames("favoriteChannelsAtBeginning") val favorite_channels_at_beginning: String? = null,
    @JsonNames("sortChannelsRecentlyWatched") val sort_channels_recently_watched: String? = null,
    @JsonNames("colorCodePrograms") val color_code_programs: String? = null,
    @JsonNames("seerrCredentials") val seerr_credentials: SeerrCredentialsDto? = null,
    @JsonNames("navDrawerItems") val nav_drawer_items: NavDrawerItemsDto? = null,
    @JsonNames("hideSettingsCog") val hide_settings_cog: String? = null,
    @JsonNames("allowSettingsOverride") val allow_settings_override: String? = null,
)

@Serializable
data class SeerrCredentialsDto(
    val url: String = "",
    val authMethod: String = "",
    val username: String? = null,
    val passwordOrApiKey: String? = null,
)

@Serializable
data class NavDrawerItemsDto(
    val items: List<NavDrawerItemDto> = emptyList(),
)

@Serializable
data class NavDrawerItemDto(
    val itemId: String = "",
    val type: String = "PINNED",
)
