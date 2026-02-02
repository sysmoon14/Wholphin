package com.github.sysmoon.wholphin.ui.nav

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.SetupDestination
import com.github.sysmoon.wholphin.services.SetupNavigationManager
import com.github.sysmoon.wholphin.ui.preferences.PreferenceScreenOption
import com.github.sysmoon.wholphin.ui.setup.UserIconCardImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Top navigation bar (Netflix-style): profile left, word nav items in center, settings right.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopNavBar(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    viewModel: NavDrawerViewModel,
    onNavigateDown: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val moreLibraries by viewModel.moreLibraries.observeAsState(initial = listOf())
    val libraries by viewModel.libraries.observeAsState(initial = listOf())
    val selectedIndex by viewModel.selectedIndex.observeAsState(initial = -1)
    val showMore by viewModel.showMore.observeAsState(initial = false)
    LaunchedEffect(Unit) { viewModel.init() }

    // Use null-safe lists in case LiveData hasn't emitted yet (moreLibraries starts as null in VM)
    val librariesList = libraries ?: emptyList()
    val moreLibrariesList = moreLibraries ?: emptyList()

    // VM selectedIndex: -2=Search, -1=Home, 0..libraries.size-1=libraries, libraries.size=More, libraries.size+1=Discover, libraries.size+2..=moreLibraries
    val allNavItems: List<Pair<Int, NavDrawerItem>> = buildList {
        add(-2 to NavDrawerItem.Search)
        add(-1 to NavDrawerItem.Home)
        librariesList.forEachIndexed { index, item -> add(index to item) }
        if (showMore) {
            moreLibrariesList.forEachIndexed { index, item -> add(librariesList.size + 2 + index to item) }
        } else if (moreLibrariesList.isNotEmpty()) {
            add(librariesList.size to NavDrawerItem.More)
        }
    }
    val downKeyModifier =
        if (onNavigateDown != null) {
            Modifier.onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionDown) {
                    if (event.type == KeyEventType.KeyUp) {
                        onNavigateDown.invoke()
                    }
                    true
                } else {
                    false
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Profile (pinned left)
            val userImageUrl = remember(user) { viewModel.getUserImage(user) }
            TopNavProfileButton(
                user = user,
                imageUrl = userImageUrl,
                onClick = {
                    viewModel.setupNavigationManager.navigateTo(SetupDestination.UserList(server))
                },
                modifier = downKeyModifier,
            )

            // Center: nav items (centered in the remaining space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .focusGroup()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allNavItems.forEach { (index, item) ->
                        when (item) {
                            NavDrawerItem.Search -> {
                                TopNavSearchIconButton(
                                    selected = selectedIndex == -2,
                                    onClick = {
                                        viewModel.setIndex(-2)
                                        viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
                                    },
                                    modifier = downKeyModifier,
                                )
                            }
                            NavDrawerItem.More -> {
                                TopNavWordItem(
                                    text = context.getString(R.string.more),
                                    selected = false,
                                    onClick = { viewModel.setShowMore(!showMore) },
                                    modifier = downKeyModifier,
                                )
                            }
                            else -> {
                                val isSelected = selectedIndex == index
                                TopNavWordItem(
                                    text = item.name(context),
                                    selected = isSelected,
                                    onClick = {
                                        when (item) {
                                            NavDrawerItem.Favorites -> {
                                                viewModel.setIndex(index)
                                                viewModel.navigationManager.navigateToFromDrawer(Destination.Favorites)
                                            }
                                            NavDrawerItem.Discover -> {
                                                viewModel.setIndex(index)
                                                viewModel.navigationManager.navigateToFromDrawer(Destination.Discover)
                                            }
                                            is ServerNavDrawerItem -> {
                                                viewModel.setIndex(index)
                                                viewModel.navigationManager.navigateToFromDrawer(item.destination)
                                            }
                                            NavDrawerItem.Home -> {
                                                viewModel.setIndex(-1)
                                                if (destination is Destination.Home) {
                                                    viewModel.navigationManager.reloadHome()
                                                } else {
                                                    viewModel.navigationManager.goToHome()
                                                }
                                            }
                                            else -> { /* More handled separately */ }
                                        }
                                    },
                                    modifier = downKeyModifier,
                                )
                            }
                        }
                    }
                }
            }

            // Settings (pinned right)
            TopNavSettingsButton(
                onClick = {
                    viewModel.navigationManager.navigateTo(
                        Destination.Settings(PreferenceScreenOption.BASIC),
                    )
                },
                modifier = downKeyModifier,
            )
        }
    }
}

private val NavBarPillShape = RoundedCornerShape(20.dp)

@Composable
private fun RowScope.TopNavProfileButton(
    user: JellyfinUser,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = ClickableSurfaceDefaults.shape(shape = NavBarPillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = NavBarPillShape,
            ),
        ),
        interactionSource = interactionSource,
    ) {
        UserIconCardImage(
            id = user.id,
            name = user.name,
            imageUrl = imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(28.dp),
            alpha = 1f,
        )
    }
}

@Composable
private fun TopNavSearchIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val backgroundColor by animateColorAsState(
        if (selected) Color(0xFF505050) else Color.Transparent,
        label = "search_bg",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = ClickableSurfaceDefaults.shape(shape = NavBarPillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = NavBarPillShape,
            ),
        ),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.search),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun TopNavWordItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val containerColor by animateColorAsState(
        if (selected) Color(0xFF505050) else Color.Transparent,
        label = "nav_item_bg",
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .padding(horizontal = 12.dp),
        shape = ClickableSurfaceDefaults.shape(shape = NavBarPillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = NavBarPillShape,
            ),
        ),
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun RowScope.TopNavSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(shape = NavBarPillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = NavBarPillShape,
            ),
        ),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.settings),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp),
        )
    }
}
