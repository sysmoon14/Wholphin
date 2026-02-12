package com.github.sysmoon.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.ui.launchIO
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import timber.log.Timber

/**
 * Applies plugin settings for the current user whenever the user changes (e.g. user switch).
 * Ensures settings like hide_settings_cog are correct for the active user so the UI
 * (TopNavBar, DestinationContent) shows the right state.
 */
@ActivityScoped
class PluginSettingsUserSwitchListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val pluginSettingsService: PluginSettingsService,
        private val pluginSettingsApplicator: PluginSettingsApplicator,
    ) {
        init {
            context as AppCompatActivity
            context.lifecycleScope.launchIO {
                serverRepository.currentUser.asFlow().collect { user ->
                    if (user != null) {
                        Timber.d("PluginSettingsUserSwitchListener: User changed to %s, applying plugin settings", user.name)
                        try {
                            val settings = pluginSettingsService.fetchSettings(user.id)
                            if (settings != null) {
                                pluginSettingsApplicator.apply(settings, user)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "PluginSettingsUserSwitchListener: Failed to fetch/apply plugin settings for user %s", user.name)
                        }
                    }
                }
            }
        }
    }
