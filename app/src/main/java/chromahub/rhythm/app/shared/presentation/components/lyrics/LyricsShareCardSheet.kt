/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmToggleButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.RhythmToggleOption
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()

    LaunchedEffect(Unit) { sheetState.expand() }

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
            title = "Share Lyrics Card",
            subtitle = "Select 1 to 4 lines (${selectedIndices.size}/4)",
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            previewBitmap?.let { bmp ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(210.dp),
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
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RhythmToggleButtonGroup(
                        options = listOf(
                            RhythmToggleOption(text = "Song Card", icon = MaterialSymbolIcon("album", filled = true)),
                            RhythmToggleOption(text = "Quote", icon = MaterialSymbolIcon("format_quote", filled = true))
                        ),
                        selectedIndices = setOf(if (cardMode == ShareCardMode.SONG_CARD) 0 else 1),
                        onToggle = { index ->
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            cardMode = if (index == 0) ShareCardMode.SONG_CARD else ShareCardMode.QUOTE_CARD
                        },
                        size = RhythmButtonSize.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        swatches.forEachIndexed { idx, swatch ->
                            val isPicked = idx == selectedSwatchIndex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch.backgroundArgb))
                                    .border(
                                        width = if (isPicked) 3.dp else 1.dp,
                                        color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        selectedSwatchIndex = idx
                                    }
                            )
                        }
                    }
                }
            }
        }

        val listState = rememberLazyListState()
        AdaptiveSheetScrollContainer(
            lazyListState = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) { endPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(shareableLines) { idx, item ->
                    val isSelected = selectedIndices.contains(idx)
                    val shape = groupedBottomSheetItemShape(idx, shareableLines.size)
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            if (isSelected) {
                                if (selectedIndices.size > 1) selectedIndices.remove(idx)
                            } else if (selectedIndices.size < 4) {
                                selectedIndices.add(idx)
                            } else {
                                selectedIndices.removeAt(0)
                                selectedIndices.add(idx)
                            }
                        },
                        shape = shape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
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
                        text = "Cancel",
                        isFirst = true,
                        isLast = false
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            val bmp = previewBitmap ?: return@RhythmButtonWeighted
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
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
                        type = RhythmButtonType.Filled,
                        size = RhythmButtonSize.Large,
                        text = "Share Card",
                        icon = MaterialSymbolIcon("share"),
                        isFirst = false,
                        isLast = true
                    )
                }
            }
        }
    }
}
