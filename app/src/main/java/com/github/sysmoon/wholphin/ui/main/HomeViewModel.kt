package com.github.sysmoon.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.NavDrawerItemRepository
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.DatePlayedService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.HomeScreenSectionsService
import com.github.sysmoon.wholphin.services.LatestNextUpService
import com.github.sysmoon.wholphin.services.NavigationManager
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
    ) : ViewModel() {
        val loadingState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val refreshState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val watchingRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())
        val latestRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())

        private lateinit var preferences: UserPreferences

        init {
            datePlayedService.invalidateAll()
        }

        fun init(preferences: UserPreferences): Job {
            val reload = loadingState.value != LoadingState.Success
            if (reload) {
                loadingState.value = LoadingState.Loading
            }
            refreshState.value = LoadingState.Loading
            this.preferences = preferences
            val prefs = preferences.appPreferences.homePagePreferences
            val limit = prefs.maxItemsPerRow
            return viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loadingState,
                        "Error loading home page",
                    ),
            ) {
                Timber.d("init HomeViewModel")
                if (reload) {
                    backdropService.clearBackdrop()
                }

                serverRepository.currentUserDto.value?.let { userDto ->
                    // Try to fetch custom sections from the plugin first (if enabled)
                    val enableCustomHomeRows = preferences.appPreferences.interfacePreferences.enableCustomHomeRows
                    Timber.d("HomeViewModel: Custom home rows enabled=$enableCustomHomeRows")
                    
                    val useNativeContinueNext =
                        preferences.appPreferences.interfacePreferences.customHomeRowsUseNativeContinueNext
                    Timber.d("HomeViewModel: Custom home rows native Continue/Next enabled=$useNativeContinueNext")
                    val customSections = if (enableCustomHomeRows) {
                        homeScreenSectionsService.getCustomSections(userDto.id)
                    } else {
                        null
                    }
                    
                    if (customSections != null) {
                        // Plugin sections are available, use them
                        // The plugin provides all sections in order, including continue watching and next up
                        Timber.i("HomeViewModel: Using custom home screen sections from plugin (${customSections.size} sections)")

                        val baseRows =
                            if (useNativeContinueNext) {
                                // Replace ContinueWatchingNextUp section with Wholphin's native interleaving logic
                                val continueNextIndex =
                                    customSections.indexOfFirst { it.sectionId == "ContinueWatchingNextUp" }
                                if (continueNextIndex >= 0) {
                                    Timber.d("HomeViewModel: Replacing ContinueWatchingNextUp with native combined logic")
                                    val resume = latestNextUpService.getResume(userDto.id, limit, true)
                                    val nextUp =
                                        latestNextUpService.getNextUp(
                                            userDto.id,
                                            limit,
                                            prefs.enableRewatchingNextUp,
                                            false,
                                        )
                                    val combined = latestNextUpService.buildCombined(resume, nextUp).take(limit)
                                    customSections.mapIndexed { idx, sectionRow ->
                                        if (idx == continueNextIndex && sectionRow.row is HomeRowLoadingState.Success) {
                                            sectionRow.row.let { existing ->
                                                HomeRowLoadingState.Success(
                                                    title = existing.title,
                                                    items = combined,
                                                )
                                            }
                                        } else {
                                            sectionRow.row
                                        }
                                    }
                                } else {
                                    customSections.map { it.row }
                                }
                            } else {
                                customSections.map { it.row }
                            }

                        val finalRows = baseRows

                        withContext(Dispatchers.Main) {
                            // Plugin sections replace the entire home screen
                            this@HomeViewModel.watchingRows.value = emptyList()
                            if (reload) {
                                this@HomeViewModel.latestRows.value = finalRows
                            }
                            loadingState.value = LoadingState.Success
                        }
                        refreshState.setValueOnMain(LoadingState.Success)
                    } else {
                        // Plugin not available, use default behavior
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
                    }
                }
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
