package com.github.sysmoon.wholphin.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.Trailer
import com.github.sysmoon.wholphin.ui.FontAwesome
import com.github.sysmoon.wholphin.ui.components.ExpandableFaButton
import com.github.sysmoon.wholphin.ui.components.TrailerButton
import kotlin.time.Duration

private val MinButtonHeight = 36.dp
private val ButtonSpacing = 2.dp
/** Height to show exactly 3 buttons: 3 * 36.dp + 2 * 2.dp */
private val ButtonsVisibleHeight = 112.dp
private val VerticalButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
private val ButtonWidth = 220.dp

/**
 * Vertical scrollable list of action buttons for detail screens (movie/series).
 * Order: Play/Resume, More Episodes (TV only), Choose Subtitles, Favourites, Trailer(s), More.
 */
@Composable
fun DetailActionButtons(
    playButtonLabel: String,
    resumePosition: Duration,
    onPlayClick: (Duration) -> Unit,
    onChooseSubtitlesClick: () -> Unit,
    favourite: Boolean,
    onFavouriteClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayFocusChanged: (Boolean) -> Unit = {},
    showMoreEpisodes: Boolean = false,
    onMoreEpisodesClick: () -> Unit = {},
    trailers: List<Trailer>? = null,
    onTrailerClick: (Trailer) -> Unit = {},
    watched: Boolean = false,
    onWatchClick: () -> Unit = {},
    showCastAndCrew: Boolean = false,
    onCastAndCrewClick: () -> Unit = {},
) {
    val firstFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val hasMoreToScroll = scrollState.maxValue > 0
    var lastButtonFocused by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .wrapContentWidth(Alignment.Start)
                .height(ButtonsVisibleHeight + 16.dp)
                .focusGroup()
                .focusRestorer(firstFocus)
                .padding(PaddingValues(vertical = 8.dp)),
    ) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(ButtonSpacing),
            horizontalAlignment = Alignment.Start,
        ) {
        DetailActionButton(
            title = playButtonLabel,
            icon = Icons.Default.PlayArrow,
            onClick = { onPlayClick(resumePosition) },
            modifier =
                Modifier
                    .width(ButtonWidth)
                    .onFocusChanged { onPlayFocusChanged(it.isFocused) }
                    .focusRequester(firstFocus),
        )
        if (showMoreEpisodes) {
            DetailActionButton(
                titleRes = R.string.more_episodes,
                iconStringRes = R.string.fa_list_ul,
                onClick = onMoreEpisodesClick,
                modifier = Modifier.width(ButtonWidth),
            )
        }
        DetailActionButton(
            titleRes = R.string.choose_subtitles,
            iconStringRes = R.string.fa_closed_captioning,
            onClick = onChooseSubtitlesClick,
            modifier = Modifier.width(ButtonWidth),
        )
        if (showCastAndCrew) {
            DetailActionButton(
                titleRes = R.string.cast_and_crew,
                iconStringRes = R.string.fa_user,
                onClick = onCastAndCrewClick,
                modifier = Modifier.width(ButtonWidth),
            )
        }
        ExpandableFaButton(
            title = if (favourite) R.string.remove_favorite else R.string.add_favorite,
            iconStringRes = R.string.fa_heart,
            onClick = onFavouriteClick,
            iconColor = if (favourite) Color.Red else Color.Unspecified,
            modifier = Modifier.width(ButtonWidth),
        )
        trailers?.let { list ->
            if (list.isNotEmpty()) {
                TrailerButton(
                    trailers = list,
                    trailerOnClick = onTrailerClick,
                    modifier = Modifier.width(ButtonWidth),
                )
            }
        }
        ExpandableFaButton(
            title = R.string.more,
            iconStringRes = R.string.fa_ellipsis_vertical,
            onClick = onMoreClick,
            modifier =
                Modifier
                    .width(ButtonWidth)
                    .onFocusChanged { lastButtonFocused = it.isFocused },
        )
        }
        if (hasMoreToScroll && !lastButtonFocused) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .width(ButtonWidth)
                        .height(48.dp)
                        .focusProperties { canFocus = false }
                        .background(
                            brush = Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    ),
                            ),
                        ),
            )
        }
    }
}

@Composable
private fun DetailActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier =
            modifier
                .requiredSizeIn(minHeight = MinButtonHeight)
                .height(MinButtonHeight),
        contentPadding = VerticalButtonPadding,
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun DetailActionButton(
    @StringRes titleRes: Int,
    @StringRes iconStringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier =
            modifier
                .requiredSizeIn(minHeight = MinButtonHeight)
                .height(MinButtonHeight),
        contentPadding = VerticalButtonPadding,
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(iconStringRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    fontFamily = FontAwesome,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
