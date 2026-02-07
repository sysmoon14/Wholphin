package com.github.sysmoon.wholphin.ui.detail.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.detail.DetailInfoBlock
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun EpisodeDetailsHeader(
    preferences: UserPreferences,
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val logoUrl = imageUrlService.rememberImageUrl(ep, ImageType.LOGO)
        ?: ep.data.seriesId?.let { seriesId -> remember(seriesId) { imageUrlService.getItemImageUrl(seriesId, ImageType.LOGO) } }
    val dto = ep.data

    DetailInfoBlock(
        item = ep,
        chosenStreams = chosenStreams,
        bringIntoViewRequester = bringIntoViewRequester,
        overviewOnClick = overviewOnClick,
        modifier = modifier,
        logoImageUrl = logoUrl,
        titleFallback = dto.seriesName,
        subtitleLine = dto.episodeTitle ?: dto.name,
    )
}
