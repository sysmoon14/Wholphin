# Wholphin developer's guide

See also the [Contributing](CONTRIBUTING.md) guide for general information on contributing to the project.

##  Overview

This project is an Android TV client for Jellyfin. It is written in Kotlin and uses the official [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin) to interact with the server.

The app is a single Activity (`MainActivity`) with MVVM architecture.

The app uses:
* [Compose](https://developer.android.com/jetpack/compose) for the UI
* [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) for navigating app screen
* [Room](https://developer.android.com/training/data-storage/room) & [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for local data storage
* [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) for dependency injection
* [Media3/ExoPlayer](https://developer.android.com/media/media3/exoplayer) for media playback
* [Coil](https://coil-kt.github.io/coil/) for image loading
* [OkHttp](https://square.github.io/okhttp/) for HTTP requests

## Getting started

We follow GitHub's fork & pull request model for contributions.

After forking and cloning your fork, you can import the project into Android Studio.

### Development environment

It is recommended to use a recent version of [Android Studio](https://developer.android.com/studio). Make sure the [version is compatible](https://developer.android.com/build/releases/gradle-plugin#android_gradle_plugin_and_android_studio_compatibility) with Wholphin's AGP version.

Code formatting should follow [ktlint's](https://github.com/pinterest/ktlint) rules. Find the `ktlint` version in [`.pre-commit-config.yaml`](./.pre-commit-config.yaml). Optionally, install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint) in Android Studio to run automatically. Configure the version in `Settings->Tools->KtLint->Ruleset Version`.

Also, it's recommend to add an extra ruleset jar for Compose-specific KtLint: https://mrmans0n.github.io/compose-rules/ktlint/#using-with-ktlint-cli-or-the-ktlint-intellij-plugin

Also setup [pre-commit](https://github.com/pre-commit/pre-commit) which will run `ktlint` as well on each commit, plus check for other common issues.

## Code organization

Code is split into several packages:
- `data` - app-specific data models and services
- `preferences` - Non-UI related code for user settings and preferences
- `services` - hilt injectable services often used by ViewModels for API calls
- `ui` - User interface code and ViewModels
- `util` - Utility classes and functions

### Native components

#### FFmpeg decoder module

Wholphin ships with [media3 ffmpeg decoder module](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md).

It is not required to build the extension in order to build the app locally.

You can build the module on MacOS or Linux with the [`build_ffmpeg_decoder.sh`](./scripts/ffmpeg/build_ffmpeg_decoder.sh) script.

#### MPV player backend

Wholphin has a playback engine that uses [`libmpv`](https://github.com/mpv-player/mpv). The app uses JNI code from [`mpv-android`](https://github.com/mpv-android/mpv-android) and has an implementation of `androidx.media3.common.Player` to swap out for `ExoPlayer`.

**Building MPV native libraries**

The MPV player backend requires native libraries (`libmpv.so`, `libplayer.so`, and FFmpeg shared libs such as `libavcodec.so`) to be built and included in the APK. These are not committed (see `.gitignore`) and must be built locally or produced by CI.

**GitHub Actions (CI):** PR and release workflows (`.github/workflows/pr.yml`, `.github/workflows/release.yml`) run the **Native build** action (`.github/actions/native-build`), which builds MPV for arm64-v8a and armeabi-v7a, runs `ndk-build`, and copies `app/src/main/libs/*` into `app/src/main/jniLibs/`. The result is cached by script hash, so later runs reuse the cache unless MPV or JNI scripts change. No extra setup is needed for builds on GitHub.

**Prerequisites:**
- Android NDK (tested with NDK r29+)
- Python 3 with `pip`
- Build tools: `meson`, `jsonschema`, `build-essential`, `autoconf`, `pkg-config`, `libtool`, `ninja-build`, `unzip`, `wget`, `nasm`

**Build steps:**

1. **Install Python dependencies:**
   ```bash
   pip install meson jsonschema
   ```

2. **Set up NDK path:**
   ```bash
   export NDK_PATH=<path-to-your-ndk>
   # Example: export NDK_PATH=~/Library/Android/sdk/ndk/29.0.14206865
   ```

3. **Get MPV dependencies:**
   ```bash
   cd scripts/mpv
   ./get_dependencies.sh
   ```

4. **Build MPV for arm64 (64-bit ARM):**
   ```bash
   PATH="$PATH:$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin" ./buildall.sh --clean --arch arm64 mpv
   ```
   Note: On Linux, use `linux-x86_64` instead of `darwin-x86_64`.

5. **Build MPV for armeabi-v7a (32-bit ARM):**
   ```bash
   PATH="$PATH:$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin" ./buildall.sh mpv
   ```

6. **Build JNI wrapper libraries:**
   ```bash
   cd ../..
   env PREFIX32="$(realpath scripts/mpv/prefix/armv7l)" \
       PREFIX64="$(realpath scripts/mpv/prefix/arm64)" \
       "$NDK_PATH/ndk-build" -C app/src/main -j
   ```

7. **Copy libraries to jniLibs directory:**
   ```bash
   cp -fr app/src/main/libs/ app/src/main/jniLibs/
   ```

**Expected directory structure after building:**
```
app/src/main/
├── jniLibs/           (packaged into APK; gitignored)
│   ├── arm64-v8a/
│   │   ├── libavcodec.so, libavformat.so, ... (FFmpeg)
│   │   ├── libmpv.so
│   │   ├── libplayer.so
│   │   └── libc++_shared.so
│   └── armeabi-v7a/
│       └── (same .so files)
└── libs/              (intermediate; ndk-build output; gitignored)
    └── arm64-v8a/, armeabi-v7a/
```
Copy the full `libs/` tree into `jniLibs/` so all dependencies (e.g. `libavcodec.so`) are included; otherwise the app may fail at runtime with "library not found".

**Verification:**
- The build system will warn you if MPV libraries are missing (see `validateMpvLibs` task)
- You can verify libraries are included by checking the APK: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libmpv.so`
- If libraries are not available, the app will automatically fall back to ExoPlayer

**Troubleshooting:**
- **Build fails with "command not found"**: Make sure the NDK toolchain is in your PATH
- **Libraries not found at runtime**: Verify `jniLibs/` directory exists and contains the `.so` files
- **Wrong architecture**: Ensure you're building for the correct ABIs (arm64-v8a and/or armeabi-v7a)
- **Build takes a long time**: This is normal - MPV and its dependencies are large projects

See the [MPV build scripts README](scripts/mpv/README.md) for more details.

### App settings

App settings are available with the `AppPreferences` object and defined by different `AppPreference` objects (note the `s` differences).

The `AppPreference` objects are used to create the UI for configuring settings using the composable functions in `com.github.damontecres.wholphin.ui.preferences`.

#### How to add a new app setting

1. Add entry in `WholphinDataStore.proto` & build to generate classes
2. Add new `AppPreference` object in `AppPreference.kt`
3. Add new object to a `PreferenceGroup` (listed in `AppPreference.kt`)
4. Update `AppPreferencesSerializer` to set the default value for new installs
5. If needed, update `AppUpgradeHandler` to set the default value for app upgrades
    - Since preferences use proto3, the [default values](https://protobuf.dev/programming-guides/proto3/#default) are zero, false, or the first enum, so only need this step if the default value is different
