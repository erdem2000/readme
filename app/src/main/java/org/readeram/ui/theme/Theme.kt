package org.readeram.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF1A5C5E)
private val TealDark = Color(0xFF0F3D3E)
private val Sand = Color(0xFFF3E3C8)

@Composable
fun ReaderamTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF8FD0C6),
            onPrimary = TealDark,
            background = Color(0xFF121417),
            surface = Color(0xFF1C2128),
        )
    } else {
        lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            background = Color(0xFFF7F4EE),
            surface = Sand,
            onSurface = Color(0xFF1C1914),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
