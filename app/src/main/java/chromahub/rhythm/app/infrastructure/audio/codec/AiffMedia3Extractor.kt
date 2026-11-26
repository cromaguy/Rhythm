/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.codec

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import chromahub.rhythm.app.util.AudioHeaderInspector
import kotlin.math.min

/**
 * Media3 [Extractor] for uncompressed AIFF and AIFF-C (`FORM` / `AIFF` / `AIFC`) audio streams.
 * Converts big-endian PCM/float (plus AIFF-C `sowt` little-endian, 8-bit signed PCM, and 64-bit
 * `fl64`) into little-endian `MimeTypes.AUDIO_RAW` frames so ExoPlayer plays AIFF without FFmpeg.
 */
@UnstableApi
class AiffMedia3Extractor : Extractor {

    private enum class SampleKind {
        PCM_8_BE,
        PCM_16_BE,
        PCM_24_BE,
        PCM_32_BE,
        PCM_16_LE,
        PCM_24_LE,
        PCM_32_LE,
        FLOAT_32_BE,
        FLOAT_64_BE
    }

    private data class CommInfo(
        val channels: Int,
        val numSampleFrames: Long,
        val sampleSizeBits: Int,
        val sampleRateHz: Int,
        val kind: SampleKind,
        val srcBytesPerFrame: Int,
        val dstBytesPerFrame: Int,
        val dstPcmEncoding: @C.PcmEncoding Int
    )

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null

