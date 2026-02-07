@file:UseSerializers(UUIDSerializer::class)

package com.github.sysmoon.wholphin.ui.detail.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.RequestOrRestoreFocus
import com.github.sysmoon.wholphin.ui.components.DialogParams
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.LoadingPage
import com.github.sysmoon.wholphin.ui.components.chooseStream
import com.github.sysmoon.wholphin.ui.components.chooseVersionParams
import com.github.sysmoon.wholphin.ui.data.AddPlaylistViewModel
import com.github.sysmoon.wholphin.ui.data.ItemDetailsDialog
import com.github.sysmoon.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.sysmoon.wholphin.ui.detail.MoreDialogActions
import com.github.sysmoon.wholphin.ui.detail.PlaylistDialog
import com.github.sysmoon.wholphin.ui.detail.PlaylistLoadingState
import com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItems
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.seasonEpisode
import com.github.sysmoon.wholphin.util.LoadingState
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID
import kotlin.time.Duration

@Serializable
data class SeasonEpisode(
    val season: Int,
    val episode: Int,
)

@Serializable
data class SeasonEpisodeIds(
    val seasonId: UUID,
    val seasonNumber: Int?,
    val episodeId: UUID?,
    val episodeNumber: Int?,
)

@Serializable
data class SeriesOverviewPosition(
    val seasonTabIndex: Int,
    val episodeRowIndex: Int,
)

