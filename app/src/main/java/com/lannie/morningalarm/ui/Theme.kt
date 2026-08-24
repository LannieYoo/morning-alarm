package com.lannie.morningalarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmScheme = lightColorScheme(
    primary = Color(0xFFE85D2F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574A),
    secondaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFFFDF8F5),
    surface = Color(0xFFFDF8F5),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF53433D),
)

@Composable
fun MorningTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WarmScheme, content = content)
}
