/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.streaming.domain.model.OfflineSummary
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon

/**
 * Badges that say what is on the device in Go mode: a green check for a fully downloaded
 * playlist or album (like the streaming services people know), a progress chip while only part
 * of it is there, and a small format chip ("FLAC 44.1k", "MP3 320") on tracks.
 */

/** Green that reads as "done" on both light and dark surfaces; one place to change it. */
@Composable
fun offlineGreen(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

/**
 * Pill for playlist and album cards. Nothing is drawn while no track is downloaded, so
 * local-mode cards and untouched playlists look exactly as before.
 */
@Composable
fun OfflineStatusChip(summary: OfflineSummary, compact: Boolean = false) {
    if (summary.downloaded <= 0) return
    val green = offlineGreen()
    val iconSize = if (compact) 10.dp else 12.dp
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
        ) {
            if (summary.isComplete) {
                Icon(
                    imageVector = MaterialSymbolIcon("check_circle", filled = true),
                    contentDescription = stringResource(R.string.streaming_downloaded),
                    tint = green,
                    modifier = Modifier.size(iconSize)
                )
                Text(
                    text = stringResource(
                        if (summary.isAllLossless) R.string.streaming_offline_lossless else R.string.streaming_downloaded
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Icon(
                    imageVector = MaterialSymbolIcon("download_for_offline"),
                    contentDescription = stringResource(R.string.streaming_offline_partial_desc),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(iconSize)
                )
                Text(
                    text = "${summary.downloaded}/${summary.total}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * One line under the "N songs • time" header of a detail screen: "Downloaded • Lossless",
 * "Downloaded", or "12 of 229 downloaded". Draws nothing when no track is on the device.
 */
@Composable
fun OfflineSummaryLine(summary: OfflineSummary, modifier: Modifier = Modifier) {
    if (summary.downloaded <= 0) return
    val green = offlineGreen()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (summary.isComplete) MaterialSymbolIcon("check_circle", filled = true)
            else MaterialSymbolIcon("download_for_offline"),
            contentDescription = null,
            tint = if (summary.isComplete) green else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = when {
                summary.isComplete && summary.isAllLossless ->
                    stringResource(R.string.streaming_offline_complete_lossless)
                summary.isComplete -> stringResource(R.string.streaming_downloaded)
                else -> stringResource(R.string.streaming_offline_partial, summary.downloaded, summary.total)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (summary.isComplete) green else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small outlined format chip for a track row, e.g. "FLAC 44.1k" or "MP3 320". */
@Composable
fun QualityChip(label: String, highlighted: Boolean = false, modifier: Modifier = Modifier) {
    val color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
