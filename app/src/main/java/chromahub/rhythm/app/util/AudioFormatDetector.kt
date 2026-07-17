package chromahub.rhythm.app.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import chromahub.rhythm.app.shared.data.model.Song

/**
 * Utility class for detecting advanced audio formats and codecs
 * Detects ALAC, Dolby (AC-3, E-AC-3, Dolby Atmos), DTS, and lossless formats
 * 
 * LOSSLESS DETECTION LOGIC:
 * - Lossless formats (ALAC, FLAC, WAV, PCM) preserve all original audio data bit-perfectly
 * - Lossy formats (MP3, AAC, OGG) discard data during encoding - NEVER lossless
 * - Detection is based on codec identification, NOT bitrate or bit depth alone
 * - High bitrate does NOT make lossy codec lossless (e.g., 320kbps MP3 is still lossy)
 * 
 * BIT DEPTH CALCULATION:
 * - For lossless formats, bit depth is calculated from: bitrate / (sample rate × channels)
 * - Standard CD: 16-bit/44.1kHz = ~1,411 kbps → 16 bits/sample
 * - Hi-Res: 24-bit/96kHz = ~4,608 kbps → 24 bits/sample
 * - MediaExtractor often reports PCM decoder format (16-bit) not source bit depth
 * - We recalculate from bitrate for accurate lossless bit depth determination
 */
object AudioFormatDetector {
    private const val TAG = "AudioFormatDetector"

    data class AudioFormatInfo(
        val codec: String = "Unknown",
        val isLossless: Boolean = false,
        val isDolby: Boolean = false,
        val isDTS: Boolean = false,
        val isHiRes: Boolean = false,
        val bitDepth: Int = 0,
        val sampleRateHz: Int = 0,
        val channelCount: Int = 0,
        val bitrateKbps: Int = 0,
        val formatName: String = "Unknown"
    )

    /**
     * Detect comprehensive audio format information from a URI
     */
    fun detectFormat(context: Context, uri: Uri): AudioFormatInfo {
        return detectFormat(context, uri, null)
    }
    