    private var state = STATE_HEADER
    private var comm: CommInfo? = null
    private var dataStart = -1L
    private var dataSizeBytes = 0L
    private var bytesRemaining = 0L
    private var pendingSeekByteOffset = 0L
    private var sampleBuffer = ByteArray(16 * 1024)
    private var expandedBuffer = ByteArray(32 * 1024)
    private val parsableOut = ParsableByteArray()

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ByteArray(12)
        if (!input.peekFully(header, 0, 12, true)) return false
        val form = String(header, 0, 4, Charsets.US_ASCII)
        val sub = String(header, 8, 4, Charsets.US_ASCII)
        return form == "FORM" && (sub == "AIFF" || sub == "AIFC")
    }

    override fun init(output: ExtractorOutput) {
        extractorOutput = output
        trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
    }

    override fun seek(position: Long, timeUs: Long) {
        val info = comm
        if (info == null || dataStart < 0L) {
            state = STATE_HEADER
            return
        }
        val frameIndex = ((timeUs * info.sampleRateHz.toLong()) / 1_000_000L)
            .coerceIn(0L, dataSizeBytes / info.srcBytesPerFrame.coerceAtLeast(1))
        val alignedByteOffset = frameIndex * info.srcBytesPerFrame
        pendingSeekByteOffset = alignedByteOffset
        bytesRemaining = (dataSizeBytes - alignedByteOffset).coerceAtLeast(0L)
        state = if (position == dataStart + alignedByteOffset) STATE_READING_SAMPLES else STATE_SEEK_TO_DATA
    }

    override fun release() {
        extractorOutput = null
        trackOutput = null
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val out = trackOutput ?: return Extractor.RESULT_END_OF_INPUT
        if (state == STATE_HEADER) {
            parseHeader(input)
            if (state == STATE_SEEK_TO_DATA) {
                seekPosition.position = dataStart + pendingSeekByteOffset
                state = STATE_READING_SAMPLES
                return Extractor.RESULT_SEEK
            }
        }
        if (state == STATE_SEEK_TO_DATA) {
            val target = dataStart + pendingSeekByteOffset
            if (input.position != target) {
                seekPosition.position = target
                state = STATE_READING_SAMPLES
                return Extractor.RESULT_SEEK
            }
            state = STATE_READING_SAMPLES
        }

        val info = comm ?: return Extractor.RESULT_END_OF_INPUT
        if (bytesRemaining <= 0L) return Extractor.RESULT_END_OF_INPUT

        val maxChunkSrc = (sampleBuffer.size / info.srcBytesPerFrame) * info.srcBytesPerFrame
        val toRead = min(bytesRemaining, maxChunkSrc.toLong()).toInt()
            .let { (it / info.srcBytesPerFrame) * info.srcBytesPerFrame }
        if (toRead <= 0) return Extractor.RESULT_END_OF_INPUT

        val readBytes = input.read(sampleBuffer, 0, toRead)
        if (readBytes == C.RESULT_END_OF_INPUT) return Extractor.RESULT_END_OF_INPUT

        val alignedRead = if (readBytes % info.srcBytesPerFrame == 0) {
            readBytes
        } else {
            val remainder = info.srcBytesPerFrame - (readBytes % info.srcBytesPerFrame)
            if (input.readFully(sampleBuffer, readBytes, remainder, true)) {
                readBytes + remainder
            } else {
                (readBytes / info.srcBytesPerFrame) * info.srcBytesPerFrame
            }
        }
        if (alignedRead <= 0) return Extractor.RESULT_END_OF_INPUT

        val currentOffset = dataSizeBytes - bytesRemaining
        val frameIndex = currentOffset / info.srcBytesPerFrame
        val timeUs = (frameIndex * 1_000_000L) / info.sampleRateHz

        val outBytes = convertSamples(info, sampleBuffer, alignedRead)
        parsableOut.reset(outBytes.first, outBytes.second)
        out.sampleData(parsableOut, outBytes.second)
        out.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, outBytes.second, 0, null)

        bytesRemaining -= alignedRead.toLong()
        return Extractor.RESULT_CONTINUE
    }

    private fun parseHeader(input: ExtractorInput) {
        val header = ByteArray(12)
        input.readFully(header, 0, 12)
        val form = String(header, 0, 4, Charsets.US_ASCII)
        val sub = String(header, 8, 4, Charsets.US_ASCII)
        if (form != "FORM" || (sub != "AIFF" && sub != "AIFC")) {
            throw ParserException.createForMalformedContainer("Not an AIFF/AIFC stream", null)
        }
        val isAifc = (sub == "AIFC")

        val chunkHead = ByteArray(8)
        var parsedComm: CommInfo? = null
        var ssndStart = -1L
        var ssndDataLen = 0L

        while (true) {
            if (!input.readFully(chunkHead, 0, 8, true)) break
            val id = String(chunkHead, 0, 4, Charsets.US_ASCII)
            val size = AudioHeaderInspector.u32BE(chunkHead, 4)
            if (size < 0L || size > 0x7FFFFFFFL) {
                throw ParserException.createForMalformedContainer("Invalid AIFF chunk size: $id", null)
            }
            when (id) {
                "COMM" -> {
                    val commLen = size.toInt().coerceIn(18, 256)
                    val commBuf = ByteArray(commLen)
                    input.readFully(commBuf, 0, commLen)
                    val extra = size - commLen
                    if (extra > 0L) input.skipFully(extra.toInt())
                    parsedComm = decodeComm(commBuf, isAifc)
                }
                "SSND" -> {
                    if (size < 8L) {
                        throw ParserException.createForMalformedContainer("Truncated AIFF SSND chunk", null)
                    }
                    val ssndHead = ByteArray(8)
                    input.readFully(ssndHead, 0, 8)
                    val offset = AudioHeaderInspector.u32BE(ssndHead, 0)
                    ssndStart = input.position + offset
                    ssndDataLen = (size - 8L - offset).coerceAtLeast(0L)
                    if (parsedComm != null) {
                        if (offset > 0L) input.skipFully(offset.toInt())
                        break
                    } else {
                        input.skipFully((size - 8L).toInt())
                    }
                }
                else -> skipFullyLong(input, size)
            }
            if ((size and 1L) != 0L) {
                input.skipFully(1)
            }
            if (parsedComm != null && ssndStart >= 0L) break
        }

        val info = parsedComm ?: throw ParserException.createForMalformedContainer("Missing AIFF COMM chunk", null)
        if (ssndStart < 0L) throw ParserException.createForMalformedContainer("Missing AIFF SSND chunk", null)

        val frameBoundedLen = if (info.numSampleFrames > 0L) {
            min(ssndDataLen, info.numSampleFrames * info.srcBytesPerFrame)
        } else {
            ssndDataLen
        }
        val alignedLen = (frameBoundedLen / info.srcBytesPerFrame) * info.srcBytesPerFrame

        comm = info
        dataStart = ssndStart
        dataSizeBytes = alignedLen
        bytesRemaining = alignedLen
        pendingSeekByteOffset = 0L

        val totalFrames = alignedLen / info.srcBytesPerFrame
        val durationUs = (totalFrames * 1_000_000L) / info.sampleRateHz

        trackOutput?.format(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(info.channels)
                .setSampleRate(info.sampleRateHz)
                .setPcmEncoding(info.dstPcmEncoding)
                .setAverageBitrate(info.sampleRateHz * info.channels * info.sampleSizeBits)
                .setPeakBitrate(info.sampleRateHz * info.channels * info.sampleSizeBits)
                .build()
        )
        extractorOutput?.endTracks()
        extractorOutput?.seekMap(object : SeekMap {
            override fun isSeekable(): Boolean = true
            override fun getDurationUs(): Long = durationUs
            override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
                val clampedUs = timeUs.coerceIn(0L, durationUs)
                val frame = (clampedUs * info.sampleRateHz) / 1_000_000L
                val pos = dataStart + frame * info.srcBytesPerFrame
                val pointTimeUs = (frame * 1_000_000L) / info.sampleRateHz
                return SeekMap.SeekPoints(SeekPoint(pointTimeUs, pos))
            }
        })

        state = if (input.position == ssndStart) STATE_READING_SAMPLES else STATE_SEEK_TO_DATA
    }

    private fun decodeComm(buf: ByteArray, isAifc: Boolean): CommInfo {
        if (buf.size < 18) throw ParserException.createForMalformedContainer("AIFF COMM too short", null)
        val channels = AudioHeaderInspector.u16BE(buf, 0)
        if (channels !in 1..8) throw ParserException.createForMalformedContainer("Unsupported AIFF channels: $channels", null)
        val numFrames = AudioHeaderInspector.u32BE(buf, 2)
        val bits = AudioHeaderInspector.u16BE(buf, 6)
        val sampleRateHz = AudioHeaderInspector.decodeExtended80BE(buf, 8).toInt()
        if (sampleRateHz !in 4_000..768_000) {
            throw ParserException.createForMalformedContainer("Invalid AIFF sample rate: $sampleRateHz", null)
        }
        val compType = if (isAifc && buf.size >= 22) String(buf, 18, 4, Charsets.US_ASCII) else "NONE"
        val isFloat = compType.equals("fl32", true) || compType.equals("fl64", true)
        val isLe = compType == "sowt"

        val kind = when {
            compType.equals("fl32", true) && bits == 32 -> SampleKind.FLOAT_32_BE
            compType.equals("fl64", true) && bits == 64 -> SampleKind.FLOAT_64_BE
            compType != "NONE" && compType != "twos" && compType != "sowt" && !isFloat ->
                throw ParserException.createForMalformedContainer("Unsupported AIFC compression: $compType", null)
            bits == 8 && !isLe -> SampleKind.PCM_8_BE
            bits == 16 -> if (isLe) SampleKind.PCM_16_LE else SampleKind.PCM_16_BE
            bits == 24 -> if (isLe) SampleKind.PCM_24_LE else SampleKind.PCM_24_BE
            bits == 32 -> if (isLe) SampleKind.PCM_32_LE else SampleKind.PCM_32_BE
            else -> throw ParserException.createForMalformedContainer("Unsupported AIFF bit depth: $bits", null)
        }

        val srcBytesPerSample = (bits + 7) / 8
        val (dstEnc, dstBytesPerSample) = when (kind) {
            SampleKind.PCM_8_BE -> C.ENCODING_PCM_16BIT to 2
            SampleKind.PCM_16_BE, SampleKind.PCM_16_LE -> C.ENCODING_PCM_16BIT to 2
            SampleKind.PCM_24_BE, SampleKind.PCM_24_LE -> C.ENCODING_PCM_24BIT to 3
            SampleKind.PCM_32_BE, SampleKind.PCM_32_LE -> C.ENCODING_PCM_32BIT to 4
            SampleKind.FLOAT_32_BE, SampleKind.FLOAT_64_BE -> C.ENCODING_PCM_FLOAT to 4
        }

        return CommInfo(
            channels = channels,
            numSampleFrames = numFrames,
            sampleSizeBits = bits,
            sampleRateHz = sampleRateHz,
            kind = kind,
            srcBytesPerFrame = channels * srcBytesPerSample,
            dstBytesPerFrame = channels * dstBytesPerSample,
            dstPcmEncoding = dstEnc
        )
    }

    private fun convertSamples(info: CommInfo, src: ByteArray, length: Int): Pair<ByteArray, Int> {
        return when (info.kind) {
            SampleKind.PCM_16_LE, SampleKind.PCM_24_LE, SampleKind.PCM_32_LE -> src to length
            SampleKind.PCM_16_BE -> {
                var i = 0
                while (i + 1 < length) {
                    val b0 = src[i]; src[i] = src[i + 1]; src[i + 1] = b0
                    i += 2
                }
                src to length
            }
            SampleKind.PCM_24_BE -> {
                var i = 0
                while (i + 2 < length) {
                    val b0 = src[i]; src[i] = src[i + 2]; src[i + 2] = b0
                    i += 3
                }
                src to length
            }
            SampleKind.PCM_32_BE, SampleKind.FLOAT_32_BE -> {
                var i = 0
                while (i + 3 < length) {
                    val b0 = src[i]; val b1 = src[i + 1]
                    src[i] = src[i + 3]; src[i + 1] = src[i + 2]; src[i + 2] = b1; src[i + 3] = b0
                    i += 4
                }
                src to length
            }
            SampleKind.PCM_8_BE -> {
                val outLen = length * 2
                if (expandedBuffer.size < outLen) expandedBuffer = ByteArray(outLen)
                var o = 0
                for (i in 0 until length) {
                    expandedBuffer[o++] = 0
                    expandedBuffer[o++] = src[i]
                }
                expandedBuffer to outLen
            }
            SampleKind.FLOAT_64_BE -> {
                val sampleCount = length / 8
                val outLen = sampleCount * 4
                if (expandedBuffer.size < outLen) expandedBuffer = ByteArray(outLen)
                var s = 0
                var o = 0
                repeat(sampleCount) {
                    val bits64 = AudioHeaderInspector.u64BE(src, s)
                    val f = java.lang.Double.longBitsToDouble(bits64).toFloat()
                    val bits32 = java.lang.Float.floatToRawIntBits(f)
                    expandedBuffer[o++] = (bits32 and 0xFF).toByte()
                    expandedBuffer[o++] = ((bits32 ushr 8) and 0xFF).toByte()
                    expandedBuffer[o++] = ((bits32 ushr 16) and 0xFF).toByte()
                    expandedBuffer[o++] = ((bits32 ushr 24) and 0xFF).toByte()
                    s += 8
                }
                expandedBuffer to outLen
            }
        }
    }

    private fun skipFullyLong(input: ExtractorInput, length: Long) {
        var left = length
        while (left > 0L) {
            val step = min(left, Int.MAX_VALUE.toLong()).toInt()
            input.skipFully(step)
            left -= step.toLong()
        }
    }

    companion object {
        private const val STATE_HEADER = 0
        private const val STATE_SEEK_TO_DATA = 1
        private const val STATE_READING_SAMPLES = 2
    }
}
