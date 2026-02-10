package com.github.sysmoon.wholphin.ui.preferences

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.contentColorFor
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.SingletonImageLoader
import coil3.imageLoader
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.preferences.AppChoicePreference
import com.github.sysmoon.wholphin.preferences.AppPreference
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.AppSliderPreference
import com.github.sysmoon.wholphin.preferences.AppStringPreference
import com.github.sysmoon.wholphin.preferences.AppMultiChoicePreference
import com.github.sysmoon.wholphin.preferences.AppSwitchPreference
import com.github.sysmoon.wholphin.preferences.ExoPlayerPreferences
import com.github.sysmoon.wholphin.preferences.MpvPreferences
import com.github.sysmoon.wholphin.preferences.PlayerBackend
import com.github.sysmoon.wholphin.preferences.advancedPreferences
import com.github.sysmoon.wholphin.preferences.basicPreferences
import com.github.sysmoon.wholphin.preferences.uiPreferences
import com.github.sysmoon.wholphin.preferences.updatePlaybackPreferences
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.services.UpdateChecker
import com.github.sysmoon.wholphin.services.Release
import com.github.sysmoon.wholphin.ui.preferences.PreferenceTile
import com.github.sysmoon.wholphin.ui.preferences.PreferenceTileSize
import com.github.sysmoon.wholphin.ui.components.ConfirmDialog
import com.github.sysmoon.wholphin.ui.components.DialogItem
import com.github.sysmoon.wholphin.ui.components.DialogParams
import com.github.sysmoon.wholphin.ui.components.DialogPopup
import com.github.sysmoon.wholphin.ui.ifElse
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import com.github.sysmoon.wholphin.ui.nav.Destination
import com.github.sysmoon.wholphin.ui.nav.NavDrawerItem
import com.github.sysmoon.wholphin.ui.playOnClickSound
import com.github.sysmoon.wholphin.ui.playSoundOnFocus
import com.github.sysmoon.wholphin.ui.handleDPadKeyEvents
import com.github.sysmoon.wholphin.ui.detail.livetv.LiveTvViewOptionsDialog
import com.github.sysmoon.wholphin.ui.preferences.subtitle.SubtitleSettings
import com.github.sysmoon.wholphin.ui.preferences.subtitle.SubtitleStylePage
import com.github.sysmoon.wholphin.ui.setup.UpdateViewModel
import com.github.sysmoon.wholphin.ui.setup.seerr.AddSeerServerDialog
import com.github.sysmoon.wholphin.ui.setup.seerr.SwitchSeerrViewModel
import com.github.sysmoon.wholphin.ui.showToast
import com.github.sysmoon.wholphin.ui.tryRequestFocus
import com.github.sysmoon.wholphin.util.ExceptionHandler
import com.github.sysmoon.wholphin.util.LoadingState
import com.github.sysmoon.wholphin.util.Version
import kotlinx.coroutines.launch
import timber.log.Timber

