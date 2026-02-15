package com.github.sysmoon.wholphin.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.isActive
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.LibraryDisplayInfoDao
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.filter.CommunityRatingFilter
import com.github.sysmoon.wholphin.data.filter.DecadeFilter
import com.github.sysmoon.wholphin.data.filter.DefaultFilterOptions
import com.github.sysmoon.wholphin.data.filter.FavoriteFilter
import com.github.sysmoon.wholphin.data.filter.FilterValueOption
import com.github.sysmoon.wholphin.data.filter.FilterVideoType
import com.github.sysmoon.wholphin.data.filter.GenreFilter
import com.github.sysmoon.wholphin.data.filter.ItemFilterBy
import com.github.sysmoon.wholphin.data.filter.OfficialRatingFilter
import com.github.sysmoon.wholphin.data.filter.PlayedFilter
import com.github.sysmoon.wholphin.data.filter.VideoTypeFilter
import com.github.sysmoon.wholphin.data.filter.YearFilter
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.data.model.CollectionFolderFilter
import com.github.sysmoon.wholphin.data.model.GetItemsFilter
import com.github.sysmoon.wholphin.data.model.GetItemsFilterOverride
import com.github.sysmoon.wholphin.data.model.LibraryDisplayInfo
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.FavoriteWatchManager
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.ui.AspectRatios
import com.github.sysmoon.wholphin.ui.RequestOrRestoreFocus
import com.github.sysmoon.wholphin.ui.SlimItemFields
import com.github.sysmoon.wholphin.ui.cards.GridCard
import com.github.sysmoon.wholphin.ui.data.AddPlaylistViewModel
import com.github.sysmoon.wholphin.ui.data.SortAndDirection
import com.github.sysmoon.wholphin.ui.detail.CardGrid
import com.github.sysmoon.wholphin.ui.detail.ItemViewModel
import com.github.sysmoon.wholphin.ui.detail.MoreDialogActions
import com.github.sysmoon.wholphin.ui.detail.PlaylistDialog
import com.github.sysmoon.wholphin.ui.detail.PlaylistLoadingState
import com.github.sysmoon.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.main.HomePageHeader
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.playback.scale
import com.github.sysmoon.wholphin.ui.rememberInt
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.ui.setValueOnMainIfActive
import com.github.sysmoon.wholphin.ui.toServerString
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.ApiRequestPager
import com.github.sysmoon.wholphin.util.DataLoadingState
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.GetItemsRequestHandler
import com.github.sysmoon.wholphin.util.GetPersonsHandler
import com.github.sysmoon.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.yearsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.TreeSet
import java.util.UUID
import kotlin.time.Duration

