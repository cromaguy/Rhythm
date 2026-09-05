/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package chromahub.rhythm.app.shared.presentation.components.player

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.DragDropLazyColumn
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlinx.coroutines.launch

private fun groupedButtonItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(8.dp)
    }
}

private data class ButtonDescriptor(
    val title: String,
    val icon: MaterialSymbolIcon
)

private val fixedBottomButtonsNormal = listOf("LYRICS", "FAVORITE")

private fun restoreFixedButtonsNormal(original: List<String>, editable: List<String>): List<String> {
    val result = editable.toMutableList()
    var inserted = 0
    original.forEach { item ->
        if (item in fixedBottomButtonsNormal) {
            val editableBefore = original.take(original.indexOf(item)).count { it !in fixedBottomButtonsNormal }
            val insertAt = (editableBefore + inserted).coerceAtMost(result.size)
            if (item !in result) {
                result.add(insertAt, item)
                inserted++
            }
        }
    }
    return result
}

@Composable
fun ExpressiveBottomButtonsOrderBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    initialModeIndex: Int = 0
) {
    val context = LocalContext.current
    val normalOrder by appSettings.expressiveBottomButtonsNormal.collectAsState()
    val hiddenNormal by appSettings.expressiveHiddenBottomButtonsNormal.collectAsState()
    val mergeOrder by appSettings.expressiveBottomButtonsMerge.collectAsState()
    val hiddenMerge by appSettings.expressiveHiddenBottomButtonsMerge.collectAsState()

    var selectedModeIndex by remember { mutableIntStateOf(initialModeIndex.coerceIn(0, 1)) }

    val editableNormalBottomButtons = appSettings.allExpressiveBottomButtons.filterNot { it in fixedBottomButtonsNormal }
    val editableMergeBottomButtons = appSettings.allExpressiveBottomButtons

    val fullNormalList = remember(normalOrder) {
        val list = normalOrder.filterNot { it in fixedBottomButtonsNormal }.toMutableList()
        editableNormalBottomButtons.forEach { btn ->
            if (!list.contains(btn)) list.add(btn)
        }
        list
    }
    val fullMergeList = remember(mergeOrder) {
        val list = mergeOrder.toMutableList()
        appSettings.defaultExpressiveBottomButtonsMerge.forEachIndexed { defaultIndex, button ->
            if (!list.contains(button)) {
                list.add(defaultIndex.coerceAtMost(list.size), button)
            }
        }
        editableMergeBottomButtons.forEach { btn ->
            if (!list.contains(btn)) list.add(btn)
        }
        list
    }

    var reorderableNormalList by remember { mutableStateOf(fullNormalList) }
    var hiddenNormalSet by remember {
        val initiallyHidden = hiddenNormal.toMutableSet()
        editableNormalBottomButtons.forEach { btn ->
            if (!normalOrder.contains(btn)) {
                initiallyHidden.add(btn)
            }
        }
        mutableStateOf(initiallyHidden.toSet())
    }

    var reorderableMergeList by remember { mutableStateOf(fullMergeList) }
    var hiddenMergeSet by remember {
        val initiallyHidden = hiddenMerge.toMutableSet()
        val activeMergeButtons = mergeOrder.toSet() + appSettings.defaultExpressiveBottomButtonsMerge.toSet()
        editableMergeBottomButtons.forEach { btn ->
            if (!activeMergeButtons.contains(btn)) {
                initiallyHidden.add(btn)
            }
        }
        mutableStateOf(initiallyHidden.toSet())
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val scope = rememberCoroutineScope()

    fun getButtonDescriptor(buttonId: String): ButtonDescriptor {
        return when (buttonId) {
            "LYRICS" -> ButtonDescriptor(
                title = context.getString(R.string.player_chip_lyrics),
                icon = RhythmIcons.Player.Lyrics
            )
            "FAVORITE" -> ButtonDescriptor(
                title = context.getString(R.string.expressiveplayerscreen_favorite),
                icon = MaterialSymbolIcon("thumb_up", filled = true)
            )
            "DEVICE" -> ButtonDescriptor(
                title = context.getString(R.string.expressiveplayerscreen_device),
                icon = RhythmIcons.SpeakerFilled
            )
            "QUEUE" -> ButtonDescriptor(
                title = context.getString(R.string.bottomsheet_queue),
                icon = RhythmIcons.Queue
            )
            "MORE" -> ButtonDescriptor(
                title = context.getString(R.string.libraryscreen_more_actions),
                icon = RhythmIcons.More
            )
            "SHUFFLE" -> ButtonDescriptor(
                title = context.getString(R.string.action_shuffle),
                icon = RhythmIcons.Player.Shuffle
            )
            "REPEAT" -> ButtonDescriptor(
                title = context.getString(R.string.player_chip_repeat),
                icon = RhythmIcons.Player.Repeat
            )
            "EQUALIZER" -> ButtonDescriptor(
                title = context.getString(R.string.equalizer),
                icon = MaterialSymbolIcon("graphic_eq", filled = true)
            )
            "SPEED" -> ButtonDescriptor(
                title = context.getString(R.string.player_chip_speed),
                icon = MaterialSymbolIcon("tune", filled = true)
            )
            "SLEEP_TIMER" -> ButtonDescriptor(
                title = context.getString(R.string.sleep_timer),
                icon = RhythmIcons.AccessTime
            )
            "ADD_TO_PLAYLIST" -> ButtonDescriptor(
                title = context.getString(R.string.bottomsheet_add_to_playlist),
                icon = RhythmIcons.AddToPlaylist
            )
            "ALBUM" -> ButtonDescriptor(
                title = context.getString(R.string.player_chip_album),
                icon = RhythmIcons.Music.Album
            )
            "ARTIST" -> ButtonDescriptor(
                title = context.getString(R.string.player_chip_artist),
                icon = RhythmIcons.Music.Artist
            )
            "SONG_INFO" -> ButtonDescriptor(
                title = context.getString(R.string.action_song_info),
                icon = RhythmIcons.Info
            )
            "SHARE" -> ButtonDescriptor(
                title = context.getString(R.string.extrasheet_share_file),
                icon = RhythmIcons.Share
            )
            else -> ButtonDescriptor(
                title = buttonId,
                icon = RhythmIcons.Edit
            )
        }
    }

    val lazyListState = rememberLazyListState()
    val isNormalMode = selectedModeIndex == 0
    val activeList = if (isNormalMode) reorderableNormalList else reorderableMergeList
    val activeHiddenSet = if (isNormalMode) hiddenNormalSet else hiddenMergeSet

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
        StandardBottomSheetHeader(
            title = context.getString(R.string.expressive_bottom_buttons_title),
            subtitle = context.getString(R.string.expressive_bottom_buttons_desc),
            visible = true
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            // Mode Switcher Tabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                ExpressiveButtonGroup(
                    items = listOf(
                        stringResource(R.string.expressive_bottom_buttons_normal_tab),
                        stringResource(R.string.expressive_bottom_buttons_merge_tab)
                    ),
                    selectedIndex = selectedModeIndex,
                    onItemClick = { index ->
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        selectedModeIndex = index
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Reorderable list inside AdaptiveSheetScrollContainer
            AdaptiveSheetScrollContainer(
                lazyListState = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { endPadding ->
                DragDropLazyColumn(
                    items = activeList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp + endPadding),
                    lazyListState = lazyListState,
                    onMove = { fromIndex, toIndex ->
                        val newList = activeList.toMutableList()
                        val item = newList.removeAt(fromIndex)
                        newList.add(toIndex, item)
                        if (isNormalMode) {
                            reorderableNormalList = newList
                        } else {
                            reorderableMergeList = newList
                        }
                    },
                    itemKey = { "${selectedModeIndex}_$it" }
                ) { buttonId, isDragging, index ->
                    val descriptor = getButtonDescriptor(buttonId)
                    val isHidden = activeHiddenSet.contains(buttonId)
                    val visibleButtonsCount = activeList.count { !activeHiddenSet.contains(it) }

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
                        shape = groupedButtonItemShape(index, activeList.size)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isHidden)
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    else
                                        MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHidden)
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = descriptor.icon,
                                    contentDescription = null,
                                    tint = if (isHidden)
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = descriptor.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isHidden)
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!isHidden && visibleButtonsCount <= 1) {
                                            Toast.makeText(
                                                context,
                                                R.string.expressive_bottom_buttons_at_least_one,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@IconButton
                                        }
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        if (isNormalMode) {
                                            hiddenNormalSet = if (isHidden) {
                                                hiddenNormalSet - buttonId
                                            } else {
                                                hiddenNormalSet + buttonId
                                            }
                                        } else {
                                            hiddenMergeSet = if (isHidden) {
                                                hiddenMergeSet - buttonId
                                            } else {
                                                hiddenMergeSet + buttonId
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) RhythmIcons.VisibilityOff else RhythmIcons.Visibility,
                                        contentDescription = if (isHidden) "Show button" else "Hide button",
                                        tint = if (isHidden)
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

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

            // Sticky Footer with Reset and Save buttons
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
                    // Reset button (resets currently selected mode)
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            if (isNormalMode) {
                                appSettings.resetExpressiveBottomButtonsNormal()
                                val defaultNormal = appSettings.defaultExpressiveBottomButtonsNormal.filterNot { it in fixedBottomButtonsNormal }
                                val full = defaultNormal.toMutableList()
                                editableNormalBottomButtons.forEach { if (!full.contains(it)) full.add(it) }
                                reorderableNormalList = full
                                hiddenNormalSet = editableNormalBottomButtons.filter { !defaultNormal.contains(it) }.toSet()
                            } else {
                                appSettings.resetExpressiveBottomButtonsMerge()
                                val defaultMerge = appSettings.defaultExpressiveBottomButtonsMerge
                                val full = defaultMerge.toMutableList()
                                editableMergeBottomButtons.forEach { if (!full.contains(it)) full.add(it) }
                                reorderableMergeList = full
                                hiddenMergeSet = editableMergeBottomButtons.filter { !defaultMerge.contains(it) }.toSet()
                            }
                            Toast.makeText(context, R.string.expressive_bottom_buttons_reset, Toast.LENGTH_SHORT).show()
                        },
                        weight = 1f,
                        isFirst = true,
                        icon = MaterialSymbolIcon("restart_alt"),
                        text = context.getString(R.string.bottomsheet_reset)
                    )

                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            appSettings.setExpressiveBottomButtonsNormal(restoreFixedButtonsNormal(fullNormalList, reorderableNormalList))
                            appSettings.setExpressiveHiddenBottomButtonsNormal(hiddenNormalSet - fixedBottomButtonsNormal.toSet())
                            appSettings.setExpressiveBottomButtonsMerge(reorderableMergeList)
                            appSettings.setExpressiveHiddenBottomButtonsMerge(hiddenMergeSet)
                            Toast.makeText(context, R.string.expressive_bottom_buttons_saved, Toast.LENGTH_SHORT).show()
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
