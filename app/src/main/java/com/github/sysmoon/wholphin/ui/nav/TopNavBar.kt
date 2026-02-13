package com.github.sysmoon.wholphin.ui.nav

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.github.sysmoon.wholphin.ui.tryRequestFocus
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
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

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
    val density = LocalDensity.current
    var navHasFocus by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    var navBoxPosition by remember { mutableStateOf(Offset.Zero) }
    val itemMetrics = remember { mutableStateMapOf<Int, NavItemMetrics>() }

    fun focusRequesterFor(key: Int): FocusRequester =
        focusRequesters.getOrPut(key) { FocusRequester() }
    val moreLibraries by viewModel.moreLibraries.observeAsState(initial = listOf())
    val libraries by viewModel.libraries.observeAsState(initial = listOf())
    val selectedIndex by viewModel.selectedIndex.observeAsState(initial = -1)
    val showMore by viewModel.showMore.observeAsState(initial = false)
    LaunchedEffect(Unit) { viewModel.init() }

    // Use null-safe lists in case LiveData hasn't emitted yet (moreLibraries starts as null in VM)
    val librariesList = libraries ?: emptyList()
    val moreLibrariesList = moreLibraries ?: emptyList()

    // Unpinned items are not shown; only Search, Home, and pinned libraries (no "More").
    // VM selectedIndex: -2=Search, -1=Home, 0..libraries.size-1=libraries, libraries.size=Discover
    val allNavItems: List<Pair<Int, NavDrawerItem>> = buildList {
        add(-2 to NavDrawerItem.Search)
        add(-1 to NavDrawerItem.Home)
        librariesList.forEachIndexed { index, item -> add(index to item) }
    }
    val hideSettingsCog = preferences.appPreferences.interfacePreferences.hideSettingsCog
    val defaultKey = allNavItems.firstOrNull()?.first ?: NavProfileKey
    val allKeys = allNavItems.map { it.first } + listOf(NavProfileKey) + if (hideSettingsCog) emptyList() else listOf(NavSettingsKey)
    allKeys.forEach { focusRequesterFor(it) }
    LaunchedEffect(navHasFocus, selectedIndex) {
        if (navHasFocus) {
            val targetKey =
                if (allNavItems.any { it.first == selectedIndex }) {
                    selectedIndex
                } else {
                    defaultKey
                }
            focusedIndex = targetKey
            focusRequesterFor(targetKey).tryRequestFocus("top_nav_enter")
        }
    }
    // Don't run delayed reclaim here (450ms/900ms) - it overwrites the user moving to another tab
    // before the delay completes. ApplicationContent already reclaims at 0/50/100ms.
    // Auto-navigate when focus moves to a different core nav item (no OK press needed).
    // Short delay so quick left/right to cross the bar doesn't trigger every tab.
    // Excludes profile and settings, which still require OK.
    LaunchedEffect(focusedIndex, selectedIndex) {
        val key = focusedIndex ?: return@LaunchedEffect
        if (key == NavProfileKey || key == NavSettingsKey) return@LaunchedEffect
        if (key == selectedIndex) return@LaunchedEffect
        delay(250)
        val item = allNavItems.find { it.first == key }?.second ?: return@LaunchedEffect
        viewModel.navigationManager.setLastTopNavDirection(selectedIndex, key)
        viewModel.navigationManager.setSkipContentFocusFor(800)
        viewModel.setIndex(key)
        when (item) {
            NavDrawerItem.Search ->
                viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
            NavDrawerItem.Favorites ->
                viewModel.navigationManager.navigateToFromDrawer(Destination.Favorites)
            NavDrawerItem.Discover ->
                viewModel.navigationManager.navigateToFromDrawer(Destination.Discover)
            is ServerNavDrawerItem ->
                viewModel.navigationManager.navigateToFromDrawer(item.destination)
            NavDrawerItem.Home -> {
                if (destination is Destination.Home) {
                    viewModel.navigationManager.reloadHome()
                } else {
                    viewModel.navigationManager.goToHome()
                }
            }
            else -> { /* More handled separately; not in top bar */ }
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
            .focusGroup()
            .onFocusChanged {
                navHasFocus = it.hasFocus
                if (!it.hasFocus) {
                    focusedIndex = null
                }
            }
            .focusProperties {
                onEnter = {
                    val targetKey =
                        if (allNavItems.any { it.first == selectedIndex }) {
                            selectedIndex
                        } else {
                            defaultKey
                        }
                    focusRequesterFor(targetKey)
                }
            }
            .onGloballyPositioned { coords ->
                navBoxPosition = coords.positionInRoot()
            }
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
        val selectedMetrics = itemMetrics[selectedIndex]
        val focusMetrics = itemMetrics[focusedIndex ?: selectedIndex]
        val selectedOffsetX =
            with(density) {
                (selectedMetrics?.x ?: 0f).toDp()
            }
        val selectedOffsetY =
            with(density) {
                (selectedMetrics?.y ?: 0f).toDp()
            }
        val selectedWidth =
            with(density) {
                (selectedMetrics?.width ?: 0f).toDp()
            }
        val selectedHeight =
            with(density) {
                (selectedMetrics?.height ?: 0f).toDp()
            }
        val focusOffsetX = with(density) { (focusMetrics?.x ?: 0f).toDp() }
        val focusOffsetY = with(density) { (focusMetrics?.y ?: 0f).toDp() }
        val focusWidth = with(density) { (focusMetrics?.width ?: 0f).toDp() }
        val focusHeight = with(density) { (focusMetrics?.height ?: 0f).toDp() }
        val selectedIndicatorOffsetX by animateDpAsState(
            targetValue = selectedOffsetX,
            label = "nav_selected_offset_x",
        )
        val selectedIndicatorOffsetY by animateDpAsState(
            targetValue = selectedOffsetY,
            label = "nav_selected_offset_y",
        )
        val selectedIndicatorWidth by animateDpAsState(
            targetValue = selectedWidth,
            label = "nav_selected_width",
        )
        val selectedIndicatorHeight by animateDpAsState(
            targetValue = selectedHeight,
            label = "nav_selected_height",
        )
        val focusIndicatorOffsetX by animateDpAsState(targetValue = focusOffsetX, label = "nav_focus_x")
        val focusIndicatorOffsetY by animateDpAsState(targetValue = focusOffsetY, label = "nav_focus_y")
        val focusIndicatorWidth by animateDpAsState(targetValue = focusWidth, label = "nav_focus_w")
        val focusIndicatorHeight by animateDpAsState(targetValue = focusHeight, label = "nav_focus_h")
        if (selectedMetrics != null) {
            Box(
                modifier =
                    Modifier
                        .offset(x = selectedIndicatorOffsetX, y = selectedIndicatorOffsetY)
                        .size(selectedIndicatorWidth, selectedIndicatorHeight)
                        .background(
                            color = Color(0xFF505050),
                            shape = selectedMetrics.shape,
                        ),
            )
        }
        if (navHasFocus && focusMetrics != null) {
            Box(
                modifier =
                    Modifier
                        .offset(x = focusIndicatorOffsetX, y = focusIndicatorOffsetY)
                        .size(focusIndicatorWidth, focusIndicatorHeight)
                        .background(
                            color = NavBarFocusedBackground,
                            shape = focusMetrics.shape,
                        ),
            )
        }
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
            val profileModifier =
                downKeyModifier
                    .focusRequester(focusRequesterFor(NavProfileKey))
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedIndex = NavProfileKey
                        }
                    }.onGloballyPositioned { coords ->
                        val position = coords.positionInRoot()
                        itemMetrics[NavProfileKey] =
                            NavItemMetrics(
                                x = position.x - navBoxPosition.x,
                                y = position.y - navBoxPosition.y,
                                width = coords.size.width.toFloat(),
                                height = coords.size.height.toFloat(),
                                shape = NavBarProfileShape,
                            )
                    }
            TopNavProfileButton(
                user = user,
                imageUrl = userImageUrl,
                onClick = {
                    viewModel.setupNavigationManager.navigateTo(SetupDestination.UserList(server))
                },
                modifier = profileModifier,
            )

            // Center: nav items (centered in the remaining space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        allNavItems.forEach { (index, item) ->
                            val navItemModifier =
                                downKeyModifier
                                    .focusRequester(focusRequesterFor(index))
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusedIndex = index
                                        }
                                    }.onGloballyPositioned { coords ->
                                        val position = coords.positionInRoot()
                                        itemMetrics[index] =
                                            NavItemMetrics(
                                                x = position.x - navBoxPosition.x,
                                                y = position.y - navBoxPosition.y,
                                                width = coords.size.width.toFloat(),
                                                height = coords.size.height.toFloat(),
                                                shape = NavBarPillShape,
                                            )
                                    }
                            when (item) {
                                NavDrawerItem.Search -> {
                                    TopNavSearchIconButton(
                                        selected = selectedIndex == -2,
                                        onClick = {
                                            viewModel.navigationManager.setLastTopNavDirection(selectedIndex, -2)
                                            viewModel.navigationManager.setSkipContentFocusFor(800)
                                            viewModel.setIndex(-2)
                                            viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
                                        },
                                        modifier = navItemModifier,
                                    )
                                }
                                NavDrawerItem.More -> {
                                    TopNavWordItem(
                                        text = context.getString(R.string.more),
                                        selected = false,
                                        onClick = { viewModel.setShowMore(!showMore) },
                                        modifier = navItemModifier,
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
                                                    viewModel.navigationManager.setLastTopNavDirection(selectedIndex, index)
                                                    viewModel.navigationManager.setSkipContentFocusFor(800)
                                                    viewModel.setIndex(index)
                                                    viewModel.navigationManager.navigateToFromDrawer(Destination.Favorites)
                                                }
                                                NavDrawerItem.Discover -> {
                                                    viewModel.navigationManager.setLastTopNavDirection(selectedIndex, index)
                                                    viewModel.navigationManager.setSkipContentFocusFor(800)
                                                    viewModel.setIndex(index)
                                                    viewModel.navigationManager.navigateToFromDrawer(Destination.Discover)
                                                }
                                                is ServerNavDrawerItem -> {
                                                    viewModel.navigationManager.setLastTopNavDirection(selectedIndex, index)
                                                    viewModel.navigationManager.setSkipContentFocusFor(800)
                                                    viewModel.setIndex(index)
                                                    viewModel.navigationManager.navigateToFromDrawer(item.destination)
                                                }
                                                NavDrawerItem.Home -> {
                                                    viewModel.navigationManager.setLastTopNavDirection(selectedIndex, -1)
                                                    viewModel.navigationManager.setSkipContentFocusFor(800)
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
                                        modifier = navItemModifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Settings (pinned right) – hidden when server sets hide_settings_cog
            if (!hideSettingsCog) {
                TopNavSettingsButton(
                    onClick = {
                        viewModel.navigationManager.navigateTo(
                            Destination.Settings(PreferenceScreenOption.BASIC),
                        )
                    },
                    modifier =
                        downKeyModifier
                            .focusRequester(focusRequesterFor(NavSettingsKey))
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedIndex = NavSettingsKey
                                }
                            }.onGloballyPositioned { coords ->
                                val position = coords.positionInRoot()
                                itemMetrics[NavSettingsKey] =
                                    NavItemMetrics(
                                        x = position.x - navBoxPosition.x,
                                        y = position.y - navBoxPosition.y,
                                        width = coords.size.width.toFloat(),
                                        height = coords.size.height.toFloat(),
                                        shape = NavBarPillShape,
                                    )
                            },
                )
            }
        }
    }
}

private val NavBarPillShape = RoundedCornerShape(20.dp)
private val NavBarProfileShape = RoundedCornerShape(4.dp)
private val NavBarFocusedBackground = Color.White.copy(alpha = 0.22f)
private const val NavProfileKey = -1000
private const val NavSettingsKey = -1001

private data class NavItemMetrics(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val shape: androidx.compose.ui.graphics.Shape,
)

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
        shape = ClickableSurfaceDefaults.shape(shape = NavBarProfileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shape = NavBarProfileShape,
            ),
            focusedBorder = Border.None,
        ),
        interactionSource = interactionSource,
    ) {
        UserIconCardImage(
            id = user.id,
            name = user.name,
            imageUrl = imageUrl,
            modifier = Modifier
                .fillMaxSize(),
            alpha = 1f,
            shape = NavBarProfileShape,
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
            focusedBorder = Border.None,
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
    val contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .padding(horizontal = 16.dp),
        shape = ClickableSurfaceDefaults.shape(shape = NavBarPillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
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
            focusedBorder = Border.None,
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
