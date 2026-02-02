package com.github.sysmoon.wholphin.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.services.BackdropService
import com.github.sysmoon.wholphin.services.NavigationManager
import com.github.sysmoon.wholphin.ui.CrossFadeFactory
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// Top scrim configuration for text readability (clock, season tabs)
private const val TOP_SCRIM_ALPHA = 0.55f
private const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
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
    val backdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
    val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
    Box(
        modifier = modifier,
    ) {
        val baseBackgroundColor = MaterialTheme.colorScheme.background
        if (backdrop.hasColors &&
            (backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR || backdropStyle == BackdropStyle.UNRECOGNIZED)
        ) {
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
        NavDisplay(
            backStack = navigationManager.backStack,
            onBack = { navigationManager.goBack() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = { key ->
                key as Destination
                val contentKey = "${key}_${server?.id}_${user?.id}"
                NavEntry(key, contentKey = contentKey) {
                    if (key.fullScreen) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (user != null && server != null) {
                        NavDrawer(
                            destination = key,
                            preferences = preferences,
                            user = user,
                            server = server,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorMessage("Trying to go to $key without a user logged in", null)
                    }
                }
            },
        )
    }
}
