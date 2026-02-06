package com.github.sysmoon.wholphin.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.AspectRatios
import com.github.sysmoon.wholphin.ui.Cards
import com.github.sysmoon.wholphin.ui.abbreviateNumber
import com.github.sysmoon.wholphin.ui.cards.BannerCard
import com.github.sysmoon.wholphin.ui.cards.ItemRow
import com.github.sysmoon.wholphin.ui.CrossFadeFactory
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.rememberInt
import com.github.sysmoon.wholphin.ui.components.CircularProgress
import com.github.sysmoon.wholphin.ui.components.DialogParams
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.components.EpisodeName
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.LoadingPage
import com.github.sysmoon.wholphin.ui.components.QuickDetails
import com.github.sysmoon.wholphin.ui.components.StreamLabel
import com.github.sysmoon.wholphin.ui.util.StreamFormatting.concatWithSpace
import com.github.sysmoon.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.sysmoon.wholphin.ui.util.StreamFormatting.formatVideoRange
import com.github.sysmoon.wholphin.ui.util.StreamFormatting.resolutionString
import com.github.sysmoon.wholphin.ui.data.AddPlaylistViewModel
import com.github.sysmoon.wholphin.ui.data.RowColumn
import com.github.sysmoon.wholphin.ui.detail.MoreDialogActions
import com.github.sysmoon.wholphin.ui.detail.PlaylistDialog
import com.github.sysmoon.wholphin.ui.detail.PlaylistLoadingState
import com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.sysmoon.wholphin.ui.indexOfFirstOrNull
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.playback.isPlayKeyUp
import com.github.sysmoon.wholphin.ui.playback.playable
import com.github.sysmoon.wholphin.ui.rememberPosition
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.HomeRowLoadingState
import com.github.sysmoon.wholphin.util.LoadingState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    topRowFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    var firstLoad by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        viewModel.init(preferences).join()
        firstLoad = false
    }
    val loading by viewModel.loadingState.observeAsState(LoadingState.Loading)
    val refreshing by viewModel.refreshState.observeAsState(LoadingState.Loading)
    val watchingRows by viewModel.watchingRows.observeAsState(listOf())
    val latestRows by viewModel.latestRows.observeAsState(listOf())
    LaunchedEffect(loading) {
        val state = loading
        if (!firstLoad && state is LoadingState.Error) {
            // After the first load, refreshes occur in the background and an ErrorMessage won't show
            // So send a Toast on errors instead
            Toast
                .makeText(
                    context,
                    "Home refresh error: ${state.localizedMessage}",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage()
        }

        LoadingState.Success -> {
            var dialog by remember { mutableStateOf<DialogParams?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                HomePageContent(
                watchingRows + latestRows,
                onClickItem = { position, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { position, item ->
                    val dialogItems =
                        buildMoreDialogItemsForHome(
                            context = context,
                            item = item,
                            seriesId = item.data.seriesId,
                            playbackPosition = item.playbackPosition,
                            watched = item.played,
                            favorite = item.favorite,
                            actions =
                                MoreDialogActions(
                                    navigateTo = viewModel.navigationManager::navigateTo,
                                    onClickWatch = { itemId, played ->
                                        viewModel.setWatched(itemId, played)
                                    },
                                    onClickFavorite = { itemId, favorite ->
                                        viewModel.setFavorite(itemId, favorite)
                                    },
                                    onClickAddPlaylist = { itemId ->
                                        playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                        showPlaylistDialog = itemId
                                    },
                                ),
                        )
                    dialog =
                        DialogParams(
                            title = item.title ?: "",
                            fromLongClick = true,
                            items = dialogItems,
                        )
                },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                modifier = modifier,
                topRowFocusRequester = topRowFocusRequester,
            )
            }
            dialog?.let { params ->
                DialogPopup(
                    params = params,
                    onDismissRequest = { dialog = null },
                )
            }
            showPlaylistDialog?.let { itemId ->
                PlaylistDialog(
                    title = stringResource(R.string.add_to_playlist),
                    state = playlistState,
                    onDismissRequest = { showPlaylistDialog = null },
                    onClick = {
                        playlistViewModel.addToPlaylist(it.id, itemId)
                        showPlaylistDialog = null
                    },
                    createEnabled = true,
                    onCreatePlaylist = {
                        playlistViewModel.createPlaylistAndAddItem(it, itemId)
                        showPlaylistDialog = null
                    },
                    elevation = 3.dp,
                )
            }
        }
    }
}

@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusPosition: ((RowColumn) -> Unit)? = null,
    loadingState: LoadingState? = null,
    topRowFocusRequester: FocusRequester? = null,
    resetPositionOnEnter: Boolean = false,
) {
    var position by rememberPosition()
    // Track column position for each row independently
    val rowColumnPositions = remember { mutableMapOf<Int, Int>() }
    val focusedItem =
        position.let {
            (homeRows.getOrNull(it.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(it.column)
        }
    // Pass the focused item directly - "View All" cards are now handled specially in the hero content
    val headerItem = focusedItem
    
    // For backdrop, keep the last real item (not "View All") to avoid abrupt background changes
    val isViewAllFocused = focusedItem?.type == BaseItemKind.BOX_SET && focusedItem.name == "View All"
    val backdropItem = if (isViewAllFocused) {
        // Use the last real item in the row for backdrop
        (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)
            ?.items
            ?.asReversed()
            ?.firstOrNull { it != null && !(it.type == BaseItemKind.BOX_SET && it.name == "View All") }
    } else {
        focusedItem
    }

    val listState = rememberLazyListState()
    val rowFocusRequesters = remember(homeRows) { List(homeRows.size) { FocusRequester() } }
    val topRowHeroFocusRequester = topRowFocusRequester ?: remember { FocusRequester() }
    val firstRowIndex =
        homeRows.indexOfFirst {
            it is HomeRowLoadingState.Success && it.items.isNotEmpty()
        }.takeIf { it >= 0 } ?: 0
    var firstFocused by remember { mutableStateOf(false) }
    var hasResetPosition by remember(resetPositionOnEnter) { mutableStateOf(false) }
    LaunchedEffect(homeRows) {
        if (!firstFocused && homeRows.isNotEmpty()) {
            if (position.row >= 0) {
                val index = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
                rowFocusRequesters.getOrNull(index)?.tryRequestFocus()
                firstFocused = true
                delay(50)
                listState.scrollToItem(index)
            } else {
                // Waiting for the first home row to load, then focus on it
                homeRows
                    .indexOfFirstOrNull { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
                    ?.let {
                        rowFocusRequesters[it].tryRequestFocus()
                        firstFocused = true
                        delay(50)
                        listState.scrollToItem(it)
                    }
            }
        }
    }
    // Track previous row to detect actual row changes (not initial focus)
    var previousRow by remember { mutableIntStateOf(-1) }
    LaunchedEffect(homeRows, resetPositionOnEnter) {
        if (resetPositionOnEnter && !hasResetPosition && homeRows.isNotEmpty()) {
            val firstRowIndex =
                homeRows.indexOfFirst {
                    it is HomeRowLoadingState.Success && it.items.isNotEmpty()
                }.takeIf { it >= 0 } ?: 0
            rowColumnPositions.clear()
            rowColumnPositions[firstRowIndex] = 0
            position = RowColumn(firstRowIndex, 0)
            previousRow = firstRowIndex
            rowFocusRequesters.getOrNull(firstRowIndex)?.tryRequestFocus()
            delay(50)
            listState.scrollToItem(firstRowIndex)
            firstFocused = true
            hasResetPosition = true
        }
    }
    
    // Ensure scrolling happens when returning to the screen with a saved position
    LaunchedEffect(homeRows, position.row) {
        if (firstFocused && homeRows.isNotEmpty() && position.row >= 0) {
            val index = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
            delay(50)
            listState.scrollToItem(index)
        }
    }
    // Only scroll vertically when navigating between rows (not on initial focus from nav bar)
    LaunchedEffect(position.row) {
        if (previousRow >= 0 && position.row >= 0 && position.row != previousRow) {
            listState.animateScrollToItem(position.row)
        }
        previousRow = position.row
    }
    LaunchedEffect(onUpdateBackdrop, backdropItem) {
        backdropItem?.let { onUpdateBackdrop.invoke(it) }
    }
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 0.dp,  // Allow rows to extend to screen edge
                    top = 16.dp,
                    bottom = Cards.height2x3,
                ),
                modifier =
                    Modifier
                        .focusGroup()
                        .focusProperties {
                            // When focus enters from the top nav, always land on the top row.
                            onEnter = {
                                if (homeRows.isEmpty()) {
                                    FocusRequester.Default
                                } else {
                                    val targetRow =
                                        firstRowIndex.coerceIn(0, rowFocusRequesters.lastIndex.coerceAtLeast(0))
                                    val savedColumn = rowColumnPositions[targetRow] ?: 0
                                    position =
                                        RowColumn(
                                            targetRow,
                                            savedColumn.coerceAtLeast(0),
                                        )
                                    if (targetRow == firstRowIndex) {
                                        topRowHeroFocusRequester
                                    } else {
                                        rowFocusRequesters.getOrNull(targetRow) ?: FocusRequester.Default
                                    }
                                }
                            }
                        },
            ) {
                itemsIndexed(homeRows) { rowIndex, row ->
                    when (val r = row) {
                        is HomeRowLoadingState.Loading,
                        is HomeRowLoadingState.Pending,
                        -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.animateItem(),
                            ) {
                                Text(
                                    text = r.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        is HomeRowLoadingState.Error -> {
                            var focused by remember { mutableStateOf(false) }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .onFocusChanged {
                                            focused = it.isFocused
                                        }.focusable()
                                        .background(
                                            if (focused) {
                                                // Just so the user can tell it has focus
                                                MaterialTheme.colorScheme.border.copy(alpha = .25f)
                                            } else {
                                                Color.Unspecified
                                            },
                                        ).animateItem(),
                            ) {
                                Text(
                                    text = r.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = r.localizedMessage,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        is HomeRowLoadingState.Success -> {
                            if (row.items.isNotEmpty()) {
                                val rowModifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusGroup()
                                        .focusRequester(rowFocusRequesters[rowIndex])
                                        .animateItem()
                                // Card content for non-hero rows (focusable cards)
                                // Uses backdrop image with logo overlay, wrapped in focusable Card
                                val rowCardContent: @Composable (Int, BaseItem?, Modifier, () -> Unit, () -> Unit) -> Unit =
                                    { index, item, cardModifier, onClick, onLongClick ->
                                        var hasFocus by remember { mutableStateOf(false) }
                                        Card(
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            colors = CardDefaults.colors(
                                                containerColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent,
                                            ),
                                            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                                            modifier = cardModifier
                                                .onFocusChanged {
                                                    hasFocus = it.isFocused
                                                    if (it.isFocused) {
                                                        position = RowColumn(rowIndex, index)
                                                    }
                                                    if (it.isFocused && onFocusPosition != null) {
                                                        val nonEmptyRowBefore =
                                                            homeRows
                                                                .subList(0, rowIndex)
                                                                .count {
                                                                    it is HomeRowLoadingState.Success && it.items.isEmpty()
                                                                }
                                                        onFocusPosition.invoke(
                                                            RowColumn(
                                                                rowIndex - nonEmptyRowBefore,
                                                                index,
                                                            ),
                                                        )
                                                    }
                                                }
                                                .onKeyEvent {
                                                    if (isPlayKeyUp(it) && item?.type?.playable == true) {
                                                        Timber.v("Clicked play on ${item.id}")
                                                        onClickPlay.invoke(position, item)
                                                        return@onKeyEvent true
                                                    }
                                                    return@onKeyEvent false
                                                },
                                        ) {
                                            BackdropPosterCard(
                                                item = item,
                                                showFocusBorder = true,
                                                hasFocus = hasFocus,
                                            )
                                        }
                                    }
                                // Card content for hero row poster preview (non-focusable cards)
                                // Uses backdrop image with logo overlay for visual consistency with hero
                                val heroRowPosterContent: @Composable (Int, BaseItem?, Modifier) -> Unit =
                                    { index, item, cardModifier ->
                                        BackdropPosterCard(
                                            item = item,
                                            modifier = cardModifier,
                                        )
                                    }
                                val isHeroRow = rowIndex == position.row
                                // Find the next valid row index (with non-empty items)
                                val nextRowIndex = (rowIndex + 1 until homeRows.size).firstOrNull { idx ->
                                    (homeRows[idx] as? HomeRowLoadingState.Success)?.items?.isNotEmpty() == true
                                }
                                // Find the previous valid row index (with non-empty items)
                                val prevRowIndex = (rowIndex - 1 downTo 0).firstOrNull { idx ->
                                    (homeRows[idx] as? HomeRowLoadingState.Success)?.items?.isNotEmpty() == true
                                }
                                
                                // For hero row, use the global headerItem; for non-hero rows, use the saved position
                                val rowFocusedIndex = if (isHeroRow) {
                                    position.column.coerceIn(0, row.items.lastIndex.coerceAtLeast(0))
                                } else {
                                    (rowColumnPositions[rowIndex] ?: 0).coerceIn(0, row.items.lastIndex.coerceAtLeast(0))
                                }
                                val rowHeroItem = if (isHeroRow) {
                                    headerItem
                                } else {
                                    row.items.getOrNull(rowFocusedIndex) as? BaseItem
                                }
                                
                                AnimatingHeroRow(
                                    title = row.title,
                                    items = row.items,
                                    heroItem = rowHeroItem,
                                    isHeroRow = isHeroRow,
                                    focusedIndex = rowFocusedIndex,
                                    onFocusedIndexChange = { newIndex ->
                                        position = RowColumn(rowIndex, newIndex)
                                        onFocusPosition?.invoke(position)
                                    },
                                    onClickItem = { index, item ->
                                        onClickItem.invoke(RowColumn(rowIndex, index), item)
                                    },
                                    onLongClickItem = { index, item ->
                                        onLongClickItem.invoke(RowColumn(rowIndex, index), item)
                                    },
                                    onPlayItem = { index, item ->
                                        if (item.type?.playable == true) {
                                            Timber.v("Clicked play on ${item.id}")
                                            onClickPlay.invoke(RowColumn(rowIndex, index), item)
                                        }
                                    },
                                    onNavigateUp = prevRowIndex?.let { prevIdx ->
                                        {
                                            rowColumnPositions[rowIndex] = position.column
                                            val prevRow = homeRows[prevIdx] as HomeRowLoadingState.Success
                                            val savedColumn = rowColumnPositions[prevIdx] ?: 0
                                            position = RowColumn(prevIdx, savedColumn.coerceIn(0, prevRow.items.lastIndex.coerceAtLeast(0)))
                                        }
                                    },
                                    onNavigateDown = nextRowIndex?.let { nextIdx ->
                                        {
                                            rowColumnPositions[rowIndex] = position.column
                                            val nextRow = homeRows[nextIdx] as HomeRowLoadingState.Success
                                            val savedColumn = rowColumnPositions[nextIdx] ?: 0
                                            position = RowColumn(nextIdx, savedColumn.coerceIn(0, nextRow.items.lastIndex.coerceAtLeast(0)))
                                        }
                                    } ?: { },
                                    modifier = rowModifier
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                val savedColumn = rowColumnPositions[rowIndex] ?: 0
                                                position = RowColumn(rowIndex, savedColumn.coerceIn(0, row.items.lastIndex.coerceAtLeast(0)))
                                            }
                                        },
                                    heroFocusRequester = if (rowIndex == firstRowIndex) topRowHeroFocusRequester else null,
                                    heroCardContent = heroRowPosterContent,
                                    posterCardContent = rowCardContent,
                                )
                            }
                        }
                    }
                }
            }
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
                }
            }

            else -> {}
        }
    }
}

