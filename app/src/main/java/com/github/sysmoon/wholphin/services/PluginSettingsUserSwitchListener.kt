package com.github.sysmoon.wholphin.services

import android.content.Context
import com.github.sysmoon.wholphin.data.ServerRepository
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

/**
 * No-op: Plugin settings are applied only in MainActivityViewModel.appPreferencesFlow
 * so there is a single apply path and no race when switching users.
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
        // Plugin settings applied in ViewModel flow only (fetch + apply before first emission).
    }
