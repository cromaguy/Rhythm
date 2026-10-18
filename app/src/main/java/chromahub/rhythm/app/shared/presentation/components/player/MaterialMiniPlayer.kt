/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ShimmerBox
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.util.windowScreenHeightDp
import chromahub.rhythm.app.util.windowScreenWidthDp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standard Material 3 MiniPlayer:
 * - Restored previous UI: top drag handle divider, padded top progress bar,
 *   artwork with Live badge, auto-scrolling metadata with compact time badge chip,
 *   morphing play/pause button with rotation deceleration, and filled tonal next button.
 * - Autonomous song change spring pulse for hands-free track changes.
 * - Responsive phone and tablet layout adaptations.
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
    modifier: Modifier = Modifier,
    onSkipPrevious: () -> Unit = {},
    onDismiss: () -> Unit = {},
    isMediaLoading: Boolean = false,
    verticalDragEnabled: Boolean = true
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val isTablet = windowScreenWidthDp() >= 600
    val isCompactHeight = windowScreenHeightDp() < 500
    val alwaysShowTabletLayout by appSettings.miniPlayerAlwaysShowTablet.collectAsState()
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    val useTabletLayout = isTablet || (alwaysShowTabletLayout && !isTablet)

    val miniPlayerProgressStyle by appSettings.miniPlayerProgressStyle.collectAsState()
    val miniPlayerShowProgress by appSettings.miniPlayerShowProgress.collectAsState()
    val miniPlayerShowArtwork by appSettings.miniPlayerShowArtwork.collectAsState()
    val miniPlayerArtworkSize by appSettings.miniPlayerArtworkSize.collectAsState()
    val miniPlayerCornerRadius by appSettings.miniPlayerCornerRadius.collectAsState()
    val miniPlayerShowTime by appSettings.miniPlayerShowTime.collectAsState()

    val miniPlayerSwipeGestures by appSettings.miniPlayerSwipeGestures.collectAsState()
    val miniPlayerSwipeTracks by appSettings.miniPlayerSwipeTracks.collectAsState()
    val miniPlayerSwipeDismiss by appSettings.miniPlayerSwipeDismiss.collectAsState()

    val haptic = LocalHapticFeedback.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress(),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )

    val autoBounceScale = remember { Animatable(1f) }
    var previousSongId by remember { mutableStateOf(song?.id) }
    var isUserInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (song != null) {
            autoBounceScale.snapTo(0.94f)
            autoBounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    LaunchedEffect(song?.id) {
        if (song != null && previousSongId != null && song.id != previousSongId) {
            if (!isUserInteracting) {
                coroutineScope.launch {
                    autoBounceScale.animateTo(
                        targetValue = 1.04f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 500f
                        )
                    )
                    autoBounceScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
        previousSongId = song?.id
    }

    var miniPlayerOffset by remember { mutableStateOf(Offset.Zero) }

    var offsetY by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeUpThreshold = 100f
    val swipeDownThreshold = 100f
    val swipeHorizontalThreshold = 120f

    var lastHapticOffset by remember { mutableFloatStateOf(0f) }
    var lastHapticOffsetX by remember { mutableFloatStateOf(0f) }

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

    val alphaValue by animateFloatAsState(
        targetValue = if (offsetY > 0) {
            (1f - (offsetY / 300f)).coerceIn(0.2f, 1f)
        } else {
            1f
        },
        label = "alphaValue"
    )

    var isDismissingPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(isDismissingPlayer) {
        if (isDismissingPlayer) {
            if (isPlaying) onPlayPause()
            delay(200)
            onDismiss()
            isDismissingPlayer = false
            offsetY = 0f
            offsetX = 0f
        }
    }

    val canSwipeTracks = miniPlayerSwipeGestures && miniPlayerSwipeTracks
    val canSwipeVertical = miniPlayerSwipeGestures && miniPlayerSwipeDismiss && verticalDragEnabled

    val cardShape = RoundedCornerShape(miniPlayerCornerRadius.dp)

    val surfaceModifier = (if (useTabletLayout) {
        if (isLandscapeTablet) {
            Modifier
                .width(360.dp)
                .padding(end = 24.dp)
                .padding(bottom = 8.dp)
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
        .scale(scale * autoBounceScale.value)
        .graphicsLayer {
            translationY = if (isDismissingPlayer) 300f else translationOffsetY + miniPlayerOffset.y
            translationX = translationOffsetX + miniPlayerOffset.x
            alpha = alphaValue
        }
        .pointerInput(canSwipeTracks, canSwipeVertical, useTabletLayout) {
            if (canSwipeTracks && canSwipeVertical) {
                detectDragGestures(
                    onDragStart = {
                        isUserInteracting = true
                        lastHapticOffset = 0f
                        lastHapticOffsetX = 0f
                        if (useTabletLayout) {
                            miniPlayerOffset = Offset.Zero
                        }
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    },
                    onDragEnd = {
                        if (useTabletLayout) {
                            if (miniPlayerOffset.y > swipeDownThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                isDismissingPlayer = true
                            } else {
                                miniPlayerOffset = Offset.Zero
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            }
                        } else {
                            val absX = abs(offsetX)
                            val absY = abs(offsetY)

                            if (absX > absY) {
                                if (offsetX < -swipeHorizontalThreshold) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onSkipNext()
                                } else if (offsetX > swipeHorizontalThreshold) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onSkipPrevious()
                                } else {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                }
                            } else {
                                if (offsetY < -swipeUpThreshold) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onPlayerClick()
                                } else if (offsetY > swipeDownThreshold) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    isDismissingPlayer = true
                                } else {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                }
                            }
                        }

                        if (!isDismissingPlayer) {
                            offsetY = 0f
                            offsetX = 0f
                        }
                        isUserInteracting = false
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (!isDismissingPlayer) {
                            offsetY = 0f
                            offsetX = 0f
                            if (useTabletLayout) {
                                miniPlayerOffset = Offset.Zero
                            }
                        }
                        isUserInteracting = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (useTabletLayout) {
                            miniPlayerOffset += dragAmount
                        } else {
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y

                            if (abs(offsetY) > abs(offsetX)) {
                                if (offsetY < 0 && abs(offsetY) - abs(lastHapticOffset) > swipeUpThreshold / 3) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    lastHapticOffset = offsetY
                                } else if (offsetY > 0 && abs(offsetY) - abs(lastHapticOffset) > swipeDownThreshold / 3) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    lastHapticOffset = offsetY
                                }
                            } else {
                                if (abs(offsetX) - abs(lastHapticOffsetX) > swipeHorizontalThreshold / 3) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    lastHapticOffsetX = offsetX
                                }
                            }
                        }
                    }
                )
            } else if (canSwipeTracks) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isUserInteracting = true
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
                        isUserInteracting = false
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        offsetX = 0f
                        isUserInteracting = false
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
            } else if (canSwipeVertical) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isUserInteracting = true
                        lastHapticOffset = 0f
                        if (useTabletLayout) {
                            miniPlayerOffset = Offset.Zero
                        }
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    },
                    onDragEnd = {
                        if (useTabletLayout) {
                            if (miniPlayerOffset.y > swipeDownThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                isDismissingPlayer = true
                            } else {
                                miniPlayerOffset = Offset.Zero
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            }
                        } else {
                            if (offsetY < -swipeUpThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onPlayerClick()
                            } else if (offsetY > swipeDownThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                isDismissingPlayer = true
                            } else {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            }
                        }
                        if (!isDismissingPlayer) {
                            offsetY = 0f
                        }
                        isUserInteracting = false
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (!isDismissingPlayer) {
                            offsetY = 0f
                            if (useTabletLayout) {
                                miniPlayerOffset = Offset.Zero
                            }
                        }
                        isUserInteracting = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (useTabletLayout) {
                            miniPlayerOffset = Offset(miniPlayerOffset.x, miniPlayerOffset.y + dragAmount)
                        } else {
                            offsetY += dragAmount
                            if (offsetY < 0 && abs(offsetY) - abs(lastHapticOffset) > swipeUpThreshold / 3) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                lastHapticOffset = offsetY
                            } else if (offsetY > swipeDownThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                lastHapticOffset = offsetY
                            }
                        }
                    }
                )
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (!isDismissingPlayer) {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    onPlayerClick()
                }
            }
        )

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

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (useTabletLayout) {
            if (isLandscapeTablet) Alignment.BottomEnd else Alignment.BottomCenter
        } else {
            Alignment.BottomCenter
        }
    ) {
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 1.dp,
                pressedElevation = 0.dp
            ),
            modifier = surfaceModifier
        ) {
            Box {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.4f + dragUpIndicatorAlpha + dragDownIndicatorAlpha + dragLeftIndicatorAlpha + dragRightIndicatorAlpha
                            )
                        )
                    }

                    if (song != null && miniPlayerShowProgress && !useTabletLayout) {
                        val resolvedStyle = try {
                            ProgressStyle.valueOf(miniPlayerProgressStyle)
                        } catch (e: Exception) {
                            ProgressStyle.NORMAL
                        }
                        val barHeight = when (resolvedStyle) {
                            ProgressStyle.WAVY -> 16.dp
                            ProgressStyle.THICK -> 6.dp
                            ProgressStyle.ROUNDED -> 5.dp
                            ProgressStyle.SEGMENTED -> 4.dp
                            ProgressStyle.THIN -> 2.dp
                            ProgressStyle.DOTS -> 16.dp
                            else -> 3.dp
                        }
                        StyledProgressBar(
                            progress = animatedProgress,
                            style = resolvedStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            progressColor = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            isPlaying = isPlaying,
                            height = barHeight,
                            waveAmplitudeWhenPlaying = 3.dp,
                            waveLength = 32.dp
                        )
                    }

                    if (useTabletLayout) {
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
                            if (miniPlayerShowArtwork) {
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(52.dp),
                                        shape = miniPlayerArtShape,
                                        shadowElevation = 0.dp,
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
                                        val resolvedTabletStyle = try {
                                            ProgressStyle.valueOf(miniPlayerProgressStyle)
                                        } catch (e: Exception) {
                                            ProgressStyle.NORMAL
                                        }
                                        StyledProgressBar(
                                            progress = animatedProgress,
                                            style = resolvedTabletStyle,
                                            modifier = Modifier.fillMaxWidth(),
                                            progressColor = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                            isPlaying = isPlaying,
                                            height = when (resolvedTabletStyle) {
                                                ProgressStyle.WAVY -> 14.dp
                                                ProgressStyle.THICK -> 6.dp
                                                ProgressStyle.ROUNDED -> 5.dp
                                                ProgressStyle.SEGMENTED -> 4.dp
                                                ProgressStyle.THIN -> 2.dp
                                                else -> 3.dp
                                            },
                                            waveAmplitudeWhenPlaying = 2.5.dp,
                                            waveLength = 24.dp
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        isUserInteracting = true
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                        onSkipPrevious()
                                        isUserInteracting = false
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

                                MorphingPlayPauseButton(
                                    isPlaying = isPlaying,
                                    onClick = {
                                        HapticUtils.performHapticFeedback(
                                            context,
                                            haptic,
                                            HapticType.HEAVY
                                        )
                                        onPlayPause()
                                    },
                                    size = 44.dp,
                                    isMediaLoading = isMediaLoading
                                )

                                IconButton(
                                    onClick = {
                                        isUserInteracting = true
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                        onSkipNext()
                                        isUserInteracting = false
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
                            if (miniPlayerShowArtwork) {
                                val miniPlayerArtShape = rememberExpressiveShapeFor(
                                    ExpressiveShapeTarget.MINI_PLAYER,
                                    fallbackShape = RoundedCornerShape(miniPlayerCornerRadius.dp)
                                )
                                Surface(
                                    modifier = Modifier
                                        .size((if (isTablet) miniPlayerArtworkSize + 8 else miniPlayerArtworkSize).dp),
                                    shape = miniPlayerArtShape,
                                    shadowElevation = 0.dp,
                                    tonalElevation = 2.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box {
                                        if (song != null) {
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

                            Row(
                                horizontalArrangement = spacedBy(if (isCompactHeight) 4.dp else 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MorphingPlayPauseButton(
                                    isPlaying = isPlaying,
                                    onClick = {
                                        HapticUtils.performHapticFeedback(
                                            context,
                                            haptic,
                                            HapticType.HEAVY
                                        )
                                        onPlayPause()
                                    },
                                    size = if (isCompactHeight) 36.dp else 44.dp,
                                    isMediaLoading = isMediaLoading
                                )

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
                                        isUserInteracting = true
                                        HapticUtils.performHapticFeedback(
                                            context,
                                            haptic,
                                            HapticType.LIGHT
                                        )
                                        onSkipNext()
                                        isUserInteracting = false
                                    },
                                    modifier = Modifier
                                        .size(if (isCompactHeight) 32.dp else 36.dp)
                                        .graphicsLayer {
                                            scaleX = nextTrackScale
                                            scaleY = nextTrackScale
                                        },
                                    shape = ExpressiveShapes.Full,
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
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetY < -20f && abs(offsetY) > abs(offsetX),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = (-offsetY / swipeUpThreshold).coerceIn(0f, 0.8f)
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetY > 20f && abs(offsetY) > abs(offsetX),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer.copy(
                                alpha = (offsetY / swipeDownThreshold).coerceIn(0f, 0.8f)
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetX < -20f && abs(offsetX) > abs(offsetY),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                alpha = (-offsetX / swipeHorizontalThreshold).coerceIn(0f, 0.8f)
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = offsetX > 20f && abs(offsetX) > abs(offsetY),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                alpha = (offsetX / swipeHorizontalThreshold).coerceIn(0f, 0.8f)
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
