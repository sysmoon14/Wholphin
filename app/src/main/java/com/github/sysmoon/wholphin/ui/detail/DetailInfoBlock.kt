package com.github.sysmoon.wholphin.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.ui.ItemLogoHeight
import com.github.sysmoon.wholphin.ui.ItemLogoWidth
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.components.GenreText
import com.github.sysmoon.wholphin.ui.components.OverviewText
import com.github.sysmoon.wholphin.ui.components.QuickDetails
import com.github.sysmoon.wholphin.ui.components.VideoStreamDetails
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.letNotEmpty
import com.github.sysmoon.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

/**
 * Shared detail header block: logo top-left, then metadata (year, rating, season count, runtime),
 * media badges, tagline, description, cast (first 3), writer(s) when present.
 * Used by movie, series, and episode detail headers.
 */
@Composable
fun DetailInfoBlock(
    item: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    seasonCount: Int? = null,
    logoImageUrl: String? = null,
    titleFallback: String? = null,
    subtitleLine: String? = null,
    showCastAndCrew: Boolean = true,
    showMediaPills: Boolean = true,
    contentStartPadding: Dp = 0.dp,
) {
    val dto = item.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageUrlService = LocalImageUrlService.current
    val resolvedLogoUrl = logoImageUrl ?: imageUrlService.rememberImageUrl(item, ImageType.LOGO)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier.padding(start = contentStartPadding),
    ) {
        var logoError by remember(item) { mutableStateOf(false) }
        if (resolvedLogoUrl.isNotNullOrBlank() && !logoError) {
            AsyncImage(
                    model = resolvedLogoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoError = true },
                    modifier = Modifier.size(width = ItemLogoWidth, height = ItemLogoHeight),
                )
        } else {
            val displayName = titleFallback ?: item.name ?: ""
            if (displayName.isNotBlank()) {
                Text(
                    text = displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.75f),
                )
            }
        }
        if (!titleFallback.isNullOrBlank() && resolvedLogoUrl.isNotNullOrBlank() && !logoError) {
            Text(
                text = titleFallback,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        }
        if (!subtitleLine.isNullOrBlank()) {
            Text(
                text = subtitleLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val padding = 4.dp
            QuickDetails(
                item.ui.quickDetails,
                item.timeRemainingOrRuntime,
                Modifier.padding(bottom = padding),
            )

            if (seasonCount != null && seasonCount > 0) {
                Text(
                    text = context.getString(R.string.seasons_count_format, seasonCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = padding),
                )
            }

            dto.genres?.letNotEmpty {
                GenreText(
                    it,
                    Modifier.padding(bottom = padding),
                    textStyle = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showMediaPills) {
                VideoStreamDetails(
                    chosenStreams = chosenStreams,
                    numberOfVersions = item.data.mediaSourceCount ?: 0,
                    modifier = Modifier.padding(bottom = padding),
                )
            }

            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier,
                )
            }

            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = null,
                    textBoxHeight = Dp.Unspecified,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }

            if (showCastAndCrew) {
                val castNames =
                    remember(dto.people) {
                        dto.people
                            ?.filter { it.type == PersonKind.ACTOR && it.name.isNotNullOrBlank() }
                            ?.take(3)
                            ?.joinToString(", ") { it.name!! }
                    }
                if (!castNames.isNullOrBlank()) {
                    Text(
                        text = context.getString(R.string.cast_label, castNames),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val writerKind = PersonKind.entries.find { it.name == "WRITER" }
                val writerNames =
                    remember(dto.people, writerKind) {
                        if (writerKind == null) null
                        else dto.people?.filter { it.type == writerKind && it.name.isNotNullOrBlank() }?.joinToString(", ") { it.name!! }
                    }
                if (!writerNames.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.written_by, writerNames),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
