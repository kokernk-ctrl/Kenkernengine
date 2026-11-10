package com.kenkern.engine.editor.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * KENKERN visual identity:
 *  - dark background #0E0E10
 *  - white text #FFFFFF
 *  - blue accent #007BFF
 *
 * The editor is intentionally a dark, technical, low-decoration surface.
 * All colors are centralized here; screens must not hardcode new palette
 * entries so the identity stays consistent.
 */
object KenkernColors {
    val Background = Color(0xFF0E0E10)
    val Panel = Color(0xFF17171A)
    val PanelRaised = Color(0xFF1D1D22)
    val PanelBorder = Color(0xFF2A2A31)
    val Field = Color(0xFF101013)
    val Accent = Color(0xFF007BFF)
    val AccentPressed = Color(0xFF0063CC)
    val SelectionBg = Color(0x2E007BFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9BA0A6)
    val TextDisabled = Color(0xFF5E6167)
    val Danger = Color(0xFFE5484D)
    val Success = Color(0xFF3DD68C)
    val Warning = Color(0xFFF5A524)
}

private val KenkernColorScheme = darkColorScheme(
    primary = KenkernColors.Accent,
    onPrimary = Color.White,
    primaryContainer = KenkernColors.AccentPressed,
    onPrimaryContainer = Color.White,
    background = KenkernColors.Background,
    onBackground = KenkernColors.TextPrimary,
    surface = KenkernColors.Panel,
    onSurface = KenkernColors.TextPrimary,
    surfaceVariant = KenkernColors.PanelRaised,
    onSurfaceVariant = KenkernColors.TextSecondary,
    surfaceContainer = KenkernColors.Panel,
    surfaceContainerHigh = KenkernColors.PanelRaised,
    error = KenkernColors.Danger,
    onError = Color.White,
    outline = KenkernColors.PanelBorder,
    outlineVariant = KenkernColors.PanelBorder
)

/** The single Compose theme of the editor. KENKERN is always dark. */
@Composable
fun KenkernTheme(content: @Composable () -> Unit) {
    // Editor identity is fixed dark regardless of system setting; the check
    // keeps the API open for a future light preview mode without call-site churn.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = KenkernColorScheme,
        content = content
    )
}
