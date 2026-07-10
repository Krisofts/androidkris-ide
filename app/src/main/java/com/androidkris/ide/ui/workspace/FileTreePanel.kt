package com.androidkris.ide.ui.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidkris.ide.workspace.TreeNode

/**
 * The project file tree shown in the navigation drawer. Tap a file to open it,
 * tap a folder to expand/collapse, long-press any row for file operations.
 */
@Composable
fun FileTreePanel(
    projectName: String,
    tree: List<TreeNode>,
    onOpen: (TreeNode) -> Unit,
    onOpenFolder: () -> Unit,
    onNewFile: (parentPath: String) -> Unit,
    onNewFolder: (parentPath: String) -> Unit,
    onRename: (TreeNode) -> Unit,
    onDelete: (TreeNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(
                text = projectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenFolder) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = "Buka folder proyek")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tree, key = { it.path }) { node ->
                TreeRow(
                    node = node,
                    onOpen = onOpen,
                    onNewFile = onNewFile,
                    onNewFolder = onNewFolder,
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    node: TreeNode,
    onOpen: (TreeNode) -> Unit,
    onNewFile: (String) -> Unit,
    onNewFolder: (String) -> Unit,
    onRename: (TreeNode) -> Unit,
    onDelete: (TreeNode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .combinedClickable(
                    onClick = { onOpen(node) },
                    onLongClick = { menuOpen = true },
                )
                .padding(start = (8 + node.depth * 16).dp, end = 8.dp),
        ) {
            if (node.isDir) {
                Icon(
                    imageVector = if (node.expanded) Icons.Rounded.KeyboardArrowDown
                    else Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                Spacer(Modifier.width(22.dp))
                Icon(
                    Icons.Rounded.Article,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (node.isDir) {
                DropdownMenuItem(
                    text = { Text("File baru") },
                    leadingIcon = { Icon(Icons.Rounded.NoteAdd, null) },
                    onClick = { menuOpen = false; onNewFile(node.path) },
                )
                DropdownMenuItem(
                    text = { Text("Folder baru") },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                    onClick = { menuOpen = false; onNewFolder(node.path) },
                )
            }
            DropdownMenuItem(
                text = { Text("Ganti nama") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                onClick = { menuOpen = false; onRename(node) },
            )
            DropdownMenuItem(
                text = { Text("Hapus") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menuOpen = false; onDelete(node) },
            )
        }
    }
}
