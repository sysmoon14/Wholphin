package com.github.sysmoon.wholphin.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.github.sysmoon.wholphin.data.NavDrawerItemRepository
import com.github.sysmoon.wholphin.data.ServerPreferencesDao
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.data.isPinned
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.data.model.NavDrawerPinnedItem
import com.github.sysmoon.wholphin.data.model.NavPinType
import com.github.sysmoon.wholphin.preferences.AppPreference
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.resetSubtitles
import com.github.sysmoon.wholphin.preferences.UserPreferencesRepository
import com.github.sysmoon.wholphin.preferences.updateSubtitlePreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.SeerrServerRepository
import com.github.sysmoon.wholphin.ui.detail.DebugViewModel.Companion.sendAppLogs
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.nav.NavDrawerItem
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        private val rememberTabManager: RememberTabManager,
        private val serverRepository: ServerRepository,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val serverPreferencesDao: ServerPreferencesDao,
        private val seerrServerRepository: SeerrServerRepository,
        private val deviceInfo: DeviceInfo,
        private val clientInfo: ClientInfo,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        private lateinit var allNavDrawerItems: List<NavDrawerItem>
        val navDrawerPins = MutableLiveData<Map<NavDrawerItem, Boolean>>(mapOf())

        val currentUser get() = serverRepository.currentUser

        /**
         * Merged preferences (device + per-user UI/UX) for the current user.
         * When no user is selected, falls back to device prefs.
         */
        val preferencesFlow: Flow<AppPreferences> =
            serverRepository.currentUser.asFlow().flatMapLatest { user ->
                if (user != null) {
                    userPreferencesRepository.getMergedPreferences(user.rowId)
                } else {
                    preferenceDataStore.data
                }
            }

        /**
         * Persists the given merged preferences as the current user's per-user preferences.
         * Use when a sub-screen (e.g. Live TV options dialog) applies multiple changes at once.
         */
        fun updateUserPreferencesFromMerged(newMerged: AppPreferences) {
            viewModelScope.launchIO(ExceptionHandler()) {
                val user = serverRepository.currentUser.value ?: return@launchIO
                userPreferencesRepository.updateUserPreferences(user.rowId, newMerged) { _ -> newMerged }
            }
        }

        /**
         * Updates a preference: writes to per-user store if UI/UX, else to device store.
         */
        fun updatePreference(
            pref: AppPreference<AppPreferences, Any?>,
            newValue: Any?,
            currentMerged: AppPreferences,
        ) {
            viewModelScope.launchIO(ExceptionHandler()) {
                val user = serverRepository.currentUser.value
                if (user != null && !AppPreference.isDeviceOnlyPreference(pref)) {
                    userPreferencesRepository.updateUserPreferences(user.rowId, currentMerged) { prefs ->
                        @Suppress("UNCHECKED_CAST")
                        (pref.setter as (AppPreferences, Any?) -> AppPreferences)(prefs, newValue)
                    }
                } else {
                    preferenceDataStore.updateData { prefs ->
                        @Suppress("UNCHECKED_CAST")
                        (pref.setter as (AppPreferences, Any?) -> AppPreferences)(prefs, newValue)
                    }
                }
            }
        }

        val seerrEnabled =
            seerrServerRepository.currentUser.combine(currentUser.asFlow()) { seerrUser, jellyfinUser ->
                seerrUser != null && jellyfinUser != null && seerrUser.jellyfinUserRowId == jellyfinUser.rowId
            }

        init {
            viewModelScope.launchIO {
                serverRepository.currentUser.value?.let { user ->
                    allNavDrawerItems = navDrawerItemRepository.getNavDrawerItems()
                    val pins = serverPreferencesDao.getNavDrawerPinnedItems(user)
                    val navDrawerPins = allNavDrawerItems.associateWith { pins.isPinned(it.id) }
                    this@PreferencesViewModel.navDrawerPins.setValueOnMain(navDrawerPins)
                }
            }
        }

        fun updatePins(newSelectedItems: List<NavDrawerItem>) {
            viewModelScope.launchIO(ExceptionHandler(true)) {
                serverRepository.currentUser.value?.let { user ->
                    val disabledItems =
                        mutableListOf<NavDrawerItem>().apply {
                            addAll(allNavDrawerItems)
                            removeAll(newSelectedItems)
                        }
                    val enabledItems = newSelectedItems.toSet()
                    val toSave =
                        disabledItems.map {
                            NavDrawerPinnedItem(
                                user.rowId,
                                it.id,
                                NavPinType.UNPINNED,
                            )
                        } +
                            enabledItems.map {
                                NavDrawerPinnedItem(
                                    user.rowId,
                                    it.id,
                                    NavPinType.PINNED,
                                )
                            }
                    serverPreferencesDao.saveNavDrawerPinnedItems(*toSave.toTypedArray())
                    val pins = serverPreferencesDao.getNavDrawerPinnedItems(user)
                    val navDrawerPins = allNavDrawerItems.associateWith { pins.isPinned(it.id) }
                    this@PreferencesViewModel.navDrawerPins.setValueOnMain(navDrawerPins)
                }
            }
        }

        fun sendAppLogs() {
            sendAppLogs(context, api, clientInfo, deviceInfo)
        }

        fun resetSubtitleSettings() {
            viewModelScope.launchIO {
                val user = serverRepository.currentUser.value ?: return@launchIO
                val current = userPreferencesRepository.getMergedPreferencesOnce(user.rowId)
                userPreferencesRepository.updateUserPreferences(user.rowId, current) {
                    it.updateSubtitlePreferences { resetSubtitles() }
                }
            }
        }

        fun setPin(
            user: JellyfinUser,
            pin: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.setUserPin(user, pin)
            }
        }

    }
