package com.androidkris.ide.terminal

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Plain HTTPS download + SHA-256 verification, shared by [BootstrapInstaller] and [GradleInstaller]. */
object Downloader {

    fun download(url: String, out: File, onProgress: (Float) -> Unit) {
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
                    if (total > 0) onProgress(read.toFloat() / total)
                }
            }
        }
        conn.disconnect()
    }

    /** Throws if [file]'s SHA-256 doesn't match [expected] (case-insensitive hex). */
    fun verifySha256(file: File, expected: String) {
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
}
