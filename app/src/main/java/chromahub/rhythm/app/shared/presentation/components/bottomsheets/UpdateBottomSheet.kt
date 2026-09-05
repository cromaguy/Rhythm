/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.foundation.lazy.rememberLazyListState
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppVersion
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updaterViewModel: AppUpdaterViewModel,
    onDismiss: () -> Unit,
    onUpdateClick: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    // Collect update data from the view model
    val latestVersion by updaterViewModel.latestVersion.collectAsState()
    val updateAvailable by updaterViewModel.updateAvailable.collectAsState()
    val downloadProgress by updaterViewModel.downloadProgress.collectAsState()
    val isDownloading by updaterViewModel.isDownloading.collectAsState()
    val downloadedFile by updaterViewModel.downloadedFile.collectAsState()
    val error by updaterViewModel.error.collectAsState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        val updateListState = rememberLazyListState()

        Column(modifier = Modifier.fillMaxWidth()) {
            AdaptiveSheetScrollContainer(
                lazyListState = updateListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { endPadding ->
                LazyColumn(
                    state = updateListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
            // Big card with icon/name in top right and update available text
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp), 
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.rhythm_logo),
                                contentDescription = context.getString(R.string.cd_rhythm_logo),
                                modifier = Modifier.size(45.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(0.dp))
                            
                            Text(
                                text = context.getString(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Update available text (larger)
                        Text(
                            text = context.getString(R.string.update_available_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Version info
            latestVersion?.let { version ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.update_version, version.versionName),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Release date
                        Text(
                            text = stringResource(R.string.released_on, version.releaseDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // APK size if available
                        if (version.apkSize > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = version.apkAssetName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${updaterViewModel.getReadableFileSize(version.apkSize)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // What's new section
                if (version.whatsNew.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.update_whats_new),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }

                    items(version.whatsNew) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = HtmlCompat.fromHtml(item, HtmlCompat.FROM_HTML_MODE_COMPACT).toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            RhythmGroupedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                size = RhythmButtonSize.Large
            ) {
                RhythmButtonWeighted(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onDismiss()
                    },
                    weight = 1f,
                    isFirst = true,
                    isLast = false,
                    type = RhythmButtonType.Tonal,
                    icon = RhythmIcons.Close,
                    text = stringResource(R.string.bottomsheet_lyrics_later)
                )

                RhythmButtonWeighted(
                    onClick = {
                        if (!isDownloading && downloadedFile == null) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            updaterViewModel.downloadUpdate()
                            onUpdateClick(false)
                        } else if (downloadedFile != null) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            updaterViewModel.installDownloadedApk()
                        }
                    },
                    weight = 1f,
                    isFirst = false,
                    isLast = true,
                    type = RhythmButtonType.Filled,
                    containerColor = if (downloadedFile != null)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary,
                    contentColor = if (downloadedFile != null)
                        MaterialTheme.colorScheme.onTertiary
                    else
                        MaterialTheme.colorScheme.onPrimary,
                    enabled = !isDownloading || downloadedFile != null,
                    icon = when {
                        downloadedFile != null -> RhythmIcons.CheckCircle
                        isDownloading -> null
                        else -> RhythmIcons.Download
                    },
                    text = when {
                        downloadedFile != null -> context.getString(R.string.updates_install_update)
                        isDownloading -> "${context.getString(R.string.updates_downloading)} ${downloadProgress.toInt()}%"
                        else -> context.getString(R.string.update_now)
                    },
                    content = if (isDownloading) {
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${context.getString(R.string.updates_downloading)} ${downloadProgress.toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else null
                )
            }
        }
    }
}
}
