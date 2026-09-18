package org.readeram.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.readeram.R
import org.readeram.tts.TtsEngineOption
import org.readeram.tts.TtsVoiceOption

@Composable
fun ReadingSettingsSheet(
    settings: ReadingSettings,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    isPdf: Boolean,
    onChange: ((ReadingSettings) -> ReadingSettings) -> Unit,
    onEngine: (String) -> Unit,
    onClose: () -> Unit,
) {
    val palette = settings.palette
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reading_settings),
                color = palette.onChrome,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.close), color = palette.onChrome)
            }
        }

        Label(stringResource(R.string.color_mode), palette)
        ChipRow {
            ColorMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.colorMode == mode,
                    onClick = { onChange { it.copy(colorMode = mode) } },
                    label = { Text(modeLabel(mode)) },
                )
            }
        }

        Label(stringResource(R.string.brightness), palette)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.use_system_brightness), color = palette.onChrome, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.useSystemBrightness,
                onCheckedChange = { checked -> onChange { it.copy(useSystemBrightness = checked) } },
            )
        }
        Slider(
            value = settings.brightness,
            onValueChange = { value -> onChange { it.copy(brightness = value, useSystemBrightness = false) } },
            valueRange = 0.02f..1f,
            enabled = !settings.useSystemBrightness,
        )

        if (isPdf) {
            Text(stringResource(R.string.pdf_font_hint), color = palette.muted)
        } else {
            Label(stringResource(R.string.font), palette)
            ChipRow {
                ReaderFont.entries.forEach { font ->
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { onChange { it.copy(font = font) } },
                        label = { Text(fontLabel(font)) },
                    )
                }
            }
            Label(stringResource(R.string.font_size), palette)
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { value -> onChange { it.copy(fontSizeSp = value) } },
                valueRange = 12f..32f,
            )
            Label(stringResource(R.string.font_weight), palette)
            ChipRow {
                WeightMode.entries.forEach { weight ->
                    FilterChip(
                        selected = settings.weight == weight,
                        onClick = { onChange { it.copy(weight = weight) } },
                        label = { Text(weightLabel(weight)) },
                    )
                }
            }
            Label(stringResource(R.string.line_spacing), palette)
            Slider(
                value = settings.lineHeight,
                onValueChange = { value -> onChange { it.copy(lineHeight = value) } },
                valueRange = 1.1f..2.0f,
            )
            Label(stringResource(R.string.paragraph_spacing), palette)
            Slider(
                value = settings.paragraphSpacingSp,
                onValueChange = { value -> onChange { it.copy(paragraphSpacingSp = value) } },
                valueRange = 0f..24f,
            )
            Label(stringResource(R.string.text_align), palette)
            ChipRow {
                TextAlignMode.entries.forEach { align ->
                    FilterChip(
                        selected = settings.align == align,
                        onClick = { onChange { it.copy(align = align) } },
                        label = {
                            Text(
                                if (align == TextAlignMode.Start) stringResource(R.string.align_start)
                                else stringResource(R.string.align_justify),
                            )
                        },
                    )
                }
            }
            Label(stringResource(R.string.page_margins), palette)
            Slider(
                value = settings.marginDp.toFloat(),
                onValueChange = { value -> onChange { it.copy(marginDp = value.toInt()) } },
                valueRange = 8f..40f,
            )
        }

        Label(stringResource(R.string.tts_engine), palette)
        if (engines.isEmpty()) {
            Text(stringResource(R.string.tts_no_engines), color = palette.muted)
        } else {
            ChipRow {
                engines.forEach { engine ->
                    FilterChip(
                        selected = settings.ttsEngine == engine.packageName,
                        onClick = { onEngine(engine.packageName) },
                        label = { Text(engineLabel(engine)) },
                    )
                }
            }
        }
        if (voices.isNotEmpty()) {
            Label(stringResource(R.string.tts_voice), palette)
            ChipRow {
                voices.take(12).forEach { voice ->
                    FilterChip(
                        selected = settings.ttsVoice == voice.name,
                        onClick = { onChange { it.copy(ttsVoice = voice.name) } },
                        label = { Text(voice.locale) },
                    )
                }
            }
        }
        Label(stringResource(R.string.tts_speed), palette)
        Slider(
            value = settings.ttsSpeed,
            onValueChange = { value -> onChange { it.copy(ttsSpeed = value) } },
            valueRange = 0.6f..2.0f,
        )
        Label(stringResource(R.string.tts_pitch), palette)
        Slider(
            value = settings.ttsPitch,
            onValueChange = { value -> onChange { it.copy(ttsPitch = value) } },
            valueRange = 0.7f..1.4f,
        )
        Text(stringResource(R.string.battery_hint), color = palette.muted, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun Label(text: String, palette: ReadingPalette) {
    Text(text, color = palette.onChrome, fontWeight = FontWeight.Medium)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun modeLabel(mode: ColorMode) = stringResource(
    when (mode) {
        ColorMode.Day -> R.string.mode_day
        ColorMode.Night -> R.string.mode_night
        ColorMode.Sepia -> R.string.mode_sepia
        ColorMode.Twilight -> R.string.mode_twilight
        ColorMode.Console -> R.string.mode_console
    },
)

@Composable
private fun fontLabel(font: ReaderFont) = stringResource(
    when (font) {
        ReaderFont.Sans -> R.string.font_sans
        ReaderFont.Serif -> R.string.font_serif
        ReaderFont.Mono -> R.string.font_mono
        ReaderFont.OpenDyslexic -> R.string.font_dyslexic
    },
)

@Composable
private fun weightLabel(weight: WeightMode) = stringResource(
    when (weight) {
        WeightMode.Normal -> R.string.weight_normal
        WeightMode.Medium -> R.string.weight_medium
        WeightMode.Bold -> R.string.weight_bold
    },
)

@Composable
private fun engineLabel(engine: TtsEngineOption) = when (engine.kind) {
    TtsEngineOption.Kind.Google -> stringResource(R.string.tts_engine_google)
    TtsEngineOption.Kind.Samsung -> stringResource(R.string.tts_engine_samsung)
    TtsEngineOption.Kind.Other -> engine.label
}
