package com.androidkris.ide.terminal

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Minimal [TerminalSessionClient]: repaints the view on output/color changes,
 * reports shell exit, and swallows logging. Clipboard/bell are no-ops for now.
 */
internal class TerminalSessionClientImpl(
    private val onScreenUpdated: () -> Unit,
    private val onFinished: () -> Unit,
    private val onTitle: (String?) -> Unit = {},
) : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) = onScreenUpdated()
    override fun onTitleChanged(changedSession: TerminalSession) = onTitle(changedSession.title)
    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished()
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
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
