/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components

import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.PlaybackQualityInfo
import chromahub.rhythm.app.util.AudioQualityDetector
import chromahub.rhythm.app.util.AudioFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri

/**
 * Quality level for badge styling
 */
private enum class QualityLevel {
    EXCELLENT,  // Studio Master, Dolby Atmos/TrueHD, DTS-HD MA
    GOOD,       // Hi-Res Lossless, CD Quality Lossless
    STANDARD    // Dolby Digital, DTS, High-quality lossy
}



@Composable
fun AudioQualityIcon(
    song: Song,
    modifier: Modifier = Modifier,
    playbackQuality: PlaybackQualityInfo? = null,
    onClick: (() -> Unit)? = null,
    iconSize: Dp = 32.dp,
    padding: Dp = 8.dp,
    tint: Color? = null,
    // When > 0, the icon fades in once quality is detected, stays visible for this long, then fades out.
    // Default 0 keeps the icon visible persistently (existing behavior for other screens).
    autoHideAfterMs: Long = 0L,
    revealTrigger: Int = 0
) {
    val context = LocalContext.current
    var audioQuality by remember(song.id) { mutableStateOf<AudioQualityDetector.AudioQuality?>(null) }

    LaunchedEffect(song.id, playbackQuality) {
        if (playbackQuality != null) {
            audioQuality = AudioQualityDetector.AudioQuality(
                qualityType = playbackQuality.qualityType,
                qualityLabel = playbackQuality.qualityLabel,
                qualityDescription = playbackQuality.qualityDescription,
                isLossless = playbackQuality.qualityType in listOf(
                    AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS,
                    AudioQualityDetector.QualityType.HI_RES_LOSSLESS,
                    AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER,
                    AudioQualityDetector.QualityType.DSD_HIGH_RES,
                    AudioQualityDetector.QualityType.DOLBY_LOSSLESS,
                    AudioQualityDetector.QualityType.LOSSLESS_SURROUND
                ),
                isHiRes = playbackQuality.qualityType in listOf(
                    AudioQualityDetector.QualityType.HI_RES_LOSSLESS,
                    AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER,
                    AudioQualityDetector.QualityType.DSD_HIGH_RES
                ),
                isDolby = playbackQuality.qualityType in listOf(
                    AudioQualityDetector.QualityType.DOLBY_LOSSLESS,
                    AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND
                ),
                isDTS = playbackQuality.qualityType == AudioQualityDetector.QualityType.DTS_SURROUND,
                bitDepthEstimate = playbackQuality.decodedBitDepth,
                category = if (playbackQuality.qualityType == AudioQualityDetector.QualityType.LOSSY_COMPRESSED) "Lossy" else "Lossless"
            )
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val formatInfo = AudioFormatDetector.detectFormat(context, song.uri, song)
                val songBitrate = song.bitrate ?: 0
                val songSampleRate = song.sampleRate ?: 0
                val songChannels = song.channels ?: 0

                val bitrateKbps = if (songBitrate > 0) songBitrate / 1000 else if (formatInfo.bitrateKbps > 0) formatInfo.bitrateKbps else 0
                val sampleRateHz = if (songSampleRate > 0) songSampleRate else if (formatInfo.sampleRateHz > 0) formatInfo.sampleRateHz else 0
                val channelCount = if (songChannels > 0) songChannels else if (formatInfo.channelCount > 0) formatInfo.channelCount else 2

                audioQuality = AudioQualityDetector.detectQuality(
                    codec = formatInfo.codec,
                    sampleRateHz = sampleRateHz,
                    bitrateKbps = bitrateKbps,
                    bitDepth = formatInfo.bitDepth,
                    channelCount = channelCount
                )
            } catch (e: Exception) {
                Log.e("AudioQualityIcon", "Failed to detect audio quality", e)
            }
        }
    }

    // Auto-hide timer: starts only when quality has been detected for the current song,
    // so the fade-in is not wasted while detection is still running.
    var autoHideVisible by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id, audioQuality != null, revealTrigger) {
        if (autoHideAfterMs > 0L && audioQuality != null) {
            autoHideVisible = true
            delay(autoHideAfterMs)
            autoHideVisible = false
        }
    }

    audioQuality?.let { quality ->
        val shouldShowIcon = quality.isLossless || quality.isDolby || quality.isDTS || quality.isHiRes ||
                           quality.qualityType != AudioQualityDetector.QualityType.UNKNOWN

        if (shouldShowIcon) {
            val iconRes = playbackQuality?.iconResId?.takeIf { it != 0 } ?: when (quality.qualityType) {
                AudioQualityDetector.QualityType.DSD_HIGH_RES -> R.drawable.ic_high_res
                AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER -> R.drawable.ic_high_res
                AudioQualityDetector.QualityType.HI_RES_LOSSLESS -> R.drawable.ic_high_res
                AudioQualityDetector.QualityType.DOLBY_LOSSLESS -> R.drawable.ic_dolby
                AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND -> R.drawable.ic_dolby
                AudioQualityDetector.QualityType.DTS_SURROUND -> R.drawable.ic_dts
                AudioQualityDetector.QualityType.LOSSLESS_SURROUND -> R.drawable.ic_surround_sound
                AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS -> R.drawable.ic_hd
                AudioQualityDetector.QualityType.LOSSY_COMPRESSED -> if (quality.qualityDescription.contains("320")) R.drawable.ic_hq else R.drawable.ic_sq
                else -> null
            }

            iconRes?.let { res ->
                val qualityIcon = @Composable {
                    Icon(
                        painter = painterResource(id = res),
                        contentDescription = quality.qualityLabel,
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                            .padding(padding)
                            .size(iconSize),
                        tint = tint ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                if (autoHideAfterMs > 0L) {
                    AnimatedVisibility(
                        visible = autoHideVisible,
                        enter = fadeIn(animationSpec = tween(400)),
                        exit = fadeOut(animationSpec = tween(600)),
                        modifier = modifier
                    ) {
                        qualityIcon()
                    }
                } else {
                    Box(modifier = modifier) { qualityIcon() }
                }
            }
        }
    }
}

@Composable
fun AudioQualityBadges(
    song: Song,
    modifier: Modifier = Modifier,
    playbackQuality: PlaybackQualityInfo? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Get enhanced audio quality information
    var audioQuality by remember(song.id) { mutableStateOf<AudioQualityDetector.AudioQuality?>(null) }

    LaunchedEffect(song.id, playbackQuality) {
        if (playbackQuality != null) {
            audioQuality = AudioQualityDetector.AudioQuality(
                qualityType = playbackQuality.qualityType,
                qualityLabel = playbackQuality.qualityLabel,
                qualityDescription = playbackQuality.qualityDescription,
                isLossless = playbackQuality.qualityType in listOf(
                    AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS,
                    AudioQualityDetector.QualityType.HI_RES_LOSSLESS,
                    AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER,
                    AudioQualityDetector.QualityType.DSD_HIGH_RES,
                    AudioQualityDetector.QualityType.DOLBY_LOSSLESS,
                    AudioQualityDetector.QualityType.LOSSLESS_SURROUND
                ),
                isHiRes = playbackQuality.qualityType in listOf(
                    AudioQualityDetector.QualityType.HI_RES_LOSSLESS,
                    AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER,
                    AudioQualityDetector.QualityType.DSD_HIGH_RES
                ),
                isDolby = playbackQuality.qualityType in listOf(
                    AudioQualityDetector.QualityType.DOLBY_LOSSLESS,
                    AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND
                ),
                isDTS = playbackQuality.qualityType == AudioQualityDetector.QualityType.DTS_SURROUND,
                bitDepthEstimate = playbackQuality.decodedBitDepth,
                category = if (playbackQuality.qualityType == AudioQualityDetector.QualityType.LOSSY_COMPRESSED) "Lossy" else "Lossless"
            )
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val formatInfo = AudioFormatDetector.detectFormat(context, song.uri, song)
                val songBitrate = song.bitrate ?: 0
                val songSampleRate = song.sampleRate ?: 0
                val songChannels = song.channels ?: 0

                val bitrateKbps = if (songBitrate > 0) {
                    songBitrate / 1000
                } else if (formatInfo.bitrateKbps > 0) {
                    formatInfo.bitrateKbps
                } else {
                    0
                }
                
                val sampleRateHz = if (songSampleRate > 0) {
                    songSampleRate
                } else if (formatInfo.sampleRateHz > 0) {
                    formatInfo.sampleRateHz
                } else {
                    0
                }
                
                val channelCount = if (songChannels > 0) {
                    songChannels
                } else if (formatInfo.channelCount > 0) {
                    formatInfo.channelCount
                } else {
                    2
                }
                
                audioQuality = AudioQualityDetector.detectQuality(
                    codec = formatInfo.codec,
                    sampleRateHz = sampleRateHz,
                    bitrateKbps = bitrateKbps,
                    bitDepth = formatInfo.bitDepth,
                    channelCount = channelCount
                )
            } catch (e: Exception) {
                Log.e("AudioQualityBadges", "Failed to detect audio quality", e)
            }
        }
    }

    audioQuality?.let { quality ->
        // Only show badges for meaningful quality indicators
        val shouldShowBadges = quality.isLossless || quality.isDolby || quality.isDTS || quality.isHiRes ||
                              quality.qualityType != AudioQualityDetector.QualityType.UNKNOWN

        if (shouldShowBadges) {
            Row(
                modifier = modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (quality.qualityType) {
                    AudioQualityDetector.QualityType.DSD_HIGH_RES -> {
                        QualityBadge(
                            text = quality.qualityLabel,
                            icon = R.drawable.ic_high_res,
                            qualityLevel = QualityLevel.EXCELLENT,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER -> {
                        QualityBadge(
                            text = stringResource(R.string.audioquality_studio_master),
                            icon = R.drawable.ic_high_res,
                            qualityLevel = QualityLevel.EXCELLENT,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.DOLBY_LOSSLESS -> {
                        val badgeText = when {
                            quality.qualityLabel.contains("Atmos", ignoreCase = true) -> "DOLBY ATMOS"
                            quality.qualityLabel.contains("TrueHD", ignoreCase = true) -> "DOLBY TRUEHD"
                            else -> "DOLBY"
                        }
                        QualityBadge(
                            text = badgeText,
                            icon = R.drawable.ic_dolby,
                            qualityLevel = QualityLevel.EXCELLENT,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND -> {
                        val badgeText = when {
                            quality.qualityLabel.contains("Plus", ignoreCase = true) -> "DOLBY DIGITAL+"
                            else -> "DOLBY D"
                        }
                        QualityBadge(
                            text = badgeText,
                            icon = R.drawable.ic_dolby,
                            qualityLevel = QualityLevel.STANDARD,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.DTS_SURROUND -> {
                        val badgeText = when {
                            quality.isLossless -> "DTS-HD MA"
                            else -> "DTS"
                        }
                        QualityBadge(
                            text = badgeText,
                            icon = R.drawable.ic_dts,
                            qualityLevel = if (quality.isLossless) QualityLevel.EXCELLENT else QualityLevel.STANDARD,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.LOSSLESS_SURROUND -> {
                        QualityBadge(
                            text = quality.qualityLabel.uppercase(),
                            icon = R.drawable.ic_surround_sound,
                            qualityLevel = QualityLevel.EXCELLENT,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.HI_RES_LOSSLESS -> {
                        QualityBadge(
                            text = stringResource(R.string.audioquality_hi_res_lossless),
                            icon = R.drawable.ic_high_res,
                            qualityLevel = QualityLevel.GOOD,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS -> {
                        QualityBadge(
                            text = stringResource(R.string.audioquality_lossless),
                            icon = R.drawable.ic_hd,
                            qualityLevel = QualityLevel.GOOD,
                            onClick = onClick
                        )
                    }

                    AudioQualityDetector.QualityType.LOSSY_COMPRESSED -> {
                        if (quality.qualityDescription.contains("320")) {
                            QualityBadge(
                                text = "320 KBPS",
                                icon = R.drawable.ic_hq,
                                qualityLevel = QualityLevel.STANDARD,
                                onClick = onClick
                            )
                        } else {
                            QualityBadge(
                                text = quality.qualityLabel.ifBlank { "SQ" },
                                icon = R.drawable.ic_sq,
                                qualityLevel = QualityLevel.STANDARD,
                                onClick = onClick
                            )
                        }
                    }

                    AudioQualityDetector.QualityType.UNKNOWN -> {}
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Badge with flat container colors and no visual chrome
 */
@Composable
private fun QualityBadge(
    text: String,
    icon: Any,
    qualityLevel: QualityLevel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val (containerColor, contentColor) = when (qualityLevel) {
        QualityLevel.EXCELLENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        QualityLevel.GOOD -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        QualityLevel.STANDARD -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    val badgeShape = RoundedCornerShape(10.dp)
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialScale = 0.94f
        ),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clip(badgeShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            shape = badgeShape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (icon) {
                    is MaterialSymbolIcon -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                    }
                    is Int -> {
                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                    }
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Legacy badge composable for backwards compatibility
 */
@Composable
private fun LegacyQualityBadge(
    text: String,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    qualityLevel: QualityLevel,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (qualityLevel) {
        QualityLevel.EXCELLENT -> 
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        QualityLevel.GOOD -> 
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        QualityLevel.STANDARD -> 
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = contentColor,
            leadingIconContentColor = contentColor
        ),
        border = null,
        modifier = modifier.height(28.dp)
    )
}
