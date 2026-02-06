package com.github.sysmoon.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.components.RecommendedMovie
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.preferences.PreferencesViewModel

@Composable
fun CollectionFolderMovie(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val recommendedTopRowFocusRequester = remember { FocusRequester() }
    RecommendedMovie(
        preferences = preferences,
        parentId = destination.itemId,
        onFocusPosition = {},
        resetPositionOnEnter = true,
        topRowFocusRequester = recommendedTopRowFocusRequester,
        consumeDownToTopRow = true,
        dropEmptyRows = true,
        modifier = modifier.fillMaxSize(),
    )
}
