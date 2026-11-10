package com.kenkern.engine.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.HierarchyRow
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.rendering.NinkRenderColors
import com.kenkern.engine.scene.NinkId
import kotlin.math.roundToInt

/**
 * Dynamic floating Hierarchy panel (Phase 2).
 *
 * Renders the registry projection (never a second source of truth):
 * search, expand/collapse, selection, per-row three-dot menus, drag-handle
 * reparenting and rename/create-prefab/move-to-group/delete dialogs.
 */
@Composable
fun HierarchyPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    var panelOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showAddMenu by remember { mutableStateOf(false) }
    var menuRowId by remember { mutableStateOf<NinkId?>(null) }
    var renameRowId by remember { mutableStateOf<NinkId?>(null) }
    var moveToGroupRowId by remember { mutableStateOf<NinkId?>(null) }
    var createPrefabRowId by remember { mutableStateOf<NinkId?>(null) }
    var deleteRowId by remember { mutableStateOf<NinkId?>(null) }
    var addChildRowId by remember { mutableStateOf<NinkId?>(null) }

    // Drag & drop reparenting state.
    var draggingId by remember { mutableStateOf<NinkId?>(null) }
    var hoverTargetId by remember { mutableStateOf<NinkId?>(null) }
    val dragPointer = remember { mutableStateOf(Offset.Zero) }
    val rowBounds = remember { mutableStateMapOf<NinkId, Rect>() }

    fun rowAt(position: Offset): NinkId? {
        for ((id, rect) in rowBounds) {
            if (rect.contains(position)) return id
        }
        return null
    }

    PanelSurface(
        modifier = modifier
            .width(272.dp)
            .offsetPx(panelOffset)
    ) {
        Column {
            // ---------------- Header (draggable) ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KenkernColors.PanelRaised, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                panelOffset = IntOffset(
                                    panelOffset.x + dragAmount.x.roundToInt(),
                                    (panelOffset.y + dragAmount.y.roundToInt()).coerceAtLeast(-8)
                                )
                            }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = KenkernColors.TextDisabled,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hierarchy_title),
                        color = KenkernColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.hierarchy_scene_format, viewModel.sceneName),
                        color = KenkernColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.hierarchy_add_nink),
                            tint = KenkernColors.Accent
                        )
                    }
                    AddNinkMenu(
                        expanded = showAddMenu,
                        selectedName = viewModel.selection?.let { viewModel.selectedNink()?.name },
                        prefabNames = viewModel.prefabNames,
                        onAddType = { viewModel.addNink(it) },
                        onAddPrefab = { viewModel.instantiatePrefab(it) },
                        onDismiss = { showAddMenu = false }
                    )
                }
                IconButton(onClick = { viewModel.toggleHierarchy() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = KenkernColors.TextSecondary
                    )
                }
            }

            // ---------------- Search + expand controls ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.refreshSearch(it) },
                    placeholder = {
                        Text(stringResource(R.string.hierarchy_search_hint), fontSize = 12.sp, color = KenkernColors.TextDisabled)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = KenkernColors.TextSecondary, modifier = Modifier.size(16.dp))
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = KenkernColors.TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KenkernColors.Accent,
                        unfocusedBorderColor = KenkernColors.PanelBorder,
                        focusedContainerColor = KenkernColors.Field,
                        unfocusedContainerColor = KenkernColors.Field,
                        cursorColor = KenkernColors.Accent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                )
                IconButton(onClick = { viewModel.expandAll() }) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.hierarchy_expand_all),
                        tint = KenkernColors.TextSecondary
                    )
                }
                IconButton(onClick = { viewModel.collapseAll() }) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.hierarchy_collapse_all),
                        tint = KenkernColors.TextSecondary
                    )
                }
            }

            // ---------------- Tree ----------------
            val rows = viewModel.hierarchyRows
            if (rows.isEmpty()) {
                Text(
                    text = if (viewModel.searchQuery.isBlank()) {
                        stringResource(R.string.hierarchy_empty)
                    } else {
                        stringResource(R.string.hierarchy_no_results)
                    },
                    color = KenkernColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(rows, key = { it.id }) { row ->
                        HierarchyRowItem(
                            row = row,
                            selected = row.id == viewModel.selection,
                            isDragging = draggingId == row.id,
                            isHoverTarget = hoverTargetId == row.id && draggingId != null && draggingId != row.id,
                            onRowBounds = { rect -> rowBounds[row.id] = rect },
                            onTap = { viewModel.select(row.id) },
                            onDoubleTap = { renameRowId = row.id },
                            onLongPress = { menuRowId = row.id },
                            onToggleExpand = { viewModel.toggleExpanded(row.id) },
                            onOpenMenu = { menuRowId = row.id },
                            onDragStart = {
                                draggingId = row.id
                                hoverTargetId = row.id
                                dragPointer.value = rowBounds[row.id]?.center ?: Offset.Zero
                            },
                            onDrag = { amount ->
                                dragPointer.value = Offset(
                                    dragPointer.value.x + amount.x,
                                    dragPointer.value.y + amount.y
                                )
                                hoverTargetId = rowAt(dragPointer.value)
                            },
                            onDragEnd = {
                                val target = hoverTargetId
                                val dragged = draggingId
                                if (dragged != null && target != null && dragged != target) {
                                    viewModel.reparentNink(dragged, target)
                                }
                                draggingId = null
                                hoverTargetId = null
                            },
                            onDragCancel = {
                                draggingId = null
                                hoverTargetId = null
                            }
                        )
                        // Row-anchored context menu
                        Box(contentAlignment = Alignment.TopStart) {
                            RowMenuHost(
                                row = row,
                                menuRowId = menuRowId,
                                viewModel = viewModel,
                                onClose = { menuRowId = null },
                                onRename = { renameRowId = row.id },
                                onAddChild = { addChildRowId = row.id },
                                onMoveToGroup = { moveToGroupRowId = row.id },
                                onCreatePrefab = { createPrefabRowId = row.id },
                                onDelete = { deleteRowId = row.id }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    // ---------------- Dialogs ----------------
    renameRowId?.let { id ->
        val name = viewModel.selectedNinkById(id)?.name ?: ""
        RenameDialog(
            currentName = name,
            onConfirm = { value ->
                viewModel.renameNink(id, value)
                renameRowId = null
            },
            onDismiss = { renameRowId = null }
        )
    }

    addChildRowId?.let { parentId ->
        AddChildNinkDialog(
            onConfirm = { type ->
                viewModel.addChildNink(type, parentId)
                addChildRowId = null
            },
            onDismiss = { addChildRowId = null }
        )
    }

    createPrefabRowId?.let { id ->
        val nink = viewModel.selectedNinkById(id)
        if (nink != null) {
            CreatePrefabDialog(
                suggestedName = nink.name,
                sourceName = nink.name,
                onConfirm = { value ->
                    viewModel.createPrefab(id, value)
                    createPrefabRowId = null
                },
                onDismiss = { createPrefabRowId = null }
            )
        } else {
            createPrefabRowId = null
        }
    }

    deleteRowId?.let { id ->
        val nink = viewModel.selectedNinkById(id)
        if (nink != null) {
            DeleteConfirmDialog(
                ninkName = nink.name,
                childCount = nink.childIds.size,
                onConfirm = {
                    viewModel.deleteNink(id)
                    deleteRowId = null
                },
                onDismiss = { deleteRowId = null }
            )
        } else {
            deleteRowId = null
        }
    }

    moveToGroupRowId?.let { id ->
        val groups = viewModel.groupTargetsFor(id)
        val hasGroupParent = viewModel.selectedNinkById(id)?.parentId != null
        MoveToGroupDialog(
            groupNames = groups.map { it.second },
            hasGroupParent = hasGroupParent,
            onPickRoot = { viewModel.reparentNink(id, null) },
            onPickGroup = { index -> viewModel.reparentNink(id, groups[index].first) },
            onDismiss = { moveToGroupRowId = null }
        )
    }
}

/** Renders the per-row DropdownMenu only for the row whose menu is open. */
@Composable
private fun RowMenuHost(
    row: HierarchyRow,
    menuRowId: NinkId?,
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onAddChild: () -> Unit,
    onMoveToGroup: () -> Unit,
    onCreatePrefab: () -> Unit,
    onDelete: () -> Unit
) {
    val nink = viewModel.selectedNinkById(row.id) ?: return
    NinkContextMenu(
        expanded = menuRowId == row.id,
        nink = nink,
        isMainCamera = viewModel.isMainCamera(row.id),
        hasParentOrdering = true,
        onDismiss = onClose,
        onRename = onRename,
        onDuplicate = { viewModel.duplicateNink(row.id) },
        onAddChild = onAddChild,
        onMoveUp = { viewModel.moveUp(row.id) },
        onMoveDown = { viewModel.moveDown(row.id) },
        onMoveToGroup = onMoveToGroup,
        onUngroup = { viewModel.ungroup(row.id) },
        onSetActiveCamera = { viewModel.setActiveCamera(row.id) },
        onToggleVisibility = { viewModel.setFlags(row.id, visible = !nink.visible) },
        onToggleLock = { viewModel.setFlags(row.id, locked = !nink.locked) },
        onCreatePrefab = onCreatePrefab,
        onDelete = onDelete
    )
}

@Composable
private fun HierarchyRowItem(
    row: HierarchyRow,
    selected: Boolean,
    isDragging: Boolean,
    isHoverTarget: Boolean,
    onRowBounds: (Rect) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenMenu: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val typeColor = remember(row.type) {
        val rgba = NinkRenderColors.colorForType(row.type.name)
        Color(rgba[0], rgba[1], rgba[2], 1f)
    }
    val background = when {
        selected -> KenkernColors.SelectionBg
        isHoverTarget -> KenkernColors.Accent.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .onGloballyPositioned { onRowBounds(it.boundsInRoot()) }
            .pointerInput(row.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(start = (8 + row.depth * 16).dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand caret
        if (row.hasChildren) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = KenkernColors.TextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleExpand() }
                    .rotate(if (row.expanded) 0f else -90f)
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }

        // Drag handle (reparenting)
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = if (row.locked) KenkernColors.TextDisabled else KenkernColors.TextSecondary,
            modifier = Modifier
                .size(16.dp)
                .pointerInput(row.id, row.locked) {
                    if (row.locked) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount)
                        }
                    )
                }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Type dot
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(typeColor, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Name
        val nameAlpha = when {
            !row.visible -> 0.42f
            !row.active -> 0.55f
            else -> 1f
        }
        Text(
            text = row.name,
            color = KenkernColors.TextPrimary.copy(alpha = nameAlpha),
            fontSize = 13.sp,
            fontWeight = if (row.type.isGroup) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 7.dp)
        )

        if (!row.visible) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = KenkernColors.TextDisabled,
                modifier = Modifier.size(14.dp)
            )
        }
        if (row.locked) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = KenkernColors.Warning,
                modifier = Modifier.size(13.dp)
            )
        }

        // Three-dot menu
        IconButton(onClick = onOpenMenu, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.hierarchy_more_actions),
                tint = KenkernColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddChildNinkDialog(
    onConfirm: (com.kenkern.engine.scene.NinkType) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_child)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(NinkCatalog.all()) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss(); onConfirm(entry.type) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = KenkernColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(entry.type.nameRes),
                            fontSize = 13.sp,
                            color = KenkernColors.TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
