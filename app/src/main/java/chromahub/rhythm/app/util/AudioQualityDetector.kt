/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.util.Log

/**
 * Audio quality categorization utility based on sample rate, bitrate, bit depth, and codec
 * 
 * LOSSLESS AUDIO DEFINITION:
 * Lossless audio refers to a file format that preserves all the original audio data from the 
 * source without any degradation, unlike lossy formats such as MP3 or AAC, which remove some 
 * data to reduce file size. Apple Music uses the Apple Lossless Audio Codec (ALAC) for its 
 * lossless tier, which ensures the audio is a bit-perfect copy of the original master recording.
 * 
 * STANDARD LOSSLESS vs HIGH-RESOLUTION LOSSLESS:
 * - Standard Lossless (CD Quality): 16-bit depth, 44.1 kHz sample rate
 *   * CD quality is defined as 16-bit depth and a 44.1 kHz sample rate
 *   * A 16-bit audio file can achieve approximately 96 dB of dynamic range
 *   * 44.1 kHz sample rate can reproduce frequencies up to 22.05 kHz (just above human hearing ~20 kHz)
 *   * Typical bitrate: ~1,411 kbps uncompressed, 200-900 kbps compressed (ALAC/FLAC)
 * 
 * - High-Resolution Lossless: 24-bit depth, 96 kHz or higher sample rate
 *   * Uses higher bit depths and sample rates than standard CD quality
 *   * 24-bit depth provides approximately 144 dB of dynamic range (48 dB more than 16-bit)
 *   * Sample rates of 96 kHz, 192 kHz, or higher capture more audio information
 *   * Higher sample rates can reproduce frequencies beyond human hearing (though audibility is debated)
 *   * Typical bitrate: 2,000-4,600 kbps at 96 kHz, 4,600-9,200+ kbps at 192 kHz
 * 
 * CALCULATING THE DIFFERENCE:
 * The difference between standard lossless and high-resolution lossless is calculated by 
 * comparing the bit depth and sample rate metadata:
 * - Bit depth determines the dynamic range (signal-to-noise ratio)
 * - Sample rate determines the frequency range that can be accurately reproduced
 * - A song with 16-bit/44.1 kHz is standard lossless (CD quality)
 * - A song with 24-bit/96 kHz or 24-bit/192 kHz is high-resolution lossless
 * 
 * Quality Levels (Based on industry standards):
 * - Lossy Compressed: MP3, AAC, OGG (128-320 kbps, 44.1 kHz, 16-bit after decoding)
 * - CD Quality (Lossless): FLAC, ALAC, WAV (~1,411 kbps, 44.1 kHz, 16-bit)
 * - Hi-Res Lossless: FLAC/ALAC (2,000-4,600 kbps, 48-96 kHz, 24-bit)
 * - Studio Master: FLAC/ALAC (4,600-9,200+ kbps, 192 kHz, 24-bit)
 * - Dolby Atmos (Spatial Audio): EAC3-JOC, AC-4 IMS (Lossy, 384-768 kbps) or TrueHD (Lossless, 4,000-18,000 kbps)
 * - Dolby Digital / Dolby Digital Plus (Lossy Surround): AC-3, E-AC-3 (192-640 kbps, 48 kHz)
 * - Dolby TrueHD (Lossless Surround): (4,000-18,000 kbps, 48-192 kHz, 24-bit)
 * - DTS-HD Master Audio (Lossless): (4,000-18,000 kbps, 48-192 kHz, 24-bit)
 */
object AudioQualityDetector {
    private const val TAG = "AudioQualityDetector"

