package com.github.sysmoon.wholphin.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
) {
    var position by rememberPosition()
    val focusedItem =
        position.let {
            (homeRows.getOrNull(it.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(it.column)
        }
    // If the focused item is the synthetic "View All" card, keep the header/backdrop
    // driven by the last real item in that row so the top area doesn't go blank.
    val headerItem =
        if (focusedItem?.type == BaseItemKind.BOX_SET && focusedItem.name == "View All") {
            (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)
                ?.items
                ?.asReversed()
                ?.firstOrNull { it != null && !(it.type == BaseItemKind.BOX_SET && it.name == "View All") }
        } else {
            focusedItem
        }

    val listState = rememberLazyListState()
    val rowFocusRequesters = remember(homeRows) { List(homeRows.size) { FocusRequester() } }
    var firstFocused by remember { mutableStateOf(false) }
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
    // Ensure scrolling happens when returning to the screen with a saved position
    LaunchedEffect(homeRows, position.row) {
        if (firstFocused && homeRows.isNotEmpty() && position.row >= 0) {
            val index = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
            delay(50)
            listState.scrollToItem(index)
        }
    }
    LaunchedEffect(position) {
        if (position.row >= 0) {
            listState.animateScrollToItem(position.row)
        }
    }
    LaunchedEffect(onUpdateBackdrop, headerItem) {
        headerItem?.let { onUpdateBackdrop.invoke(it) }
    }
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = Cards.height2x3,
                ),
                modifier =
                    Modifier
                        .focusRestorer(),
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
                                val rowCardContent: @Composable (Int, BaseItem?, Modifier, () -> Unit, () -> Unit) -> Unit =
                                    { index, item, cardModifier, onClick, onLongClick ->
                                        val cornerText =
                                            remember(item) {
                                                item
                                                    ?.data
                                                    ?.userData
                                                    ?.unplayedItemCount
                                                    ?.takeIf { it > 0 }
                                                    ?.let { abbreviateNumber(it) }
                                            }
                                        BannerCard(
                                            name = item?.data?.seriesName ?: item?.name,
                                            item = item,
                                            aspectRatio = AspectRatios.TALL,
                                            forceTextOnly = item?.type == BaseItemKind.BOX_SET && item.name == "View All",
                                            cornerText = cornerText,
                                            played = item?.data?.userData?.played ?: false,
                                            favorite = item?.favorite ?: false,
                                            playPercent =
                                                item?.data?.userData?.playedPercentage
                                                    ?: 0.0,
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            modifier =
                                                cardModifier
                                                    .onFocusChanged {
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
                                                    }.onKeyEvent {
                                                        if (isPlayKeyUp(it) && item?.type?.playable == true) {
                                                            Timber.v("Clicked play on ${item.id}")
                                                            onClickPlay.invoke(position, item)
                                                            return@onKeyEvent true
                                                        }
                                                        return@onKeyEvent false
                                                    },
                                            interactionSource = null,
                                            cardHeight = Cards.height2x3,
                                        )
                                    }
                                // Card content for hero row poster preview (non-focusable cards)
                                // Use same height as hero card for visual consistency
                                val heroRowPosterContent: @Composable (Int, BaseItem?, Modifier) -> Unit =
                                    { index, item, cardModifier ->
                                        val cornerText =
                                            remember(item) {
                                                item
                                                    ?.data
                                                    ?.userData
                                                    ?.unplayedItemCount
                                                    ?.takeIf { it > 0 }
                                                    ?.let { abbreviateNumber(it) }
                                            }
                                        BannerCard(
                                            name = item?.data?.seriesName ?: item?.name,
                                            item = item,
                                            aspectRatio = AspectRatios.TALL,
                                            forceTextOnly = item?.type == BaseItemKind.BOX_SET && item.name == "View All",
                                            cornerText = cornerText,
                                            played = item?.data?.userData?.played ?: false,
                                            favorite = item?.favorite ?: false,
                                            playPercent =
                                                item?.data?.userData?.playedPercentage
                                                    ?: 0.0,
                                            onClick = { },
                                            onLongClick = { },
                                            modifier = cardModifier,
                                            interactionSource = null,
                                            cardHeight = HERO_CARD_HEIGHT,
                                        )
                                    }
                                if (rowIndex == position.row) {
                                    // Find the next valid row index (with non-empty items)
                                    val nextRowIndex = (rowIndex + 1 until homeRows.size).firstOrNull { idx ->
                                        (homeRows[idx] as? HomeRowLoadingState.Success)?.items?.isNotEmpty() == true
                                    }
                                    // Find the previous valid row index (with non-empty items)
                                    val prevRowIndex = (rowIndex - 1 downTo 0).firstOrNull { idx ->
                                        (homeRows[idx] as? HomeRowLoadingState.Success)?.items?.isNotEmpty() == true
                                    }
                                    HeroItemRow(
                                        title = row.title,
                                        items = row.items,
                                        heroItem = headerItem,
                                        focusedIndex = position.column.coerceIn(0, row.items.lastIndex.coerceAtLeast(0)),
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
                                        // Only provide onNavigateUp if there's a row above (null allows default behavior to nav bar)
                                        onNavigateUp = prevRowIndex?.let { prevIdx ->
                                            {
                                                val prevRow = homeRows[prevIdx] as HomeRowLoadingState.Success
                                                position = RowColumn(prevIdx, position.column.coerceIn(0, prevRow.items.lastIndex.coerceAtLeast(0)))
                                            }
                                        },
                                        // Always provide onNavigateDown to prevent focus escaping; only navigate if there's a row below
                                        onNavigateDown = nextRowIndex?.let { nextIdx ->
                                            {
                                                val nextRow = homeRows[nextIdx] as HomeRowLoadingState.Success
                                                position = RowColumn(nextIdx, position.column.coerceIn(0, nextRow.items.lastIndex.coerceAtLeast(0)))
                                            }
                                        } ?: { /* At last row, do nothing but consume the event */ },
                                        modifier =
                                            rowModifier
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        // Update position when the hero row gains focus
                                                        position = RowColumn(rowIndex, position.column.coerceIn(0, row.items.lastIndex.coerceAtLeast(0)))
                                                    }
                                                },
                                        cardContent = heroRowPosterContent,
                                    )
                                } else {
                                    ItemRow(
                                        title = row.title,
                                        items = row.items,
                                        onClickItem = { index, item ->
                                            onClickItem.invoke(RowColumn(rowIndex, index), item)
                                        },
                                        onLongClickItem = { index, item ->
                                            onLongClickItem.invoke(RowColumn(rowIndex, index), item)
                                        },
                                        modifier = rowModifier,
                                        cardContent = rowCardContent,
                                    )
                                }
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
private val HERO_ROW_BOTTOM_SPACING = 48.dp
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
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    val upcomingState = rememberLazyListState()
    val heroFocusRequester = remember { FocusRequester() }
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
        heroFocusRequester.tryRequestFocus()
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
                    onEnter = { heroFocusRequester }
                },
    ) {
        // Row title - above the hero card, aligned with its left edge
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                    modifier =
                        Modifier
                            .height(HERO_CARD_HEIGHT)
                            .width(HERO_CARD_WIDTH)
                            .focusRequester(heroFocusRequester)
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
                Spacer(modifier = Modifier.height(HERO_INFO_TOP_SPACING))
                HeroInfo(item = heroItem)
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
 * Metadata displayed below the hero card: quick details (genre, year, runtime, rating),
 * and overview. For episodes, the episode name is prepended to the quick details line.
 * No title — the logo in the hero card serves that purpose.
 */
@Composable
fun HeroInfo(item: BaseItem?) {
    item ?: return
    val isEpisode = item.type == BaseItemKind.EPISODE
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
        item.data.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
