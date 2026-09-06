/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.ShimmerBox
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

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
    val appSettings = remember { AppSettings.getInstance(context) }

    val miniPlayerSwipeGestures by appSettings.miniPlayerSwipeGestures.collectAsState()
    val miniPlayerSwipeTracks by appSettings.miniPlayerSwipeTracks.collectAsState()
    val miniPlayerSwipeDismiss by appSettings.miniPlayerSwipeDismiss.collectAsState()
    val miniPlayerCornerRadius by appSettings.miniPlayerCornerRadius.collectAsState()

    val isTablet = windowScreenWidthDp() >= 600
    val isCompactHeight = windowScreenHeightDp() < 500
    val isLargeHeight = windowScreenHeightDp() >= 700
    val alwaysShowTabletLayout by appSettings.miniPlayerAlwaysShowTablet.collectAsState()
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    val useTabletLayout = isTablet || (alwaysShowTabletLayout && !isTablet)

    val miniPlayerShowArtwork by appSettings.miniPlayerShowArtwork.collectAsState()

    
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

    var songChangeBounceTrigger by remember { mutableStateOf(false) }
    val songBounceScale by animateFloatAsState(
        targetValue = if (songChangeBounceTrigger) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "songBounceScale"
    )

    var initialAppearanceBounceTrigger by remember { mutableStateOf(false) }
    val initialAppearanceBounceScale by animateFloatAsState(
        targetValue = if (initialAppearanceBounceTrigger) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "initialAppearanceBounceScale"
    )

    
    LaunchedEffect(song) {
        if (song != null) {
            initialAppearanceBounceTrigger = true
            delay(150)
            initialAppearanceBounceTrigger = false

            delay(50)
            songChangeBounceTrigger = true
            delay(100)
            songChangeBounceTrigger = false
        }
    }

    
    var offsetY by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeUpThreshold = 100f
    val swipeDownThreshold = 100f
    val swipeHorizontalThreshold = 120f

    var lastHapticOffset by remember { mutableFloatStateOf(0f) }
    var lastHapticOffsetX by remember { mutableFloatStateOf(0f) }
    var isDismissingPlayer by remember { mutableStateOf(false) }

    
    val translationOffsetY by animateFloatAsState(
        targetValue = when {
            isDismissingPlayer -> 500f
            offsetY > 0 -> offsetY.coerceAtMost(250f)
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "translationOffsetY"
    )
    val translationOffsetX by animateFloatAsState(
        targetValue = offsetX.coerceIn(-300f, 300f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "translationOffsetX"
    )
    val alphaValue by animateFloatAsState(
        targetValue = when {
            isDismissingPlayer -> 0f
            offsetY > 0 -> (1f - (offsetY / 250f)).coerceIn(0f, 1f)
            else -> 1f
        },
        animationSpec = tween(150),
        label = "alphaValue"
    )

    val dragScale by animateFloatAsState(
        targetValue = if (offsetY > 0) (1f - (offsetY / 1000f)).coerceAtLeast(0.9f) else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "dragScale"
    )

    LaunchedEffect(isDismissingPlayer) {
        if (isDismissingPlayer) {
            if (isPlaying) onPlayPause()
            delay(250)
            onDismiss()
            isDismissingPlayer = false
            offsetY = 0f
        }
    }

    val canSwipeTracks = miniPlayerSwipeGestures && miniPlayerSwipeTracks
    val canSwipeVertical = miniPlayerSwipeGestures && miniPlayerSwipeDismiss && verticalDragEnabled

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
        .scale(scale * songBounceScale * initialAppearanceBounceScale * dragScale)
        .graphicsLayer {
            translationY = translationOffsetY
            translationX = translationOffsetX
            alpha = alphaValue
        }
        .pointerInput(canSwipeTracks, canSwipeVertical) {
            if (canSwipeTracks && canSwipeVertical) {
                detectDragGestures(
                    onDragStart = {
                        lastHapticOffset = 0f
                        lastHapticOffsetX = 0f
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    },
                    onDragEnd = {
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

                        if (!isDismissingPlayer) {
                            offsetY = 0f
                            offsetX = 0f
                        }
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (!isDismissingPlayer) {
                            offsetY = 0f
                            offsetX = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
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
                )
            } else if (canSwipeTracks) {
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
            } else if (canSwipeVertical) {
                detectVerticalDragGestures(
                    onDragStart = {
                        lastHapticOffset = 0f
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    },
                    onDragEnd = {
                        if (offsetY < -swipeUpThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            onPlayerClick()
                        } else if (offsetY > swipeDownThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            isDismissingPlayer = true
                        } else {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        }

                        if (!isDismissingPlayer) {
                            offsetY = 0f
                        }
                    },
                    onDragCancel = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (!isDismissingPlayer) {
                            offsetY = 0f
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount
                        if (offsetY < 0 && abs(offsetY) - abs(lastHapticOffset) > swipeUpThreshold / 3) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            lastHapticOffset = offsetY
                        } else if (offsetY > 0 && abs(offsetY) - abs(lastHapticOffset) > swipeDownThreshold / 3) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            lastHapticOffset = offsetY
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
            if (useTabletLayout) {
                // Tablet layout
                val miniPlayerArtShape = rememberExpressiveShapeFor(
                    ExpressiveShapeTarget.MINI_PLAYER,
                    fallbackShape = RoundedCornerShape(miniPlayerCornerRadius.dp)
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    if (animatedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = animatedProgress.coerceIn(0.001f, 1f))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album art
                        if (miniPlayerShowArtwork) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(46.dp),
                                    shape = miniPlayerArtShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
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
                                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Album,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                    // Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (song != null) {
                            AutoScrollingTextOnDemand(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                enabled = true,
                                textAlign = TextAlign.Start,
                                respectGlobalSetting = true
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AutoScrollingTextOnDemand(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                enabled = true,
                                textAlign = TextAlign.Start,
                                respectGlobalSetting = true
                            )
                        } else {
                            Text(
                                text = context.getString(R.string.miniplayer_no_song),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Controls Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Skip Previous
                        IconButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onSkipPrevious()
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

                        // Play/Pause
                        MorphingPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onPlayPause()
                            },
                            size = 44.dp,
                            isMediaLoading = isMediaLoading
                        )

                        // Skip Next
                        IconButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onSkipNext()
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
            }
        } else {
                // Phone layout (Original)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (animatedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = animatedProgress.coerceIn(0.001f, 1f))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val miniPlayerArtShape = rememberExpressiveShapeFor(
                                ExpressiveShapeTarget.MINI_PLAYER,
                                fallbackShape = RoundedCornerShape(miniPlayerCornerRadius.dp)
                            )

                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = miniPlayerArtShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
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
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Album,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (song != null) {
                                AutoScrollingTextOnDemand(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                    enabled = true,
                                    textAlign = TextAlign.Start,
                                    respectGlobalSetting = true
                                )
                                AutoScrollingTextOnDemand(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                                    enabled = true,
                                    textAlign = TextAlign.Start,
                                    respectGlobalSetting = true
                                )
                            } else {
                                Text(
                                    text = context.getString(R.string.miniplayer_no_song),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        MorphingPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onPlayPause()
                            },
                            size = 56.dp,
                            isMediaLoading = isMediaLoading
                        )
                    }
                }
            }
        }

        // Overlay: Swipe Guide Chips (only on phone)
        if (!useTabletLayout) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // Swipe up hint
                AnimatedVisibility(
                    visible = offsetY < -20f && abs(offsetY) > abs(offsetX),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = (-offsetY / swipeUpThreshold).coerceIn(0f, 0.8f)
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

                // Swipe down hint
                AnimatedVisibility(
                    visible = offsetY > 20f && abs(offsetY) > abs(offsetX),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(
                            alpha = (offsetY / swipeDownThreshold).coerceIn(0f, 0.8f)
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

                // Swipe left (next)
                AnimatedVisibility(
                    visible = offsetX < -20f && abs(offsetX) > abs(offsetY),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                            alpha = (-offsetX / swipeHorizontalThreshold).coerceIn(0f, 0.8f)
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

                // Swipe right (previous)
                AnimatedVisibility(
                    visible = offsetX > 20f && abs(offsetX) > abs(offsetY),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(
                            alpha = (offsetX / swipeHorizontalThreshold).coerceIn(0f, 0.8f)
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