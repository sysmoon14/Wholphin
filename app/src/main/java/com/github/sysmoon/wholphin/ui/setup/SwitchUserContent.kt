package com.github.sysmoon.wholphin.ui.setup

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.ui.FontAwesome
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.services.SetupDestination
import com.github.sysmoon.wholphin.ui.components.BasicDialog
import com.github.sysmoon.wholphin.ui.components.CircularProgress
import com.github.sysmoon.wholphin.ui.components.EditTextBox
import com.github.sysmoon.wholphin.ui.components.TextButton
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.github.sysmoon.wholphin.ui.dimAndBlur
import kotlin.time.Duration.Companion.milliseconds
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.components.TextButton
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.LoadingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SwitchUserContent(
    currentServer: JellyfinServer,
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel =
        hiltViewModel<SwitchUserViewModel, SwitchUserViewModel.Factory>(
            creationCallback = { it.create(currentServer) },
        ),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.init()
    }

//    val currentServer by viewModel.serverRepository.currentServer.observeAsState()
    val currentUser by viewModel.serverRepository.currentUser.observeAsState()
    val users by viewModel.users.observeAsState(listOf())

    val quickConnectEnabled by viewModel.serverQuickConnect.observeAsState(false)
    val quickConnect by viewModel.quickConnectState.observeAsState(null)
    var showAddUser by remember { mutableStateOf(false) }

    val userState by viewModel.switchUserState.observeAsState(LoadingState.Pending)
    val loginAttempts by viewModel.loginAttempts.observeAsState(0)
    LaunchedEffect(userState) {
        if (!showAddUser) {
            when (val s = userState) {
                is LoadingState.Error -> {
                    val msg = s.message ?: s.exception?.localizedMessage
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }
    var switchUserWithPin by remember { mutableStateOf<JellyfinUser?>(null) }

    val background by viewModel.userSelectBackground.observeAsState(null)
    val selectedUserId by viewModel.selectedUserForBackgroundId.observeAsState(null)
    var isAddUserFocused by remember { mutableStateOf(false) }
    var isSwitchServerFocused by remember { mutableStateOf(false) }

    val isPreloading by viewModel.isPreloading.observeAsState(true)

    currentServer?.let { server ->
        if (isPreloading) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1a1a2e),
                                    Color(0xFF16213e),
                                    Color.Black,
                                ),
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    CircularProgress(Modifier.size(48.dp))
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .dimAndBlur(showAddUser || switchUserWithPin != null),
        ) {
            // Full-screen backdrop (Netflix-style: art from selected user's library)
            Box(Modifier.fillMaxSize()) {
                val ctx = LocalContext.current
                // Two fixed slots for backdrop and logo so they transition together. We never change
                // the visible slot's URL during transition; new image goes in the other slot and we crossfade.
                var slot0Url by remember { mutableStateOf<String?>(null) }
                var slot1Url by remember { mutableStateOf<String?>(null) }
                var slot0LogoUrl by remember { mutableStateOf<String?>(null) }
                var slot1LogoUrl by remember { mutableStateOf<String?>(null) }
                var frontSlot by remember { mutableStateOf(0) } // which slot is on top (visible)
                val slot0Alpha = remember { Animatable(1f) }
                val slot1Alpha = remember { Animatable(0f) }
                // Trigger crossfade when back slot's image loads (onSuccess) or after fallback delay if it never fires
                var crossfadeTrigger by remember { mutableStateOf(0) }
                var previousSelectedUserId by remember { mutableStateOf<UUID?>(null) }
                val scope = rememberCoroutineScope()
                var crossfadeFallbackJob by remember { mutableStateOf<Job?>(null) }
                LaunchedEffect(background?.backdropUrl, background?.logoUrl, selectedUserId) {
                    val newUrl = background?.backdropUrl
                    val newLogoUrl = background?.logoUrl
                    if (newUrl == null) {
                        slot0Url = null
                        slot1Url = null
                        slot0LogoUrl = null
                        slot1LogoUrl = null
                        frontSlot = 0
                        slot0Alpha.snapTo(1f)
                        slot1Alpha.snapTo(0f)
                        previousSelectedUserId = selectedUserId
                        return@LaunchedEffect
                    }
                    val currentFrontUrl = if (frontSlot == 0) slot0Url else slot1Url
                    if (newUrl == currentFrontUrl) return@LaunchedEffect
                    // First load only: show immediately (nothing to fade from)
                    if (currentFrontUrl == null) {
                        slot0Url = newUrl
                        slot0LogoUrl = newLogoUrl
                        slot1Url = null
                        slot1LogoUrl = null
                        frontSlot = 0
                        slot0Alpha.snapTo(1f)
                        slot1Alpha.snapTo(0f)
                        previousSelectedUserId = selectedUserId
                        return@LaunchedEffect
                    }
                    // User switch or rotation: put new image in back slot, then trigger crossfade immediately
                    previousSelectedUserId = selectedUserId
                    val backSlot = 1 - frontSlot
                    if (backSlot == 0) {
                        slot0Url = newUrl
                        slot0LogoUrl = newLogoUrl
                        slot0Alpha.snapTo(0f)
                        slot1Alpha.snapTo(1f)
                    } else {
                        slot1Url = newUrl
                        slot1LogoUrl = newLogoUrl
                        slot1Alpha.snapTo(0f)
                        slot0Alpha.snapTo(1f)
                    }
                    crossfadeFallbackJob?.cancel()
                    crossfadeFallbackJob = null
                    crossfadeTrigger++
                }
                LaunchedEffect(crossfadeTrigger) {
                    if (crossfadeTrigger == 0) return@LaunchedEffect
                    val backSlot = 1 - frontSlot
                    val backAlpha = if (backSlot == 0) slot0Alpha else slot1Alpha
                    val foreAlpha = if (backSlot == 0) slot1Alpha else slot0Alpha
                    coroutineScope {
                        launch { backAlpha.animateTo(1f, animationSpec = tween(450)) }
                        foreAlpha.animateTo(0f, animationSpec = tween(450))
                    }
                    frontSlot = backSlot
                }
                // Ken Burns: slow zoom and pan (slightly more noticeable)
                val infiniteTransition = rememberInfiniteTransition(label = "kenburns")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.07f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(18_000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "scale",
                )
                val offsetProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(18_000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "offset",
                )
                val density = LocalDensity.current
                val offsetXPx = with(density) { 24.dp.toPx() }
                val offsetYPx = with(density) { 16.dp.toPx() }
                // Always draw gradient first so it shows while loading or on error
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1a1a2e),
                                        Color(0xFF16213e),
                                        Color.Black,
                                    ),
                                ),
                            )
                        },
                )
                // Backdrop image(s): two fixed slots so the visible one never changes URL mid-transition
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetProgress * offsetXPx
                            translationY = offsetProgress * offsetYPx
                        },
                ) {
                    slot0Url?.let { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(url).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = slot0Alpha.value },
                            onSuccess = {
                                if (frontSlot == 1) {
                                    crossfadeFallbackJob?.cancel()
                                    crossfadeFallbackJob = null
                                    crossfadeTrigger++
                                }
                            },
                            onError = { viewModel.onBackgroundLoadFailed() },
                        )
                    }
                    slot1Url?.let { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(url).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = slot1Alpha.value },
                            onSuccess = {
                                if (frontSlot == 0) {
                                    crossfadeFallbackJob?.cancel()
                                    crossfadeFallbackJob = null
                                    crossfadeTrigger++
                                }
                            },
                            onError = { viewModel.onBackgroundLoadFailed() },
                        )
                    }
                }
                // Gradient overlay for readability (bottom/top)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.4f to Color.Black.copy(alpha = 0.3f),
                                        1f to Color.Black.copy(alpha = 0.7f),
                                    ),
                                ),
                            )
                        },
                )
                // Bottom-right: logo slots (same two-slot alpha as backdrop); padding pulls logo slightly left and up from corner
                val logoBoxModifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = 48.dp)
                        .size(width = 200.dp, height = 100.dp)
                slot0LogoUrl?.let { logoUrl ->
                    Box(modifier = logoBoxModifier.graphicsLayer { alpha = slot0Alpha.value }) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(logoUrl).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            onError = { viewModel.onBackgroundLoadFailed() },
                        )
                    }
                }
                slot1LogoUrl?.let { logoUrl ->
                    Box(modifier = logoBoxModifier.graphicsLayer { alpha = slot1Alpha.value }) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(logoUrl).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            onError = { viewModel.onBackgroundLoadFailed() },
                        )
                    }
                }
            }

            // Black gradient from left edge over the image: solid black until profile area, then fade to transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black,
                                    0.22f to Color.Black,
                                    0.32f to Color.Black.copy(alpha = 0.95f),
                                    0.48f to Color.Black.copy(alpha = 0.6f),
                                    0.65f to Color.Black.copy(alpha = 0.2f),
                                    0.85f to Color.Transparent,
                                ),
                            ),
                        )
                    },
            )

            // Left: "Who's watching?" + vertical user list + small switch server when at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(280.dp)
                    .padding(start = 48.dp, top = 48.dp, end = 24.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.whos_watching),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                UserList(
                    users = users,
                    currentUser = currentUser,
                    serverName = server.name ?: server.url,
                    onSwitchUser = { user ->
                        if (user.hasPin) {
                            switchUserWithPin = user
                        } else {
                            viewModel.switchUser(user)
                        }
                    },
                    onAddUser = { showAddUser = true },
                    onRemoveUser = { user ->
                        viewModel.removeUser(user)
                    },
                    onSwitchServer = {
                        viewModel.setupNavigationManager.navigateTo(
                            SetupDestination.ServerList,
                        )
                    },
                    onSelectedUser = { selected ->
                        // Only switch background from focus after preload is done; preload already sets first user's background
                        if (!isPreloading) viewModel.loadBackgroundForUser(selected)
                    },
                    onAddUserFocused = { focused -> isAddUserFocused = focused },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                // Only visible when Add User or Switch Server is focused; always in composition so focus can move to it
                val showSwitchServer = isAddUserFocused || isSwitchServerFocused
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .padding(8.dp)
                        .graphicsLayer { clip = false },
                ) {
                TextButton(
                    onClick = {
                        viewModel.setupNavigationManager.navigateTo(SetupDestination.ServerList)
                    },
                    modifier = Modifier
                        .focusProperties { canFocus = showSwitchServer }
                        .graphicsLayer { alpha = if (showSwitchServer) 1f else 0f }
                        .onFocusChanged { isSwitchServerFocused = it.isFocused },
                ) {
                    Text(
                        text = stringResource(R.string.fa_arrow_left_arrow_right),
                        fontFamily = FontAwesome,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = server.name ?: server.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                }
            }
        }

        if (showAddUser) {
            var useQuickConnect by remember { mutableStateOf(quickConnectEnabled) }
            LaunchedEffect(Unit) {
                viewModel.clearSwitchUserState()
                viewModel.resetAttempts()
                if (useQuickConnect) {
                    viewModel.initiateQuickConnect(server)
                }
            }
            BasicDialog(
                onDismissRequest = {
                    viewModel.cancelQuickConnect()
                    showAddUser = false
                },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                    ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .focusGroup()
                            .padding(16.dp)
                            .fillMaxWidth(.4f),
                ) {
                    if (useQuickConnect) {
                        if (quickConnect == null && userState !is LoadingState.Error) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .height(32.dp)
                                        .align(Alignment.CenterHorizontally),
                            ) {
                                CircularProgress(Modifier.size(20.dp))
                                Text(
                                    text = "Waiting for Quick Connect code...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier,
                                )
                            }
                        } else if (quickConnect != null) {
                            Text(
                                text = "Use Quick Connect on your device to authenticate to ${server.name ?: server.url}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = quickConnect?.code ?: "Failed to get code",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        UserStateError(userState)
                        TextButton(
                            stringRes = R.string.username_or_password,
                            onClick = {
                                viewModel.cancelQuickConnect()
                                viewModel.clearSwitchUserState()
                                useQuickConnect = false
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
//                        val username = rememberTextFieldState()
//                        val password = rememberTextFieldState()
                        var username by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        val onSubmit = {
                            viewModel.login(
                                server,
                                username,
                                password,
                            )
                        }
                        val focusRequester = remember { FocusRequester() }
                        val passwordFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                        Text(
                            text = "Enter username/password to login to ${server.name ?: server.url}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        UserStateError(userState)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = "Username",
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            EditTextBox(
                                value = username,
                                onValueChange = { username = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onNext = {
                                            passwordFocusRequester.tryRequestFocus()
                                        },
                                    ),
                                //                                onKeyboardAction = {
//                                    passwordFocusRequester.tryRequestFocus()
//                                },
                                isInputValid = { userState !is LoadingState.Error },
                                modifier = Modifier.focusRequester(focusRequester),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = "Password",
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            LaunchedEffect(password) {
                                viewModel.clearSwitchUserState()
                            }
                            EditTextBox(
                                value = password,
                                onValueChange = { password = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Go,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onGo = { onSubmit.invoke() },
                                    ),
                                isInputValid = { userState !is LoadingState.Error },
                                modifier = Modifier.focusRequester(passwordFocusRequester),
                            )
                        }
                        TextButton(
                            stringRes = R.string.login,
                            onClick = { onSubmit.invoke() },
                            enabled = username.isNotNullOrBlank(),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (loginAttempts > 2) {
                        Text(
                            text = "Trouble logging in?",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        TextButton(
                            stringRes = R.string.show_debug_info,
                            onClick = {
                                viewModel.navigationManager.navigateTo(Destination.Debug)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
        switchUserWithPin?.let { user ->
            PinEntryDialog(
                onDismissRequest = { switchUserWithPin = null },
                onClickServerAuth = {
                    showAddUser = true
                    switchUserWithPin = null
                },
                onTextChange = {
                    if (it == user.pin) viewModel.switchUser(user)
                },
            )
        }
        }
    }
}

@Composable
private fun UserStateError(
    userState: LoadingState,
    modifier: Modifier = Modifier,
) {
    when (val s = userState) {
        is LoadingState.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier,
            ) {
                s.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (s.exception != null) {
                    s.exception.localizedMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    s.exception.cause?.localizedMessage?.let {
                        Text(
                            text = "Cause: $it",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        else -> {}
    }
}
