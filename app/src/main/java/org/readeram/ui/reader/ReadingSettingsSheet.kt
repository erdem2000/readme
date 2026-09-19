package org.readeram.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.readeram.R
import org.readeram.tts.TtsEngineOption
import org.readeram.tts.TtsVoiceOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
    settings: ReadingSettings,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    isPdf: Boolean,
    onChange: ((ReadingSettings) -> ReadingSettings) -> Unit,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onClose: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reading_settings),
                color = onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.close), color = onSurface)
            }
        }

        Label(stringResource(R.string.color_mode))
        ChipRow {
            ColorMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.colorMode == mode,
                    onClick = { onChange { it.copy(colorMode = mode) } },
                    label = { Text(modeLabel(mode)) },
                )
            }
        }

        Label(stringResource(R.string.brightness))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.use_system_brightness), color = onSurface, modifier = Modifier.weight(1f))
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
            Text(stringResource(R.string.pdf_font_hint), color = muted)
        } else {
            Label(stringResource(R.string.font))
            ChipRow {
                ReaderFont.entries.forEach { font ->
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { onChange { it.copy(font = font) } },
                        label = { Text(fontLabel(font)) },
                    )
                }
            }
            Label(stringResource(R.string.font_size))
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { value -> onChange { it.copy(fontSizeSp = value) } },
                valueRange = 12f..32f,
            )
            Label(stringResource(R.string.font_weight))
            ChipRow {
                WeightMode.entries.forEach { weight ->
                    FilterChip(
                        selected = settings.weight == weight,
                        onClick = { onChange { it.copy(weight = weight) } },
                        label = { Text(weightLabel(weight)) },
                    )
                }
            }
            Label(stringResource(R.string.line_spacing))
            Slider(
                value = settings.lineHeight,
                onValueChange = { value -> onChange { it.copy(lineHeight = value) } },
                valueRange = 1.1f..2.0f,
            )
            Label(stringResource(R.string.paragraph_spacing))
            Slider(
                value = settings.paragraphSpacingSp,
                onValueChange = { value -> onChange { it.copy(paragraphSpacingSp = value) } },
                valueRange = 0f..24f,
            )
            Label(stringResource(R.string.text_align))
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
            Label(stringResource(R.string.page_margins))
            Slider(
                value = settings.marginDp.toFloat(),
                onValueChange = { value -> onChange { it.copy(marginDp = value.toInt()) } },
                valueRange = 8f..40f,
            )
        }

        Label(stringResource(R.string.tts_engine))
        if (engines.isEmpty()) {
            Text(stringResource(R.string.tts_no_engines), color = muted)
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
            Label(stringResource(R.string.tts_voice))
            VoicePicker(
                voices = voices,
                selectedName = settings.ttsVoice,
                onVoice = onVoice,
            )
        }
        Label(stringResource(R.string.tts_speed))
        Slider(
            value = settings.ttsSpeed,
            onValueChange = { value -> onChange { it.copy(ttsSpeed = value) } },
            valueRange = 0.6f..2.0f,
        )
        Label(stringResource(R.string.tts_pitch))
        Slider(
            value = settings.ttsPitch,
            onValueChange = { value -> onChange { it.copy(ttsPitch = value) } },
            valueRange = 0.7f..1.4f,
        )
        Text(
            stringResource(R.string.battery_hint),
            color = muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    voices: List<TtsVoiceOption>,
    selectedName: String,
    onVoice: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == selectedName } ?: voices.firstOrNull()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = false,
            maxLines = 3,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = onSurface,
                unfocusedTextColor = onSurface,
                focusedBorderColor = onSurface,
                unfocusedBorderColor = muted,
                focusedTrailingIconColor = onSurface,
                unfocusedTrailingIconColor = onSurface,
                cursorColor = onSurface,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.label) },
                    onClick = {
                        onVoice(voice.name)
                        expanded = false
                    },
                )
            }
        }
    }
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
