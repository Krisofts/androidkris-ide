package com.androidkris.ide.terminal

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/** Progress of the Ubuntu rootfs install. Mirrors [BootstrapState]/[GradleState]'s shape. */
sealed interface UbuntuState {
    data object Idle : UbuntuState
    data class Downloading(val fraction: Float) : UbuntuState
    data object Verifying : UbuntuState
    data object Extracting : UbuntuState
    data object Done : UbuntuState
    data class Failed(val message: String) : UbuntuState
}

/**
 * Downloads Canonical's official Ubuntu Base rootfs (arm64) and extracts it into
 * [Environment.ubuntuRootfs] — a genuine, unmodified Debian/Ubuntu filesystem, entered later via
 * `proot` (see [Environment.prootEnterCommand]). Deliberately independent of [BootstrapInstaller]:
 * this doesn't touch the bionic prefix at all, it's a second, self-contained filesystem tree that
 * proot presents as "/" to whatever runs inside it — no Termux-hardcoded-path bugs are possible
 * here because nothing in this tarball was ever built assuming a specific Android app's data
 * directory. `proot` itself is apt-installed into the existing bionic prefix (reuses the
 * already-hardened apt/dpkg pipeline) rather than sourced as a separately-trusted native binary.
 */
object UbuntuInstaller {

    private val installLock = Mutex()

    // Canonical's official minimal rootfs, downloaded and SHA-256-verified directly against
    // cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/SHA256SUMS (not summarized by any
    // intermediate tool — fetched and hashed byte-for-byte).
    const val UBUNTU_URL =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz"
    const val UBUNTU_SHA256 =
        "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"

    suspend fun hasValidCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        Environment.init(context)
        val tar = Environment.ubuntuCachedTar
        tar.exists() && runCatching { Downloader.verifySha256(tar, UBUNTU_SHA256) }.isSuccess
    }

    suspend fun install(context: Context, onProgress: (UbuntuState) -> Unit) =
        withContext(Dispatchers.IO) {
            if (!installLock.tryLock()) {
                onProgress(UbuntuState.Failed("Instalasi Ubuntu lain sedang berjalan, tunggu sampai selesai"))
                return@withContext
            }
            try {
                Environment.init(context)
                Environment.logDiag("ubuntu install() start")
                val tar = Environment.ubuntuCachedTar
                val cached = tar.exists() && runCatching { Downloader.verifySha256(tar, UBUNTU_SHA256) }.isSuccess
                if (cached) {
                    Environment.logDiag("ubuntu: reusing cached+verified tarball, size=${tar.length()} (skip download)")
                    onProgress(UbuntuState.Verifying)
                } else {
                    onProgress(UbuntuState.Downloading(0f))
                    Downloader.download(UBUNTU_URL, tar) { onProgress(UbuntuState.Downloading(it)) }
                    Environment.logDiag("ubuntu: download done, size=${tar.length()}")

                    onProgress(UbuntuState.Verifying)
                    runCatching { Downloader.verifySha256(tar, UBUNTU_SHA256) }
                        .onFailure { tar.delete(); throw it }
                    Environment.logDiag("ubuntu: sha256 verified")
                }

                onProgress(UbuntuState.Extracting)
                extract(tar)
                writeNetworkConfig()

                val ok = File(Environment.ubuntuRootfs, "etc/os-release").exists()
                Environment.logDiag("ubuntu: extract done, os-release exists=$ok")
                if (!ok) {
                    throw RuntimeException("Verifikasi gagal: /etc/os-release tidak ada setelah extract")
                }

                Environment.logDiag("ubuntu: Done reported to UI")
                onProgress(UbuntuState.Done)
            } catch (t: Throwable) {
                Environment.logDiag("ubuntu: FAILED: ${t.javaClass.simpleName}: ${t.message}")
                runCatching { File(Environment.root, "ubuntu-staging").deleteRecursively() }
                onProgress(UbuntuState.Failed("${t.javaClass.simpleName}: ${t.message}"))
            } finally {
                installLock.unlock()
            }
        }

    private fun extract(tar: File) {
        val staging = File(Environment.root, "ubuntu-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        // (path, mode) pairs for a batched chmod pass at the end — same OEM-cleanup-heuristic
        // mitigation as BootstrapInstaller.extract() and GradleInstaller.extract(), doubly
        // important here since a full rootfs is thousands of files, not hundreds.
        val pendingModes = ArrayList<Pair<String, Int>>(4000)
        // Hardlink entries whose target wasn't extracted yet when encountered (tar doesn't
        // guarantee ordering) — resolved in a second pass once every regular file exists.
        // (destination path, link target name, mode) — mode is applied via pendingModes either way.
        val pendingHardlinks = ArrayList<Triple<String, String, Int>>(64)

        TarArchiveInputStream(GZIPInputStream(tar.inputStream().buffered(), 64 * 1024)).use { tin ->
            var entry: TarArchiveEntry? = tin.nextTarEntry
            while (entry != null) {
                val target = File(staging, entry.name)
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        runCatching { Os.symlink(entry.linkName, target.absolutePath) }
                    }
                    entry.isLink -> {
                        // Hard link. Android filesystems don't reliably support real hardlinks
                        // (this is exactly what proot's --link2symlink works around at runtime);
                        // here at extract time, just copy the already-extracted target's bytes.
                        target.parentFile?.mkdirs()
                        val linkedFile = File(staging, entry.linkName)
                        if (linkedFile.exists()) {
                            linkedFile.copyTo(target, overwrite = true)
                        } else {
                            pendingHardlinks.add(Triple(target.absolutePath, entry.linkName, entry.mode))
                        }
                        pendingModes.add(target.absolutePath to entry.mode)
                    }
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { tin.copyTo(it) }
                        pendingModes.add(target.absolutePath to entry.mode)
                    }
                    // Device nodes, FIFOs etc: not needed for a proot userland (proot binds the
                    // real /dev over it anyway) — skip rather than fail on an unprivileged mknod.
                }
                entry = tin.nextTarEntry
            }
        }

        for ((path, linkName, _) in pendingHardlinks) {
            val linkedFile = File(staging, linkName)
            if (linkedFile.exists()) linkedFile.copyTo(File(path), overwrite = true)
        }

        for ((path, mode) in pendingModes) {
            runCatching { Os.chmod(path, mode and 0xFFF) } // strip any non-permission bits
        }

        val dest = Environment.ubuntuRootfs
        if (dest.exists()) dest.deleteRecursively()
        dest.parentFile?.mkdirs()
        if (!staging.renameTo(dest)) throw RuntimeException("Gagal memindah staging Ubuntu ke tujuan")
    }

    /**
     * Ubuntu Base ships no usable `/etc/resolv.conf` (either absent or a systemd-resolved stub
     * symlink that resolves to nothing inside proot) — without this, DNS lookups inside the
     * container fail and `apt update` can't reach any mirror.
     */
    private fun writeNetworkConfig() {
        val etc = File(Environment.ubuntuRootfs, "etc")
        File(etc, "resolv.conf").apply { delete() }.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        File(etc, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n127.0.1.1 androidkris\n")
        File(etc, "hostname").writeText("androidkris\n")
    }
}
