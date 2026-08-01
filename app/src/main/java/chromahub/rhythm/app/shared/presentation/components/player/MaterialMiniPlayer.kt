package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.ui.theme.PlayerButtonColor
import chromahub.rhythm.app.ui.theme.PlayerProgressColor
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.concurrent.TimeUnit
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import chromahub.rhythm.app.shared.presentation.components.common.M3LinearLoader
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.common.ShimmerBox
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import chromahub.rhythm.app.shared.data.model.AppSettings
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration


import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import androidx.compose.ui.res.stringResource


/**
 * Mini player that appears at the bottom of the screen
 * Updated to support customizable progress bar styles (NORMAL, WAVY, ROUNDED, etc.)
 * Adapted for tablet UI with responsive layout
 * Uses M3 Expressive design with organic shapes and bouncy animations
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialMiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onDismiss: () -> Unit = {},
    isMediaLoading: Boolean = false,
    verticalDragEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isCompactHeight = configuration.screenHeightDp < 500
    val isLargeHeight = configuration.screenHeightDp >= 700
    val alwaysShowTabletLayout by appSettings.miniPlayerAlwaysShowTablet.collectAsState()
    val isLandscapeTablet = isTablet && configuration.screenWidthDp > configuration.screenHeightDp
    val useTabletLayout = isTablet || (alwaysShowTabletLayout && !isTablet)
    
    // MiniPlayer customization settings
    val miniPlayerProgressStyle by appSettings.miniPlayerProgressStyle.collectAsState()
    val miniPlayerShowProgress by appSettings.miniPlayerShowProgress.collectAsState()
    val miniPlayerShowArtwork by appSettings.miniPlayerShowArtwork.collectAsState()
    val miniPlayerArtworkSize by appSettings.miniPlayerArtworkSize.collectAsState()
    val miniPlayerCornerRadius by appSettings.miniPlayerCornerRadius.collectAsState()
    val miniPlayerShowTime by appSettings.miniPlayerShowTime.collectAsState()
    val miniPlayerUseCircularProgress by appSettings.miniPlayerUseCircularProgress.collectAsState()
    
    // Gesture settings
    val miniPlayerSwipeGestures by appSettings.miniPlayerSwipeGestures.collectAsState()
    
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress(),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    
    // Animation for tap feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )

    // Animation for song change bounce effect
    var songChangeBounceTrigger by remember { mutableStateOf(false) }
    val songBounceScale by animateFloatAsState(
        targetValue = if (songChangeBounceTrigger) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "songBounceScale"
    )

    // Animation for initial appearance bounce effect
    var initialAppearanceBounceTrigger by remember { mutableStateOf(false) }
    val initialAppearanceBounceScale by animateFloatAsState(
        targetValue = if (initialAppearanceBounceTrigger) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "initialAppearanceBounceScale"
    )

    // Tablet miniplayer position for drag
    var miniPlayerOffset by remember { mutableStateOf(Offset.Zero) }

    // Trigger bounce animation when mini player first appears
    LaunchedEffect(song) {
        if (song != null) {
            // Trigger initial appearance bounce
            initialAppearanceBounceTrigger = true
            delay(150)
            initialAppearanceBounceTrigger = false
            
            // Then trigger song change bounce after a short delay
            delay(50)
            songChangeBounceTrigger = true
            delay(100)
            songChangeBounceTrigger = false
        }
    }
    
    // For swipe gesture detection
    var offsetY by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    val swipeUpThreshold = 100f // Minimum distance to trigger player open
    val swipeDownThreshold = 100f // Minimum distance to trigger dismissal
    val swipeHorizontalThreshold = 120f // Minimum distance to trigger prev/next
    
    // Track last offset for haptic feedback at intervals
    var lastHapticOffset by remember { mutableStateOf(0f) }
    var lastHapticOffsetX by remember { mutableStateOf(0f) }
    
    // Animation for translation during swipe
    val translationOffsetY by animateFloatAsState(
        targetValue = if (offsetY > 0) offsetY.coerceAtMost(200f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "translationOffsetY"
    )
    
    val translationOffsetX by animateFloatAsState(
        targetValue = offsetX.coerceIn(-300f, 300f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "translationOffsetX"
    )
    
    // Calculate alpha based on offset
    val alphaValue by animateFloatAsState(
        targetValue = if (offsetY > 0) {
            // Fade out as user swipes down
            (1f - (offsetY / 300f)).coerceIn(0.2f, 1f)
        } else {
            1f
        },
        label = "alphaValue"
    )
    
    // For tracking if the mini player is being dismissed
    var isDismissingPlayer by remember { mutableStateOf(false) }
    
    // If dismissing, animate out and stop playback
    LaunchedEffect(isDismissingPlayer) {
        if (isDismissingPlayer) {
            // Stop playback when dismissing
            if (isPlaying) {
                onPlayPause()
            }
            // Call the dismiss callback to hide the player
            onDismiss()
            delay(300) // Keep short delay for local state reset if composable remains mounted
            // Reset local state
            isDismissingPlayer = false
            offsetY = 0f
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (useTabletLayout) {
            if (isLandscapeTablet) Alignment.BottomEnd else Alignment.BottomCenter
        } else {
            Alignment.BottomCenter
        }
    ) {
        Card(
            onClick = {
                if (!isDismissingPlayer) {
                    // Enhanced haptic feedback for click - respecting settings
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    onPlayerClick()
                }
            },
            shape = ExpressiveShapes.Large, // Expressive organic shape
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp, // Subtle elevation for depth
                pressedElevation = 1.dp  // Reduce on press
            ),
            modifier = (if (useTabletLayout) {
                if (isLandscapeTablet) {
                    Modifier
                        .width(360.dp)
                        .padding(end = 24.dp, bottom = 8.dp)
                } else {
                    Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth(0.92f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                }
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            })
                .scale(scale * songBounceScale * initialAppearanceBounceScale)
                .graphicsLayer { 
                    translationY = if (isDismissingPlayer) 300f else translationOffsetY + miniPlayerOffset.y
                    translationX = translationOffsetX + miniPlayerOffset.x
                    alpha = alphaValue
                }
                .pointerInput(miniPlayerSwipeGestures, useTabletLayout, verticalDragEnabled) {
                if (miniPlayerSwipeGestures) {
                    if (verticalDragEnabled) {
                        detectDragGestures(
                            onDragStart = { 
                                // Reset the last haptic offsets on new drag
                                lastHapticOffset = 0f
                                lastHapticOffsetX = 0f
                                if (useTabletLayout) {
                                    // For tablets, reset position for dismiss check
                                    miniPlayerOffset = Offset.Zero
                                }
                                
                                // Initial feedback when starting to drag - respecting settings
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            },
                            onDragEnd = {
                                if (useTabletLayout) {
                                    // Tablet: check for dismiss
                                    if (miniPlayerOffset.y > swipeDownThreshold) {
                                        // Swipe down detected, dismiss mini player
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                        isDismissingPlayer = true
                                    } else {
                                        // Snap back to original position
                                        miniPlayerOffset = Offset.Zero
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    }
                                } else {
                                    // Phone: Determine which gesture was dominant
                                    val absX = abs(offsetX)
                                    val absY = abs(offsetY)
                                    
                                    if (absX > absY) {
                                        // Horizontal swipe is dominant
                                        if (offsetX < -swipeHorizontalThreshold) {
                                            // Swipe left - next track
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                            onSkipNext()
                                        } else if (offsetX > swipeHorizontalThreshold) {
                                            // Swipe right - previous track
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                            onSkipPrevious()
                                        } else {
                                            // Not enough swipe distance
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        }
                                    } else {
                                        // Vertical swipe is dominant
                                        if (offsetY < -swipeUpThreshold) {
                                            // Swipe up detected, open player with stronger feedback
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                            onPlayerClick()
                                        } else if (offsetY > swipeDownThreshold) {
                                            // Swipe down detected, dismiss mini player with stronger feedback
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                            isDismissingPlayer = true
                                        } else {
                                            // Snap-back haptic when releasing before threshold
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        }
                                    }
                                }
                                
                                // Reset offsets if not dismissing
                                if (!isDismissingPlayer) {
                                    offsetY = 0f
                                    offsetX = 0f
                                }
                            },
                            onDragCancel = {
                                // Feedback when drag canceled
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                // Reset offsets if not dismissing
                                if (!isDismissingPlayer) {
                                    offsetY = 0f
                                    offsetX = 0f
                                    if (useTabletLayout) {
                                        miniPlayerOffset = Offset.Zero
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (useTabletLayout) {
                                    // For tablets, update position for moving
                                    miniPlayerOffset += dragAmount
                                } else {
                                    // Update offsets for both horizontal and vertical gestures
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                    
                                    // Provide interval haptic feedback during drag
                                    // For vertical swipes
                                    if (abs(offsetY) > abs(offsetX)) {
                                        // Vertical is dominant
                                        if (offsetY < 0 && abs(offsetY) - abs(lastHapticOffset) > swipeUpThreshold / 3) {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            lastHapticOffset = offsetY
                                        } else if (offsetY > 0 && abs(offsetY) - abs(lastHapticOffset) > swipeDownThreshold / 3) {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            lastHapticOffset = offsetY
                                        }
                                    } else {
                                        // Horizontal is dominant
                                        if (abs(offsetX) - abs(lastHapticOffsetX) > swipeHorizontalThreshold / 3) {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            lastHapticOffsetX = offsetX
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        // Only horizontal drag for skip next/previous!
                        detectHorizontalDragGestures(
                            onDragStart = {
                                lastHapticOffsetX = 0f
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            },
                            onDragEnd = {
                                if (offsetX < -swipeHorizontalThreshold) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onSkipNext()
                                } else if (offsetX > swipeHorizontalThreshold) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onSkipPrevious()
                                } else {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                }
                                offsetX = 0f
                            },
                            onDragCancel = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                offsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount
                                if (abs(offsetX) - abs(lastHapticOffsetX) > swipeHorizontalThreshold / 3) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    lastHapticOffsetX = offsetX
                                }
                            }
                        )
                    }
                }
            },
        interactionSource = interactionSource
    ) {
        // Display visual hints when user starts dragging
        val dragUpIndicatorAlpha = if (offsetY < 0 && abs(offsetY) > abs(offsetX)) minOf(
            (-offsetY / swipeUpThreshold) * 0.3f,
            0.3f
        ) else 0f
        val dragDownIndicatorAlpha = if (offsetY > 0 && abs(offsetY) > abs(offsetX)) minOf(
            (offsetY / swipeDownThreshold) * 0.3f,
            0.3f
        ) else 0f
        val dragLeftIndicatorAlpha = if (offsetX < 0 && abs(offsetX) > abs(offsetY)) minOf(
            (-offsetX / swipeHorizontalThreshold) * 0.3f,
            0.3f
        ) else 0f
        val dragRightIndicatorAlpha = if (offsetX > 0 && abs(offsetX) > abs(offsetY)) minOf(
            (offsetX / swipeHorizontalThreshold) * 0.3f,
            0.3f
        ) else 0f

        Box {
            Column {
                // Enhanced drag handle indicator with better visual feedback
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(40.dp) // Slightly wider for better touch target
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.4f + dragUpIndicatorAlpha + dragDownIndicatorAlpha + dragLeftIndicatorAlpha + dragRightIndicatorAlpha
                        )
                    )
                }

                // Mini player progress bar (phone)
                if (song != null && miniPlayerShowProgress && !useTabletLayout) {
                    if (miniPlayerUseCircularProgress) {
                        M3LinearLoader(
                            progress = animatedProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp)
                                .height(4.dp),
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    } else {
                        StyledProgressBar(
                            progress = animatedProgress,
                            style = try {
                                ProgressStyle.valueOf(miniPlayerProgressStyle)
                            } catch (e: Exception) {
                                ProgressStyle.NORMAL
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            progressColor = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            isPlaying = isPlaying,
                            height = 4.dp,
                            waveAmplitudeWhenPlaying = 3.dp,
                            waveLength = 30.dp // Shorter wavelength = more waves for MiniPlayer
                        )
                    }
                }

                if (useTabletLayout) {
                    // Tablet: Sleek horizontal expressive pill layout
                    val miniPlayerArtShape = rememberExpressiveShapeFor(
                        ExpressiveShapeTarget.MINI_PLAYER,
                        fallbackShape = RoundedCornerShape(miniPlayerCornerRadius.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Artwork on left
                        if (miniPlayerShowArtwork) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = miniPlayerArtShape,
                                    shadowElevation = 1.dp,
                                    tonalElevation = 2.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box {
                                        if (song != null) {
                                            ShimmerBox(modifier = Modifier.fillMaxSize())
                                            M3ImageUtils.TrackImage(
                                                imageUrl = song.artworkUri,
                                                trackName = song.title,
                                                modifier = Modifier.fillMaxSize(),
                                                applyExpressiveShape = false
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.primaryContainer,
                                                                MaterialTheme.colorScheme.secondaryContainer
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Album,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Center song info & progress
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (song != null) {
                                AutoScrollingTextOnDemand(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                    enabled = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (miniPlayerShowProgress) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    StyledProgressBar(
                                        progress = animatedProgress,
                                        style = try {
                                            ProgressStyle.valueOf(miniPlayerProgressStyle)
                                        } catch (e: Exception) {
                                            ProgressStyle.NORMAL
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        progressColor = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        isPlaying = isPlaying,
                                        height = 3.dp,
                                        waveAmplitudeWhenPlaying = 2.dp,
                                        waveLength = 20.dp
                                    )
                                }
                            } else {
                                Text(
                                    text = context.getString(R.string.miniplayer_no_song),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Right playback action controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val playerControlsShape = rememberExpressiveShapeFor(
                                ExpressiveShapeTarget.PLAYER_CONTROLS,
                                fallbackShape = ExpressiveShapes.Full
                            )

                            // Previous button
                            IconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onSkipPrevious()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.SkipPrevious,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Play/Pause button
                            FilledIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onPlayPause()
                                },
                                modifier = Modifier.size(44.dp),
                                shape = playerControlsShape,
                                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isMediaLoading) {
                                    M3CircularLoader(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2f
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) RhythmIcons.Pause else RhythmIcons.Play,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            // Next button
                            IconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onSkipNext()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.SkipNext,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Phone: Original horizontal layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isCompactHeight) 12.dp else 20.dp,
                                vertical = if (isCompactHeight) 6.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(if (isCompactHeight) 8.dp else 16.dp)
                    ) {
                        // Customizable album art with settings-driven size and corner radius
                        if (miniPlayerShowArtwork) {
                            val miniPlayerArtShape = rememberExpressiveShapeFor(
                                ExpressiveShapeTarget.MINI_PLAYER,
                                fallbackShape = RoundedCornerShape(miniPlayerCornerRadius.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .size((if (isTablet) miniPlayerArtworkSize + 8 else miniPlayerArtworkSize).dp),
                                shape = miniPlayerArtShape,
                                shadowElevation = 0.dp, // Remove shadow as requested
                                tonalElevation = 2.dp, // Keep subtle tonal elevation for depth
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box {
                                    if (song != null) {
                                        // Show shimmer while loading artwork
                                        ShimmerBox(
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        M3ImageUtils.TrackImage(
                                            imageUrl = song.artworkUri,
                                            trackName = song.title,
                                            modifier = Modifier.fillMaxSize(),
                                            applyExpressiveShape = false
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primaryContainer,
                                                            MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = RhythmIcons.Album,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    // Enhanced "live" badge with better styling
                                    if (song?.title?.contains("LIVE", ignoreCase = true) == true ||
                                        song?.genre?.contains("live", ignoreCase = true) == true
                                    ) {
                                        Badge(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp),
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Text(
                                                context.getString(R.string.badge_live),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onError,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Enhanced song info with better typography and spacing
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = spacedBy(if (isCompactHeight) 0.dp else 2.dp)
                        ) {
                            if (song != null) {
                                AutoScrollingTextOnDemand(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = if (isCompactHeight) 13.sp else 16.sp
                                    ),
                                    gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                    enabled = true
                                )
                            } else {
                                Text(
                                    text = context.getString(R.string.miniplayer_no_song),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = if (isCompactHeight) 13.sp else 16.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = spacedBy(if (isCompactHeight) 3.dp else 6.dp)
                            ) {
                                // Artist info with auto-scrolling
                                if (song != null) {
                                    AutoScrollingTextOnDemand(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = if (isCompactHeight) 11.sp else 13.sp
                                        ),
                                        gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.weight(1f, fill = false),
                                        enabled = true
                                    )
                                } else {
                                    Text(
                                        text = "",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = if (isCompactHeight) 11.sp else 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }

                                // Compact time indicator - controlled by setting
                                if (miniPlayerShowTime && song != null && progress() > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(
                                            alpha = 0.6f
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${
                                                formatDuration(
                                                    (progress() * song.duration).toLong(),
                                                    useHoursFormat
                                                )
                                            }/${formatDuration(song.duration, useHoursFormat)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = if (isCompactHeight) 8.sp else 10.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = if (isCompactHeight) 4.dp else 6.dp,
                                                vertical = if (isCompactHeight) 1.dp else 2.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Enhanced controls with better visual hierarchy and spacing
                        // Get miniplayer play button shape from expressive settings
                        val miniPlayButtonShape = rememberExpressiveShapeFor(
                            ExpressiveShapeTarget.PLAYER_CONTROLS,
                            fallbackShape = CircleShape
                        )
                        Row(
                            horizontalArrangement = spacedBy(if (isCompactHeight) 4.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play/pause button with optional circular progress border
                            if (song != null && miniPlayerUseCircularProgress) {
                                // Circular progress as border around play/pause button using official Material 3 Expressive
                                Box(
                                    modifier = Modifier.size(if (isCompactHeight) 50.dp else 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularWavyProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.size(if (isCompactHeight) 50.dp else 60.dp)
                                    )

                                    // Expressive play/pause with bouncy animation
                                    val phonePlayInteractionSource =
                                        remember { MutableInteractionSource() }
                                    val isPhonePlayPressed by phonePlayInteractionSource.collectIsPressedAsState()
                                    val phonePlayScale by animateFloatAsState(
                                        targetValue = if (isPhonePlayPressed) 0.88f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "phone_play_scale"
                                    )

                                    FilledIconButton(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(
                                                context,
                                                haptic,
                                                HapticType.HEAVY
                                            )
                                            onPlayPause()
                                        },
                                        modifier = Modifier
                                            .size(if (isCompactHeight) 36.dp else 44.dp)
                                            .graphicsLayer {
                                                scaleX = phonePlayScale
                                                scaleY = phonePlayScale
                                            },
                                        shape = miniPlayButtonShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        interactionSource = phonePlayInteractionSource
                                    ) {
                                        if (isMediaLoading) {
                                            M3CircularLoader(
                                                modifier = Modifier.size(if (isCompactHeight) 14.dp else 18.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                trackColor = MaterialTheme.colorScheme.onPrimary.copy(
                                                    alpha = 0.24f
                                                ),
                                                strokeWidth = 2f
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) RhythmIcons.Pause else RhythmIcons.Play,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(if (isCompactHeight) 16.dp else 20.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Standard play/pause button without circular progress - with expressive animation
                                val stdPlayInteractionSource =
                                    remember { MutableInteractionSource() }
                                val isStdPlayPressed by stdPlayInteractionSource.collectIsPressedAsState()
                                val stdPlayScale by animateFloatAsState(
                                    targetValue = if (isStdPlayPressed) 0.88f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "std_play_scale"
                                )

                                FilledIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(
                                            context,
                                            haptic,
                                            HapticType.HEAVY
                                        )
                                        onPlayPause()
                                    },
                                    modifier = Modifier
                                        .size(if (isCompactHeight) 36.dp else 44.dp)
                                        .graphicsLayer {
                                            scaleX = stdPlayScale
                                            scaleY = stdPlayScale
                                        },
                                    shape = miniPlayButtonShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    interactionSource = stdPlayInteractionSource
                                ) {
                                    if (isMediaLoading) {
                                        M3CircularLoader(
                                            modifier = Modifier.size(if (isCompactHeight) 14.dp else 18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(
                                                alpha = 0.24f
                                            ),
                                            strokeWidth = 2f
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) RhythmIcons.Pause else RhythmIcons.Play,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(if (isCompactHeight) 16.dp else 20.dp)
                                        )
                                    }
                                }
                            }

                            // Enhanced next track button with expressive bouncy animation
                            val nextTrackInteractionSource = remember { MutableInteractionSource() }
                            val isNextTrackPressed by nextTrackInteractionSource.collectIsPressedAsState()
                            val nextTrackScale by animateFloatAsState(
                                targetValue = if (isNextTrackPressed) 0.88f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "next_track_scale"
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    // Strong haptic feedback for next track - respecting settings
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.LIGHT
                                    )
                                    onSkipNext()
                                },
                                modifier = Modifier
                                    .size(if (isCompactHeight) 32.dp else 36.dp)
                                    .graphicsLayer {
                                        scaleX = nextTrackScale
                                        scaleY = nextTrackScale
                                    },
                                shape = ExpressiveShapes.Full, // Expressive pill shape
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                interactionSource = nextTrackInteractionSource
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.SkipNext,
                                    contentDescription = stringResource(R.string.cd_next_track),
                                    modifier = Modifier.size(if (isCompactHeight) 14.dp else 18.dp)
                                )
                            }
                        }
                    }
                }  // Close the else block for phone layout
            }

                // Guide chips are rendered in an overlay so they never affect MiniPlayer height.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Swipe up indicator
                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetY < -20f && abs(offsetY) > abs(offsetX),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = (-offsetY / swipeUpThreshold).coerceIn(
                                    0f,
                                    0.8f
                                )
                            )
                        ) {
                            Text(
                                text = context.getString(R.string.miniplayer_swipe_up_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Swipe down indicator
                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetY > 20f && abs(offsetY) > abs(offsetX),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer.copy(
                                alpha = (offsetY / swipeDownThreshold).coerceIn(
                                    0f,
                                    0.8f
                                )
                            )
                        ) {
                            Text(
                                text = context.getString(R.string.miniplayer_swipe_down_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Swipe left indicator (next track)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetX < -20f && abs(offsetX) > abs(offsetY),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                alpha = (-offsetX / swipeHorizontalThreshold).coerceIn(
                                    0f,
                                    0.8f
                                )
                            )
                        ) {
                            Text(
                                text = context.getString(R.string.miniplayer_swipe_left_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Swipe right indicator (previous track)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetX > 20f && abs(offsetX) > abs(offsetY),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                alpha = (offsetX / swipeHorizontalThreshold).coerceIn(
                                    0f,
                                    0.8f
                                )
                            )
                        ) {
                            Text(
                                text = context.getString(R.string.miniplayer_swipe_right_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
