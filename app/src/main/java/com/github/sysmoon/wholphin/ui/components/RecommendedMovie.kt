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
import com.github.sysmoon.wholphin.preferences.AppPreference
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.ui.SlimItemFields
import com.github.sysmoon.wholphin.ui.data.RowColumn
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.ui.toBaseItems
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.GetItemsRequestHandler
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedMovieViewModel.Factory::class)
class RecommendedMovieViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
        @Assisted val parentId: UUID,
        navigationManager: NavigationManager,
        favoriteWatchManager: FavoriteWatchManager,
        backdropService: BackdropService,
    ) : RecommendedViewModel(context, navigationManager, favoriteWatchManager, backdropService) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedMovieViewModel
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
                val itemsPerRow =
                    preferencesDataStore.data
                        .firstOrNull()
                        ?.homePagePreferences
                        ?.maxItemsPerRow
                        ?: AppPreference.HomePageItems.defaultValue.toInt()
                try {
                    val resumeItemsRequest =
                        GetResumeItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            enableUserData = true,
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    val resumeItems =
                        GetResumeItemsRequestHandler
                            .execute(api, resumeItemsRequest)
                            .toBaseItems(api, false)
                    update(
                        R.string.continue_watching,
                        HomeRowLoadingState.Success(
                            context.getString(R.string.continue_watching),
                            resumeItems,
                        ),
                    )

                    if (resumeItems.isNotEmpty()) {
                        loading.setValueOnMain(LoadingState.Success)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching movie recommendations")
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                    }
                }

                update(R.string.recently_released) {
                    val recentlyReleasedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
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
                        .toBaseItems(api, false)
                }

                update(R.string.recently_added) {
                    val recentlyAddedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
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
                        .toBaseItems(api, false)
                }

                update(R.string.suggestions) {
                    val suggestionsRequest =
                        GetSuggestionsRequest(
                            userId = serverRepository.currentUser.value?.id,
                            type = listOf(BaseItemKind.MOVIE),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetSuggestionsRequestHandler
                        .execute(api, suggestionsRequest)
                        .toBaseItems(api, false)
                }

                update(R.string.top_unwatched) {
                    val unwatchedTopRatedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
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
                        .toBaseItems(api, false)
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
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }

/**
 * The "recommended" tab of a movie library
 */
@Composable
fun RecommendedMovie(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    resetPositionOnEnter: Boolean = false,
    topRowFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    consumeDownToTopRow: Boolean = false,
    dropEmptyRows: Boolean = false,
    skipContentFocusUntilMillis: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    viewModel: RecommendedMovieViewModel =
        hiltViewModel<RecommendedMovieViewModel, RecommendedMovieViewModel.Factory>(
            creationCallback = { it.create(parentId) },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
        resetPositionOnEnter = resetPositionOnEnter,
        topRowFocusRequester = topRowFocusRequester,
        consumeDownToTopRow = consumeDownToTopRow,
        dropEmptyRows = dropEmptyRows,
        skipContentFocusUntilMillis = skipContentFocusUntilMillis,
        wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
        navHasFocus = navHasFocus,
    )
}
