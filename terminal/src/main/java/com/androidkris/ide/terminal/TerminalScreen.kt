package com.androidkris.ide.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * A working terminal running the Android system shell (`/system/bin/sh`) via
 * Termux's [TerminalView]. Needs no Linux bootstrap — that (plus an arm64 JDK
 * and Gradle) arrives in Fase 2.1. Ships arm64 JNI only, matching the app ABI.
 */
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    shellPath: String = "/system/bin/sh",
    workingDir: String? = null,
    env: Array<String>? = null,
    args: Array<String>? = null,
    textSizeSp: Int = 14,
    onSessionEnd: () -> Unit = {},
) {
    // One-slot holder so the extra-keys row and onRelease can reach the session.
    val holder = remember { arrayOfNulls<TerminalSession>(1) }

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                val textPx = textSizeSp * ctx.resources.displayMetrics.density
                val view = TerminalView(ctx, null).apply {
                    setTextSize(textPx.toInt())
                    setTypeface(Typeface.MONOSPACE)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    // TerminalRenderer only paints non-default cell backgrounds; without an
                    // opaque view background the default (black) cells show the app's own
                    // Surface through them, so default-white text becomes invisible on it.
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
                view.setTerminalViewClient(
                    TerminalViewClientImpl(baseTextSizePx = textPx, onTap = { showKeyboard(ctx, view) })
                )

                val cwd = workingDir ?: ctx.filesDir.absolutePath
                val shellEnv = env ?: arrayOf(
                    "HOME=${ctx.filesDir.absolutePath}",
                    "PATH=/system/bin:/system/xbin",
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                )
                // argv is built entirely from args by the native layer; ensure argv[0] is set.
                val shellArgs = args ?: arrayOf(shellPath.substringAfterLast('/'))
                val session = TerminalSession(
                    shellPath, cwd, shellArgs, shellEnv, null,
                    TerminalSessionClientImpl(
                        onScreenUpdated = { view.onScreenUpdated() },
                        onFinished = { onSessionEnd() },
                    ),
                )
                holder[0] = session
                view.attachSession(session)
                view.requestFocus()
                showKeyboard(ctx, view)
                view
            },
            onRelease = {
                holder[0]?.finishIfRunning()
                holder[0] = null
            },
        )

        ExtraKeysRow(
            onKey = { seq ->
                holder[0]?.let { s -> val b = seq.toByteArray(); s.write(b, 0, b.size) }
            },
        )
    }
}

private fun showKeyboard(ctx: Context, view: TerminalView) {
    view.requestFocus()
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

@Composable
private fun ExtraKeysRow(onKey: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ExtraKeys.forEach { (label, seq) ->
            FilledTonalButton(
                onClick = { onKey(seq) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(label)
            }
        }
    }
}

// Control sequences built from code points (no literal control chars in source).
private val ESC = Char(27).toString()      // escape
private val ETX = Char(3).toString()       // Ctrl-C (interrupt)
private val EOT = Char(4).toString()       // Ctrl-D (EOF)

// Display label -> byte sequence written to the shell.
private val ExtraKeys = listOf(
    "ESC" to ESC,
    "TAB" to "\t",
    "^C" to ETX,
    "^D" to EOT,
    "←" to ESC + "[D",
    "↓" to ESC + "[B",
    "↑" to ESC + "[A",
    "→" to ESC + "[C",
    "/" to "/",
    "-" to "-",
    "|" to "|",
    "~" to "~",
)