@Composable
fun HomePageHeader(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    item?.let {
        val isEpisode = item.type == BaseItemKind.EPISODE
        val dto = item.data
        HomePageHeader(
            title = item.title,
            subtitle = if (isEpisode) dto.name else null,
            overview = dto.overview,
            overviewTwoLines = isEpisode,
            quickDetails = item.ui.quickDetails,
            timeRemaining = item.timeRemainingOrRuntime,
            modifier = modifier,
        )
    }
}

@Composable
fun HomePageHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: AnnotatedString,
    timeRemaining: Duration?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f)
                    .fillMaxHeight(),
        ) {
            subtitle?.let {
                EpisodeName(it)
            }
            QuickDetails(quickDetails, timeRemaining)
            val overviewModifier =
                Modifier
                    .padding(0.dp)
                    .height(48.dp + if (!overviewTwoLines) 12.dp else 0.dp)
                    .width(400.dp)
            if (overview.isNotNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (overviewTwoLines) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = overviewModifier,
                )
            } else {
                Spacer(overviewModifier)
            }
        }
    }
}

private val HERO_CARD_HEIGHT = 260.dp
private val HERO_CARD_WIDTH = HERO_CARD_HEIGHT * AspectRatios.WIDE
private val HERO_ROW_CARD_CORNER_RADIUS = 4.dp
private val HERO_ROW_LEFT_PADDING = 24.dp  // Space for passed card to peep from left edge
private val HERO_POSTER_GAP = 16.dp  // Same as gap between poster cards
private val HERO_INFO_TOP_SPACING = 12.dp
private val HERO_INFO_HEIGHT = 100.dp  // Fixed height for info section to prevent row shifting
private val HERO_ROW_BOTTOM_SPACING = 8.dp
private const val PASSED_ITEMS_ALPHA = 0.25f  // Dimmed alpha for passed items