    /**
     * Represents the audio quality tier
     */
    enum class QualityType {
        LOSSY_COMPRESSED,      // MP3, AAC, OGG (128-320 kbps)
        CD_QUALITY_LOSSLESS,   // FLAC, ALAC 16/44.1
        HI_RES_LOSSLESS,       // FLAC, ALAC 24/48+ kHz
        HI_RES_STUDIO_MASTER,  // FLAC, ALAC 24/192 kHz
        LOSSLESS_SURROUND,     // e.g., FLAC 5.1, WAV 5.1
        DOLBY_ATMOS,           // Dolby Atmos (EAC3-JOC, AC-4 Atmos, TrueHD Atmos)
        DOLBY_LOSSY_SURROUND,  // AC-3, E-AC-3, AC-4
        DOLBY_LOSSLESS,        // TrueHD (without Atmos)
        DTS_SURROUND,          // DTS variants (DTS, DTS-HD MA, DTS:X)
        MPEG_H_3D,             // MPEG-H 3D Audio (immersive/spatial)
        DSD_HIGH_RES,          // DSD64, DSD128, DSD256, DSD512 (1-bit high-end audiophile)
        UNKNOWN
    }

    /**
     * Detailed audio quality information
     */
    data class AudioQuality(
        val qualityType: QualityType,
        val qualityLabel: String,        // e.g., "Dolby Atmos", "Hi-Res Lossless", "CD Quality"
        val qualityDescription: String,  // e.g., "768 kbps / 48 kHz Spatial Audio"
        val isLossless: Boolean,
        val isHiRes: Boolean,
        val isDolby: Boolean,
        val isDTS: Boolean,
        val bitDepthEstimate: Int,       // 16 or 24 bit (or 0 if unknown)
        val category: String             // "Spatial Audio", "Dolby Audio", "Lossless", "Lossy"
    )

