package com.github.sysmoon.wholphin.ui.nav

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.NavDrawerItemRepository
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.preferences.AppThemeColors
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.services.SeerrServerRepository
import com.github.sysmoon.wholphin.services.SetupDestination
import com.github.sysmoon.wholphin.services.SetupNavigationManager
import com.github.sysmoon.wholphin.ui.FontAwesome
import com.github.sysmoon.wholphin.ui.components.TimeDisplay
import com.github.sysmoon.wholphin.ui.ifElse
import com.github.sysmoon.wholphin.ui.launchIO
import com.github.sysmoon.wholphin.ui.preferences.PreferenceScreenOption
import com.github.sysmoon.wholphin.ui.setValueOnMain
import com.github.sysmoon.wholphin.ui.setup.UserIconCardImage
import com.github.sysmoon.wholphin.ui.spacedByWithFooter
import com.github.sysmoon.wholphin.ui.theme.LocalTheme
import com.github.sysmoon.wholphin.ui.toServerString
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.ExceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val backdropService: BackdropService,
        private val seerrServerRepository: SeerrServerRepository,
    ) : ViewModel() {
        //        private var all: List<NavDrawerItem>? = null
        val moreLibraries = MutableLiveData<List<NavDrawerItem>>(listOf())
        val libraries = MutableLiveData<List<NavDrawerItem>>(listOf())
        val selectedIndex = MutableLiveData(-1)
        val showMore = MutableLiveData(false)
        /** True while the top nav bar has focus. Content must not request focus when this is true. */
        val navHasFocus = MutableLiveData(false)

        init {
            seerrServerRepository.active
                .onEach {
                    init()
                }.launchIn(viewModelScope)
        }

        fun init() {
            viewModelScope.launchIO {
                val all = navDrawerItemRepository.getNavDrawerItems()
//                this@NavDrawerViewModel.all = all
                val libraries = navDrawerItemRepository.getFilteredNavDrawerItems(all)
                val moreLibraries = all.toMutableList().apply { removeAll(libraries) }

                withContext(Dispatchers.Main) {
                    this@NavDrawerViewModel.moreLibraries.value = moreLibraries
                    this@NavDrawerViewModel.libraries.value = libraries
                }
                val asDestinations =
                    (libraries + listOf(NavDrawerItem.Discover)).map {
                        if (it is ServerNavDrawerItem) {
                            it.destination
                        } else if (it is NavDrawerItem.Favorites) {
                            Destination.Favorites
                        } else if (it is NavDrawerItem.Discover) {
                            Destination.Discover
                        } else {
                            null
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    if (key is Destination) {
                        val index =
                            if (key is Destination.Home) {
                                -1
                            } else if (key is Destination.Search) {
                                -2
                            } else {
                                val idx = asDestinations.indexOf(key)
                                if (idx >= 0) {
                                    idx
                                } else {
                                    null
                                }
                            }
                        Timber.v("Found $index => $key")
                        if (index != null) {
                            selectedIndex.setValueOnMain(index)
                            break
                        }
                    }
                }
            }
        }

        fun setIndex(index: Int) {
            selectedIndex.value = index
        }

        fun setShowMore(value: Boolean) {
            showMore.value = value
        }

        fun getUserImage(user: JellyfinUser): String = api.imageApi.getUserImageUrl(user.id)
    }

sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Search : NavDrawerItem {
        override val id: String get() = "search"
        override fun name(context: Context): String = context.getString(R.string.search)
    }

    object Home : NavDrawerItem {
        override val id: String get() = "home"
        override fun name(context: Context): String = context.getString(R.string.home)
    }

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    object More : NavDrawerItem {
        override val id: String
            get() = "a_more"

        override fun name(context: Context): String = context.getString(R.string.more)
    }

    object Discover : NavDrawerItem {
        override val id: String
            get() = "a_discover"

        override fun name(context: Context): String = context.getString(R.string.discover)
    }
}

data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = "s_" + itemId.toServerString()

    override fun name(context: Context): String = name
}

