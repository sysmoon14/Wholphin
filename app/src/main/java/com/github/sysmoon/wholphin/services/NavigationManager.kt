package com.github.sysmoon.wholphin.services

import android.os.SystemClock
import androidx.navigation3.runtime.NavKey
import com.github.sysmoon.wholphin.ui.nav.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<NavKey> = mutableListOf()

        /** Current destination for reactive UI (e.g. fixed top nav that doesn't animate). */
        private val _currentDestination = MutableStateFlow<Destination?>(null)
        val currentDestination: StateFlow<Destination?> = _currentDestination.asStateFlow()

        /**
         * Invoked when the user returns to the home screen (e.g. via Back or Go to Home).
         * Used to refresh Up Next / Continue Watching so the row is up to date after playback.
         */
        var onReturnedToHome: (() -> Unit)? = null

        /**
         * Direction of the last top-nav bar navigation for slide transitions.
         * 1 = moved right (e.g. Home → Movies), -1 = moved left, 0 = not from top nav (use default transition).
         */
        var lastTopNavDirection: Int = 0

        /**
         * Set the direction for the next transition when navigating from the top nav bar.
         * @param fromIndex nav bar index we're leaving (e.g. -1 for Home)
         * @param toIndex nav bar index we're going to
         */
        fun setLastTopNavDirection(fromIndex: Int, toIndex: Int) {
            lastTopNavDirection = (toIndex - fromIndex).coerceIn(-1, 1)
        }

        /** Call after assigning [backStack] so [currentDestination] is in sync (e.g. initial load). */
        fun syncCurrentDestinationFromBackStack() {
            _currentDestination.value = backStack.lastOrNull() as? Destination
        }

        /**
         * After a top-nav switch, content should skip requesting initial focus until this time (uptimeMillis).
         * Set via [setSkipContentFocusFor]; content reads and skips focus if now < value.
         */
        private val _skipContentFocusUntilMillis = MutableStateFlow(0L)
        val skipContentFocusUntilMillis: StateFlow<Long> = _skipContentFocusUntilMillis.asStateFlow()

        /** Call when navigating from the top nav so content doesn't steal focus for [durationMs]. */
        fun setSkipContentFocusFor(durationMs: Long) {
            _skipContentFocusUntilMillis.value = SystemClock.uptimeMillis() + durationMs
            _openedViaTopNavSwitch = true
        }

        /**
         * True when the current destination was reached by switching tabs in the top nav.
         * Consumed once per destination so content can keep focus in nav and never steal it.
         */
        private var _openedViaTopNavSwitch = false
        fun consumeOpenedViaTopNavSwitch(): Boolean =
            _openedViaTopNavSwitch.also { _openedViaTopNavSwitch = false }

        /**
         * Called by TopNavBar to register a callback that moves focus to the selected nav item.
         * ApplicationContent invokes this when the destination changes so focus stays in the nav
         * instead of flickering into content.
         */
        var onRequestTopNavFocus: (() -> Unit)? = null

        /**
         * Go to the specified [com.github.sysmoon.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            lastTopNavDirection = 0
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            val hadMultiple = synchronized(this) {
                val size = backStack.size
                if (size > 1) {
                    backStack.removeLastOrNull()
                    true
                } else {
                    false
                }
            }
            log()
            if (hadMultiple && backStack.lastOrNull() is Destination.Home) {
                onReturnedToHome?.invoke()
            }
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome() {
            val hadMultiple = backStack.size > 1
            synchronized(this) {
                while (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
                if (backStack[0] !is Destination.Home) {
                    backStack[0] = Destination.Home()
                }
            }
            log()
            if (hadMultiple) {
                onReturnedToHome?.invoke()
            }
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        /**
         * Resets the backstack to the specified destination
         */
        fun replace(destination: Destination) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(0, destination)
            } else {
                backStack[0] = destination
            }
            log()
        }

        private fun log() {
            val last = backStack.lastOrNull()
            val dest = last.toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
            _currentDestination.value = last as? Destination
        }
    }