@Composable
fun SeriesOverview(
    preferences: UserPreferences,
    destination: Destination.SeriesOverview,
    initialSeasonEpisode: SeasonEpisodeIds?,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, initialSeasonEpisode, SeriesPageType.OVERVIEW)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }

    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val series by viewModel.item.observeAsState(null)
    val seasons by viewModel.seasons.observeAsState(listOf())
    val episodes by viewModel.episodes.observeAsState(EpisodeList.Loading)
    val episodeList = (episodes as? EpisodeList.Success)?.episodes

    val position by viewModel.position.collectAsState(SeriesOverviewPosition(0, 0))
    LaunchedEffect(Unit) {
        if (seasons.isNotEmpty()) {
            seasons.getOrNull(position.seasonTabIndex)?.let {
                viewModel.loadEpisodes(it.id)
            }
        }
    }

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    LaunchedEffect(episodes) {
        episodes?.let { episodes ->
            if (episodes is EpisodeList.Success) {
                if (episodes.episodes.isNotEmpty()) {
                    // TODO focus on first episode when changing seasons?
//            firstItemFocusRequester.requestFocus()
                    episodes.episodes.getOrNull(position.episodeRowIndex)?.let {
                        viewModel.refreshEpisode(it.id, position.episodeRowIndex)
                    }
                }
            }
        }
    }

    LaunchedEffect(position, episodes) {
        val focusedEpisode =
            (episodes as? EpisodeList.Success)
                ?.episodes
                ?.getOrNull(position.episodeRowIndex)

        focusedEpisode?.let {
            viewModel.lookUpChosenTracks(it.id, it)
            viewModel.lookupPeopleInEpisode(it)
        }
    }
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)

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
            series?.let { series ->

                RequestOrRestoreFocus(firstItemFocusRequester, "series_overview")
                LifecycleResumeEffect(destination.itemId) {
                    viewModel.onResumePage()

                    onPauseOrDispose {
                        viewModel.release()
                    }
                }

                fun buildMoreForEpisode(
                    ep: BaseItem,
                    chosenStreams: ChosenStreams?,
                    fromLongClick: Boolean,
                ): DialogParams =
                    DialogParams(
                        fromLongClick = fromLongClick,
                        title = series.name + " - " + ep.data.seasonEpisode,
                        items =
                            buildMoreDialogItems(
                                context = context,
                                item = ep,
                                watched = ep.data.userData?.played ?: false,
                                favorite = ep.data.userData?.isFavorite ?: false,
                                seriesId = series.id,
                                sourceId = chosenStreams?.source?.id?.toUUIDOrNull(),
                                canClearChosenStreams = chosenStreams?.itemPlayback != null || chosenStreams?.plc != null,
                                actions =
                                    MoreDialogActions(
                                        navigateTo = viewModel::navigateTo,
                                        onClickWatch = { itemId, watched ->
                                            viewModel.setWatched(
                                                itemId,
                                                watched,
                                                position.episodeRowIndex,
                                            )
                                        },
                                        onClickFavorite = { itemId, favorite ->
                                            viewModel.setFavorite(
                                                itemId,
                                                favorite,
                                                position.episodeRowIndex,
                                            )
                                        },
                                        onClickAddPlaylist = {
                                            playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                            showPlaylistDialog = it
                                        },
                                    ),
                                onChooseVersion = {
                                    chooseVersion =
                                        chooseVersionParams(
                                            context,
                                            ep.data.mediaSources!!,
                                        ) { idx ->
                                            val source = ep.data.mediaSources!![idx]
                                            viewModel.savePlayVersion(
                                                ep,
                                                source.id!!.toUUID(),
                                            )
                                        }
                                    moreDialog = null
                                },
                                onChooseTracks = { type ->
                                    viewModel.streamChoiceService
                                        .chooseSource(
                                            ep.data,
                                            chosenStreams?.itemPlayback,
                                        )?.let { source ->
                                            chooseVersion =
                                                chooseStream(
                                                    context = context,
                                                    streams = source.mediaStreams.orEmpty(),
                                                    type = type,
                                                    currentIndex =
                                                        if (type == MediaStreamType.AUDIO) {
                                                            chosenStreams?.audioStream?.index
                                                        } else {
                                                            chosenStreams?.subtitleStream?.index
                                                        },
                                                    onClick = { trackIndex ->
                                                        viewModel.saveTrackSelection(
                                                            ep,
                                                            chosenStreams?.itemPlayback,
                                                            trackIndex,
                                                            type,
                                                        )
                                                    },
                                                )
                                        }
                                },
                                onShowOverview = {
                                    overviewDialog =
                                        ItemDetailsDialogInfo(
                                            title = ep.name ?: context.getString(R.string.unknown),
                                            overview = ep.data.overview,
                                            genres = ep.data.genres.orEmpty(),
                                            files = ep.data.mediaSources.orEmpty(),
                                        )
                                },
                                onClearChosenStreams = {
                                    viewModel.clearChosenStreams(ep, chosenStreams)
                                },
                            ),
                    )

                SeriesOverviewContent(
                    preferences = preferences,
                    series = series,
                    seasons = seasons,
                    episodes = episodes,
                    chosenStreams = chosenStreams,
                    position = position,
                    firstItemFocusRequester = firstItemFocusRequester,
                    onChangeSeason = { index ->
                        if (index != position.seasonTabIndex) {
                            seasons.getOrNull(index)?.let { season ->
                                viewModel.loadEpisodes(season.id)
                                viewModel.position.update {
                                    SeriesOverviewPosition(index, 0)
                                }
                            }
                        }
                    },
                    onFocusEpisode = { episodeIndex ->
                        viewModel.position.update {
                            it.copy(episodeRowIndex = episodeIndex)
                        }
                    },
                    onSelectNextEpisode = {
                        when (val e = episodes) {
                            is EpisodeList.Success ->
                                viewModel.position.update {
                                    it.copy(
                                        episodeRowIndex = (it.episodeRowIndex + 1)
                                            .coerceAtMost(e.episodes.size - 1),
                                    )
                                }
                            else -> { }
                        }
                    },
                    onSelectPreviousEpisode = {
                        viewModel.position.update {
                            it.copy(
                                episodeRowIndex = (it.episodeRowIndex - 1).coerceAtLeast(0),
                            )
                        }
                    },
                    onClick = {
                        val resumePosition =
                            it.data.userData
                                ?.playbackPositionTicks
                                ?.ticks ?: Duration.ZERO
                        viewModel.navigateTo(
                            Destination.Playback(
                                it.id,
                                resumePosition.inWholeMilliseconds,
                            ),
                        )
                    },
                    onLongClick = { ep ->
                        moreDialog = buildMoreForEpisode(ep, chosenStreams, true)
                    },
                    modifier = modifier,
                )
            }
        }
    }

    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                viewModel.serverRepository.currentUserDto.value
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
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
    chooseVersion?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { chooseVersion = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
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
