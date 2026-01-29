package com.github.sysmoon.wholphin.util

import android.os.Build

/**
 * Returns true if the app is running on an Android emulator.
 * Used to disable MPV hardware decoding on emulators, where EGL/MediaCodec
 * often fail (e.g. EGL_BAD_ATTRIBUTE, Codec2 setOutputSurface BAD_INDEX),
 * causing playback to never reach FILE_LOADED and the UI to spin indefinitely.
 */
fun isRunningOnEmulator(): Boolean {
    if (Build.FINGERPRINT != null) {
        if (
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("vbox") ||
            Build.FINGERPRINT.contains("test-keys") ||
            Build.FINGERPRINT.contains("sdk_gphone") ||
            Build.FINGERPRINT.contains("sdk_google")
        ) {
            return true
        }
    }
    if (Build.HARDWARE != null) {
        if (
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("vbox") ||
            Build.HARDWARE.contains("vexpress") ||
            Build.HARDWARE.contains("virtio")
        ) {
            return true
        }
    }
    if (Build.PRODUCT != null) {
        if (
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.PRODUCT.contains("sdk_google")
        ) {
            return true
        }
    }
    return false
}
