package com.androidkris.ide.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidkris.ide.editor.CodeEditorView
import com.androidkris.ide.editor.rememberEditorController
import com.androidkris.ide.terminal.TerminalScreen
import com.androidkris.ide.workspace.StoragePermission
import com.androidkris.ide.workspace.TabUi
import com.androidkris.ide.workspace.TreeNode
import com.androidkris.ide.workspace.WorkspaceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controller = rememberEditorController()
    val attached by controller.attached.collectAsStateWithLifecycle()
    val darkTheme = isSystemInDarkTheme()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showFind by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    // Returns from the "All files access" settings screen; open the picker if granted.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (StoragePermission.hasAllFilesAccess()) showFolderPicker = true
    }

    fun openFolder() {
        if (StoragePermission.hasAllFilesAccess()) showFolderPicker = true
        else permLauncher.launch(StoragePermission.requestIntent(context))
    }

    // Bind the active tab's buffer into the shared editor once it's attached.
    androidx.compose.runtime.LaunchedEffect(state.activeTabId, attached) {
        if (attached) {
            viewModel.snapshot(state.activeTabId)?.let { controller.load(it.content, it.language) }
        }
    }

    fun captureCurrent() {
        if (attached) viewModel.updateContent(state.activeTabId, controller.getText())
    }

    Box(modifier = modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FileTreePanel(
                    projectName = state.projectName,
                    tree = state.tree,
                    onOpen = { node ->
                        captureCurrent()
                        viewModel.openFile(node)
                        if (!node.isDir) scope.launch { drawerState.close() }
                    },
                    onOpenFolder = { openFolder() },
                    onNewFile = { dialog = FileDialog.NewFile(it) },
                    onNewFolder = { dialog = FileDialog.NewFolder(it) },
                    onRename = { dialog = FileDialog.Rename(it) },
                    onDelete = { dialog = FileDialog.Delete(it) },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(state.projectName.ifEmpty { "AndroidKris IDE" }) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "File tree")
                        }
                    },
                    actions = {
                        IconButton(onClick = { controller.undo() }) {
                            Icon(Icons.Rounded.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { controller.redo() }) {
                            Icon(Icons.Rounded.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = {
                            showFind = !showFind
                            if (!showFind) controller.stopSearch()
                        }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Find")
                        }
                        IconButton(onClick = { showTerminal = true }) {
                            Icon(Icons.Rounded.Terminal, contentDescription = "Terminal")
                        }
                        IconButton(
                            onClick = { viewModel.save(state.activeTabId, controller.getText()) },
                            enabled = state.activeTabId != null,
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = "Save")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.tabs.isNotEmpty()) {
                    EditorTabs(
                        tabs = state.tabs,
                        activeId = state.activeTabId,
                        onSelect = { id -> captureCurrent(); viewModel.selectTab(id) },
                        onClose = { id -> captureCurrent(); viewModel.closeTab(id) },
                    )
                }

                AnimatedVisibility(visible = showFind) {
                    FindReplaceBar(
                        onQueryChange = { controller.search(it) },
                        onNext = { controller.findNext() },
                        onPrevious = { controller.findPrevious() },
                        onReplaceAll = { controller.replaceAll(it) },
                        onClose = { showFind = false; controller.stopSearch() },
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.activeTabId != null) {
                        CodeEditorView(
                            controller = controller,
                            darkTheme = darkTheme,
                            onModified = { viewModel.markModified(state.activeTabId) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        EmptyState(
                            loading = state.loading,
                            onOpenTree = { scope.launch { drawerState.open() } },
                        )
                    }
                }
            }
        }
    }

        if (showTerminal) {
            TerminalOverlay(onClose = { showTerminal = false })
        }
    }

    dialog?.let { d ->
        FileDialogHost(
            dialog = d,
            onDismiss = { dialog = null },
            onNewFile = { parent, name -> viewModel.createFile(parent, name) },
            onNewFolder = { parent, name -> viewModel.createDir(parent, name) },
            onRename = { node, name -> viewModel.rename(node.path, name) },
            onDelete = { node -> viewModel.delete(node.path) },
        )
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            initialPath = StoragePermission.externalRoot(),
            onDismiss = { showFolderPicker = false },
            onPick = { path ->
                showFolderPicker = false
                viewModel.setProjectRoot(path)
                scope.launch { drawerState.close() }
            },
        )
    }
}

@Composable
private fun EditorTabs(
    tabs: List<TabUi>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == activeId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .clickable { onSelect(tab.id) }
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                if (tab.modified) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = tab.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close ${tab.name}",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(loading: Boolean, onOpenTree: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            Text("Menyiapkan proyek…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Belum ada file terbuka",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onOpenTree) { Text("Buka file explorer") }
        }
    }
}

/** Full-screen terminal overlay (Fase 2.0) running the Android system shell. */
@Composable
private fun TerminalOverlay(onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    "Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup terminal")
                }
            }
            TerminalScreen(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onSessionEnd = onClose,
            )
        }
    }
}
