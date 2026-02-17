package com.github.sysmoon.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.ui.data.RowColumn
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.ui.OneTimeLaunchedEffect
import com.github.sysmoon.wholphin.ui.data.AddPlaylistViewModel
import com.github.sysmoon.wholphin.ui.detail.MoreDialogActions
import com.github.sysmoon.wholphin.ui.detail.PlaylistDialog
import com.github.sysmoon.wholphin.ui.detail.PlaylistLoadingState
import com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.main.HomePageContent
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.util.ApiRequestPager
import com.github.sysmoon.wholphin.util.HomeRowLoadingState
import com.github.sysmoon.wholphin.util.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

abstract class RecommendedViewModel(
    val context: Context,
    val navigationManager: NavigationManager,
    val favoriteWatchManager: FavoriteWatchManager,
    private val backdropService: BackdropService,
) : ViewModel() {
    abstract fun init()

    abstract val rows: MutableStateFlow<List<HomeRowLoadingState>>

    val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

    /** When non-null, recommended content should restore focus to this position (e.g. after back from details). Consumed once applied. */
    val savedPositionToRestore = MutableLiveData<RowColumn?>(null)

    /** Call before navigating to a details screen so we can restore this position when the user presses back. */
    fun savePositionForRestore(position: RowColumn) {
        savedPositionToRestore.value = position
    }

    fun clearSavedPositionToRestore() {
        savedPositionToRestore.value = null
    }

    /** Called when returning to this screen and we have a position persisted in NavigationManager. */
    fun restorePositionFromNavigation(position: RowColumn) {
        savedPositionToRestore.value = position
    }

    fun refreshItem(
        position: RowColumn,
        itemId: UUID,
    ) {
        viewModelScope.launchIO {
            val row = rows.value.getOrNull(position.row)
            if (row is HomeRowLoadingState.Success) {
                (row.items as? ApiRequestPager<*>)?.refreshItem(position.column, itemId)
            }
        }
    }

    fun setWatched(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setWatched(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun setFavorite(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setFavorite(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun updateBackdrop(item: BaseItem) {
        viewModelScope.launchIO {
            backdropService.submit(item)
        }
    }

    abstract fun update(
        @StringRes title: Int,
        row: HomeRowLoadingState,
    )

    fun update(
        @StringRes title: Int,
        block: suspend () -> List<BaseItem>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val titleStr = context.getString(title)
            val row =
                try {
                    HomeRowLoadingState.Success(titleStr, block.invoke())
                } catch (ex: Exception) {
                    HomeRowLoadingState.Error(titleStr, null, ex)
                }
            update(title, row)
        }
    }
}

@Composable
fun RecommendedContent(
    preferences: UserPreferences,
    viewModel: RecommendedViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    onFocusPosition: ((RowColumn) -> Unit)? = null,
    resetPositionOnEnter: Boolean = false,
    topRowFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    consumeDownToTopRow: Boolean = false,
    dropEmptyRows: Boolean = false,
    skipContentFocusUntilMillis: StateFlow<Long>? = null,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    libraryId: UUID? = null,
) {
    val context = LocalContext.current
    var moreDialog by remember { mutableStateOf<Optional<RowColumnItem>>(Optional.absent()) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val savedPositionToRestore by viewModel.savedPositionToRestore.observeAsState()
    val rows by viewModel.rows.collectAsState()
    // Only show Success and Error rows so we never flash "row header + Loading..." placeholders.
    // Rows appear as they load instead of showing all headers with Loading at once.
    val effectiveRows =
        rows
            .filter { row ->
                row is HomeRowLoadingState.Success || row is HomeRowLoadingState.Error
            }
            .let { filtered ->
                if (dropEmptyRows) {
                    filtered.filterNot { row ->
                        row is HomeRowLoadingState.Success && row.items.isEmpty()
                    }
                } else {
                    filtered
                }
            }
    val resetKey = rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(resetPositionOnEnter) {
        if (resetPositionOnEnter) {
            resetKey.value += 1
        }
    }

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(focusEnabled = false)
        }

        LoadingState.Success -> {
            val focusEntryModifier =
                if (topRowFocusRequester != null) {
                    Modifier
                        .focusGroup()
                        .focusProperties { onEnter = { topRowFocusRequester } }
                } else {
                    Modifier
                }
            var consumeNextDown by remember { mutableStateOf(true) }
            val focusGateModifier =
                if (consumeDownToTopRow && topRowFocusRequester != null) {
                    Modifier.onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            consumeNextDown = true
                        }
                    }
                } else {
                    Modifier
                }
            val downKeyModifier =
                if (consumeDownToTopRow && topRowFocusRequester != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionDown) {
                            if (consumeNextDown) {
                                if (event.type == KeyEventType.KeyUp) {
                                    topRowFocusRequester.tryRequestFocus("recommended_down_to_top_row")
                                }
                                consumeNextDown = false
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                }
            key(resetKey.value) {
                HomePageContent(
                    homeRows = effectiveRows,
                    skipContentFocusUntilMillis = skipContentFocusUntilMillis,
                    wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                    navHasFocus = navHasFocus,
                    savedPositionToRestore = savedPositionToRestore,
                    onConsumeRestoredPosition = viewModel::clearSavedPositionToRestore,
                    onClickItem = { position, item ->
                        viewModel.savePositionForRestore(position)
                        libraryId?.let { viewModel.navigationManager.setSavedRecommendedPositionForLibrary(it, position) }
                        viewModel.updateBackdrop(item)
                        viewModel.navigationManager.navigateTo(item.destination())
                    },
                    onLongClickItem = { position, item ->
                        moreDialog.makePresent(RowColumnItem(position, item))
                    },
                    onClickPlay = { _, item ->
                        viewModel.navigationManager.navigateTo(Destination.Playback(item))
                    },
                    onFocusPosition = onFocusPosition,
                    showClock = preferences.appPreferences.interfacePreferences.showClock,
                    onUpdateBackdrop = viewModel::updateBackdrop,
                    modifier =
                        modifier
                            .then(focusEntryModifier)
                            .then(focusGateModifier)
                            .then(downKeyModifier)
                            .fillMaxSize(),
                    resetPositionOnEnter = resetPositionOnEnter,
                    topRowFocusRequester = topRowFocusRequester,
                )
            }
        }
    }
    moreDialog.compose { (position, item) ->
        DialogPopup(
            showDialog = true,
            title = item.title ?: "",
            dialogItems =
                buildMoreDialogItemsForHome(
                    context = context,
                    item = item,
                    seriesId = null,
                    playbackPosition = item.playbackPosition,
                    watched = item.played,
                    favorite = item.favorite,
                    actions =
                        MoreDialogActions(
                            navigateTo = { viewModel.navigationManager.navigateTo(it) },
                            onClickWatch = { itemId, watched ->
                                viewModel.setWatched(position, itemId, watched)
                            },
                            onClickFavorite = { itemId, watched ->
                                viewModel.setFavorite(position, itemId, watched)
                            },
                            onClickAddPlaylist = {
                                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                showPlaylistDialog.makePresent(it)
                            },
                        ),
                ),
            onDismissRequest = { moreDialog.makeAbsent() },
            dismissOnClick = true,
            waitToLoad = true,
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

private data class RowColumnItem(
    val position: RowColumn,
    val item: BaseItem,
)
