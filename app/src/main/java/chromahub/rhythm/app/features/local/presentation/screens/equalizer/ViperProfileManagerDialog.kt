/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens.equalizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import chromahub.rhythm.app.R
import chromahub.rhythm.app.infrastructure.audio.viper.*
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

@Composable
fun ViperProfileManagerDialog(
    repository: ViperSettingsRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showImportField by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("folder", filled = true),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.viper_profiles_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.viper_profiles_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = stringResource(R.string.viper_studio_presets),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                PresetButton("Audiophile Reference") {
                    repository.updateConfig {
                        it.copy(
                            masterEnabled = true,
                            fireEqualizer = FireEqualizerConfig(enabled = true, presetName = "Acoustic"),
                            viperClarity = ViperClarityConfig(enabled = true, mode = 1, gainDb = 2.5f),
                            cureCrossfeed = CureCrossfeedConfig(enabled = true, preset = 1),
                            masterGate = MasterGateConfig(limiterEnabled = true)
                        )
                    }
                    Toast.makeText(context, "Loaded Audiophile Reference", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                PresetButton("Basshead Earthquake") {
                    repository.updateConfig {
                        it.copy(
                            masterEnabled = true,
                            viperBass = ViperBassConfig(enabled = true, mode = 2, frequencyHz = 55, gainDb = 8.5f),
                            psychoacousticBass = PsychoacousticBassConfig(enabled = true, cutoffHz = 75, intensity = 0.6f),
                            bassMono = BassMonoConfig(enabled = true, frequencyHz = 85),
                            masterGate = MasterGateConfig(limiterEnabled = true)
                        )
                    }
                    Toast.makeText(context, "Loaded Basshead Earthquake", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                PresetButton("Warm Vintage Tube (6N1J)") {
                    repository.updateConfig {
                        it.copy(
                            masterEnabled = true,
                            tubeSimulator = TubeSimulatorConfig(enabled = true, model = 0, drive = 0.6f, mix = 0.45f),
                            analogX = AnalogXConfig(enabled = true, mode = 2),
                            convolver = ConvolverConfig(enabled = true, kernelName = "Warm Tube"),
                            masterGate = MasterGateConfig(limiterEnabled = true)
                        )
                    }
                    Toast.makeText(context, "Loaded Warm Vintage Tube", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                PresetButton("Stadium Live Soundstage") {
                    repository.updateConfig {
                        it.copy(
                            masterEnabled = true,
                            fieldSurround = FieldSurroundConfig(enabled = true, widening = 0.65f),
                            diffSurround = DiffSurroundConfig(enabled = true, delayMs = 9.0f, wetDryMix = 0.45f),
                            reverberation = ReverberationConfig(enabled = true, roomSize = 0.55f, wet = 0.22f),
                            masterGate = MasterGateConfig(limiterEnabled = true)
                        )
                    }
                    Toast.makeText(context, "Loaded Stadium Live Soundstage", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                Text(
                    text = stringResource(R.string.viper_backup_sharing),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val json = repository.exportProfileJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ViPER Profile", json))
                        Toast.makeText(context, context.getString(R.string.viper_profile_copied), Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.viper_export_profile))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showImportField = !showImportField },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showImportField) stringResource(R.string.viper_hide_import) else stringResource(R.string.viper_import_profile))
                }

                if (showImportField) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(stringResource(R.string.viper_paste_json)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (repository.importProfileJson(importText)) {
                                Toast.makeText(context, context.getString(R.string.viper_profile_imported_success), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.viper_profile_invalid_format), Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.viper_apply_imported))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    name: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(name, fontWeight = FontWeight.Medium)
    }
}
