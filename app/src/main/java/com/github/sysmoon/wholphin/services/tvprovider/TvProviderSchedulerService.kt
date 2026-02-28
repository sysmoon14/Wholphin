package com.github.sysmoon.wholphin.services.tvprovider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

/**
 * No longer schedules or cancels TvProvider work from the activity. Work is enqueued once from
 * [com.github.sysmoon.wholphin.WholphinApplication] after a delay to avoid WorkManager
 * AssertionError when the main thread is in a sensitive state (user switch, device wake, etc.).
 * [launchOneTimeRefresh] is a no-op; the periodic worker runs on its own schedule.
 */
@ActivityScoped
class TvProviderSchedulerService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
    ) {
        private val supportsTvProvider =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        /**
         * No-op. TvProvider updates are handled by the periodic work scheduled from Application.
         * Kept so callers (e.g. MainActivity) do not need to change.
         */
        fun launchOneTimeRefresh() {
            if (!supportsTvProvider) return
            // No WorkManager usage from activity to avoid AssertionError on main thread.
        }
    }
