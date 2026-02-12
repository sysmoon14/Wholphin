package com.github.sysmoon.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import androidx.tv.material3.surfaceColorAtElevation
import com.github.sysmoon.wholphin.preferences.AppPreference
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.ui.handleDPadKeyEvents
import com.github.sysmoon.wholphin.ui.playOnClickSound

val PreferenceTileSize = 160.dp

/**
 * Square tile for one preference. Booleans show a Switch; others show label, optional [valueSummary], and open a popup on click.
 * [nextFocus], [previousFocus], [upFocus], [downFocus] define D-pad navigation order.
 * [icon] is optional and shown above the title.
 * When [enabled] is false, the tile is greyed out and not clickable (e.g. plugin-controlled settings).
 */
@Composable
fun PreferenceTile(
    pref: AppPreference<AppPreferences, *>,
    groupIndex: Int,
    prefIndex: Int,
    value: Any?,
    valueSummary: String?,
    icon: ImageVector?,
    modifier: Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    nextFocus: androidx.compose.ui.focus.FocusRequester?,
    previousFocus: androidx.compose.ui.focus.FocusRequester?,
    downFocus: androidx.compose.ui.focus.FocusRequester?,
    upFocus: androidx.compose.ui.focus.FocusRequester?,
    focusedIndex: Pair<Int, Int>,
    setFocusedIndex: (Pair<Int, Int>) -> Unit,
    movementSounds: Boolean,
    onFocus: (Int, Int) -> Unit,
    onTileClick: () -> Unit,
    onToggle: (() -> Unit)?,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val focusedByInteraction = interactionSource.collectIsFocusedAsState().value
    var focusedByFocusChange by remember { mutableStateOf(false) }
    val isTrackedAsFocused = (focusedIndex == Pair(groupIndex, prefIndex))
    val focused = focusedByInteraction || focusedByFocusChange || isTrackedAsFocused
    LaunchedEffect(focused) {
        if (focused) {
            setFocusedIndex(Pair(groupIndex, prefIndex))
            if (movementSounds) playOnClickSound(context)
            onFocus(groupIndex, prefIndex)
        }
    }
    val background =
        if (focused) MaterialTheme.colorScheme.inverseSurface
        else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val contentColor = contentColorFor(background).let { if (enabled) it else it.copy(alpha = 0.5f) }
    val shape = RoundedCornerShape(12.dp)
    val borderWidth = if (focused) 3.dp else 1.dp
    val borderColor = if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier =
            modifier
                .size(PreferenceTileSize)
                .padding(4.dp)
                .then(if (!enabled) Modifier.graphicsLayer { alpha = 0.6f } else Modifier)
                .focusRequester(focusRequester)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .onFocusChanged { focusState ->
                    focusedByFocusChange = focusState.isFocused
                }
                .focusProperties {
                    next = nextFocus ?: FocusRequester.Default
                    previous = previousFocus ?: FocusRequester.Default
                    down = downFocus ?: FocusRequester.Default
                    up = upFocus ?: FocusRequester.Default
                }
                .handleDPadKeyEvents(onCenter = { if (enabled) onTileClick() })
                .clickable(enabled = enabled, onClick = onTileClick)
                .background(background, shape = shape)
                .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            // Fixed-height icon slot so tiles align when some have icon, some don't
            Box(
                modifier = Modifier.height(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            // Fixed-height title slot (2 lines) so different title lengths don't shift layout
            Text(
                text = stringResource(pref.title),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.heightIn(min = 36.dp),
            )
            // Fixed-height bottom slot: switch, value summary, or empty
            Box(
                modifier = Modifier.height(40.dp).padding(top = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (onToggle != null) {
                    Switch(
                        checked = value as? Boolean ?: false,
                        onCheckedChange = { if (enabled) onToggle() },
                        colors = SwitchColors(),
                        enabled = enabled,
                    )
                } else if (!valueSummary.isNullOrBlank()) {
                    Text(
                        text = valueSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
