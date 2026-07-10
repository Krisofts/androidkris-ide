package com.androidkris.ide.terminal

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Progress of the on-device Linux bootstrap installation. */
sealed interface BootstrapState {
    data object Idle : BootstrapState
    data class Downloading(val fraction: Float) : BootstrapState
    data object Verifying : BootstrapState
    data object Extracting : BootstrapState
    data object Done : BootstrapState
    data class Failed(val message: String) : BootstrapState
}

/**
 * Downloads AndroidIDE's prebuilt bionic bootstrap (bash + coreutils) and
 * extracts it into [Environment.prefix], replicating Termux's install:
 * stage → chmod 0700 each file → apply SYMLINKS.txt via `Os.symlink` → move
 * staging into place. All work runs on IO; failures surface via [BootstrapState.Failed].
 */
object BootstrapInstaller {

    private val SYMLINK_SEPARATOR = Char(0x2190) // '←' U+2190, Termux SYMLINKS.txt delimiter
    private const val MODE_0700 = 448            // "700".toInt(8)

    suspend fun install(context: Context, onProgress: (BootstrapState) -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                Environment.init(context)
                val zip = File(context.cacheDir, "bootstrap-aarch64.zip")
                download(Environment.BOOTSTRAP_URL, zip, onProgress)

                onProgress(BootstrapState.Verifying)
                verifySha256(zip, Environment.BOOTSTRAP_SHA256)

                onProgress(BootstrapState.Extracting)
                extract(zip)
                zip.delete()

                Environment.ensureRuntimeDirs()
                onProgress(BootstrapState.Done)
            } catch (t: Throwable) {
                runCatching { cleanup() }
                onProgress(BootstrapState.Failed("${t.javaClass.simpleName}: ${t.message}"))
            }
        }

    private fun download(url: String, out: File, onProgress: (BootstrapState) -> Unit) {
        onProgress(BootstrapState.Downloading(0f))
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw RuntimeException("Download gagal: HTTP ${conn.responseCode}")
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    fos.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress(BootstrapState.Downloading(read.toFloat() / total))
                }
            }
        }
        conn.disconnect()
    }

    private fun verifySha256(file: File, expected: String) {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw RuntimeException("SHA-256 tidak cocok (file rusak)")
        }
    }

    private fun extract(zip: File) {
        val staging = File(Environment.root, "usr-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        val symlinks = ArrayList<Pair<String, String>>(64)
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (entry.name == "SYMLINKS.txt") {
                    val reader = BufferedReader(InputStreamReader(zin))
                    var line = reader.readLine()
                    while (line != null) {
                        val parts = line.split(SYMLINK_SEPARATOR)
                        if (parts.size != 2) throw RuntimeException("SYMLINKS.txt rusak: $line")
                        val newPath = File(staging, parts[1])
                        newPath.parentFile?.mkdirs()
                        symlinks.add(parts[0] to newPath.absolutePath)
                        line = reader.readLine()
                    }
                } else {
                    val target = File(staging, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zin.copyTo(it) }
                        Os.chmod(target.absolutePath, MODE_0700)
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        if (symlinks.isEmpty()) throw RuntimeException("SYMLINKS.txt tidak ditemukan di bootstrap")
        for ((oldPath, newPath) in symlinks) Os.symlink(oldPath, newPath)

        val prefix = Environment.prefix
        if (prefix.exists()) prefix.deleteRecursively()
        prefix.parentFile?.mkdirs()
        if (!staging.renameTo(prefix)) throw RuntimeException("Gagal memindah staging ke prefix")
    }

    private fun cleanup() {
        File(Environment.root, "usr-staging").deleteRecursively()
    }
}
