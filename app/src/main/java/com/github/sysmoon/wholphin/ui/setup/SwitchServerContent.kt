package com.github.sysmoon.wholphin.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.ui.components.BasicDialog
import com.github.sysmoon.wholphin.ui.components.CircularProgress
import com.github.sysmoon.wholphin.ui.components.DialogItem
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.components.EditTextBox
import com.github.sysmoon.wholphin.ui.components.TextButton
import com.github.sysmoon.wholphin.ui.dimAndBlur
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.LoadingState

@Composable
fun SwitchServerContent(
    modifier: Modifier = Modifier,
    viewModel: SwitchServerViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.observeAsState(listOf())
    val serverStatus by viewModel.serverStatus.observeAsState(mapOf())

    var showAddServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.init()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dimAndBlur(showAddServer),
    ) {
        // Gradient background (no backdrops/logos on server select)
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

        // Left: "Select Server" + vertical server list (matches user select layout)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(280.dp)
                .padding(start = 48.dp, top = 48.dp, end = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.select_server),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ServerSelectList(
                servers = servers,
                connectionStatus = serverStatus,
                onSwitchServer = { viewModel.switchServer(it) },
                onTestServer = { viewModel.testServer(it) },
                onAddServer = { showAddServer = true },
                onRemoveServer = { viewModel.removeServer(it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        if (showAddServer) {
            var showEnterAddress by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                viewModel.clearAddServerState()
                if (!showEnterAddress) {
                    viewModel.discoverServers()
                }
            }

            val discoveredServers by viewModel.discoveredServers.observeAsState(listOf())

            // Filter out duplicates within the discovered servers list (same URL appearing multiple times)
            val filteredDiscoveredServers =
                remember(discoveredServers) {
                    val seenUrls = mutableSetOf<String>()
                    discoveredServers.filter { server ->
                        val normalizedUrl = server.url.lowercase().trim()
                        if (normalizedUrl in seenUrls) {
                            false // Duplicate, filter it out
                        } else {
                            seenUrls.add(normalizedUrl)
                            true // First occurrence, keep it
                        }
                    }
                }

            val firstDiscoveredServerFocusRequester = remember { FocusRequester() }

            // Default focus to first discovered server if available
            LaunchedEffect(filteredDiscoveredServers.isNotEmpty(), showEnterAddress) {
                if (!showEnterAddress && filteredDiscoveredServers.isNotEmpty()) {
                    firstDiscoveredServerFocusRequester.tryRequestFocus()
                }
            }

            BasicDialog(
                onDismissRequest = {
                    showAddServer = false
                    showEnterAddress = false
                    viewModel.clearAddServerState()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                elevation = 10.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(.4f),
                ) {
                    if (!showEnterAddress) {
                        // Show discovered servers first
                        Text(
                            text = stringResource(R.string.discovered_servers),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (filteredDiscoveredServers.isEmpty() && discoveredServers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.searching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else if (filteredDiscoveredServers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_servers_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp),
                            ) {
                                items(
                                    filteredDiscoveredServers.size,
                                    key = { filteredDiscoveredServers[it].url },
                                ) { index ->
                                    val server = filteredDiscoveredServers[index]
                                    val focusRequester =
                                        if (index == 0) {
                                            firstDiscoveredServerFocusRequester
                                        } else {
                                            remember { FocusRequester() }
                                        }

                                    ListItem(
                                        enabled = true,
                                        selected = false,
                                        headlineContent = {
                                            Text(
                                                text = server.name?.ifBlank { null } ?: server.url,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = server.url,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        onClick = {
                                            viewModel.addServer(server.url)
                                        },
                                        modifier = Modifier.focusRequester(focusRequester),
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                showEnterAddress = true
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(text = stringResource(R.string.enter_server_address))
                        }
                    } else {
                        // Show enter server address form
                        val state by viewModel.addServerState.observeAsState(LoadingState.Pending)
                        var url by remember { mutableStateOf("") }
                        val submit = {
                            viewModel.addServer(url)
                        }
                        val textBoxFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(Unit) {
                            textBoxFocusRequester.tryRequestFocus()
                        }

                        Text(
                            text = stringResource(R.string.enter_server_url),
                        )
                        EditTextBox(
                            value = url,
                            onValueChange = { url = it },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onGo = { submit.invoke() },
                                ),
                            modifier =
                                Modifier
                                    .focusRequester(textBoxFocusRequester)
                                    .fillMaxWidth(),
                        )
                        when (val st = state) {
                            is LoadingState.Error -> {
                                Text(
                                    text =
                                        st.message ?: st.exception?.localizedMessage
                                            ?: "An error occurred",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            else -> {}
                        }
                        TextButton(
                            onClick = { submit.invoke() },
                            enabled = url.isNotNullOrBlank() && state == LoadingState.Pending,
                            modifier = Modifier,
                        ) {
                            if (state == LoadingState.Loading) {
                                CircularProgress(Modifier.size(32.dp))
                            } else {
                                Text(text = stringResource(R.string.submit))
                            }
                        }
                    }
                }
            }
        }
    }
}
