package com.focuslock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF000000)
val Surf = Color(0xFF121212)
val Fg = Color(0xFFF2F2F2)
val Muted = Color(0xFF8C8C8C)
val Line = Color(0xFF262626)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Fg, onPrimary = Bg,
            background = Bg, onBackground = Fg,
            surface = Surf, onSurface = Fg,
            surfaceVariant = Surf, onSurfaceVariant = Muted,
        ),
        content = content
    )
}
