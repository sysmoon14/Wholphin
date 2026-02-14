package com.github.sysmoon.wholphin.ui.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.sysmoon.wholphin.data.filter.DefaultFilterOptions
import com.github.sysmoon.wholphin.data.filter.ItemFilterBy
import com.github.sysmoon.wholphin.data.model.CollectionFolderFilter
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.components.CollectionFolderGrid
import com.github.sysmoon.wholphin.ui.components.ViewOptionsPoster
import com.github.sysmoon.wholphin.ui.components.ViewOptionsWide
import com.github.sysmoon.wholphin.ui.data.VideoSortOptions
import com.github.sysmoon.wholphin.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID

@Composable
fun CollectionFolderGeneric(
    preferences: UserPreferences,
    itemId: UUID,
    usePosters: Boolean,
    recursive: Boolean,
    playEnabled: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    sortOptions: List<ItemSortBy> = VideoSortOptions,
    wasOpenedViaTopNavSwitch: Boolean = false,
    navHasFocus: Boolean = false,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by remember { mutableStateOf(true) }
    val viewOptions =
        remember(usePosters) {
            if (usePosters) {
                ViewOptionsPoster
            } else {
                ViewOptionsWide
            }
        }
    CollectionFolderGrid(
        preferences = preferences,
        onClickItem = { _, item -> preferencesViewModel.navigationManager.navigateTo(item.destination()) },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = sortOptions,
        modifier =
            modifier
                .padding(start = 16.dp),
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = viewOptions,
        playEnabled = playEnabled,
        filterOptions = filterOptions,
        deferInitialFocus = wasOpenedViaTopNavSwitch,
        navHasFocus = navHasFocus,
    )
}
