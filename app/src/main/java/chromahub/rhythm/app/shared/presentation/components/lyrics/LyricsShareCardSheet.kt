/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package chromahub.rhythm.app.shared.presentation.components.lyrics

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LyricsShareCardSheet(
    song: Song?,
    lyrics: LyricsData?,
    currentPositionMs: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()

    val shareableLines = remember(lyrics) {
        LyricsShareCardRenderer.extractShareableLines(lyrics)
    }

    val initialRange = remember(shareableLines, currentPositionMs) {
        LyricsShareCardRenderer.defaultSelectionRange(shareableLines.size, currentPositionMs, shareableLines)
    }
    val selectedIndices = remember(shareableLines) {
        mutableStateListOf<Int>().apply {
            addAll(initialRange.toList().take(4))
        }
    }

    var cardMode by remember { mutableStateOf(ShareCardMode.SONG_CARD) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song?.artworkUri) {
        coverBitmap = withContext(Dispatchers.IO) {
            LyricsShareCardRenderer.loadCoverBitmap(context, song?.artworkUri)
        }
    }

    val swatches = remember(coverBitmap, primaryArgb) {
        LyricsShareCardRenderer.extractSwatches(coverBitmap, primaryArgb)
    }
    var selectedSwatchIndex by remember(swatches) { mutableIntStateOf(0) }
    val activeSwatch = swatches.getOrElse(selectedSwatchIndex) { swatches.first() }

    val selectedTexts = remember(selectedIndices.toList(), shareableLines) {
        selectedIndices
            .distinct()
            .sorted()
            .mapNotNull { shareableLines.getOrNull(it)?.text }
            .ifEmpty { listOfNotNull(shareableLines.firstOrNull()?.text) }
    }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(song?.title, song?.artist, song?.album, selectedTexts, cardMode, activeSwatch, coverBitmap) {
        if (selectedTexts.isNotEmpty()) {
            previewBitmap = LyricsShareCardRenderer.renderCardBitmap(
                songTitle = song?.title ?: "Unknown Title",
                artistName = song?.artist ?: "Unknown Artist",
                albumName = song?.album,
                selectedLines = selectedTexts,
                mode = cardMode,
                swatch = activeSwatch,
                coverBitmap = coverBitmap
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Share Lyrics Card",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            previewBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = cardMode == ShareCardMode.SONG_CARD,
                    onClick = { cardMode = ShareCardMode.SONG_CARD },
                    label = { Text("Song Card") }
                )
                FilterChip(
                    selected = cardMode == ShareCardMode.QUOTE_CARD,
                    onClick = { cardMode = ShareCardMode.QUOTE_CARD },
                    label = { Text("Quote") }
                )
                Spacer(modifier = Modifier.width(4.dp))
                swatches.forEachIndexed { idx, swatch ->
                    val isPicked = idx == selectedSwatchIndex
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(swatch.backgroundArgb))
                            .border(
                                width = if (isPicked) 2.5.dp else 1.dp,
                                color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { selectedSwatchIndex = idx }
                    )
                }
            }

            Text(
                text = "Select 1 to 4 lines (${selectedIndices.size}/4)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 190.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(shareableLines) { idx, item ->
                    val isSelected = selectedIndices.contains(idx)
                    Surface(
                        onClick = {
                            if (isSelected) {
                                if (selectedIndices.size > 1) selectedIndices.remove(idx)
                            } else if (selectedIndices.size < 4) {
                                selectedIndices.add(idx)
                            } else {
                                selectedIndices.removeAt(0)
                                selectedIndices.add(idx)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = item.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val bmp = previewBitmap ?: return@Button
                    scope.launch {
                        val intent = LyricsShareCardRenderer.exportAndShareCard(
                            context = context,
                            bitmap = bmp,
                            songTitle = song?.title ?: "Lyrics"
                        )
                        if (intent != null) {
                            context.startActivity(Intent.createChooser(intent, "Share Lyrics Card"))
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Unable to export lyrics card", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = previewBitmap != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("share"),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Card")
            }
        }
    }
}
