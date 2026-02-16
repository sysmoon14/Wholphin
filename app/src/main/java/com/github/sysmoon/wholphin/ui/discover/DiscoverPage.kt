package com.github.sysmoon.wholphin.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.OneTimeLaunchedEffect
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.TabRow
import com.github.sysmoon.wholphin.ui.logTab
import com.github.sysmoon.wholphin.ui.nav.NavDrawerItem
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.ui.preferences.PreferencesViewModel
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

@Composable
fun DiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val rememberedTabIndex =
        remember { preferencesViewModel.getRememberedTab(preferences, NavDrawerItem.Discover.id, 0) }

    val tabs =
        listOf(
            stringResource(R.string.discover),
            stringResource(R.string.my_requests),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val tabFocusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var focusedTabIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedTabIndex) {
        logTab("discover", selectedTabIndex)
        preferencesViewModel.saveRememberedTab(preferences, NavDrawerItem.Discover.id, selectedTabIndex)
    }
    OneTimeLaunchedEffect { preferencesViewModel.backdropService.clearBackdrop() }

    // Navigate to tab on focus (like top nav), with short delay to avoid switching on quick cross
    LaunchedEffect(focusedTabIndex, selectedTabIndex) {
        if (focusedTabIndex !in tabs.indices || focusedTabIndex == selectedTabIndex) return@LaunchedEffect
        delay(250)
        selectedTabIndex = focusedTabIndex
        // Reclaim focus on the tab we switched to immediately so it doesn't flicker up to the top nav
        delay(16)
        tabFocusRequesters.getOrNull(focusedTabIndex)?.tryRequestFocus()
    }

    // Keep focus on the sub-tab row: when the page is first shown and whenever the selected tab changes.
    // Skip when opened via top nav switch so focus stays in the top nav (same as other tabbed pages).
    LaunchedEffect(selectedTabIndex, navHasFocus, wasOpenedViaTopNavSwitch) {
        if (navHasFocus || wasOpenedViaTopNavSwitch) return@LaunchedEffect
        delay(16)
        tabFocusRequesters.getOrNull(selectedTabIndex)?.tryRequestFocus()
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            showHeader,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp),
                tabs = tabs,
                onClick = { selectedTabIndex = it },
                focusRequesters = tabFocusRequesters,
                onTabFocused = { focusedTabIndex = it },
            )
        }
        val slideDuration = 220
        AnimatedContent(
            targetState = selectedTabIndex,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                if (direction == 1) {
                    (slideInHorizontally(animationSpec = tween(slideDuration)) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(animationSpec = tween(slideDuration)) { -it } + fadeOut())
                } else {
                    (slideInHorizontally(animationSpec = tween(slideDuration)) { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally(animationSpec = tween(slideDuration)) { it } + fadeOut())
                }
            },
            label = "discover_tab_content",
        ) { tabIndex ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (tabIndex) {
                    0 -> SeerrDiscoverPage(
                        preferences = preferences,
                        modifier = Modifier.fillMaxSize(),
                        wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                        navHasFocus = navHasFocus,
                        deferInitialFocus = true,
                    )
                    1 -> SeerrRequestsPage(
                        focusRequesterOnEmpty = tabFocusRequesters.getOrNull(1),
                        modifier = Modifier.fillMaxSize(),
                        deferInitialFocus = true,
                    )
                    else -> ErrorMessage("Invalid tab index $tabIndex", null)
                }
            }
        }
    }
}
