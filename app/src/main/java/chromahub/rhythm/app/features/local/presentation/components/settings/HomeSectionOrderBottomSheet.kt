/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package chromahub.rhythm.app.features.local.presentation.components.settings

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader

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
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer

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
    
    // Unsupported legacy sections stay hidden; every visible section can move.
    val fixedSections = setOf("GREETING", "MOOD")
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
            "RECENTLY_PLAYED" -> Pair(context.getString(R.string.home_recently_played), MaterialSymbolIcon("history"))
            "DISCOVER" -> Pair(context.getString(R.string.home_section_discover), MaterialSymbolIcon("explore"))
            "ARTISTS" -> Pair(context.getString(R.string.home_top_artists), RhythmIcons.Artist)
            "NEW_RELEASES" -> Pair(context.getString(R.string.home_new_releases), MaterialSymbolIcon("new_releases"))
            "RECENTLY_ADDED" -> Pair(context.getString(R.string.home_recently_added), RhythmIcons.Music.Album)
            "RECOMMENDED" -> Pair(context.getString(R.string.personal_collections_title), MaterialSymbolIcon("recommend"))
            "RHYTHM_GUARD" -> Pair(context.getString(R.string.settings_rhythm_guard), RhythmIcons.Security)
            "STATS" -> Pair(context.getString(R.string.home_listening_stats), MaterialSymbolIcon("insert_chart"))
            else -> Pair(sectionId, RhythmIcons.Music.Song)
        }
    }
    
    val lazyListState = rememberLazyListState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        lazyListState = lazyListState,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        val totalSectionCards = reorderableList.size

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.bottomsheet_home_section_order), style = MaterialTheme.typography.headlineSmall)
            Text(context.getString(R.string.bottomsheet_reorder_toggle_visibility), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Reorderable list using DragDropLazyColumn inside AdaptiveSheetScrollContainer
            AdaptiveSheetScrollContainer(
                lazyListState = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { endPadding ->
                DragDropLazyColumn(
                    items = reorderableList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp + endPadding),
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
                    shape = groupedBottomSheetItemShape(index, totalSectionCards)
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
                                    contentDescription = context.getString(if (isVisible) R.string.personal_hide_section else R.string.personal_show_section),
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
                            "RECOMMENDED", "RECENTLY_PLAYED", "DISCOVER", "RECENTLY_ADDED",
                            "ARTISTS", "NEW_RELEASES", "STATS", "RHYTHM_GUARD"
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

                        // Preserve the exact order chosen by the user.
                        val finalOrder = reorderableList
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
