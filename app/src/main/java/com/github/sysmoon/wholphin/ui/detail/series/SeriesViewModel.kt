package com.github.sysmoon.wholphin.ui.detail.series

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.ExtrasItem
import com.github.sysmoon.wholphin.data.ItemPlaybackRepository
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.data.model.DiscoverItem
import com.github.sysmoon.wholphin.data.model.ItemPlayback
import com.github.sysmoon.wholphin.data.model.Person
import com.github.sysmoon.wholphin.data.model.Trailer
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.ExtrasService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.PeopleFavorites
import com.github.sysmoon.wholphin.services.SeerrService
import com.github.sysmoon.wholphin.services.StreamChoiceService
import com.github.sysmoon.wholphin.services.ThemeSongPlayer
import com.github.sysmoon.wholphin.services.TrailerService
import com.github.sysmoon.wholphin.services.UserPreferencesService
import com.github.sysmoon.wholphin.ui.SlimItemFields
import com.github.sysmoon.wholphin.ui.detail.CollectionRow
import com.github.sysmoon.wholphin.ui.detail.ItemViewModel
import com.github.sysmoon.wholphin.ui.equalsNotNull
import com.github.sysmoon.wholphin.ui.gt
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.letNotEmpty
import com.github.sysmoon.wholphin.ui.lt
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.sysmoon.wholphin.ui.detail.series.SeriesOverviewPosition
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.ui.showToast
import com.github.sysmoon.wholphin.util.ApiRequestPager
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.GetEpisodesRequestHandler
import com.github.sysmoon.wholphin.util.GetItemsRequestHandler
import com.github.sysmoon.wholphin.util.LoadingExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = SeriesViewModel.Factory::class)
class SeriesViewModel
    @AssistedInject
    constructor(
        api: ApiClient,
        @param:ApplicationContext val context: Context,
        val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        private val themeSongPlayer: ThemeSongPlayer,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val peopleFavorites: PeopleFavorites,
        private val trailerService: TrailerService,
        private val extrasService: ExtrasService,
        val streamChoiceService: StreamChoiceService,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val seerrService: SeerrService,
        private val savedStateHandle: SavedStateHandle,
        @Assisted val seriesId: UUID,
        @Assisted val seasonEpisodeIds: SeasonEpisodeIds?,
        @Assisted val seriesPageType: SeriesPageType,
    ) : ItemViewModel(api) {
        @AssistedFactory
        interface Factory {
            fun create(
                seriesId: UUID,
                seasonEpisodeIds: SeasonEpisodeIds?,
                seriesPageType: SeriesPageType,
            ): SeriesViewModel
        }

        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<List<BaseItem?>>(listOf())
        val episodes = MutableLiveData<EpisodeList>(EpisodeList.Loading)

        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val extras = MutableLiveData<List<ExtrasItem>>(listOf())
        val people = MutableLiveData<List<Person>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()

        val peopleInEpisode = MutableLiveData<PeopleInItem>(PeopleInItem())
        val discovered = MutableStateFlow<List<DiscoverItem>>(listOf())
        /** Next-up episode for this series, used for "Play Season X Episode Y" button label. */
        val nextUpEpisode = MutableLiveData<BaseItem?>(null)
        val collections = MutableLiveData<List<CollectionRow>>(listOf())

        val position = MutableStateFlow(SeriesOverviewPosition(0, 0))

        init {
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading series $seriesId",
                ) + Dispatchers.IO,
            ) {
                Timber.v("Start")
                addCloseable { themeSongPlayer.stop() }
                val item = fetchItem(seriesId)
                backdropService.submit(item)

                val seasonsDeferred = getSeasons(item, seasonEpisodeIds?.seasonNumber)

                val seasons = seasonsDeferred.await()
                var initialSeasonIndex =
                    if (seriesPageType == SeriesPageType.OVERVIEW && seasonEpisodeIds != null) {
                        (seasons as? ApiRequestPager<*>)?.let {
                            findIndexOf(
                                seasonEpisodeIds.seasonNumber,
                                seasonEpisodeIds.seasonId,
                                it,
                            )
                        }?.coerceAtLeast(0) ?: 0
                    } else {
                        0
                    }
                var initialEpisodeIndex = 0
                // Restore position from saved state when returning from playback (e.g. overview
                // opened from "More Episodes" with no specific episode, then user played an episode)
                if (
                    seriesPageType == SeriesPageType.OVERVIEW &&
                    seasonEpisodeIds == null
                ) {
                    savedStateHandle.get<Int>("seasonIndex")?.let { savedSeason ->
                        savedStateHandle.get<Int>("episodeIndex")?.let { savedEpisode ->
                            initialSeasonIndex = savedSeason.coerceAtLeast(0)
                            initialEpisodeIndex = savedEpisode.coerceAtLeast(0)
                        }
                    }
                }
                val episodes =
                    if (seriesPageType == SeriesPageType.OVERVIEW) {
                        if (seasonEpisodeIds != null) {
                            loadEpisodesInternal(
                                seasonEpisodeIds.seasonId,
                                seasonEpisodeIds.episodeId,
                                seasonEpisodeIds.episodeNumber,
                            )
                        } else {
                            val seasonIndex = initialSeasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
                            seasons.getOrNull(seasonIndex)?.let { season ->
                                loadEpisodesInternal(season.id, null, null)
                            } ?: EpisodeList.Error(message = "Could not determine season")
                        }
                    } else {
                        EpisodeList.Loading
                    }
                if (episodes is EpisodeList.Success && initialEpisodeIndex == 0) {
                    initialEpisodeIndex = episodes.initialEpisodeIndex
                }
                Timber.v("Done: initial season index: $initialSeasonIndex, episode index: $initialEpisodeIndex")

                val remoteTrailers = trailerService.getRemoteTrailers(item)
                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.trailers.value = remoteTrailers
                    this@SeriesViewModel.position.update {
                        it.copy(
                            seasonTabIndex = initialSeasonIndex,
                            episodeRowIndex = initialEpisodeIndex,
                        )
                    }
                    this@SeriesViewModel.seasons.value = seasons
                    this@SeriesViewModel.episodes.value = episodes
                    loading.value = LoadingState.Success
                }
                if (seriesPageType == SeriesPageType.OVERVIEW) {
                    viewModelScope.launch {
                        position.collect { pos ->
                            savedStateHandle["seasonIndex"] = pos.seasonTabIndex
                            savedStateHandle["episodeIndex"] = pos.episodeRowIndex
                        }
                    }
                }
                if (seriesPageType == SeriesPageType.DETAILS) {
                    viewModelScope.launchIO {
                        val similar =
                            api.libraryApi
                                .getSimilarItems(
                                    GetSimilarItemsRequest(
                                        userId = serverRepository.currentUser.value?.id,
                                        itemId = seriesId,
                                        fields = SlimItemFields,
                                        limit = 25,
                                    ),
                                ).content.items
                                .map { BaseItem.from(it, api, true) }
                        this@SeriesViewModel.similar.setValueOnMain(similar)
                    }
                    viewModelScope.launchIO {
                        val nextUpResult = api.tvShowsApi.getNextUp(seriesId = seriesId).content.items.firstOrNull()
                            ?: api.tvShowsApi.getEpisodes(seriesId, limit = 1).content.items.firstOrNull()
                        nextUpResult?.let { dto ->
                            try {
                                val nextUp = BaseItem.from(dto, api, useSeriesForPrimary = true)
                                this@SeriesViewModel.nextUpEpisode.setValueOnMain(nextUp)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to convert next-up episode to BaseItem")
                            }
                        }
                    }
                    viewModelScope.launchIO {
                        trailerService.getLocalTrailers(item).letNotEmpty { localTrailers ->
                            withContext(Dispatchers.Main) {
                                this@SeriesViewModel.trailers.value = localTrailers + remoteTrailers
                            }
                        }
                    }
                    viewModelScope.launchIO {
                        val people = peopleFavorites.getPeopleFor(item)
                        this@SeriesViewModel.people.setValueOnMain(people)
                    }
                    viewModelScope.launchIO {
                        val extras = extrasService.getExtras(item.id)
                        this@SeriesViewModel.extras.setValueOnMain(extras)
                    }
                    viewModelScope.launchIO {
                        val results = seerrService.similar(item).orEmpty()
                        discovered.update { results }
                    }
                    loadParentCollection(item)
                }
            }
        }

        private fun loadParentCollection(item: BaseItem) {
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                val userId = serverRepository.currentUser.value?.id ?: return@launch
                try {
                    // 1) Try parentId when item is directly under a Box Set
                    val parentId = item.data.parentId
                    if (parentId != null) {
                        val parentDto = api.userLibraryApi.getItem(parentId).content
                        if (parentDto.type == BaseItemKind.BOX_SET) {
                            val request =
                                GetItemsRequest(
                                    userId = userId,
                                    parentId = parentId,
                                    excludeItemIds = listOf(item.id),
                                    fields = SlimItemFields,
                                    recursive = true,
                                    enableUserData = true,
                                    startIndex = 0,
                                    limit = 20,
                                )
                            val items =
                                api.itemsApi.getItems(request).content.items.mapNotNull { dto ->
                                    try {
                                        BaseItem.from(dto, api, true)
                                    } catch (e: Exception) {
                                        Timber.w(e, "Failed to parse collection item")
                                        null
                                    }
                                }
                                    .filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.SERIES }
                            if (items.isNotEmpty()) {
                                val name = parentDto.name ?: context.getString(com.github.sysmoon.wholphin.R.string.collection)
                                this@SeriesViewModel.collections.setValueOnMain(
                                    listOf(CollectionRow(name, items)),
                                )
                                return@launch
                            }
                        }
                    }
                    // 2) Fallback: find Box Sets that contain this item (e.g. when parentId is not set)
                    val boxSetsRequest =
                        GetItemsRequest(
                            userId = userId,
                            parentId = null,
                            includeItemTypes = listOf(BaseItemKind.BOX_SET),
                            fields = SlimItemFields,
                            recursive = true,
                            enableUserData = false,
                            startIndex = 0,
                            limit = 40,
                        )
                    val boxSetDtos = api.itemsApi.getItems(boxSetsRequest).content.items
                    val collectionRows = mutableListOf<CollectionRow>()
                    for (boxSetDto in boxSetDtos) {
                        val boxSetId = boxSetDto.id
                        val childRequest =
                            GetItemsRequest(
                                userId = userId,
                                parentId = boxSetId,
                                fields = SlimItemFields,
                                recursive = true,
                                enableUserData = true,
                                startIndex = 0,
                                limit = 100,
                            )
                        val children = api.itemsApi.getItems(childRequest).content.items
                        val containsItem = children.any { it.id == item.id }
                        if (containsItem) {
                            val siblings =
                                children
                                    .filter { it.id != item.id }
                                    .mapNotNull { dto ->
                                        try {
                                            BaseItem.from(dto, api, true)
                                        } catch (e: Exception) {
                                            Timber.w(e, "Failed to parse collection item")
                                            null
                                        }
                                    }
                                    .filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.SERIES }
                            if (siblings.isNotEmpty()) {
                                val name = boxSetDto.name ?: context.getString(com.github.sysmoon.wholphin.R.string.collection)
                                collectionRows.add(CollectionRow(name, siblings))
                            }
                        }
                    }
                    if (collectionRows.isNotEmpty()) {
                        this@SeriesViewModel.collections.setValueOnMain(collectionRows)
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Could not load parent collection for series ${item.id}")
                }
            }
        }

        fun onResumePage() {
            viewModelScope.launchIO {
                item.value?.let {
                    backdropService.submit(it)
                    val playThemeSongs =
                        userPreferencesService
                            .getCurrent()
                            .appPreferences.interfacePreferences.playThemeSongs
                    themeSongPlayer.playThemeFor(seriesId, playThemeSongs)
                }
            }
        }

        fun release() {
            themeSongPlayer.stop()
        }

        private fun getSeasons(
            series: BaseItem,
            seasonNum: Int?,
        ): Deferred<List<BaseItem?>> =
            viewModelScope.async(Dispatchers.IO) {
                val request =
                    GetItemsRequest(
                        parentId = series.id,
                        recursive = false,
                        includeItemTypes = listOf(BaseItemKind.SEASON),
                        sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        fields =
                            when (seriesPageType) {
                                SeriesPageType.DETAILS ->
                                    listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
                                SeriesPageType.OVERVIEW ->
                                    listOf(
                                        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                        ItemFields.CHILD_COUNT,
                                    )
                                else -> null
                            },
                    )
                val pager =
                    ApiRequestPager(
                        api,
                        request,
                        GetItemsRequestHandler,
                        viewModelScope,
                        pageSize = 10,
                    ).init(seasonNum ?: 0)
//                val seasons =
//                    GetItemsRequestHandler.execute(api, request).content.items.map {
                pager
            }

        private suspend fun loadEpisodesInternal(
            seasonId: UUID,
            episodeId: UUID?,
            episodeNumber: Int?,
        ): EpisodeList {
            val request =
                GetEpisodesRequest(
                    seriesId = seriesId,
                    seasonId = seasonId,
                    sortBy = ItemSortBy.INDEX_NUMBER,
                    fields =
                        listOf(
                            ItemFields.MEDIA_SOURCES,
                            ItemFields.MEDIA_SOURCE_COUNT,
                            ItemFields.MEDIA_STREAMS,
                            ItemFields.OVERVIEW,
                            ItemFields.CUSTOM_RATING,
                            ItemFields.TRICKPLAY,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                            ItemFields.PEOPLE,
                        ),
                )
            val pager = ApiRequestPager(api, request, GetEpisodesRequestHandler, viewModelScope)
            pager.init(episodeNumber ?: 0)
            val initialIndex =
                if (episodeId != null || episodeNumber != null) {
                    findIndexOf(episodeNumber, episodeId, pager)
                        .coerceAtLeast(0)
                } else {
                    // Force the first page to to be fetched
                    if (pager.isNotEmpty()) {
                        pager.getBlocking(0)
                    }
                    0
                }
            Timber.v("Loaded ${pager.size} episodes for season $seasonId, initialIndex=$initialIndex")
            return EpisodeList.Success(seasonId, pager, initialIndex)
        }

        fun loadEpisodes(seasonId: UUID) {
            val currentEpisodes = (this@SeriesViewModel.episodes.value as? EpisodeList.Success)
            if (currentEpisodes == null || currentEpisodes.seasonId != seasonId) {
                this@SeriesViewModel.peopleInEpisode.value = PeopleInItem()
                this@SeriesViewModel.episodes.value = EpisodeList.Loading
            }
            viewModelScope.launchIO(ExceptionHandler(true)) {
                val episodes =
                    try {
                        loadEpisodesInternal(seasonId, null, null)
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading episodes for $seriesId for season $seasonId")
                        EpisodeList.Error(e)
                    }
                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.episodes.value = episodes
                }
                if (currentEpisodes == null || currentEpisodes.seasonId != seasonId) {
                    (episodes as? EpisodeList.Success)
                        ?.let {
                            it.episodes.getOrNull(it.initialEpisodeIndex)
                        }?.let { lookupPeopleInEpisode(it) }
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            favoriteWatchManager.setWatched(itemId, played)
            listIndex?.let {
                refreshEpisode(itemId, listIndex)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            if (listIndex != null) {
                refreshEpisode(itemId, listIndex)
            } else {
                val item = fetchItem(seriesId)
                viewModelScope.launchIO {
                    val people = peopleFavorites.getPeopleFor(item)
                    this@SeriesViewModel.people.setValueOnMain(people)
                }
            }
        }

        fun setSeasonWatched(
            seasonId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            setWatched(seasonId, played, null)
            val series = fetchItem(seriesId)
            val seasons = getSeasons(series, null).await()
            this@SeriesViewModel.seasons.setValueOnMain(seasons)
        }

        fun setWatchedSeries(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                favoriteWatchManager.setWatched(seriesId, played)
                val series = fetchItem(seriesId)
                val seasons = getSeasons(series, null).await()
                this@SeriesViewModel.seasons.setValueOnMain(seasons)
            }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            val eps = episodes.value
            if (eps is EpisodeList.Success) {
                eps.episodes.refreshItem(listIndex, itemId)
                withContext(Dispatchers.Main) {
                    episodes.value = eps
                }
            }
            // Kind of hack to ensure the backdrop is reloaded if needed
            item.value?.let { backdropService.submit(it) }
        }

        /**
         * Play whichever episode is next up for series or else the first episode.
         * @param startPositionMs Start position in milliseconds (0 for play from start).
         */
        fun playNextUp(startPositionMs: Long = 0L) {
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                val result by api.tvShowsApi.getNextUp(seriesId = seriesId)
                val nextUp =
                    result.items.firstOrNull() ?: api.tvShowsApi
                        .getEpisodes(
                            seriesId,
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                if (nextUp != null) {
                    withContext(Dispatchers.Main) {
                        navigateTo(Destination.Playback(nextUp.id, startPositionMs))
                    }
                } else {
                    showToast(
                        context,
                        "Could not find an episode to play",
                        Toast.LENGTH_SHORT,
                    )
                }
            }
        }

        fun navigateTo(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        val chosenStreams = MutableLiveData<ChosenStreams?>(null)
        private var chosenStreamsJob: Job? = null

        fun lookUpChosenTracks(
            itemId: UUID,
            item: BaseItem,
        ) {
            chosenStreamsJob?.cancel()
            chosenStreamsJob =
                viewModelScope.launchIO {
                    val result =
                        itemPlaybackRepository.getSelectedTracks(
                            itemId,
                            item,
                            userPreferencesService.getCurrent(),
                        )
                    withContext(Dispatchers.Main) {
                        chosenStreams.value = result
                    }
                }
        }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                withContext(Dispatchers.Main) {
                    chosenStreams.value = chosen
                }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen =
                    result.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                withContext(Dispatchers.Main) {
                    chosenStreams.value = chosen
                }
            }
        }

        private var peopleInEpisodeJob: Job? = null

        suspend fun lookupPeopleInEpisode(item: BaseItem) {
            peopleInEpisodeJob?.cancel()
            if (peopleInEpisode.value?.itemId != item.id) {
                peopleInEpisode.setValueOnMain(PeopleInItem())
                peopleInEpisodeJob =
                    viewModelScope.launch(ExceptionHandler()) {
                        delay(250)
                        val people =
                            item.data.people
                                ?.letNotEmpty { it.map { Person.fromDto(it, api) } }
                                .orEmpty()
                        peopleInEpisode.setValueOnMain(PeopleInItem(item.id, people))
                    }
            }
        }

        fun clearChosenStreams(
            item: BaseItem,
            chosenStreams: ChosenStreams?,
        ) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                lookUpChosenTracks(item.id, item)
            }
        }
    }

