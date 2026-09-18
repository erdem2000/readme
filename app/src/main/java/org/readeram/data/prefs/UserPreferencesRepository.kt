package org.readeram.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.readeram.ui.reader.ColorMode
import org.readeram.ui.reader.ReaderFont
import org.readeram.ui.reader.ReadingSettings
import org.readeram.ui.reader.TextAlignMode
import org.readeram.ui.reader.WeightMode

private val Context.dataStore by preferencesDataStore(name = "readeram_prefs")

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<ReadingSettings> = dataStore.data.map { prefs ->
        ReadingSettings(
            colorMode = ColorMode.fromKey(prefs[COLOR_MODE] ?: ColorMode.Sepia.key),
            brightness = prefs[BRIGHTNESS] ?: 0.72f,
            useSystemBrightness = prefs[USE_SYSTEM_BRIGHTNESS] ?: false,
            font = ReaderFont.fromKey(prefs[FONT] ?: ReaderFont.Serif.key),
            fontSizeSp = prefs[FONT_SIZE] ?: 18f,
            weight = WeightMode.fromKey(prefs[FONT_WEIGHT] ?: WeightMode.Normal.key),
            lineHeight = prefs[LINE_HEIGHT] ?: 1.45f,
            paragraphSpacingSp = prefs[PARAGRAPH_SPACING] ?: 10f,
            align = TextAlignMode.fromKey(prefs[ALIGN] ?: TextAlignMode.Justify.key),
            marginDp = prefs[MARGIN] ?: 20,
            ttsEngine = prefs[TTS_ENGINE] ?: "",
            ttsVoice = prefs[TTS_VOICE] ?: "",
            ttsSpeed = prefs[TTS_SPEED] ?: 1.0f,
            ttsPitch = prefs[TTS_PITCH] ?: 1.0f,
        )
    }

    suspend fun update(transform: (ReadingSettings) -> ReadingSettings) {
        dataStore.edit { prefs ->
            val current = ReadingSettings(
                colorMode = ColorMode.fromKey(prefs[COLOR_MODE] ?: ColorMode.Sepia.key),
                brightness = prefs[BRIGHTNESS] ?: 0.72f,
                useSystemBrightness = prefs[USE_SYSTEM_BRIGHTNESS] ?: false,
                font = ReaderFont.fromKey(prefs[FONT] ?: ReaderFont.Serif.key),
                fontSizeSp = prefs[FONT_SIZE] ?: 18f,
                weight = WeightMode.fromKey(prefs[FONT_WEIGHT] ?: WeightMode.Normal.key),
                lineHeight = prefs[LINE_HEIGHT] ?: 1.45f,
                paragraphSpacingSp = prefs[PARAGRAPH_SPACING] ?: 10f,
                align = TextAlignMode.fromKey(prefs[ALIGN] ?: TextAlignMode.Justify.key),
                marginDp = prefs[MARGIN] ?: 20,
                ttsEngine = prefs[TTS_ENGINE] ?: "",
                ttsVoice = prefs[TTS_VOICE] ?: "",
                ttsSpeed = prefs[TTS_SPEED] ?: 1.0f,
                ttsPitch = prefs[TTS_PITCH] ?: 1.0f,
            )
            val next = transform(current)
            prefs[COLOR_MODE] = next.colorMode.key
            prefs[BRIGHTNESS] = next.brightness
            prefs[USE_SYSTEM_BRIGHTNESS] = next.useSystemBrightness
            prefs[FONT] = next.font.key
            prefs[FONT_SIZE] = next.fontSizeSp
            prefs[FONT_WEIGHT] = next.weight.key
            prefs[LINE_HEIGHT] = next.lineHeight
            prefs[PARAGRAPH_SPACING] = next.paragraphSpacingSp
            prefs[ALIGN] = next.align.key
            prefs[MARGIN] = next.marginDp
            prefs[TTS_ENGINE] = next.ttsEngine
            prefs[TTS_VOICE] = next.ttsVoice
            prefs[TTS_SPEED] = next.ttsSpeed
            prefs[TTS_PITCH] = next.ttsPitch
        }
    }

    private companion object {
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val USE_SYSTEM_BRIGHTNESS = booleanPreferencesKey("use_system_brightness")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_WEIGHT = stringPreferencesKey("font_weight")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val ALIGN = stringPreferencesKey("align")
        val MARGIN = intPreferencesKey("margin")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
    }
}
