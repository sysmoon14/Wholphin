package com.github.sysmoon.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.api.seerr.infrastructure.ClientException
import com.github.sysmoon.wholphin.api.seerr.infrastructure.ServerException
import com.github.sysmoon.wholphin.api.seerr.model.RelatedVideo
import com.github.sysmoon.wholphin.api.seerr.model.RequestPostRequest
import com.github.sysmoon.wholphin.api.seerr.model.Season
import com.github.sysmoon.wholphin.api.seerr.model.TvDetails
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.model.DiscoverItem
import com.github.sysmoon.wholphin.data.model.DiscoverRating
import com.github.sysmoon.wholphin.data.model.RemoteTrailer
import com.github.sysmoon.wholphin.data.model.Trailer
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.SeerrServerRepository
import com.github.sysmoon.wholphin.services.SeerrService
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.util.LoadingExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber

@HiltViewModel(assistedFactory = DiscoverSeriesViewModel.Factory::class)
class DiscoverSeriesViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        val serverRepository: ServerRepository,
        val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        @Assisted val item: DiscoverItem,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(item: DiscoverItem): DiscoverSeriesViewModel
        }

        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val tvSeries = MutableLiveData<TvDetails?>(null)
        val rating = MutableLiveData<DiscoverRating?>(null)

        val seasons = MutableLiveData<List<Season>>(listOf())
        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val people = MutableLiveData<List<DiscoverItem>>(listOf())
        val similar = MutableLiveData<List<DiscoverItem>>()
        val recommended = MutableLiveData<List<DiscoverItem>>()
        val canCancelRequest = MutableStateFlow(false)

        val userConfig = seerrServerRepository.current.map { it?.config }
        val request4kEnabled = seerrServerRepository.current.map { it?.request4kTvEnabled ?: false }

        init {
            init()
        }

        private fun fetchAndSetItem(): Deferred<TvDetails> =
            viewModelScope.async(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching TV series",
                    ),
            ) {
                val tv = seerrService.api.tvApi.tvTvIdGet(tvId = item.id)
                this@DiscoverSeriesViewModel.tvSeries.setValueOnMain(tv)
                tv
            }

        fun init(): Job =
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching TV series",
                    ),
            ) {
                Timber.v("Init for tv %s", item.id)
                val tv = fetchAndSetItem().await()
                val discoveredItem = DiscoverItem(tv)
                backdropService.submit(discoveredItem)

                updateCanCancel()

                withContext(Dispatchers.Main) {
                    loading.value = LoadingState.Success
                }
                viewModelScope.launchIO {
                    try {
                        val result = seerrService.api.tvApi.tvTvIdRatingsGet(tvId = item.id)
                        rating.setValueOnMain(DiscoverRating(result))
                    } catch (e: ClientException) {
                        Timber.d(e, "Seerr TV ratings 404 or error for tvId=%s, treating as no ratings", item.id)
                        rating.setValueOnMain(DiscoverRating(null, null))
                    } catch (e: ServerException) {
                        Timber.d(e, "Seerr TV ratings server error for tvId=%s, treating as no ratings", item.id)
                        rating.setValueOnMain(DiscoverRating(null, null))
                    }
                }
                if (!similar.isInitialized) {
                    viewModelScope.launchIO {
                        val result =
                            seerrService.api.tvApi
                                .tvTvIdSimilarGet(tvId = item.id, page = 1)
                                .results
                                ?.map(::DiscoverItem)
                                .orEmpty()
                        similar.setValueOnMain(result)
                    }
                    viewModelScope.launchIO {
                        val result =
                            seerrService.api.tvApi
                                .tvTvIdRecommendationsGet(tvId = item.id, page = 1)
                                .results
                                ?.map(::DiscoverItem)
                                .orEmpty()
                        recommended.setValueOnMain(result)
                    }
                }
                val people =
                    tv.credits
                        ?.cast
                        ?.map(::DiscoverItem)
                        .orEmpty() +
                        tv.credits
                            ?.crew
                            ?.map(::DiscoverItem)
                            .orEmpty()
                this@DiscoverSeriesViewModel.people.setValueOnMain(people)

                val trailers =
                    tv.relatedVideos
                        ?.filter { it.type == RelatedVideo.Type.TRAILER }
                        ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                        ?.map {
                            RemoteTrailer(it.name!!, it.url!!, it.site)
                        }.orEmpty()
                this@DiscoverSeriesViewModel.trailers.setValueOnMain(trailers)
            }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            val canCancel = canUserCancelRequest(user, tvSeries.value?.mediaInfo?.requests)
            canCancelRequest.update { canCancel }
        }

        fun request(
            id: Int,
            is4k: Boolean,
        ) {
            viewModelScope.launchIO {
                val request =
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            is4k = is4k,
                            mediaId = id,
                            mediaType = RequestPostRequest.MediaType.TV,
                            seasons = RequestPostRequest.Seasons.ALL, // TODO handle picking seasons
                        ),
                    )
                fetchAndSetItem().await()
                updateCanCancel()
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                tvSeries.value?.mediaInfo?.requests?.firstOrNull()?.let {
                    // TODO handle multiple requests? Or just delete self's request?
                    seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
                    fetchAndSetItem().await()
                    updateCanCancel()
                }
            }
        }
    }
