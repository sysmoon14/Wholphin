package com.github.sysmoon.wholphin.ui.detail.castcrew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.data.model.Person
import com.github.sysmoon.wholphin.ui.ItemLogoHeight
import com.github.sysmoon.wholphin.ui.ItemLogoWidth
import com.github.sysmoon.wholphin.ui.LocalImageUrlService
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.components.ErrorMessage
import com.github.sysmoon.wholphin.ui.components.LoadingPage
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private val HeadshotSize = 88.dp

@Composable
private fun CastAndCrewHeader(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val resolvedLogoUrl = imageUrlService.rememberImageUrl(item, ImageType.LOGO)
    var logoError by remember(item) { mutableStateOf(false) }

    if (resolvedLogoUrl.isNotNullOrBlank() && !logoError) {
        AsyncImage(
            model = resolvedLogoUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoError = true },
            modifier =
                modifier
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .size(width = ItemLogoWidth, height = ItemLogoHeight),
        )
    } else {
        val title = item.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cast_and_crew)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        )
    }
}

@Composable
fun CastAndCrewPage(
    destination: Destination.CastAndCrew,
    modifier: Modifier = Modifier,
    viewModel: CastAndCrewViewModel =
        hiltViewModel<CastAndCrewViewModel, CastAndCrewViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
) {
    val item by viewModel.item.observeAsState()
    val people by viewModel.people.observeAsState(emptyList())
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier)
        }
        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }
        LoadingState.Success -> {
            val mediaItem = item
            Column(modifier = modifier) {
                mediaItem?.let { baseItem ->
                    CastAndCrewHeader(item = baseItem)
                } ?: run {
                    Text(
                        text = stringResource(R.string.cast_and_crew),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
                if (people.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_cast_and_crew),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                    ) {
                        items(people, key = { it.id }) { person ->
                            CastAndCrewRow(
                                person = person,
                                onClick = {
                                    viewModel.navigationManager.navigateTo(
                                        Destination.MediaItem(person.id, BaseItemKind.PERSON),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastAndCrewRow(
    person: Person,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = person.name.orEmpty().ifBlank { person.id.toString() }
    val role = person.role?.takeIf { it.isNotBlank() } ?: person.type.name

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f, pressedScale = 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = person.imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(HeadshotSize)
                        .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
