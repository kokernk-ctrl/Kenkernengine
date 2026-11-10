package com.kenkern.engine.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.editor.theme.KenkernColors
import com.kenkern.engine.scene.NinkComponent
import com.kenkern.engine.scene.Transform
import kotlin.math.roundToInt

/**
 * Floating Inspector panel (Phase 2).
 *
 * Reflects the selected Nink live: flags, transform (position / rotation in
 * degrees / scale) and component data. Changes write into the actual scene
 * data (undoable flags + coalesced transforms), and scene changes resync the
 * fields via the revision keys.
 */
@Composable
fun InspectorPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    var panelOffset by remember { mutableStateOf(IntOffset.Zero) }

    // Reading the revisions makes the whole panel recompose when the scene
    // changes, so the fields below always reflect live scene data.
    val structureRevision = viewModel.structureRevision
    val transformRevision = viewModel.transformRevision
    val selection = viewModel.selection
    val nink = viewModel.selectedNink()

    PanelSurface(
        modifier = modifier
            .width(296.dp)
            .offsetPx(panelOffset)
    ) {
        Column {
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = KenkernColors.TextDisabled,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.inspector_title),
                    color = KenkernColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleInspector() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = KenkernColors.TextSecondary
                    )
                }
            }

            if (selection == null || nink == null) {
                Text(
                    text = stringResource(R.string.inspector_no_selection),
                    color = KenkernColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                // ---------------- Identity ----------------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nink.name,
                        color = KenkernColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.isMainCamera(nink.id)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(KenkernColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = KenkernColors.Accent,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.inspector_active_camera_badge),
                                color = KenkernColors.Accent,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Text(
                    text = nink.type.name,
                    color = KenkernColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                // ---------------- Flags ----------------
                FlagSwitch(stringResource(R.string.inspector_flag_active), nink.active) {
                    viewModel.setFlags(nink.id, active = it)
                }
                FlagSwitch(stringResource(R.string.inspector_flag_visible), nink.visible) {
                    viewModel.setFlags(nink.id, visible = it)
                }
                FlagSwitch(stringResource(R.string.inspector_flag_locked), nink.locked) {
                    viewModel.setFlags(nink.id, locked = it)
                }

                SectionDivider()

                // ---------------- Transform ----------------
                SectionLabel(stringResource(R.string.inspector_section_transform))
                val transformResetKey = remember(nink.id, structureRevision, transformRevision) { transformRevision }
                val current = nink.transform
                Vector3Fields(
                    label = stringResource(R.string.inspector_transform_position),
                    x = current.position.x,
                    y = current.position.y,
                    z = current.position.z,
                    resetKey = transformResetKey to "pos",
                    onCommit = { axis, value ->
                        commitTransform(viewModel, nink.id, current, axis, value, isPosition = true)
                    }
                )
                Vector3Fields(
                    label = stringResource(R.string.inspector_transform_rotation),
                    x = current.rotationEulerRadians.x,
                    y = current.rotationEulerRadians.y,
                    z = current.rotationEulerRadians.z,
                    resetKey = transformResetKey to "rot",
                    inDegrees = true,
                    onCommit = { axis, value ->
                        commitTransform(viewModel, nink.id, current, axis, value, isRotation = true)
                    }
                )
                Vector3Fields(
                    label = stringResource(R.string.inspector_transform_scale),
                    x = current.scale.x,
                    y = current.scale.y,
                    z = current.scale.z,
                    resetKey = transformResetKey to "scale",
                    onCommit = { axis, value ->
                        commitTransform(viewModel, nink.id, current, axis, value, isScale = true)
                    }
                )

                // ---------------- Components ----------------
                val camera = nink.componentOfType<NinkComponent.Camera>()
                val light = nink.componentOfType<NinkComponent.Light>()
                if (camera != null) {
                    SectionDivider()
                    SectionLabel(stringResource(R.string.inspector_component_camera))
                    NumberRow(stringResource(R.string.inspector_camera_fov), camera.fovDegrees) {
                        viewModel.updateCameraComponent(nink.id, fovDegrees = it)
                    }
                    NumberRow(stringResource(R.string.inspector_camera_near), camera.nearPlane) {
                        viewModel.updateCameraComponent(nink.id, nearPlane = it)
                    }
                    NumberRow(stringResource(R.string.inspector_camera_far), camera.farPlane) {
                        viewModel.updateCameraComponent(nink.id, farPlane = it)
                    }
                }
                if (light != null) {
                    SectionDivider()
                    SectionLabel(stringResource(R.string.inspector_component_light))
                    NumberRow(stringResource(R.string.inspector_light_intensity), light.intensity) {
                        viewModel.updateLightComponent(nink.id, intensity = it)
                    }
                    NumberRow(stringResource(R.string.inspector_light_range), light.range) {
                        viewModel.updateLightComponent(nink.id, range = it)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

private fun commitTransform(
    viewModel: EditorViewModel,
    id: com.kenkern.engine.scene.NinkId,
    current: Transform,
    axis: Int,
    value: Float,
    isPosition: Boolean = false,
    isRotation: Boolean = false,
    isScale: Boolean = false
) {
    val next = current.copy()
    when {
        isPosition -> when (axis) {
            0 -> next.position.x = value
            1 -> next.position.y = value
            else -> next.position.z = value
        }

        isRotation -> when (axis) {
            0 -> next.rotationEulerRadians.x = value
            1 -> next.rotationEulerRadians.y = value
            else -> next.rotationEulerRadians.z = value
        }

        isScale -> {
            if (value == 0f) return // degenerate scale rejected
            when (axis) {
                0 -> next.scale.x = value
                1 -> next.scale.y = value
                else -> next.scale.z = value
            }
        }
    }
    viewModel.setTransformFromInspector(id, next)
}

@Composable
private fun FlagSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = KenkernColors.TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = KenkernColors.Accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = KenkernColors.Field,
                uncheckedThumbColor = KenkernColors.TextDisabled,
                uncheckedBorderColor = KenkernColors.PanelBorder
            ),
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun NumberRow(label: String, value: Float, onCommit: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = KenkernColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(70.dp)
        )
        NumberField(
            value = value,
            resetKey = label,
            onCommit = onCommit,
            fieldWidth = 76.dp
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = KenkernColors.TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(KenkernColors.PanelBorder)
    )
}