    /**
     * Determine audio quality based on codec, sample rate, bitrate, and bit depth
     * 
     * @param codec Audio codec (e.g., "FLAC", "MP3", "AAC", "EAC3-JOC", "AC-3")
     * @param sampleRateHz Sample rate in Hz (e.g., 44100, 48000, 96000)
     * @param bitrateKbps Bitrate in kbps (e.g., 320, 768, 1411, 4608)
     * @param bitDepth Bit depth if known (16, 24, or 0 if unknown)
     * @param channelCount Number of audio channels (1=mono, 2=stereo, 6=5.1, etc.)
     * @return AudioQuality with detailed quality information
     */
    fun detectQuality(
        codec: String,
        sampleRateHz: Int,
        bitrateKbps: Int,
        bitDepth: Int = 0,
        channelCount: Int = 2
    ): AudioQuality {
        val normalizedCodec = codec.uppercase().trim()
        val sampleRateKhz = sampleRateHz / 1000.0

        // Detect Dolby Atmos specifically (object-based spatial audio)
        val isDolbyAtmos = normalizedCodec.contains("ATMOS") ||
                normalizedCodec.contains("EAC3-JOC") ||
                normalizedCodec.contains("EAC3_JOC") ||
                normalizedCodec.contains("E-AC-3-JOC") ||
                normalizedCodec.contains("EC-3-JOC") ||
                normalizedCodec.contains("EC3-JOC") ||
                normalizedCodec.contains("JOC") ||
                (normalizedCodec.contains("AC-4") && normalizedCodec.contains("IMS")) ||
                (normalizedCodec.contains("AC4") && normalizedCodec.contains("IMS"))

        // Detect TrueHD / MLP lossless Dolby
        val isTrueHD = normalizedCodec.contains("TRUEHD") || normalizedCodec.contains("MLP")

        // Detect Dolby Digital Plus (E-AC-3, without Atmos)
        val isDolbyDigitalPlus = !isDolbyAtmos && (
            normalizedCodec.contains("E-AC-3") || normalizedCodec.contains("EAC3") ||
            normalizedCodec.contains("EC-3") || normalizedCodec.contains("EC3") ||
            normalizedCodec.contains("DOLBY DIGITAL PLUS") || normalizedCodec.contains("DD+")
        )

        // Detect Dolby Digital (AC-3, without Atmos)
        val isDolbyDigital = !isDolbyAtmos && !isDolbyDigitalPlus && (
            normalizedCodec.contains("AC-3") || normalizedCodec.contains("AC3") ||
            normalizedCodec.contains("DOLBY DIGITAL") || normalizedCodec == "DD"
        )

        // Detect Dolby AC-4 (without Atmos)
        val isDolbyAC4 = !isDolbyAtmos && (normalizedCodec.contains("AC-4") || normalizedCodec.contains("AC4"))

        // Detect MPEG-H 3D Audio
        val isMpegHCodec = normalizedCodec.contains("MPEG-H") || normalizedCodec.contains("MPEGH") ||
                normalizedCodec.contains("MHM1") || normalizedCodec.contains("MHM")

        // Detect DTS variants
        val isDTSCodec = normalizedCodec.contains("DTS")

        // Any Dolby codec
        val isDolbyCodec = isDolbyAtmos || isTrueHD || isDolbyDigitalPlus || isDolbyDigital || isDolbyAC4 || normalizedCodec.contains("DOLBY")

        // Explicitly lossy codecs
        val isLossyCodec = (!isTrueHD) && (
            isDolbyDigital || isDolbyDigitalPlus || isDolbyAC4 || isMpegHCodec ||
            (isDolbyAtmos && !isTrueHD) ||
            normalizedCodec in listOf("MP3", "AAC", "OGG", "OPUS", "VORBIS", "MP2", "AMR", "AMR-NB", "AMR-WB", "LC3", "CELT", "DRA") ||
            normalizedCodec.contains("MP3") || normalizedCodec.contains("AAC") ||
            normalizedCodec.contains("OGG") || normalizedCodec.contains("OPUS") ||
            normalizedCodec.contains("VORBIS") ||
            (normalizedCodec.contains("WMA") && !normalizedCodec.contains("LOSSLESS"))
        )

        // Determine if codec is inherently lossless
        val isLosslessCodec = !isLossyCodec && (
            normalizedCodec in listOf(
                "FLAC", "ALAC", "WAV", "PCM", "APE", "DSD", "AIFF", "AIF", "WMA LOSSLESS",
                "TRUEHD", "MLP", "DTS-HD", "DTS-HD MA", "MIDI", "MID", "WV", "WAVPACK", "TAK", "TTA",
                "APPLE LOSSLESS", "FLAC LOSSLESS"
            ) ||
            normalizedCodec.contains("LOSSLESS") ||
            normalizedCodec.contains("FLAC") ||
            normalizedCodec.contains("ALAC") ||
            normalizedCodec.contains("WAVPACK") ||
            normalizedCodec == "WV" ||
            normalizedCodec == "TAK" ||
            normalizedCodec == "TTA" ||
            isTrueHD
        )

        // Bitrate heuristic for unknown codecs
        val isLikelyLosslessFromBitrate = !isLossyCodec &&
                !isLosslessCodec &&
                (normalizedCodec.isEmpty() || normalizedCodec == "UNKNOWN" || normalizedCodec == "AUDIO") &&
                sampleRateHz in 44000..48000 &&
                bitrateKbps > 900

        val finalIsLossless = isLosslessCodec || isLikelyLosslessFromBitrate

        // Estimate bit depth if not provided
        val estimatedBitDepth = when {
            normalizedCodec.contains("DSD") || normalizedCodec == "DSD" -> 1
            bitDepth > 0 -> {
                Log.d(TAG, "Using provided bit depth: $bitDepth-bit")
                bitDepth
            }
            finalIsLossless && bitrateKbps > 0 && sampleRateHz > 0 && channelCount > 0 -> {
                val isCompressedLossless = normalizedCodec != "WAV" && normalizedCodec != "PCM" && normalizedCodec != "AIFF" && normalizedCodec != "DSD"
                val rawBitrate = if (isCompressedLossless) (bitrateKbps * 1000.0 / 0.55) else (bitrateKbps * 1000.0)
                val calculated = (rawBitrate / (sampleRateHz * channelCount)).toInt()
                val result = when {
                    calculated >= 30 -> 32
                    calculated >= 22 -> 24
                    calculated >= 14 -> 16
                    calculated >= 12 -> 16
                    else -> 0
                }
                Log.d(TAG, "Bit depth from bitrate (adjusted for compression=$isCompressedLossless): codec=$normalizedCodec, " +
                        "bitrate=${bitrateKbps}kbps, sampleRate=${sampleRateHz}Hz, channels=$channelCount → $result-bit")
                result
            }
            finalIsLossless && sampleRateHz >= 48000 -> 24
            finalIsLossless -> 16
            else -> 16
        }

        Log.d(TAG, "Quality detection: codec=$normalizedCodec, sampleRate=${sampleRateHz}Hz, " +
                "bitrate=${bitrateKbps}kbps, bitDepth=$estimatedBitDepth-bit, channels=$channelCount, isLossless=$finalIsLossless")

        return when {
            // DSD (Direct Stream Digital)
            normalizedCodec.contains("DSD") || normalizedCodec == "DSD" || normalizedCodec.contains("DSF") || normalizedCodec.contains("DFF") -> {
                val dsdVersion = when {
                    sampleRateHz >= 22000000 -> "DSD512"
                    sampleRateHz >= 11000000 -> "DSD256"
                    sampleRateHz >= 5000000 -> "DSD128"
                    else -> "DSD64"
                }
                AudioQuality(
                    qualityType = QualityType.DSD_HIGH_RES,
                    qualityLabel = dsdVersion,
                    qualityDescription = "1-bit / ${(sampleRateHz / 1000000.0)} MHz DSD Audio",
                    isLossless = true,
                    isHiRes = true,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = 1,
                    category = "Hi-Res Master"
                )
            }

            // Dolby Atmos (Spatial Audio - can be lossy E-AC-3 JOC / AC-4 or lossless TrueHD Atmos)
            isDolbyAtmos -> {
                val isLosslessAtmos = isTrueHD || (bitrateKbps >= 3000 && estimatedBitDepth >= 24)
                AudioQuality(
                    qualityType = QualityType.DOLBY_ATMOS,
                    qualityLabel = "Dolby Atmos",
                    qualityDescription = if (isLosslessAtmos) {
                        "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless Spatial Audio"
                    } else if (bitrateKbps > 0) {
                        "$bitrateKbps kbps / ${sampleRateKhz.toInt()} kHz Spatial Audio"
                    } else {
                        "${sampleRateKhz.toInt()} kHz Spatial Audio"
                    },
                    isLossless = isLosslessAtmos,
                    isHiRes = isLosslessAtmos,
                    isDolby = true,
                    isDTS = false,
                    bitDepthEstimate = if (isLosslessAtmos) estimatedBitDepth else 16,
                    category = if (isLosslessAtmos) "Lossless Spatial Audio" else "Spatial Audio"
                )
            }

            // Dolby TrueHD (Lossless surround without Atmos)
            isTrueHD -> {
                AudioQuality(
                    qualityType = QualityType.DOLBY_LOSSLESS,
                    qualityLabel = "Dolby TrueHD",
                    qualityDescription = "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless Surround",
                    isLossless = true,
                    isHiRes = true,
                    isDolby = true,
                    isDTS = false,
                    bitDepthEstimate = estimatedBitDepth,
                    category = "Lossless Surround"
                )
            }

            // Dolby Digital Plus / Dolby Digital / Dolby AC-4 (Lossy surround)
            isDolbyDigitalPlus || isDolbyDigital || isDolbyAC4 -> {
                val label = when {
                    isDolbyDigitalPlus -> "Dolby Digital Plus"
                    isDolbyAC4 -> "Dolby AC-4"
                    else -> "Dolby Digital"
                }
                AudioQuality(
                    qualityType = QualityType.DOLBY_LOSSY_SURROUND,
                    qualityLabel = label,
                    qualityDescription = if (bitrateKbps > 0) "$bitrateKbps kbps / ${sampleRateKhz.toInt()} kHz Surround" else "${sampleRateKhz.toInt()} kHz Surround",
                    isLossless = false,
                    isHiRes = false,
                    isDolby = true,
                    isDTS = false,
                    bitDepthEstimate = 16,
                    category = "Dolby Audio"
                )
            }

            // MPEG-H 3D Audio (Spatial / Immersive audio)
            isMpegHCodec -> {
                AudioQuality(
                    qualityType = QualityType.MPEG_H_3D,
                    qualityLabel = "MPEG-H 3D Audio",
                    qualityDescription = if (bitrateKbps > 0) "$bitrateKbps kbps / ${sampleRateKhz.toInt()} kHz 3D Audio" else "3D Spatial Audio",
                    isLossless = false,
                    isHiRes = false,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = 16,
                    category = "Spatial Audio"
                )
            }

            // DTS (various forms: DTS, DTS-HD MA, DTS:X)
            isDTSCodec -> {
                val isLosslessDTS = normalizedCodec.contains("HD") || normalizedCodec.contains("MA")
                val isDtsX = normalizedCodec.contains("X") || normalizedCodec.contains("DTS:X") || normalizedCodec.contains("DTSX")
                val label = when {
                    isDtsX -> "DTS:X"
                    isLosslessDTS -> "DTS-HD Master Audio"
                    else -> "DTS"
                }
                AudioQuality(
                    qualityType = QualityType.DTS_SURROUND,
                    qualityLabel = label,
                    qualityDescription = if (isLosslessDTS)
                        "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless Surround"
                    else if (bitrateKbps > 0)
                        "$bitrateKbps kbps Surround"
                    else
                        "Surround Audio",
                    isLossless = isLosslessDTS,
                    isHiRes = isLosslessDTS,
                    isDolby = false,
                    isDTS = true,
                    bitDepthEstimate = if (isLosslessDTS) estimatedBitDepth else 16,
                    category = if (isLosslessDTS) "Lossless Surround" else "Lossy Surround"
                )
            }

            // Multichannel Lossless (5.1/7.1 FLAC, ALAC, WAV, PCM, etc.)
            finalIsLossless && channelCount > 2 -> {
                val channelLabel = when (channelCount) {
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> "${channelCount}ch"
                }
                AudioQuality(
                    qualityType = QualityType.LOSSLESS_SURROUND,
                    qualityLabel = "Multichannel Lossless $channelLabel",
                    qualityDescription = "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless",
                    isLossless = true,
                    isHiRes = estimatedBitDepth >= 24 || sampleRateHz > 48000,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = estimatedBitDepth,
                    category = "Lossless Surround"
                )
            }

            // Hi-Res Studio Master (192 kHz, 24-bit lossless, >4600 kbps)
            finalIsLossless && sampleRateHz >= 192000 && estimatedBitDepth >= 24 -> {
                AudioQuality(
                    qualityType = QualityType.HI_RES_STUDIO_MASTER,
                    qualityLabel = "Studio Master",
                    qualityDescription = "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless",
                    isLossless = true,
                    isHiRes = true,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = estimatedBitDepth,
                    category = "Lossless"
                )
            }

            // Hi-Res Lossless (48-96 kHz, 24-bit, >2000 kbps)
            finalIsLossless && sampleRateHz >= 48000 && estimatedBitDepth >= 24 -> {
                Log.d(TAG, "Detected: HI_RES_LOSSLESS (sampleRate=${sampleRateHz}Hz, bitDepth=$estimatedBitDepth-bit)")
                AudioQuality(
                    qualityType = QualityType.HI_RES_LOSSLESS,
                    qualityLabel = "Hi-Res Lossless",
                    qualityDescription = "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless",
                    isLossless = true,
                    isHiRes = true,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = estimatedBitDepth,
                    category = "Lossless"
                )
            }

            // CD Quality Lossless (44.1 kHz, 16-bit, ~1411 kbps)
            finalIsLossless && estimatedBitDepth >= 12 -> {
                Log.d(TAG, "Detected: CD_QUALITY_LOSSLESS (sampleRate=${sampleRateHz}Hz, bitDepth=$estimatedBitDepth-bit)")
                AudioQuality(
                    qualityType = QualityType.CD_QUALITY_LOSSLESS,
                    qualityLabel = "Lossless",
                    qualityDescription = "${estimatedBitDepth}-bit / ${sampleRateKhz.toInt()} kHz Lossless",
                    isLossless = true,
                    isHiRes = false,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = estimatedBitDepth,
                    category = "Lossless"
                )
            }

            // Invalid/Suspicious Lossless (bit depth too low)
            finalIsLossless && estimatedBitDepth < 12 -> {
                Log.w(TAG, "File claims lossless codec but bit depth is too low ($estimatedBitDepth-bit). Treating as LOSSY.")
                AudioQuality(
                    qualityType = QualityType.LOSSY_COMPRESSED,
                    qualityLabel = "Lossy",
                    qualityDescription = "$bitrateKbps kbps / ${sampleRateKhz.toInt()} kHz",
                    isLossless = false,
                    isHiRes = false,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = estimatedBitDepth,
                    category = "Lossy"
                )
            }

            // Traditional 2-channel Lossy Compressed (MP3, AAC, OGG, Opus, etc.)
            else -> {
                val label = when {
                    bitrateKbps >= 320 -> "320 kbps"
                    bitrateKbps >= 256 -> "256 kbps"
                    bitrateKbps >= 192 -> "192 kbps"
                    bitrateKbps >= 128 -> "128 kbps"
                    else -> "Lossy"
                }
                AudioQuality(
                    qualityType = QualityType.LOSSY_COMPRESSED,
                    qualityLabel = label,
                    qualityDescription = if (bitrateKbps > 0) "$bitrateKbps kbps / ${sampleRateKhz.toInt()} kHz" else "${sampleRateKhz.toInt()} kHz",
                    isLossless = false,
                    isHiRes = false,
                    isDolby = false,
                    isDTS = false,
                    bitDepthEstimate = 16,
                    category = "Lossy"
                )
            }
        }
    }

