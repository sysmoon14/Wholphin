package com.github.sysmoon.wholphin.ui.detail.series

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.ui.Cards
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.cards.WatchedIcon
import com.github.sysmoon.wholphin.ui.components.EpisodeName
import com.github.sysmoon.wholphin.ui.components.QuickDetails
import com.github.sysmoon.wholphin.ui.components.VideoStreamDetails
import com.github.sysmoon.wholphin.ui.playback.isPlayKeyUp
import com.github.sysmoon.wholphin.ui.roundMinutes
import com.github.sysmoon.wholphin.ui.seasonEpisode
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks

private val ThumbnailWidth = 260.dp
private val ThumbnailAspectRatio = 16f / 9f

private fun episodePlayPercent(episode: BaseItem?): Double {
    if (episode == null) return 0.0
    episode.data.userData?.playedPercentage?.let { return it.coerceIn(0.0, 100.0) }
    val totalMs = episode.data.runTimeTicks?.ticks?.inWholeMilliseconds ?: 0L
    if (totalMs <= 0) return 0.0
    val posMs = episode.playbackPosition.inWholeMilliseconds
    return (posMs.toDouble() / totalMs * 100).coerceIn(0.0, 100.0)
}

@Composable
fun EpisodeListRow(
    episode: BaseItem?,
    chosenStreams: ChosenStreams?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSelectNext: (() -> Unit)? = null,
    onSelectPrevious: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusRequester2: FocusRequester? = null,
    isFocusable: Boolean = true,
) {
    val imageUrlService = LocalImageUrlService.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val imageUrl =
        remember(episode) {
            episode?.let {
                imageUrlService.getItemImageUrl(it, ImageType.PRIMARY)
            }
        }
    val played = episode?.played ?: false
    val playPercent = remember(episode) { episodePlayPercent(episode) }
    val inProgress = playPercent > 0 && playPercent < 100

    Card(
        onClick = { if (episode != null) onClick() },
        onLongClick = { if (episode != null) onLongClick() },
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (!isFocusable) {
                        Modifier.focusProperties { canFocus = false }
                    } else Modifier,
                ),
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    modifier
                        .then(
                            if (isFocusable && focusRequester != null)
                                Modifier.focusRequester(focusRequester)
                            else Modifier,
                        )
                        .then(
                            if (isFocusable && focusRequester2 != null)
                                Modifier.focusRequester(focusRequester2)
                            else Modifier,
                        )
                        .then(
                            if (isFocusable) {
                                Modifier.focusable(interactionSource = interactionSource)
                                    .onFocusChanged { onFocusChanged(it.isFocused) }
                                    .onKeyEvent {
                                        when {
                                            it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown -> {
                                                onSelectNext?.invoke()
                                                return@onKeyEvent onSelectNext != null
                                            }
                                            it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp -> {
                                                onSelectPrevious?.invoke()
                                                return@onKeyEvent onSelectPrevious != null
                                            }
                                            episode != null && isPlayKeyUp(it) -> {
                                                onClick()
                                                return@onKeyEvent true
                                            }
                                            else -> false
                                        }
                                    }
                            } else Modifier,
                        )
                        .size(width = ThumbnailWidth, height = ThumbnailWidth / ThumbnailAspectRatio)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (isFocusable && isFocused) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            } else Modifier,
                        ),
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = episode?.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                episode?.data?.seasonEpisode?.let { se ->
                    Text(
                        text = se,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                episode?.timeRemainingOrRuntime?.roundMinutes?.takeIf { it.inWholeMinutes > 0 }
                    ?.let { duration ->
                        val text =
                            if (inProgress) {
                                "${duration.inWholeMinutes}m left"
                            } else {
                                "${duration.inWholeMinutes} min"
                            }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                if (played) {
                    WatchedIcon(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp),
                    )
                }
                if (inProgress) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .clip(RectangleShape)
                                .height(Cards.playedPercentHeight)
                                .fillMaxWidth((playPercent / 100).toFloat()),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EpisodeName(episode?.data, modifier = Modifier.fillMaxWidth())
                episode?.data?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                (episode?.ui?.quickDetailsForEpisodeRow ?: episode?.ui?.quickDetails)?.let {
                    QuickDetails(it, episode?.timeRemainingOrRuntime)
                }
                if (episode?.data != null) {
                    VideoStreamDetails(
                        chosenStreams = chosenStreams,
                        numberOfVersions = episode.data.mediaSourceCount ?: 0,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
