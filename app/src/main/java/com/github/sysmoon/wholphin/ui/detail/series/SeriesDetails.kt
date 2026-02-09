package com.github.sysmoon.wholphin.ui.detail.series

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ExtrasItem
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.data.model.DiscoverItem
import com.github.sysmoon.wholphin.data.model.Person
import com.github.sysmoon.wholphin.data.model.Trailer
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.TrailerService
import com.github.sysmoon.wholphin.ui.Cards
import com.github.sysmoon.wholphin.ui.RequestOrRestoreFocus
import com.github.sysmoon.wholphin.ui.cards.ExtrasRow
import com.github.sysmoon.wholphin.ui.cards.ItemRow
import com.github.sysmoon.wholphin.ui.cards.SeasonCard
import com.github.sysmoon.wholphin.ui.components.ConfirmDialog
import com.github.sysmoon.wholphin.ui.components.DialogItem
import com.github.sysmoon.wholphin.ui.components.DialogParams
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.ExpandableFaButton
import com.github.sysmoon.wholphin.ui.components.ExpandablePlayButton
import com.github.sysmoon.wholphin.ui.components.GenreText
import com.github.sysmoon.wholphin.ui.components.LoadingPage
import com.github.sysmoon.wholphin.ui.components.Optional
import com.github.sysmoon.wholphin.ui.components.OverviewText
import com.github.sysmoon.wholphin.ui.components.QuickDetails
import com.github.sysmoon.wholphin.ui.components.TrailerButton
import com.github.sysmoon.wholphin.ui.data.AddPlaylistViewModel
import com.github.sysmoon.wholphin.ui.data.ItemDetailsDialog
import com.github.sysmoon.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.sysmoon.wholphin.ui.detail.MoreDialogActions
import com.github.sysmoon.wholphin.ui.detail.PlaylistDialog
import com.github.sysmoon.wholphin.ui.detail.PlaylistLoadingState
import com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.sysmoon.wholphin.ui.detail.DetailActionButtons
import com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItemsForPerson
import com.github.sysmoon.wholphin.ui.discover.DiscoverRow
import com.github.sysmoon.wholphin.ui.discover.DiscoverRowData
import com.github.sysmoon.wholphin.ui.ItemLogoHeight
import com.github.sysmoon.wholphin.ui.ItemLogoWidth
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.letNotEmpty
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.rememberInt
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.extensions.ticks
import com.github.sysmoon.wholphin.util.DataLoadingState
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID
import kotlin.time.Duration

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    autoPlayOnLoad: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, null, SeriesPageType.DETAILS)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val item by viewModel.item.observeAsState()
    val seasons by viewModel.seasons.observeAsState(listOf())
    val nextUpEpisode by viewModel.nextUpEpisode.observeAsState()
    val collections by viewModel.collections.observeAsState(listOf())
    val trailers by viewModel.trailers.observeAsState(listOf())
    val extras by viewModel.extras.observeAsState(listOf())
    val people by viewModel.people.observeAsState(listOf())
    val similar by viewModel.similar.observeAsState(listOf())
    val discovered by viewModel.discovered.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showWatchConfirmation by remember { mutableStateOf(false) }
    var seasonDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    val didAutoPlay = remember(destination.itemId, autoPlayOnLoad) { mutableStateOf(false) }
    LaunchedEffect(loading, item, autoPlayOnLoad) {
        if (!didAutoPlay.value &&
            autoPlayOnLoad &&
            loading == LoadingState.Success &&
            item != null
        ) {
            didAutoPlay.value = true
            viewModel.playNextUp()
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
            item?.let { item ->
                LifecycleResumeEffect(destination.itemId) {
                    viewModel.onResumePage()

                    onPauseOrDispose {
                        viewModel.release()
                    }
                }

                val played = item.data.userData?.played ?: false
                val playButtonLabel =
                    nextUpEpisode?.let { ep ->
                        val seasonNum = ep.data.parentIndexNumber ?: 1
                        val epNum = ep.data.indexNumber ?: 1
                        val isResume =
                            (ep.data.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO) >
                                Duration.ZERO
                        context.getString(
                            if (isResume) R.string.resume_season_episode else R.string.play_season_episode,
                            seasonNum,
                            epNum,
                        )
                    } ?: context.getString(if (played) R.string.resume else R.string.play)
                SeriesDetailsContent(
                    preferences = preferences,
                    series = item,
                    seasons = seasons,
                    playButtonLabel = playButtonLabel,
                    nextUpResumePosition = nextUpEpisode?.playbackPosition ?: Duration.ZERO,
                    trailers = trailers,
                    extras = extras,
                    people = people,
                    similar = similar,
                    played = played,
                    favorite = item.data.userData?.isFavorite ?: false,
                    modifier = modifier,
                    onClickItem = { index, item ->
                        viewModel.navigateTo(item.destination())
                    },
                    onClickPerson = {
                        viewModel.navigateTo(
                            Destination.MediaItem(
                                it.id,
                                BaseItemKind.PERSON,
                            ),
                        )
                    },
                    onLongClickItem = { index, season ->
                        seasonDialog =
                            buildDialogForSeason(
                                context = context,
                                s = season,
                                onClickItem = { viewModel.navigateTo(it.destination()) },
                                markPlayed = { played ->
                                    viewModel.setSeasonWatched(season.id, played)
                                },
                                onClickPlay = { shuffle ->
                                    viewModel.navigateTo(
                                        Destination.PlaybackList(
                                            itemId = season.id,
                                            shuffle = shuffle,
                                        ),
                                    )
                                },
                            )
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = item.name ?: context.getString(R.string.unknown),
                                overview = item.data.overview,
                                genres = item.data.genres.orEmpty(),
                                files = listOf(),
                            )
                    },
                    playOnClick = { shuffle ->
                        if (shuffle) {
                            viewModel.navigateTo(
                                Destination.PlaybackList(
                                    itemId = item.id,
                                    shuffle = true,
                                ),
                            )
                        } else {
                            viewModel.playNextUp()
                        }
                    },
                    watchOnClick = { showWatchConfirmation = true },
                    favoriteOnClick = {
                        val favorite = item.data.userData?.isFavorite ?: false
                        viewModel.setFavorite(item.id, !favorite, null)
                    },
                    trailerOnClick = {
                        TrailerService.onClick(context, it, viewModel::navigateTo)
                    },
                    onClickExtra = { _, extra ->
                        viewModel.navigateTo(extra.destination)
                    },
                    discovered = discovered,
                    onClickDiscover = { index, item ->
                        viewModel.navigateTo(item.destination)
                    },
                    onMoreEpisodesClick = {
                        viewModel.navigateTo(
                            Destination.SeriesOverview(
                                item.id,
                                BaseItemKind.SERIES,
                                null,
                            ),
                        )
                    },
                    onChooseSubtitlesClick = { },
                    onCastAndCrewClick = {
                        viewModel.navigateTo(Destination.CastAndCrew(item.id, BaseItemKind.SERIES))
                    },
                    collections = collections,
                    moreActions =
                        MoreDialogActions(
                            navigateTo = { viewModel.navigateTo(it) },
                            onClickWatch = { itemId, played ->
                                viewModel.setWatched(itemId, played, null)
                            },
                            onClickFavorite = { itemId, played ->
                                viewModel.setFavorite(itemId, played, null)
                            },
                            onClickAddPlaylist = { itemId ->
                                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                showPlaylistDialog.makePresent(itemId)
                            },
                        ),
                )
                if (showWatchConfirmation) {
                    ConfirmDialog(
                        title = item.name ?: "",
                        body =
                            stringResource(if (played) R.string.mark_entire_series_as_unplayed else R.string.mark_entire_series_as_played),
                        onCancel = {
                            showWatchConfirmation = false
                        },
                        onConfirm = {
                            viewModel.setWatchedSeries(!played)
                            showWatchConfirmation = false
                        },
                    )
                }
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
            onDismissRequest = { overviewDialog = null },
        )
    }
    seasonDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            waitToLoad = params.fromLongClick,
            onDismissRequest = { seasonDialog = null },
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
}

