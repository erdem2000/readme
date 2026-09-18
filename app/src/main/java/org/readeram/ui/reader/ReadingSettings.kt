package org.readeram.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

enum class ColorMode(val key: String) {
    Day("day"),
    Night("night"),
    Sepia("sepia"),
    Twilight("twilight"),
    Console("console");

    fun next(): ColorMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromKey(key: String): ColorMode = entries.firstOrNull { it.key == key } ?: Sepia
    }
}

data class ReadingPalette(
    val background: Color,
    val onBackground: Color,
    val muted: Color,
    val highlight: Color,
    val chrome: Color,
    val onChrome: Color,
    val isDark: Boolean,
) {
    companion object {
        fun of(mode: ColorMode): ReadingPalette = when (mode) {
            ColorMode.Day -> ReadingPalette(
                background = Color(0xFFF7F4EE),
                onBackground = Color(0xFF1C1914),
                muted = Color(0xFF6B6458),
                highlight = Color(0xFFFFE08A),
                chrome = Color(0xFFE8E2D6),
                onChrome = Color(0xFF1C1914),
                isDark = false,
            )
            ColorMode.Night -> ReadingPalette(
                background = Color(0xFF121417),
                onBackground = Color(0xFFE6E8EC),
                muted = Color(0xFF9AA3B2),
                highlight = Color(0xFF3D4A2E),
                chrome = Color(0xFF1C2128),
                onChrome = Color(0xFFE6E8EC),
                isDark = true,
            )
            ColorMode.Sepia -> ReadingPalette(
                background = Color(0xFFF3E3C8),
                onBackground = Color(0xFF4A3420),
                muted = Color(0xFF7A5A3A),
                highlight = Color(0xFFE2C48A),
                chrome = Color(0xFFE6D1AE),
                onChrome = Color(0xFF4A3420),
                isDark = false,
            )
            ColorMode.Twilight -> ReadingPalette(
                background = Color(0xFF2A231C),
                onBackground = Color(0xFFD9C4A8),
                muted = Color(0xFFB39A7A),
                highlight = Color(0xFF4A3A28),
                chrome = Color(0xFF1F1A15),
                onChrome = Color(0xFFD9C4A8),
                isDark = true,
            )
            ColorMode.Console -> ReadingPalette(
                background = Color(0xFF07140C),
                onBackground = Color(0xFF7DFF9A),
                muted = Color(0xFF4CAF6A),
                highlight = Color(0xFF145C2A),
                chrome = Color(0xFF0B1F12),
                onChrome = Color(0xFF7DFF9A),
                isDark = true,
            )
        }
    }
}

enum class ReaderFont(val key: String) {
    Sans("sans"),
    Serif("serif"),
    Mono("mono"),
    OpenDyslexic("opendyslexic");

    val family: FontFamily
        get() = when (this) {
            Sans -> FontFamily.SansSerif
            Serif -> FontFamily.Serif
            Mono -> FontFamily.Monospace
            OpenDyslexic -> ReaderFonts.openDyslexic
        }

    companion object {
        fun fromKey(key: String): ReaderFont = entries.firstOrNull { it.key == key } ?: Serif
    }
}

enum class WeightMode(val key: String, val weight: FontWeight) {
    Normal("normal", FontWeight.Normal),
    Medium("medium", FontWeight.Medium),
    Bold("bold", FontWeight.Bold);

    companion object {
        fun fromKey(key: String): WeightMode = entries.firstOrNull { it.key == key } ?: Normal
    }
}

enum class TextAlignMode(val key: String) {
    Start("start"),
    Justify("justify");

    val compose: TextAlign
        get() = when (this) {
            Start -> TextAlign.Start
            Justify -> TextAlign.Justify
        }

    companion object {
        fun fromKey(key: String): TextAlignMode = entries.firstOrNull { it.key == key } ?: Justify
    }
}

data class ReadingSettings(
    val colorMode: ColorMode = ColorMode.Sepia,
    val brightness: Float = 0.72f,
    val useSystemBrightness: Boolean = false,
    val font: ReaderFont = ReaderFont.Serif,
    val fontSizeSp: Float = 18f,
    val weight: WeightMode = WeightMode.Normal,
    val lineHeight: Float = 1.45f,
    val paragraphSpacingSp: Float = 10f,
    val align: TextAlignMode = TextAlignMode.Justify,
    val marginDp: Int = 20,
    val ttsEngine: String = "",
    val ttsVoice: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
) {
    val palette: ReadingPalette get() = ReadingPalette.of(colorMode)
    val extraDim: Float
        get() = if (useSystemBrightness) 0f else ((0.18f - brightness).coerceAtLeast(0f) / 0.18f)
}
