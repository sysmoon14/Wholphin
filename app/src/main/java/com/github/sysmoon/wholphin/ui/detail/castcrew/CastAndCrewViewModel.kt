package com.github.sysmoon.wholphin.ui.detail.castcrew

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.data.model.Person
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.PeopleFavorites
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.util.LoadingExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = CastAndCrewViewModel.Factory::class)
class CastAndCrewViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val peopleFavorites: PeopleFavorites,
        val navigationManager: NavigationManager,
        @Assisted private val itemId: UUID,
    ) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(itemId: UUID): CastAndCrewViewModel
    }

    val item = MutableLiveData<BaseItem?>(null)
    val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
    val people = MutableLiveData<List<Person>>(listOf())

    init {
        viewModelScope.launch(
            LoadingExceptionHandler(loading, "Error loading cast and crew"),
        ) {
            loading.setValueOnMain(LoadingState.Loading)
            try {
                val fetchedItem = withContext(Dispatchers.IO) {
                    val dto = api.userLibraryApi.getItem(itemId).content
                    BaseItem.from(dto, api)
                }
                withContext(Dispatchers.Main) {
                    item.value = fetchedItem
                }
                val peopleList = peopleFavorites.getPeopleFor(fetchedItem)
                withContext(Dispatchers.Main) {
                    people.value = peopleList
                    loading.setValueOnMain(LoadingState.Success)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cast and crew for $itemId")
                withContext(Dispatchers.Main) {
                    item.value = null
                    people.value = listOf()
                    loading.setValueOnMain(LoadingState.Error("Error loading cast and crew", e))
                }
            }
        }
    }
}
