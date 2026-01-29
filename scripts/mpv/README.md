# MPV build scripts

These scripts are adapted from https://github.com/mpv-android/mpv-android/tree/ae0d956c5a98ab8bf25af7e2c73bcb59e19c15b7/buildscripts licensed MIT.

## Prerequisites

- Android NDK (r29 or later recommended)
- Python 3 with pip
- Build tools: `meson`, `jsonschema`, `build-essential`, `autoconf`, `pkg-config`, `libtool`, `ninja-build`, `unzip`, `wget`, `nasm`

On macOS, install via Homebrew:
```bash
brew install meson ninja nasm
pip install jsonschema
```

On Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install -y build-essential autoconf pkg-config libtool ninja-build unzip wget meson nasm python3-pip
pip install jsonschema
```

## Build Instructions

### Step 1: Set up NDK path

```bash
export NDK_PATH=<path-to-your-ndk>
# Example on macOS:
# export NDK_PATH=~/Library/Android/sdk/ndk/29.0.14206865
# Example on Linux:
# export NDK_PATH=~/Android/Sdk/ndk/29.0.14206865
```

### Step 2: Get dependencies

```bash
cd scripts/mpv
./get_dependencies.sh
```

### Step 3: Install Python dependencies

```bash
pip install meson jsonschema
```

### Step 4: Build MPV libraries

**For macOS:**
```bash
# Build arm64 (64-bit ARM)
PATH="$PATH:$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin" ./buildall.sh --clean --arch arm64 mpv

# Build armeabi-v7a (32-bit ARM)
PATH="$PATH:$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin" ./buildall.sh mpv
```

**For Linux:**
```bash
# Build arm64 (64-bit ARM)
PATH="$PATH:$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin" ./buildall.sh --clean --arch arm64 mpv

# Build armeabi-v7a (32-bit ARM)
PATH="$PATH:$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin" ./buildall.sh mpv
```

### Step 5: Build JNI wrapper

```bash
cd ../..
env PREFIX32="$(realpath scripts/mpv/prefix/armv7l)" \
    PREFIX64="$(realpath scripts/mpv/prefix/arm64)" \
    "$NDK_PATH/ndk-build" -C app/src/main -j
```

### Step 6: Copy libraries to jniLibs

```bash
mkdir -p app/src/main/jniLibs
cp -fr app/src/main/libs/ app/src/main/jniLibs/
```

## Verification

After building, verify the libraries exist:
```bash
ls -la app/src/main/jniLibs/arm64-v8a/libmpv.so
ls -la app/src/main/jniLibs/armeabi-v7a/libmpv.so
```

Both `libmpv.so` and `libplayer.so` should be present in each ABI directory.

## Troubleshooting

- **Build fails**: Check that NDK_PATH is set correctly and the toolchain path matches your OS
- **Missing libraries**: Ensure all build steps completed successfully
- **Wrong architecture**: Verify you're building for the correct ABIs (arm64-v8a and/or armeabi-v7a)

For more information, see [DEVELOPMENT.md](../../DEVELOPMENT.md#mpv-player-backend).
