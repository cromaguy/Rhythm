package chromahub.rhythm.app.shared.presentation.components

import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.util.AudioQualityDetector
import chromahub.rhythm.app.util.AudioFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.unit.Dp

/**
 * Quality level for badge styling
 */
private enum class QualityLevel {
    EXCELLENT,  // Studio Master, Dolby Atmos/TrueHD, DTS-HD MA
    GOOD,       // Hi-Res Lossless, CD Quality Lossless
    STANDARD    // Dolby Digital, DTS, High-quality lossy
}

/**
 * Composable that displays audio quality badges (Lossless, Dolby, Hi-Res, etc.)
 * Updated to use the enhanced AudioQualityDetector for more accurate quality categorization
 * 
 * QUALITY BADGE LOGIC:
 * - Badges are based on codec, sample rate, bit depth, and bitrate analysis
 * - Lossless formats (ALAC, FLAC, WAV) preserve original audio bit-perfectly
 * - Lossy formats (MP3, AAC, OGG) are NEVER shown as lossless regardless of bitrate
 * - Standard Lossless (CD Quality): 16-bit/44.1kHz - shows "LOSSLESS" badge
 * - High-Resolution Lossless: 24-bit/96kHz+ - shows "HI-RES LOSSLESS" badge
 * - Bit depth is calculated from bitrate or explicitly provided by the codec
 */
