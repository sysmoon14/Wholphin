package com.github.sysmoon.wholphin.ui.detail.series

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.ItemLogoHeight
import com.github.sysmoon.wholphin.ui.ItemLogoWidth
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.LoadingPage
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ImageType

private val SeasonColumnWidth = 280.dp
private val SeasonEpisodeGap = 32.dp

/** Height of one episode row slot (thumbnail + padding + spacing). Matches EpisodeListRow. */
private val EpisodeRowSlotHeight: Dp = 12.dp + (260.dp * 9f / 16f) + 12.dp + 8.dp  // ~186.dp

/** Thumbnail size in episode row - must match EpisodeListRow. */
private val EpisodeThumbnailWidth = 260.dp
private val EpisodeThumbnailHeight = EpisodeThumbnailWidth * 9f / 16f

@Composable
private fun SeriesOverviewHeader(
    series: BaseItem,
    seasons: List<BaseItem?>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrlService = LocalImageUrlService.current
    val resolvedLogoUrl = imageUrlService.rememberImageUrl(series, ImageType.LOGO)
    var logoError by remember(series) { mutableStateOf(false) }

    val metaParts =
        buildList<String> {
            series.data.productionYear?.let { add(it.toString()) }
            add(context.getString(R.string.seasons_count_format, seasons.size))
            series.data.status?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    val metaLine = metaParts.joinToString(" · ")

    Column(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (resolvedLogoUrl.isNotNullOrBlank() && !logoError) {
            AsyncImage(
                model = resolvedLogoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoError = true },
                modifier = Modifier.size(width = ItemLogoWidth, height = ItemLogoHeight),
            )
        }
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = ItemLogoWidth),
            )
        }
    }
}

