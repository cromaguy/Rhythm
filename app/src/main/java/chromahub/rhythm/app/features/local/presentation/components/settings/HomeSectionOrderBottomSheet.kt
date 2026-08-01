@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package chromahub.rhythm.app.features.local.presentation.components.settings

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
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.OutlinedButton
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

private fun groupedBottomSheetItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    if (totalCount <= 1) return RoundedCornerShape(24.dp)

    return when (index) {
        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
fun HomeSectionOrderBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sectionOrder by appSettings.homeSectionOrder.collectAsState()
    
    // Collect all visibility states
    val showRecentlyPlayed by appSettings.homeShowRecentlyPlayed.collectAsState()
    val showDiscoverCarousel by appSettings.homeShowDiscoverCarousel.collectAsState()
    val showArtists by appSettings.homeShowArtists.collectAsState()
    val showNewReleases by appSettings.homeShowNewReleases.collectAsState()
    val showRecentlyAdded by appSettings.homeShowRecentlyAdded.collectAsState()
    val showRecommended by appSettings.homeShowRecommended.collectAsState()
    val showListeningStats by appSettings.homeShowListeningStats.collectAsState()
    val showRhythmGuard by appSettings.homeShowRhythmGuard.collectAsState()
    
    // Fixed sections: DISCOVER always first (not reorderable)
    val fixedSections = setOf("GREETING", "DISCOVER", "MOOD")
    var reorderableList by remember { mutableStateOf(sectionOrder.filter { it !in fixedSections }.toList()) }
    var visibilityMap by remember {
        mutableStateOf(
            mapOf(
                "RECENTLY_PLAYED" to showRecentlyPlayed,
                "DISCOVER" to showDiscoverCarousel,
                "ARTISTS" to showArtists,
                "NEW_RELEASES" to showNewReleases,
                "RECENTLY_ADDED" to showRecentlyAdded,
                "RECOMMENDED" to showRecommended,
                "RHYTHM_GUARD" to showRhythmGuard,
                "STATS" to showListeningStats
            )
        )
    }
    
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    
    // Helper function to get display name and icon for section
    fun getSectionInfo(sectionId: String): Pair<String, MaterialSymbolIcon> {
        return when (sectionId) {
            "GREETING" -> Pair("Greeting", RhythmIcons.WavingHand)
            "RECENTLY_PLAYED" -> Pair("Recently Played", MaterialSymbolIcon("history"))
            "DISCOVER" -> Pair("Discover Carousel", MaterialSymbolIcon("explore"))
            "ARTISTS" -> Pair("Top Artists", RhythmIcons.Artist)
            "NEW_RELEASES" -> Pair("New Releases", MaterialSymbolIcon("new_releases"))
            "RECENTLY_ADDED" -> Pair("Recently Added", RhythmIcons.Music.Album)
            "RECOMMENDED" -> Pair("Recommended", MaterialSymbolIcon("recommend"))
            "RHYTHM_GUARD" -> Pair("Rhythm Guard", RhythmIcons.Security)
            "STATS" -> Pair("Listening Stats", MaterialSymbolIcon("insert_chart"))
            else -> Pair(sectionId, RhythmIcons.Music.Song)
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
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
                val (discoverName, discoverIcon) = getSectionInfo("DISCOVER")
                val isDiscoverVisible = visibilityMap["DISCOVER"] ?: true
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
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
                            // Fixed position indicator
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
                                imageVector = discoverIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = discoverName,
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
                        
                        // Visibility toggle only (no reorder buttons)
                        IconButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                visibilityMap = visibilityMap.toMutableMap().apply {
                                    this["DISCOVER"] = !isDiscoverVisible
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isDiscoverVisible) RhythmIcons.Visibility else RhythmIcons.VisibilityOff,
                                contentDescription = if (isDiscoverVisible) "Hide carousel" else "Show carousel",
                                tint = if (isDiscoverVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                            // Position indicator
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
                            
                            // Section icon
                            Icon(
                                imageVector = sectionIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            
                            // Section name
                            Text(
                                text = sectionName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Visibility toggle and drag handle
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isVisible = visibilityMap[sectionId] ?: true
                            val visibleSectionsCount = visibilityMap.values.count { it }
                            
                            IconButton(
                                onClick = {
                                    // Prevent hiding the last visible section
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
        
        // Sticky action buttons at bottom
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
                // Reset button
                RhythmButtonWeighted(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        val defaultOrder = listOf(
                            "RECENTLY_PLAYED", "ARTISTS", "RHYTHM_GUARD",
                            "NEW_RELEASES", "RECENTLY_ADDED", "RECOMMENDED", "STATS"
                        )
                        reorderableList = defaultOrder
                        visibilityMap = mapOf(
                            "RECENTLY_PLAYED" to true,
                            "DISCOVER" to true,
                            "ARTISTS" to true,
                            "RHYTHM_GUARD" to true,
                            "NEW_RELEASES" to true,
                            "RECENTLY_ADDED" to true,
                            "RECOMMENDED" to true,
                            "STATS" to true
                        )
                        Toast.makeText(context, R.string.homesectionorderbottomsheet_section_order_and_visibility, Toast.LENGTH_SHORT).show()
                    },
                    weight = 1f,
                    isFirst = true,
                    icon = MaterialSymbolIcon("restart_alt"),
                    text = context.getString(R.string.bottomsheet_reset)
                )

                // Save button
                RhythmButtonWeighted(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)

                        // Save section order (DISCOVER first, then user-ordered sections)
                        val finalOrder = listOf("DISCOVER") + reorderableList
                        appSettings.setHomeSectionOrder(finalOrder)

                        // Save visibility for each section.
                        appSettings.setHomeShowRecentlyPlayed(visibilityMap["RECENTLY_PLAYED"] ?: true)
                        appSettings.setHomeShowDiscoverCarousel(visibilityMap["DISCOVER"] ?: true)
                        appSettings.setHomeShowArtists(visibilityMap["ARTISTS"] ?: true)
                        appSettings.setHomeShowNewReleases(visibilityMap["NEW_RELEASES"] ?: true)
                        appSettings.setHomeShowRecentlyAdded(visibilityMap["RECENTLY_ADDED"] ?: true)
                        appSettings.setHomeShowRecommended(visibilityMap["RECOMMENDED"] ?: true)
                        appSettings.setHomeShowListeningStats(visibilityMap["STATS"] ?: true)
                        
                        val rhythmGuardVisible = visibilityMap["RHYTHM_GUARD"] ?: true
                        appSettings.setHomeShowRhythmGuard(rhythmGuardVisible)

                        Toast.makeText(context, R.string.homesectionorderbottomsheet_home_section_order_and, Toast.LENGTH_SHORT).show()
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
