package com.github.sysmoon.wholphin.services

import androidx.datastore.core.DataStore
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
    ) {
        suspend fun getCurrent(): UserPreferences =
            serverRepository.currentUserDto.value!!.configuration.let { userConfig ->
                val appPrefs = preferencesDataStore.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                UserPreferences(
                    appPrefs,
                )
            }
    }
