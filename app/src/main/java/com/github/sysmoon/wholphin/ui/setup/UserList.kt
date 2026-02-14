package com.github.sysmoon.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.ui.Cards
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.github.sysmoon.wholphin.ui.FontAwesome
import com.github.sysmoon.wholphin.ui.PreviewTvSpec
import com.github.sysmoon.wholphin.ui.components.DialogItem
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.theme.WholphinTheme
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Display a vertical list of users on the left, with Add User at the bottom.
 * Netflix-style: vertical list, avatar + name per row. Reports focused/selected user for background art.
 */
@Composable
fun UserList(
    users: List<JellyfinUserAndImage>,
    currentUser: JellyfinUser?,
    serverName: String,
    onSwitchUser: (JellyfinUser) -> Unit,
    onAddUser: () -> Unit,
    onRemoveUser: (JellyfinUser) -> Unit,
    onSwitchServer: () -> Unit,
    onSelectedUser: ((JellyfinUser?) -> Unit)? = null,
    onAddUserFocused: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinUser?>(null) }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val focusRequester = remember { FocusRequester() }
        val firstFocusRequester = remember { FocusRequester() }
        if (users.isNotEmpty()) {
            LaunchedEffect(users.size) {
                delay(80)
                firstFocusRequester.tryRequestFocus()
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .focusRestorer(firstFocusRequester)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(users) { index, user ->
                UserRow(
                    user = user,
                    isCurrentUser = user.user.id == currentUser?.id,
                    onClick = { onSwitchUser.invoke(user.user) },
                    onLongClick = { showDeleteDialog = user.user },
                    onFocusChanged = { focused ->
                        if (focused) onSelectedUser?.invoke(user.user)
                    },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                )
            }
            item {
                AddUserRow(
                    onClick = { onAddUser.invoke() },
                    onFocusChanged = { focused ->
                        if (focused) onSelectedUser?.invoke(null)
                    },
                    onAddUserFocused = { focused -> onAddUserFocused?.invoke(focused) },
                )
            }
        }
    }
    showDeleteDialog?.let { user ->
        DialogPopup(
            showDialog = true,
            title = user.name ?: user.id.toString(),
            dialogItems =
                listOf(
                    DialogItem(
                        stringResource(R.string.switch_user),
                        R.string.fa_arrow_left_arrow_right,
                    ) {
                        onSwitchUser.invoke(user)
                    },
                    DialogItem(
                        stringResource(R.string.delete),
                        Icons.Default.Delete,
                        Color.Red.copy(alpha = .8f),
                    ) {
                        onRemoveUser.invoke(user)
                    },
                ),
            onDismissRequest = { showDeleteDialog = null },
            dismissOnClick = true,
            waitToLoad = true,
            properties = DialogProperties(),
            elevation = 5.dp,
        )
    }
}

// User-select list: square avatars with slight corner radius; focused row is larger
private val UserSelectAvatarSize = 56.dp
private val UserSelectAvatarSizeFocused = 72.dp
private val UserSelectAvatarShape = RoundedCornerShape(6.dp)

