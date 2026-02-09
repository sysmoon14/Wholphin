package com.github.sysmoon.wholphin.services

import androidx.navigation3.runtime.NavKey
import com.github.sysmoon.wholphin.ui.nav.Destination
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

        /**
         * Invoked when the user returns to the home screen (e.g. via Back or Go to Home).
         * Used to refresh Up Next / Continue Watching so the row is up to date after playback.
         */
        var onReturnedToHome: (() -> Unit)? = null

        /**
         * Go to the specified [com.github.sysmoon.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
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
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
        }
    }
