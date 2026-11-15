package com.nijatva.music.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The four surfaces Nijatva apps offer. */
enum class ThemeMode(val label: String) {
    System("System"),
    Dark("Dark"),
    Light("Light"),
    Black("Black"),
}

/** The three Nijatva accent colours. */
enum class Accent(val label: String, val argb: Long) {
    Green("Green", 0xFF10B981),
    Saffron("Saffron", 0xFFD97706),
    Indigo("Indigo", 0xFF4338CA),
}

val Accent.color: Color get() = Color(argb)

@Composable
private fun schemeFor(mode: ThemeMode, accent: Accent) = when (mode) {
    ThemeMode.Light -> lightColorScheme(
        primary = accent.color,
        onPrimary = Color.White,
        secondary = accent.color,
        onSecondary = Color.White,
        background = Color(0xFFFDFDFD),
        onBackground = Color(0xFF111111),
        surface = Color.White,
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFF1F1F1),
        onSurfaceVariant = Color(0xFF5F6368),
        outline = Color(0xFFDDDDDD),
    )

    ThemeMode.Dark -> darkColorScheme(
        primary = accent.color,
        onPrimary = Color.White,
        secondary = accent.color,
        onSecondary = Color.White,
        background = Color(0xFF121212),
        onBackground = Color(0xFFF3F4F6),
        surface = Color(0xFF1A1A1A),
        onSurface = Color(0xFFF3F4F6),
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color(0xFF9CA3AF),
        outline = Color(0xFF3A3A3A),
    )

    ThemeMode.Black -> darkColorScheme(
        primary = accent.color,
        onPrimary = Color.White,
        secondary = accent.color,
        onSecondary = Color.White,
        background = Color.Black,
        onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF0B0B0B),
        onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF171717),
        onSurfaceVariant = Color(0xFFA1A1AA),
        outline = Color(0xFF2A2A2A),
    )

    // "System" resolves to black when the platform is in dark mode.
    ThemeMode.System -> if (isSystemInDarkTheme()) schemeFor(ThemeMode.Black, accent) else schemeFor(ThemeMode.Light, accent)
}

@Composable
fun NijatvaTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Saffron,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = schemeFor(mode, accent), content = content)
}
