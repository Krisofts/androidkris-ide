package com.androidkris.ide.terminal

import android.content.Context
import java.io.File

/**
 * Filesystem layout + shell environment for the on-device Linux prefix
 * (Fase 2.1), mirroring AndroidIDE/Termux: `$PREFIX = <files>/ide/usr`,
 * `$HOME = <files>/ide/home`. The bootstrap (bash + coreutils, compiled for
 * Android/bionic) is downloaded and extracted here by [BootstrapInstaller].
 *
 * Executing binaries from here requires `targetSdk = 28` (see app build.gradle).
 */
object Environment {

    lateinit var root: File
        private set
    lateinit var prefix: File
        private set
    lateinit var home: File
        private set
    lateinit var bin: File
        private set
    lateinit var lib: File
        private set
    lateinit var tmp: File
        private set
    lateinit var bash: File
        private set

    fun init(context: Context) {
        root = File(context.filesDir, "ide")
        prefix = File(root, "usr")
        home = File(root, "home")
        bin = File(prefix, "bin")
        lib = File(prefix, "lib")
        tmp = File(prefix, "tmp")
        bash = File(bin, "bash")
    }

    /** True once the bootstrap is extracted and bash is executable. */
    val isInstalled: Boolean
        get() = this::bash.isInitialized && bash.canExecute()

    fun ensureRuntimeDirs() {
        home.mkdirs()
        tmp.mkdirs()
    }

    /** Environment for a login shell inside the prefix. */
    fun shellEnv(): Array<String> = arrayOf(
        "PREFIX=${prefix.absolutePath}",
        "HOME=${home.absolutePath}",
        "PATH=${bin.absolutePath}:/system/bin:/system/xbin",
        "LD_LIBRARY_PATH=${lib.absolutePath}",
        "TMPDIR=${tmp.absolutePath}",
        "TERM=xterm-256color",
        "LANG=en_US.UTF-8",
    )

    // AndroidIDE's prebuilt bionic bootstrap (bash + coreutils), aarch64.
    const val BOOTSTRAP_URL =
        "https://github.com/AndroidIDEOfficial/terminal-packages/releases/download/bootstrap-16.12.2023/bootstrap-aarch64.zip"
    const val BOOTSTRAP_SHA256 =
        "68da03ed270d59cafcd37981b00583c713b42cb440adf03d1bf980f39a55181d"
}
