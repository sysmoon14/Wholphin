package com.github.sysmoon.wholphin.preferences

import androidx.datastore.core.DataStore
import com.github.sysmoon.wholphin.data.UserPreferencesDao
import com.github.sysmoon.wholphin.data.model.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides merged app preferences (device + per-user UI/UX) and persists per-user preferences to Room.
 */
@Singleton
class UserPreferencesRepository
    @Inject
    constructor(
        private val appPreferencesDataStore: DataStore<AppPreferences>,
        private val userPreferencesDao: UserPreferencesDao,
    ) {

    /**
     * Flow of merged preferences for the given user (device prefs with user UI/UX overlay).
     * Runs migration on first collection if user has no row. Emits when device or user prefs change.
     */
    fun getMergedPreferences(userId: Int): Flow<AppPreferences> =
        flow {
            val device = appPreferencesDataStore.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
            if (userPreferencesDao.get(userId) == null) {
                migrateFromDevice(userId, device)
            }
            emit(Unit)
        }.flatMapLatest {
            combine(
                appPreferencesDataStore.data,
                userPreferencesDao.getFlow(userId),
            ) { device, userEntity ->
                val userProto = userEntity?.preferencesBlob?.let { UserPreferencesProto.parseFrom(it) }
                merge(device, userProto)
            }
        }

    /**
     * One-shot merged preferences for the given user. If user has no row, migrates from device and returns merged.
     */
    suspend fun getMergedPreferencesOnce(userId: Int): AppPreferences {
        val device = appPreferencesDataStore.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
        var userEntity = userPreferencesDao.get(userId)
        if (userEntity == null) {
            migrateFromDevice(userId, device)
            userEntity = userPreferencesDao.get(userId)
        }
        val userProto = userEntity?.preferencesBlob?.let { UserPreferencesProto.parseFrom(it) }
        return merge(device, userProto)
    }

    /**
     * Updates the per-user preferences for the given user. The block receives the current merged prefs;
     * the user-relevant part of the result is persisted to Room.
     */
    suspend fun updateUserPreferences(
        userId: Int,
        currentMerged: AppPreferences,
        block: (AppPreferences) -> AppPreferences,
    ) {
        val updated = block(currentMerged)
        val userProto = extractUserPart(updated)
        userPreferencesDao.insertOrReplace(
            UserPreferencesEntity(userId = userId, preferencesBlob = userProto.toByteArray()),
        )
    }

    private suspend fun migrateFromDevice(userId: Int, device: AppPreferences) {
        val userProto = extractUserPart(device)
        userPreferencesDao.insertOrReplace(
            UserPreferencesEntity(userId = userId, preferencesBlob = userProto.toByteArray()),
        )
    }

    companion object {
        fun merge(device: AppPreferences, user: UserPreferencesProto?): AppPreferences {
            if (user == null) return device
            return device.toBuilder().apply {
                interfacePreferences =
                    user.interfacePreferences.toBuilder()
                        .setHomeUsesPluginRows(device.interfacePreferences.homeUsesPluginRows)
                        .build()
                homePagePreferences = user.homePagePreferences
                playbackPreferences = device.playbackPreferences.toBuilder().apply {
                    if (user.hasPlaybackUi()) {
                        val ui = user.playbackUi
                        skipForwardMs = ui.skipForwardMs
                        skipBackMs = ui.skipBackMs
                        controllerTimeoutMs = ui.controllerTimeoutMs
                        seekBarSteps = ui.seekBarSteps
                        showDebugInfo = ui.showDebugInfo
                        autoPlayNext = ui.autoPlayNext
                        autoPlayNextDelaySeconds = ui.autoPlayNextDelaySeconds
                        skipBackOnResumeSeconds = ui.skipBackOnResumeSeconds
                        skipIntros = ui.skipIntros
                        skipOutros = ui.skipOutros
                        skipCommercials = ui.skipCommercials
                        skipRecaps = ui.skipRecaps
                        skipPreviews = ui.skipPreviews
                        passOutProtectionMs = ui.passOutProtectionMs
                        globalContentScale = ui.globalContentScale
                        showNextUpWhen = ui.showNextUpWhen
                        oneClickPause = ui.oneClickPause
                    }
                }.build()
            }.build()
        }

        fun extractUserPart(prefs: AppPreferences): UserPreferencesProto =
            userPreferencesProto {
                interfacePreferences = prefs.interfacePreferences
                homePagePreferences = prefs.homePagePreferences
                playbackUi = userPlaybackUIPreferences {
                    skipForwardMs = prefs.playbackPreferences.skipForwardMs
                    skipBackMs = prefs.playbackPreferences.skipBackMs
                    controllerTimeoutMs = prefs.playbackPreferences.controllerTimeoutMs
                    seekBarSteps = prefs.playbackPreferences.seekBarSteps
                    showDebugInfo = prefs.playbackPreferences.showDebugInfo
                    autoPlayNext = prefs.playbackPreferences.autoPlayNext
                    autoPlayNextDelaySeconds = prefs.playbackPreferences.autoPlayNextDelaySeconds
                    skipBackOnResumeSeconds = prefs.playbackPreferences.skipBackOnResumeSeconds
                    skipIntros = prefs.playbackPreferences.skipIntros
                    skipOutros = prefs.playbackPreferences.skipOutros
                    skipCommercials = prefs.playbackPreferences.skipCommercials
                    skipRecaps = prefs.playbackPreferences.skipRecaps
                    skipPreviews = prefs.playbackPreferences.skipPreviews
                    passOutProtectionMs = prefs.playbackPreferences.passOutProtectionMs
                    globalContentScale = prefs.playbackPreferences.globalContentScale
                    showNextUpWhen = prefs.playbackPreferences.showNextUpWhen
                    oneClickPause = prefs.playbackPreferences.oneClickPause
                }
            }
    }
}
