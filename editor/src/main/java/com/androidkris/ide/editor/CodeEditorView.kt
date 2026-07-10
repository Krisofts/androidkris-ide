package com.androidkris.ide.editor

import android.graphics.Typeface
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.subscribeAlways

/**
 * Compose host for the sora-editor [CodeEditor], with a [SymbolInputView] row
 * docked at the bottom (common coding symbols + tab). One instance is created
 * and reused for the whole workspace; [controller] rebinds documents per tab.
 *
 * Highlighting comes from TextMate grammars ([TextMateSetup]); the color scheme
 * follows [darkTheme] and updates reactively when the app theme flips.
 */
@Composable
fun CodeEditorView(
    controller: EditorController,
    darkTheme: Boolean,
    onModified: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextMateSetup.ensureInitialized(context)
            TextMateSetup.applyTheme(darkTheme)

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
            val editor = CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                setTextSize(14f)
                colorScheme = TextMateSetup.colorScheme()
                subscribeAlways<ContentChangeEvent> {
                    if (!controller.loading) onModified()
                }
            }
            controller.onAttach(editor)

            val symbolInput = SymbolInputView(context).apply {
                bindEditor(editor)
                addSymbols(SYMBOL_DISPLAY, SYMBOL_INSERT)
            }

            container.addView(editor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            container.addView(symbolInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            container
        },
        update = {
            controller.editor?.let { editor ->
                TextMateSetup.applyTheme(darkTheme)
                editor.colorScheme = TextMateSetup.colorScheme()
            }
        },
        onRelease = { controller.onDetach() },
    )
}

// Left column is the display glyph; right column is the inserted text (→ inserts a tab).
private val SYMBOL_DISPLAY = arrayOf(
    "→", "(", ")", "{", "}", "[", "]", "<", ">", ";", "=", "\"", "'",
    ":", ".", ",", "/", "\\", "|", "&", "!", "?", "_", "+", "-", "*",
)
private val SYMBOL_INSERT = arrayOf(
    "\t", "(", ")", "{", "}", "[", "]", "<", ">", ";", "=", "\"", "'",
    ":", ".", ",", "/", "\\", "|", "&", "!", "?", "_", "+", "-", "*",
)
