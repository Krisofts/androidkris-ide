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

    // Termux's official bootstrap is built for its own app, so a good chunk of it (SYMLINKS.txt
    // entries, apt/dpkg's compiled-in Dir::* defaults, ~110 script shebangs) hardcodes this
    // absolute path at compile/package time instead of a relocatable one.
    const val TERMUX_HARDCODED_PREFIX = "/data/data/com.termux/files/usr"

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
    lateinit var aptConfig: File
        private set
    lateinit var caCertFile: File
        private set
    lateinit var interposeLib: File
        private set

    fun init(context: Context) {
        root = File(context.filesDir, "ide")
        prefix = File(root, "usr")
        home = File(root, "home")
        bin = File(prefix, "bin")
        lib = File(prefix, "lib")
        tmp = File(prefix, "tmp")
        bash = File(bin, "bash")
        aptConfig = File(prefix, "etc/apt/apt.conf.d/00androidkris-prefix.conf")
        caCertFile = File(prefix, "etc/tls/cert.pem")
        interposeLib = File(context.applicationInfo.nativeLibraryDir, "libinterpose.so")
    }

    /** True once the bootstrap is extracted and bash is executable. */
    val isInstalled: Boolean
        get() = this::bash.isInitialized && bash.canExecute()

    fun ensureRuntimeDirs() {
        home.mkdirs()
        tmp.mkdirs()
    }

    /**
     * Append-only, timestamped diagnostic trail for the install/launch sequence, written outside
     * [prefix] (a sibling of `ide/`, directly under `filesDir`) so it survives even a total wipe
     * of the prefix and stays readable from the Android system-shell fallback (same app UID) when
     * the prefix's own `bin/` is gone and nothing inside it can be exec'd. Exists to catch cases
     * where a freshly-extracted, verified-working prefix disappears moments later with no
     * destructive command run in between — e.g. some OEM ROMs' "storage cleaner"/"memory booster"
     * treating a burst of hundreds of small newly-written files in app-private storage as junk.
     * Retrievable even with a fully broken bootstrap via bash's own builtin command substitution
     * (no external `cat` needed) against the absolute path, or from the system-shell fallback
     * with plain `cat`.
     */
    fun logDiag(msg: String) {
        runCatching {
            val f = File(root.parentFile, "install.log")
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())
            f.appendText("[$ts] $msg\n")
        }
    }

    /**
     * Fixes up everything in an already-extracted prefix that still points at Termux's own
     * absolute prefix instead of ours (apt.conf + script shebangs/bodies). Idempotent and cheap
     * enough to run on every terminal launch — called from both [BootstrapInstaller] right
     * after a fresh extract and from [TerminalHost] before every session, so a fix here reaches
     * an already-installed prefix without re-downloading the 30MB bootstrap zip.
     */
    fun repairPrefix() {
        writeAptConfig()
        fixHardcodedScriptShebangs()
    }

    /**
     * ~110 files in the bootstrap are shell scripts (apt-key, termux-*, dpkg-buildapi, ...)
     * whose shebang — and sometimes body, e.g. apt-key's trusted.gpg path — is hardcoded to
     * Termux's own absolute prefix. The interpreter path doesn't exist in our sandbox (EACCES,
     * another app's data dir), so the script can't even be exec'd. Unlike compiled binaries
     * (fixed-width embedded strings, can't safely patch), scripts are plain text: peek the
     * first 2 bytes to skip binaries cheaply, then rewrite every occurrence in true scripts.
     */
    private fun fixHardcodedScriptShebangs() {
        prefix.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val head = runCatching {
                file.inputStream().use { val b = ByteArray(2); if (it.read(b) == 2) b else null }
            }.getOrNull() ?: return@forEach
            if (head[0] != '#'.code.toByte() || head[1] != '!'.code.toByte()) return@forEach
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
            if (!text.contains(TERMUX_HARDCODED_PREFIX)) return@forEach
            file.writeText(text.replace(TERMUX_HARDCODED_PREFIX, prefix.absolutePath), Charsets.UTF_8)
        }
    }

    /**
     * (Re)writes the apt.conf.d override that retargets apt/dpkg's compiled-in `Dir::*`
     * defaults (Termux's own absolute prefix) at ours.
     *
     * Deliberately does NOT set the top-level `Dir` (root/instdir) key: Termux's own packages
     * ship data.tar entries as paths *relative to "/"* (e.g. `./data/data/com.termux/files/usr/
     * lib/foo`), because Termux's own dpkg root really is "/". Pointing `Dir` at our prefix
     * made dpkg join that onto our prefix instead, producing a bogus nested
     * "<prefix>/data/data/com.termux/..." path (confirmed on device: "unable to stat
     * './data/data/com.termux'"). Leaving root at its natural "/" default makes dpkg construct
     * the real absolute path (`/data/data/com.termux/files/usr/lib/foo`), which interpose.c
     * *does* catch and redirect — that was the whole point of the syscall-level shim.
     */
    private fun writeAptConfig() {
        val p = prefix.absolutePath
        aptConfig.parentFile?.mkdirs()
        aptConfig.writeText(
            """
            Dir::Bin::Methods "$p/lib/apt/methods/";
            Dir::Bin::dpkg "$p/bin/dpkg";
            Dir::Bin::apt-key "$p/bin/apt-key";
            Dir::Etc "$p/etc/apt/";
            Dir::State "$p/var/lib/apt/";
            Dir::State::status "$p/var/lib/dpkg/status";
            Dir::Cache "$p/var/cache/apt/";
            Dir::Log "$p/var/log/apt/";
            Acquire::https::CAInfo "${caCertFile.absolutePath}";

            """.trimIndent()
        )
        File(prefix, "etc/dpkg").mkdirs()
        File(prefix, "var/lib/dpkg").mkdirs()
        File(prefix, "var/lib/apt/lists/partial").mkdirs()
        File(prefix, "var/cache/apt/archives/partial").mkdirs()
        File(prefix, "var/log/apt").mkdirs()
    }

    /** Environment for a login shell inside the prefix. */
    fun shellEnv(): Array<String> {
        val env = mutableListOf(
            "PREFIX=${prefix.absolutePath}",
            "HOME=${home.absolutePath}",
            "PATH=${bin.absolutePath}:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH=${lib.absolutePath}",
            "TMPDIR=${tmp.absolutePath}",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            // apt/dpkg compile in Dir::Etc pointing at Termux's own prefix, so they never look
            // in our etc/apt/apt.conf.d/ for the override written by writeAptConfig().
            // APT_CONFIG is read before any compiled-in default, bypassing that chicken-and-egg
            // problem.
            "APT_CONFIG=${aptConfig.absolutePath}",
            // GnuTLS's system-trust lookup is likewise hardcoded to Termux's own prefix and
            // finds nothing there (EACCES, it's another app's sandbox) — "No system
            // certificates available". SSL_CERT_FILE overrides that for GnuTLS/OpenSSL alike
            // (curl, apt's https method, anything else linked against either), pointed at the
            // real cert.pem the bootstrap already ships.
            "SSL_CERT_FILE=${caCertFile.absolutePath}",
        )
        // Some hardcoded Termux-prefix paths (e.g. dpkg's own config directory) have no
        // config-file or env-var override at all — Android also flatly denies traversing
        // another app's data dir (EACCES on the whole subtree), so there's nothing to create
        // our way around either. interpose.c rewrites those paths at the libc call level for
        // every process in this session; only enable it if the build actually produced the
        // .so, so a missing/failed native build degrades to "some things still don't work"
        // instead of every process failing to start.
        if (interposeLib.exists()) {
            env += "LD_PRELOAD=${interposeLib.absolutePath}"
            env += "ANDROIDKRIS_REAL_PREFIX=${prefix.absolutePath}"
        }
        return env.toTypedArray()
    }

    // Termux's own official bootstrap, aarch64 (bash + coreutils + apt/dpkg/curl/nano/...).
    // AndroidIDE (previously used here) was discontinued in Dec 2024 (org archived); Termux's
    // own bootstrap is still built weekly and is a superset (adds apt/dpkg, needed for Fase
    // 2.1b). A handful of apt/dpkg paths are hardcoded to Termux's own prefix at compile time
    // — see BootstrapInstaller's symlink retargeting + generated apt.conf override.
    const val BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.07.05-r1+apt.android-7/bootstrap-aarch64.zip"
    const val BOOTSTRAP_SHA256 =
        "e976289d117a94f6acc969096d8e3c01d54a53024416cd0279d84cac11458ab8"
}
