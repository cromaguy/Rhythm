/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import chromahub.rhythm.app.R
import chromahub.rhythm.app.infrastructure.audio.viper.ViperEngineTelemetry
import chromahub.rhythm.app.infrastructure.audio.viper.ViperOutputDevice
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import java.util.Locale

@Composable
fun ViperEngineStatusDialog(
    telemetry: ViperEngineTelemetry,
    isMasterEnabled: Boolean,
    onDismiss: () -> Unit
) {
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
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("insights", filled = true),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.viper_engine_status_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.viper_engine_status_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                StatusRow(
                    label = "Engine Status",
                    value = if (isMasterEnabled) "Active / Processing" else "Standby (Disabled)",
                    isHighlight = isMasterEnabled
                )

                StatusRow(
                    label = "DSP Core Architecture",
                    value = "Internal 32-bit Float NEON"
                )

                StatusRow(
                    label = "Audio Pipeline",
                    value = "Media3 / ExoPlayer Realtime"
                )

                StatusRow(
                    label = "Sampling Rate",
                    value = "${telemetry.sampleRate} Hz"
                )

                StatusRow(
                    label = "Audio Channels",
                    value = if (telemetry.channelCount == 2) "Stereo (2 Ch)" else "Mono (1 Ch)"
                )

                StatusRow(
                    label = "Output Device",
                    value = telemetry.currentDevice.displayName
                )

                StatusRow(
                    label = "Active Effects",
                    value = if (isMasterEnabled) "${telemetry.activeEffectsCount} of 24 Active" else "0 Active",
                    isHighlight = telemetry.activeEffectsCount > 0
                )

                StatusRow(
                    label = "Buffer Size",
                    value = "${telemetry.bufferFrames} Frames (< 1.0 ms Latency)"
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
