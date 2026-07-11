package com.androidkris.ide.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Minimal [TerminalSessionClient]: repaints the view on output/color changes,
 * reports shell exit, wires clipboard copy/paste to Android's system clipboard
 * (TerminalView's own long-press selection UI calls these), and swallows logging.
 */
internal class TerminalSessionClientImpl(
    private val context: Context,
    private val onScreenUpdated: () -> Unit,
    private val onFinished: () -> Unit,
    private val onTitle: (String?) -> Unit = {},
) : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) = onScreenUpdated()
    override fun onTitleChanged(changedSession: TerminalSession) = onTitle(changedSession.title)
    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished()

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (!text.isNullOrEmpty()) {
            val bytes = text.toByteArray()
            session?.write(bytes, 0, bytes.size)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) = onScreenUpdated()
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}
