# Wholphin plugin settings reference

Single reference for building the companion plugin's settings section. All keys, names, types, scopes, and value semantics are below. **Scope:** `global` = device-only (same for all users); `user` = per-user.

---

## 1. Global (device-only) settings

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `sign_in_auto` | Sign in automatically | boolean | default: true. Summary: Enabled / Disabled |
| `auto_check_for_updates` | Automatically check for updates | boolean | default: true. Summary: Enabled / Disabled |
| `update_url` | Update URL | string | default: `https://api.github.com/repos/sysmoon14/Wholphin/releases/latest`. Summary: URL used to check for app updates |
| `send_crash_reports` | Send crash reports | boolean | default: true |
| `verbose_logging` | Verbose logging | boolean | default: false. Summary: Enabled / Disabled |
| `image_cache_size` | Image disk cache size (MB) | integer | min: 25, max: 1000, step: 25, default: 200. Unit: MB |
| `clear_image_cache` | Clear image cache | action | Click-only; no value |
| `player_backend` | Player backend | choice | Options (array `player_backend_options`): "ExoPlayer", "MPV", "Prefer MPV". Default index: 2 (Prefer MPV). Subtitles array: (empty), (empty), "Use ExoPlayer for HDR playback" |
| `exoplayer_options` | ExoPlayer options | action | Navigate to sub-screen; click-only |
| `mpv_options` | MPV options | action | Navigate to sub-screen; click-only |
| `ffmpeg_extension_pref` | FFmpeg extension | choice | Options (`ffmpeg_extension_options`): "Only use FFmpeg if no built-in decoder exists", "Prefer to use FFmpeg over built-in decoders", "Never use FFmpeg decoders". Default index: 0 |
| `downmix_stereo` | Downmix to stereo | boolean | default: true. Summary: Enabled / Disabled |
| `ac3_supported` | AC3 supported | boolean | default: true. Summary: Enabled / Disabled |
| `direct_play_ass` | Direct play ASS | boolean | default: true. Summary: Enabled / Disabled |
| `direct_play_pgs` | Direct play PGS | boolean | default: true. Summary: Enabled / Disabled |
| `force_dovi_profile_7` | Force Dolby Vision profile 7 | boolean | default: false. Has custom summary |
| `software_decoding_av1` | AV1 software decoding | boolean | default: false. Summary: Enabled / Disabled |
| `max_bitrate` | Max bitrate | **integer (index)** | **See "Max bitrate" section below.** Stored as bits per second; UI uses index 0–22. Default index: 17 (100 Mbps). **Scope: global only.** |
| `refresh_rate_switching` | Refresh rate switching | boolean | default: false. Summary: Automatic / Disabled |
| `resolution_switching` | Resolution switching | boolean | default: false. Summary: Automatic / Disabled |
| `mpv_hardware_decoding` | MPV hardware decoding | boolean | default: true. Summary: Disable if crash |
| `mpv_use_gpu_next` | MPV use GPU Next | boolean | default: true. Summary: Enabled / Disabled |
| `mpv_conf` | MPV conf | action | Click-only |
| `advanced_settings` | Advanced settings | action | Navigate to advanced sub-screen |

---

## 2. User settings

### 2.1 Sign-in & home

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `seerr_login` | Sign-In (Seerr) | action | Click-only; opens Sign-In |
| `require_pin_code` | Require PIN for profile | action | Click-only |
| `max_homepage_items` | Max items on home page rows | integer | min: 5, max: 50, step: 1, default: 25 |
| `customize_nav_bar` | Customize nav bar | action | Click-only. Summary: nav drawer pins |
| `combine_continue_next` | Combine Continue & Next | boolean | default: false. Summary: Enabled / Disabled |
| `rewatch_next_up` | Rewatch next up | boolean | default: false. Summary: Enabled / Disabled |
| `backdrop_display` | Backdrop display | choice | Options (`backdrop_style_options`): "Image with dynamic color", "Image only", "None". Default index: 0 |

