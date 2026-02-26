package com.github.sysmoon.wholphin.services.tvprovider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.github.sysmoon.wholphin.data.ServerRepository
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@ActivityScoped
class TvProviderSchedulerService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
    ) {
        private val activity = (context as AppCompatActivity)

        private val supportsTvProvider =
            // TODO <=25 has limited support
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        init {
            serverRepository.current.observe(activity) { user ->
                // Defer all WorkManager calls out of the observer and current frame to avoid
                // AssertionError in WorkManager (nc.c0.clear) when it runs during user switch or
                // activity start while the main thread is in a sensitive state (e.g. Compose frame).
                if (!supportsTvProvider) return@observe
                val userToSchedule = user
                val run: () -> Unit = {
                    activity.lifecycleScope.launchIO(ExceptionHandler()) {
                        scheduleTvProviderWork(userToSchedule, retryCount = 0)
                    }
                    Unit
                }
                if (activity.window?.decorView != null) {
                    activity.window!!.decorView.post(run)
                } else {
                    Handler(Looper.getMainLooper()).post(run)
                }
            }
        }

        /**
         * Schedules or cancels TvProvider work. Catches WorkManager's internal AssertionError
         * (nc.c0.clear) which can occur during activity start or user switch; retries once after
         * a longer delay so the crash dialog does not show and work is still scheduled when possible.
         */
        private suspend fun scheduleTvProviderWork(
            userToSchedule: com.github.sysmoon.wholphin.data.CurrentUser?,
            retryCount: Int,
        ) {
            val delayMs = if (retryCount == 0) 400L else 2000L
            delay(delayMs)
            try {
                val wm = WorkManager.getInstance(context)
                if (userToSchedule != null) {
                    Timber.i("Scheduling TvProviderWorker for ${userToSchedule.user}")
                    wm.enqueueUniquePeriodicWork(
                        uniqueWorkName = TvProviderWorker.WORK_NAME,
                        existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                        request =
                            PeriodicWorkRequestBuilder<TvProviderWorker>(
                                repeatInterval = 1.hours.toJavaDuration(),
                            ).setBackoffCriteria(
                                BackoffPolicy.LINEAR,
                                15.minutes.toJavaDuration(),
                            ).setInputData(
                                workDataOf(
                                    TvProviderWorker.PARAM_USER_ID to userToSchedule.user.id.toString(),
                                    TvProviderWorker.PARAM_SERVER_ID to userToSchedule.server.id.toString(),
                                ),
                            ).build(),
                    ).await()
                } else {
                    wm.cancelUniqueWork(TvProviderWorker.WORK_NAME)
                }
            } catch (e: AssertionError) {
                Timber.w(e, "WorkManager AssertionError scheduling TvProviderWorker (retryCount=%d)", retryCount)
                if (retryCount < 1) {
                    scheduleTvProviderWork(userToSchedule, retryCount + 1)
                }
            }
        }

        fun launchOneTimeRefresh() {
            if (supportsTvProvider) {
                activity.lifecycleScope.launchIO(ExceptionHandler()) {
                    delay(300)
                    try {
                        serverRepository.current.value?.let { user ->
                            Timber.i("Scheduling on-time TvProviderWorker for ${user.user}")
                            WorkManager.getInstance(context).enqueue(
                                OneTimeWorkRequestBuilder<TvProviderWorker>()
                                    .setInputData(
                                        workDataOf(
                                            TvProviderWorker.PARAM_USER_ID to user.user.id.toString(),
                                            TvProviderWorker.PARAM_SERVER_ID to user.server.id.toString(),
                                        ),
                                    ).build(),
                            )
                        }
                    } catch (e: AssertionError) {
                        Timber.w(e, "WorkManager AssertionError in launchOneTimeRefresh")
                    }
                }
            }
        }
    }
