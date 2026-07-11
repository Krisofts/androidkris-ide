package com.androidkris.ide.terminal

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Progress of the on-device Gradle install. Mirrors [BootstrapState]'s shape but kept separate
 *  since it reports on an unrelated install (see [GradleInstaller]). */
sealed interface GradleState {
    data object Idle : GradleState
    data class Downloading(val fraction: Float) : GradleState
    data object Verifying : GradleState
    data object Extracting : GradleState
    data object Done : GradleState
    data class Failed(val message: String) : GradleState
}

/**
 * Downloads Gradle's official upstream binary distribution and extracts it into
 * [Environment.gradleHome], independent of the apt/dpkg prefix entirely.
 *
 * Unlike bash/coreutils/JDK, Gradle itself is pure JVM bytecode plus a launcher shell script —
 * no native/aarch64-specific build exists or is needed, so there's no reason to route it through
 * Termux's `pkg install gradle` (which pins its own JDK version as a dependency, per MASTERPLAN).
 * This downloads the exact same zip `gradle-wrapper.properties` files everywhere point at
 * (services.gradle.org), the same way any desktop install works — the only Android-specific part
 * is making the `bin/gradle` launcher script executable after extraction.
 */
object GradleInstaller {

    private const val MODE_0700 = 448 // "700".toInt(8)
    private val installLock = Mutex()

    // Gradle's release zip is pure Java + a shell launcher: no aarch64/ARM-specific build exists,
    // this is the exact same artifact `gradle-wrapper.properties` resolves on any desktop OS.
    // SHA-256 cross-checked against gradle.org/release-checksums/ (Binary-only ZIP Checksum).
    const val GRADLE_URL = "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"
    const val GRADLE_SHA256 =
        "31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26"

    /** Mirrors [BootstrapInstaller.hasValidCache] — lets a wiped-out Gradle install (see the
     *  OEM-cleanup notes on [BootstrapInstaller]) self-heal in seconds instead of a re-download. */
    suspend fun hasValidCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        Environment.init(context)
        val zip = Environment.gradleCachedZip
        zip.exists() && runCatching { Downloader.verifySha256(zip, GRADLE_SHA256) }.isSuccess
    }

    suspend fun install(context: Context, onProgress: (GradleState) -> Unit) =
        withContext(Dispatchers.IO) {
            if (!installLock.tryLock()) {
                onProgress(GradleState.Failed("Instalasi Gradle lain sedang berjalan, tunggu sampai selesai"))
                return@withContext
            }
            try {
                Environment.init(context)
                Environment.logDiag("gradle install() start")
                val zip = Environment.gradleCachedZip
                val cached = zip.exists() && runCatching { Downloader.verifySha256(zip, GRADLE_SHA256) }.isSuccess
                if (cached) {
                    Environment.logDiag("gradle: reusing cached+verified zip, size=${zip.length()} (skip download)")
                    onProgress(GradleState.Verifying)
                } else {
                    onProgress(GradleState.Downloading(0f))
                    Downloader.download(GRADLE_URL, zip) { onProgress(GradleState.Downloading(it)) }
                    Environment.logDiag("gradle: download done, size=${zip.length()}")

                    onProgress(GradleState.Verifying)
                    runCatching { Downloader.verifySha256(zip, GRADLE_SHA256) }
                        .onFailure { zip.delete(); throw it }
                    Environment.logDiag("gradle: sha256 verified")
                }

                onProgress(GradleState.Extracting)
                extract(zip)

                val gradleOk = Environment.gradleBin.canExecute()
                Environment.logDiag("gradle: extract done, gradleBinExecutable=$gradleOk")
                if (!gradleOk) {
                    throw RuntimeException("Verifikasi gagal: ${Environment.gradleBin} tidak ada/tidak bisa dieksekusi setelah extract")
                }

                Environment.logDiag("gradle: Done reported to UI")
                onProgress(GradleState.Done)
            } catch (t: Throwable) {
                Environment.logDiag("gradle: FAILED: ${t.javaClass.simpleName}: ${t.message}")
                runCatching { File(Environment.root, "gradle-staging").deleteRecursively() }
                onProgress(GradleState.Failed("${t.javaClass.simpleName}: ${t.message}"))
            } finally {
                installLock.unlock()
            }
        }

    private fun extract(zip: File) {
        val staging = File(Environment.root, "gradle-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        // The zip's entries are all prefixed with a top-level "gradle-<version>/" directory;
        // strip it so gradleHome directly contains bin/, lib/, etc.
        var stripPrefix: String? = null
        val writtenFiles = ArrayList<String>(2000)
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (stripPrefix == null) {
                    val slash = name.indexOf('/')
                    if (slash < 0) throw RuntimeException("Struktur zip Gradle tidak dikenali: $name")
                    stripPrefix = name.substring(0, slash + 1)
                }
                val relative = name.removePrefix(stripPrefix!!)
                if (relative.isNotEmpty()) {
                    val target = File(staging, relative)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zin.copyTo(it) }
                        writtenFiles.add(target.absolutePath)
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        // Only the launcher script needs to be executable; everything else (jars, docs) is
        // read-only. Batched after all writes for the same reason as BootstrapInstaller.extract().
        val launcher = File(staging, "bin/gradle")
        if (!launcher.exists()) throw RuntimeException("bin/gradle tidak ditemukan setelah extract")
        Os.chmod(launcher.absolutePath, MODE_0700)

        val home = Environment.gradleHome
        if (home.exists()) home.deleteRecursively()
        home.parentFile?.mkdirs()
        if (!staging.renameTo(home)) throw RuntimeException("Gagal memindah staging Gradle ke tujuan")
    }
}
