package com.github.sysmoon.wholphin.ui.detail.movie

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.detail.DetailInfoBlock
import androidx.compose.foundation.relocation.BringIntoViewRequester

@Composable
fun MovieDetailsHeader(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCastAndCrew: Boolean = true,
) {
    DetailInfoBlock(
        item = movie,
        chosenStreams = chosenStreams,
        bringIntoViewRequester = bringIntoViewRequester,
        overviewOnClick = overviewOnClick,
        modifier = modifier,
        showCastAndCrew = showCastAndCrew,
    )
}
