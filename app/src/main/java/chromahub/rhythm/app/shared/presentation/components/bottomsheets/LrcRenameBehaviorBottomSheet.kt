/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LrcRenameBehaviorBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lrcRenameBehavior by appSettings.lrcRenameBehavior.collectAsState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
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
            title = stringResource(R.string.lyrics_lrc_rename_behavior),
            subtitle = stringResource(R.string.lyrics_lrc_rename_behavior_desc),
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
                val options = listOf(
                    "ask" to Triple(
                        stringResource(R.string.lyrics_rename_ask),
                        stringResource(R.string.lyrics_rename_ask_desc),
                        MaterialSymbolIcon("help_outline")
                    ),
                    "always" to Triple(
                        stringResource(R.string.lyrics_rename_always),
                        stringResource(R.string.lyrics_rename_always_desc),
                        MaterialSymbolIcon("sync")
                    ),
                    "never" to Triple(
                        stringResource(R.string.lyrics_rename_never),
                        stringResource(R.string.lyrics_rename_never_desc),
                        MaterialSymbolIcon("block")
                    )
                )

                options.forEachIndexed { index, (behaviorKey, info) ->
                    val (title, description, icon) = info
                    val isSelected = lrcRenameBehavior == behaviorKey

                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setLrcRenameBehavior(behaviorKey)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, options.size),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primaryContainer,
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
