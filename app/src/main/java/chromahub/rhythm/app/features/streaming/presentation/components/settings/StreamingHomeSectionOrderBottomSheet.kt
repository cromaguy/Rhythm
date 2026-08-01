@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package chromahub.rhythm.app.features.streaming.presentation.components.settings

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import chromahub.rhythm.app.shared.presentation.components.common.DragDropLazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

private const val STREAMING_SECTION_DISCOVER = "DISCOVER"
private const val STREAMING_SECTION_PLAYLISTS = "PLAYLISTS"
private const val STREAMING_SECTION_ARTISTS = "ARTISTS"
private const val STREAMING_SECTION_RHYTHM_GUARD = "RHYTHM_GUARD"
private const val STREAMING_SECTION_RHYTHM_STATS = "RHYTHM_STATS"
private const val STREAMING_SECTION_RECENTLY_PLAYED = "RECENTLY_PLAYED"
private const val STREAMING_SECTION_NEW_RELEASES = "NEW_RELEASES"

private val defaultStreamingReorderableSections = listOf(
    STREAMING_SECTION_RECENTLY_PLAYED,
    STREAMING_SECTION_ARTISTS,
    STREAMING_SECTION_PLAYLISTS,
    STREAMING_SECTION_RHYTHM_GUARD,
    STREAMING_SECTION_RHYTHM_STATS,
    STREAMING_SECTION_NEW_RELEASES
)

