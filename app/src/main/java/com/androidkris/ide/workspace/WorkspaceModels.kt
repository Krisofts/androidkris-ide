package com.androidkris.ide.workspace

import com.androidkris.ide.editor.EditorLanguageType

/** A single visible row in the file tree (flattened, depth-indented). */
data class TreeNode(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val depth: Int,
    val expanded: Boolean,
)

/** An open editor tab (chrome only; the live buffer lives in the editor view). */
data class TabUi(
    val id: String,
    val name: String,
    val modified: Boolean,
)

/** Content + language handed to the editor when a tab becomes active. */
data class DocSnapshot(
    val content: String,
    val language: EditorLanguageType,
)

data class WorkspaceUiState(
    val projectName: String = "",
    val tree: List<TreeNode> = emptyList(),
    val tabs: List<TabUi> = emptyList(),
    val activeTabId: String? = null,
    val loading: Boolean = true,
)
