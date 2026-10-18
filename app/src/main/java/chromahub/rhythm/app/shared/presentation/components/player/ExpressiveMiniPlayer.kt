/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.windowScreenHeightDp
import chromahub.rhythm.app.util.windowScreenWidthDp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive MiniPlayer:
 * - Fluid pill shape with integrated spring progress backdrop.
 * - Directional elastic spring morph transitions for album artwork and text.
 * - Settings suggestions carousel-like swipe physics: continuous 1:1 drag,
 *   smooth fling exit, and organic incoming spring enter from opposite side.
 * - Autonomous song change spring pulse for hands-free track changes.
 * - Decisive current play/pause rotation that smoothly decelerates to rest on pause.
 */
@Composable
fun ExpressiveMiniPlayer(
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
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val miniPlayerSwipeGestures by appSettings.miniPlayerSwipeGestures.collectAsState()
    val miniPlayerSwipeTracks by appSettings.miniPlayerSwipeTracks.collectAsState()
    val miniPlayerSwipeDismiss by appSettings.miniPlayerSwipeDismiss.collectAsState()
    val miniPlayerCornerRadius by appSettings.miniPlayerCornerRadius.collectAsState()
    val miniPlayerShowArtwork by appSettings.miniPlayerShowArtwork.collectAsState()

    val isTablet = windowScreenWidthDp() >= 600
    val alwaysShowTabletLayout by appSettings.miniPlayerAlwaysShowTablet.collectAsState()
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    val useTabletLayout = isTablet || (alwaysShowTabletLayout && !isTablet)

    val motionConfig = MiniPlayerMotion.resolveConfig("EXPRESSIVE")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) motionConfig.pressScale else 1f,
        animationSpec = motionConfig.fastSpatialSpring,
        label = "miniPlayerPressScale"
    )

    val screenWidthPx = with(density) { windowScreenWidthDp().dp.toPx() }
    val carouselOffsetX = remember { Animatable(0f) }
    val carouselOffsetY = remember { Animatable(0f) }
    var isCarouselFlinging by remember { mutableStateOf(false) }
    var isDismissingPlayer by remember { mutableStateOf(false) }
    var isUserInteracting by remember { mutableStateOf(false) }

    val swipeHorizontalThreshold = with(density) { 75.dp.toPx() }
    val swipeUpThreshold = with(density) { 70.dp.toPx() }
    val swipeDownThreshold = with(density) { 75.dp.toPx() }

    val autoBounceScale = remember { Animatable(1f) }
    var previousSongId by remember { mutableStateOf(song?.id) }

    var trackDirection by remember { mutableStateOf(TrackChangeDirection.NONE) }

    LaunchedEffect(Unit) {
        if (song != null) {
            autoBounceScale.snapTo(0.93f)
            autoBounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = MiniPlayerMotionTokens.ExpressiveFastSpatialDamping,
                    stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                )
            )
        }
    }

    LaunchedEffect(song?.id) {
        if (song != null && previousSongId != null && song.id != previousSongId) {
            if (!isUserInteracting && !isCarouselFlinging) {
                if (trackDirection == TrackChangeDirection.NONE) {
                    trackDirection = TrackChangeDirection.FORWARD
                }
                coroutineScope.launch {
                    autoBounceScale.animateTo(
                        targetValue = 1.045f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 500f
                        )
                    )
                    autoBounceScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = 380f
                        )
                    )
                }
                delay(350)
                trackDirection = TrackChangeDirection.NONE
            }
        }
        previousSongId = song?.id
    }

    val canSwipeTracks = miniPlayerSwipeGestures && miniPlayerSwipeTracks
    val canSwipeVertical = miniPlayerSwipeGestures && miniPlayerSwipeDismiss && verticalDragEnabled

    fun handleSkipNext() {
        trackDirection = TrackChangeDirection.FORWARD
        onSkipNext()
    }

    fun handleSkipPrevious() {
        trackDirection = TrackChangeDirection.BACKWARD
        onSkipPrevious()
    }

    fun handleDismiss() {
        coroutineScope.launch {
            if (isPlaying) onPlayPause()
            delay(200)
            onDismiss()
            isDismissingPlayer = false
            carouselOffsetY.snapTo(0f)
            carouselOffsetX.snapTo(0f)
            isUserInteracting = false
        }
    }

    var lastHapticOffset by remember { mutableFloatStateOf(0f) }
    var lastHapticOffsetX by remember { mutableFloatStateOf(0f) }

    val carouselDragScale = (1f - (abs(carouselOffsetX.value) / (screenWidthPx * 4f))).coerceIn(0.92f, 1f)
    val cardAlpha = when {
        isDismissingPlayer -> 0f
        carouselOffsetY.value > 0 -> (1f - (carouselOffsetY.value / 250f)).coerceIn(0f, 1f)
        else -> (1f - (abs(carouselOffsetX.value) / (screenWidthPx * 1.5f))).coerceIn(0.55f, 1f)
    }

    val surfaceModifier = (if (useTabletLayout) {
        if (isLandscapeTablet) {
            Modifier
                .width(380.dp)
                .height(84.dp)
                .padding(end = 24.dp, bottom = 8.dp)
        } else {
            Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.92f)
                .height(84.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        }
    } else {
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    })
        .scale(pressScale * autoBounceScale.value * carouselDragScale)
        .graphicsLayer {
            translationX = carouselOffsetX.value
            translationY = when {
                isDismissingPlayer -> 500f
                carouselOffsetY.value > 0 -> carouselOffsetY.value.coerceAtMost(250f)
                else -> 0f
            }
            alpha = cardAlpha
        }
        .pointerInput(canSwipeTracks, canSwipeVertical) {
            if (canSwipeTracks && canSwipeVertical) {
                detectDragGestures(
                    onDragStart = {
                        isUserInteracting = true
                        lastHapticOffset = 0f
                        lastHapticOffsetX = 0f
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    },
                    onDragEnd = {
                        val absX = abs(carouselOffsetX.value)
                        val absY = abs(carouselOffsetY.value)

                        if (absX > absY && canSwipeTracks) {
                            if (carouselOffsetX.value < -swipeHorizontalThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                coroutineScope.launch {
                                    isCarouselFlinging = true
                                    carouselOffsetX.animateTo(
                                        targetValue = -screenWidthPx,
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            easing = MiniPlayerMotionTokens.EasingEmphasizedDecelerate
                                        )
                                    )
                                    handleSkipNext()
                                    carouselOffsetX.snapTo(screenWidthPx)
                                    carouselOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                                            stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                                        )
                                    )
                                    isCarouselFlinging = false
                                    isUserInteracting = false
                                }
                            } else if (carouselOffsetX.value > swipeHorizontalThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                coroutineScope.launch {
                                    isCarouselFlinging = true
                                    carouselOffsetX.animateTo(
                                        targetValue = screenWidthPx,
                                        animationSpec = tween(
                                            durationMillis = 180,
                                            easing = MiniPlayerMotionTokens.EasingEmphasizedDecelerate
                                        )
                                    )
                                    handleSkipPrevious()
                                    carouselOffsetX.snapTo(-screenWidthPx)
                                    carouselOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                                            stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                                        )
                                    )
                                    isCarouselFlinging = false
                                    isUserInteracting = false
                                }
                            } else {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                coroutineScope.launch {
                                    carouselOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                                            stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                                        )
                                    )
                                    isUserInteracting = false
                                }
                            }
                            coroutineScope.launch {
                                carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                            }
                        } else if (canSwipeVertical) {
                            if (carouselOffsetY.value < -swipeUpThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onPlayerClick()
                                coroutineScope.launch {
                                    carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    carouselOffsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    isUserInteracting = false
                                }
                            } else if (carouselOffsetY.value > swipeDownThreshold) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                isDismissingPlayer = true
                                coroutineScope.launch {
                                    carouselOffsetY.animateTo(500f, tween(150))
                                    handleDismiss()
                                }
                            } else {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                coroutineScope.launch {
                                    carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    carouselOffsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    isUserInteracting = false
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                carouselOffsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                isUserInteracting = false
                            }
                        }
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        coroutineScope.launch {
                            carouselOffsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                            carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                            isUserInteracting = false
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            carouselOffsetX.snapTo(carouselOffsetX.value + dragAmount.x)
                            carouselOffsetY.snapTo(carouselOffsetY.value + dragAmount.y)

                            val absX = abs(carouselOffsetX.value)
                            val absY = abs(carouselOffsetY.value)

                            if (absX > absY) {
                                if (absX - abs(lastHapticOffsetX) > swipeHorizontalThreshold / 2) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    lastHapticOffsetX = carouselOffsetX.value
                                }
                            } else {
                                if (absY - abs(lastHapticOffset) > swipeUpThreshold / 2) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    lastHapticOffset = carouselOffsetY.value
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
                        if (carouselOffsetX.value < -swipeHorizontalThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            coroutineScope.launch {
                                isCarouselFlinging = true
                                carouselOffsetX.animateTo(
                                    targetValue = -screenWidthPx,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = MiniPlayerMotionTokens.EasingEmphasizedDecelerate
                                    )
                                )
                                handleSkipNext()
                                carouselOffsetX.snapTo(screenWidthPx)
                                carouselOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                                        stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                                    )
                                )
                                isCarouselFlinging = false
                                isUserInteracting = false
                            }
                        } else if (carouselOffsetX.value > swipeHorizontalThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            coroutineScope.launch {
                                isCarouselFlinging = true
                                carouselOffsetX.animateTo(
                                    targetValue = screenWidthPx,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = MiniPlayerMotionTokens.EasingEmphasizedDecelerate
                                    )
                                )
                                handleSkipPrevious()
                                carouselOffsetX.snapTo(-screenWidthPx)
                                carouselOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                                        stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                                    )
                                )
                                isCarouselFlinging = false
                                isUserInteracting = false
                            }
                        } else {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            coroutineScope.launch {
                                carouselOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                                        stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                                    )
                                )
                                isUserInteracting = false
                            }
                        }
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        coroutineScope.launch {
                            carouselOffsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                            isUserInteracting = false
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            carouselOffsetX.snapTo(carouselOffsetX.value + dragAmount)
                            if (abs(carouselOffsetX.value) - abs(lastHapticOffsetX) > swipeHorizontalThreshold / 2) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                lastHapticOffsetX = carouselOffsetX.value
                            }
                        }
                    }
                )
            } else if (canSwipeVertical) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isUserInteracting = true
                        lastHapticOffset = 0f
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    },
                    onDragEnd = {
                        if (carouselOffsetY.value < -swipeUpThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            onPlayerClick()
                            coroutineScope.launch {
                                carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                isUserInteracting = false
                            }
                        } else if (carouselOffsetY.value > swipeDownThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            isDismissingPlayer = true
                            coroutineScope.launch {
                                carouselOffsetY.animateTo(500f, tween(150))
                                handleDismiss()
                            }
                        } else {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            coroutineScope.launch {
                                carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                isUserInteracting = false
                            }
                        }
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        coroutineScope.launch {
                            carouselOffsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                            isUserInteracting = false
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            carouselOffsetY.snapTo(carouselOffsetY.value + dragAmount)
                            if (abs(carouselOffsetY.value) - abs(lastHapticOffset) > swipeUpThreshold / 2) {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                lastHapticOffset = carouselOffsetY.value
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
                if (!isDismissingPlayer && !isCarouselFlinging) {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    onPlayerClick()
                }
            }
        )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (useTabletLayout) {
            if (isLandscapeTablet) Alignment.BottomEnd else Alignment.BottomCenter
        } else {
            Alignment.BottomCenter
        }
    ) {
        Surface(
            modifier = surfaceModifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MiniPlayerIntegratedProgress(
                    progress = progress,
                    isExpressive = true,
                    shape = CircleShape
                )

                if (useTabletLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (miniPlayerShowArtwork) {
                            MiniPlayerArtwork(
                                song = song,
                                direction = trackDirection,
                                isExpressive = true,
                                cornerRadius = miniPlayerCornerRadius,
                                size = 46.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        MiniPlayerContent(
                            song = song,
                            direction = trackDirection,
                            isExpressive = true,
                            titleFontSize = 14,
                            artistFontSize = 12,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isUserInteracting = true
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    handleSkipPrevious()
                                    isUserInteracting = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.SkipPrevious,
                                    contentDescription = stringResource(R.string.animatedplaybackcontrols_previous),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            MorphingPlayPauseButton(
                                isPlaying = isPlaying,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onPlayPause()
                                },
                                size = 44.dp,
                                isMediaLoading = isMediaLoading,
                                isExpressive = true
                            )

                            IconButton(
                                onClick = {
                                    isUserInteracting = true
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    handleSkipNext()
                                    isUserInteracting = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.SkipNext,
                                    contentDescription = stringResource(R.string.onboarding_next),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (miniPlayerShowArtwork) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MiniPlayerArtwork(
                                    song = song,
                                    direction = trackDirection,
                                    isExpressive = true,
                                    cornerRadius = miniPlayerCornerRadius,
                                    size = 56.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        MiniPlayerContent(
                            song = song,
                            direction = trackDirection,
                            isExpressive = true,
                            titleFontSize = 15,
                            artistFontSize = 13,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        MorphingPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onPlayPause()
                            },
                            size = 56.dp,
                            isMediaLoading = isMediaLoading,
                            isExpressive = true
                        )
                    }
                }
            }
        }

        if (!useTabletLayout) {
            val curX = carouselOffsetX.value
            val curY = carouselOffsetY.value
            val absX = abs(curX)
            val absY = abs(curY)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visible = curY < -20f && absY > absX,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = (-curY / swipeUpThreshold).coerceIn(0f, 0.85f)
                        )
                    ) {
                        Text(
                            text = context.getString(R.string.miniplayer_swipe_up_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = curY > 20f && absY > absX,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(
                            alpha = (curY / swipeDownThreshold).coerceIn(0f, 0.85f)
                        )
                    ) {
                        Text(
                            text = context.getString(R.string.miniplayer_swipe_down_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = curX < -20f && absX > absY,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                            alpha = (-curX / swipeHorizontalThreshold).coerceIn(0f, 0.85f)
                        )
                    ) {
                        Text(
                            text = context.getString(R.string.miniplayer_swipe_left_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = curX > 20f && absX > absY,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                            alpha = (curX / swipeHorizontalThreshold).coerceIn(0f, 0.85f)
                        )
                    ) {
                        Text(
                            text = context.getString(R.string.miniplayer_swipe_right_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}