package com.github.sysmoon.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.github.sysmoon.wholphin.ui.Spacing
import kotlinx.coroutines.delay

/**
 * Default delay before showing focused content overlay (in milliseconds)
 */
const val DEFAULT_FOCUS_OVERLAY_DELAY = 500L

/**
 * Longer delay used for PersonCard (in milliseconds)
 */
const val PERSON_CARD_FOCUS_OVERLAY_DELAY = 1_000L

/**
 * Holds the animated focus state for card components.
 *
 * @property focused Whether the card is currently focused
 * @property focusedAfterDelay Whether the card has been focused for the specified delay duration
 * @property spaceBetween Animated spacing between card content (larger when focused)
 * @property spaceBelow Animated spacing below the card (smaller when focused)
 */
data class CardFocusState(
    val focused: Boolean,
    val focusedAfterDelay: Boolean,
    val spaceBetween: Dp,
    val spaceBelow: Dp,
)

/**
 * Remembers and manages card focus state with animated spacing and delayed focus detection.
 *
 * This composable extracts the common focus handling pattern used across card components:
 * - Tracks focus state from the interaction source
 * - Animates spacing values based on focus state
 * - Provides a delayed focus indicator for overlay visibility
 *
 * @param interactionSource The interaction source to monitor for focus changes
 * @param hideOverlayDelay Delay in milliseconds before [CardFocusState.focusedAfterDelay] becomes true
 * @return A [CardFocusState] containing the current focus state and animated values
 */
@Composable
fun rememberCardFocusState(
    interactionSource: InteractionSource,
    hideOverlayDelay: Long = DEFAULT_FOCUS_OVERLAY_DELAY,
): CardFocusState {
    val focused by interactionSource.collectIsFocusedAsState()
    val spaceBetween by animateDpAsState(
        if (focused) Spacing.medium else Spacing.extraSmall,
        label = "spaceBetween",
    )
    val spaceBelow by animateDpAsState(
        if (focused) Spacing.extraSmall else Spacing.medium,
        label = "spaceBelow",
    )
    var focusedAfterDelay by remember { mutableStateOf(false) }

    if (focused) {
        LaunchedEffect(Unit) {
            delay(hideOverlayDelay)
            if (focused) {
                focusedAfterDelay = true
            } else {
                focusedAfterDelay = false
            }
        }
    } else {
        focusedAfterDelay = false
    }

    return CardFocusState(
        focused = focused,
        focusedAfterDelay = focusedAfterDelay,
        spaceBetween = spaceBetween,
        spaceBelow = spaceBelow,
    )
}