@Composable
fun SeriesOverviewContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    chosenStreams: ChosenStreams?,
    position: SeriesOverviewPosition,
    firstItemFocusRequester: FocusRequester,
    onChangeSeason: (Int) -> Unit,
    onFocusEpisode: (Int) -> Unit,
    onSelectNextEpisode: () -> Unit,
    onSelectPreviousEpisode: () -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    onLongClickSeason: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var requestFocusAfterSeason by remember { mutableStateOf(false) }

    val seasonStr = stringResource(R.string.tv_season)
    val seasonFocusRequesters = remember(seasons) { List(seasons.size) { FocusRequester() } }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
                    .focusGroup()
                    .bringIntoViewRequester(bringIntoViewRequester),
            horizontalArrangement = Arrangement.spacedBy(SeasonEpisodeGap),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(SeasonColumnWidth)
                        .fillMaxHeight()
                        .focusGroup()
                        .then(
                            seasonFocusRequesters.getOrNull(position.seasonTabIndex.coerceIn(0, seasons.size - 1))
                                ?.let { Modifier.focusRestorer(it) }
                                ?: Modifier,
                        ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeriesOverviewHeader(
                    series = series,
                    seasons = seasons,
                    modifier = Modifier.fillMaxWidth(),
                )
                val seasonListState = rememberLazyListState()
                val seasonLayoutInfo = seasonListState.layoutInfo
                val lastVisibleSeasonIndex = seasonLayoutInfo.visibleItemsInfo.maxOfOrNull { it.index }
                val moreSeasonsBelow =
                    lastVisibleSeasonIndex != null &&
                        lastVisibleSeasonIndex < seasons.size - 1
                LazyColumn(
                    state = seasonListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        seasons,
                        key = { index, _ -> index },
                    ) { index, season ->
                        val isSelected = index == position.seasonTabIndex
                        val seasonInteractionSource = remember(index) { MutableInteractionSource() }
                        val isDimmed = moreSeasonsBelow && index == lastVisibleSeasonIndex
                        var ignoreNextSelectKeyUp by remember(index) { mutableStateOf(false) }
                        val onSeasonClick = {
                            onChangeSeason(index)
                            requestFocusAfterSeason = true
                        }
                        val isSelectKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = {
                            it.nativeKeyEvent.keyCode in
                                setOf(
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                )
                        }
                        Button(
                            onClick = onSeasonClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onPreviewKeyEvent { event ->
                                        if (isSelectKey(event)) {
                                            if (event.nativeKeyEvent.isLongPress) {
                                                season?.let {
                                                    ignoreNextSelectKeyUp = true
                                                    onLongClickSeason(it)
                                                }
                                                true
                                            } else if (
                                                event.type == KeyEventType.KeyUp &&
                                                ignoreNextSelectKeyUp
                                            ) {
                                                ignoreNextSelectKeyUp = false
                                                true
                                            } else false
                                        } else false
                                    }
                                    .then(
                                        if (isDimmed) Modifier.alpha(0.5f)
                                        else Modifier,
                                    )
                                    .then(
                                        if (isSelected) seasonFocusRequesters.getOrNull(index)?.let { Modifier.focusRequester(it) } ?: Modifier
                                        else Modifier,
                                    )
                                    .focusable(interactionSource = seasonInteractionSource)
                                    .focusProperties {
                                        right = firstItemFocusRequester
                                    },
                            interactionSource = seasonInteractionSource,
                            colors =
                                ButtonDefaults.colors(
                                    containerColor =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        } else {
                                            Color.Transparent
                                        },
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = season?.name
                                        ?: season?.data?.indexNumber?.let { "$seasonStr $it" }
                                        ?: "$seasonStr ${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                val episodeCount = season?.data?.childCount ?: 0
                                Text(
                                    text = context.getString(R.string.episode_count_format, episodeCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            EpisodeAreaInRow(
                episodes = episodes,
                position = position,
                firstItemFocusRequester = firstItemFocusRequester,
                requestFocusAfterSeason = requestFocusAfterSeason,
                onRequestFocusConsumed = { requestFocusAfterSeason = false },
                seasonFocusRequesters = seasonFocusRequesters,
                scope = scope,
                bringIntoViewRequester = bringIntoViewRequester,
                chosenStreams = chosenStreams,
                onFocusEpisode = onFocusEpisode,
                onSelectNextEpisode = onSelectNextEpisode,
                onSelectPreviousEpisode = onSelectPreviousEpisode,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickSeason = onLongClickSeason,
            )
        }
    }
}

@Composable
private fun RowScope.EpisodeAreaInRow(
    episodes: EpisodeList,
    position: SeriesOverviewPosition,
    firstItemFocusRequester: FocusRequester,
    requestFocusAfterSeason: Boolean,
    onRequestFocusConsumed: () -> Unit,
    seasonFocusRequesters: List<FocusRequester>,
    scope: kotlinx.coroutines.CoroutineScope,
    bringIntoViewRequester: BringIntoViewRequester,
    chosenStreams: ChosenStreams?,
    onFocusEpisode: (Int) -> Unit,
    onSelectNextEpisode: () -> Unit,
    onSelectPreviousEpisode: () -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    onLongClickSeason: (BaseItem) -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight(),
    ) {
        when (val eps = episodes) {
            EpisodeList.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingPage()
                }
            }
            is EpisodeList.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(eps.message, eps.exception)
                }
            }
            is EpisodeList.Success -> {
                if (requestFocusAfterSeason) {
                    LaunchedEffect(Unit) {
                        firstItemFocusRequester.tryRequestFocus()
                        onRequestFocusConsumed()
                    }
                }
                val lazyListState = rememberLazyListState(position.episodeRowIndex)
                LaunchedEffect(position.episodeRowIndex) {
                    if (position.episodeRowIndex in 0 until eps.episodes.size) {
                        lazyListState.animateScrollToItem(position.episodeRowIndex, 0)
                        // Ensure the selected item is exactly at the top (fixes last-item clamp)
                        lazyListState.scroll {
                            val info = lazyListState.layoutInfo
                            val first = info.visibleItemsInfo.firstOrNull() ?: return@scroll
                            if (first.index == position.episodeRowIndex && first.offset != 0) {
                                scrollBy(-first.offset.toFloat())
                            }
                        }
                        firstItemFocusRequester.tryRequestFocus()
                    }
                }
                val slotFocusSource = remember { MutableInteractionSource() }
                val slotFocused by slotFocusSource.collectIsFocusedAsState()
                var ignoreNextSelectKeyUp by remember { mutableStateOf(false) }
                val currentEpisode = eps.episodes.getOrNull(position.episodeRowIndex)
                val selectedSeasonFocus =
                    seasonFocusRequesters.getOrNull(
                        position.seasonTabIndex.coerceIn(0, seasonFocusRequesters.size - 1),
                    )

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Use full viewport height as bottom padding so the last item can scroll so its top reaches 0
                    val bottomPaddingPx = maxHeight
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            bottom = bottomPaddingPx,
                            end = 16.dp,
                        ),
                    ) {
                        itemsIndexed(eps.episodes) { _, episode ->
                            EpisodeListRow(
                                episode = episode,
                                chosenStreams = chosenStreams,
                                onClick = { if (episode != null) onClick(episode) },
                                onLongClick = { if (episode != null) onLongClick(episode) },
                                onFocusChanged = { },
                                modifier = Modifier,
                                isFocusable = false,
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(EpisodeRowSlotHeight)
                                .then(
                                    if (selectedSeasonFocus != null) {
                                        Modifier.focusProperties { left = selectedSeasonFocus }
                                    } else Modifier,
                                )
                                .focusRequester(firstItemFocusRequester)
                                .focusRestorer(firstItemFocusRequester)
                                .focusable(interactionSource = slotFocusSource)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusEpisode(position.episodeRowIndex)
                                        scope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                onSelectNextEpisode()
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                onSelectPreviousEpisode()
                                            }
                                            true
                                        }
                                        Key.MediaPlay, Key.MediaPlayPause,
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                                        Key.ButtonSelect, Key.ButtonA -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                if (ignoreNextSelectKeyUp) {
                                                    ignoreNextSelectKeyUp = false
                                                    true
                                                } else if (currentEpisode != null) {
                                                    onClick(currentEpisode)
                                                    true
                                                } else false
                                            } else false
                                        }
                                        else -> false
                                    }
                                }
                                .combinedClickable(
                                    onClick = { currentEpisode?.let { onClick(it) } },
                                    onLongClick = {
                                        currentEpisode?.let {
                                            ignoreNextSelectKeyUp = true
                                            onLongClick(it)
                                        }
                                    },
                                ),
                    ) {
                        if (slotFocused) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                        .size(EpisodeThumbnailWidth, EpisodeThumbnailHeight)
                                        .border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.border,
                                            shape = RoundedCornerShape(4.dp),
                                        ),
                            )
                        }
                    }
                }
            }
        }
    }
}
