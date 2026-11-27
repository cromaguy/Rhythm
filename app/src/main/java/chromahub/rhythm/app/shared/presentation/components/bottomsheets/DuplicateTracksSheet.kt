/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.features.local.domain.library.DuplicateFinder
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DuplicateTracksSheet(
    songs: List<Song>,
    onPlaySong: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var groups by remember { mutableStateOf<List<DuplicateFinder.DuplicateGroup>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(Unit) { sheetState.expand() }

    LaunchedEffect(songs) {
        isScanning = true
        groups = withContext(Dispatchers.Default) {
            DuplicateFinder.findDuplicates(songs)
        }
        isScanning = false
    }

    val subtitleText = when {
        isScanning -> "Scanning ${songs.size} tracks..."
        groups.isEmpty() -> "No duplicate tracks found across ${songs.size} songs"
        else -> "${groups.size} duplicate groups detected (${groups.sumOf { it.songs.size }} files)"
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = "Duplicate Tracks",
            subtitle = subtitleText,
            visible = true
        )

        if (groups.isNotEmpty()) {
            val listState = rememberLazyListState()
            AdaptiveSheetScrollContainer(
                lazyListState = listState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 420.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(groups, key = { _, g -> "${g.title}|${g.artist}|${g.recommendedKeepId}" }) { groupIdx, group ->
                        Card(
                            shape = groupedBottomSheetItemShape(groupIdx, groups.size),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${group.title} — ${group.artist}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    group.songs.forEachIndexed { copyIdx, copy ->
                                        val isBest = copy.id == group.recommendedKeepId
                                        val specs = listOfNotNull(
                                            copy.codec?.takeIf { it.isNotBlank() },
                                            copy.sampleRate?.let { "${it / 1000} kHz" },
                                            copy.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
                                        ).joinToString(" • ").ifBlank { "Audio file" }
                                        val itemShape = groupedBottomSheetItemShape(copyIdx, group.songs.size)

                                        Card(
                                            shape = itemShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isBest) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(itemShape)
                                                .clickable {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    onPlaySong(copy)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = specs,
                                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                            color = if (isBest) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1
                                                        )
                                                        if (isBest) {
                                                            Text(
                                                                text = "KEEP BEST",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = copy.path ?: copy.album,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isBest) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (!isBest) {
                                                    IconButton(onClick = { pendingDelete = copy }) {
                                                        Icon(
                                                            imageVector = RhythmIcons.Delete,
                                                            contentDescription = "Delete duplicate",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                } else {
                                                    Box(Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = MaterialSymbolIcon("verified", filled = true),
                                                        contentDescription = "Highest quality copy",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (!isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveCookieEmptyState(
                    title = "No duplicates found",
                    subtitle = "All ${songs.size} tracks in your library are unique copies.",
                    mainIcon = MaterialSymbolIcon("content_copy", filled = true),
                    accentIcon = RhythmIcons.CheckCircle,
                    cornerIcon = RhythmIcons.MusicNote,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                RhythmGroupedButton(modifier = Modifier.fillMaxWidth(), size = RhythmButtonSize.Large) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onDismiss()
                        },
                        type = RhythmButtonType.Tonal,
                        size = RhythmButtonSize.Large,
                        text = "Close",
                        isFirst = true,
                        isLast = true
                    )
                }
            }
        }
    }

    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete duplicate copy?") },
            text = { Text("Remove \"${song.title}\" (${song.path ?: song.album}) from your device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSong(song)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
