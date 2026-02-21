package com.github.sysmoon.wholphin.services

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey
import com.github.sysmoon.wholphin.data.CurrentUser
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import kotlinx.serialization.Serializable
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating for setup
 */
@Singleton
class SetupNavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<NavKey> = mutableStateListOf(SetupDestination.Loading)
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Go to the specified [SetupDestination].
         * Defers the backStack update to the next frame to avoid Compose AssertionError
         * (slot table / recompose scope cleared while in use) when navigating from UserList
         * to AppContent after the app has been backgrounded.
         */
        fun navigateTo(destination: SetupDestination) {
            mainHandler.post {
                backStack[0] = destination
                log()
            }
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current setup destination: %s", dest)
            ACRA.errorReporter.putCustomData("setupDestination", dest)
        }
    }

@Serializable
sealed interface SetupDestination : NavKey {
    @Serializable
    data object Loading : SetupDestination

    @Serializable
    data object ServerList : SetupDestination

    @Serializable
    data class UserList(
        val server: JellyfinServer,
    ) : SetupDestination

    @Serializable
    data class AppContent(
        val current: CurrentUser,
    ) : SetupDestination
}