private fun preferenceTileIcon(pref: AppPreference<AppPreferences, *>): ImageVector? =
    when (pref) {
        AppPreference.SignInAuto -> Icons.Default.Login
        AppPreference.RequireProfilePin -> Icons.Default.Lock
        AppPreference.HomePageItems,
        AppPreference.CombineContinueNext -> Icons.Default.Home
        AppPreference.RewatchNextUp -> Icons.Default.Replay
        AppPreference.BackdropStylePref -> Icons.Default.Wallpaper
        AppPreference.ThemeColors -> Icons.Default.Palette
        AppPreference.PlayThemeMusic -> Icons.Default.MusicNote
        AppPreference.RememberSelectedTab -> Icons.Default.Settings
        AppPreference.ShowClock -> Icons.Default.Schedule
        AppPreference.CombinedSearchResults -> Icons.Default.Search
        AppPreference.LiveTvOptions -> Icons.Default.Tv
        AppPreference.SubtitleStyle -> Icons.Default.Subtitles
        AppPreference.SkipForward -> Icons.Default.FastForward
        AppPreference.SkipBack,
        AppPreference.SkipBackOnResume -> Icons.Default.FastRewind
        AppPreference.ShowNextUpTiming -> Icons.Default.SkipNext
        AppPreference.AutoPlayNextUp -> Icons.Default.PlayCircleFilled
        AppPreference.AutoPlayNextDelay -> Icons.Default.Timer
        AppPreference.PassOutProtection -> Icons.Default.Bedtime
        AppPreference.SkipIntros,
        AppPreference.SkipOutros,
        AppPreference.SkipCommercials,
        AppPreference.SkipPreviews,
        AppPreference.SkipRecaps -> Icons.Default.Forward10
        AppPreference.UserPinnedNavDrawerItems -> Icons.Default.Menu
        AppPreference.SeerrIntegration -> Icons.Default.Login
        AppPreference.InstalledVersion,
        AppPreference.Update,
        AppPreference.AutoCheckForUpdates,
        AppPreference.UpdateUrl -> Icons.Default.Update
        AppPreference.AdvancedSettings -> Icons.Default.Settings
        AppPreference.OneClickPause -> Icons.Default.Pause
        AppPreference.GlobalContentScale -> Icons.Default.Crop
        AppPreference.MaxBitrate -> Icons.Default.Public
        AppPreference.RefreshRateSwitching -> Icons.Default.RestartAlt
        AppPreference.ResolutionSwitching -> Icons.Default.DisplaySettings
        AppPreference.PlaybackDebugInfo -> Icons.Default.BugReport
        AppPreference.ControllerTimeout -> Icons.Default.VisibilityOff
        AppPreference.SeekBarSteps -> Icons.Default.LinearScale
        AppPreference.PlayerBackendPref -> Icons.Default.PlayCircleFilled
        AppPreference.SendAppLogs -> Icons.Default.Send
        AppPreference.SendCrashReports -> Icons.Default.Report
        AppPreference.DebugLogging -> Icons.Default.Article
        AppPreference.ImageDiskCacheSize -> Icons.Default.AddPhotoAlternate
        AppPreference.ClearImageCache -> Icons.Default.ClearAll
        AppPreference.OssLicenseInfo -> Icons.Default.MenuBook
        else -> Icons.Default.Settings
    }

