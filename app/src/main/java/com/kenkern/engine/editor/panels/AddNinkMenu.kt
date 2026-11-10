package com.kenkern.engine.editor.panels

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.scene.NinkType

/**
 * Dynamic creation menu opened by the Hierarchy "Add Nink" button.
 *
 * Content is driven by [NinkCatalog] (extensible without UI changes) plus
 * the project's saved prefabs. The target hint tells the user where the new
 * Nink will be created (scene root or under the current selection).
 */
@Composable
fun AddNinkMenu(
    expanded: Boolean,
    selectedName: String?,
    prefabNames: List<String>,
    onAddType: (NinkType) -> Unit,
    onAddPrefab: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 420.dp)
    ) {
        TargetHint(selectedName)

        val grouped = NinkCatalog.byCategory()
        for ((category, entries) in grouped) {
            CategoryHeader(stringResource(category.titleRes))
            for (entry in entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(entry.type.nameRes), fontSize = 13.sp, color = KenkernColors.TextPrimary) },
                    leadingIcon = {
                        Icon(imageVector = entry.icon, contentDescription = null, tint = KenkernColors.Accent)
                    },
                    onClick = {
                        onDismiss()
                        onAddType(entry.type)
                    }
                )
            }
        }

        CategoryHeader(stringResource(R.string.add_menu_prefabs))
        if (prefabNames.isEmpty()) {
            Text(
                text = stringResource(R.string.add_menu_prefabs_empty),
                color = KenkernColors.TextDisabled,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        } else {
            for (prefab in prefabNames) {
                DropdownMenuItem(
                    text = { Text(prefab, fontSize = 13.sp, color = KenkernColors.TextPrimary) },
                    leadingIcon = {
                        Icon(imageVector = NinkCatalog.prefabIcon, contentDescription = null, tint = KenkernColors.Accent)
                    },
                    onClick = {
                        onDismiss()
                        onAddPrefab(prefab)
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetHint(selectedName: String?) {
    Text(
        text = if (selectedName == null) {
            stringResource(R.string.add_menu_target_root)
        } else {
            stringResource(R.string.add_menu_target_selected, selectedName)
        },
        color = KenkernColors.TextSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        color = KenkernColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}