sealed interface EpisodeList {
    data object Loading : EpisodeList

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : EpisodeList {
        constructor(exception: Throwable) : this(null, exception)
    }

    data class Success(
        val seasonId: UUID,
        val episodes: ApiRequestPager<GetEpisodesRequest>,
        val initialEpisodeIndex: Int,
    ) : EpisodeList
}

data class PeopleInItem(
    val itemId: UUID? = null,
    val people: List<Person> = listOf(),
)

enum class SeriesPageType {
    DETAILS,
    OVERVIEW,
}

private suspend fun findIndexOf(
    targetNum: Int?,
    targetId: UUID?,
    pager: ApiRequestPager<*>,
): Int {
    // Fast path: seasons are typically 1-based (Season 1 at index 0), so try index = targetNum - 1 first.
    // This avoids loading every page when opening e.g. Season 37.
    if (targetNum != null && targetNum >= 1) {
        val candidateIndex = (targetNum - 1).coerceIn(0, pager.size - 1)
        if (candidateIndex in pager.indices) {
            val item = pager.getBlocking(candidateIndex)
            if (item != null && (equalsNotNull(item.indexNumber, targetNum) || equalsNotNull(item.id, targetId))) {
                return candidateIndex
            }
        }
    }
    val index =
        if (targetId != null && (targetNum == null || targetNum !in pager.indices)) {
            // No hint info, so have to check everything
            pager.indexOfBlocking {
                equalsNotNull(it?.indexNumber, targetNum) ||
                    equalsNotNull(it?.id, targetId)
            }
        } else if (targetNum != null && targetNum in pager.indices) {
            // Start searching from the season number and choose direction from there
            val num = pager.getBlocking(targetNum)?.indexNumber
            if (num.lt(targetNum)) {
                for (i in targetNum + 1 until pager.lastIndex) {
                    val season = pager.getBlocking(i)
                    if (equalsNotNull(season?.indexNumber, targetNum) ||
                        equalsNotNull(season?.id, targetId)
                    ) {
                        return i
                    }
                }
                return 0
            } else if (num.gt(targetNum)) {
                for (i in targetNum - 1 downTo 0) {
                    val season = pager.getBlocking(i)
                    if (equalsNotNull(season?.indexNumber, targetNum) ||
                        equalsNotNull(season?.id, targetId)
                    ) {
                        return i
                    }
                }
                return 0
            } else {
                targetNum
            }
        } else {
            0
        }
    return index
}
