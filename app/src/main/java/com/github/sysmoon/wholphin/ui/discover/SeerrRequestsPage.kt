package com.github.sysmoon.wholphin.ui.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.api.seerr.infrastructure.ClientException
import com.github.sysmoon.wholphin.api.seerr.model.MediaRequest
import com.github.sysmoon.wholphin.data.model.DiscoverItem
import com.github.sysmoon.wholphin.data.model.SeerrItemType
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.SeerrServerRepository
import com.github.sysmoon.wholphin.services.SeerrService
import com.github.sysmoon.wholphin.ui.cards.DiscoverItemCard
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.LoadingPage
import com.github.sysmoon.wholphin.ui.detail.CardGrid
import com.github.sysmoon.wholphin.ui.detail.CardGridItem
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

private const val REQUESTS_PAGE_SIZE = 32

@HiltViewModel
class SeerrRequestsViewModel
    @Inject
    constructor(
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(SeerrRequestsState.EMPTY)

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            seerrServerRepository.current
                .onEach { user ->
                    if (user != null) {
                        state.update { it.copy(requests = DataLoadingState.Loading, hasMore = false) }
                        loadPage(take = REQUESTS_PAGE_SIZE, skip = 0, append = false)
                    } else {
                        state.update { it.copy(requests = DataLoadingState.Pending, hasMore = false) }
                    }
                }.launchIn(viewModelScope)
        }

        fun loadMore() {
            if (state.value.loadingMore || !state.value.hasMore) return
            val current = (state.value.requests as? DataLoadingState.Success)?.data ?: return
            state.update { it.copy(loadingMore = true) }
            viewModelScope.launchIO {
                try {
                    loadPage(take = REQUESTS_PAGE_SIZE, skip = current.size, append = true)
                } finally {
                    state.update { it.copy(loadingMore = false) }
                }
            }
        }

        private suspend fun loadPage(
            take: Int,
            skip: Int,
            append: Boolean,
        ) {
            val semaphore = Semaphore(3)
            val mediaRequests =
                try {
                    seerrService.api.requestApi
                        .requestGet(take = take, skip = skip)
                        .results
                        .orEmpty()
                } catch (e: ClientException) {
                    Timber.w(e, "Seerr requestGet failed (e.g. 401 when navigating away)")
                    state.update {
                        it.copy(requests = DataLoadingState.Error(e.message ?: "Request failed", e))
                    }
                    return
                } catch (e: Exception) {
                    Timber.w(e, "Seerr requestGet failed")
                    state.update {
                        it.copy(requests = DataLoadingState.Error(e.message ?: "Request failed", e))
                    }
                    return
                }
            val requests =
                mediaRequests.mapNotNull { request ->
                    if (request.media?.tmdbId != null) {
                        viewModelScope.async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val type = SeerrItemType.fromString(request.type)
                                when (type) {
                                    SeerrItemType.MOVIE -> {
                                        seerrService.api.moviesApi
                                            .movieMovieIdGet(
                                                movieId = request.media.tmdbId,
                                            ).let { DiscoverItem(it) }
                                    }

                                    SeerrItemType.TV -> {
                                        seerrService.api.tvApi
                                            .tvTvIdGet(tvId = request.media.tmdbId)
                                            .let { DiscoverItem(it) }
                                    }

                                    SeerrItemType.PERSON -> {
                                        null
                                    }

                                    SeerrItemType.UNKNOWN -> {
                                        null
                                    }
                                }?.let { RequestGridItem(request, it) }
                            }
                        }
                    } else {
                        Timber.v("No TMDB ID for request %s", request.id)
                        null
                    }
                }
            val results = requests.awaitAll().filterNotNull()
            val hasMore = mediaRequests.size == take

            state.update {
                if (append) {
                    val existing = (it.requests as? DataLoadingState.Success)?.data.orEmpty()
                    it.copy(
                        requests = DataLoadingState.Success(existing + results),
                        hasMore = hasMore,
                    )
                } else {
                    it.copy(
                        requests = DataLoadingState.Success(results),
                        hasMore = hasMore,
                    )
                }
            }
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                }
            }
        }
    }

data class SeerrRequestsState(
    val requests: DataLoadingState<List<RequestGridItem>>,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
) {
    companion object {
        val EMPTY = SeerrRequestsState(DataLoadingState.Pending)
    }
}

data class RequestGridItem(
    val request: MediaRequest,
    val item: DiscoverItem,
) : CardGridItem {
    override val gridId: String = request.id.toString()
    override val playable: Boolean = false
    override val sortName: String = request.updatedAt ?: "0000"
}

@Composable
fun SeerrRequestsPage(
    focusRequesterOnEmpty: FocusRequester?,
    modifier: Modifier = Modifier,
    deferInitialFocus: Boolean = false,
    viewModel: SeerrRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState(SeerrRequestsState.EMPTY)

    when (val state = state.requests) {
        is DataLoadingState.Error -> {
            ErrorMessage(state.message, state.exception, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<RequestGridItem>> -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (deferInitialFocus) return@LaunchedEffect
                if (state.data.isNotEmpty()) {
                    focusRequester.tryRequestFocus()
                } else {
                    focusRequesterOnEmpty?.tryRequestFocus()
                }
            }
            Column(modifier = modifier) {
//                Text(
//                    text = stringResource(R.string.request),
//                    style = MaterialTheme.typography.displaySmall,
//                    color = MaterialTheme.colorScheme.onBackground,
//                    textAlign = TextAlign.Center,
//                    modifier = Modifier.fillMaxWidth(),
//                )
                if (state.data.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CardGrid(
                        pager = state.data,
                        onClickItem = { index: Int, item: RequestGridItem ->
                            viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item.item))
                        },
                        onLongClickItem = { index: Int, item: RequestGridItem ->
                        },
                        onClickPlay = { _, item ->
                        },
                        letterPosition = { c: Char -> 0 },
                        gridFocusRequester = focusRequester,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        spacing = 16.dp,
                        onNearEndOfList = { viewModel.loadMore() },
                        cardContent = @Composable { item, onClick, onLongClick, mod ->
                            DiscoverItemCard(
                                item = item?.item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = true,
                                modifier = mod,
                            )
                        },
                        columns = 6,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
