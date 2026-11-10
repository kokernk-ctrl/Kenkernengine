package com.kenkern.engine.editor.panels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.scene.Nink

/**
 * Three-dot contextual menu of a hierarchy row.
 *
 * Actions are filtered by Nink kind so unimplemented or inapplicable
 * entries are never shown (e.g. "Ungroup" only for groups, "Set Active
 * Camera" only for cameras that are not already active).
 */
@Composable
fun NinkContextMenu(
    expanded: Boolean,
    nink: Nink,
    isMainCamera: Boolean,
    hasParentOrdering: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onAddChild: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToGroup: () -> Unit,
    onUngroup: () -> Unit,
    onSetActiveCamera: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onCreatePrefab: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(R.string.action_rename, Icons.Filled.Edit) { onDismiss(); onRename() }
        MenuItem(R.string.action_duplicate, Icons.Filled.ContentCopy) { onDismiss(); onDuplicate() }
        MenuItem(R.string.action_add_child, Icons.Filled.Add) { onDismiss(); onAddChild() }

        if (hasParentOrdering) {
            MenuItem(R.string.action_move_up, Icons.Filled.KeyboardArrowUp) { onDismiss(); onMoveUp() }
            MenuItem(R.string.action_move_down, Icons.Filled.KeyboardArrowDown) { onDismiss(); onMoveDown() }
        }
        MenuItem(R.string.action_move_to_group, Icons.Filled.Folder) { onDismiss(); onMoveToGroup() }

        if (nink.type.isGroup) {
            MenuItem(R.string.action_ungroup, Icons.Filled.FolderOpen) { onDismiss(); onUngroup() }
        }
        if (nink.type.isCamera && !isMainCamera) {
            MenuItem(R.string.action_set_active_camera, Icons.Filled.Videocam) { onDismiss(); onSetActiveCamera() }
        }

        if (nink.visible) {
            MenuItem(R.string.action_hide, Icons.Filled.VisibilityOff) { onDismiss(); onToggleVisibility() }
        } else {
            MenuItem(R.string.action_show, Icons.Filled.Visibility) { onDismiss(); onToggleVisibility() }
        }
        if (nink.locked) {
            MenuItem(R.string.action_unlock, Icons.Filled.LockOpen) { onDismiss(); onToggleLock() }
        } else {
            MenuItem(R.string.action_lock, Icons.Filled.Lock) { onDismiss(); onToggleLock() }
        }
        MenuItem(R.string.action_create_prefab, Icons.Filled.Widgets) { onDismiss(); onCreatePrefab() }
        MenuItem(R.string.action_delete, Icons.Filled.Delete, tint = KenkernColors.Danger) { onDismiss(); onDelete() }
    }
}

@Composable
private fun MenuItem(
    labelRes: Int,
    icon: ImageVector,
    tint: Color = KenkernColors.TextPrimary,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), fontSize = 13.sp, color = tint) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = tint) },
        onClick = onClick
    )
}
