package com.github.sysmoon.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.R
import androidx.datastore.core.DataStore
import com.github.sysmoon.wholphin.data.NavDrawerItemRepository
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.preferences.updateInterfacePreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.DatePlayedService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.HomeScreenSectionsService
import com.github.sysmoon.wholphin.services.LatestNextUpService
import com.github.sysmoon.wholphin.services.WholphinRow
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.PluginSettingsApplicator
import com.github.sysmoon.wholphin.services.PluginSettingsService
import com.github.sysmoon.wholphin.ui.data.RowColumn
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.nav.ServerNavDrawerItem
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.HomeRowLoadingState
import com.github.sysmoon.wholphin.util.LoadingExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val navDrawerItemRepository: NavDrawerItemRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val latestNextUpService: LatestNextUpService,
        private val backdropService: BackdropService,
        private val homeScreenSectionsService: HomeScreenSectionsService,
        private val appPreferencesDataStore: DataStore<AppPreferences>,
        private val pluginSettingsService: PluginSettingsService,
        private val pluginSettingsApplicator: PluginSettingsApplicator,
    ) : ViewModel() {
        val loadingState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val refreshState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val watchingRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())
        val latestRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())

        /** When non-null, home content should restore focus to this position (e.g. after back from details). Consumed once applied. */
        val savedHomePositionToRestore = MutableLiveData<RowColumn?>(null)

        private lateinit var preferences: UserPreferences
        /** Cached plugin row layout; on subsequent visits we only refresh content for these rows. */
        private var cachedPluginLayout: List<WholphinRow>? = null
        private var cachedPluginLayoutUserId: UUID? = null

        init {
            datePlayedService.invalidateAll()
            navigationManager.onReturnedToHome = {
                if (::preferences.isInitialized) {
                    viewModelScope.launch {
                        refreshWhenReturnedToHome()
                    }
                }
            }
        }

        fun init(preferences: UserPreferences): Job {
            val reload = loadingState.value != LoadingState.Success
            return init(preferences, backgroundRefresh = !reload)
        }

        private fun init(preferences: UserPreferences, backgroundRefresh: Boolean): Job {
            val reload = !backgroundRefresh && loadingState.value != LoadingState.Success
            if (reload) {
                loadingState.value = LoadingState.Loading
            }
            refreshState.value = LoadingState.Loading
            this.preferences = preferences
            val prefs = preferences.appPreferences.homePagePreferences
            val limit = prefs.maxItemsPerRow
            val exceptionHandler =
                LoadingExceptionHandler(
                    if (backgroundRefresh) refreshState else loadingState,
                    "Error loading home page",
                )
            return viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                Timber.d("init HomeViewModel backgroundRefresh=$backgroundRefresh")
                if (reload) {
                    backdropService.clearBackdrop()
                }

                serverRepository.currentUserDto.value?.let { userDto ->
                    val jellyfinUser = serverRepository.currentUser.value
                    // Fetch plugin settings and companion layout in parallel (both may be cached from preload).
                    val (settings, layout) = coroutineScope {
                        val settingsDeferred = async {
                            pluginSettingsService.fetchSettings(userDto.id)
                        }
                        val layoutDeferred = async {
                            if (backgroundRefresh && cachedPluginLayout != null && cachedPluginLayoutUserId == userDto.id) {
                                cachedPluginLayout
                            } else {
                                homeScreenSectionsService.getLayoutRows(userDto.id)
                            }
                        }
                        Pair(settingsDeferred.await(), layoutDeferred.await())
                    }
                    if (settings != null && jellyfinUser != null) {
                        pluginSettingsApplicator.apply(settings, jellyfinUser)
                    }
                    // Try custom sections from companion plugin; if none, use default home rows.
                    val useCachedLayout =
                        backgroundRefresh &&
                            cachedPluginLayout != null &&
                            cachedPluginLayoutUserId == userDto.id
                    val customRows =
                        if (useCachedLayout) {
                            val existingRows = withContext(Dispatchers.Main) { latestRows.value }.orEmpty()
                            homeScreenSectionsService.buildRowsFromLayoutWithPartialRefresh(
                                layoutRows = cachedPluginLayout!!,
                                userId = userDto.id,
                                itemsPerRow = limit,
                                enableRewatchingNextUp = prefs.enableRewatchingNextUp,
                                existingRows = existingRows,
                            ).takeIf { it.isNotEmpty() }
                        } else if (layout != null) {
                            cachedPluginLayout = layout
                            cachedPluginLayoutUserId = userDto.id
                            homeScreenSectionsService.buildRowsFromLayout(
                                layoutRows = layout,
                                userId = userDto.id,
                                itemsPerRow = limit,
                                enableRewatchingNextUp = prefs.enableRewatchingNextUp,
                            ).takeIf { it.isNotEmpty() }
                        } else {
                            cachedPluginLayout = null
                            cachedPluginLayoutUserId = null
                            null
                        }

                    if (customRows != null) {
                        // Plugin rows are available, use them
                        Timber.i("HomeViewModel: Using custom home rows from plugin (%s rows)%s", customRows.size, if (useCachedLayout) " (content refresh)" else " (full load)")
                        appPreferencesDataStore.updateData {
                            it.updateInterfacePreferences { homeUsesPluginRows = true }
                        }
                        withContext(Dispatchers.Main) {
                            // Plugin rows replace the entire home screen
                            this@HomeViewModel.watchingRows.value = emptyList()
                            this@HomeViewModel.latestRows.value = customRows
                            if (reload) {
                                loadingState.value = LoadingState.Success
                            }
                        }
                        refreshState.setValueOnMain(LoadingState.Success)
                        preloadLibraryTabs(userDto.id, limit, prefs.enableRewatchingNextUp)
                    } else {
                        // Plugin not available, use default behavior
                        cachedPluginLayout = null
                        cachedPluginLayoutUserId = null
                        appPreferencesDataStore.updateData {
                            it.updateInterfacePreferences { homeUsesPluginRows = false }
                        }
                        Timber.d("HomeViewModel: Plugin not available, using default home screen sections")
                        val includedIds =
                            navDrawerItemRepository
                                .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                                .filter { it is ServerNavDrawerItem }
                                .map { (it as ServerNavDrawerItem).itemId }
                        val resume = latestNextUpService.getResume(userDto.id, limit, true)
                        val nextUp =
                            latestNextUpService.getNextUp(
                                userDto.id,
                                limit,
                                prefs.enableRewatchingNextUp,
                                false,
                            )
                        val watching =
                            buildList {
                                if (prefs.combineContinueNext) {
                                    val items = latestNextUpService.buildCombined(resume, nextUp)
                                    add(
                                        HomeRowLoadingState.Success(
                                            title = context.getString(R.string.continue_watching),
                                            items = items,
                                        ),
                                    )
                                } else {
                                    if (resume.isNotEmpty()) {
                                        add(
                                            HomeRowLoadingState.Success(
                                                title = context.getString(R.string.continue_watching),
                                                items = resume,
                                            ),
                                        )
                                    }
                                    if (nextUp.isNotEmpty()) {
                                        add(
                                            HomeRowLoadingState.Success(
                                                title = context.getString(R.string.next_up),
                                                items = nextUp,
                                            ),
                                        )
                                    }
                                }
                            }

                        val latest = latestNextUpService.getLatest(userDto, limit, includedIds)
                        val loadedLatest = latestNextUpService.loadLatest(latest)

                        withContext(Dispatchers.Main) {
                            this@HomeViewModel.watchingRows.value = watching
                            this@HomeViewModel.latestRows.value = loadedLatest
                            loadingState.value = LoadingState.Success
                        }
                        refreshState.setValueOnMain(LoadingState.Success)
                        preloadLibraryTabs(userDto.id, limit, prefs.enableRewatchingNextUp)
                    }
                }
            }
        }

        /** Preloads library tab data (Movies, Shows, etc.) in background so tab switch shows content without spinner. */
        private fun preloadLibraryTabs(userId: UUID, itemsPerRow: Int, enableRewatchingNextUp: Boolean) {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                val all = navDrawerItemRepository.getNavDrawerItems()
                val pinned = navDrawerItemRepository.getFilteredNavDrawerItems(all)
                    .filterIsInstance<ServerNavDrawerItem>()
                for (item in pinned) {
                    try {
                        val layout = homeScreenSectionsService.getLibraryLayoutRows(item.itemId, userId) ?: continue
                        val rows = homeScreenSectionsService.buildRowsFromLayout(
                            layout,
                            userId,
                            itemsPerRow,
                            enableRewatchingNextUp,
                            parentId = item.itemId,
                            collectionType = item.type,
                        )
                        if (rows.isNotEmpty()) {
                            homeScreenSectionsService.putCachedLibraryRows(userId, item.itemId, rows)
                            Timber.d("HomeViewModel: Preloaded library tab for %s (%s rows)", item.name, rows.size)
                        }
                    } catch (e: Exception) {
                        Timber.d(e, "HomeViewModel: Preload library tab failed for %s", item.itemId)
                    }
                }
            }
        }

        /**
         * Refreshes Up Next / Continue Watching (and latest rows) when the user returns to the home screen.
         * Called from [NavigationManager.onReturnedToHome]; runs as a background refresh (no full-screen loading).
         */
        private fun refreshWhenReturnedToHome() {
            if (::preferences.isInitialized) {
                init(preferences, backgroundRefresh = true)
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(Dispatchers.Main) {
                init(preferences)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(Dispatchers.Main) {
                init(preferences)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        /** Call before navigating to a details screen so we can restore this position when the user presses back. */
        fun saveHomePositionForRestore(position: RowColumn) {
            savedHomePositionToRestore.value = position
        }

        fun clearSavedHomePositionToRestore() {
            savedHomePositionToRestore.value = null
        }
    }

val supportedLatestCollectionTypes =
    setOf(
        CollectionType.MOVIES,
        CollectionType.TVSHOWS,
        CollectionType.HOMEVIDEOS,
        // Exclude Live TV because a recording folder view will be used instead
        null, // Recordings & mixed collection types
    )

data class LatestData(
    val title: String,
    val request: GetLatestMediaRequest,
)
