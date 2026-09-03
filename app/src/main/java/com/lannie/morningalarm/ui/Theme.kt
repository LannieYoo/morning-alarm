package com.lannie.morningalarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 앱 팔레트: 짙은 남색 바탕 + 주황 포인트 + 청록 보조 */
object Palette {
    val Bg = Color(0xFF0F172A)
    val Surface = Color(0xFF1E293B)
    val Surface2 = Color(0xFF273449)
    val Outline = Color(0xFF334155)
    val Orange = Color(0xFFF59E0B)
    val OrangeDim = Color(0xFF3D2E0A)
    val Teal = Color(0xFF14B8A6)
    val TealDim = Color(0xFF0F3D3A)
    val Text = Color(0xFFF1F5F9)
    val Muted = Color(0xFF94A3B8)
    val Danger = Color(0xFFF87171)
    val DangerDim = Color(0xFF4C1D1D)
    val Success = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
}

private val NightScheme = darkColorScheme(
    primary = Palette.Orange,
    onPrimary = Palette.Bg,
    primaryContainer = Palette.OrangeDim,
    onPrimaryContainer = Palette.Orange,
    secondary = Palette.Teal,
    onSecondary = Palette.Bg,
    secondaryContainer = Palette.OrangeDim,
    onSecondaryContainer = Palette.Orange,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface2,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Danger
)

@Composable
fun MorningTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightScheme, content = content)
}
