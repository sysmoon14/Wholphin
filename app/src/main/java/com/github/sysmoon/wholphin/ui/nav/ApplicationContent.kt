package com.github.sysmoon.wholphin.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.preferences.BackdropStyle
import org.jellyfin.sdk.model.api.BaseItemKind
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.ui.CrossFadeFactory
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.TimeDisplay
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// Top scrim configuration for text readability (clock, season tabs)
private const val TOP_SCRIM_ALPHA = 0.55f
private const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height
// Backdrop image dimming and scrim for content readability (Netflix-style)
private const val BACKDROP_IMAGE_ALPHA = 0.5f

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        /**
         * Clears the backdrop image. Deferred to the next run loop iteration so that
         * the StateFlow update does not trigger parent recomposition in the same
         * frame as the new destination's composition, which can cause an AssertionError
         * in the Compose runtime (slot table / recompose scope cleared while in use).
         */
        fun clearBackdrop() {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                viewModelScope.launchIO { backdropService.clearBackdrop() }
            }
        }
    }

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    startDestination: Destination,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val backStack: MutableList<NavKey> =
        rememberSerializable(
            server,
            user,
            serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer()),
        ) {
            NavBackStack(startDestination)
        }
    navigationManager.backStack = backStack
    LaunchedEffect(Unit) { navigationManager.syncCurrentDestinationFromBackStack() }
    val currentDestination by navigationManager.currentDestination.collectAsStateWithLifecycle(initialValue = null)
    val showTopNavBar = currentDestination?.shouldShowTopNavBar() == true
    // Consume once per destination change: true = opened via tab switch (keep focus in nav), false = initial load (focus content).
    val wasOpenedViaTopNavSwitch = remember(currentDestination) {
        navigationManager.consumeOpenedViaTopNavSwitch()
    }
    val homeTopRowFocusRequester = remember { FocusRequester() }
    val topNavFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val navDrawerViewModel: NavDrawerViewModel = hiltViewModel(key = "${server.id}_${user.id}")
    LaunchedEffect(currentDestination) {
        navDrawerViewModel.syncSelectedIndexFromBackStack()
    }
    val showMore by navDrawerViewModel.showMore.observeAsState(initial = false)
    val navHasFocus by navDrawerViewModel.navHasFocus.observeAsState(initial = false)
    BackHandler(enabled = showMore) { navDrawerViewModel.setShowMore(false) }
    // When at root (e.g. Home tab) with focus in content: first Back moves focus to top nav; second Back closes app
    BackHandler(enabled = showTopNavBar && !navHasFocus && backStack.size == 1) {
        topNavFocusRequester.tryRequestFocus("back_to_top_nav")
    }
    val backdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
    val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
    val dest = currentDestination
    val showBackdropImage =
        when (dest) {
            is Destination.MediaItem ->
                dest.type in setOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                )
            is Destination.SeriesOverview -> true
            Destination.Discover -> true
            is Destination.DiscoveredItem -> true
            else -> false
        }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        val baseBackgroundColor = MaterialTheme.colorScheme.background
        // Full-screen backdrop image only on detail pages (movie/series/episode/season list), not home
        if (showBackdropImage &&
            backdrop.imageUrl.isNotNullOrBlank() &&
            backdropStyle != BackdropStyle.BACKDROP_NONE
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().clipToBounds(),
            ) {
                val density = LocalDensity.current
                // Shift image left so the visible portion shows more of the right side of the backdrop
                val shiftPx = with(density) { maxWidth.toPx() * 0.25f }
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(backdrop.imageUrl)
                                .transitionFactory(CrossFadeFactory(400.milliseconds))
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = shiftPx }
                                .alpha(BACKDROP_IMAGE_ALPHA),
                    )
                }
                // Scrim for text readability: gradient from transparent to dark bottom
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.4f),
                                                    Color.Black.copy(alpha = 0.75f),
                                                ),
                                            startY = 0f,
                                            endY = size.height,
                                        ),
                                )
                            },
                )
                // Left edge gradient: in front of backdrop image, behind content (logo, text)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val gradientWidth = size.width * 0.65f
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors =
                                                listOf(
                                                    Color.Black,
                                                    Color.Black,
                                                    Color.Black,
                                                    Color.Black.copy(alpha = 0.95f),
                                                    Color.Black.copy(alpha = 0.9f),
                                                    Color.Black.copy(alpha = 0.7f),
                                                    Color.Black.copy(alpha = 0.45f),
                                                    Color.Transparent,
                                                ),
                                            startX = 0f,
                                            endX = gradientWidth,
                                        ),
                                )
                            },
                )
            }
        }
        if (backdrop.hasColors &&
            (backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR || backdropStyle == BackdropStyle.UNRECOGNIZED)
        ) {
            val backdropAlpha = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                backdropAlpha.animateTo(1f, tween(400))
            }
            val animPrimary by animateColorAsState(
                backdrop.primaryColor,
                animationSpec = tween(1250),
                label = "dynamic_backdrop_primary",
            )
            val animSecondary by animateColorAsState(
                backdrop.secondaryColor,
                animationSpec = tween(1250),
                label = "dynamic_backdrop_secondary",
            )
            val animTertiary by animateColorAsState(
                backdrop.tertiaryColor,
                animationSpec = tween(1250),
                label = "dynamic_backdrop_tertiary",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(backdropAlpha.value)
                        .then(
                            if (showBackdropImage && backdrop.imageUrl.isNotNullOrBlank()) {
                                Modifier.alpha(0.75f)
                            } else {
                                Modifier
                            },
                        )
                        .drawBehind {
                            drawRect(color = baseBackgroundColor)
                            // Top Left (Vibrant/Muted)
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(animSecondary, Color.Transparent),
                                        center = Offset(0f, 0f),
                                        radius = size.width * 0.8f,
                                    ),
                            )
                            // Bottom Right (DarkVibrant/DarkMuted)
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(animPrimary, Color.Transparent),
                                        center = Offset(size.width, size.height),
                                        radius = size.width * 0.8f,
                                    ),
                            )
                            // Bottom Left (Dark / Bridge)
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                baseBackgroundColor,
                                                Color.Transparent,
                                            ),
                                        center = Offset(0f, size.height),
                                        radius = size.width * 0.8f,
                                    ),
                            )
                            // Top Right (Under Image - Vibrant/Bright)
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(animTertiary, Color.Transparent),
                                        center = Offset(size.width, 0f),
                                        radius = size.width * 0.8f,
                                    ),
                            )
                        },
            )
        }
        val navDirection = navigationManager.lastTopNavDirection
        val slideDuration = 220
        Column(modifier = Modifier.fillMaxSize()) {
            if (showTopNavBar && currentDestination != null) {
                TopNavBar(
                    destination = currentDestination!!,
                    preferences = preferences,
                    user = user,
                    server = server,
                    viewModel = navDrawerViewModel,
                    onNavigateDown = { focusManager.moveFocus(FocusDirection.Down) },
                    contentAreaUpFocusRequester = topNavFocusRequester,
                )
            }
            Box(
                modifier =
                    (if (showTopNavBar) Modifier.fillMaxWidth().weight(1f)
                    else Modifier.fillMaxSize())
                        .then(
                            if (showTopNavBar) {
                                Modifier.focusProperties { up = topNavFocusRequester }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                NavDisplay(
                    backStack = navigationManager.backStack,
                    onBack = { navigationManager.goBack() },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    transitionSpec = {
                        when (navDirection) {
                            1 -> ContentTransform(
                                targetContentEnter =
                                    slideInHorizontally(animationSpec = tween(slideDuration)) { it } + fadeIn(animationSpec = tween(slideDuration)),
                                initialContentExit =
                                    slideOutHorizontally(animationSpec = tween(slideDuration)) { -it } + fadeOut(animationSpec = tween(slideDuration)),
                                sizeTransform = null,
                            )
                            -1 -> ContentTransform(
                                targetContentEnter =
                                    slideInHorizontally(animationSpec = tween(slideDuration)) { -it } + fadeIn(animationSpec = tween(slideDuration)),
                                initialContentExit =
                                    slideOutHorizontally(animationSpec = tween(slideDuration)) { it } + fadeOut(animationSpec = tween(slideDuration)),
                                sizeTransform = null,
                            )
                            else -> ContentTransform(
                                targetContentEnter = fadeIn(animationSpec = tween(350)),
                                initialContentExit = fadeOut(animationSpec = tween(350)),
                                sizeTransform = null,
                            )
                        }
                    },
                    popTransitionSpec = {
                        when (navDirection) {
                            1 -> ContentTransform(
                                targetContentEnter =
                                    slideInHorizontally(animationSpec = tween(slideDuration)) { -it } + fadeIn(animationSpec = tween(slideDuration)),
                                initialContentExit =
                                    slideOutHorizontally(animationSpec = tween(slideDuration)) { it } + fadeOut(animationSpec = tween(slideDuration)),
                                sizeTransform = null,
                            )
                            -1 -> ContentTransform(
                                targetContentEnter =
                                    slideInHorizontally(animationSpec = tween(slideDuration)) { -it } + fadeIn(animationSpec = tween(slideDuration)),
                                initialContentExit =
                                    slideOutHorizontally(animationSpec = tween(slideDuration)) { it } + fadeOut(animationSpec = tween(slideDuration)),
                                sizeTransform = null,
                            )
                            else -> ContentTransform(
                                targetContentEnter = fadeIn(animationSpec = tween(350)),
                                initialContentExit = fadeOut(animationSpec = tween(350)),
                                sizeTransform = null,
                            )
                        }
                    },
                    entryProvider = { key ->
                        key as Destination
                        val contentKey = "${key}_${server.id}_${user.id}"
                        NavEntry(key, contentKey = contentKey) {
                            if (key.fullScreen) {
                                DestinationContent(
                                    destination = key,
                                    preferences = preferences,
                                    onClearBackdrop = viewModel::clearBackdrop,
                                    onNavigateBack = { navigationManager.goBack() },
                                    modifier = Modifier.fillMaxSize(),
                                    navHasFocus = navHasFocus,
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    DestinationContent(
                                        destination = key,
                                        preferences = preferences,
                                        onClearBackdrop = viewModel::clearBackdrop,
                                        onNavigateBack = { navigationManager.goBack() },
                                        modifier = Modifier.fillMaxSize(),
                                        homeTopRowFocusRequester = if (key is Destination.Home) homeTopRowFocusRequester else null,
                                        skipContentFocusUntilMillis = navigationManager.skipContentFocusUntilMillis,
                                        wasOpenedViaTopNavSwitch = key == currentDestination && wasOpenedViaTopNavSwitch,
                                        navHasFocus = navHasFocus,
                                    )
                                    if (preferences.appPreferences.interfacePreferences.showClock) {
                                        TimeDisplay()
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
