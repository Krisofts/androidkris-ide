package com.androidkris.ide.buildengine

import android.os.Build

/**
 * Detects whether this device can host the on-device Android build toolchain.
 *
 * The Gradle / AAPT2 / d8 / JDK binaries shipped by AndroidIDE-style IDEs are
 * compiled **only** for `arm64-v8a`. An x86/x86_64 emulator or a 32-bit device
 * can run the editor and terminal, but cannot build APKs. Fase 4 (build & install)
 * must gate on [canBuild] before spawning a Gradle daemon.
 */
object DeviceAbi {

    const val ARM64: String = "arm64-v8a"

    /** ABIs this device supports, most-preferred first (e.g. `[arm64-v8a, armeabi-v7a]`). */
    val supportedAbis: List<String>
        get() = Build.SUPPORTED_ABIS.toList()

    /** The primary ABI Android will prefer when loading native code. */
    val primaryAbi: String
        get() = supportedAbis.firstOrNull() ?: "unknown"

    /** True when the on-device build toolchain can run here (arm64 present). */
    val canBuild: Boolean
        get() = supportedAbis.contains(ARM64)

    /** A short human-readable capability summary for the Home screen. */
    fun capability(): BuildCapability =
        if (canBuild) BuildCapability.Supported(primaryAbi)
        else BuildCapability.Unsupported(primaryAbi)
}

sealed interface BuildCapability {
    val abi: String

    /** Device is arm64 — full on-device APK builds are possible. */
    data class Supported(override val abi: String) : BuildCapability

    /** Editor/terminal work, but this ABI can't host the build toolchain. */
    data class Unsupported(override val abi: String) : BuildCapability
}
