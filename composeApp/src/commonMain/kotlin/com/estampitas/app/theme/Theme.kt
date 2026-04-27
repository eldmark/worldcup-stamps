package com.estampitas.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF1565C0)
private val AccentDark = Color(0xFF90CAF9)

private val LightColors =
    lightColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD0E4FF),
        secondary = Color(0xFF006874),
        tertiary = Color(0xFF6750A4),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = AccentDark,
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF004A7A),
        secondary = Color(0xFF4FD8EB),
        tertiary = Color(0xFFD0BCFF),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun EstampitasTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
