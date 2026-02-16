package com.github.sysmoon.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.HomeScreenSectionsService
import com.github.sysmoon.wholphin.services.LatestNextUpService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.ui.SlimItemFields
import com.github.sysmoon.wholphin.ui.data.RowColumn
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.ui.toBaseItems
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.GetItemsRequestHandler
import com.github.sysmoon.wholphin.util.GetNextUpRequestHandler
import com.github.sysmoon.wholphin.util.GetResumeItemsRequestHandler
import com.github.sysmoon.wholphin.util.GetSuggestionsRequestHandler
import com.github.sysmoon.wholphin.util.HomeRowLoadingState
import com.github.sysmoon.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedTvShowViewModel.Factory::class)
class RecommendedTvShowViewModel
        @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
        private val lastestNextUpService: LatestNextUpService,
        private val homeScreenSectionsService: HomeScreenSectionsService,
        @Assisted val parentId: UUID,
        navigationManager: NavigationManager,
        favoriteWatchManager: FavoriteWatchManager,
        backdropService: BackdropService,
    ) : RecommendedViewModel(context, navigationManager, favoriteWatchManager, backdropService) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedTvShowViewModel
        }

        override val rows =
            MutableStateFlow<List<HomeRowLoadingState>>(
                rowTitles.keys.map {
                    HomeRowLoadingState.Pending(
                        context.getString(it),
                    )
                },
            )

        override fun init() {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                val userId = serverRepository.currentUser.value?.id ?: return@launch
                val cached = homeScreenSectionsService.getCachedLibraryRows(userId, parentId)
                if (!cached.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        rows.value = cached
                        loading.value = LoadingState.Success
                    }
                    return@launch
                }
                val preferences =
                    preferencesDataStore.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                val combineNextUp = preferences.homePagePreferences.combineContinueNext
                val itemsPerRow = preferences.homePagePreferences.maxItemsPerRow
                val enableRewatchingNextUp = preferences.homePagePreferences.enableRewatchingNextUp
                try {
                    val libraryLayoutRows = homeScreenSectionsService.getLibraryLayoutRows(parentId, userId)
                    if (!libraryLayoutRows.isNullOrEmpty()) {
                        val builtRows =
                            homeScreenSectionsService.buildRowsFromLayout(
                                libraryLayoutRows,
                                userId,
                                itemsPerRow,
                                enableRewatchingNextUp,
                                parentId = parentId,
                                collectionType = CollectionType.TVSHOWS,
                            )
                        withContext(Dispatchers.Main) {
                            rows.value = builtRows
                            loading.value = LoadingState.Success
                        }
                        return@launch
                    }
                    // Fallback: use hardcoded rows when no library layout is configured
                    val resumeItemsDeferred =
                        viewModelScope.async(Dispatchers.IO) {
                            val resumeItemsRequest =
                                GetResumeItemsRequest(
                                    userId = userId,
                                    parentId = parentId,
                                    fields = SlimItemFields,
                                    includeItemTypes = listOf(BaseItemKind.EPISODE),
                                    enableUserData = true,
                                    startIndex = 0,
                                    limit = itemsPerRow,
                                    enableTotalRecordCount = false,
                                )
                            GetResumeItemsRequestHandler
                                .execute(api, resumeItemsRequest)
                                .toBaseItems(api, true)
                        }

                    val nextUpItemsDeferred =
                        viewModelScope.async(Dispatchers.IO) {
                            val nextUpRequest =
                                GetNextUpRequest(
                                    userId = userId,
                                    fields = SlimItemFields,
                                    imageTypeLimit = 1,
                                    parentId = parentId,
                                    limit = itemsPerRow,
                                    enableResumable = false,
                                    enableUserData = true,
                                    enableRewatching = preferences.homePagePreferences.enableRewatchingNextUp,
                                )

                            GetNextUpRequestHandler
                                .execute(api, nextUpRequest)
                                .toBaseItems(api, true)
                        }
                    val resumeItems = resumeItemsDeferred.await()
                    val nextUpItems = nextUpItemsDeferred.await()
                    if (combineNextUp) {
                        val combined =
                            lastestNextUpService.buildCombined(
                                resumeItems,
                                nextUpItems,
                            )
                        update(
                            R.string.continue_watching,
                            HomeRowLoadingState.Success(
                                context.getString(R.string.continue_watching),
                                combined,
                            ),
                        )
                        update(
                            R.string.next_up,
                            HomeRowLoadingState.Success(
                                context.getString(R.string.next_up),
                                listOf(),
                            ),
                        )
                    } else {
                        update(
                            R.string.continue_watching,
                            HomeRowLoadingState.Success(
                                context.getString(R.string.continue_watching),
                                resumeItems,
                            ),
                        )
                        update(
                            R.string.next_up,
                            HomeRowLoadingState.Success(
                                context.getString(R.string.next_up),
                                nextUpItems,
                            ),
                        )
                    }

                    if (resumeItems.isNotEmpty() || nextUpItems.isNotEmpty()) {
                        loading.setValueOnMain(LoadingState.Success)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching tv recommendations")
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                    }
                }

                update(R.string.recently_released) {
                    val recentlyReleasedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            recursive = true,
                            enableUserData = true,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )

                    GetItemsRequestHandler
                        .execute(api, recentlyReleasedRequest)
                        .toBaseItems(api, true)
                }

                update(R.string.recently_added) {
                    val recentlyAddedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            recursive = true,
                            enableUserData = true,
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )

                    GetItemsRequestHandler
                        .execute(api, recentlyAddedRequest)
                        .toBaseItems(api, true)
                }

                update(R.string.suggestions) {
                    val suggestionsRequest =
                        GetSuggestionsRequest(
                            userId = serverRepository.currentUser.value?.id,
                            type = listOf(BaseItemKind.SERIES),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )

                    GetSuggestionsRequestHandler
                        .execute(api, suggestionsRequest)
                        .toBaseItems(api, true)
                }

                update(R.string.top_unwatched) {
                    val unwatchedTopRatedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            recursive = true,
                            enableUserData = true,
                            isPlayed = false,
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetItemsRequestHandler
                        .execute(api, unwatchedTopRatedRequest)
                        .toBaseItems(api, true)
                }

                if (loading.value == LoadingState.Loading || loading.value == LoadingState.Pending) {
                    loading.setValueOnMain(LoadingState.Success)
                }
            }
        }

        override fun update(
            @StringRes title: Int,
            row: HomeRowLoadingState,
        ) {
            rows.update { current ->
                current.toMutableList().apply { set(rowTitles[title]!!, row) }
            }
        }

        companion object {
            private val rowTitles =
                listOf(
                    R.string.continue_watching,
                    R.string.next_up,
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }

/**
 * The "recommended" tab of a TV show library
 */
@Composable
fun RecommendedTvShow(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    topRowFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    resetPositionOnEnter: Boolean = false,
    consumeDownToTopRow: Boolean = false,
    dropEmptyRows: Boolean = false,
    skipContentFocusUntilMillis: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    viewModel: RecommendedTvShowViewModel =
        hiltViewModel<RecommendedTvShowViewModel, RecommendedTvShowViewModel.Factory>(
            creationCallback = { it.create(parentId) },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
        topRowFocusRequester = topRowFocusRequester,
        resetPositionOnEnter = resetPositionOnEnter,
        consumeDownToTopRow = consumeDownToTopRow,
        dropEmptyRows = dropEmptyRows,
        skipContentFocusUntilMillis = skipContentFocusUntilMillis,
        wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
        navHasFocus = navHasFocus,
    )
}
