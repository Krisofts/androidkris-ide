package com.androidkris.ide.ui.workspace

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.androidkris.ide.workspace.TreeNode

/** Which file-operation dialog is currently showing. */
sealed interface FileDialog {
    data class NewFile(val parentPath: String) : FileDialog
    data class NewFolder(val parentPath: String) : FileDialog
    data class Rename(val node: TreeNode) : FileDialog
    data class Delete(val node: TreeNode) : FileDialog
}

@Composable
fun FileDialogHost(
    dialog: FileDialog,
    onDismiss: () -> Unit,
    onNewFile: (parentPath: String, name: String) -> Unit,
    onNewFolder: (parentPath: String, name: String) -> Unit,
    onRename: (node: TreeNode, newName: String) -> Unit,
    onDelete: (node: TreeNode) -> Unit,
) {
    when (dialog) {
        is FileDialog.NewFile -> NameDialog(
            title = "File baru",
            initial = "",
            confirmLabel = "Buat",
            onDismiss = onDismiss,
            onConfirm = { onNewFile(dialog.parentPath, it); onDismiss() },
        )

        is FileDialog.NewFolder -> NameDialog(
            title = "Folder baru",
            initial = "",
            confirmLabel = "Buat",
            onDismiss = onDismiss,
            onConfirm = { onNewFolder(dialog.parentPath, it); onDismiss() },
        )

        is FileDialog.Rename -> NameDialog(
            title = "Ganti nama",
            initial = dialog.node.name,
            confirmLabel = "Simpan",
            onDismiss = onDismiss,
            onConfirm = { onRename(dialog.node, it); onDismiss() },
        )

        is FileDialog.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Hapus ${dialog.node.name}?") },
            text = { Text("Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { onDelete(dialog.node); onDismiss() }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("nama") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
