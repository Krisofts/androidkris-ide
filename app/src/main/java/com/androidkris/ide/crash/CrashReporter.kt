package com.androidkris.ide.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.androidkris.ide.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Global uncaught-exception handler. On any crash (any thread), it formats a
 * copyable report and launches [CrashActivity] in a separate `:crash` process
 * so the report survives the dying main process — instead of a bare "app closed".
 */
class CrashReporter(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(EXTRA_REPORT, buildReport(thread, throwable))
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("AndroidKris IDE — Crash Report")
        appendLine("Time: $time")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Thread: ${thread.name}")
        appendLine("--------")
        append(Log.getStackTraceString(throwable))
    }

    companion object {
        const val EXTRA_REPORT = "crash_report"

        fun install(context: Context) {
            val app = context.applicationContext
            Thread.setDefaultUncaughtExceptionHandler(
                CrashReporter(app, Thread.getDefaultUncaughtExceptionHandler())
            )
        }
    }
}
