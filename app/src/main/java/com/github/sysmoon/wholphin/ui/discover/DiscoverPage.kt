package com.github.sysmoon.wholphin.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.github.sysmoon.wholphin.ui.preferences.PreferencesViewModel

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
            stringResource(R.string.requests),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val tabFocusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }

    LaunchedEffect(selectedTabIndex) {
        logTab("discover", selectedTabIndex)
        preferencesViewModel.saveRememberedTab(preferences, NavDrawerItem.Discover.id, selectedTabIndex)
    }
    OneTimeLaunchedEffect { preferencesViewModel.backdropService.clearBackdrop() }

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
            )
        }
        when (selectedTabIndex) {
            // Discover
            0 -> {
                SeerrDiscoverPage(
                    preferences = preferences,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    wasOpenedViaTopNavSwitch = wasOpenedViaTopNavSwitch,
                    navHasFocus = navHasFocus,
                )
            }

            // Requests
            1 -> {
                SeerrRequestsPage(
                    focusRequesterOnEmpty = tabFocusRequesters.getOrNull(selectedTabIndex),
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $selectedTabIndex", null)
            }
        }
    }
}
