package com.github.sysmoon.wholphin.services

import androidx.datastore.core.DataStore
import com.github.sysmoon.wholphin.data.ServerPreferencesDao
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.data.model.NavDrawerPinnedItem
import com.github.sysmoon.wholphin.data.model.NavPinType
import com.github.sysmoon.wholphin.data.model.SeerrAuthMethod
import com.github.sysmoon.wholphin.preferences.AppPreference
import com.github.sysmoon.wholphin.preferences.AppPreferences
import com.github.sysmoon.wholphin.preferences.AppThemeColors
import com.github.sysmoon.wholphin.preferences.BackdropStyle
import com.github.sysmoon.wholphin.preferences.PrefContentScale
import com.github.sysmoon.wholphin.preferences.ShowNextUpWhen
import com.github.sysmoon.wholphin.preferences.SkipSegmentBehavior
import com.github.sysmoon.wholphin.preferences.ThemeSongVolume
import com.github.sysmoon.wholphin.preferences.UserPreferencesRepository
import com.github.sysmoon.wholphin.preferences.update
import com.github.sysmoon.wholphin.preferences.updateHomePagePreferences
import com.github.sysmoon.wholphin.preferences.updateInterfacePreferences
import com.github.sysmoon.wholphin.preferences.updateLiveTvPreferences
import com.github.sysmoon.wholphin.preferences.updatePlaybackPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Converts camelCase to snake_case so we recognize plugin keys regardless of format. */
private fun String.toSnakeCase(): String =
    replace(Regex("([A-Z])"), { "_${it.value.lowercase()}" }).trimStart('_')

private val BITRATE_VALUES =
    listOf(
        500 * 1024L,
        750 * 1024L,
        1 * AppPreference.MEGA_BIT,
        2 * AppPreference.MEGA_BIT,
        3 * AppPreference.MEGA_BIT,
        5 * AppPreference.MEGA_BIT,
        8 * AppPreference.MEGA_BIT,
        10 * AppPreference.MEGA_BIT,
        15 * AppPreference.MEGA_BIT,
        20 * AppPreference.MEGA_BIT,
        *(30..100 step 10).map { it * AppPreference.MEGA_BIT }.toTypedArray(),
        *(120..200 step 20).map { it * AppPreference.MEGA_BIT }.toTypedArray(),
    )

/**
 * Maps plugin setting keys to the corresponding [AppPreference] for UI (e.g. greying out tiles).
 * Includes every key that can appear in GET /Wholphin/Settings response and has a settings tile.
 */
object PluginSettingsKeyToPreference {
    fun pluginControlledPrefsFromResponse(response: PluginSettingsResponse): Set<AppPreference<AppPreferences, *>> {
        val set = mutableSetOf<AppPreference<AppPreferences, *>>()
        for (key in response.global.keys) {
            val normalized = key.toSnakeCase()
            GLOBAL_KEYS[normalized]?.let { set.add(it) }
        }
        val user = response.user ?: return set
        if (user.max_homepage_items != null) set.add(AppPreference.HomePageItems)
        if (user.rewatch_next_up != null) set.add(AppPreference.RewatchNextUp)
        if (user.combine_continue_next != null) set.add(AppPreference.CombineContinueNext)
        if (user.backdrop_display != null) set.add(AppPreference.BackdropStylePref)
        if (user.play_theme_music != null) set.add(AppPreference.PlayThemeMusic)
        if (user.remember_selected_tab != null) set.add(AppPreference.RememberSelectedTab)
        if (user.app_theme != null) set.add(AppPreference.ThemeColors)
        if (user.show_clock != null) set.add(AppPreference.ShowClock)
        if (user.combined_search_results != null) set.add(AppPreference.CombinedSearchResults)
        if (user.nav_drawer_switch_on_focus != null) set.add(AppPreference.NavDrawerSwitchOnFocus)
        if (user.skip_forward_preference != null) set.add(AppPreference.SkipForward)
        if (user.skip_back_preference != null) set.add(AppPreference.SkipBack)
        if (user.skip_back_on_resume_preference != null) set.add(AppPreference.SkipBackOnResume)
        if (user.hide_controller_timeout != null) set.add(AppPreference.ControllerTimeout)
        if (user.seek_bar_steps != null) set.add(AppPreference.SeekBarSteps)
        if (user.playback_debug_info != null) set.add(AppPreference.PlaybackDebugInfo)
        if (user.global_content_scale != null) set.add(AppPreference.GlobalContentScale)
        if (user.one_click_pause != null) set.add(AppPreference.OneClickPause)
        if (user.auto_play_next != null) set.add(AppPreference.AutoPlayNextUp)
        if (user.auto_play_next_delay != null) set.add(AppPreference.AutoPlayNextDelay)
        if (user.show_next_up_when != null) set.add(AppPreference.ShowNextUpTiming)
        if (user.pass_out_protection != null) set.add(AppPreference.PassOutProtection)
        if (user.skip_intro_behavior != null) set.add(AppPreference.SkipIntros)
        if (user.skip_outro_behavior != null) set.add(AppPreference.SkipOutros)
        if (user.skip_commercials_behavior != null) set.add(AppPreference.SkipCommercials)
        if (user.skip_previews_behavior != null) set.add(AppPreference.SkipPreviews)
        if (user.skip_recap_behavior != null) set.add(AppPreference.SkipRecaps)
        if (user.show_details != null) set.add(AppPreference.LiveTvShowHeader)
        if (user.favorite_channels_at_beginning != null) set.add(AppPreference.LiveTvFavoriteChannelsBeginning)
        if (user.sort_channels_recently_watched != null) set.add(AppPreference.LiveTvChannelSortByWatched)
        if (user.color_code_programs != null) set.add(AppPreference.LiveTvColorCodePrograms)
        if (user.seerr_credentials != null) set.add(AppPreference.SeerrIntegration)
        if (user.nav_drawer_items != null) set.add(AppPreference.UserPinnedNavDrawerItems)
        return set
    }

