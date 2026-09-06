/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource

private data class ControlAction(
    val icon: MaterialSymbolIcon,
    val label: String,
    val description: String?,
    val containerColor: Color,
    val iconColor: Color,
    val onClick: () -> Unit
)

private fun getGridItemShape(index: Int, totalItems: Int): RoundedCornerShape {
    if (totalItems <= 1) return RoundedCornerShape(24.dp)
    if (totalItems == 2) {
        return if (index == 0) {
            RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
        } else {
            RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
        }
    }
    
    val totalRows = (totalItems + 1) / 2
    val r = index / 2
    val c = index % 2
    
    return when {
        r == 0 -> {
            if (c == 0) {
                RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            }
        }
        r == totalRows - 1 -> {
            if (index == totalItems - 1 && c == 0) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            } else if (c == 0) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            }
        }
        else -> RoundedCornerShape(8.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraControlBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    hiddenChips: Set<String>,
    equalizerEnabled: Boolean,
    sleepTimerActive: Boolean,
    sleepTimerRemainingSeconds: Long,
    lyrics: LyricsData?,
    onAddToPlaylist: () -> Unit,
    onEditControls: (() -> Unit)? = null,
    onPlaybackSpeed: () -> Unit,
    onPlaybackPitch: () -> Unit = {},
    onEqualizer: () -> Unit,
    onSleepTimer: () -> Unit,
    onLyricsEditor: () -> Unit,
    onAlbum: () -> Unit = {},
    onArtist: () -> Unit = {},
    onSongInfo: () -> Unit,
    onShareFile: () -> Unit = {},
    haptic: HapticFeedback,
    isExtraSmallWidth: Boolean = false,
    isCompactWidth: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun dismissAndDo(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val onSecondary = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiary = MaterialTheme.colorScheme.onTertiaryContainer

    val actions = buildList {
        onEditControls?.let { editControls ->
            add(ControlAction(
                icon = RhythmIcons.Edit,
                label = context.getString(R.string.bottomsheet_edit_controls),
                description = null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { editControls() }
                }
            ))
        }

        add(ControlAction(
            icon = RhythmIcons.AddToPlaylist,
            label = context.getString(R.string.bottomsheet_add_to_playlist),
            description = null,
            containerColor = secondary,
            iconColor = onSecondary,
            onClick = {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                dismissAndDo { onAddToPlaylist() }
            }
        ))

        if ("SPEED" !in hiddenChips || "PITCH" !in hiddenChips) {
            add(ControlAction(
                icon = MaterialSymbolIcon("tune", filled = true),
                label = context.getString(R.string.player_speed_and_pitch),
                description = context.getString(R.string.extrasheet_tempo_pitch),
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onPlaybackSpeed() }
                }
            ))
        }

        if ("EQUALIZER" !in hiddenChips) {
            add(ControlAction(
                icon = MaterialSymbolIcon("graphic_eq", filled = true),
                label = context.getString(R.string.equalizer),
                description = if (equalizerEnabled) context.getString(R.string.status_enabled) else context.getString(R.string.status_disabled),
                containerColor = if (equalizerEnabled) tertiary else secondary,
                iconColor = if (equalizerEnabled) onTertiary else onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onEqualizer() }
                }
            ))
        }

        if ("SLEEP_TIMER" !in hiddenChips) {                val sleepLabel = if (sleepTimerActive) {
                val m = sleepTimerRemainingSeconds / 60
                val s = sleepTimerRemainingSeconds % 60
                "${m}:${s.toString().padStart(2, '0')}"
            } else context.getString(R.string.status_disabled)
            add(ControlAction(
                icon = RhythmIcons.AccessTime,
                label = context.getString(R.string.sleep_timer),
                description = sleepLabel,
                containerColor = if (sleepTimerActive) tertiary else secondary,
                iconColor = if (sleepTimerActive) onTertiary else onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onSleepTimer() }
                }
            ))
        }

        if ("LYRICS" !in hiddenChips) {
            val hasLyrics = lyrics != null && lyrics.hasLyrics() && !lyrics.isErrorMessage()
            add(ControlAction(
                icon = if (hasLyrics) RhythmIcons.Edit else MaterialSymbolIcon("lyrics", filled = true),
                label = if (hasLyrics) context.getString(R.string.action_edit_lyrics) else context.getString(R.string.action_add_lyrics),
                description = if (hasLyrics) context.getString(R.string.extrasheet_has_lyrics) else null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onLyricsEditor() }
                }
            ))
        }

        if ("ALBUM" !in hiddenChips) {
            add(ControlAction(
                icon = RhythmIcons.AlbumFilled,
                label = context.getString(R.string.multiselectionbottomsheet_go_to_album),
                description = null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onAlbum() }
                }
            ))
        }

        if ("ARTIST" !in hiddenChips) {
            add(ControlAction(
                icon = RhythmIcons.ArtistFilled,
                label = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                description = null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onArtist() }
                }
            ))
        }

        add(ControlAction(
            icon = RhythmIcons.Info,
            label = context.getString(R.string.action_song_info),
            description = null,
            containerColor = secondary,
            iconColor = onSecondary,
            onClick = {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                dismissAndDo { onSongInfo() }
            }
        ))

        add(ControlAction(
            icon = RhythmIcons.Share,
            label = context.getString(R.string.extrasheet_share_file),
            description = null,
            containerColor = secondary,
            iconColor = onSecondary,
            onClick = {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                dismissAndDo { onShareFile() }
            }
        ))
    }

    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        scrollState = scrollState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header — matches SongOptionsBottomSheet style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_shapes_player_controls),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        text = stringResource(R.string.libraryscreen_more_actions),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Grouped status grid layout matching RhythmGuardTrendsRow
            AdaptiveSheetScrollContainer(
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) { endPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 8.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    actions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowActions.forEachIndexed { colIndex, action ->
                                val overallIndex = rowIndex * 2 + colIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    ControlGridItem(
                                        icon = action.icon,
                                        text = action.label,
                                        description = action.description,
                                        containerColor = action.containerColor,
                                        iconColor = action.iconColor,
                                        shape = getGridItemShape(overallIndex, actions.size),
                                        onClick = action.onClick,
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                            if (rowActions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ControlGridItem(
    icon: MaterialSymbolIcon,
    text: String,
    description: String?,
    containerColor: Color,
    iconColor: Color,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.25f),
                tonalElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