@Composable
fun AudioQualityIcon(
    song: StreamingSong,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    padding: Dp = 8.dp
) {
    val context = LocalContext.current
    var audioQuality by remember(song.id) { mutableStateOf<AudioQualityDetector.AudioQuality?>(null) }

    LaunchedEffect(song.id) {
        val playbackUri = song.getPlaybackUri()
        if (playbackUri.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(playbackUri)
                val formatInfo = AudioFormatDetector.detectFormat(context, uri)

                val bitrateKbps = if (formatInfo.bitrateKbps > 0) formatInfo.bitrateKbps else 0
                val sampleRateHz = if (formatInfo.sampleRateHz > 0) formatInfo.sampleRateHz else 0
                val channelCount = if (formatInfo.channelCount > 0) formatInfo.channelCount else 2

                audioQuality = AudioQualityDetector.detectQuality(
                    codec = formatInfo.codec,
                    sampleRateHz = sampleRateHz,
                    bitrateKbps = bitrateKbps,
                    bitDepth = formatInfo.bitDepth,
                    channelCount = channelCount
                )
            } catch (e: Exception) {
                Log.e("AudioQualityIcon", "Failed to detect audio quality for streaming song", e)
            }
        }
    }

    audioQuality?.let { quality ->
        val shouldShowIcon = quality.isLossless || quality.isDolby || quality.isDTS || quality.isHiRes ||
                           quality.qualityType != AudioQualityDetector.QualityType.LOSSY_COMPRESSED

        if (shouldShowIcon) {
            val iconRes = when (quality.qualityType) {
                AudioQualityDetector.QualityType.DSD_HIGH_RES,
                AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER,
                AudioQualityDetector.QualityType.HI_RES_LOSSLESS -> R.drawable.ic_high_res
                AudioQualityDetector.QualityType.DOLBY_LOSSLESS,
                AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND -> R.drawable.ic_dolby
                AudioQualityDetector.QualityType.DTS_SURROUND -> R.drawable.ic_dts
                AudioQualityDetector.QualityType.LOSSLESS_SURROUND -> R.drawable.ic_surround_sound
                AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS -> R.drawable.ic_cd
                AudioQualityDetector.QualityType.LOSSY_COMPRESSED -> if (quality.qualityDescription.contains("320")) R.drawable.ic_hq else null
                else -> null
            }

            iconRes?.let { res ->
                Icon(
                    painter = painterResource(id = res),
                    contentDescription = quality.qualityLabel,
                    modifier = modifier
                        .padding(padding)
                        .size(iconSize),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AudioQualityIcon(
    song: Song,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    padding: Dp = 8.dp,
    tint: Color? = null
) {
    val context = LocalContext.current
    var audioQuality by remember(song.id) { mutableStateOf<AudioQualityDetector.AudioQuality?>(null) }

    LaunchedEffect(song.id) {
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

    audioQuality?.let { quality ->
        val shouldShowIcon = quality.isLossless || quality.isDolby || quality.isDTS || quality.isHiRes ||
                           quality.qualityType != AudioQualityDetector.QualityType.LOSSY_COMPRESSED

        if (shouldShowIcon) {
            val iconRes = when (quality.qualityType) {
                AudioQualityDetector.QualityType.DSD_HIGH_RES,
                AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER,
                AudioQualityDetector.QualityType.HI_RES_LOSSLESS -> R.drawable.ic_high_res
                AudioQualityDetector.QualityType.DOLBY_LOSSLESS,
                AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND -> R.drawable.ic_dolby
                AudioQualityDetector.QualityType.DTS_SURROUND -> R.drawable.ic_dts
                AudioQualityDetector.QualityType.LOSSLESS_SURROUND -> R.drawable.ic_surround_sound
                AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS -> R.drawable.ic_cd
                AudioQualityDetector.QualityType.LOSSY_COMPRESSED -> if (quality.qualityDescription.contains("320")) R.drawable.ic_hq else null
                else -> null
            }

            iconRes?.let { res ->
                Icon(
                    painter = painterResource(id = res),
                    contentDescription = quality.qualityLabel,
                    modifier = modifier
                        .padding(padding)
                        .size(iconSize),
                    tint = tint ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AudioQualityBadges(

    song: Song,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Get enhanced audio quality information
    var audioQuality by remember(song.id) { mutableStateOf<AudioQualityDetector.AudioQuality?>(null) }

    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) {
            try {
                // First try to get format info for codec detection
                // Pass song object for better bit depth calculation
                val formatInfo = AudioFormatDetector.detectFormat(context, song.uri, song)

                // Use the enhanced quality detector with format info
                // Prefer Song's metadata when available as it's more reliable
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
                
                Log.d("AudioQualityBadges", "Song metadata: bitrate=${song.bitrate}, sampleRate=${song.sampleRate}, channels=${song.channels}, codec=${song.codec}")
                Log.d("AudioQualityBadges", "Format info: codec=${formatInfo.codec}, " +
                        "sampleRate=${formatInfo.sampleRateHz}Hz, bitrate=${formatInfo.bitrateKbps}kbps, " +
                        "bitDepth=${formatInfo.bitDepth}-bit, channels=${formatInfo.channelCount}")
                Log.d("AudioQualityBadges", "Using for detection: codec=${formatInfo.codec}, " +
                        "sampleRate=${sampleRateHz}Hz, bitrate=${bitrateKbps}kbps, " +
                        "bitDepth=${formatInfo.bitDepth}-bit, channels=${channelCount}")
                
                audioQuality = AudioQualityDetector.detectQuality(
                    codec = formatInfo.codec,
                    sampleRateHz = sampleRateHz,
                    bitrateKbps = bitrateKbps,
                    bitDepth = formatInfo.bitDepth,
                    channelCount = channelCount
                )
                
                Log.d("AudioQualityBadges", "Badge quality result: ${audioQuality?.qualityType} - ${audioQuality?.qualityLabel}")
            } catch (e: Exception) {
                Log.e("AudioQualityBadges", "Failed to detect audio quality", e)
            }
        }
    }

    audioQuality?.let { quality ->
        // Only show badges for meaningful quality indicators
        val shouldShowBadges = quality.isLossless || quality.isDolby || quality.isDTS || quality.isHiRes ||
                              quality.qualityType != AudioQualityDetector.QualityType.LOSSY_COMPRESSED

        if (shouldShowBadges) {
            Row(
                modifier = modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary quality badge based on type with enhanced color coding
                when (quality.qualityType) {
                    AudioQualityDetector.QualityType.DSD_HIGH_RES -> {
                        QualityBadge(
                            text = quality.qualityLabel,
                            icon = R.drawable.ic_high_res,
                            qualityLevel = QualityLevel.EXCELLENT
                        )
                    }

                    AudioQualityDetector.QualityType.HI_RES_STUDIO_MASTER -> {
                        QualityBadge(
                            text = "STUDIO MASTER",
                            icon = R.drawable.ic_high_res,
                            qualityLevel = QualityLevel.EXCELLENT
                        )
                    }

                    AudioQualityDetector.QualityType.DOLBY_LOSSLESS -> {
                        val badgeText = when {
                            quality.qualityLabel.contains("Atmos") -> "DOLBY ATMOS"
                            quality.qualityLabel.contains("TrueHD") -> "DOLBY TRUEHD"
                            else -> "DOLBY"
                        }
                        QualityBadge(
                            text = badgeText,
                            icon = R.drawable.ic_dolby,
                            qualityLevel = QualityLevel.EXCELLENT
                        )
                    }

                    AudioQualityDetector.QualityType.DOLBY_LOSSY_SURROUND -> {
                        val badgeText = when {
                            quality.qualityLabel.contains("Plus") -> "DOLBY DIGITAL+"
                            else -> "DOLBY D"
                        }
                        QualityBadge(
                            text = badgeText,
                            icon = R.drawable.ic_dolby,
                            qualityLevel = QualityLevel.STANDARD
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
                            qualityLevel = if (quality.isLossless) QualityLevel.EXCELLENT else QualityLevel.STANDARD
                        )
                    }

                    AudioQualityDetector.QualityType.LOSSLESS_SURROUND -> {
                        // Show full label: "DOLBY SURROUND 5.1" or "DOLBY SURROUND 7.1"
                        QualityBadge(
                            text = quality.qualityLabel.uppercase(),
                            icon = R.drawable.ic_surround_sound,
                            qualityLevel = QualityLevel.EXCELLENT
                        )
                    }

                    AudioQualityDetector.QualityType.HI_RES_LOSSLESS -> {
                        QualityBadge(
                            text = "HI-RES LOSSLESS",
                            icon = R.drawable.ic_high_res,
                            qualityLevel = QualityLevel.GOOD
                        )
                    }

                    AudioQualityDetector.QualityType.CD_QUALITY_LOSSLESS -> {
                        QualityBadge(
                            text = "LOSSLESS",
                            icon = R.drawable.ic_cd,
                            qualityLevel = QualityLevel.GOOD
                        )
                    }

                    AudioQualityDetector.QualityType.LOSSY_COMPRESSED -> {
                        // Only show high-quality lossy badges (320kbps+)
                        if (quality.qualityDescription.contains("320")) {
                            QualityBadge(
                                text = "320 KBPS",
                                icon = R.drawable.ic_hq,
                                qualityLevel = QualityLevel.STANDARD
                            )
                        }
                    }

                    AudioQualityDetector.QualityType.UNKNOWN -> {
                        // Don't show unknown quality badges
                    }
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
    modifier: Modifier = Modifier
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
                .padding(horizontal = 4.dp),
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
