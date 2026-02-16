package com.github.sysmoon.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.data.filter.DefaultForGenresFilterOptions
import com.github.sysmoon.wholphin.data.model.SeerrItemType
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.components.ItemGrid
import com.github.sysmoon.wholphin.ui.components.LicenseInfo
import com.github.sysmoon.wholphin.ui.data.MovieSortOptions
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderBoxSet
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderGeneric
import com.github.sysmoon.wholphin.ui.detail.castcrew.CastAndCrewPage
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderLiveTv
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderMovie
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderPlaylist
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderRecordings
import com.github.sysmoon.wholphin.ui.detail.CollectionFolderTv
import com.github.sysmoon.wholphin.ui.detail.DebugPage
import com.github.sysmoon.wholphin.ui.detail.FavoritesPage
import com.github.sysmoon.wholphin.ui.detail.PersonPage
import com.github.sysmoon.wholphin.ui.detail.PlaylistDetails
import com.github.sysmoon.wholphin.ui.detail.discover.DiscoverMovieDetails
import com.github.sysmoon.wholphin.ui.detail.discover.DiscoverPersonPage
import com.github.sysmoon.wholphin.ui.detail.discover.DiscoverSeriesDetails
import com.github.sysmoon.wholphin.ui.detail.episode.EpisodeDetails
import com.github.sysmoon.wholphin.ui.detail.movie.MovieDetails
import com.github.sysmoon.wholphin.ui.detail.series.SeriesDetails
import com.github.sysmoon.wholphin.ui.detail.series.SeriesOverview
import com.github.sysmoon.wholphin.ui.discover.DiscoverPage
import com.github.sysmoon.wholphin.ui.main.HomePage
import com.github.sysmoon.wholphin.ui.main.SearchPage
import com.github.sysmoon.wholphin.ui.playback.PlaybackPage
import com.github.sysmoon.wholphin.ui.preferences.PreferencesPage
import com.github.sysmoon.wholphin.ui.setup.InstallUpdatePage
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber

/**
 * Chose the page for the [Destination]
 */