    /**
     * Get a simple quality badge label for UI display
     */
    fun getQualityBadge(quality: AudioQuality): String {
        return when (quality.qualityType) {
            QualityType.DSD_HIGH_RES -> quality.qualityLabel
            QualityType.HI_RES_STUDIO_MASTER -> "Studio Master"
            QualityType.HI_RES_LOSSLESS -> "Hi-Res"
            QualityType.CD_QUALITY_LOSSLESS -> "Lossless"
            QualityType.LOSSLESS_SURROUND -> quality.qualityLabel
            QualityType.DOLBY_ATMOS -> "Dolby Atmos"
            QualityType.DOLBY_LOSSLESS -> "Dolby TrueHD"
            QualityType.DOLBY_LOSSY_SURROUND -> when {
                quality.qualityLabel.contains("Plus") -> "Dolby Digital Plus"
                quality.qualityLabel.contains("AC-4") -> "Dolby AC-4"
                else -> "Dolby Digital"
            }
            QualityType.MPEG_H_3D -> "MPEG-H 3D"
            QualityType.DTS_SURROUND -> when {
                quality.qualityLabel.contains("X") -> "DTS:X"
                quality.isLossless -> "DTS-HD Master Audio"
                else -> "DTS"
            }
            QualityType.LOSSY_COMPRESSED -> when {
                quality.qualityDescription.contains("320") -> "320kbps"
                quality.qualityDescription.contains("256") -> "256kbps"
                quality.qualityDescription.contains("192") -> "192kbps"
                quality.qualityDescription.contains("128") -> "128kbps"
                else -> "Lossy"
            }
            QualityType.UNKNOWN -> ""
        }
    }

    /**
     * Determine if audio qualifies as "Hi-Res" (≥48 kHz or lossless with >2000 kbps)
     */
    fun isHiResAudio(sampleRateHz: Int, bitrateKbps: Int, isLossless: Boolean): Boolean {
        return sampleRateHz >= 48000 || (isLossless && bitrateKbps >= 2000)
    }

    /**
     * Get color indicator for quality level (for UI theming)
     * Returns: "excellent", "good", "standard", "unknown"
     */
    fun getQualityColorIndicator(quality: AudioQuality): String {
        return when (quality.qualityType) {
            QualityType.DSD_HIGH_RES,
            QualityType.HI_RES_STUDIO_MASTER, 
            QualityType.DOLBY_ATMOS,
            QualityType.DOLBY_LOSSLESS,
            QualityType.MPEG_H_3D,
            QualityType.LOSSLESS_SURROUND -> "excellent"
            
            QualityType.HI_RES_LOSSLESS, 
            QualityType.CD_QUALITY_LOSSLESS -> "good"
            
            QualityType.LOSSY_COMPRESSED,
            QualityType.DOLBY_LOSSY_SURROUND,
            QualityType.DTS_SURROUND -> "standard"
            
            QualityType.UNKNOWN -> "unknown"
        }
    }
}
