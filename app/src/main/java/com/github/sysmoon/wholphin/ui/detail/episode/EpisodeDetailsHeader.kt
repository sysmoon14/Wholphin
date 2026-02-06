package com.github.sysmoon.wholphin.ui.detail.episode

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.components.EpisodeName
import com.github.sysmoon.wholphin.ui.components.OverviewText
import com.github.sysmoon.wholphin.ui.components.QuickDetails
import com.github.sysmoon.wholphin.ui.components.SeriesName
import com.github.sysmoon.wholphin.ui.ItemLogoHeight
import com.github.sysmoon.wholphin.ui.ItemLogoWidth
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.components.VideoStreamDetails
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

@Composable
fun EpisodeDetailsHeader(
    preferences: UserPreferences,
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = ep.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageUrlService = LocalImageUrlService.current
    // Episodes often don't have their own logo; use series logo when episode logo is null
    val logoUrl = imageUrlService.rememberImageUrl(ep, ImageType.LOGO)
        ?: ep.data.seriesId?.let { seriesId -> remember(seriesId) { imageUrlService.getItemImageUrl(seriesId, ImageType.LOGO) } }
    var logoError by remember(ep) { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (logoUrl.isNotNullOrBlank() && !logoError) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { logoError = true },
                    modifier = Modifier.size(width = ItemLogoWidth, height = ItemLogoHeight),
                )
            }
        }
        SeriesName(dto.seriesName, Modifier.fillMaxWidth(.75f))
        EpisodeName(dto, Modifier.fillMaxWidth(.75f))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val padding = 8.dp
            QuickDetails(ep.ui.quickDetails, ep.timeRemainingOrRuntime)

            VideoStreamDetails(
                chosenStreams = chosenStreams,
                numberOfVersions = dto.mediaSourceCount ?: 0,
                modifier = Modifier.padding(bottom = padding),
            )
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier,
                )
            }

            // Description
            dto.overview?.let { overview ->
                val interactionSource = remember { MutableInteractionSource() }
                val focused = interactionSource.collectIsFocusedAsState().value
                LaunchedEffect(focused) {
                    if (focused) bringIntoViewRequester.bringIntoView()
                }
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    interactionSource = interactionSource,
                )
            }
            ep.data.people
                ?.filter { it.type == PersonKind.DIRECTOR && it.name.isNotNullOrBlank() }
                ?.joinToString(", ") { it.name!! }
                ?.let {
                    Text(
                        text = stringResource(R.string.directed_by, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
        }
    }
}
