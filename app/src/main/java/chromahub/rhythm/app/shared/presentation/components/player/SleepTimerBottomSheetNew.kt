@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.player

import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel.SleepAction
import chromahub.rhythm.app.shared.presentation.components.common.RhythmWavyProgressLoader
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SleepTimerOption(
    val minutes: Int,
    val label: String,
    val icon: MaterialSymbolIcon
)

private enum class SheetContentState { Presets, Active, InlinePicker }

@Composable
fun SleepTimerBottomSheetNew(
    onDismiss: () -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    musicViewModel: MusicViewModel
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val isTimerActive by musicViewModel.sleepTimerActive.collectAsState()
    val remainingSeconds by musicViewModel.sleepTimerRemainingSeconds.collectAsState()
    val totalTimerSeconds by musicViewModel.sleepTimerTotalSeconds.collectAsState()
    val timerAction by musicViewModel.sleepTimerAction.collectAsState()
    val serviceConnected by musicViewModel.serviceConnected.collectAsState()

    var selectedAction by remember { mutableStateOf(SleepAction.valueOf(timerAction.takeIf { it.isNotBlank() } ?: "FADE_OUT")) }
    var statusMessage by remember { mutableStateOf("") }
    var sheetState by remember { mutableStateOf(SheetContentState.Presets) }

    val timerOptions = listOf(
        SleepTimerOption(5, "5 min", MaterialSymbolIcon("coffee", filled = true)),
        SleepTimerOption(15, "15 min", MaterialSymbolIcon("local_cafe", filled = true)),
        SleepTimerOption(30, "30 min", MaterialSymbolIcon("wb_twilight", filled = true)),
        SleepTimerOption(45, "45 min", MaterialSymbolIcon("bedtime", filled = true)),
        SleepTimerOption(60, "1 hour", MaterialSymbolIcon("nightlight_round", filled = true)),
        SleepTimerOption(90, "1.5 hr", RhythmIcons.DarkMode)
    )

    val actionOptions = listOf(
        Triple(SleepAction.FADE_OUT, "Fade Out", RhythmIcons.VolumeDown),
        Triple(SleepAction.PAUSE, "Pause", RhythmIcons.Pause),
        Triple(SleepAction.STOP, "Stop", RhythmIcons.Stop)
    )

    fun startTimer(minutes: Int) {
        if (!isPlaying || currentSong == null) {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
            return
        }
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
        coroutineScope.launch {
            statusMessage = "Timer Started"
            delay(1500)
            if (statusMessage == "Timer Started") statusMessage = ""
        }
        musicViewModel.startSleepTimer(minutes, selectedAction)
    }

    fun stopTimer() {
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
        musicViewModel.stopSleepTimer()
    }

    fun adjustTime(deltaMinutes: Int) {
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
        val newRemainingSeconds = remainingSeconds + (deltaMinutes * 60)
        
        if (newRemainingSeconds <= 0L) {
            stopTimer() // The LaunchedEffect will handle showing the "Timer Stopped" text
        } else {
            val newMinutes = (newRemainingSeconds / 60) + if (newRemainingSeconds % 60 > 0) 1 else 0
            val msg = if (deltaMinutes > 0) "+$deltaMinutes Minutes" else "$deltaMinutes Minutes"
            coroutineScope.launch {
                statusMessage = msg
                delay(1500)
                if (statusMessage == msg) statusMessage = ""
            }
            musicViewModel.startSleepTimer(newMinutes.toInt(), selectedAction)
        }
    }

    fun formatTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
    }

    // Intercept state changes to play the 'Stopped' animation before swapping content
    LaunchedEffect(isTimerActive) {
        if (isTimerActive && sheetState != SheetContentState.InlinePicker) {
            sheetState = SheetContentState.Active
        } else if (!isTimerActive && sheetState == SheetContentState.Active) {
            statusMessage = "Timer Stopped"
            delay(1500)
            sheetState = SheetContentState.Presets
            statusMessage = ""
        }
    }

    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // ── Header ──────────────────────────────────────────────────────
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
                            text = context.getString(R.string.sleep_timer),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val isErrorState = !isPlaying || !serviceConnected || currentSong == null
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(CircleShape)
                                .background(
                                    color = if (isTimerActive) MaterialTheme.colorScheme.primaryContainer 
                                            else if (isErrorState) MaterialTheme.colorScheme.errorContainer 
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                text = when {
                                    isTimerActive -> "Active"
                                    isErrorState -> "No music playing"
                                    else -> "Set automatic playback control"
                                },
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                color = if (isTimerActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else if (isErrorState) MaterialTheme.colorScheme.onErrorContainer 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Scrollable Content Area ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = sheetState,
                    transitionSpec = {
                        val floatSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                        val offsetSpring = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)

                        (fadeIn(animationSpec = floatSpring) + slideInVertically(
                            animationSpec = offsetSpring,
                            initialOffsetY = { if (targetState == SheetContentState.Active) -it / 6 else it / 6 }
                        )).togetherWith(
                            fadeOut(animationSpec = floatSpring) + slideOutVertically(
                                animationSpec = offsetSpring,
                                targetOffsetY = { if (targetState == SheetContentState.Active) it / 6 else -it / 6 }
                            )
                        )
                    },
                    label = "sleep_timer_content"
                ) { state ->
                    when (state) {
                        // ── Active Timer ───────────────────────────────────────
                        SheetContentState.Active -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // Animated Wavy Card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(208.dp) // Fixed height to prevent jumping (160 progress + 48 padding)
                                        ) {
                                            AnimatedContent(
                                                targetState = statusMessage.isNotEmpty(),
                                                transitionSpec = {
                                                    val floatSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                                    (fadeIn(animationSpec = floatSpring) + scaleIn(animationSpec = floatSpring, initialScale = 0.9f)) togetherWith
                                                    (fadeOut(animationSpec = floatSpring) + scaleOut(animationSpec = floatSpring, targetScale = 0.9f))
                                                },
                                                label = "status_anim"
                                            ) { isShowingMessage ->
                                                if (isShowingMessage) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(24.dp), 
                                                        contentAlignment = Alignment.BottomEnd
                                                    ) {
                                                        Text(
                                                            text = statusMessage,
                                                            style = MaterialTheme.typography.headlineLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                                                            val elapsedSeconds = totalTimerSeconds - remainingSeconds
                                                            val progress = if (totalTimerSeconds > 0L) {
                                                                (elapsedSeconds.toFloat() / totalTimerSeconds).coerceIn(0f, 1f)
                                                            } else 0f

                                                            RhythmWavyProgressLoader(
                                                                progress = progress,
                                                                modifier = Modifier.fillMaxSize(),
                                                                indicatorColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                            ) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(
                                                                        text = formatTime(remainingSeconds),
                                                                        style = MaterialTheme.typography.headlineMedium,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                                    )
                                                                    Text(
                                                                        text = context.getString(R.string.bottomsheet_timer_remaining),
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Quick Adjust Controls
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalButton(
                                            onClick = { adjustTime(-5) },
                                            modifier = Modifier.height(44.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("remove", filled = true), 
                                                contentDescription = null, 
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("5m", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        Spacer(modifier = Modifier.width(20.dp))
                                        
                                        FilledTonalButton(
                                            onClick = { adjustTime(5) },
                                            modifier = Modifier.height(44.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("add", filled = true), 
                                                contentDescription = null, 
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("5m", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    ActionSelectionCard(
                                        selectedAction = selectedAction,
                                        actionOptions = actionOptions,
                                        onActionSelected = { action ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            selectedAction = action
                                            musicViewModel.appSettings.setSleepTimerAction(action.name)
                                        },
                                        context = context
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }

                        // ── Inline Time Picker ─────────────────────────────────
                        SheetContentState.InlinePicker -> {
                            InlineTimePickerContent(
                                onCancel = {
                                    sheetState = if (isTimerActive) SheetContentState.Active else SheetContentState.Presets
                                },
                                onTimeSelected = { hours, minutes ->
                                    val totalMinutes = hours * 60 + minutes
                                    if (totalMinutes > 0) {
                                        startTimer(totalMinutes)
                                    }
                                    sheetState = SheetContentState.Active
                                }
                            )
                        }

                        // ── Presets ────────────────────────────────────────────
                        SheetContentState.Presets -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // Quick Presets
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = MaterialSymbolIcon("timer", filled = true),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = context.getString(R.string.sleep_timer_quick),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                            ) {
                                                items(timerOptions, key = { "timer_${it.minutes}" }) { option ->
                                                    val isTimerAvailable = isPlaying && serviceConnected && currentSong != null
                                                    Card(
                                                        onClick = { if (isTimerAvailable) startTimer(option.minutes) },
                                                        modifier = Modifier.size(width = 80.dp, height = 84.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isTimerAvailable) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.fillMaxSize().padding(10.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = option.icon,
                                                                contentDescription = null,
                                                                tint = if (isTimerAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = option.label,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Medium,
                                                                color = if (isTimerAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                                textAlign = TextAlign.Center,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Custom Timer
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.AccessTime,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = context.getString(R.string.sleep_timer_custom),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = context.getString(R.string.bottomsheet_timer_custom_desc),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            val isCustomTimerAvailable = isPlaying && serviceConnected && currentSong != null
                                            FilledTonalButton(
                                                onClick = {
                                                    if (isCustomTimerAvailable) sheetState = SheetContentState.InlinePicker
                                                },
                                                enabled = isCustomTimerAvailable,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    context.getString(R.string.bottomsheet_timer_custom_title),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    ActionSelectionCard(
                                        selectedAction = selectedAction,
                                        actionOptions = actionOptions,
                                        onActionSelected = { action ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            selectedAction = action
                                            musicViewModel.appSettings.setSleepTimerAction(action.name)
                                        },
                                        context = context
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Fixed Footer ────────────────────────────────────────────────
            if (sheetState == SheetContentState.Active) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    RhythmGroupedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        size = RhythmButtonSize.Large
                    ) {
                        RhythmButtonWeighted(
                            onClick = { stopTimer() },
                            weight = 1f,
                            isFirst = true,
                            icon = RhythmIcons.Stop,
                            text = context.getString(R.string.bottomsheet_cancel),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        RhythmButtonWeighted(
                            onClick = { sheetState = SheetContentState.InlinePicker },
                            weight = 1f,
                            isLast = true,
                            icon = RhythmIcons.Edit,
                            text = context.getString(R.string.bottomsheet_timer_edit)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSelectionCard(
    selectedAction: SleepAction,
    actionOptions: List<Triple<SleepAction, String, MaterialSymbolIcon>>,
    onActionSelected: (SleepAction) -> Unit,
    context: android.content.Context
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = RhythmIcons.Play,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.sleep_timer_action),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = context.getString(R.string.sleep_timer_action_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actionOptions.forEach { (action, label, icon) ->
                    val isSelected = selectedAction == action
                    Card(
                        onClick = { onActionSelected(action) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineTimePickerContent(
    onCancel: () -> Unit,
    onTimeSelected: (hours: Int, minutes: Int) -> Unit
) {
    val context = LocalContext.current
    val timePickerState = rememberTimePickerState(
        initialHour = 0,
        initialMinute = 30,
        is24Hour = true
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TimePicker(
                modifier = Modifier.padding(horizontal = 24.dp),
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            RhythmGroupedButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                size = RhythmButtonSize.Large
            ) {
                RhythmButtonWeighted(
                    onClick = onCancel,
                    weight = 1f,
                    isFirst = true,
                    icon = RhythmIcons.Close,
                    text = context.getString(R.string.bottomsheet_cancel),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                RhythmButtonWeighted(
                    onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) },
                    weight = 1f,
                    isLast = true,
                    icon = RhythmIcons.Check,
                    text = context.getString(R.string.bottomsheet_timer_set)
                )
            }
        }
    }
}