    private val GLOBAL_KEYS =
        mapOf(
            "sign_in_auto" to AppPreference.SignInAuto,
            "update_url" to AppPreference.UpdateUrl,
            "max_bitrate" to AppPreference.MaxBitrate,
        )

    /** Plugin key for a preference (for recording user overrides). Returns null if not a plugin-controlled key. */
    fun pluginKeyForPreference(pref: AppPreference<AppPreferences, *>): String? = PREF_TO_PLUGIN_KEY[pref]

    private val PREF_TO_PLUGIN_KEY: Map<AppPreference<AppPreferences, *>, String> =
        buildMap {
            GLOBAL_KEYS.forEach { (k, v) -> put(v, k) }
            put(AppPreference.HomePageItems, "max_homepage_items")
            put(AppPreference.RewatchNextUp, "rewatch_next_up")
            put(AppPreference.CombineContinueNext, "combine_continue_next")
            put(AppPreference.BackdropStylePref, "backdrop_display")
            put(AppPreference.PlayThemeMusic, "play_theme_music")
            put(AppPreference.RememberSelectedTab, "remember_selected_tab")
            put(AppPreference.ThemeColors, "app_theme")
            put(AppPreference.ShowClock, "show_clock")
            put(AppPreference.CombinedSearchResults, "combined_search_results")
            put(AppPreference.NavDrawerSwitchOnFocus, "nav_drawer_switch_on_focus")
            put(AppPreference.SkipForward, "skip_forward_preference")
            put(AppPreference.SkipBack, "skip_back_preference")
            put(AppPreference.SkipBackOnResume, "skip_back_on_resume_preference")
            put(AppPreference.ControllerTimeout, "hide_controller_timeout")
            put(AppPreference.SeekBarSteps, "seek_bar_steps")
            put(AppPreference.PlaybackDebugInfo, "playback_debug_info")
            put(AppPreference.GlobalContentScale, "global_content_scale")
            put(AppPreference.OneClickPause, "one_click_pause")
            put(AppPreference.AutoPlayNextUp, "auto_play_next")
            put(AppPreference.AutoPlayNextDelay, "auto_play_next_delay")
            put(AppPreference.ShowNextUpTiming, "show_next_up_when")
            put(AppPreference.PassOutProtection, "pass_out_protection")
            put(AppPreference.SkipIntros, "skip_intro_behavior")
            put(AppPreference.SkipOutros, "skip_outro_behavior")
            put(AppPreference.SkipCommercials, "skip_commercials_behavior")
            put(AppPreference.SkipPreviews, "skip_previews_behavior")
            put(AppPreference.SkipRecaps, "skip_recap_behavior")
            put(AppPreference.LiveTvShowHeader, "show_details")
            put(AppPreference.LiveTvFavoriteChannelsBeginning, "favorite_channels_at_beginning")
            put(AppPreference.LiveTvChannelSortByWatched, "sort_channels_recently_watched")
            put(AppPreference.LiveTvColorCodePrograms, "color_code_programs")
            put(AppPreference.SeerrIntegration, "seerr_credentials")
            put(AppPreference.UserPinnedNavDrawerItems, "nav_drawer_items")
        }
}