@Composable
fun PreferencesContent(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
    updateVM: UpdateViewModel = hiltViewModel(),
    seerrVm: SwitchSeerrViewModel = hiltViewModel(),
    onFocus: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var focusedIndex by rememberSaveable { mutableStateOf(Pair(0, 0)) }
    val state = rememberLazyListState()
    var preferences by remember { mutableStateOf(initialPreferences) }
    val currentUser by viewModel.currentUser.observeAsState()
    var showPinFlow by remember { mutableStateOf(false) }
    var showLiveTvDialog by remember { mutableStateOf(false) }

    val navDrawerPins by viewModel.navDrawerPins.observeAsState(mapOf())
    var cacheUsage by remember { mutableStateOf(CacheUsage(0, 0, 0)) }
    val seerrIntegrationEnabled by viewModel.seerrEnabled.collectAsState(false)
    var seerrDialogMode by remember { mutableStateOf<SeerrDialogMode>(SeerrDialogMode.None) }
    var popupPreference by remember { mutableStateOf<AppPreference<AppPreferences, *>?>(null) }
    var installedVersionClickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.preferencesFlow.collect {
            preferences = it
        }
    }
    var updateCache by remember { mutableStateOf(false) }
    LaunchedEffect(updateCache) {
        val imageUsedMemory = context.imageLoader.memoryCache?.size ?: 0L
        val imageMaxMemory = context.imageLoader.memoryCache?.maxSize ?: 0L
        val imageDisk = context.imageLoader.diskCache?.size ?: 0L
        cacheUsage = CacheUsage(imageUsedMemory, imageMaxMemory, imageDisk)
        updateCache = false
    }

    val release by updateVM.release.observeAsState(null)
    LaunchedEffect(Unit) {
        if (UpdateChecker.ACTIVE && preferences.autoCheckForUpdates) {
            updateVM.init(preferences.updateUrl)
        }
    }

    val movementSounds = true
    val installedVersion = updateVM.currentVersion
    val updateAvailable = release?.version?.isGreaterThan(installedVersion) ?: false

    val prefList =
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC -> basicPreferences + advancedPreferences
            PreferenceScreenOption.ADVANCED -> advancedPreferences
            PreferenceScreenOption.USER_INTERFACE -> uiPreferences
            PreferenceScreenOption.SUBTITLES -> SubtitleSettings.preferences
            PreferenceScreenOption.EXO_PLAYER -> ExoPlayerPreferences
            PreferenceScreenOption.MPV -> MpvPreferences
        }
    val screenTitle =
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC -> R.string.settings
            PreferenceScreenOption.ADVANCED -> R.string.advanced_settings
            PreferenceScreenOption.USER_INTERFACE -> R.string.ui_interface
            PreferenceScreenOption.SUBTITLES -> R.string.subtitle_style
            PreferenceScreenOption.EXO_PLAYER -> R.string.exoplayer_options
            PreferenceScreenOption.MPV -> R.string.mpv_options
        }

    val groupPreferenceCounts = remember(prefList, preferences) {
        prefList.map { group ->
            group.preferences.size +
                group.conditionalPreferences
                    .filter { it.condition(preferences) }
                    .sumOf { it.preferences.size }
        }
    }
    val allFocusRequesters = remember(groupPreferenceCounts) {
        groupPreferenceCounts.map { count -> List(count) { FocusRequester() } }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Forces the animated to trigger
        visible = true
    }

    LaunchedEffect(preferences.playbackPreferences.playerBackend) {
        if (preferences.playbackPreferences.playerBackend == PlayerBackend.MPV) {
            Timber.d("Checking for libmpv")
            try {
                System.loadLibrary("mpv")
                System.loadLibrary("player")
            } catch (ex: Exception) {
                Timber.w(ex, "Could not load libmpv")
                showToast(context, "MPV is not supported on this device")
                viewModel.preferenceDataStore.updateData {
                    it.updatePlaybackPreferences { playerBackend = PlayerBackend.EXO_PLAYER }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        val showUpdateBanner =
            UpdateChecker.ACTIVE &&
                preferenceScreenOption == PreferenceScreenOption.BASIC &&
                preferences.autoCheckForUpdates &&
                updateAvailable
        val updateBannerFocusRequester = remember { FocusRequester() }
        LaunchedEffect(allFocusRequesters.size) {
            allFocusRequesters.firstOrNull()?.firstOrNull()?.tryRequestFocus()
        }
        LazyColumn(
            state = state,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        ) {
            stickyHeader {
                Text(
                    text = stringResource(screenTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                )
            }
            if (showUpdateBanner) {
                item {
                    val updateInteractionSource = remember { MutableInteractionSource() }
                    val updateFocused by updateInteractionSource.collectIsFocusedAsState()
                    LaunchedEffect(Unit) {
                        if (focusedIndex.first == 0 && focusedIndex.second == 0) {
                            updateBannerFocusRequester.tryRequestFocus()
                        }
                    }
                    LaunchedEffect(updateFocused) {
                        if (updateFocused) focusedIndex = Pair(-1, -1)
                    }
                    val updateBg =
                        if (updateFocused) MaterialTheme.colorScheme.inverseSurface
                        else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    val updateContentColor = contentColorFor(updateBg)
                    val updateShape = RoundedCornerShape(8.dp)
                    val updateBorderWidth = if (updateFocused) 3.dp else 1.dp
                    val updateBorderColor =
                        if (updateFocused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .focusRequester(updateBannerFocusRequester)
                                    .focusable(interactionSource = updateInteractionSource)
                                    .onFocusChanged { if (it.isFocused) focusedIndex = Pair(-1, -1) }
                                    .focusProperties {
                                        down =
                                            allFocusRequesters.firstOrNull()?.firstOrNull()
                                                ?: FocusRequester.Default
                                    }
                                    .handleDPadKeyEvents(
                                        onCenter = {
                                            if (movementSounds) playOnClickSound(context)
                                            viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                                        },
                                    )
                                    .clickable {
                                        if (movementSounds) playOnClickSound(context)
                                        viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                                    }
                                    .background(updateBg, shape = updateShape)
                                    .border(updateBorderWidth, updateBorderColor, updateShape)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .playSoundOnFocus(movementSounds),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.install_update),
                                style = MaterialTheme.typography.labelLarge,
                                color = updateContentColor,
                            )
                            release?.version?.let { ver ->
                                Text(
                                    text = " · ${ver}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = updateContentColor,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            prefList.forEachIndexed { groupIndex, group ->
                val groupPreferences: List<AppPreference<AppPreferences, *>> =
                    group.preferences +
                        group.conditionalPreferences
                            .filter { it.condition.invoke(preferences) }
                            .map { it.preferences }
                            .flatten()
                item(key = "group_$groupIndex") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(group.title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val requesters = allFocusRequesters.getOrNull(groupIndex).orEmpty()
                            groupPreferences.forEachIndexed { prefIndex, pref ->
                                val prefValue = pref.getter.invoke(preferences)
                                val tileRequester = requesters.getOrNull(prefIndex)
                                val nextRequester = requesters.getOrNull(prefIndex + 1)
                                val previousRequester = requesters.getOrNull(prefIndex - 1)
                                val downRequester =
                                    allFocusRequesters.getOrNull(groupIndex + 1)?.let { nextRow ->
                                        nextRow.getOrNull(
                                            prefIndex.coerceIn(0, nextRow.size - 1),
                                        )
                                    }
                                val upRequester =
                                    if (groupIndex == 0 && showUpdateBanner) updateBannerFocusRequester
                                    else
                                        allFocusRequesters.getOrNull(groupIndex - 1)?.let { prevRow ->
                                            prevRow.getOrNull(
                                                prefIndex.coerceIn(0, prevRow.size - 1),
                                            )
                                        }
                                val (onToggle, onTileClick) = when (pref) {
                                    AppPreference.RequireProfilePin ->
                                        null to { showPinFlow = true }
                                    is AppSwitchPreference -> when (pref) {
                                        else -> {
                                            val toggle: () -> Unit = {
                                                val newVal = !(prefValue as Boolean)
                                                val validation = pref.validate(newVal)
                                                if (validation is PreferenceValidation.Valid) {
                                                    viewModel.updatePreference(
                                                        pref as AppPreference<AppPreferences, Any?>,
                                                        newVal,
                                                        preferences,
                                                    )
                                                    preferences = pref.setter(preferences, newVal)
                                                }
                                            }
                                            toggle to toggle
                                        }
                                    }
                                    AppPreference.SeerrIntegration ->
                                        null to {
                                            if (seerrIntegrationEnabled) {
                                                seerrDialogMode = SeerrDialogMode.Remove
                                            } else {
                                                seerrVm.resetStatus()
                                                seerrDialogMode = SeerrDialogMode.Add
                                            }
                                        }
                                    AppPreference.LiveTvOptions ->
                                        null to { showLiveTvDialog = true }
                                    AppPreference.InstalledVersion ->
                                        null to {
                                            if (movementSounds) playOnClickSound(context)
                                            if (++installedVersionClickCount >= 2) {
                                                installedVersionClickCount = 0
                                                viewModel.navigationManager.navigateTo(Destination.Debug)
                                            }
                                        }
                                    AppPreference.Update ->
                                        null to {
                                            if (movementSounds) playOnClickSound(context)
                                            if (release != null && updateAvailable) {
                                                viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                                            } else {
                                                updateVM.init(preferences.updateUrl)
                                            }
                                        }
                                    AppPreference.ClearImageCache ->
                                        null to {
                                            SingletonImageLoader.get(context).let {
                                                it.memoryCache?.clear()
                                                it.diskCache?.clear()
                                                updateCache = true
                                            }
                                        }
                                    AppPreference.SendAppLogs ->
                                        null to { viewModel.sendAppLogs() }
                                    SubtitleSettings.Reset ->
                                        null to { viewModel.resetSubtitleSettings() }
                                    AppPreference.UserPinnedNavDrawerItems ->
                                        null to { popupPreference = pref }
                                    else ->
                                        null to { popupPreference = pref }
                                }
                                PreferenceTile(
                                    pref = pref,
                                    groupIndex = groupIndex,
                                    prefIndex = prefIndex,
                                    value = prefValue,
                                    valueSummary =
                                        ((pref as AppPreference<AppPreferences, Any?>).summary(context, prefValue)?.takeIf { it.isNotBlank() }
                                            ?: when (pref) {
                                                is AppChoicePreference<*, *> -> {
                                                    val choice = pref as AppChoicePreference<AppPreferences, Any>
                                                    prefValue?.let { v ->
                                                        stringArrayResource(choice.displayValues).getOrNull(choice.valueToIndex(v))
                                                    }
                                                }
                                                else -> prefValue?.takeIf { it !== Unit }?.toString()
                                            }) as String?,
                                    icon = preferenceTileIcon(pref),
                                    modifier = Modifier,
                                    focusRequester = tileRequester!!,
                                    nextFocus = nextRequester,
                                    previousFocus = previousRequester,
                                    downFocus = downRequester,
                                    upFocus = upRequester,
                                    focusedIndex = focusedIndex,
                                    setFocusedIndex = { focusedIndex = it },
                                    movementSounds = movementSounds,
                                    onFocus = onFocus,
                                    onTileClick = onTileClick,
                                    onToggle = onToggle,
                                )
                            }
                        }
                    }
                }
            }
        }
        popupPreference?.let { pref ->
            PreferencePopupContent(
                pref = pref,
                preferences = preferences,
                setPreferences = { preferences = it },
                viewModel = viewModel,
                context = context,
                navDrawerPins = navDrawerPins,
                onDismiss = { popupPreference = null },
            )
        }
        if (showPinFlow && currentUser != null) {
            currentUser?.let { user ->
                SetPinFlow(
                    currentPin = user.pin,
                    onAddPin = {
                        viewModel.setPin(user, it)
                        showPinFlow = false
                    },
                    onRemovePin = {
                        viewModel.setPin(user, null)
                        showPinFlow = false
                    },
                    onDismissRequest = { showPinFlow = false },
                )
            }
        }
        when (seerrDialogMode) {
            SeerrDialogMode.Remove -> {
                ConfirmDialog(
                    title = stringResource(R.string.remove_seerr_server),
                    body = "",
                    onCancel = { seerrDialogMode = SeerrDialogMode.None },
                    onConfirm = {
                        seerrVm.removeServer()
                        seerrDialogMode = SeerrDialogMode.None
                    },
                )
            }

            SeerrDialogMode.Add -> {
                val currentUser by seerrVm.currentUser.observeAsState()
                val status by seerrVm.serverConnectionStatus.collectAsState(LoadingState.Pending)
                val serverAddedMessage = stringResource(R.string.seerr_server_added)
                LaunchedEffect(status) {
                    if (status == LoadingState.Success) {
                        Toast.makeText(context, serverAddedMessage, Toast.LENGTH_SHORT).show()
                        seerrDialogMode = SeerrDialogMode.None
                    }
                }
                AddSeerServerDialog(
                    currentUsername = currentUser?.name,
                    status = status,
                    onSubmit = seerrVm::submitServer,
                    onDismissRequest = { seerrDialogMode = SeerrDialogMode.None },
                )
            }

            SeerrDialogMode.None -> {}
        }
        if (showLiveTvDialog) {
            LiveTvViewOptionsDialog(
                preferences = preferences,
                onDismissRequest = { showLiveTvDialog = false },
                onViewOptionsChange = { newPrefs ->
                    viewModel.updateUserPreferencesFromMerged(newPrefs)
                    preferences = newPrefs
                },
            )
        }
    }
}

@Composable
private fun PreferencePopupContent(
    pref: AppPreference<AppPreferences, *>,
    preferences: AppPreferences,
    setPreferences: (AppPreferences) -> Unit,
    viewModel: PreferencesViewModel,
    context: Context,
    navDrawerPins: Map<NavDrawerItem, Boolean>,
    onDismiss: () -> Unit,
) {
    val value = pref.getter.invoke(preferences)
    val title = stringResource(pref.title)

    when (pref) {
        is AppChoicePreference -> {
            val choicePref = pref as AppChoicePreference<AppPreferences, Any>
            val values = stringArrayResource(choicePref.displayValues).toList()
            val subtitles = choicePref.subtitles?.let { stringArrayResource(it).toList() }
            val selectedIndex = choicePref.valueToIndex(value as Any)
            val params =
                DialogParams(
                    fromLongClick = false,
                    title = title,
                    items =
                        values.mapIndexed { index, label ->
                            DialogItem(
                                headlineContent = { Text(label) },
                                leadingContent = {
                                    if (index == selectedIndex) {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = "selected",
                                        )
                                    }
                                },
                                supportingContent = {
                                    subtitles?.getOrNull(index)?.takeIf { s -> s.isNotNullOrBlank() }?.let {
                                        Text(it)
                                    }
                                },
                                onClick = {
                                    val newVal = choicePref.indexToValue(index)
                                    val validation = choicePref.validate(newVal)
                                    if (validation is PreferenceValidation.Valid) {
                                        viewModel.updatePreference(
                                            choicePref as AppPreference<AppPreferences, Any?>,
                                            newVal,
                                            preferences,
                                        )
                                        setPreferences(choicePref.setter(preferences, newVal))
                                    }
                                    onDismiss()
                                },
                            )
                        },
                )
            DialogPopup(
                showDialog = true,
                title = params.title,
                dialogItems = params.items,
                onDismissRequest = onDismiss,
                waitToLoad = false,
                dismissOnClick = false,
            )
        }

        is AppSliderPreference -> {
            val sliderPref = pref as AppSliderPreference<AppPreferences>
            val longValue: Long = (value as? Long) ?: sliderPref.defaultValue
            val summary = sliderPref.summary(context, longValue) ?: sliderPref.summary?.let { stringResource(it) }
            androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
                Box(
                    modifier =
                        Modifier
                            .padding(24.dp)
                            .background(MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        SliderPreference(
                            preference = sliderPref,
                            title = title,
                            summary = summary,
                            value = longValue,
                            onChange = { newVal ->
                                viewModel.updatePreference(
                                    sliderPref as AppPreference<AppPreferences, Any?>,
                                    newVal,
                                    preferences,
                                )
                                setPreferences(sliderPref.setter(preferences, newVal))
                            },
                        )
                    }
                }
            }
        }

        is AppStringPreference -> {
            val stringInput =
                StringInput(
                    title = title,
                    value = value as? String,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    onSubmit = { newVal ->
                        val validation = pref.validate(newVal)
                        when (validation) {
                            is PreferenceValidation.Invalid ->
                                Toast.makeText(context, validation.message, Toast.LENGTH_SHORT).show()
                            PreferenceValidation.Valid -> {
                                viewModel.updatePreference(
                                    pref as AppPreference<AppPreferences, Any?>,
                                    newVal,
                                    preferences,
                                )
                                setPreferences(pref.setter(preferences, newVal))
                                onDismiss()
                            }
                        }
                    },
                )
            StringInputDialog(
                input = stringInput,
                onSave = { stringInput.onSubmit.invoke(it) },
                onDismissRequest = onDismiss,
            )
        }

        AppPreference.UserPinnedNavDrawerItems -> {
            val dialogParamsValue =
                DialogParams(
                    fromLongClick = false,
                    title = title,
                    items =
                        navDrawerPins.keys.map { item ->
                            DialogItem(
                                headlineContent = { Text(item.name(context)) },
                                trailingContent = {
                                    androidx.tv.material3.Switch(
                                        checked = navDrawerPins[item] == true,
                                        onCheckedChange = { },
                                    )
                                },
                                onClick = {
                                    val current = navDrawerPins.toMutableMap()
                                    current[item] = current[item] != true
                                    viewModel.updatePins(
                                        current.filterValues { it }.keys.toList(),
                                    )
                                },
                            )
                        },
                )
            DialogPopup(
                showDialog = true,
                title = dialogParamsValue.title,
                dialogItems = dialogParamsValue.items,
                onDismissRequest = onDismiss,
                waitToLoad = false,
                dismissOnClick = false,
            )
        }

        is AppMultiChoicePreference<*, *> -> {
            val labels = stringArrayResource(pref.displayValues).toList()
            val allValues = pref.allValues
            val selectedSet = remember(value) { (value as? List<*>)?.toSet() ?: emptySet<Any>() }
            val params =
                DialogParams(
                    fromLongClick = false,
                    title = title,
                    items =
                        allValues.mapIndexed { index, optionValue ->
                            val label = labels.getOrNull(index) ?: optionValue.toString()
                            val isSelected = selectedSet.contains(optionValue)
                            DialogItem(
                                headlineContent = { Text(label) },
                                trailingContent = {
                                    androidx.tv.material3.Switch(
                                        checked = isSelected,
                                        onCheckedChange = { },
                                    )
                                },
                                onClick = {
                                    val currentList = (value as? List<Any>)?.toMutableList() ?: mutableListOf()
                                    if (currentList.contains(optionValue)) {
                                        currentList.remove(optionValue)
                                    } else {
                                        currentList.add(optionValue as Any)
                                    }
                                    viewModel.updatePreference(
                                        pref as AppPreference<AppPreferences, Any?>,
                                        currentList,
                                        preferences,
                                    )
                                    setPreferences(pref.setter(preferences, currentList))
                                },
                            )
                        },
                )
            DialogPopup(
                showDialog = true,
                title = params.title,
                dialogItems = params.items,
                onDismissRequest = onDismiss,
                waitToLoad = false,
                dismissOnClick = false,
            )
        }

        else -> {
            onDismiss()
        }
    }
}

@Composable
fun PreferencesPage(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC,
            PreferenceScreenOption.ADVANCED,
            PreferenceScreenOption.USER_INTERFACE,
            PreferenceScreenOption.EXO_PLAYER,
            PreferenceScreenOption.MPV,
            -> {
                PreferencesContent(
                    initialPreferences,
                    preferenceScreenOption,
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.Center),
                )
            }

            PreferenceScreenOption.SUBTITLES -> {
                SubtitleStylePage(
                    initialPreferences,
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                )
            }
        }
    }
}

data class CacheUsage(
    val imageMemoryUsed: Long,
    val imageMemoryMax: Long,
    val imageDiskUsed: Long,
)

private sealed class SeerrDialogMode {
    data object None : SeerrDialogMode()

    data object Add : SeerrDialogMode()

    data object Remove : SeerrDialogMode()
}
