package com.github.sysmoon.wholphin.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.ui.PreviewTvSpec
import com.github.sysmoon.wholphin.ui.ifElse
import com.github.sysmoon.wholphin.ui.theme.WholphinTheme
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import timber.log.Timber

@Composable
fun TabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    focusRequesters: List<FocusRequester>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, called with the tab index when that tab receives focus (for focus-to-navigate like top nav). */
    onTabFocused: ((Int) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    val density = LocalDensity.current
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex >= 0) {
            state.animateScrollToItem(selectedTabIndex, -(state.layoutInfo.viewportSize.width / 3.5).toInt())
        }
    }
    var rowHasFocus by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val tabMetrics = remember { mutableStateMapOf<Int, TabMetrics>() }
    var rowPosition by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier =
            modifier
                .onFocusChanged {
                    rowHasFocus = it.hasFocus
                    if (!it.hasFocus) {
                        focusedIndex = -1
                    }
                }.onGloballyPositioned { coords ->
                    rowPosition = coords.positionInRoot()
                }.focusGroup()
                .focusProperties {
                    onEnter = {
                        // If entering from left or right, use last or first tab
                        // Otherwise use the selected tab
                        Timber.v("onEnter requestedFocusDirection=$requestedFocusDirection, selectedTabIndex=$selectedTabIndex")
                        val focusRequester =
                            if (requestedFocusDirection == FocusDirection.Left) {
                                focusRequesters.lastOrNull()
                            } else if (requestedFocusDirection == FocusDirection.Right) {
                                focusRequesters.firstOrNull()
                            } else {
                                focusRequesters.getOrNull(selectedTabIndex)
                            }
                        (focusRequester ?: FocusRequester.Default).tryRequestFocus()
                    }
                },
    ) {
        val focusMetrics =
            tabMetrics[focusedIndex.takeIf { it >= 0 } ?: selectedTabIndex]
        val targetOffsetX =
            with(density) { (focusMetrics?.x ?: 0f).toDp() }
        val targetOffsetY =
            with(density) { (focusMetrics?.y ?: 0f).toDp() }
        val targetWidth =
            with(density) { (focusMetrics?.width ?: 0f).toDp() }
        val targetHeight =
            with(density) { (focusMetrics?.height ?: 0f).toDp() }
        val indicatorOffsetX by animateDpAsState(
            targetValue = targetOffsetX,
            label = "tab_focus_offset_x",
        )
        val indicatorOffsetY by animateDpAsState(
            targetValue = targetOffsetY,
            label = "tab_focus_offset_y",
        )
        val indicatorWidth by animateDpAsState(
            targetValue = targetWidth,
            label = "tab_focus_width",
        )
        val indicatorHeight by animateDpAsState(
            targetValue = targetHeight,
            label = "tab_focus_height",
        )
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(tabs) { index, tabTitle ->
                val interactionSource = remember { MutableInteractionSource() }
                Tab(
                    title = tabTitle,
                    selected = index == selectedTabIndex,
                    rowActive = rowHasFocus,
                    interactionSource = interactionSource,
                    onClick = {
                        onClick.invoke(index)
                    },
                    modifier =
                        Modifier
                            .focusRequester(focusRequesters[index])
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedIndex = index
                                    onTabFocused?.invoke(index)
                                }
                            }.onGloballyPositioned { coords ->
                                val position = coords.positionInRoot()
                                tabMetrics[index] =
                                    TabMetrics(
                                        x = position.x - rowPosition.x,
                                        y = position.y - rowPosition.y,
                                        width = coords.size.width.toFloat(),
                                        height = coords.size.height.toFloat(),
                                    )
                            },
                )
            }
        }
        if (rowHasFocus && focusMetrics != null) {
            Box(
                modifier =
                    Modifier
                        .offset(x = indicatorOffsetX, y = indicatorOffsetY)
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .border(2.dp, Color.White, shape),
            )
        }
    }
}

@Composable
fun Tab(
    title: String,
    selected: Boolean,
    rowActive: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (rowActive || selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
        }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            modifier
                .clickable(
                    enabled = true,
                    interactionSource = interactionSource,
                    onClick = onClick,
                    indication = null,
                ).background(
                    color = if (selected) Color(0xFF505050) else Color.Transparent,
                    shape = shape,
                ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier,
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

private data class TabMetrics(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@PreviewTvSpec
@Composable
private fun TabRowPreview() {
    WholphinTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            TabRow(
                selectedTabIndex = 1,
                tabs = listOf("Tab 1", "Tab 2", "Tab 3"),
                focusRequesters = listOf(remember { FocusRequester() }, remember { FocusRequester() }, remember { FocusRequester() }),
                onClick = {},
            )
            Tab(
                title = "This is a Tab",
                selected = true,
                rowActive = true,
                onClick = {},
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