@HiltViewModel(assistedFactory = CollectionFolderViewModel.Factory::class)
class CollectionFolderViewModel
    @AssistedInject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        val navigationManager: NavigationManager,
        @Assisted itemId: String,
        @Assisted initialSortAndDirection: SortAndDirection?,
        @Assisted("recursive") private val recursive: Boolean,
        @Assisted private val collectionFilter: CollectionFolderFilter,
        @Assisted("useSeriesForPrimary") private val useSeriesForPrimary: Boolean,
        @Assisted defaultViewOptions: ViewOptions,
    ) : ItemViewModel(api) {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: String,
                initialSortAndDirection: SortAndDirection?,
                @Assisted("recursive") recursive: Boolean,
                collectionFilter: CollectionFolderFilter,
                @Assisted("useSeriesForPrimary") useSeriesForPrimary: Boolean,
                defaultViewOptions: ViewOptions,
            ): CollectionFolderViewModel
        }

        val loading = MutableLiveData<DataLoadingState<List<BaseItem?>>>(DataLoadingState.Loading)
        val backgroundLoading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val sortAndDirection = MutableLiveData<SortAndDirection>()
        val filter = MutableLiveData<GetItemsFilter>(GetItemsFilter())
        val viewOptions = MutableLiveData<ViewOptions>()

        var position: Int
            get() = savedStateHandle.get<Int>("position") ?: 0
            set(value) {
                savedStateHandle["position"] = value
            }

        init {
            viewModelScope.launchIO {
                super.itemId = itemId
                try {
                    val libraryDisplayInfo =
                        serverRepository.currentUser.value?.let { user ->
                            libraryDisplayInfoDao.getItem(user, itemId)
                        }

                    val sortAndDirection =
                        if (collectionFilter.useSavedLibraryDisplayInfo) {
                            libraryDisplayInfo?.sortAndDirection
                        } else {
                            null
                        } ?: initialSortAndDirection ?: SortAndDirection.DEFAULT

                    val filterToUse =
                        if (collectionFilter.useSavedLibraryDisplayInfo && libraryDisplayInfo?.filter != null) {
                            collectionFilter.filter.merge(libraryDisplayInfo.filter)
                        } else {
                            collectionFilter.filter
                        }

                    val viewOptionsToSet = libraryDisplayInfo?.viewOptions ?: defaultViewOptions
                    // Defer first LiveData updates to next frame to avoid Compose AssertionError when
                    // navigating to this screen (slot table / recompose scope cleared while in use).
                    withContext(Dispatchers.Main) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (!viewModelScope.isActive) return@post
                            viewModelScope.launchIO {
                                try {
                                    itemId.toUUIDOrNull()?.let { fetchItem(it) }
                                    this@CollectionFolderViewModel.viewOptions.setValueOnMainIfActive(
                                        viewModelScope,
                                        viewOptionsToSet,
                                    )
                                    loadResults(true, sortAndDirection, recursive, filterToUse, useSeriesForPrimary)
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Error during init")
                                    loading.setValueOnMainIfActive(viewModelScope, DataLoadingState.Error(ex))
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during init")
                    withContext(Dispatchers.Main) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (viewModelScope.isActive) {
                                loading.value = DataLoadingState.Error(ex)
                            }
                        }
                    }
                }
            }
        }

        private fun saveLibraryDisplayInfo(
            newFilter: GetItemsFilter = this.filter.value!!,
            newSort: SortAndDirection = this.sortAndDirection.value!!,
            viewOptions: ViewOptions? = this.viewOptions.value,
        ) {
            if (collectionFilter.useSavedLibraryDisplayInfo) {
                serverRepository.currentUser.value?.let { user ->
                    viewModelScope.launchIO {
                        val libraryDisplayInfo =
                            LibraryDisplayInfo(
                                userId = user.rowId,
                                itemId = itemId,
                                sort = newSort.sort,
                                direction = newSort.direction,
                                filter = newFilter,
                                viewOptions = viewOptions,
                            )
                        libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                    }
                }
            }
        }

        fun saveViewOptions(viewOptions: ViewOptions) {
            this.viewOptions.value = viewOptions
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                saveLibraryDisplayInfo(viewOptions = viewOptions)
                if (!viewOptions.showDetails) {
                    backdropService.clearBackdrop()
                }
            }
        }

        fun onFilterChange(
            newFilter: GetItemsFilter,
            recursive: Boolean,
        ) {
            Timber.v("onFilterChange: filter=%s", newFilter)
            saveLibraryDisplayInfo(newFilter, sortAndDirection.value!!)
            loadResults(false, sortAndDirection.value!!, recursive, newFilter, useSeriesForPrimary)
        }

        fun onSortChange(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ) {
            Timber.v(
                "onSortChange: sort=%s, recursive=%s, filter=%s",
                sortAndDirection,
                recursive,
                filter,
            )
            saveLibraryDisplayInfo(filter, sortAndDirection)
            loadResults(true, sortAndDirection, recursive, filter, useSeriesForPrimary)
        }

        private fun loadResults(
            resetState: Boolean,
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    if (viewModelScope.isActive) {
                        if (resetState) {
                            loading.value = DataLoadingState.Loading
                        }
                        backgroundLoading.value = LoadingState.Loading
                        this@CollectionFolderViewModel.sortAndDirection.value = sortAndDirection
                        this@CollectionFolderViewModel.filter.value = filter
                    }
                }
                try {
                    val newPager =
                        createPager(sortAndDirection, recursive, filter, useSeriesForPrimary).init()
                    if (newPager.isNotEmpty()) newPager.getBlocking(0)
                    withContext(Dispatchers.Main) {
                        if (viewModelScope.isActive) {
                            loading.value = DataLoadingState.Success(newPager)
                            backgroundLoading.value = LoadingState.Success
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(
                        ex,
                        "Exception while loading data: sort=%s, filter=%s",
                        sortAndDirection,
                        filter,
                    )
                    withContext(Dispatchers.Main) {
                        if (viewModelScope.isActive) {
                            loading.value = DataLoadingState.Error(ex)
                        }
                    }
                }
            }
        }

        private fun createPager(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ): ApiRequestPager<out Any> =
            when (filter.override) {
                GetItemsFilterOverride.NONE -> {
                    val request =
                        createGetItemsRequest(
                            sortAndDirection = sortAndDirection,
                            recursive = recursive,
                            filter = filter,
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            useSeriesForPrimary = useSeriesForPrimary,
                        )
                    newPager
                }

                GetItemsFilterOverride.PERSON -> {
                    val request =
                        filter.applyTo(
                            GetPersonsRequest(
                                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                            ),
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetPersonsHandler,
                            viewModelScope,
                            useSeriesForPrimary = useSeriesForPrimary,
                        )
                    newPager
                }
            }

        private fun createGetItemsRequest(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ): GetItemsRequest {
            val item = item.value
            val collectionType = item?.data?.collectionType
            val includeItemTypes =
                collectionType
                    ?.baseItemKinds
                    .orEmpty()
            var request =
                filter.applyTo(
                    GetItemsRequest(
                        parentId = item?.id,
                        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                        includeItemTypes = includeItemTypes,
                        recursive = recursive,
                        excludeItemIds = item?.let { listOf(item.id) },
                        sortBy =
                            buildList {
                                if (sortAndDirection.sort != ItemSortBy.DEFAULT) {
                                    add(sortAndDirection.sort)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(ItemSortBy.SORT_NAME)
                                    }
                                    if (collectionType == CollectionType.MOVIES) {
                                        add(ItemSortBy.PRODUCTION_YEAR)
                                    }
                                }
                            },
                        sortOrder =
                            buildList {
                                if (sortAndDirection.sort != ItemSortBy.DEFAULT) {
                                    add(sortAndDirection.direction)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(SortOrder.ASCENDING)
                                    }
                                    if (collectionType == CollectionType.MOVIES) {
                                        add(SortOrder.ASCENDING)
                                    }
                                }
                            },
                        fields = SlimItemFields,
                    ),
                )
            // Shows library grid always displays series, not episodes (saved filter may have includeItemTypes = EPISODE)
            if (collectionType == CollectionType.TVSHOWS) {
                request = request.copy(includeItemTypes = listOf(BaseItemKind.SERIES))
            }
            return request
        }

        suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            try {
                when (filterOption) {
                    GenreFilter -> {
                        api.genresApi
                            .getGenres(
                                parentId = itemUuid,
                                userId = serverRepository.currentUser.value?.id,
                            ).content.items
                            .map { FilterValueOption(it.name ?: "", it.id) }
                    }

                    FavoriteFilter,
                    PlayedFilter,
                    -> {
                        listOf(
                            FilterValueOption("True", null),
                            FilterValueOption("False", null),
                        )
                    }

                    OfficialRatingFilter -> {
                        api.localizationApi.getParentalRatings().content.map {
                            FilterValueOption(it.name ?: "", it.value)
                        }
                    }

                    VideoTypeFilter -> {
                        FilterVideoType.entries.map {
                            FilterValueOption(it.readable, it)
                        }
                    }

                    YearFilter -> {
                        api.yearsApi
                            .getYears(
                                parentId = itemUuid,
                                userId = serverRepository.currentUser.value?.id,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                            ).content.items
                            .mapNotNull {
                                it.name?.toIntOrNull()?.let { FilterValueOption(it.toString(), it) }
                            }
                    }

                    DecadeFilter -> {
                        val items = TreeSet<Int>()
                        api.yearsApi
                            .getYears(
                                parentId = itemUuid,
                                userId = serverRepository.currentUser.value?.id,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                            ).content.items
                            .mapNotNullTo(items) {
                                it.name
                                    ?.toIntOrNull()
                                    ?.div(10)
                                    ?.times(10)
                            }
                        items.toList().sorted().map { FilterValueOption("$it's", it) }
                    }

                    CommunityRatingFilter -> {
                        (1..10).map {
                            FilterValueOption("$it", it)
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception get filter value options for $filterOption")
                listOf()
            }

        suspend fun positionOfLetter(letter: Char): Int? =
            withContext(Dispatchers.IO) {
                val sort = sortAndDirection.value
                val filter = filter.value
                if (sort == null || filter == null) {
                    return@withContext null
                }
                val request =
                    createGetItemsRequest(
                        sortAndDirection = sort,
                        recursive = recursive,
                        filter = filter,
                    ).copy(
                        enableImageTypes = null,
                        fields = null,
                        nameLessThan = letter.toString(),
                        limit = 0,
                        enableTotalRecordCount = true,
                    )
                val result by GetItemsRequestHandler.execute(api, request)
                result.totalRecordCount
            }

        fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            (loading.value as? DataLoadingState.Success)?.let {
                (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
            }
        }

        fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            (loading.value as? DataLoadingState.Success)?.let {
                (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }
    }

/**
 * Shows a collection folder as a grid
 *
 * This is the "Library" tab for Movies or TV shows
 */
@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: UUID,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId.toServerString(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
    onHeaderFocusChange: ((Boolean) -> Unit)? = null,
    deferInitialFocus: Boolean = false,
    navHasFocus: Boolean = false,
) = CollectionFolderGrid(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    onClickItem,
    sortOptions,
    playEnabled,
    viewModelKey = viewModelKey,
    defaultViewOptions = defaultViewOptions,
    modifier = modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
    focusRequesterOnEmpty = focusRequesterOnEmpty,
    onHeaderFocusChange = onHeaderFocusChange,
    deferInitialFocus = deferInitialFocus,
    navHasFocus = navHasFocus,
)

@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: String,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId,
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
    onHeaderFocusChange: ((Boolean) -> Unit)? = null,
    deferInitialFocus: Boolean = false,
    navHasFocus: Boolean = false,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    viewModel: CollectionFolderViewModel =
        hiltViewModel<CollectionFolderViewModel, CollectionFolderViewModel.Factory>(
            key = viewModelKey,
        ) {
            it.create(
                itemId = itemId,
                initialSortAndDirection = initialSortAndDirection,
                recursive = recursive,
                collectionFilter = initialFilter,
                useSeriesForPrimary = useSeriesForPrimary,
                defaultViewOptions = defaultViewOptions,
            )
        },
) {
    val context = LocalContext.current
    val sortAndDirection by viewModel.sortAndDirection.observeAsState(SortAndDirection.DEFAULT)
    val filter by viewModel.filter.observeAsState(initialFilter.filter)
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val backgroundLoading by viewModel.backgroundLoading.observeAsState(LoadingState.Loading)
    val item by viewModel.item.observeAsState()
    val viewOptions by viewModel.viewOptions.observeAsState(defaultViewOptions)

    var moreDialog by remember { mutableStateOf<Optional<PositionItem>>(Optional.absent()) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    when (val state = loading) {
        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(focusEnabled = false)
        }

        is DataLoadingState.Error,
        is DataLoadingState.Success<*>,
        -> {
            val title =
                initialFilter.nameOverride
                    ?: item?.name
                    ?: item?.data?.collectionType?.name
                    ?: stringResource(R.string.collection)
            Box(modifier = modifier) {
                CollectionFolderGridContent(
                    preferences = preferences,
                    initialPosition = viewModel.position,
                    item = item,
                    title = title,
                    loadingState = @Suppress("UNCHECKED_CAST") (state as DataLoadingState<List<BaseItem?>>),
                    sortAndDirection = sortAndDirection!!,
                    modifier = Modifier.fillMaxSize(),
                    focusRequesterOnEmpty = focusRequesterOnEmpty,
                    deferInitialFocus = deferInitialFocus,
                    navHasFocus = navHasFocus,
                    onClickItem = onClickItem,
                    onLongClickItem = { position, item ->
                        moreDialog.makePresent(PositionItem(position, item))
                    },
                    onSortChange = {
                        viewModel.onSortChange(it, recursive, filter)
                    },
                    filterOptions = filterOptions,
                    currentFilter = filter,
                    onFilterChange = {
                        viewModel.onFilterChange(it, recursive)
                    },
                    getPossibleFilterValues = {
                        viewModel.getFilterOptionValues(it)
                    },
                    showTitle = showTitle,
                    sortOptions = sortOptions,
                    positionCallback = { columns, position ->
                        viewModel.position = position
                        positionCallback?.invoke(columns, position)
                    },
                    letterPosition = { viewModel.positionOfLetter(it) ?: -1 },
                    viewOptions = viewOptions,
                    defaultViewOptions = defaultViewOptions,
                    onSaveViewOptions = { viewModel.saveViewOptions(it) },
                    onChangeBackdrop = viewModel::updateBackdrop,
                    playEnabled = playEnabled,
                    onClickPlay = { _, item ->
                        viewModel.navigationManager.navigateTo(Destination.Playback(item))
                    },
                    onClickPlayAll = { shuffle ->
                        itemId.toUUIDOrNull()?.let {
                            viewModel.navigationManager.navigateTo(
                                Destination.PlaybackList(
                                    itemId = it,
                                    startIndex = 0,
                                    shuffle = shuffle,
                                    recursive = recursive,
                                    sortAndDirection = sortAndDirection,
                                    filter = filter,
                                ),
                            )
                        }
                    },
                )

                AnimatedVisibility(
                    backgroundLoading == LoadingState.Loading,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                ) {
                    CircularProgress(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = .25f),
                                shape = CircleShape,
                            ).size(64.dp)
                            .padding(4.dp),
                    )
                }
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

@Composable
fun CollectionFolderGridContent(
    preferences: UserPreferences,
    item: BaseItem?,
    title: String,
    loadingState: DataLoadingState<List<BaseItem?>>,
    sortAndDirection: SortAndDirection,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    letterPosition: suspend (Char) -> Int,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    defaultViewOptions: ViewOptions,
    onSaveViewOptions: (ViewOptions) -> Unit,
    viewOptions: ViewOptions,
    onClickPlayAll: (shuffle: Boolean) -> Unit,
    onClickPlay: (Int, BaseItem) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    initialPosition: Int,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    currentFilter: GetItemsFilter = GetItemsFilter(),
    filterOptions: List<ItemFilterBy<*>> = listOf(),
    onFilterChange: (GetItemsFilter) -> Unit = {},
    focusRequesterOnEmpty: FocusRequester? = null,
    onHeaderFocusChange: ((Boolean) -> Unit)? = null,
    deferInitialFocus: Boolean = false,
    navHasFocus: Boolean = false,
) {
    val context = LocalContext.current

    val pager = (loadingState as? DataLoadingState.Success)?.data
    var showHeader by rememberSaveable { mutableStateOf(true) }
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var viewOptions by remember { mutableStateOf(viewOptions) }
    val headerRowFocusRequester = remember { FocusRequester() }

    val gridFocusRequester = remember { FocusRequester() }
    if (pager?.isNotEmpty() == true) {
        RequestOrRestoreFocus(gridFocusRequester, requestOnLaunch = !deferInitialFocus && !navHasFocus)
    } else if (!deferInitialFocus) {
        LaunchedEffect(Unit, navHasFocus) {
            if (navHasFocus) return@LaunchedEffect
            (focusRequesterOnEmpty ?: headerRowFocusRequester).tryRequestFocus()
        }
    }
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }

    var position by rememberInt(initialPosition)
    val focusedItem = pager?.getOrNull(position)
    if (viewOptions.showDetails) {
        LaunchedEffect(focusedItem) {
            focusedItem?.let(onChangeBackdrop)
        }
    }

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedVisibility(
                showHeader || loadingState !is DataLoadingState.Success,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val collectionsLabel = stringResource(R.string.collections)
                    if (showTitle && title != collectionsLabel) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val endPadding =
                        16.dp + if (sortAndDirection.sort == ItemSortBy.SORT_NAME) 24.dp else 0.dp
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(start = 16.dp, end = endPadding)
                                .fillMaxWidth()
                                .focusRequester(headerRowFocusRequester)
                                .onFocusChanged { focusState ->
                                    onHeaderFocusChange?.invoke(focusState.hasFocus)
                                },
                    ) {
                        if (sortOptions.isNotEmpty() || filterOptions.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier,
                            ) {
                                if (sortOptions.isNotEmpty()) {
                                    SortByButton(
                                        sortOptions = sortOptions,
                                        current = sortAndDirection,
                                        onSortChange = onSortChange,
                                        modifier = Modifier,
                                    )
                                }
                                if (filterOptions.isNotEmpty()) {
                                    FilterByButton(
                                        filterOptions = filterOptions,
                                        current = currentFilter,
                                        onFilterChange = onFilterChange,
                                        getPossibleValues = getPossibleFilterValues,
                                        modifier = Modifier,
                                    )
                                }
                                ExpandableFaButton(
                                    title = R.string.view_options,
                                    iconStringRes = R.string.fa_sliders,
                                    onClick = { showViewOptions = true },
                                    modifier = Modifier,
                                )
                            }
                        }
                        if (playEnabled && pager?.isNotEmpty() == true) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier,
                            ) {
                                ExpandablePlayButton(
                                    title = R.string.play,
                                    resume = Duration.ZERO,
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { onClickPlayAll.invoke(false) },
                                )
                                ExpandableFaButton(
                                    title = R.string.shuffle,
                                    iconStringRes = R.string.fa_shuffle,
                                    onClick = { onClickPlayAll.invoke(true) },
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(viewOptions.showDetails) {
                HomePageHeader(
                    item = focusedItem,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(16.dp),
                )
            }
            when (val state = loadingState) {
                DataLoadingState.Pending,
                DataLoadingState.Loading,
                -> {
                    // This shouldn't happen, so just show placeholder
                    Text("Loading")
                }

                is DataLoadingState.Error -> {
                    ErrorMessage(state.message, state.exception)
                }

                is DataLoadingState.Success<List<BaseItem?>> -> {
                    CardGrid(
                        pager = state.data,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        onClickPlay = onClickPlay,
                        letterPosition = letterPosition,
                        gridFocusRequester = gridFocusRequester,
                        showJumpButtons = false, // TODO add preference
                        showLetterButtons = sortAndDirection.sort == ItemSortBy.SORT_NAME,
                        modifier = Modifier.fillMaxSize(),
                        initialPosition = initialPosition,
                        positionCallback = { columns, newPosition ->
                            showHeader = newPosition < columns
                            position = newPosition
                            positionCallback?.invoke(columns, newPosition)
                        },
                        cardContent = { item, onClick, onLongClick, mod ->
                            GridCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                imageContentScale = viewOptions.contentScale.scale,
                                imageAspectRatio = viewOptions.aspectRatio.ratio,
                                imageType = viewOptions.imageType,
                                showTitle = viewOptions.showTitles,
                                modifier = mod,
                            )
                        },
                        columns = viewOptions.columns,
                        spacing = viewOptions.spacing.dp,
                    )
                    AnimatedVisibility(showViewOptions) {
                        ViewOptionsDialog(
                            viewOptions = viewOptions,
                            defaultViewOptions = defaultViewOptions,
                            onDismissRequest = {
                                showViewOptions = false
                                onSaveViewOptions.invoke(viewOptions)
                            },
                            onViewOptionsChange = { viewOptions = it },
                        )
                    }
                }
            }
        }
    }
}

data class PositionItem(
    val position: Int,
    val item: BaseItem,
)

data class CollectionFolderGridParameters(
    val columns: Int = 6,
    val spacing: Dp = 16.dp,
    val cardContent: @Composable (
        item: BaseItem?,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        mod: Modifier,
    ) -> Unit = { item, onClick, onLongClick, mod ->
        GridCard(
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            imageContentScale = ContentScale.FillBounds,
            modifier = mod,
        )
    },
) {
    companion object {
        val POSTER =
            CollectionFolderGridParameters(
                columns = 6,
                spacing = 16.dp,
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.FillBounds,
                        imageAspectRatio = AspectRatios.TALL,
                        modifier = mod,
                    )
                },
            )
        val WIDE =
            CollectionFolderGridParameters(
                columns = 4,
                spacing = 24.dp,
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.Crop,
                        imageAspectRatio = AspectRatios.WIDE,
                        modifier = mod,
                    )
                },
            )
        val SQUARE =
            CollectionFolderGridParameters(
                columns = 6,
                spacing = 16.dp,
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.FillBounds,
                        imageAspectRatio = AspectRatios.SQUARE,
                        modifier = mod,
                    )
                },
            )
    }
}

val CollectionType.baseItemKinds: List<BaseItemKind>
    get() =
        when (this) {
            CollectionType.MOVIES -> {
                listOf(BaseItemKind.MOVIE)
            }

            CollectionType.TVSHOWS -> {
                listOf(BaseItemKind.SERIES)
            }

            CollectionType.HOMEVIDEOS -> {
                listOf(BaseItemKind.VIDEO)
            }

            CollectionType.MUSIC -> {
                listOf(
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.MUSIC_ALBUM,
                )
            }

            CollectionType.BOXSETS -> {
                listOf(BaseItemKind.BOX_SET)
            }

            CollectionType.PLAYLISTS -> {
                listOf(BaseItemKind.PLAYLIST)
            }

            else -> {
                listOf()
            }
        }
