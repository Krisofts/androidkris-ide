package com.androidkris.ide.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Imperative handle over the single reused [CodeEditor] instance.
 *
 * The workspace owns one controller and rebinds it as the active tab changes.
 * The Compose layer coordinates buffer hand-off (read [getText] before switching
 * tabs, [load] the incoming document), so the editor view itself is stateless
 * across tabs. All calls are no-ops until the view attaches the editor.
 */
class EditorController {

    internal var editor: CodeEditor? = null

    /** True while [load] is replacing the buffer, so we can ignore the resulting change event. */
    internal var loading: Boolean = false

    private val _attached = MutableStateFlow(false)

    /** Emits true once the editor view is created and bound; the screen waits on this before loading. */
    val attached: StateFlow<Boolean> = _attached.asStateFlow()

    internal fun onAttach(ed: CodeEditor) {
        editor = ed
        _attached.value = true
    }

    internal fun onDetach() {
        editor?.release()
        editor = null
        _attached.value = false
    }

    fun load(text: String, type: EditorLanguageType) {
        val ed = editor ?: return
        loading = true
        ed.setText(text)
        ed.setEditorLanguage(type.create())
        loading = false
    }

    fun getText(): String = editor?.text?.toString() ?: ""

    fun undo() { editor?.undo() }
    fun redo() { editor?.redo() }
    fun canUndo(): Boolean = editor?.canUndo() ?: false
    fun canRedo(): Boolean = editor?.canRedo() ?: false

    // --- Find / replace -----------------------------------------------------

    fun search(query: String, caseInsensitive: Boolean = true, regex: Boolean = false) {
        val ed = editor ?: return
        if (query.isEmpty()) {
            ed.searcher.stopSearch()
            return
        }
        ed.searcher.search(query, SearchOptions(caseInsensitive, regex))
    }

    fun findNext() { editor?.searcher?.gotoNext() }
    fun findPrevious() { editor?.searcher?.gotoPrevious() }
    fun stopSearch() { editor?.searcher?.stopSearch() }

    fun replaceAll(replacement: String) {
        editor?.searcher?.takeIf { it.hasQuery() }?.replaceAll(replacement)
    }
}

@Composable
fun rememberEditorController(): EditorController = remember { EditorController() }