### 2.2 Appearance

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `play_theme_music` | Play theme music | choice | Options (`theme_song_volume`): "Disabled", "Lowest", "Low", "Medium", "High", "Full". Default index: 3 (Medium) |
| `remember_selected_tab` | Remember selected tab | boolean | default: true. Summary: Enabled / Disabled |
| `app_theme` | App theme (color) | choice | Options (`app_theme_colors`): "Purple", "Blue", "Green", "Orange", "Bold Blue", "Black". Default: app-defined |
| `show_clock` | Show clock | boolean | default: true. Summary: Enabled / Disabled |
| `combined_search_results` | Combined search results | boolean | default: false. Summary: On / Off (use `combined_search_results_on` / `combined_search_results_off`) |
| `nav_drawer_switch_on_focus` | Switch nav drawer pages on focus | boolean | default: true. Summary: Enabled / "Click to switch pages" |

### 2.3 Playback

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `skip_forward_preference` | Skip forward | integer | min: 10, max: 300, step: 5, default: 30. Unit: seconds |
| `skip_back_preference` | Skip back | integer | min: 5, max: 300, step: 5, default: 10. Unit: seconds |
| `skip_back_on_resume_preference` | Skip back when resuming playback | integer | min: 0, max: 10, step: 1, default: 0. 0 = Disabled; else seconds |
| `subtitle_style` | Subtitle style | **subsection** | **Opens "Subtitle style" group; see section 3.** |
| `hide_controller_timeout` | Hide playback controls | integer | min: 500, max: 15000, step: 100, default: 5000. Unit: milliseconds (display as seconds, e.g. "5.0 seconds") |
| `seek_bar_steps` | Seek bar steps | integer | min: 4, max: 64, step: 1, default: 16 |
| `playback_debug_info` | Show playback debug info | boolean | default: false. Summary: Show / Hide |
| `global_content_scale` | Global content scale | choice | Options (`content_scale`): "Fit", "None", "Crop", "Fill", "Fill Width", "Fill Height". Default index: 0 (Fit) |
| `one_click_pause` | Pause with one click | boolean | default: false. Summary: "Press D-Pad center to pause/play" / Disabled |

### 2.4 Next up & skip

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `auto_play_next` | Auto play next up | boolean | default: true. Summary: Enabled / Disabled |
| `auto_play_next_delay` | Delay before playing next up | integer | min: 0, max: 60, step: 5, default: 15. Unit: seconds; 0 = "Immediate" |
| `show_next_up_when` | Show next up | choice | Options (`show_next_up_when_options`): "At the end of playback", "During end credits/outro". Default index: 0 |
| `pass_out_protection` | Passout Protection | integer | min: 0, max: 3, step: 1, default: 2. Unit: hours; 0 = Disabled |
| `skip_intro_behavior` | Skip intro behavior | choice | Options (`skip_behaviors`): "Ignore", "Skip automatically", "Ask to skip". Default index: 1 (Ask to skip) |
| `skip_outro_behavior` | Skip outro behavior | choice | Same as skip_intro_behavior. Default index: 1 |
| `skip_commercials_behavior` | Skip commercials behavior | choice | Same. Default index: 1 |
| `skip_previews_behavior` | Skip previews behavior | choice | Same. Default index: 0 (Ignore) |
| `skip_recap_behavior` | Skip recap behavior | choice | Same. Default index: 0 (Ignore) |

### 2.5 About & more

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `installed_version` | Installed version | action | Click-only; read-only display |
| `check_for_updates` | Check for updates | action | Click-only |
| `license_info` | License info | action | Navigate to licenses |
| `send_app_logs` | Send app logs | boolean | Has summary; device-level storage may apply |
| `live_tv` | Live TV | action | Click-only. Summary: View options |

### 2.6 Live TV (user)

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `show_details` | Show details | boolean | default: true. Summary: Enabled / Disabled |
| `favorite_channels_at_beginning` | Favorite channels at beginning | boolean | default: true. Summary: Enabled / Disabled |
| `sort_channels_recently_watched` | Sort channels by recently watched | boolean | default: false. Summary: Enabled / Disabled |
| `color_code_programs` | Color code programs | boolean | default: true. Summary: Enabled / Disabled |

---

## 3. Subtitle style (subsection)

All subtitle settings are **user** scope. The plugin can expose a "Subtitle style" group that contains the following. Use the **key** as the stable id for API/state.

