package com.github.sysmoon.wholphin.services

import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) {
        /**
         * Returns merged preferences (device + per-user UI/UX) for the current user.
         * Migrates from device prefs on first load if the user has no per-user row.
         */
        suspend fun getCurrent(): UserPreferences {
            val user = serverRepository.currentUser.value
                ?: error("No current user when getCurrent() called")
            val appPrefs = userPreferencesRepository.getMergedPreferencesOnce(user.rowId)
            return UserPreferences(appPrefs)
        }

        /**
         * Flow of merged preferences for the current user. Emits when device or per-user prefs change.
         * Use when the current user is non-null (e.g. after user selection).
         */
        fun getCurrentUserRowId(): Int? = serverRepository.currentUser.value?.rowId
    }