@Singleton
class PluginSettingsApplicator
    @Inject
    constructor(
        private val appPreferencesDataStore: DataStore<AppPreferences>,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val seerrServerRepository: SeerrServerRepository,
        private val serverPreferencesDao: ServerPreferencesDao,
    ) {

    /**
     * Applies plugin settings to device prefs, user prefs, Seerr, and nav drawer.
     * @return Set of [AppPreference] that were present in the response (for greying out tiles).
     */
    suspend fun apply(
        settings: PluginSettingsResponse,
        currentUser: JellyfinUser,
    ): Set<AppPreference<AppPreferences, *>> {
        applyGlobal(settings.global)
        applyUser(settings.user, currentUser)
        applySeerr(settings.user?.seerr_credentials, currentUser)
        applyNavDrawer(settings.user?.nav_drawer_items, currentUser)
        val controlled = PluginSettingsKeyToPreference.pluginControlledPrefsFromResponse(settings)
        Timber.d("PluginSettingsApplicator: Applied settings, pluginControlledPrefs size=%s", controlled.size)
        return controlled
    }

    private suspend fun applyGlobal(global: Map<String, String>) {
        if (global.isEmpty()) return
        appPreferencesDataStore.updateData { prefs ->
            var updated = prefs
            for ((key, value) in global) {
                val normalized = key.toSnakeCase()
                updated =
                    when (normalized) {
                        "sign_in_auto" -> updated.update { signInAutomatically = value.toBooleanStrictOrNull() ?: updated.signInAutomatically }
                        "update_url" -> updated.update { updateUrl = value }
                        "max_bitrate" -> {
                            val index = value.toIntOrNull()?.coerceIn(0, BITRATE_VALUES.size - 1) ?: return@updateData updated
                            val bps = BITRATE_VALUES.getOrNull(index) ?: return@updateData updated
                            updated.updatePlaybackPreferences { maxBitrate = bps }
                        }
                        else -> updated
                    }
            }
            updated
        }
    }

    private suspend fun applyUser(user: UserSettingsResponse?, currentUser: JellyfinUser) {
        if (user == null) return
        val currentMerged = userPreferencesRepository.getMergedPreferencesOnce(currentUser.rowId)
        val overriddenKeys = currentMerged.interfacePreferences.overriddenPluginKeysList.toSet()
        val updated =
            currentMerged
                .applyUserString("hide_settings_cog", user.hide_settings_cog, overriddenKeys) { v ->
                    updateInterfacePreferences { hideSettingsCog = v.toBooleanStrictOrNull() ?: hideSettingsCog }
                }
                .applyUserString("allow_settings_override", user.allow_settings_override, overriddenKeys) { v ->
                    updateInterfacePreferences { allowSettingsOverride = v.toBooleanStrictOrNull() ?: allowSettingsOverride }
                }
                .applyUserString("max_homepage_items", user.max_homepage_items, overriddenKeys) { v ->
                    updateHomePagePreferences { maxItemsPerRow = v.toIntOrNull() ?: maxItemsPerRow }
                }
                .applyUserString("rewatch_next_up", user.rewatch_next_up, overriddenKeys) { v ->
                    updateHomePagePreferences { enableRewatchingNextUp = v.toBooleanStrictOrNull() ?: enableRewatchingNextUp }
                }
                .applyUserString("combine_continue_next", user.combine_continue_next, overriddenKeys) { v ->
                    updateHomePagePreferences { combineContinueNext = v.toBooleanStrictOrNull() ?: combineContinueNext }
                }
                .applyUserString("backdrop_display", user.backdrop_display, overriddenKeys) { v ->
                    updateInterfacePreferences {
                        backdropStyle = when (v.toIntOrNull()) {
                            0 -> BackdropStyle.BACKDROP_DYNAMIC_COLOR
                            1 -> BackdropStyle.BACKDROP_IMAGE_ONLY
                            2 -> BackdropStyle.BACKDROP_NONE
                            else -> backdropStyle
                        }
                    }
                }
                .applyUserString("play_theme_music", user.play_theme_music, overriddenKeys) { v ->
                    updateInterfacePreferences {
                        playThemeSongs = ThemeSongVolume.forNumber(v.toIntOrNull() ?: 3)
                    }
                }
                .applyUserString("remember_selected_tab", user.remember_selected_tab, overriddenKeys) { v ->
                    updateInterfacePreferences { rememberSelectedTab = v.toBooleanStrictOrNull() ?: rememberSelectedTab }
                }
                .applyUserString("app_theme", user.app_theme, overriddenKeys) { v ->
                    updateInterfacePreferences {
                        appThemeColors = AppThemeColors.forNumber(v.toIntOrNull() ?: 0)
                    }
                }
                .applyUserString("show_clock", user.show_clock, overriddenKeys) { v ->
                    updateInterfacePreferences { showClock = v.toBooleanStrictOrNull() ?: showClock }
                }
                .applyUserString("combined_search_results", user.combined_search_results, overriddenKeys) { v ->
                    updateInterfacePreferences { combinedSearchResults = v.toBooleanStrictOrNull() ?: combinedSearchResults }
                }
                .applyUserString("nav_drawer_switch_on_focus", user.nav_drawer_switch_on_focus, overriddenKeys) { v ->
                    updateInterfacePreferences { navDrawerSwitchOnFocus = v.toBooleanStrictOrNull() ?: navDrawerSwitchOnFocus }
                }
                .applyUserString("skip_forward_preference", user.skip_forward_preference, overriddenKeys) { v ->
                    updatePlaybackPreferences {
                        skipForwardMs = (v.toLongOrNull() ?: 30).seconds.inWholeMilliseconds
                    }
                }
                .applyUserString("skip_back_preference", user.skip_back_preference, overriddenKeys) { v ->
                    updatePlaybackPreferences {
                        skipBackMs = (v.toLongOrNull() ?: 10).seconds.inWholeMilliseconds
                    }
                }
                .applyUserString("skip_back_on_resume_preference", user.skip_back_on_resume_preference, overriddenKeys) { v ->
                    updatePlaybackPreferences {
                        skipBackOnResumeSeconds = (v.toLongOrNull() ?: 0).seconds.inWholeMilliseconds
                    }
                }
                .applyUserString("hide_controller_timeout", user.hide_controller_timeout, overriddenKeys) { v ->
                    updatePlaybackPreferences { controllerTimeoutMs = v.toLongOrNull() ?: controllerTimeoutMs }
                }
                .applyUserString("seek_bar_steps", user.seek_bar_steps, overriddenKeys) { v ->
                    updatePlaybackPreferences { seekBarSteps = (v.toIntOrNull() ?: seekBarSteps).toInt() }
                }
                .applyUserString("playback_debug_info", user.playback_debug_info, overriddenKeys) { v ->
                    updatePlaybackPreferences { showDebugInfo = v.toBooleanStrictOrNull() ?: showDebugInfo }
                }
                .applyUserString("global_content_scale", user.global_content_scale, overriddenKeys) { v ->
                    updatePlaybackPreferences {
                        globalContentScale = PrefContentScale.forNumber(v.toIntOrNull() ?: 0)
                    }
                }
                .applyUserString("one_click_pause", user.one_click_pause, overriddenKeys) { v ->
                    updatePlaybackPreferences { oneClickPause = v.toBooleanStrictOrNull() ?: oneClickPause }
                }
                .applyUserString("auto_play_next", user.auto_play_next, overriddenKeys) { v ->
                    updatePlaybackPreferences { autoPlayNext = v.toBooleanStrictOrNull() ?: autoPlayNext }
                }
                .applyUserString("auto_play_next_delay", user.auto_play_next_delay, overriddenKeys) { v ->
                    updatePlaybackPreferences { autoPlayNextDelaySeconds = v.toLongOrNull() ?: autoPlayNextDelaySeconds }
                }
                .applyUserString("show_next_up_when", user.show_next_up_when, overriddenKeys) { v ->
                    updatePlaybackPreferences {
                        showNextUpWhen = ShowNextUpWhen.forNumber(v.toIntOrNull() ?: 0)
                    }
                }
                .applyUserString("pass_out_protection", user.pass_out_protection, overriddenKeys) { v ->
                    updatePlaybackPreferences {
                        passOutProtectionMs = (v.toLongOrNull() ?: 2).hours.inWholeMilliseconds
                    }
                }
                .applyUserString("skip_intro_behavior", user.skip_intro_behavior, overriddenKeys) { v ->
                    updatePlaybackPreferences { skipIntros = SkipSegmentBehavior.forNumber(v.toIntOrNull() ?: 1) }
                }
                .applyUserString("skip_outro_behavior", user.skip_outro_behavior, overriddenKeys) { v ->
                    updatePlaybackPreferences { skipOutros = SkipSegmentBehavior.forNumber(v.toIntOrNull() ?: 1) }
                }
                .applyUserString("skip_commercials_behavior", user.skip_commercials_behavior, overriddenKeys) { v ->
                    updatePlaybackPreferences { skipCommercials = SkipSegmentBehavior.forNumber(v.toIntOrNull() ?: 1) }
                }
                .applyUserString("skip_previews_behavior", user.skip_previews_behavior, overriddenKeys) { v ->
                    updatePlaybackPreferences { skipPreviews = SkipSegmentBehavior.forNumber(v.toIntOrNull() ?: 0) }
                }
                .applyUserString("skip_recap_behavior", user.skip_recap_behavior, overriddenKeys) { v ->
                    updatePlaybackPreferences { skipRecaps = SkipSegmentBehavior.forNumber(v.toIntOrNull() ?: 0) }
                }
                .applyUserString("show_details", user.show_details, overriddenKeys) { v ->
                    updateLiveTvPreferences { showHeader = v.toBooleanStrictOrNull() ?: showHeader }
                }
                .applyUserString("favorite_channels_at_beginning", user.favorite_channels_at_beginning, overriddenKeys) { v ->
                    updateLiveTvPreferences { favoriteChannelsAtBeginning = v.toBooleanStrictOrNull() ?: favoriteChannelsAtBeginning }
                }
                .applyUserString("sort_channels_recently_watched", user.sort_channels_recently_watched, overriddenKeys) { v ->
                    updateLiveTvPreferences { sortByRecentlyWatched = v.toBooleanStrictOrNull() ?: sortByRecentlyWatched }
                }
                .applyUserString("color_code_programs", user.color_code_programs, overriddenKeys) { v ->
                    updateLiveTvPreferences { colorCodePrograms = v.toBooleanStrictOrNull() ?: colorCodePrograms }
                }
        userPreferencesRepository.updateUserPreferences(currentUser.rowId, currentMerged) { updated }
    }

    private inline fun AppPreferences.applyUserString(
        key: String,
        value: String?,
        overriddenKeys: Set<String>,
        block: AppPreferences.(String) -> AppPreferences,
    ): AppPreferences =
        if (value != null && key !in overriddenKeys) block(value) else this

    private suspend fun applySeerr(creds: SeerrCredentialsDto?, currentUser: JellyfinUser) {
        if (creds == null || creds.url.isBlank()) return
        try {
            val authMethod =
                when (creds.authMethod.uppercase()) {
                    "API_KEY" -> SeerrAuthMethod.API_KEY
                    "JELLYFIN" -> SeerrAuthMethod.JELLYFIN
                    "LOCAL" -> SeerrAuthMethod.LOCAL
                    else -> {
                        Timber.w("PluginSettingsApplicator: Unknown Seerr authMethod=%s", creds.authMethod)
                        return
                    }
                }
            val passwordOrApiKey = creds.passwordOrApiKey ?: ""
            if (authMethod == SeerrAuthMethod.API_KEY) {
                seerrServerRepository.addAndChangeServer(creds.url, passwordOrApiKey)
            } else {
                seerrServerRepository.addAndChangeServer(
                    creds.url,
                    authMethod,
                    creds.username ?: "",
                    passwordOrApiKey,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "PluginSettingsApplicator: Failed to apply Seerr credentials")
        }
    }

    private suspend fun applyNavDrawer(navDrawerItems: NavDrawerItemsDto?, currentUser: JellyfinUser) {
        if (navDrawerItems == null || navDrawerItems.items.isEmpty()) return
        val items =
            navDrawerItems.items.mapIndexed { index, dto ->
                NavDrawerPinnedItem(
                    userId = currentUser.rowId,
                    itemId = dto.itemId,
                    type = if (dto.type.equals("UNPINNED", ignoreCase = true)) NavPinType.UNPINNED else NavPinType.PINNED,
                    position = index,
                )
            }
        serverPreferencesDao.saveNavDrawerPinnedItems(*items.toTypedArray())
    }
}
