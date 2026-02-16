package com.github.sysmoon.wholphin.ui.setup

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.data.JellyfinServerDao
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.services.HomeScreenSectionsService
import com.github.sysmoon.wholphin.services.ImageUrlService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.PluginSettingsApplicator
import com.github.sysmoon.wholphin.services.PluginSettingsService
import com.github.sysmoon.wholphin.services.SetupDestination
import com.github.sysmoon.wholphin.services.SetupNavigationManager
import com.github.sysmoon.wholphin.ui.SlimItemFields
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import coil3.imageLoader
import coil3.request.ImageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import timber.log.Timber
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@HiltViewModel(assistedFactory = SwitchUserViewModel.Factory::class)
class SwitchUserViewModel
    @AssistedInject
    constructor(
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val imageUrlService: ImageUrlService,
        private val pluginSettingsService: PluginSettingsService,
        private val pluginSettingsApplicator: PluginSettingsApplicator,
        private val homeScreenSectionsService: HomeScreenSectionsService,
        @ApplicationContext private val context: Context,
        @Assisted val server: JellyfinServer,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(server: JellyfinServer): SwitchUserViewModel
        }

        val serverQuickConnect = MutableLiveData<Boolean>(false)

        val users = MutableLiveData<List<JellyfinUserAndImage>>(listOf())
        val quickConnectState = MutableLiveData<QuickConnectResult?>(null)

        private var quickConnectJob: Job? = null

        val switchUserState = MutableLiveData<LoadingState>(LoadingState.Pending)

        val loginAttempts = MutableLiveData(0)

        /** True until preloadAllUserBackdrops() has finished; UI shows full-screen spinner while true. */
        val isPreloading = MutableLiveData(true)

        /** Current background art for the user-select screen (backdrop + logo of one library item). */
        val userSelectBackground = MutableLiveData<UserSelectBackground?>(null)

        /** Id of user currently selected for background; UI uses this to show new user's image immediately (no crossfade delay). */
        val selectedUserForBackgroundId = MutableLiveData<UUID?>(null)

        /** User currently selected for background (used for rotation from pool). */
        private var selectedUserForBackground: JellyfinUser? = null
        /** Round-robin index into the current user's backdrop pool. */
        private var currentRotationIndex: Int = 0
        private var backgroundRotationJob: Job? = null

        /** Pre-loaded backdrop+logo lists per user (key = user.id). Only populated during preload. */
        private var userBackdropPools: Map<UUID, List<UserSelectBackground>> = emptyMap()

        fun clearSwitchUserState() {
            switchUserState.value = LoadingState.Pending
        }

        fun resetAttempts() {
            loginAttempts.value = 0
        }

        init {
            init()
        }

        fun init() {
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                serverRepository.switchServerOrUser()
            }
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                users.setValueOnMain(listOf())
                try {
                    val serverUsers = getUsers()
                    withContext(Dispatchers.Main) {
                        users.setValueOnMain(serverUsers)
                    }
                    // Timeout so user list appears even if backdrop fetch hangs (e.g. slow/unreachable server)
                    withTimeoutOrNull(15.seconds) {
                        preloadAllUserBackdrops(serverUsers)
                    }
                    ?: Timber.w("User backdrop preload timed out; showing user list anyway")
                } catch (ex: Exception) {
                    if (ex is CancellationException) throw ex
                    Timber.e(ex, "Error loading users or preloading backdrops")
                } finally {
                    withContext(Dispatchers.Main) {
                        isPreloading.value = false
                    }
                }
            }

            viewModelScope.launchIO {
                try {
                    jellyfin
                        .createApi(
                            server.url,
                            httpClientOptions =
                                HttpClientOptions(
                                    requestTimeout = 6.seconds,
                                    connectTimeout = 6.seconds,
                                    socketTimeout = 6.seconds,
                                ),
                        ).systemApi
                        .getPublicSystemInfo()
                    val quickConnect by
                        jellyfin
                            .createApi(server.url)
                            .quickConnectApi
                            .getQuickConnectEnabled()
                    withContext(Dispatchers.Main) {
                        serverQuickConnect.value = quickConnect
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error checking quick connect for server ${server.url}")
                    withContext(Dispatchers.Main) {
                        serverQuickConnect.value = false
                    }
                }
            }
        }

        fun switchUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                try {
                    val current = serverRepository.changeUser(server, user)
                    if (current != null) {
                        withContext(Dispatchers.Main) {
                            setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                        }
                        homeScreenSectionsService.clearLayoutCache()
                        val settings = pluginSettingsService.fetchSettings(current.user.id)
                        if (settings != null) {
                            pluginSettingsApplicator.apply(settings, current.user)
                        }
                        homeScreenSectionsService.getLayoutRows(current.user.id)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error switching user")
                    setError("Error switching user", ex)
                }
            }
        }

        fun login(
            server: JellyfinServer,
            username: String,
            password: String,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                try {
                    val api = jellyfin.createApi(baseUrl = server.url)
                    val authenticationResult by api.userApi.authenticateUserByName(
                        username = username,
                        password = password,
                    )
                    val current = serverRepository.changeUser(server.url, authenticationResult)
                    if (current != null) {
                        withContext(Dispatchers.Main) {
                            setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                        }
                        homeScreenSectionsService.clearLayoutCache()
                        val settings = pluginSettingsService.fetchSettings(current.user.id)
                        if (settings != null) {
                            pluginSettingsApplicator.apply(settings, current.user)
                        }
                        homeScreenSectionsService.getLayoutRows(current.user.id)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error logging in user")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        withContext(Dispatchers.Main) {
                            switchUserState.value =
                                LoadingState.Error("Invalid username or password")
                        }
                    } else {
                        setError("Error during login", ex)
                    }
                }
            }
        }

        fun initiateQuickConnect(server: JellyfinServer) {
            quickConnectJob?.cancel()
            quickConnectJob =
                viewModelScope.launchIO {
                    try {
                        val api = jellyfin.createApi(server.url)
                        var state =
                            api
                                .quickConnectApi
                                .initiateQuickConnect()
                                .content

                        withContext(Dispatchers.Main) {
                            quickConnectState.value = state
                        }

                        while (!state.authenticated) {
                            delay(5_000L)
                            state =
                                api.quickConnectApi
                                    .getQuickConnectState(
                                        secret = state.secret,
                                    ).content
                            withContext(Dispatchers.Main) {
                                quickConnectState.value = state
                            }
                        }
                        val authenticationResult by api.userApi.authenticateWithQuickConnect(
                            QuickConnectDto(secret = state.secret),
                        )
                        val current = serverRepository.changeUser(server.url, authenticationResult)
                        if (current != null) {
                            withContext(Dispatchers.Main) {
                                setupNavigationManager.navigateTo(
                                    SetupDestination.AppContent(current),
                                )
                            }
                            homeScreenSectionsService.clearLayoutCache()
                            val settings = pluginSettingsService.fetchSettings(current.user.id)
                            if (settings != null) {
                                pluginSettingsApplicator.apply(settings, current.user)
                            }
                            homeScreenSectionsService.getLayoutRows(current.user.id)
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during quick connect")
                        if (ex is InvalidStatusException && ex.status == 401) {
                            withContext(Dispatchers.Main) {
                                quickConnectState.value = null
                                serverQuickConnect.value = false
                            }
                        }
                        setError("Error with Quick Connect", ex)
                    }
                }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            quickConnectState.value = null
        }

        fun removeUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                serverRepository.removeUser(user)
                val serverUsers = getUsers()
                withContext(Dispatchers.Main) {
                    users.value = serverUsers
                }
            }
        }

        /**
         * Pre-loads backdrop+logo URLs for all users into Coil and fills [userBackdropPools].
         * Uses cached image item IDs when available for instant display, then refreshes in background.
         */
        private suspend fun preloadAllUserBackdrops(serverUsers: List<JellyfinUserAndImage>) {
            val cachedIds = withContext(Dispatchers.IO) { loadBackdropPoolCache() }
            val currentUserIds = serverUsers.map { it.user.id }.toSet()
            val cacheHit =
                cachedIds != null &&
                    currentUserIds.isNotEmpty() &&
                    currentUserIds.all { cachedIds.containsKey(it) && !cachedIds[it].isNullOrEmpty() }
            if (cacheHit && cachedIds != null) {
                val pools = mutableMapOf<UUID, List<UserSelectBackground>>()
                for (entry in serverUsers) {
                    val user = entry.user
                    if (user.accessToken.isNullOrBlank()) continue
                    val imageItemIds = cachedIds[user.id].orEmpty()
                    val list = buildBackdropsFromImageItemIds(user, imageItemIds).shuffled()
                    pools[user.id] = list
                }
                userBackdropPools = pools
                withContext(Dispatchers.Main) {
                    isPreloading.value = false
                    val firstUser = serverUsers.firstOrNull()?.user
                    val firstPool = firstUser?.let { userBackdropPools[it.id].orEmpty() }
                    val firstBackdrop = firstPool?.firstOrNull()
                    userSelectBackground.value = firstBackdrop
                    selectedUserForBackground = firstUser
                    selectedUserForBackgroundId.value = firstUser?.id
                    currentRotationIndex = 0
                    if (!firstPool.isNullOrEmpty()) startRotationJob()
                }
                viewModelScope.launchIO { refreshBackdropPoolsInBackground(serverUsers) }
                return
            }
            val pools = mutableMapOf<UUID, List<UserSelectBackground>>()
            val poolsForCache = mutableMapOf<UUID, List<UUID>>()
            for (entry in serverUsers) {
                val user = entry.user
                if (user.accessToken.isNullOrBlank()) continue
                try {
                    val (list, imageItemIds) = fetchBackdropsForUserWithIds(user, limit = 10)
                    pools[user.id] = list.shuffled()
                    poolsForCache[user.id] = imageItemIds
                } catch (ex: Exception) {
                    if (ex is CancellationException) throw ex
                    Timber.w(ex, "preload backdrops failed for user ${user.id}")
                    pools[user.id] = emptyList()
                    poolsForCache[user.id] = emptyList()
                }
            }
            userBackdropPools = pools
            withContext(Dispatchers.IO) { saveBackdropPoolCache(poolsForCache) }
            withContext(Dispatchers.Main) {
                isPreloading.value = false
                val firstUser = serverUsers.firstOrNull()?.user
                val firstPool = firstUser?.let { userBackdropPools[it.id].orEmpty() }
                val firstBackdrop = firstPool?.firstOrNull()
                userSelectBackground.value = firstBackdrop
                selectedUserForBackground = firstUser
                selectedUserForBackgroundId.value = firstUser?.id
                currentRotationIndex = 0
                if (!firstPool.isNullOrEmpty()) startRotationJob()
            }
        }

        /** Builds backdrop+logo URLs from cached image item IDs using current token; no API or prefetch. */
        private fun buildBackdropsFromImageItemIds(user: JellyfinUser, imageItemIds: List<UUID>): List<UserSelectBackground> {
            val token = user.accessToken ?: return emptyList()
            if (token.isBlank() || imageItemIds.isEmpty()) return emptyList()
            val api = jellyfin.createApi(baseUrl = server.url, accessToken = token)
            val baseUrl = api.baseUrl?.trimEnd('/').orEmpty()
            val result = mutableListOf<UserSelectBackground>()
            for (imageItemId in imageItemIds) {
                val backdropUrl =
                    toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.BACKDROP))
                        ?: toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.PRIMARY))
                        ?: toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.THUMB))
                if (backdropUrl == null) continue
                val logoUrl =
                    toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.LOGO))
                        ?: toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.PRIMARY))
                result.add(UserSelectBackground(backdropUrl = backdropUrl, logoUrl = logoUrl))
            }
            return result
        }

        /** Fetches fresh pools from API, prefetches into Coil, updates [userBackdropPools] and cache. */
        private suspend fun refreshBackdropPoolsInBackground(serverUsers: List<JellyfinUserAndImage>) {
            val pools = mutableMapOf<UUID, List<UserSelectBackground>>()
            val poolsForCache = mutableMapOf<UUID, List<UUID>>()
            for (entry in serverUsers) {
                val user = entry.user
                if (user.accessToken.isNullOrBlank()) continue
                try {
                    val (list, imageItemIds) = fetchBackdropsForUserWithIds(user, limit = 10)
                    pools[user.id] = list.shuffled()
                    poolsForCache[user.id] = imageItemIds
                } catch (ex: Exception) {
                    if (ex is CancellationException) throw ex
                    Timber.w(ex, "background refresh backdrops failed for user ${user.id}")
                }
            }
            withContext(Dispatchers.Main) {
                userBackdropPools = userBackdropPools + pools
            }
            saveBackdropPoolCache(poolsForCache)
        }

        private val backdropCacheFile: File
            get() = File(context.cacheDir, "user_select_backdrops_${server.id}.json")

        private fun loadBackdropPoolCache(): Map<UUID, List<UUID>>? {
            return try {
                if (!backdropCacheFile.isFile) return null
                val json = backdropCacheFile.readText()
                val parsed = Json.decodeFromString<CachedBackdropPools>(json)
                parsed.pools.entries.mapNotNull { (k, v) ->
                    runCatching { UUID.fromString(k) }.getOrNull()?.let { uuid ->
                        uuid to v.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                    }
                }.toMap().takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load backdrop pool cache")
                null
            }
        }

        private fun saveBackdropPoolCache(pools: Map<UUID, List<UUID>>) {
            try {
                val existing = loadBackdropPoolCache()?.toMutableMap() ?: mutableMapOf()
                existing.putAll(pools)
                val dto = CachedBackdropPools(
                    existing.mapKeys { it.key.toString() }.mapValues { it.value.map { id -> id.toString() } },
                )
                backdropCacheFile.writeText(Json.encodeToString(CachedBackdropPools.serializer(), dto))
            } catch (e: Exception) {
                Timber.w(e, "Failed to save backdrop pool cache")
            }
        }

        /**
         * Fetches up to [limit] backdrop+logo pairs for [user], prefetches into Coil.
         * Returns (list of backdrops, list of image item IDs for cache).
         */
        private suspend fun fetchBackdropsForUserWithIds(user: JellyfinUser, limit: Int): Pair<List<UserSelectBackground>, List<UUID>> {
            val token = user.accessToken ?: return emptyList<UserSelectBackground>() to emptyList<UUID>()
            if (token.isBlank()) return emptyList<UserSelectBackground>() to emptyList<UUID>()
            val api = jellyfin.createApi(baseUrl = server.url, accessToken = token)
            val request = GetLatestMediaRequest(
                fields = SlimItemFields,
                imageTypeLimit = 1,
                parentId = null,
                groupItems = true,
                limit = limit,
                isPlayed = null,
            )
            val items = api.userLibraryApi.getLatestMedia(request).content
            val baseUrl = api.baseUrl?.trimEnd('/').orEmpty()
            val result = mutableListOf<UserSelectBackground>()
            val imageItemIds = mutableListOf<UUID>()
            for (item in items) {
                // Episodes often lack backdrop/logo; use parent series for imagery
                val imageItemId: UUID =
                    if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
                        item.seriesId!!
                    } else {
                        item.id
                    }
                val backdropUrl =
                    toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.BACKDROP))
                        ?: toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.PRIMARY))
                        ?: toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.THUMB))
                if (backdropUrl == null) continue
                val logoUrl =
                    toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.LOGO))
                        ?: toAbsoluteWithAuth(baseUrl, token, api.imageApi.getItemImageUrl(imageItemId, ImageType.PRIMARY))
                prefetchImages(backdropUrl, logoUrl)
                result.add(UserSelectBackground(backdropUrl = backdropUrl, logoUrl = logoUrl))
                imageItemIds.add(imageItemId)
            }
            return result to imageItemIds
        }

        /** Fetches backdrops for [user]; used by on-demand load when pool is empty. */
        private suspend fun fetchBackdropsForUser(user: JellyfinUser, limit: Int): List<UserSelectBackground> =
            fetchBackdropsForUserWithIds(user, limit).first

        /**
         * Called when the backdrop or logo image failed to load. Advances to the next item in the
         * current user's pool; if none left, clears background (gradient only).
         */
        fun onBackgroundLoadFailed() {
            val user = selectedUserForBackground ?: run {
                userSelectBackground.postValue(null)
                return
            }
            val pool = userBackdropPools[user.id].orEmpty()
            if (pool.isEmpty()) {
                userSelectBackground.postValue(null)
                return
            }
            currentRotationIndex = (currentRotationIndex + 1) % pool.size
            userSelectBackground.postValue(pool[currentRotationIndex])
        }

        /**
         * Selects background from the pre-loaded pool for [user].
         * Sets [selectedUserForBackground] and shows first item from pool; if empty and user has token, fetches once and updates pool.
         * (Re)starts the 10s rotation job when pool is non-empty.
         */
        fun loadBackgroundForUser(user: JellyfinUser?) {
            selectedUserForBackground = user
            selectedUserForBackgroundId.value = user?.id
            backgroundRotationJob?.cancel()
            if (user == null) {
                userSelectBackground.value = null
                return
            }
            var pool = userBackdropPools[user.id].orEmpty()
            if (pool.isNotEmpty()) {
                currentRotationIndex = 0
                userSelectBackground.value = pool[0]
                startRotationJob()
                return
            }
            // Pool empty: show gradient; if user has token, fetch backdrops once and update pool
            userSelectBackground.value = null
            if (!user.accessToken.isNullOrBlank()) {
                viewModelScope.launchIO {
                    try {
                        val list = fetchBackdropsForUser(user, limit = 10)
                        withContext(Dispatchers.Main) {
                            if (selectedUserForBackground != user) return@withContext
                            val existing = userBackdropPools[user.id].orEmpty()
                            // Don't overwrite a preloaded (larger) pool with a smaller on-demand result
                            if (existing.isNotEmpty() && list.size <= existing.size) return@withContext
                            if (list.isEmpty()) return@withContext
                            val shuffled = list.shuffled()
                            userBackdropPools = userBackdropPools + (user.id to shuffled)
                            currentRotationIndex = 0
                            userSelectBackground.value = shuffled[0]
                            startRotationJob()
                        }
                    } catch (ex: Exception) {
                        if (ex is CancellationException) throw ex
                        Timber.w(ex, "on-demand backdrop fetch failed for user ${user.id}")
                    }
                }
            }
        }

        /** Starts a job that every 10s sets [userSelectBackground] to the next item in the current user's pool. */
        private fun startRotationJob() {
            backgroundRotationJob?.cancel()
            backgroundRotationJob = viewModelScope.launchIO {
                while (true) {
                    delay(10_000L)
                    val user = selectedUserForBackground ?: continue
                    val pool = userBackdropPools[user.id].orEmpty()
                    if (pool.isEmpty()) continue
                    withContext(Dispatchers.Main) {
                        currentRotationIndex = (currentRotationIndex + 1) % pool.size
                        userSelectBackground.value = pool[currentRotationIndex]
                    }
                }
            }
        }

        /** Loads backdrop and logo into Coil cache so the next transition shows immediately. */
        private suspend fun prefetchImages(backdropUrl: String, logoUrl: String?) {
            val loader = context.imageLoader
            coroutineScope {
                val backdropDeferred = async {
                    loader.execute(
                        ImageRequest.Builder(context).data(backdropUrl).build(),
                    )
                }
                val logoDeferred =
                    if (logoUrl != null) {
                        async {
                            loader.execute(
                                ImageRequest.Builder(context).data(logoUrl).build(),
                            )
                        }
                    } else null
                backdropDeferred.await()
                logoDeferred?.await()
            }
        }

        /** Build absolute image URL with ApiKey so Coil can load (app's client may not have this user's token). */
        private fun toAbsoluteWithAuth(baseUrl: String, token: String?, url: String?): String? {
            if (url == null) return null
            val absolute =
                if (url.startsWith("http")) url
                else "$baseUrl${if (url.startsWith("/")) url else "/$url"}"
            if (token.isNullOrBlank() || absolute.contains("ApiKey=")) return absolute
            val encoded = java.net.URLEncoder.encode(token, "UTF-8").replace("+", "%20")
            val sep = if (absolute.contains("?")) "&" else "?"
            return "$absolute$sep" + "ApiKey=$encoded"
        }

        /** Appends maxWidth/maxHeight to an image URL so the server returns higher resolution (crisp on high-DPI). */
        private fun appendImageSizeParams(url: String?, maxWidth: Int, maxHeight: Int): String? {
            if (url == null) return null
            val sep = if (url.contains("?")) "&" else "?"
            return "$url${sep}maxWidth=$maxWidth&maxHeight=$maxHeight"
        }

        /** Requested size for user avatar images (2x 72dp at ~3x density ≈ 432px; use 512 for crisp). */
        private val userAvatarImageSize = 512

        private suspend fun getUsers(): List<JellyfinUserAndImage> {
            val api = jellyfin.createApi(server.url)
            val baseUrl = api.baseUrl?.trimEnd('/').orEmpty()
            return serverDao
                .getServer(server.id)
                ?.users
                ?.sortedBy { it.name }
                ?.map { user ->
                    val rawUrl = api.imageApi.getUserImageUrl(user.id)
                    val withSize = appendImageSizeParams(rawUrl, userAvatarImageSize, userAvatarImageSize)
                    val imageUrl = toAbsoluteWithAuth(baseUrl, user.accessToken, withSize)
                    JellyfinUserAndImage(user, imageUrl)
                }
                .orEmpty()
        }

        private suspend fun setError(
            msg: String? = null,
            ex: Exception? = null,
        ) = withContext(Dispatchers.Main) {
            loginAttempts.value = (loginAttempts.value ?: 0) + 1
            switchUserState.value = LoadingState.Error(msg, ex)
        }
    }

data class JellyfinUserAndImage(
    val user: JellyfinUser,
    val imageUrl: String?,
)

/** Backdrop and logo URLs for the user-select screen background art. */
data class UserSelectBackground(
    val backdropUrl: String?,
    val logoUrl: String?,
)

/** Persisted cache of backdrop image item IDs per user (key = userId string, value = list of image item UUID strings). */
@Serializable
data class CachedBackdropPools(val pools: Map<String, List<String>> = emptyMap())