| Key | Display name | Type | Values / Notes |
|-----|--------------|------|----------------|
| `font_size` | Font size | integer | min: 8, max: 70, step: 2, default: 24 |
| `font_color` | Font color | choice | Options (`font_colors`): White, Black, Light Gray, Dark Gray, Red, Yellow, Green, Cyan, Blue, Magenta. Default index: 0 (White) |
| `bold_font` | Bold font | boolean | default: false |
| `italic_font` | Italicize font | boolean | default: false |
| `font_opacity` | Font opacity | integer | min: 10, max: 100, step: 10, default: 100. Unit: percent (display "n%") |
| `edge_style` | Edge style | choice | Options (`subtitle_edge`): "None", "Outline", "Shadow". Default index: 1 (Outline) |
| `edge_color` | Edge color | choice | Same options as `font_colors`. Default index: 1 (Black) |
| `edge_size` | Edge size | integer | min: 1, max: 32, step: 1, default: 4. Display value = raw value / 2.0 (e.g. "2.0") |
| `background_style` | Background style | choice | Options (`background_style`): "None", "Wrap", "Boxed". Default index: 0 (None) |
| `background_color` | Background color | choice | Same options as `font_colors`. Default: Transparent (index 0 or separate sentinel) |
| `background_opacity` | Background opacity | integer | min: 10, max: 100, step: 10, default: 50. Unit: percent |
| `subtitle_margin` | Margin | integer | min: 0, max: 100, step: 1, default: 8. Unit: percent |
| `reset` (subtitle) | Reset | action | Click-only; resets all subtitle style options above |

**Choice arrays (for subtitle style):**

- **font_colors:** White, Black, Light Gray, Dark Gray, Red, Yellow, Green, Cyan, Blue, Magenta
- **subtitle_edge:** None, Outline, Shadow
- **background_style:** None, Wrap, Boxed

---

## 4. Max bitrate (global only)

- **Key:** `max_bitrate`
- **Scope:** global (device-only)
- **Type:** Integer **index** from 0 to 22. Stored value in app is **bits per second (bps)**; plugin UI should use the index and the label column for display.
- **Default index:** 17 (100 Mbps).

| Index | bps (approx) | Label (display) |
|-------|----------------|------------------|
| 0 | 512 000 | 500 kbps |
| 1 | 768 000 | 750 kbps |
| 2 | 1 048 576 | 1 Mbps |
| 3 | 2 097 152 | 2 Mbps |
| 4 | 3 145 728 | 3 Mbps |
| 5 | 5 242 880 | 5 Mbps |
| 6 | 8 388 608 | 8 Mbps |
| 7 | 10 485 760 | 10 Mbps |
| 8 | 15 728 640 | 15 Mbps |
| 9 | 20 971 520 | 20 Mbps |
| 10 | 31 457 280 | 30 Mbps |
| 11 | 41 943 040 | 40 Mbps |
| 12 | 52 428 800 | 50 Mbps |
| 13 | 62 914 560 | 60 Mbps |
| 14 | 73 400 320 | 70 Mbps |
| 15 | 83 886 080 | 80 Mbps |
| 16 | 94 371 840 | 90 Mbps |
| 17 | 104 857 600 | 100 Mbps **(default)** |
| 18 | 125 829 120 | 120 Mbps |
| 19 | 146 800 640 | 140 Mbps |
| 20 | 167 772 160 | 160 Mbps |
| 21 | 188 743 680 | 180 Mbps |
| 22 | 209 715 200 | 200 Mbps |

If the plugin API uses bps, use the bps column; if it uses an index, use 0–22 with default 17.

---

## 5. Shared strings (for summaries / consistency)

- **Enabled** / **Disabled** – for boolean summary when not custom
- **Show** / **Hide** – for playback debug info
- **Automatic** – for refresh rate / resolution switching
- **Immediate** – for auto-play-next delay when value is 0
- **Seconds** – plural for duration (e.g. "30 seconds")
- **Hours** – plural for pass-out protection

---

## 6. Summary

- **Global settings:** Use section 1 and section 4 for `max_bitrate`.
- **User settings:** Use section 2 (and section 3 for the subtitle-style group). Sign-In (Seerr) is **user-level** (section 2.1).
- **Stable identifiers:** Use the **Key** column for API and state; use **Display name** for UI labels.
- **Subtitle style:** Implement the entire group in section 3 so the plugin can mirror the app's Subtitle style screen without reading app code.
- **Actions:** For type **action**, the plugin may show a button or navigate; no value to read/write beyond triggering the action.

This document is the single reference for building the plugin's settings section.
