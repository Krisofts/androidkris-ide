package com.androidkris.ide.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidkris.ide.editor.EditorLanguageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Holds workspace state: the project tree, open tabs, and each open document's
 * in-memory buffer. The editor view is a single reused surface — this VM never
 * touches it; the screen mediates buffer hand-off (see [snapshot] / [updateContent]).
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val repo: FileRepository,
) : ViewModel() {

    private class OpenDoc(
        val file: File,
        var content: String,
        val language: EditorLanguageType,
        var modified: Boolean = false,
    )

    private lateinit var root: File
    private val openDocs = LinkedHashMap<String, OpenDoc>()
    private val expanded = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            root = withContext(Dispatchers.IO) { repo.ensureSampleProject() }
            expanded += root.absolutePath
            _uiState.update { it.copy(projectName = root.name, loading = false) }
            refreshTree()
        }
    }

    // --- Tree ---------------------------------------------------------------

    /** Switch the workspace to an arbitrary folder (from the folder picker). */
    fun setProjectRoot(path: String) {
        root = File(path)
        openDocs.clear()
        expanded.clear()
        expanded += root.absolutePath
        _uiState.update {
            it.copy(projectName = root.name, tabs = emptyList(), activeTabId = null)
        }
        refreshTree()
    }

    fun toggleDir(path: String) {
        if (!expanded.add(path)) expanded.remove(path)
        refreshTree()
    }

    private fun refreshTree() {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) { buildTree() }
            _uiState.update { it.copy(tree = nodes) }
        }
    }

    private fun buildTree(): List<TreeNode> {
        val out = ArrayList<TreeNode>()
        fun walk(dir: File, depth: Int) {
            for (f in repo.listChildren(dir)) {
                val isDir = f.isDirectory
                val exp = expanded.contains(f.absolutePath)
                out.add(TreeNode(f.absolutePath, f.name, isDir, depth, exp))
                if (isDir && exp) walk(f, depth + 1)
            }
        }
        if (::root.isInitialized) walk(root, 0)
        return out
    }

    // --- Tabs / documents ---------------------------------------------------

    fun openFile(node: TreeNode) {
        if (node.isDir) {
            toggleDir(node.path)
            return
        }
        val existing = openDocs[node.path]
        if (existing != null) {
            _uiState.update { it.copy(activeTabId = node.path) }
            return
        }
        viewModelScope.launch {
            val file = File(node.path)
            val text = withContext(Dispatchers.IO) { runCatching { repo.readText(file) }.getOrDefault("") }
            openDocs[node.path] = OpenDoc(file, text, EditorLanguageType.fromFileName(node.name))
            _uiState.update { it.copy(activeTabId = node.path) }
            refreshTabs()
        }
    }

    fun selectTab(id: String) {
        if (openDocs.containsKey(id)) _uiState.update { it.copy(activeTabId = id) }
    }

    /** Content + language for the tab the editor is about to display. */
    fun snapshot(id: String?): DocSnapshot? {
        val doc = openDocs[id] ?: return null
        return DocSnapshot(doc.content, doc.language)
    }

    /** Store the live editor buffer back into a tab (call before switching/saving). */
    fun updateContent(id: String?, text: String) {
        openDocs[id]?.content = text
    }

    fun markModified(id: String?) {
        val doc = openDocs[id] ?: return
        if (!doc.modified) {
            doc.modified = true
            refreshTabs()
        }
    }

    fun save(id: String?, text: String) {
        val doc = openDocs[id] ?: return
        doc.content = text
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.writeText(doc.file, text) }
            doc.modified = false
            refreshTabs()
        }
    }

    fun closeTab(id: String) {
        val order = openDocs.keys.toList()
        val idx = order.indexOf(id)
        openDocs.remove(id)
        val newActive = if (_uiState.value.activeTabId == id) {
            order.getOrNull(idx + 1) ?: order.getOrNull(idx - 1)
        } else {
            _uiState.value.activeTabId
        }?.takeIf { openDocs.containsKey(it) }
        _uiState.update { it.copy(activeTabId = newActive) }
        refreshTabs()
    }

    private fun refreshTabs() {
        val tabs = openDocs.values.map { TabUi(it.file.absolutePath, it.file.name, it.modified) }
        _uiState.update { it.copy(tabs = tabs) }
    }

    // --- File operations ----------------------------------------------------

    fun createFile(parentPath: String, name: String) = fsOp {
        repo.createFile(File(parentPath), name)
        expanded += parentPath
    }

    fun createDir(parentPath: String, name: String) = fsOp {
        repo.createDir(File(parentPath), name)
        expanded += parentPath
    }

    fun delete(path: String) = fsOp {
        repo.delete(File(path))
        openDocs.remove(path)?.let {
            if (_uiState.value.activeTabId == path) {
                _uiState.update { s -> s.copy(activeTabId = openDocs.keys.lastOrNull()) }
            }
            refreshTabs()
        }
    }

    fun rename(path: String, newName: String) = fsOp {
        repo.rename(File(path), newName)
    }

    private inline fun fsOp(crossinline block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            refreshTree()
        }
    }
}