/**
 * Display top navigation bar with [DestinationContent] below (Netflix-style layout).
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    onClearBackdrop: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()
                ?: (LocalView.current.context as? ComponentActivity)
                ?: error("NavDrawer must be used from an Activity or a view with a ViewModelStoreOwner"),
            key = "${server?.id}_${user?.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    val showMore by viewModel.showMore.observeAsState(false)

    BackHandler(enabled = showMore) {
        viewModel.setShowMore(false)
    }

    val homeTopRowFocusRequester = remember { FocusRequester() }
    val showTopNavBar = destination.shouldShowTopNavBar()

    Column(modifier = modifier.fillMaxSize()) {
        if (showTopNavBar) {
            TopNavBar(
                destination = destination,
                preferences = preferences,
                user = user,
                server = server,
                viewModel = viewModel,
                onNavigateDown =
                    if (destination is Destination.Home) {
                        {
                            homeTopRowFocusRequester.tryRequestFocus("top_nav_to_home")
                        }
                    } else {
                        null
                    },
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            DestinationContent(
                destination = destination,
                preferences = preferences,
                onClearBackdrop = onClearBackdrop,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.fillMaxSize(),
                homeTopRowFocusRequester = homeTopRowFocusRequester,
            )
            if (preferences.appPreferences.interfacePreferences.showClock) {
                TimeDisplay()
            }
        }
    }
}

@Composable
fun NavigationDrawerScope.ProfileIcon(
    user: JellyfinUser,
    imageUrl: String?,
    serverName: String,
    drawerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        leadingContent = {
            UserIconCardImage(
                id = user.id,
                name = user.name,
                imageUrl = imageUrl,
                alpha = if (drawerOpen) 1f else .5f,
                modifier = Modifier.fillMaxSize(),
            )
        },
        supportingContent = {
            Text(
                text = serverName,
                maxLines = 1,
            )
        },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = user.name ?: user.id.toString(),
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(0.dp),
            )
        },
        supportingContent =
            subtext?.let {
                {
                    Text(
                        text = it,
                        maxLines = 1,
                    )
                }
            },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = text,
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.NavItem(
    library: NavDrawerItem,
    onClick: () -> Unit,
    selected: Boolean,
    moreExpanded: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    containerColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val useFont = library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV
    val icon =
        when (library) {
            NavDrawerItem.Favorites -> {
                R.string.fa_heart
            }

            NavDrawerItem.More -> {
                R.string.fa_ellipsis
            }

            NavDrawerItem.Discover -> {
                R.string.fa_magnifying_glass_plus
            }

            NavDrawerItem.Search -> {
                R.string.fa_magnifying_glass_plus
            }

            NavDrawerItem.Home -> {
                R.string.fa_house
            }

            is ServerNavDrawerItem -> {
                when (library.type) {
                    CollectionType.MOVIES -> R.string.fa_film
                    CollectionType.TVSHOWS -> R.string.fa_tv
                    CollectionType.HOMEVIDEOS -> R.string.fa_video
                    CollectionType.LIVETV -> R.drawable.gf_dvr
                    CollectionType.MUSIC -> R.string.fa_music
                    CollectionType.BOXSETS -> R.string.fa_open_folder
                    CollectionType.PLAYLISTS -> R.string.fa_list_ul
                    else -> R.string.fa_film
                }
            }
        }
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        colors =
            NavigationDrawerItemDefaults.colors(
                containerColor = containerColor,
            ),
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (useFont) {
                    Text(
                        text = stringResource(icon),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontFamily = FontAwesome,
                        color = color,
                        modifier = Modifier,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier,
                    )
                }
            }
        },
        trailingContent = {
            if (library is NavDrawerItem.More) {
                Icon(
                    imageVector = if (moreExpanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowLeft,
                    contentDescription = null,
                )
            }
        },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = library.name(context),
            maxLines = 1,
        )
    }
}

@Composable
fun navItemColor(
    selected: Boolean,
    focused: Boolean,
    drawerOpen: Boolean,
): Color {
    val theme = LocalTheme.current
    if (theme == AppThemeColors.OLED_BLACK) {
        return when {
            selected && focused -> Color.Black
            selected && !drawerOpen -> Color.White.copy(alpha = .5f)
            selected && drawerOpen -> Color.White.copy(alpha = .85f)
            focused -> Color.Black.copy(alpha = .5f)
            drawerOpen -> Color(0xFF707070)
            else -> Color(0xFF505050).copy(alpha = .66f)
        }
    } else {
        val alpha =
            when {
                drawerOpen -> .85f
                selected && !drawerOpen -> .5f
                else -> .2f
            }
        return when {
            selected && focused -> {
                when (theme) {
                    AppThemeColors.UNRECOGNIZED,
                    AppThemeColors.PURPLE,
                    AppThemeColors.BLUE,
                    AppThemeColors.GREEN,
                    AppThemeColors.ORANGE,
                    -> MaterialTheme.colorScheme.border

                    AppThemeColors.BOLD_BLUE,
                    AppThemeColors.OLED_BLACK,
                    -> MaterialTheme.colorScheme.primary
                }
            }

            selected -> {
                MaterialTheme.colorScheme.border
            }

            focused -> {
                LocalContentColor.current
            }

            else -> {
                MaterialTheme.colorScheme.onSurface
            }
        }.copy(alpha = alpha)
    }
}

val DrawerState.isOpen: Boolean get() = this.currentValue == DrawerValue.Open