/** Vertical-list row: avatar (left) + name (right). Focused row has larger avatar and animated white ring. */
@Composable
private fun UserRow(
    user: JellyfinUserAndImage,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) onFocusChanged(true)
    }
    val userColor = rememberIdColor(user.user.id)
    var imageError by remember { mutableStateOf(false) }
    val avatarSize by animateDpAsState(
        targetValue = if (focused) UserSelectAvatarSizeFocused else UserSelectAvatarSize,
        animationSpec = tween(durationMillis = 200),
        label = "avatarSize",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "ringWidth",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .graphicsLayer { clip = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .border(ringWidth, Color.White, UserSelectAvatarShape),
        ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { if (it.isFocused) onFocusChanged(true) },
            shape = ClickableSurfaceDefaults.shape(shape = UserSelectAvatarShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor =
                        if (isCurrentUser) userColor.copy(alpha = 0.7f)
                        else userColor.copy(alpha = 0.5f),
                    focusedContainerColor =
                        if (isCurrentUser) userColor.copy(alpha = 0.9f)
                        else userColor.copy(alpha = 0.7f),
                ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (user.imageUrl.isNotNullOrBlank() && !imageError) {
                    AsyncImage(
                        model = user.imageUrl,
                        contentDescription = user.user.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                        modifier = Modifier.fillMaxSize().clip(UserSelectAvatarShape),
                    )
                } else {
                    val firstLetter =
                        remember(user) {
                            (user.user.name ?: user.user.id.toString()).firstOrNull()?.uppercase() ?: "?"
                        }
                    Text(
                        text = firstLetter,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        }
        Text(
            text = user.user.name ?: user.user.id.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f, fill = false)
                .wrapContentWidth(unbounded = true),
        )
    }
}

@Composable
private fun UserIconCard(
    user: JellyfinUserAndImage,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val userColor = rememberIdColor(user.user.id)
    var imageError by remember { mutableStateOf(false) }
    val cardSize = Cards.serverUserCircle

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor =
                        if (isCurrentUser) userColor.copy(alpha = 0.7f)
                        else userColor.copy(alpha = 0.5f),
                    focusedContainerColor =
                        if (isCurrentUser) userColor.copy(alpha = 0.9f)
                        else userColor.copy(alpha = 0.7f),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (user.imageUrl.isNotNullOrBlank() && !imageError) {
                    AsyncImage(
                        model = user.imageUrl,
                        contentDescription = user.user.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    val firstLetter =
                        remember(user) {
                            (user.user.name ?: user.user.id.toString()).firstOrNull()?.uppercase() ?: "?"
                        }
                    Text(
                        text = firstLetter,
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = user.user.name ?: user.user.id.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardSize).padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun UserIconCardImage(
    id: UUID,
    name: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    var imageError by remember { mutableStateOf(false) }
    val userColor = rememberIdColor(id, alpha)
    Box(
        modifier =
            modifier.background(
                color = userColor,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNotNullOrBlank() && !imageError) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageError = true },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(shape),
            )
        } else {
            val firstLetter =
                remember(id, name) {
                    (name ?: id.toString()).firstOrNull()?.uppercase() ?: "?"
                }
            Text(
                text = firstLetter,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewTvSpec
@Composable
fun UserIconCardImagePreview() {
    WholphinTheme {
        UserIconCardImage(
            id = UUID.randomUUID(),
            name = "A smith",
            imageUrl = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Vertical-list row: + icon (left) + "Add User" text (right). Enlarges and shows ring when focused, like UserRow. */
@Composable
private fun AddUserRow(
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onAddUserFocused: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        onFocusChanged(focused)
        onAddUserFocused(focused)
    }
    val addUserColor = MaterialTheme.colorScheme.surfaceVariant
    val avatarSize by animateDpAsState(
        targetValue = if (focused) UserSelectAvatarSizeFocused else UserSelectAvatarSize,
        animationSpec = tween(durationMillis = 200),
        label = "addUserAvatarSize",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "addUserRingWidth",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .border(ringWidth, Color.White, UserSelectAvatarShape),
        ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { if (it.isFocused) { onFocusChanged(true); onAddUserFocused(true) } },
            shape = ClickableSurfaceDefaults.shape(shape = UserSelectAvatarShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = addUserColor.copy(alpha = 0.4f),
                    focusedContainerColor = addUserColor.copy(alpha = 0.6f),
                ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_user),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(avatarSize * 0.4f),
                )
            }
        }
        }
        Text(
            text = stringResource(R.string.add_user),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Add User card component - displays a + icon in a circle (horizontal layout legacy)
 */
@Composable
private fun AddUserCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val addUserColor = MaterialTheme.colorScheme.surfaceVariant
    val cardSize = Cards.serverUserCircle

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = addUserColor.copy(alpha = 0.4f),
                    focusedContainerColor = addUserColor.copy(alpha = 0.6f),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_user),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(cardSize * 0.4f),
                )
            }
        }
        Text(
            text = stringResource(R.string.add_user),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardSize).padding(horizontal = 4.dp),
        )
    }
}
