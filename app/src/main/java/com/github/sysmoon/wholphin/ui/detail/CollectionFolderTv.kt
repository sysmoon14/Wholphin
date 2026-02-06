package com.github.sysmoon.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.components.RecommendedTvShow
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.preferences.PreferencesViewModel

@Composable
fun CollectionFolderTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val recommendedTopRowFocusRequester = remember { FocusRequester() }
    RecommendedTvShow(
        preferences = preferences,
        parentId = destination.itemId,
        onFocusPosition = {},
        topRowFocusRequester = recommendedTopRowFocusRequester,
        resetPositionOnEnter = true,
        consumeDownToTopRow = true,
        modifier = modifier.fillMaxSize(),
    )
}
