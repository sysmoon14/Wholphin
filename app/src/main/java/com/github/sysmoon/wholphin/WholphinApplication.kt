package com.github.sysmoon.wholphin

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.sysmoon.wholphin.services.tvprovider.TvProviderWorker
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.dialog
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import timber.log.Timber
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import javax.inject.Inject

@HiltAndroidApp
class WholphinApplication :
    Application(),
    Configuration.Provider {
    init {
        instance = this

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy
                    .Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(
                object : Timber.Tree() {
                    override fun isLoggable(
                        tag: String?,
                        priority: Int,
                    ): Boolean = priority >= Log.INFO

                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        Log.println(priority, tag ?: "Wholphin", message)
                    }
                },
            )
        }

        Composer.setDiagnosticStackTraceMode(
            if (BuildConfig.DEBUG) ComposeStackTraceMode.SourceInformation else ComposeStackTraceMode.None,
        )
    }

    override fun onCreate() {
        super.onCreate()
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            excludeMatchingSharedPreferencesKeys = listOf()
            reportContent =
                listOf(
                    ReportField.ANDROID_VERSION,
                    ReportField.APP_VERSION_CODE,
                    ReportField.APP_VERSION_NAME,
                    ReportField.BRAND,
                    // ReportField.BUILD_CONFIG,
                    // ReportField.BUILD,
                    ReportField.CUSTOM_DATA,
                    ReportField.LOGCAT,
                    ReportField.PHONE_MODEL,
                    ReportField.PRODUCT,
                    ReportField.REPORT_ID,
                    ReportField.SHARED_PREFERENCES,
                    ReportField.STACK_TRACE,
                    ReportField.USER_COMMENT,
                    ReportField.USER_CRASH_DATE,
                )
            dialog {
                text =
                    "Wholphin has crashed! Would you like to attempt to " +
                    "send a crash report to your Jellyfin server?"
                title = "Wholphin Crash Report"
                positiveButtonText = "Send"
                negativeButtonText = "Do not send"
            }
            reportSendFailureToast = "Crash report failed to send"
            reportSendSuccessToast = "Sent crash report!"
        }
        ACRA.errorReporter.putCustomData("SDK_INT", Build.VERSION.SDK_INT.toString())

        scheduleTvProviderWorkDeferred()
    }

    /**
     * Schedules TvProvider periodic work from Application with a long delay so WorkManager is never
     * touched during activity lifecycle (avoids AssertionError in WorkManager when opening app or
     * switching user). The worker resolves the current user at run time via ServerRepository.
     */
    private fun scheduleTvProviderWorkDeferred() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    TvProviderWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<TvProviderWorker>(
                        repeatInterval = 1.hours.toJavaDuration(),
                    ).build(),
                )
            } catch (e: Throwable) {
                Timber.w(e, "Failed to schedule TvProviderWorker from Application (will not retry)")
            }
        }, 30_000L)
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    companion object {
        lateinit var instance: WholphinApplication
            private set
    }
}