    /**
     * Detect comprehensive audio format information from a URI with optional Song metadata
     * for better bit depth calculation
     */
    fun detectFormat(context: Context, uri: Uri, song: Song?): AudioFormatInfo {
        var formatInfo = AudioFormatInfo()

        try {
            // Try MediaExtractor first for codec details
            formatInfo = detectUsingMediaExtractor(context, uri) ?: formatInfo
            
            // Fallback to MediaMetadataRetriever for additional metadata
            val retrieverInfo = detectUsingMetadataRetriever(context, uri)
            formatInfo = mergeFormatInfo(formatInfo, retrieverInfo)
            
            // Always recalculate bit depth from Song metadata if available for lossless
            // This is more accurate than MediaExtractor/MediaMetadataRetriever
            // MediaExtractor often returns PCM decoding format (16-bit) instead of source bit depth
            if (song != null && formatInfo.isLossless) {
                Log.d(TAG, "Recalculating bit depth for lossless format using Song metadata")
                formatInfo = enhanceBitDepthFromSong(formatInfo, song)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting audio format for $uri", e)
        }

        return formatInfo
    }
    
    /**
     * Enhance bit depth calculation using Song metadata when AudioFormat doesn't provide it
     * For lossless audio, ALWAYS recalculate to ensure accuracy
     */
    private fun enhanceBitDepthFromSong(formatInfo: AudioFormatInfo, song: Song): AudioFormatInfo {
        val codec = formatInfo.codec.uppercase()
        if (codec.contains("DSD") || codec == "DSD") {
            return formatInfo.copy(bitDepth = 1)
        }
        if (formatInfo.bitDepth > 0 && formatInfo.isLossless) {
            Log.d(TAG, "Keeping precise bit depth (${formatInfo.bitDepth}-bit) over heuristic.")
            return formatInfo
        }
        val bitrate = song.bitrate ?: 0
        val sampleRate = song.sampleRate ?: formatInfo.sampleRateHz
        val channels = song.channels ?: formatInfo.channelCount
        
        if (bitrate > 0 && sampleRate > 0 && channels > 0 && formatInfo.isLossless) {
            val bitrateKbps = bitrate / 1000
            val isCompressedLossless = codec != "WAV" && codec != "PCM" && codec != "AIFF" && codec != "DSD"
            val rawBitrate = if (isCompressedLossless) (bitrateKbps * 1000.0 / 0.55) else (bitrateKbps * 1000.0)
            val calculatedBitDepth = (rawBitrate / (sampleRate * channels)).toInt()
            
            Log.d(TAG, "Calculating bit depth from Song metadata (adjusted for compression=$isCompressedLossless): bitrate=${bitrateKbps}kbps, sampleRate=${sampleRate}Hz, channels=$channels, calculated=$calculatedBitDepth bits/sample (current=${formatInfo.bitDepth}-bit)")
            
            // Use accurate thresholds based on actual bit depths
            // CD: 44.1kHz/16-bit/stereo = 1,411 kbps → 16.0 bits/sample
            // Hi-Res: 96kHz/24-bit/stereo = 4,608 kbps → 24.0 bits/sample
            val bitDepth = when {
                calculatedBitDepth >= 30 -> 32  // 32-bit (30+ bits/sample)
                calculatedBitDepth >= 20 -> 24  // 24-bit (20-29 bits/sample, allows for compression)
                calculatedBitDepth >= 14 -> 16  // 16-bit (14-19 bits/sample)
                calculatedBitDepth >= 7 -> 8    // 8-bit (7-13 bits/sample)
                else -> formatInfo.bitDepth  // Keep original if calculation fails
            }
            
            if (bitDepth > 0 && bitDepth != formatInfo.bitDepth) {
                Log.d(TAG, "Corrected bit depth from ${formatInfo.bitDepth}-bit to $bitDepth-bit based on bitrate calculation")
                return formatInfo.copy(bitDepth = bitDepth)
            } else if (bitDepth > 0) {
                Log.d(TAG, "Bit depth confirmed as $bitDepth-bit from bitrate calculation")
                return formatInfo.copy(bitDepth = bitDepth)
            }
        }
        
        return formatInfo
    }

    /**
     * Detect format using MediaExtractor (best for codec detection)
     */
    private fun detectUsingMediaExtractor(context: Context, uri: Uri): AudioFormatInfo? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            
            // Find audio track
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("audio/")) {
                    return parseMediaFormat(format, mime)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor failed: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    /**
     * Parse MediaFormat to extract codec and quality information
     */
    private fun parseMediaFormat(format: MediaFormat, mime: String): AudioFormatInfo {
        val sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 0
        val channelCount = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 0
        val bitrate = format.getIntegerOrNull(MediaFormat.KEY_BIT_RATE) ?: 0
        val bitrateKbps = if (bitrate > 0) bitrate / 1000 else 0
        
        // Detect codec from MIME type with better Dolby and DTS detection
        val codec = when {
            mime.contains("alac", ignoreCase = true) -> "ALAC"
            mime.contains("flac", ignoreCase = true) -> "FLAC"
            mime.contains("opus", ignoreCase = true) -> "Opus"
            mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
            // Enhanced Dolby detection
            mime.contains("truehd", ignoreCase = true) -> "TrueHD"
            mime.contains("eac3-joc", ignoreCase = true) -> "Dolby Atmos"
            mime.contains("mlp", ignoreCase = true) -> "TrueHD" // MLP is TrueHD
            mime.contains("ac4", ignoreCase = true) -> "AC-4"
            mime.contains("eac3", ignoreCase = true) || mime.contains("ec-3", ignoreCase = true) -> "E-AC-3"
            mime.contains("ac3", ignoreCase = true) || mime.contains("ac-3", ignoreCase = true) -> "AC-3"
            // Enhanced DTS detection
            mime.contains("dts-x", ignoreCase = true) || mime.contains("dtsx", ignoreCase = true) -> "DTS:X"
            mime.contains("dts-hd", ignoreCase = true) || mime.contains("dtshd", ignoreCase = true) -> "DTS-HD MA"
            mime.contains("dts", ignoreCase = true) -> "DTS"
            // DSD support
            mime.contains("dsd", ignoreCase = true) || mime.contains("x-dsd", ignoreCase = true) -> "DSD"
            // Standard codecs
            mime.contains("mp4a", ignoreCase = true) -> "AAC"
            mime.contains("mpeg", ignoreCase = true) -> "MP3"
            mime.contains("raw", ignoreCase = true) || mime.contains("pcm", ignoreCase = true) -> "PCM"
            mime.contains("wav", ignoreCase = true) -> "WAV"
            // Additional codecs from format compatibility
            mime.contains("midi", ignoreCase = true) || mime.contains("mid", ignoreCase = true) -> "MIDI"
            mime.contains("mp2", ignoreCase = true) -> "MP2"
            mime.contains("amr", ignoreCase = true) -> "AMR"
            mime.contains("mpegh", ignoreCase = true) || mime.contains("mpeg-h", ignoreCase = true) -> "MPEG-H"
            mime.contains("lc3", ignoreCase = true) -> "LC3"
            mime.contains("celt", ignoreCase = true) -> "CELT"
            mime.contains("dra", ignoreCase = true) -> "DRA"
            else -> mime.substringAfter("/").uppercase()
        }
        
        // Determine if lossless based on codec
        // CRITICAL: Only codecs that preserve bit-perfect audio data are lossless
        // High bitrate does NOT make lossy codecs lossless (e.g., 320kbps MP3 is still lossy)
        val isLossless = codec in listOf("ALAC", "FLAC", "PCM", "WAV", "APE", "DSD", "TrueHD", "Dolby Atmos", "DTS-HD MA", "DTS:X", "AIFF", "MIDI") ||
                         codec.contains("LOSSLESS", ignoreCase = true)
        
        // Determine if Dolby (expanded detection)
        val isDolby = codec in listOf("AC-3", "AC-4", "E-AC-3", "TrueHD", "Dolby Atmos") || 
                      codec.contains("ATMOS", ignoreCase = true) ||
                      codec.contains("TRUEHD", ignoreCase = true)
        
        // Determine if DTS
        val isDTS = codec.contains("DTS", ignoreCase = true)
        
        // Determine if Hi-Res (>= 48kHz sample rate for lossless, or >2000 kbps)
        val isHiRes = sampleRate >= 48000 || (isLossless && bitrateKbps >= 2000) || codec == "DSD"
        
        // Try to detect bit depth for lossless formats
        var bitDepth = 0
        
        // For lossless formats, ALWAYS calculate bit depth from bitrate
        // KEY_PCM_ENCODING gives decoded PCM format, NOT the original bit depth!
        // Only use PCM encoding for uncompressed formats (PCM, WAV)
        if (!isLossless) {
            val pkmEncoding = format.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
            bitDepth = when (pkmEncoding) {
                android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
                android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                else -> 0
            }
        }
        
        // Calculate bit depth from bitrate for lossless audio (most accurate method)
        if (isLossless && sampleRate > 0 && bitrateKbps > 0 && channelCount > 0) {
            if (codec == "DSD") {
                bitDepth = 1
            } else {
                // For lossless audio: bitrate (bps) ≈ sampleRate × bitDepth × channels
                // Calculate: bitDepth = bitrate / (sampleRate × channels)
                val isCompressedLossless = codec != "WAV" && codec != "PCM" && codec != "AIFF"
                val rawBitrate = if (isCompressedLossless) (bitrateKbps * 1000.0 / 0.55) else (bitrateKbps * 1000.0)
                val calculatedBitDepth = (rawBitrate / (sampleRate * channelCount)).toInt()
                
                Log.d(TAG, "Bit depth calculation (adjusted for compression=$isCompressedLossless): bitrate=${bitrateKbps}kbps, sampleRate=${sampleRate}Hz, channels=$channelCount, calculated=$calculatedBitDepth bits/sample")
                
                // Use accurate thresholds based on actual bit depths
                // Reference: CD (44.1kHz/16-bit/stereo) = 1,411 kbps → 16.0 bits/sample
                //           Hi-Res (96kHz/24-bit/stereo) = 4,608 kbps → 24.0 bits/sample
                bitDepth = when {
                    calculatedBitDepth >= 30 -> 32  // 32-bit (30+ bits/sample)
                    calculatedBitDepth >= 20 -> 24  // 24-bit (20-29 bits/sample, allows for compression)
                    calculatedBitDepth >= 14 -> 16  // 16-bit (14-19 bits/sample)
                    calculatedBitDepth >= 7 -> 8    // 8-bit (7-13 bits/sample)
                    else -> 0
                }
                
                Log.d(TAG, "Assigned bit depth: $bitDepth-bit (from calculated ${calculatedBitDepth} bits/sample)")
            }
        }
        
        // If still unknown, use sample rate heuristics for lossless
        if (bitDepth == 0 && isLossless) {
            if (codec == "DSD") {
                bitDepth = 1
            } else if (sampleRate >= 48000) {
                // Hi-Res audio is typically 24-bit
                bitDepth = 24
                Log.d(TAG, "Assumed 24-bit for Hi-Res lossless (${sampleRate}Hz)")
            } else if (sampleRate >= 44000) {
                // CD quality is 16-bit
                bitDepth = 16
                Log.d(TAG, "Assumed 16-bit for CD quality (${sampleRate}Hz)")
            }
        }
        
        val formatName = when {
            codec == "ALAC" -> "Apple Lossless"
            codec == "FLAC" -> "FLAC Lossless"
            codec == "E-AC-3" -> "Dolby Digital Plus"
            codec == "AC-3" -> "Dolby Digital"
            codec == "TrueHD" -> "Dolby TrueHD"
            codec == "Dolby Atmos" -> "Dolby Atmos"
            codec == "DTS-HD MA" -> "DTS-HD Master Audio"
            codec == "DTS" -> "DTS Audio"
            codec == "AAC" && bitrateKbps > 256 -> "AAC High Quality"
            else -> codec
        }
        
        return AudioFormatInfo(
            codec = codec,
            isLossless = isLossless,
            isDolby = isDolby,
            isDTS = isDTS,
            isHiRes = isHiRes,
            bitDepth = bitDepth,
            sampleRateHz = sampleRate,
            channelCount = channelCount,
            bitrateKbps = bitrateKbps,
            formatName = formatName
        )
    }

    /**
     * Detect format using MediaMetadataRetriever as fallback
     */
    private fun detectUsingMetadataRetriever(context: Context, uri: Uri): AudioFormatInfo? {
        val retriever = MediaMetadataRetriever()
        try {
            // Use the correct overload: setDataSource(Context, Uri)
            retriever.setDataSource(context, uri)
            
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            val bitDepth = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
            } else 0
            
            val path = uri.path?.lowercase() ?: ""
            // Try to detect codec from file path or mime type
            val codec = when {
                path.endsWith(".dsf") || path.endsWith(".dff") -> "DSD"
                mime?.contains("alac", ignoreCase = true) == true -> "ALAC"
                mime?.contains("flac", ignoreCase = true) == true -> "FLAC"
                mime?.contains("dsd", ignoreCase = true) == true || mime?.contains("x-dsd", ignoreCase = true) == true -> "DSD"
                mime?.contains("mp4", ignoreCase = true) == true -> "AAC" // Could be ALAC in MP4 container
                mime?.contains("mpeg", ignoreCase = true) == true -> "MP3"
                mime?.contains("ogg", ignoreCase = true) == true -> "OGG Vorbis"
                else -> "Unknown"
            }
            
            val isLossless = codec in listOf("ALAC", "FLAC", "PCM", "WAV", "DSD")
            val isHiRes = sampleRate >= 48000 || isLossless
            
            return AudioFormatInfo(
                codec = codec,
                isLossless = isLossless,
                isDolby = false,
                isDTS = false,
                isHiRes = isHiRes,
                bitDepth = bitDepth,
                sampleRateHz = sampleRate,
                formatName = codec
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    /**
     * Merge two AudioFormatInfo objects, preferring non-default values
     */
    private fun mergeFormatInfo(primary: AudioFormatInfo, fallback: AudioFormatInfo?): AudioFormatInfo {
        if (fallback == null) return primary
        
        return AudioFormatInfo(
            codec = if (primary.codec != "Unknown") primary.codec else fallback.codec,
            isLossless = primary.isLossless || fallback.isLossless,
            isDolby = primary.isDolby || fallback.isDolby,
            isDTS = primary.isDTS || fallback.isDTS,
            isHiRes = primary.isHiRes || fallback.isHiRes,
            bitDepth = when {
                primary.bitDepth <= 0 -> fallback.bitDepth
                fallback.bitDepth > primary.bitDepth -> fallback.bitDepth
                else -> primary.bitDepth
            },
            sampleRateHz = if (primary.sampleRateHz > 0) primary.sampleRateHz else fallback.sampleRateHz,
            channelCount = if (primary.channelCount > 0) primary.channelCount else fallback.channelCount,
            bitrateKbps = if (primary.bitrateKbps > 0) primary.bitrateKbps else fallback.bitrateKbps,
            formatName = if (primary.formatName != "Unknown") primary.formatName else fallback.formatName
        )
    }

    /**
     * Detect ALAC specifically (which often uses .m4a container)
     */
    fun isALAC(context: Context, uri: Uri): Boolean {
        // Check file extension first
        val path = uri.toString()
        if (path.endsWith(".alac", ignoreCase = true)) return true
        
        // For .m4a files, need to check the codec
        if (path.endsWith(".m4a", ignoreCase = true)) {
            val formatInfo = detectFormat(context, uri)
            return formatInfo.codec == "ALAC"
        }
        
        return false
    }

    /**
     * Helper extension to safely get integer from MediaFormat
     */
    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return try {
            if (containsKey(key)) getInteger(key) else null
        } catch (e: Exception) {
            null
        }
    }
}
