/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.LyricsApiPriority
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsApiPriorityBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val savedPriority by appSettings.lyricsApiPriority.collectAsState()
    val apiPriority = if (appSettings.isLyricsApiPrioritySet()) savedPriority else chromahub.rhythm.app.shared.data.model.LyricsApiPriority.BETTERLYRICS_FIRST

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.lyricssourcesettingsscreen_lyrics_api_priority),
            subtitle = stringResource(R.string.lyricssourcesettingsscreen_choose_which_online_lyrics),
            visible = true
        )

        val scrollState = rememberScrollState()

        AdaptiveSheetScrollContainer(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

            // Options
            val priorityOptions = listOf(
                LyricsApiPriority.BETTERLYRICS_FIRST to Pair(
                    "Better Lyrics First",
                    "Word-by-word synchronized lyrics"
                ),
                LyricsApiPriority.LYRICALLY_FIRST to Pair(
                    "Lyrically First",
                    "Word-by-word synchronized lyrics"
                ),
                LyricsApiPriority.LRCLIB_FIRST to Pair(
                    "LRCLib First",
                    "Line-by-line synchronized lyrics"
                ),
                LyricsApiPriority.MUSIC_CRAWLER_FIRST to Pair(
                    context.getString(R.string.catalog_lyrics_source),
                    context.getString(R.string.catalog_lyrics_source_hint)
                )
            )

            val siteOptions = listOf(
                LyricsApiPriority.BUGS_FIRST to Pair("Bugs · 벅스", context.getString(R.string.catalog_site_lyrics_hint)),
                LyricsApiPriority.MELON_FIRST to Pair("Melon · 멜론", context.getString(R.string.catalog_site_lyrics_hint)),
                LyricsApiPriority.GENIE_FIRST to Pair("Genie · 지니", context.getString(R.string.catalog_site_lyrics_hint))
            )
            (priorityOptions + siteOptions).forEachIndexed { index, (priority, info) ->
                val (title, description) = info
                val isSelected = apiPriority == priority

                Card(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        appSettings.setLyricsApiPriority(priority)
                        if (priority in setOf(LyricsApiPriority.MUSIC_CRAWLER_FIRST, LyricsApiPriority.BUGS_FIRST, LyricsApiPriority.MELON_FIRST, LyricsApiPriority.GENIE_FIRST)) appSettings.setMusicCrawlerLyricsApiEnabled(true)
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = groupedBottomSheetItemShape(index, priorityOptions.size + siteOptions.size),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = RhythmIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
