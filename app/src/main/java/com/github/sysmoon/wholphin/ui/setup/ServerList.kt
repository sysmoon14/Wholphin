package com.github.sysmoon.wholphin.ui.setup

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.ui.theme.LocalFocusOverrideColors
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.ui.Cards
import com.github.sysmoon.wholphin.ui.components.CircularProgress
import com.github.sysmoon.wholphin.ui.components.DialogItem
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.util.UUID

sealed interface ServerConnectionStatus {
    data class Success(
        val systemInfo: PublicSystemInfo,
    ) : ServerConnectionStatus

    object Pending : ServerConnectionStatus

    data class Error(
        val message: String?,
    ) : ServerConnectionStatus
}

/**
 * Display a list of servers plus option to add a new one
 */
@Composable
fun ServerList(
    servers: List<JellyfinServer>,
    connectionStatus: Map<UUID, ServerConnectionStatus>,
    onSwitchServer: (JellyfinServer) -> Unit,
    onTestServer: (JellyfinServer) -> Unit,
    onAddServer: () -> Unit,
    onRemoveServer: (JellyfinServer) -> Unit,
    allowAdd: Boolean,
    allowDelete: Boolean,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinServer?>(null) }

    LazyColumn(modifier = modifier) {
        items(servers) { server ->
            val status = connectionStatus[server.id] ?: ServerConnectionStatus.Pending
            ListItem(
                enabled = true,
                selected = false,
                headlineContent = { Text(text = server.name?.ifBlank { null } ?: server.url) },
                supportingContent = { if (server.name.isNotNullOrBlank()) Text(text = server.url) },
                leadingContent = {
                    when (status) {
                        is ServerConnectionStatus.Success -> {}

                        ServerConnectionStatus.Pending -> {
                            CircularProgress(
                                Modifier.size(IconButtonDefaults.MediumIconSize),
                            )
                        }

                        is ServerConnectionStatus.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = status.message,
                                tint = MaterialTheme.colorScheme.errorContainer,
                            )
                        }
                    }
                },
                onClick = {
                    when (status) {
                        is ServerConnectionStatus.Success -> {
                            onSwitchServer.invoke(server)
                        }

                        ServerConnectionStatus.Pending -> {}

                        is ServerConnectionStatus.Error -> {
                            onTestServer.invoke(server)
                        }
                    }
                },
                onLongClick = {
                    if (allowDelete) {
                        showDeleteDialog = server
                    }
                },
                modifier = Modifier,
            )
        }
        if (allowAdd) {
            item {
                HorizontalDivider()
                ListItem(
                    enabled = true,
                    selected = false,
                    headlineContent = { Text(text = stringResource(R.string.add_server)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            tint = Color.Green.copy(alpha = .8f),
                            contentDescription = null,
                        )
                    },
                    onClick = { onAddServer.invoke() },
                    modifier = Modifier,
                )
            }
        }
    }
    showDeleteDialog?.let { server ->
        DialogPopup(
            showDialog = allowDelete,
            title = server.name ?: server.url,
            dialogItems =
                listOf(
                    DialogItem(
                        stringResource(R.string.switch_servers),
                        R.string.fa_arrow_left_arrow_right,
                    ) {
                        onSwitchServer.invoke(server)
                    },
                    DialogItem(
                        stringResource(R.string.delete),
                        Icons.Default.Delete,
                        Color.Red.copy(alpha = .8f),
                    ) {
                        onRemoveServer.invoke(server)
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

// Server-select vertical list: square icons (match user select layout)
private val ServerSelectAvatarSize = 56.dp
private val ServerSelectAvatarSizeFocused = 72.dp
private val ServerSelectAvatarShape = RoundedCornerShape(6.dp)

/**
 * Vertical list of server rows + Add Server for the server select screen (matches user select layout).
 */
@Composable
fun ServerSelectList(
    servers: List<JellyfinServer>,
    connectionStatus: Map<UUID, ServerConnectionStatus>,
    onSwitchServer: (JellyfinServer) -> Unit,
    onTestServer: (JellyfinServer) -> Unit,
    onAddServer: () -> Unit,
    onRemoveServer: (JellyfinServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinServer?>(null) }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val focusRequester = remember { FocusRequester() }
        val firstFocusRequester = remember { FocusRequester() }
        if (servers.isNotEmpty()) {
            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .focusRestorer(firstFocusRequester)
                .focusRequester(focusRequester),
        ) {
            itemsIndexed(servers) { index, server ->
                val status = connectionStatus[server.id] ?: ServerConnectionStatus.Pending
                ServerRow(
                    server = server,
                    connectionStatus = status,
                    isCurrentServer = false,
                    onClick = {
                        when (status) {
                            is ServerConnectionStatus.Success -> onSwitchServer(server)
                            ServerConnectionStatus.Pending -> {}
                            is ServerConnectionStatus.Error -> onTestServer(server)
                        }
                    },
                    onLongClick = { showDeleteDialog = server },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                )
            }
            item {
                AddServerRow(onClick = onAddServer)
            }
        }
    }
    showDeleteDialog?.let { server ->
        DialogPopup(
            showDialog = true,
            title = server.name ?: server.url,
            dialogItems = listOf(
                DialogItem(
                    stringResource(R.string.switch_servers),
                    R.string.fa_arrow_left_arrow_right,
                ) {
                    onSwitchServer(server)
                    showDeleteDialog = null
                },
                DialogItem(
                    stringResource(R.string.delete),
                    Icons.Default.Delete,
                    Color.Red.copy(alpha = .8f),
                ) {
                    onRemoveServer(server)
                    showDeleteDialog = null
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

/** Vertical-list row: square icon (left) + server name (right). Focused row has larger icon and white ring. */
@Composable
private fun ServerRow(
    server: JellyfinServer,
    connectionStatus: ServerConnectionStatus,
    isCurrentServer: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val serverColor = rememberIdColor(server.id)
    val avatarSize by animateDpAsState(
        targetValue = if (focused) ServerSelectAvatarSizeFocused else ServerSelectAvatarSize,
        animationSpec = tween(durationMillis = 200),
        label = "avatarSize",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "ringWidth",
    )
    val displayText = remember(server) {
        (server.name ?: server.url.replace(Regex("^https?://"), ""))
            .firstOrNull()?.uppercase() ?: "?"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .border(ringWidth, Color.White, ServerSelectAvatarShape),
        ) {
            Surface(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxSize(),
                shape = ClickableSurfaceDefaults.shape(shape = ServerSelectAvatarShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isCurrentServer) serverColor.copy(alpha = 0.7f) else serverColor.copy(alpha = 0.5f),
                    focusedContainerColor = if (isCurrentServer) serverColor.copy(alpha = 0.9f) else serverColor.copy(alpha = 0.7f),
                ),
                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (connectionStatus) {
                        is ServerConnectionStatus.Success -> {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                        ServerConnectionStatus.Pending -> {
                            CircularProgress(Modifier.size(avatarSize * 0.4f))
                        }
                        is ServerConnectionStatus.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = connectionStatus.message,
                                tint = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(avatarSize * 0.4f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = server.name?.ifBlank { null } ?: server.url,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Vertical-list row: + icon (left) + "Add Server" text (right). Matches AddUserRow style. */
@Composable
private fun AddServerRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val addServerColor = MaterialTheme.colorScheme.surfaceVariant
    val focusOverride = LocalFocusOverrideColors.current
    val avatarSize by animateDpAsState(
        targetValue = if (focused) ServerSelectAvatarSizeFocused else ServerSelectAvatarSize,
        animationSpec = tween(durationMillis = 200),
        label = "addServerAvatarSize",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "addServerRingWidth",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .border(ringWidth, Color.White, ServerSelectAvatarShape),
        ) {
            Surface(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxSize(),
                shape = ClickableSurfaceDefaults.shape(shape = ServerSelectAvatarShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = addServerColor.copy(alpha = 0.4f),
                    focusedContainerColor = focusOverride?.container ?: addServerColor.copy(alpha = 0.6f),
                ),
                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_server),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(avatarSize * 0.4f),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.add_server),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Generate a consistent color for a UUID
 */
@Composable
fun rememberIdColor(
    id: UUID?,
    alpha: Float = 1f,
    nullColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Color =
    remember(id, alpha) {
        if (id == null) {
            return@remember nullColor
        }
        // Generate a color based on the server ID hash, fallback to URL hash
        val hash = id.hashCode()
        val hue = (hash % 360).toFloat()
        val saturation = 0.6f + ((hash / 360) % 40).toFloat() / 100f // 0.6-1.0
        val brightness = 0.4f + ((hash / 14400) % 30).toFloat() / 100f // 0.4-0.7 (darker colors)

        // Convert HSV to RGB
        val c = brightness * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2f - 1))
        val m = brightness - c

        val (r, g, b) =
            when {
                hue < 60 -> Triple(c, x, 0f)
                hue < 120 -> Triple(x, c, 0f)
                hue < 180 -> Triple(0f, c, x)
                hue < 240 -> Triple(0f, x, c)
                hue < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }

/**
 * Server icon card component - displays a circular card with server name/letter
 */
@Composable
fun ServerIconCard(
    server: JellyfinServer,
    connectionStatus: ServerConnectionStatus,
    isCurrentServer: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowDelete: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Generate unique color for this server
    val serverColor = rememberIdColor(server.id)

    // Card dimensions - circular card
    val cardSize = Cards.serverUserCircle

    val displayText =
        remember(server) {
            (server.name ?: server.url.replace(Regex("^https?://"), ""))
                .firstOrNull()
                ?.uppercase()
                ?: "?"
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            onLongClick = if (allowDelete) onLongClick else null,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor =
                        if (isCurrentServer) {
                            serverColor.copy(alpha = 0.7f)
                        } else {
                            serverColor.copy(alpha = 0.5f)
                        },
                    focusedContainerColor =
                        if (isCurrentServer) {
                            serverColor.copy(alpha = 0.9f)
                        } else {
                            serverColor.copy(alpha = 0.7f)
                        },
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Show connection status indicator or server name/letter
                when (connectionStatus) {
                    is ServerConnectionStatus.Success -> {
                        // Show server name/letter

                        Text(
                            text = displayText,
                            style =
                                MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                    ServerConnectionStatus.Pending -> {
                        CircularProgress(
                            modifier = Modifier.size(cardSize * 0.4f),
                        )
                    }

                    is ServerConnectionStatus.Error -> {
                        // Show warning icon with server letter below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = connectionStatus.message,
                                tint = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(cardSize * 0.3f),
                            )
                            Text(
                                text = displayText,
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Server name below the card
        Text(
            text = server.name?.ifBlank { null } ?: server.url,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}

/**
 * Add Server card component - displays a + icon in a circle
 */
@Composable
fun AddServerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusOverride = LocalFocusOverrideColors.current

    // Use a neutral gray color for the add server card
    val addServerColor = MaterialTheme.colorScheme.surfaceVariant

    // Card dimensions - circular card (same as server cards)
    val cardSize = Cards.height2x3 * 0.75f // ~120dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = addServerColor.copy(alpha = 0.4f),
                    focusedContainerColor = focusOverride?.container ?: addServerColor.copy(alpha = 0.6f),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_server),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(cardSize * 0.4f), // Size of the + icon
                )
            }
        }

        // "Add Server" text below the card
        Text(
            text = stringResource(R.string.add_server),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}
