/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens.equalizer

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import chromahub.rhythm.app.R
import chromahub.rhythm.app.infrastructure.audio.viper.ViperEngineTelemetry
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.ui.theme.MusicDimensions
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType

/**
 * Connected-corner shape for a tile inside a grouped grid, matching the
 * grouped cards used elsewhere in the app's bottom sheets.
 */
private fun statusTileShape(
    row: Int,
    col: Int,
    rowCount: Int,
    columnCount: Int
): RoundedCornerShape {
    fun corner(outerRow: Boolean, outerColumn: Boolean) = when {
        outerRow && outerColumn -> 20.dp
        outerRow || outerColumn -> 12.dp
        else -> 4.dp
    }

    return RoundedCornerShape(
        topStart = corner(row == 0, col == 0),
        topEnd = corner(row == 0, col == columnCount - 1),
        bottomStart = corner(row == rowCount - 1, col == 0),
        bottomEnd = corner(row == rowCount - 1, col == columnCount - 1)
    )
}

private data class ViperStatusItem(
    val icon: MaterialSymbolIcon,
    val label: String,
    val value: String
)

@Composable
fun ViperEngineStatusSheet(
    telemetry: ViperEngineTelemetry,
    isMasterEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val stateLabel = if (isMasterEnabled) {
        stringResource(R.string.viper_status_active)
    } else {
        stringResource(R.string.viper_status_standby)
    }

    val engineItems = listOf(
        ViperStatusItem(
            icon = MaterialSymbolIcon("power_settings_new", filled = true),
            label = stringResource(R.string.viper_status_engine),
            value = stateLabel
        ),
        ViperStatusItem(
            icon = MaterialSymbolIcon("auto_awesome", filled = true),
            label = stringResource(R.string.viper_status_active_effects),
            value = if (isMasterEnabled) "${telemetry.activeEffectsCount} / 24" else "0 / 24"
        ),
        ViperStatusItem(
            icon = MaterialSymbolIcon("memory", filled = true),
            label = stringResource(R.string.viper_status_dsp_core),
            value = "32-bit Float NEON"
        ),
        ViperStatusItem(
            icon = MaterialSymbolIcon("bolt", filled = true),
            label = stringResource(R.string.viper_status_buffer),
            value = "${telemetry.bufferFrames} frames"
        )
    )

    val outputItems = listOf(
        ViperStatusItem(
            icon = MaterialSymbolIcon("speed", filled = true),
            label = stringResource(R.string.viper_status_sample_rate),
            value = "${telemetry.sampleRate} Hz"
        ),
        ViperStatusItem(
            icon = MaterialSymbolIcon("surround_sound", filled = true),
            label = stringResource(R.string.viper_status_channels),
            value = if (telemetry.channelCount == 2) "Stereo (2 Ch)" else "Mono (1 Ch)"
        ),
        ViperStatusItem(
            icon = MaterialSymbolIcon("speaker", filled = true),
            label = stringResource(R.string.viper_status_output_device),
            value = telemetry.currentDevice.displayName
        ),
        ViperStatusItem(
            icon = MaterialSymbolIcon("graphic_eq", filled = true),
            label = stringResource(R.string.viper_status_pipeline),
            value = "Media3 / ExoPlayer"
        )
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("developer_board", filled = true),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.viper_engine_status_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ViperStatusSection(
                title = stringResource(R.string.viper_status_engine),
                icon = MaterialSymbolIcon("insights", filled = true),
                items = engineItems,
                tint = MaterialTheme.colorScheme.primary,
                tintContainer = MaterialTheme.colorScheme.primaryContainer
            )

            ViperStatusSection(
                title = stringResource(R.string.viper_status_output_device),
                icon = MaterialSymbolIcon("speaker_group", filled = true),
                items = outputItems,
                tint = MaterialTheme.colorScheme.secondary,
                tintContainer = MaterialTheme.colorScheme.secondaryContainer
            )

            TextButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/likelikeslike/ViPER4Android".toUri()
                    ).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text(
                    text = stringResource(R.string.viper_engine_upstream),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = MaterialSymbolIcon("open_in_new", filled = true),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ViperStatusSection(
    title: String,
    icon: MaterialSymbolIcon,
    items: List<ViperStatusItem>,
    tint: androidx.compose.ui.graphics.Color,
    tintContainer: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val rows = items.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowItems.forEachIndexed { colIndex, item ->
                        ViperStatusTile(
                            item = item,
                            tint = tint,
                            tintContainer = tintContainer,
                            shape = statusTileShape(
                                row = rowIndex,
                                col = colIndex,
                                rowCount = rows.size,
                                columnCount = 2
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ViperStatusTile(
    item: ViperStatusItem,
    tint: androidx.compose.ui.graphics.Color,
    tintContainer: androidx.compose.ui.graphics.Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    val tileColor = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        tintContainer,
        0.35f
    )

    Surface(
        modifier = modifier
            .height(MusicDimensions.infoTileHeight)
            .clip(shape),
        shape = shape,
        color = tileColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = tint.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(
                        x = MusicDimensions.infoTileBackdropCut,
                        y = MusicDimensions.infoTileBackdropCut
                    )
                    .size(MusicDimensions.infoTileIconSize)
            )

            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset,
                        bottom = MusicDimensions.infoTileInset
                    )
            )

            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        top = MusicDimensions.infoTileInset,
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset
                    )
            )
        }
    }
}