@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    homeTopRowFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    skipContentFocusUntilMillis: StateFlow<Long>? = null,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
) {
    if (destination.fullScreen) {
        LaunchedEffect(Unit) { onClearBackdrop.invoke() }
    }
    when (destination) {
        is Destination.Home -> {
            HomePage(
                preferences = preferences,
                modifier = modifier,
                topRowFocusRequester = homeTopRowFocusRequester,
                skipContentFocusUntilMillis = skipContentFocusUntilMillis,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        is Destination.PlaybackList,
        is Destination.Playback,
        -> {
            PlaybackPage(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.Settings -> {
            val hideSettingsCog = preferences.appPreferences.interfacePreferences.hideSettingsCog
            LaunchedEffect(hideSettingsCog) {
                if (hideSettingsCog) {
                    onNavigateBack?.invoke()
                }
            }
            if (!hideSettingsCog) {
                PreferencesPage(
                    preferences.appPreferences,
                    destination.screen,
                    modifier,
                )
            }
        }

        is Destination.SeriesOverview -> {
            SeriesOverview(
                preferences = preferences,
                destination = destination,
                initialSeasonEpisode = destination.seasonEpisode,
                modifier = modifier,
            )
        }

        is Destination.MediaItem -> {
            when (destination.type) {
                BaseItemKind.SERIES -> {
                    SeriesDetails(
                        preferences,
                        destination,
                        autoPlayOnLoad = destination.autoPlayOnLoad,
                        modifier,
                    )
                }

                BaseItemKind.MOVIE -> {
                    MovieDetails(
                        preferences,
                        destination,
                        autoPlayOnLoad = destination.autoPlayOnLoad,
                        modifier,
                    )
                }

                BaseItemKind.VIDEO -> {
                    // TODO Use VideoDetails
                    MovieDetails(
                        preferences,
                        destination,
                        autoPlayOnLoad = destination.autoPlayOnLoad,
                        modifier,
                    )
                }

                BaseItemKind.EPISODE -> {
                    EpisodeDetails(
                        preferences,
                        destination,
                        autoPlayOnLoad = destination.autoPlayOnLoad,
                        modifier,
                    )
                }

                BaseItemKind.BOX_SET -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolderBoxSet(
                        preferences = preferences,
                        itemId = destination.itemId,
                        recursive = false,
                        playEnabled = true,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PLAYLIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PlaylistDetails(
                        destination = destination,
                        modifier = modifier,
                    )
                }

                BaseItemKind.COLLECTION_FOLDER -> {
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = null,
                        modifier = modifier,
                        skipContentFocusUntilMillis = skipContentFocusUntilMillis,
                        wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                        navHasFocus = navHasFocus,
                        onClearBackdrop = onClearBackdrop,
                    )
                }

                BaseItemKind.FOLDER -> {
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = true,
                        recursiveOverride = null,
                        modifier = modifier,
                        skipContentFocusUntilMillis = skipContentFocusUntilMillis,
                        wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                        navHasFocus = navHasFocus,
                        onClearBackdrop = onClearBackdrop,
                    )
                }

                BaseItemKind.USER_VIEW -> {
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = true,
                        modifier = modifier,
                        skipContentFocusUntilMillis = skipContentFocusUntilMillis,
                        wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                        navHasFocus = navHasFocus,
                        onClearBackdrop = onClearBackdrop,
                    )
                }

                BaseItemKind.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PersonPage(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                else -> {
                    Timber.w("Unsupported item type: ${destination.type}")
                    Text("Unsupported item type: ${destination.type}")
                }
            }
        }

        is Destination.CastAndCrew -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CastAndCrewPage(
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.FilteredCollection -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                filter = destination.filter,
                recursive = destination.recursive,
                usePosters = true,
                playEnabled = true, // TODO only genres use this currently, so might need to change in future
                filterOptions = DefaultForGenresFilterOptions,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        is Destination.Recordings -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderRecordings(
                preferences,
                destination.itemId,
                false,
                modifier,
            )
        }

        is Destination.ItemGrid -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            ItemGrid(
                destination,
                modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        Destination.Favorites -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            FavoritesPage(
                preferences = preferences,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        Destination.UpdateApp -> {
            InstallUpdatePage(preferences, modifier)
        }

        Destination.License -> {
            LicenseInfo(modifier)
        }

        Destination.Search -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            SearchPage(
                userPreferences = preferences,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        Destination.Debug -> {
            DebugPage(preferences, modifier)
        }

        Destination.Discover -> {
            DiscoverPage(
                preferences = preferences,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        is Destination.DiscoveredItem -> {
            when (destination.item.type) {
                SeerrItemType.MOVIE -> {
                    DiscoverMovieDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.TV -> {
                    DiscoverSeriesDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.PERSON -> {
                    DiscoverPersonPage(
                        person = destination.item,
                        modifier = modifier,
                    )
                }

                SeerrItemType.UNKNOWN -> {
                    Text(
                        text = "Unknown discover type",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionFolder(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    collectionType: CollectionType?,
    usePostersOverride: Boolean?,
    recursiveOverride: Boolean?,
    modifier: Modifier = Modifier,
    skipContentFocusUntilMillis: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    onClearBackdrop: () -> Unit = {},
) {
    when (collectionType) {
        CollectionType.TVSHOWS -> {
            CollectionFolderTv(
                preferences,
                destination,
                modifier,
                skipContentFocusUntilMillis,
                wasOpenedViaTopNavSwitch,
                navHasFocus,
            )
        }

        CollectionType.MOVIES -> {
            CollectionFolderMovie(
                preferences,
                destination,
                modifier,
                skipContentFocusUntilMillis,
                wasOpenedViaTopNavSwitch,
                navHasFocus,
            )
        }

        CollectionType.BOXSETS -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                usePosters = true,
                recursive = false,
                playEnabled = false,
                modifier = modifier,
                sortOptions = MovieSortOptions,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        CollectionType.PLAYLISTS -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderPlaylist(
                preferences,
                destination.itemId,
                true,
                modifier,
            )
        }

        CollectionType.LIVETV -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderLiveTv(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        CollectionType.HOMEVIDEOS,
        CollectionType.MUSICVIDEOS,
        CollectionType.MUSIC,
        CollectionType.BOOKS,
        CollectionType.PHOTOS,
        -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = true,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }

        CollectionType.FOLDERS,
        CollectionType.TRAILERS,
        CollectionType.UNKNOWN,
        null,
        -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = false,
                modifier = modifier,
                wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                navHasFocus = navHasFocus,
            )
        }
    }
}
