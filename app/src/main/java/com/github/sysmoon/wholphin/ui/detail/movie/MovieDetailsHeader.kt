package com.github.sysmoon.wholphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.ChosenStreams
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.preferences.UserPreferences
import com.github.sysmoon.wholphin.ui.components.GenreText
import com.github.sysmoon.wholphin.ui.components.OverviewText
import com.github.sysmoon.wholphin.ui.components.QuickDetails
import com.github.sysmoon.wholphin.ui.ItemLogoHeight
import com.github.sysmoon.wholphin.ui.ItemLogoWidth
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.components.VideoStreamDetails
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.letNotEmpty
import org.jellyfin.sdk.model.api.ImageType
import com.github.sysmoon.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PersonKind

@Composable
fun MovieDetailsHeader(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val imageUrlService = LocalImageUrlService.current
        val logoUrl = imageUrlService.rememberImageUrl(movie, ImageType.LOGO)
        var logoError by remember(movie) { mutableStateOf(false) }
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
        } else {
            Text(
                text = movie.name ?: "",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
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
                movie.ui.quickDetails,
                movie.timeRemainingOrRuntime,
                Modifier.padding(bottom = padding),
            )

            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(bottom = padding))
            }

            VideoStreamDetails(
                chosenStreams = chosenStreams,
                numberOfVersions = movie.data.mediaSourceCount ?: 0,
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
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }

            val directorName =
                remember(movie.data.people) {
                    movie.data.people
                        ?.filter { it.type == PersonKind.DIRECTOR && it.name.isNotNullOrBlank() }
                        ?.joinToString(", ") { it.name!! }
                }

            directorName
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
