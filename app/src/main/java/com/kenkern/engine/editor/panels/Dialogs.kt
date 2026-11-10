package com.kenkern.engine.editor.panels

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.theme.KenkernColors

/** Dialogs shared by the hierarchy panel. */

@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = KenkernColors.Accent)
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    ninkName: String,
    childCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val suffix = if (childCount > 0) stringResource(R.string.dialog_delete_children_suffix, childCount) else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(stringResource(R.string.dialog_delete_text, ninkName, suffix)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = KenkernColors.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Target picker for "Move to Group": lists all group ninks plus the scene
 * root. Locked groups and the nink's own subtree are excluded by the caller.
 */
@Composable
fun MoveToGroupDialog(
    groupNames: List<String>,
    hasGroupParent: Boolean,
    onPickRoot: () -> Unit,
    onPickGroup: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_move_to_group_title)) },
        text = {
            if (groupNames.isEmpty() && !hasGroupParent) {
                Text(stringResource(R.string.dialog_move_to_group_none), fontSize = 13.sp)
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (hasGroupParent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onPickRoot() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(selected = false, onClick = { onDismiss(); onPickRoot() })
                            Text(
                                stringResource(R.string.dialog_move_to_group_root),
                                fontSize = 13.sp,
                                color = KenkernColors.TextPrimary
                            )
                        }
                    }
                    groupNames.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onPickGroup(index) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(selected = false, onClick = { onDismiss(); onPickGroup(index) })
                            Text(name, fontSize = 13.sp, color = KenkernColors.TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun CreatePrefabDialog(
    suggestedName: String,
    sourceName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_create_prefab_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.dialog_create_prefab_text, sourceName),
                    fontSize = 13.sp,
                    color = KenkernColors.TextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_prefab_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = KenkernColors.Accent)
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
