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
        private val workManager = WorkManager.getInstance(context)

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
                        delay(150) // Let main-thread WorkManager state settle after user/activity change
                        if (userToSchedule != null) {
                            Timber.i("Scheduling TvProviderWorker for ${userToSchedule.user}")
                            workManager
                                .enqueueUniquePeriodicWork(
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
                            workManager.cancelUniqueWork(TvProviderWorker.WORK_NAME)
                        }
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

        fun launchOneTimeRefresh() {
            if (supportsTvProvider) {
                activity.lifecycleScope.launchIO(ExceptionHandler()) {
                    serverRepository.current.value?.let { user ->
                        Timber.i("Scheduling on-time TvProviderWorker for ${user.user}")
                        workManager.enqueue(
                            OneTimeWorkRequestBuilder<TvProviderWorker>()
                                .setInputData(
                                    workDataOf(
                                        TvProviderWorker.PARAM_USER_ID to user.user.id.toString(),
                                        TvProviderWorker.PARAM_SERVER_ID to user.server.id.toString(),
                                    ),
                                ).build(),
                        )
                    }
                }
            }
        }
    }