private const val HEADER_ROW = 0
private const val CONTENT_ROW = HEADER_ROW + 1
private const val EXTRAS_ROW = CONTENT_ROW + 1
private const val DISCOVER_ROW = EXTRAS_ROW + 1

@Composable
fun SeriesDetailsContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    playButtonLabel: String,
    nextUpResumePosition: Duration,
    similar: List<BaseItem>,
    trailers: List<Trailer>,
    extras: List<ExtrasItem>,
    people: List<Person>,
    discovered: List<DiscoverItem>,
    played: Boolean,
    favorite: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    playOnClick: (Boolean) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    moreActions: MoreDialogActions,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    onMoreEpisodesClick: () -> Unit,
    onChooseSubtitlesClick: () -> Unit,
    onCastAndCrewClick: () -> Unit = {},
    collections: List<com.github.sysmoon.wholphin.ui.detail.CollectionRow>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var position by rememberInt()
    val focusRequesters = remember { List(DISCOVER_ROW + 1) { FocusRequester() } }
    val playFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(focusRequesters.getOrNull(position))
    LaunchedEffect(position) {
        if (position == HEADER_ROW) {
            focusRequesters[HEADER_ROW].tryRequestFocus()
        }
    }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }

    Box(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
            ) {
                SeriesDetailsHeader(
                    series = series,
                    overviewOnClick = overviewOnClick,
                    bringIntoViewRequester = bringIntoViewRequester,
                    showCastAndCrew = position == HEADER_ROW,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .padding(top = 32.dp, bottom = 16.dp),
                    seasonCount = seasons.size,
                )
                AnimatedVisibility(visible = position == HEADER_ROW) {
                DetailActionButtons(
                    playButtonLabel = playButtonLabel,
                    resumePosition = nextUpResumePosition,
                    onPlayClick = {
                        position = HEADER_ROW
                        playOnClick.invoke(false)
                    },
                    onChooseSubtitlesClick = onChooseSubtitlesClick,
                    favourite = favorite,
                    onFavouriteClick = favoriteOnClick,
                    onMoreClick = {
                        moreDialog =
                            DialogParams(
                                fromLongClick = false,
                                title = series.name ?: context.getString(R.string.unknown),
                                items =
                                    com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItems(
                                        context = context,
                                        item = series,
                                        seriesId = series.id,
                                        sourceId = null,
                                        watched = played,
                                        favorite = favorite,
                                        canClearChosenStreams = false,
                                        actions = moreActions,
                                        onChooseVersion = { },
                                        onChooseTracks = { },
                                        onShowOverview = overviewOnClick,
                                        onClearChosenStreams = { },
                                    ),
                            )
                    },
                    onPlayFocusChanged = {
                        if (it) {
                            position = HEADER_ROW
                            scope.launch(ExceptionHandler()) {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                    showMoreEpisodes = true,
                    onMoreEpisodesClick = onMoreEpisodesClick,
                    showShuffle = true,
                    onShuffleClick = { playOnClick(true) },
                    trailers = trailers,
                    onTrailerClick = trailerOnClick,
                    showCastAndCrew = people.isNotEmpty(),
                    onCastAndCrewClick = onCastAndCrewClick,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxWidth()
                            .focusRequester(focusRequesters[HEADER_ROW])
                            .focusRestorer(playFocusRequester)
                            .padding(bottom = 16.dp),
                )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.weight(1f, fill = true),
            ) {
                if (similar.isNotEmpty()) {
                    item(key = "more_like_this") {
                        ItemRow(
                            title = context.getString(R.string.more_like_this),
                            items = similar,
                            onClickItem = { index, item ->
                                position = CONTENT_ROW
                                onClickItem.invoke(index, item)
                            },
                            onLongClickItem = { index, item ->
                                position = CONTENT_ROW
                                val items =
                                    buildMoreDialogItemsForHome(
                                        context = context,
                                        item = item,
                                        seriesId = null,
                                        playbackPosition = item.playbackPosition,
                                        watched = item.played,
                                        favorite = item.favorite,
                                        actions = moreActions,
                                    )
                                moreDialog =
                                    DialogParams(
                                        fromLongClick = true,
                                        title = item.name ?: "",
                                        items = items,
                                    )
                            },
                            cardContent = { _, item, mod, onClick, onLongClick ->
                                SeasonCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    modifier =
                                        mod.onPreviewKeyEvent { event ->
                                            if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyUp) {
                                                position = HEADER_ROW
                                                return@onPreviewKeyEvent true
                                            }
                                            false
                                        },
                                    showImageOverlay = true,
                                    imageHeight = Cards.height2x3,
                                    imageWidth = Dp.Unspecified,
                                    showTitleAndSubtitle = false,
                                )
                            },
                            onFocusInRow = { position = CONTENT_ROW },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                items(
                    collections,
                    key = { it.collectionName },
                ) { collectionRow ->
                    ItemRow(
                        title = context.getString(R.string.more_from_collection, collectionRow.collectionName),
                        items = collectionRow.items,
                        onClickItem = { index, item ->
                            position = CONTENT_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, item ->
                            position = CONTENT_ROW
                            val items =
                                buildMoreDialogItemsForHome(
                                    context = context,
                                    item = item,
                                    seriesId = null,
                                    playbackPosition = item.playbackPosition,
                                    watched = item.played,
                                    favorite = item.favorite,
                                    actions = moreActions,
                                )
                            moreDialog =
                                DialogParams(
                                    fromLongClick = true,
                                    title = item.name ?: "",
                                    items = items,
                                )
                        },
                        cardContent = { _, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier =
                                    mod.onPreviewKeyEvent { event ->
                                        if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyUp) {
                                            position = HEADER_ROW
                                            return@onPreviewKeyEvent true
                                        }
                                        false
                                    },
                                showImageOverlay = true,
                                imageHeight = Cards.height2x3,
                                imageWidth = Dp.Unspecified,
                                showTitleAndSubtitle = false,
                            )
                        },
                        onFocusInRow = { position = CONTENT_ROW },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (extras.isNotEmpty()) {
                    item {
                        ExtrasRow(
                            extras = extras,
                            onClickItem = { index, item ->
                                position = EXTRAS_ROW
                                onClickExtra.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[EXTRAS_ROW])
                                    .onFocusChanged { if (it.hasFocus) position = EXTRAS_ROW },
                        )
                    }
                }
                if (discovered.isNotEmpty()) {
                    item {
                        DiscoverRow(
                            row =
                                DiscoverRowData(
                                    stringResource(R.string.discover),
                                    DataLoadingState.Success(discovered),
                                ),
                            onClickItem = { index: Int, item: DiscoverItem ->
                                position = DISCOVER_ROW
                                onClickDiscover.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            onCardFocus = { position = DISCOVER_ROW },
                            focusRequester = focusRequesters[DISCOVER_ROW],
                        )
                    }
                }
            }
        }
    }
    moreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { moreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
}

@Composable
fun SeriesDetailsHeader(
    series: BaseItem,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester,
    modifier: Modifier = Modifier,
    seasonCount: Int? = null,
    showCastAndCrew: Boolean = true,
) {
    com.github.sysmoon.wholphin.ui.detail.DetailInfoBlock(
        item = series,
        chosenStreams = null,
        bringIntoViewRequester = bringIntoViewRequester,
        overviewOnClick = overviewOnClick,
        modifier = modifier,
        seasonCount = seasonCount,
        showCastAndCrew = showCastAndCrew,
        showMediaPills = false,
        contentStartPadding = 24.dp,
    )
}

fun buildDialogForSeason(
    context: Context,
    s: BaseItem,
    onClickItem: (BaseItem) -> Unit,
    markPlayed: (Boolean) -> Unit,
    onClickPlay: (Boolean) -> Unit,
): DialogParams {
    val items =
        buildList {
            add(
                DialogItem(context.getString(R.string.go_to), Icons.Default.PlayArrow) {
                    onClickItem.invoke(s)
                },
            )
            if (s.data.userData?.played == true) {
                add(
                    DialogItem(context.getString(R.string.mark_unwatched), R.string.fa_eye) {
                        markPlayed.invoke(false)
                    },
                )
            } else {
                add(
                    DialogItem(context.getString(R.string.mark_watched), R.string.fa_eye_slash) {
                        markPlayed.invoke(true)
                    },
                )
            }
            add(
                DialogItem(
                    context.getString(R.string.play),
                    Icons.Default.PlayArrow,
                    iconColor = Color.Green.copy(alpha = .8f),
                ) {
                    onClickPlay.invoke(false)
                },
            )
            add(
                DialogItem(
                    context.getString(R.string.shuffle),
                    R.string.fa_shuffle,
                ) {
                    onClickPlay.invoke(true)
                },
            )
        }
    return DialogParams(
        title = s.name ?: context.getString(R.string.tv_season),
        fromLongClick = true,
        items = items,
    )
}