/**
 * A row with a large hero card on the left (showing the focused item's backdrop + logo)
 * and scrollable poster cards to the right. The hero card is the focusable element;
 * D-pad left/right changes which item is the hero. Poster cards show items AFTER
 * the focused index and are not individually focusable - they scroll under the hero.
 */
@Composable
fun <T : Any> HeroItemRow(
    title: String,
    items: List<T?>,
    heroItem: BaseItem?,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onPlayItem: ((Int, T) -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    heroFocusRequester: FocusRequester? = null,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    val upcomingState = rememberLazyListState()
    val internalHeroFocusRequester = heroFocusRequester ?: remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    // Track previous focused index to determine navigation direction
    var previousFocusedIndex by remember { mutableIntStateOf(focusedIndex) }
    
    // The "current" passed item is at focusedIndex - 1
    // But during left-navigation animation, we need to show the OLD passed item
    // until the animation completes, then switch to the new one
    val currentPassedItem = remember(focusedIndex, items) {
        if (focusedIndex > 0 && focusedIndex <= items.size) {
            items.getOrNull(focusedIndex - 1)
        } else {
            null
        }
    }
    
    // Displayed item state - controlled by animation logic
    var displayedPassedItem by remember {
        mutableStateOf(if (focusedIndex > 0) items.getOrNull(focusedIndex - 1) else null)
    }
    var displayedPassedIndex by remember {
        mutableIntStateOf(if (focusedIndex > 0) focusedIndex - 1 else 0)
    }
    
    // Calculate card width (poster card width based on hero height and tall aspect ratio)
    val posterCardWidth = HERO_CARD_HEIGHT * (2f / 3f)  // Tall aspect ratio ≈ 173dp
    
    // Clipping box dimensions (extends from screen edge to hero's left edge)
    val clipBoxWidth = HERO_ROW_LEFT_PADDING + 16.dp  // 40dp total
    
    // Animation positions (in clipping box coordinates, where clipping box origin is at screen x=0):
    // - visible: card right edge at (clipBoxWidth - HERO_POSTER_GAP) = 24dp, showing rightmost portion at screen edge
    // - hiddenRight: card left edge at clipBoxWidth = 40dp, card fully outside clipping area (hidden under hero)
    // - hiddenLeft: card right edge at 0dp, card fully off-screen to the left
    val visibleOffset = (clipBoxWidth - HERO_POSTER_GAP) - posterCardWidth  // Card right edge at 24dp ≈ -149dp
    val hiddenRightOffset = clipBoxWidth  // Card left edge at 40dp (fully hidden under hero)
    val hiddenLeftOffset = -posterCardWidth  // Card right edge at 0dp (fully off-screen left) ≈ -173dp
    
    val passedCardOffset = remember {
        Animatable(if (focusedIndex > 0) visibleOffset else hiddenRightOffset, Dp.VectorConverter)
    }
    
    // Track if last navigation was right (for handling rapid navigation)
    var wasNavigatingRight by remember { mutableStateOf(true) }
    
    // Animate when focusedIndex changes
    LaunchedEffect(focusedIndex) {
        // Skip first invocation - initial state set via remember{}
        if (focusedIndex == previousFocusedIndex) return@LaunchedEffect
        
        val navigatingRight = focusedIndex > previousFocusedIndex
        val sameDirection = navigatingRight == wasNavigatingRight
        previousFocusedIndex = focusedIndex
        wasNavigatingRight = navigatingRight
        
        // Stop any ongoing animation immediately for responsive feel
        passedCardOffset.stop()
        
        coroutineScope {
            // Animate upcoming items scroll
            launch {
                val upcomingTarget = (focusedIndex + 1).coerceIn(0, items.lastIndex.coerceAtLeast(0))
                val upcomingScrollOffset = if (focusedIndex >= items.lastIndex) 10000 else 0
                upcomingState.animateScrollToItem(upcomingTarget, upcomingScrollOffset)
            }
            
            // Animate passed card
            launch {
                when {
                    navigatingRight && focusedIndex > 0 -> {
                        // Navigating RIGHT: new passed item slides out from under hero
                        displayedPassedItem = items.getOrNull(focusedIndex - 1)
                        displayedPassedIndex = focusedIndex - 1
                        
                        // Check if card is mid-animation (between hidden and visible, not at either end)
                        val midAnimation = passedCardOffset.value > visibleOffset + 10.dp && 
                                          passedCardOffset.value < hiddenRightOffset - 10.dp
                        
                        if (sameDirection && midAnimation) {
                            // Rapid right navigation while mid-animation - continue from current position
                            passedCardOffset.animateTo(visibleOffset, tween(200))
                        } else {
                            // Normal navigation or at rest - full animation from hidden (under hero)
                            passedCardOffset.snapTo(hiddenRightOffset)
                            passedCardOffset.animateTo(visibleOffset, tween(300))
                        }
                    }
                    !navigatingRight && focusedIndex == 0 -> {
                        // Going back to first item: slide current passed item under hero
                        passedCardOffset.animateTo(hiddenRightOffset, tween(300))
                        displayedPassedItem = null
                    }
                    !navigatingRight && focusedIndex > 0 -> {
                        // Navigating LEFT: old passed item slides under hero, new one slides in from left
                        // Use shorter durations (150ms each) so total time (~300ms) matches other animations
                        // Check if card is mid-animation
                        val midAnimation = passedCardOffset.value > visibleOffset + 10.dp && 
                                          passedCardOffset.value < hiddenRightOffset - 10.dp
                        
                        if (sameDirection && midAnimation) {
                            // Rapid left navigation while mid-animation - continue sliding under hero
                            passedCardOffset.animateTo(hiddenRightOffset, tween(100))
                        } else {
                            // Slide old item under hero
                            passedCardOffset.animateTo(hiddenRightOffset, tween(150))
                        }
                        // Switch to new passed item and animate from off-screen left
                        displayedPassedItem = items.getOrNull(focusedIndex - 1)
                        displayedPassedIndex = focusedIndex - 1
                        passedCardOffset.snapTo(hiddenLeftOffset)
                        passedCardOffset.animateTo(visibleOffset, tween(150))
                    }
                }
            }
        }
    }

    // When this HeroItemRow is first composed (row became focused), request focus on hero card
    DisposableEffect(Unit) {
        internalHeroFocusRequester.tryRequestFocus()
        onDispose { }
    }

    // Initial scroll position when first composed
    LaunchedEffect(Unit) {
        val upcomingTarget = (focusedIndex + 1).coerceIn(0, items.lastIndex.coerceAtLeast(0))
        val upcomingOffset = if (focusedIndex >= items.lastIndex) 10000 else 0
        if (items.isNotEmpty()) {
            upcomingState.scrollToItem(upcomingTarget, upcomingOffset)
        }
    }

    Column(
        modifier =
            modifier
                .focusGroup()
                .focusProperties {
                    // When entering this row via D-pad, focus the hero card
                    onEnter = { internalHeroFocusRequester }
                },
    ) {
        // Row title - above the hero card, aligned with its left edge
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = HERO_ROW_LEFT_PADDING, bottom = 8.dp),
        )
        // Use a Box to allow absolute positioning of passed card behind the hero
        Box(modifier = Modifier.fillMaxWidth()) {
            // Passed item - positioned absolutely so it doesn't affect hero position
            // Wrapped in a clipping Box that clips at the hero's left edge
            // This ensures the card slides "under" the hero visually
            displayedPassedItem?.let { item ->
                // Clipping region: from screen edge (offset -16dp) to hero's left edge
                // Clips at the right edge so card appears to slide "under" the hero
                Box(
                    modifier = Modifier
                        .offset(x = (-16).dp)  // Extend to screen edge (compensate for LazyColumn padding)
                        .width(clipBoxWidth)  // From screen edge to hero
                        .height(HERO_CARD_HEIGHT)
                        .clipToBounds(),  // Clip content at boundaries
                ) {
                    // Card positioned directly in clipping box coordinates
                    // wrapContentSize(unbounded = true) allows card to be full size, not constrained to clip box
                    // At visible: right edge at 24dp (shows 24dp at left screen edge)
                    // At hidden: left edge at 40dp (completely outside visible area)
                    Box(
                        modifier = Modifier
                            .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                            .offset(x = passedCardOffset.value)
                            .clip(RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS))
                            .graphicsLayer { alpha = PASSED_ITEMS_ALPHA },
                    ) {
                        cardContent.invoke(
                            displayedPassedIndex,
                            item,
                            Modifier.focusProperties { canFocus = false },
                        )
                    }
                }
            }
            
            // Main row content - hero and upcoming cards
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().zIndex(1f),
            ) {
                // Spacer to position hero at HERO_ROW_LEFT_PADDING
                Spacer(modifier = Modifier.width(HERO_ROW_LEFT_PADDING))
                
                // Hero card + info - this is the focusable element
            Column(
                modifier = Modifier.width(HERO_CARD_WIDTH),
            ) {
                Card(
                    onClick = {
                        val item = items.getOrNull(focusedIndex)
                        if (item != null) onClickItem.invoke(focusedIndex, item)
                    },
                    onLongClick = {
                        val item = items.getOrNull(focusedIndex)
                        if (item != null) onLongClickItem.invoke(focusedIndex, item)
                    },
                    colors = CardDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                    scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                    modifier =
                        Modifier
                            .height(HERO_CARD_HEIGHT)
                            .width(HERO_CARD_WIDTH)
                            .focusRequester(internalHeroFocusRequester)
                            .onFocusChanged { focusState ->
                                hasFocus = focusState.isFocused
                            }
                            .onPreviewKeyEvent { event ->
                                // Use onPreviewKeyEvent to intercept before Card processes
                                when (event.key) {
                                    Key.DirectionRight -> {
                                        // Always consume left/right to prevent focus escaping
                                        if (event.type == KeyEventType.KeyUp && focusedIndex < items.lastIndex) {
                                            onFocusedIndexChange(focusedIndex + 1)
                                        }
                                        return@onPreviewKeyEvent true // Consume both KeyDown and KeyUp
                                    }
                                    Key.DirectionLeft -> {
                                        // Always consume left/right to prevent focus escaping
                                        if (event.type == KeyEventType.KeyUp && focusedIndex > 0) {
                                            onFocusedIndexChange(focusedIndex - 1)
                                        }
                                        return@onPreviewKeyEvent true // Consume both KeyDown and KeyUp
                                    }
                                    Key.DirectionUp -> {
                                        // Handle up navigation - either go to previous row or let it escape to nav bar
                                        if (onNavigateUp != null) {
                                            if (event.type == KeyEventType.KeyUp) {
                                                onNavigateUp.invoke()
                                            }
                                            return@onPreviewKeyEvent true // Consume to prevent default behavior
                                        }
                                        // If no callback, allow default behavior (e.g., go to nav bar from first row)
                                        return@onPreviewKeyEvent false
                                    }
                                    Key.DirectionDown -> {
                                        // Handle down navigation
                                        if (onNavigateDown != null) {
                                            if (event.type == KeyEventType.KeyUp) {
                                                onNavigateDown.invoke()
                                            }
                                            return@onPreviewKeyEvent true // Consume to prevent default behavior
                                        }
                                        // If no callback, consume anyway to prevent focus escaping
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.MediaPlay, Key.MediaPlayPause -> {
                                        if (event.type == KeyEventType.KeyUp) {
                                            val item = items.getOrNull(focusedIndex)
                                            if (item != null && onPlayItem != null) {
                                                onPlayItem.invoke(focusedIndex, item)
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                    }
                                }
                                // Let other keys propagate
                                false
                            },
                ) {
                    HeroCardContent(
                        item = heroItem,
                        hasFocus = hasFocus,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Hide info for "View All" items
                val isHeroViewAll = heroItem?.type == BaseItemKind.BOX_SET && heroItem.name == "View All"
                if (!isHeroViewAll) {
                    Spacer(modifier = Modifier.height(HERO_INFO_TOP_SPACING))
                    HeroInfo(item = heroItem)
                }
            }
            // Right: upcoming items - shows all items but scrolls to show only those after focused
            // These are NOT focusable - they're a visual preview that scrolls under the hero
            LazyRow(
                state = upcomingState,
                horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
                contentPadding =
                    PaddingValues(
                        start = HERO_POSTER_GAP,
                        // Large end padding to allow scrolling to continue at end of list
                        end = HERO_CARD_WIDTH * 2,
                        top = 0.dp,
                        bottom = 0.dp,
                    ),
                userScrollEnabled = false, // Scrolling is controlled by focus changes
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(items) { index, item ->
                    cardContent.invoke(
                        index,
                        item,
                        Modifier.focusProperties { canFocus = false },
                    )
                }
            }
            }  // End Row
        }  // End Box (wrapper for passed card + row)
        // Bottom spacing to push next row down
        Spacer(modifier = Modifier.height(HERO_ROW_BOTTOM_SPACING))
    }
}

/**
 * A row that animates between hero (expanded) and non-hero (collapsed) states.
 * When isHeroRow is true, the first card expands to hero size and other cards slide right.
 * When isHeroRow is false, all cards are poster-sized in a standard row.
 */
@Composable
fun <T : Any> AnimatingHeroRow(
    title: String,
    items: List<T?>,
    heroItem: BaseItem?,
    isHeroRow: Boolean,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onPlayItem: ((Int, T) -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    heroFocusRequester: FocusRequester? = null,
    heroCardContent: @Composable (Int, T?, Modifier) -> Unit,
    posterCardContent: @Composable (Int, T?, Modifier, () -> Unit, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val posterCardWidth = HERO_CARD_HEIGHT * (2f / 3f)  // ~173dp
    val animationDuration = 600
    
    // Animate first card width between poster and hero size
    val firstCardWidth by animateDpAsState(
        targetValue = if (isHeroRow) HERO_CARD_WIDTH else posterCardWidth,
        animationSpec = tween(animationDuration),
        label = "firstCardWidth",
    )
    
    // Animate the offset for other cards (they slide right when hero expands)
    val cardsOffset by animateDpAsState(
        targetValue = if (isHeroRow) (HERO_CARD_WIDTH - posterCardWidth) else 0.dp,
        animationSpec = tween(animationDuration),
        label = "cardsOffset",
    )
    
    // Animate hero info visibility
    val heroInfoAlpha by animateFloatAsState(
        targetValue = if (isHeroRow) 1f else 0f,
        animationSpec = tween(animationDuration),
        label = "heroInfoAlpha",
    )
    
    // Hero content crossfade (show backdrop when hero, poster when not)
    val heroContentAlpha by animateFloatAsState(
        targetValue = if (isHeroRow) 1f else 0f,
        animationSpec = tween(animationDuration),
        label = "heroContentAlpha",
    )
    
    val internalHeroFocusRequester = heroFocusRequester ?: remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    
    // For hero mode: track passed items
    var previousFocusedIndex by remember { mutableIntStateOf(focusedIndex) }
    var displayedPassedItem by remember {
        mutableStateOf(if (focusedIndex > 0) items.getOrNull(focusedIndex - 1) else null)
    }
    var displayedPassedIndex by remember {
        mutableIntStateOf(if (focusedIndex > 0) focusedIndex - 1 else 0)
    }
    
    // Passed card animation values
    val clipBoxWidth = HERO_ROW_LEFT_PADDING + 16.dp
    val visibleOffset = (clipBoxWidth - HERO_POSTER_GAP) - posterCardWidth
    val hiddenRightOffset = clipBoxWidth
    val hiddenLeftOffset = -posterCardWidth
    
    val passedCardOffset = remember {
        Animatable(if (focusedIndex > 0) visibleOffset else hiddenRightOffset, Dp.VectorConverter)
    }
    var wasNavigatingRight by remember { mutableStateOf(true) }
    
    // When becoming hero row, request focus
    LaunchedEffect(isHeroRow) {
        if (isHeroRow) {
            internalHeroFocusRequester.tryRequestFocus()
        }
    }
    
    // Handle focus index changes (only when in hero mode)
    LaunchedEffect(focusedIndex, isHeroRow) {
        if (!isHeroRow) return@LaunchedEffect
        if (focusedIndex == previousFocusedIndex) return@LaunchedEffect
        
        val navigatingRight = focusedIndex > previousFocusedIndex
        val sameDirection = navigatingRight == wasNavigatingRight
        previousFocusedIndex = focusedIndex
        wasNavigatingRight = navigatingRight
        
        passedCardOffset.stop()
        
        coroutineScope {
            // Scroll the upcoming items (LazyRow uses items.drop(1), so index is focusedIndex, not focusedIndex+1)
            launch {
                val droppedListLastIndex = (items.size - 2).coerceAtLeast(0)
                val upcomingTarget = focusedIndex.coerceIn(0, droppedListLastIndex)
                val scrollOffset = if (focusedIndex >= items.lastIndex) 10000 else 0
                lazyListState.animateScrollToItem(upcomingTarget, scrollOffset)
            }
            
            // Animate passed card
            launch {
                when {
                    navigatingRight && focusedIndex > 0 -> {
                        displayedPassedItem = items.getOrNull(focusedIndex - 1)
                        displayedPassedIndex = focusedIndex - 1
                        val midAnimation = passedCardOffset.value > visibleOffset + 10.dp && 
                                          passedCardOffset.value < hiddenRightOffset - 10.dp
                        if (sameDirection && midAnimation) {
                            passedCardOffset.animateTo(visibleOffset, tween(200))
                        } else {
                            passedCardOffset.snapTo(hiddenRightOffset)
                            passedCardOffset.animateTo(visibleOffset, tween(300))
                        }
                    }
                    !navigatingRight && focusedIndex == 0 -> {
                        passedCardOffset.animateTo(hiddenRightOffset, tween(300))
                        displayedPassedItem = null
                    }
                    !navigatingRight && focusedIndex > 0 -> {
                        val midAnimation = passedCardOffset.value > visibleOffset + 10.dp && 
                                          passedCardOffset.value < hiddenRightOffset - 10.dp
                        if (sameDirection && midAnimation) {
                            passedCardOffset.animateTo(hiddenRightOffset, tween(100))
                        } else {
                            passedCardOffset.animateTo(hiddenRightOffset, tween(150))
                        }
                        displayedPassedItem = items.getOrNull(focusedIndex - 1)
                        displayedPassedIndex = focusedIndex - 1
                        passedCardOffset.snapTo(hiddenLeftOffset)
                        passedCardOffset.animateTo(visibleOffset, tween(150))
                    }
                }
            }
        }
    }
    
    // Initial scroll position (LazyRow uses items.drop(1), so index is focusedIndex, not focusedIndex+1)
    LaunchedEffect(Unit) {
        if (isHeroRow && items.isNotEmpty()) {
            val droppedListLastIndex = (items.size - 2).coerceAtLeast(0)
            val target = focusedIndex.coerceIn(0, droppedListLastIndex)
            val offset = if (focusedIndex >= items.lastIndex) 10000 else 0
            lazyListState.scrollToItem(target, offset)
        }
    }
    
    Column(
        modifier = modifier
            .focusGroup()
            .focusProperties {
                onEnter = { internalHeroFocusRequester }
            },
    ) {
        // Row title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = HERO_ROW_LEFT_PADDING, bottom = 8.dp),
        )
        
        Box(modifier = Modifier.fillMaxWidth()) {
            // Passed item - shown when focusedIndex > 0
            // In hero mode: uses animated offset
            // In non-hero mode: uses static visible position
            val showPassedCard = if (isHeroRow) displayedPassedItem != null else focusedIndex > 0
            val passedItem = if (isHeroRow) displayedPassedItem else items.getOrNull(focusedIndex - 1)
            val passedIndex = if (isHeroRow) displayedPassedIndex else focusedIndex - 1
            val passedOffset = if (isHeroRow) passedCardOffset.value else visibleOffset
            
            if (showPassedCard && passedItem != null) {
                Box(
                    modifier = Modifier
                        .offset(x = (-16).dp)
                        .width(clipBoxWidth)
                        .height(HERO_CARD_HEIGHT)
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                            .offset(x = passedOffset)
                            .clip(RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS))
                            .graphicsLayer { alpha = PASSED_ITEMS_ALPHA },
                    ) {
                        heroCardContent.invoke(
                            passedIndex,
                            passedItem,
                            Modifier.focusProperties { canFocus = false },
                        )
                    }
                }
            }
            
            // Main row content
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().zIndex(1f),
            ) {
                Spacer(modifier = Modifier.width(HERO_ROW_LEFT_PADDING))
                
                // First card (hero/expanded card)
                Column(modifier = Modifier.width(firstCardWidth)) {
                    Card(
                        onClick = {
                            val item = items.getOrNull(focusedIndex)
                            if (item != null) onClickItem.invoke(focusedIndex, item)
                        },
                        onLongClick = {
                            val item = items.getOrNull(focusedIndex)
                            if (item != null) onLongClickItem.invoke(focusedIndex, item)
                        },
                        colors = CardDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                        ),
                        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                        modifier = Modifier
                            .height(HERO_CARD_HEIGHT)
                            .fillMaxWidth()
                            .focusRequester(internalHeroFocusRequester)
                            .onFocusChanged { focusState ->
                                hasFocus = focusState.isFocused
                            }
                            .onPreviewKeyEvent { event ->
                                if (!isHeroRow) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionRight -> {
                                        if (event.type == KeyEventType.KeyUp && focusedIndex < items.lastIndex) {
                                            onFocusedIndexChange(focusedIndex + 1)
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.DirectionLeft -> {
                                        if (event.type == KeyEventType.KeyUp && focusedIndex > 0) {
                                            onFocusedIndexChange(focusedIndex - 1)
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.DirectionUp -> {
                                        if (onNavigateUp != null) {
                                            if (event.type == KeyEventType.KeyUp) onNavigateUp.invoke()
                                            return@onPreviewKeyEvent true
                                        }
                                        return@onPreviewKeyEvent false
                                    }
                                    Key.DirectionDown -> {
                                        if (onNavigateDown != null) {
                                            if (event.type == KeyEventType.KeyUp) onNavigateDown.invoke()
                                            return@onPreviewKeyEvent true
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.MediaPlay, Key.MediaPlayPause -> {
                                        if (event.type == KeyEventType.KeyUp) {
                                            val item = items.getOrNull(focusedIndex)
                                            if (item != null && onPlayItem != null) {
                                                onPlayItem.invoke(focusedIndex, item)
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                    }
                                }
                                false
                            },
                    ) {
                        // Single backdrop with animated logo position/size
                        AnimatingHeroCardContent(
                            item = heroItem,
                            isHeroMode = isHeroRow,
                            hasFocus = hasFocus && isHeroRow,
                            animationDuration = animationDuration,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // Hero info (animated visibility) - hide for "View All" items
                    val isHeroViewAll = heroItem?.type == BaseItemKind.BOX_SET && heroItem.name == "View All"
                    if (heroInfoAlpha > 0f && !isHeroViewAll) {
                        Spacer(modifier = Modifier.height(HERO_INFO_TOP_SPACING))
                        Box(modifier = Modifier.graphicsLayer { alpha = heroInfoAlpha }) {
                            HeroInfo(item = heroItem)
                        }
                    }
                }
                
                // Gap between first card and rest
                Spacer(modifier = Modifier.width(HERO_POSTER_GAP))
                
                // Other cards in a LazyRow
                LazyRow(
                    state = lazyListState,
                    horizontalArrangement = Arrangement.spacedBy(HERO_POSTER_GAP),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        end = HERO_CARD_WIDTH * 2,
                        top = 0.dp,
                        bottom = 0.dp,
                    ),
                    userScrollEnabled = !isHeroRow,
                    modifier = Modifier
                        .weight(1f)
                        .focusGroup(),
                ) {
                    // Start from index 1 since item 0 is shown as the first/hero card
                    itemsIndexed(items.drop(1)) { index, item ->
                        val actualIndex = index + 1
                        if (isHeroRow) {
                            // Hero mode: non-focusable preview cards
                            heroCardContent.invoke(
                                actualIndex,
                                item,
                                Modifier.focusProperties { canFocus = false },
                            )
                        } else {
                            // Non-hero mode: focusable cards with click handlers
                            posterCardContent.invoke(
                                actualIndex,
                                item,
                                Modifier,
                                { if (item != null) onClickItem.invoke(actualIndex, item) },
                                { if (item != null) onLongClickItem.invoke(actualIndex, item) },
                            )
                        }
                    }
                }
            }
        }
        
        // Bottom spacing (matches hero row spacing when in hero mode)
        val bottomSpacing by animateDpAsState(
            targetValue = if (isHeroRow) HERO_ROW_BOTTOM_SPACING else 8.dp,
            animationSpec = tween(animationDuration),
            label = "bottomSpacing",
        )
        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}

/**
 * Inner content of the hero card showing an item's backdrop image with its logo overlaid.
 * Falls back to title text if the logo image is unavailable.
 */
@Composable
fun HeroCardContent(
    item: BaseItem?,
    hasFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrlService = LocalImageUrlService.current
    val backdropUrl = imageUrlService.rememberImageUrl(item, ImageType.BACKDROP)
    val logoUrl = imageUrlService.rememberImageUrl(item, ImageType.LOGO)
    var logoError by remember(item) { mutableStateOf(false) }
    var backdropError by remember(item) { mutableStateOf(false) }
    val focusBorderColor = MaterialTheme.colorScheme.border
    val focusBorderWidth = 3.dp

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS))
                .then(
                    if (hasFocus) {
                        Modifier.border(
                            width = focusBorderWidth,
                            color = focusBorderColor,
                            shape = RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS),
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        // Backdrop image fills the card
        if (!backdropError && backdropUrl != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(backdropUrl)
                        .transitionFactory(CrossFadeFactory(600.milliseconds))
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { backdropError = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        // Bottom gradient scrim for logo legibility
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.75f),
                                        ),
                                    startY = size.height * 0.4f,
                                    endY = size.height,
                                ),
                        )
                    },
        )

        // Logo image or title text fallback, bottom-left
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            if (!logoError && logoUrl != null) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(logoUrl)
                            .transitionFactory(CrossFadeFactory(600.milliseconds))
                            .build(),
                    contentDescription = item?.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onError = { logoError = true },
                    modifier =
                        Modifier
                            .width(200.dp)
                            .height(80.dp),
                )
            } else {
                item?.title?.let { title ->
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Hero card content that animates the logo position and size when transitioning
 * between poster mode (collapsed) and hero mode (expanded).
 */
@Composable
fun AnimatingHeroCardContent(
    item: BaseItem?,
    isHeroMode: Boolean,
    hasFocus: Boolean,
    animationDuration: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrlService = LocalImageUrlService.current
    val focusBorderColor = MaterialTheme.colorScheme.border
    val focusBorderWidth = 3.dp
    
    // Check if this is a "View All" card
    val isViewAll = item?.type == BaseItemKind.BOX_SET && item.name == "View All"
    
    // Only load images for non-View All items
    val backdropUrl = if (!isViewAll) imageUrlService.rememberImageUrl(item, ImageType.BACKDROP) else null
    val logoUrl = if (!isViewAll) imageUrlService.rememberImageUrl(item, ImageType.LOGO) else null
    var logoError by remember(item) { mutableStateOf(false) }
    var backdropError by remember(item) { mutableStateOf(false) }
    
    // Animate logo dimensions
    val logoWidth by animateDpAsState(
        targetValue = if (isHeroMode) 200.dp else 120.dp,
        animationSpec = tween(animationDuration),
        label = "logoWidth",
    )
    val logoHeight by animateDpAsState(
        targetValue = if (isHeroMode) 80.dp else 50.dp,
        animationSpec = tween(animationDuration),
        label = "logoHeight",
    )
    
    // Animate padding
    val logoPadding by animateDpAsState(
        targetValue = if (isHeroMode) 16.dp else 8.dp,
        animationSpec = tween(animationDuration),
        label = "logoPadding",
    )
    
    // Animate text size for fallback
    val textStyle = if (isHeroMode) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.titleSmall
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS))
            .then(
                if (hasFocus) {
                    Modifier.border(
                        width = focusBorderWidth,
                        color = focusBorderColor,
                        shape = RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (isViewAll) {
            // "View All" card - show centered text with icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isHeroMode) 64.dp else 32.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "View All",
                        style = if (isHeroMode) {
                            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // Normal item - show backdrop and logo
            // Backdrop image fills the card
            if (!backdropError && backdropUrl != null) {
                AsyncImage(
                    model = ImageRequest
                        .Builder(context)
                        .data(backdropUrl)
                        .transitionFactory(CrossFadeFactory(600.milliseconds))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { backdropError = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            // Bottom gradient scrim for logo legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f),
                                ),
                                startY = size.height * 0.4f,
                                endY = size.height,
                            ),
                        )
                    },
            )

            // Logo image or title text fallback - animates position from center to left
            // Use BiasAlignment with animated horizontal bias for smooth position animation
            val horizontalBias by animateFloatAsState(
                targetValue = if (isHeroMode) -1f else 0f,  // -1 = start, 0 = center
                animationSpec = tween(animationDuration),
                label = "logoHorizontalBias",
            )
            
            Box(
                contentAlignment = BiasAlignment(horizontalBias, 1f),  // 1f = bottom
                modifier = Modifier
                    .fillMaxSize()
                    .padding(logoPadding),
            ) {
                if (!logoError && logoUrl != null) {
                    AsyncImage(
                        model = ImageRequest
                            .Builder(context)
                            .data(logoUrl)
                            .transitionFactory(CrossFadeFactory(600.milliseconds))
                            .build(),
                        contentDescription = item?.title,
                        contentScale = ContentScale.Fit,
                        alignment = BiasAlignment(horizontalBias, 1f),
                        onError = { logoError = true },
                        modifier = Modifier
                            .width(logoWidth)
                            .height(logoHeight),
                    )
                } else {
                    item?.title?.let { title ->
                        Text(
                            text = title,
                            style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (isHeroMode) TextAlign.Start else TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A poster-sized card showing backdrop image with logo overlaid at the bottom.
 * Used for poster cards in hero rows to maintain visual consistency with the hero card.
 */
@Composable
fun BackdropPosterCard(
    item: BaseItem?,
    modifier: Modifier = Modifier,
    showFocusBorder: Boolean = false,
    hasFocus: Boolean = false,
) {
    val context = LocalContext.current
    val imageUrlService = LocalImageUrlService.current
    val backdropUrl = imageUrlService.rememberImageUrl(item, ImageType.BACKDROP)
    val logoUrl = imageUrlService.rememberImageUrl(item, ImageType.LOGO)
    var logoError by remember(item) { mutableStateOf(false) }
    var backdropError by remember(item) { mutableStateOf(false) }
    val focusBorderColor = MaterialTheme.colorScheme.border
    val focusBorderWidth = 3.dp
    
    // Poster card dimensions
    val posterCardWidth = HERO_CARD_HEIGHT * (2f / 3f)

    Box(
        modifier = modifier
            .width(posterCardWidth)
            .height(HERO_CARD_HEIGHT)
            .clip(RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS))
            .then(
                if (showFocusBorder && hasFocus) {
                    Modifier.border(
                        width = focusBorderWidth,
                        color = focusBorderColor,
                        shape = RoundedCornerShape(HERO_ROW_CARD_CORNER_RADIUS),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // Backdrop image fills the card
        if (!backdropError && backdropUrl != null) {
            AsyncImage(
                model = ImageRequest
                    .Builder(context)
                    .data(backdropUrl)
                    .transitionFactory(CrossFadeFactory(600.milliseconds))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { backdropError = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        // Bottom gradient scrim for logo legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                            startY = size.height * 0.5f,
                            endY = size.height,
                        ),
                    )
                },
        )

        // Logo image or title text fallback, bottom-center
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            if (!logoError && logoUrl != null) {
                AsyncImage(
                    model = ImageRequest
                        .Builder(context)
                        .data(logoUrl)
                        .transitionFactory(CrossFadeFactory(600.milliseconds))
                        .build(),
                    contentDescription = item?.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    onError = { logoError = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                )
            } else {
                item?.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Metadata displayed below the hero card: quick details (genre, year, runtime, rating),
 * and overview. For episodes, the episode name is prepended to the quick details line.
 * No title — the logo in the hero card serves that purpose.
 */
@Composable
fun HeroInfo(item: BaseItem?) {
    item ?: return
    val context = LocalContext.current
    val isEpisode = item.type == BaseItemKind.EPISODE
    val isMovie = item.type == BaseItemKind.MOVIE
    
    // Extract video and audio info from media sources for movies and episodes
    val mediaSource = remember(item) { item.data.mediaSources?.firstOrNull() }
    val videoStream = remember(mediaSource) {
        mediaSource?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    }
    val audioStream = remember(mediaSource) {
        mediaSource?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
    }
    
    // Format video badge (resolution + HDR)
    val videoBadge = remember(videoStream) {
        videoStream?.let {
            val width = it.width
            val height = it.height
            val resName = if (width != null && height != null) {
                resolutionString(width, height, videoStream.isInterlaced)
            } else null
            val range = formatVideoRange(context, it.videoRange, it.videoRangeType, it.videoDoViTitle)
            resName.concatWithSpace(range)
        }
    }
    
    // Format audio badge - show profile (e.g., "Dolby Atmos") or codec + channel layout (e.g., "DD+ 5.1")
    val audioBadge = remember(audioStream) {
        audioStream?.let { stream ->
            // Prefer profile name (Dolby Atmos, DTS:X, etc)
            stream.profile?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(
                    formatAudioCodec(context, stream.codec, stream.profile),
                    stream.channelLayout
                ).joinToString(" ").takeIf { it.isNotBlank() }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(HERO_INFO_HEIGHT),
    ) {
        // For episodes, prepend episode name to details; otherwise just show details
        if (isEpisode && !item.data.name.isNullOrBlank()) {
            val episodeName = item.data.name ?: ""
            val separator = " • "
            // Build combined text: "Episode Name • S1:E2 • 45 min • etc"
            val combinedDetails = buildAnnotatedString {
                append(episodeName)
                if (item.ui.quickDetails.isNotEmpty()) {
                    append(separator)
                    append(item.ui.quickDetails)
                }
            }
            QuickDetails(combinedDetails, item.timeRemainingOrRuntime)
        } else {
            QuickDetails(item.ui.quickDetails, item.timeRemainingOrRuntime)
        }
        
        // Media badges for movies and episodes (resolution/HDR, audio format)
        if ((isMovie || isEpisode) && (videoBadge != null || audioBadge != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                videoBadge?.let { StreamLabel(text = it) }
                audioBadge?.let { StreamLabel(text = it) }
            }
        }
        
        // Overview - displayed for all item types (movies, TV shows, episodes)
        val overview = item.data.overview?.takeIf { it.isNotBlank() }
        if (overview != null) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