private fun groupedBottomSheetItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    if (totalCount <= 1) return RoundedCornerShape(24.dp)

    return when (index) {
        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
fun StreamingHomeSectionOrderBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val sectionOrder by appSettings.streamingHomeSectionOrder.collectAsState()
    val showDiscover by appSettings.streamingHomeShowRecommended.collectAsState()
    val showArtists by appSettings.streamingHomeShowArtists.collectAsState()
    val showRhythmGuard by appSettings.streamingHomeShowRhythmGuard.collectAsState()
    val showRhythmStats by appSettings.streamingHomeShowRhythmStats.collectAsState()
    val showRecentlyPlayed by appSettings.streamingHomeShowRecentlyPlayed.collectAsState()
    val showNewReleases by appSettings.streamingHomeShowNewReleases.collectAsState()
    val showPlaylists by appSettings.streamingHomeShowPlaylists.collectAsState()

    val fixedSections = setOf("GREETING", STREAMING_SECTION_DISCOVER)
    var reorderableList by remember(sectionOrder) {
        mutableStateOf(sectionOrder.filter { it !in fixedSections }.toList())
    }
    var visibilityMap by remember(
        showDiscover,
        showArtists,
        showPlaylists,
        showRhythmGuard,
        showRhythmStats,
        showRecentlyPlayed,
        showNewReleases
    ) {
        mutableStateOf(
            mapOf(
                STREAMING_SECTION_DISCOVER to showDiscover,
                STREAMING_SECTION_ARTISTS to showArtists,
                STREAMING_SECTION_PLAYLISTS to showPlaylists,
                STREAMING_SECTION_RHYTHM_GUARD to showRhythmGuard,
                STREAMING_SECTION_RHYTHM_STATS to showRhythmStats,
                STREAMING_SECTION_RECENTLY_PLAYED to showRecentlyPlayed,
                STREAMING_SECTION_NEW_RELEASES to showNewReleases
            )
        )
    }

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()

    fun getSectionInfo(sectionId: String): Pair<String, MaterialSymbolIcon> {
        return when (sectionId) {
            STREAMING_SECTION_DISCOVER -> context.getString(R.string.home_section_discover) to MaterialSymbolIcon("recommend")
            STREAMING_SECTION_ARTISTS -> context.getString(R.string.home_top_artists) to RhythmIcons.ArtistFilled
            STREAMING_SECTION_PLAYLISTS -> context.getString(R.string.settings_playlists) to RhythmIcons.Music.Album
            STREAMING_SECTION_RHYTHM_GUARD -> context.getString(R.string.settings_rhythm_guard) to RhythmIcons.Security
            STREAMING_SECTION_RHYTHM_STATS -> context.getString(R.string.settings_rhythm_stats) to MaterialSymbolIcon("auto_graph", filled = true)
            STREAMING_SECTION_RECENTLY_PLAYED -> context.getString(R.string.home_section_recently_played) to MaterialSymbolIcon("history")
            STREAMING_SECTION_NEW_RELEASES -> context.getString(R.string.home_section_new_releases) to MaterialSymbolIcon("new_releases", filled = true)
            else -> sectionId to MaterialSymbolIcon("recommend")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        val totalSectionCards = reorderableList.size + 1

        Column(modifier = Modifier.fillMaxWidth()) {
            // Header content (Fixed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.bottomsheet_home_section_order),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                )
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                text = context.getString(R.string.bottomsheet_reorder_toggle_visibility),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Fixed Discover Carousel section (always first, not reorderable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val (sectionName, sectionIcon) = getSectionInfo(STREAMING_SECTION_DISCOVER)
                val isVisible = visibilityMap[STREAMING_SECTION_DISCOVER] ?: true
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shape = groupedBottomSheetItemShape(0, totalSectionCards)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = RhythmIcons.Pushpin,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Icon(
                                imageVector = sectionIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sectionName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.homesectionorderbottomsheet_fixed_position),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                visibilityMap = visibilityMap.toMutableMap().apply {
                                    this[STREAMING_SECTION_DISCOVER] = !isVisible
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isVisible) RhythmIcons.Visibility else RhythmIcons.VisibilityOff,
                                contentDescription = if (isVisible) "Hide section" else "Show section",
                                tint = if (isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Reorderable list using DragDropLazyColumn
            val lazyListState = rememberLazyListState()
            DragDropLazyColumn(
                items = reorderableList,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                lazyListState = lazyListState,
                onMove = { fromIndex, toIndex ->
                    val newList = reorderableList.toMutableList()
                    val item = newList.removeAt(fromIndex)
                    newList.add(toIndex, item)
                    reorderableList = newList
                },
                itemKey = { it }
            ) { sectionId, isDragging, index ->
                val (sectionName, sectionIcon) = getSectionInfo(sectionId)
                val isVisible = visibilityMap[sectionId] ?: true
                val visibleSectionsCount = visibilityMap.values.count { it }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragging) 
                            MaterialTheme.colorScheme.secondaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = groupedBottomSheetItemShape(index + 1, totalSectionCards)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Icon(
                                imageVector = sectionIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sectionName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = context.getString(R.string.bottomsheet_reorder_toggle_visibility),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Visibility toggle and drag handle
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isVisible && visibleSectionsCount <= 1) {
                                        Toast.makeText(context, R.string.home_section_one_visible, Toast.LENGTH_SHORT).show()
                                        return@IconButton
                                    }
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    visibilityMap = visibilityMap.toMutableMap().apply {
                                        this[sectionId] = !isVisible
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVisible) RhythmIcons.Visibility else RhythmIcons.VisibilityOff,
                                    contentDescription = if (isVisible) "Hide section" else "Show section",
                                    tint = if (isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Drag Handle Icon
                            Icon(
                                imageVector = RhythmIcons.DragHandle,
                                contentDescription = stringResource(R.string.drag_to_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(horizontal = 4.dp)
                            )
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
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            reorderableList = defaultStreamingReorderableSections
                            visibilityMap = mapOf(
                                STREAMING_SECTION_DISCOVER to true,
                                STREAMING_SECTION_ARTISTS to true,
                                STREAMING_SECTION_PLAYLISTS to true,
                                STREAMING_SECTION_RHYTHM_GUARD to true,
                                STREAMING_SECTION_RHYTHM_STATS to true,
                                STREAMING_SECTION_RECENTLY_PLAYED to true,
                                STREAMING_SECTION_NEW_RELEASES to true
                            )
                            Toast.makeText(context, R.string.streaminghomesectionorderbottomsheet_streaming_section_order_and, Toast.LENGTH_SHORT).show()
                        },
                        weight = 1f,
                        isFirst = true,
                        icon = MaterialSymbolIcon("restart_alt"),
                        text = context.getString(R.string.bottomsheet_reset)
                    )

                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            val finalOrder = listOf(STREAMING_SECTION_DISCOVER) + reorderableList
                            appSettings.setStreamingHomeSectionOrder(finalOrder)
                            appSettings.setStreamingHomeShowRecommended(visibilityMap[STREAMING_SECTION_DISCOVER] ?: true)
                            appSettings.setStreamingHomeShowArtists(visibilityMap[STREAMING_SECTION_ARTISTS] ?: true)
                            appSettings.setStreamingHomeShowPlaylists(visibilityMap[STREAMING_SECTION_PLAYLISTS] ?: true)
                            appSettings.setStreamingHomeShowRhythmGuard(visibilityMap[STREAMING_SECTION_RHYTHM_GUARD] ?: true)
                            appSettings.setStreamingHomeShowRhythmStats(visibilityMap[STREAMING_SECTION_RHYTHM_STATS] ?: true)
                            appSettings.setStreamingHomeShowRecentlyPlayed(visibilityMap[STREAMING_SECTION_RECENTLY_PLAYED] ?: true)
                            appSettings.setStreamingHomeShowNewReleases(visibilityMap[STREAMING_SECTION_NEW_RELEASES] ?: true)

                            Toast.makeText(context, R.string.streaminghomesectionorderbottomsheet_streaming_section_order_and_1, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    onDismiss()
                                }
                            }
                        },
                        weight = 1f,
                        isLast = true,
                        icon = RhythmIcons.Check,
                        text = context.getString(R.string.bottomsheet_save)
                    )
                }
            }
    }
}
}
