package com.kenkern.engine.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.editor.theme.KenkernColors
import java.util.Locale

/** Shared building blocks for floating editor panels. */

/** Panel drag offset helper shared by both floating panels. */
internal fun Modifier.offsetPx(offset: IntOffset): Modifier =
    this.offset { IntOffset(offset.x, offset.y) }

/**
 * Floating panel chrome: rounded dark surface with a KENKERN border.
 * Rendered above the GL viewport as regular Compose content.
 */
@Composable
fun PanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = KenkernColors.Panel.copy(alpha = 0.98f),
        border = androidx.compose.foundation.BorderStroke(1.dp, KenkernColors.PanelBorder),
        shadowElevation = 6.dp
    ) {
        content()
    }
}

/** Parses float input tolerating both decimal separators. */
fun parseFlexibleFloat(raw: String): Float? =
    raw.trim().replace(',', '.').toFloatOrNull()

/** Locale-stable float formatting for numeric editor fields. */
fun formatFloat(value: Float): String {
    val rounded = kotlin.math.round(value * 100f) / 100f
    return if (rounded == kotlin.math.round(rounded)) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded)
    }
}

/**
 * Compact numeric text field that commits live while typing and re-syncs
 * from scene data when the reset key changes (selection, undo, external
 * edits) — but never while the field owns focus.
 */
@Composable
fun NumberField(
    value: Float,
    resetKey: Any?,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fieldWidth: Dp = 64.dp
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(formatFloat(value))) }
    var isFocused by remember { mutableStateOf(false) }
    val focusedState by rememberUpdatedState(isFocused)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value, resetKey) {
        if (!focusedState) {
            fieldValue = TextFieldValue(formatFloat(value))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { input ->
            fieldValue = input
            parseFlexibleFloat(input.text)?.let(onCommit)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = KenkernColors.TextPrimary, fontSize = 12.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KenkernColors.Accent,
            unfocusedBorderColor = KenkernColors.PanelBorder,
            focusedContainerColor = KenkernColors.Field,
            unfocusedContainerColor = KenkernColors.Field,
            cursorColor = KenkernColors.Accent
        ),
        modifier = modifier
            .width(fieldWidth)
            .onFocusChanged { state ->
                val wasFocused = isFocused
                isFocused = state.isFocused
                if (wasFocused && !state.isFocused) {
                    fieldValue = TextFieldValue(formatFloat(value))
                }
            }
    )
}

/**
 * Labeled X/Y/Z triple of numeric fields (used for transform editing).
 * [inDegrees] converts radians <-> degrees for rotation rows.
 */
@Composable
fun Vector3Fields(
    label: String,
    x: Float,
    y: Float,
    z: Float,
    resetKey: Any?,
    inDegrees: Boolean = false,
    onCommit: (axis: Int, value: Float) -> Unit
) {
    fun toUi(v: Float) = if (inDegrees) Math.toDegrees(v.toDouble()).toFloat() else v
    fun fromUi(v: Float) = if (inDegrees) Math.toRadians(v.toDouble()).toFloat() else v

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = KenkernColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(64.dp)
            )
            AxisField(axisLabel = "X", value = toUi(x), resetKey = resetKey) { onCommit(0, fromUi(it)) }
            AxisField(axisLabel = "Y", value = toUi(y), resetKey = resetKey) { onCommit(1, fromUi(it)) }
            AxisField(axisLabel = "Z", value = toUi(z), resetKey = resetKey) { onCommit(2, fromUi(it)) }
        }
    }
}

@Composable
private fun AxisField(
    axisLabel: String,
    value: Float,
    resetKey: Any?,
    onCommit: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = axisLabel,
            color = KenkernColors.TextDisabled,
            fontSize = 10.sp,
            modifier = Modifier.padding(end = 2.dp)
        )
        NumberField(
            value = value,
            resetKey = resetKey,
            onCommit = onCommit,
            fieldWidth = 58.dp
        )
    }
}

/** Small type indicator dot used in hierarchy rows. */
fun Modifier.ninkTypeDot(color: androidx.compose.ui.graphics.Color): Modifier = this.then(
    Modifier.background(color, RoundedCornerShape(50))
)
