/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.pow

/**
 * Pure binary header inspector for local audio containers (`FLAC`, `WAV`/`RF64`, `AIFF`/`AIFC`,
 * `MP4`/`M4A` `ALAC`, `DSF`, `DFF`, `Ogg Opus`/`Vorbis`, and `MP3`).
 *
 * Reads exact stream parameters (sample rate, bit depth, channel count, and bitrate when present)
 * directly from container headers rather than guessing bit depth from compressed bitrate heuristics.
 */
object AudioHeaderInspector {

    data class InspectedHeader(
        val codec: String,
        val isLossless: Boolean,
        val sampleRateHz: Int = 0,
        val bitDepth: Int = 0,
        val channelCount: Int = 0,
        val bitrateKbps: Int = 0,
        val totalSamples: Long = 0L
    )

    private const val MAX_HEADER_READ_BYTES = 262_144

    fun inspectBytes(bytes: ByteArray, fileSizeBytes: Long = 0L): InspectedHeader? {
        if (bytes.size < 12) return null
        val header = bytes
        // Skip ID3v2 tag if prepended before FLAC or MP3
        var offset = 0
        if (header.size >= 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            offset = 10 + tagSize
        }

        if (offset + 4 <= header.size) {
            when {
                matchesAscii(header, offset, "fLaC") -> return parseFlac(header, offset, fileSizeBytes)
            }
        }

        return when {
            matchesAscii(header, 0, "RIFF") || matchesAscii(header, 0, "RF64") -> parseWav(header)
            matchesAscii(header, 0, "FORM") -> parseAiff(header)
            matchesAscii(header, 0, "DSD ") -> parseDsf(header)
            matchesAscii(header, 0, "FRM8") -> parseDff(header)
            matchesAscii(header, 0, "OggS") -> parseOgg(header)
            header.size >= 8 && matchesAscii(header, 4, "ftyp") -> parseMp4(header)
            offset < header.size -> parseMp3Frame(header, offset)
            else -> null
        }
    }

    fun inspect(inputStream: InputStream, fileSizeBytes: Long = 0L): InspectedHeader? {
        val buf = ByteArray(MAX_HEADER_READ_BYTES)
        var total = 0
        while (total < buf.size) {
            val read = inputStream.read(buf, total, buf.size - total)
            if (read <= 0) break
            total += read
        }
        if (total < 12) return null
        val trimmed = if (total == buf.size) buf else buf.copyOf(total)
        return inspectBytes(trimmed, fileSizeBytes)
    }

    fun hasRecognizedContainerMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return matchesAscii(bytes, 0, "fLaC") ||
            matchesAscii(bytes, 0, "RIFF") ||
            matchesAscii(bytes, 0, "RF64") ||
            matchesAscii(bytes, 0, "FORM") ||
            matchesAscii(bytes, 0, "OggS") ||
            matchesAscii(bytes, 0, "DSD ") ||
            matchesAscii(bytes, 0, "FRM8") ||
            matchesAscii(bytes, 0, "ID3") ||
            (bytes.size >= 8 && matchesAscii(bytes, 4, "ftyp")) ||
            ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0)
    }

    private fun parseFlac(data: ByteArray, flacStart: Int, fileSizeBytes: Long): InspectedHeader? {
        val blockHeaderOffset = flacStart + 4
        if (blockHeaderOffset + 4 > data.size) return null
        val blockType = data[blockHeaderOffset].toInt() and 0x7F
        val blockLen = u24BE(data, blockHeaderOffset + 1)
        val infoOffset = blockHeaderOffset + 4
        if (blockType != 0 || blockLen < 18 || infoOffset + 18 > data.size) {
            return InspectedHeader(codec = "FLAC", isLossless = true)
        }

        // Bytes 10..17 inside STREAMINFO pack sampleRate (20 bits), channels-1 (3 bits), bps-1 (5 bits), totalSamples (36 bits)
        val b10 = data[infoOffset + 10].toInt() and 0xFF
        val b11 = data[infoOffset + 11].toInt() and 0xFF
        val b12 = data[infoOffset + 12].toInt() and 0xFF
        val b13 = data[infoOffset + 13].toInt() and 0xFF
        val b14 = data[infoOffset + 14].toInt() and 0xFF

        val sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
        val channels = ((b12 ushr 1) and 0x07) + 1
        val bitsPerSample = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1

        val totalSamples = ((b13.toLong() and 0x0FL) shl 32) or
            ((b14.toLong() and 0xFFL) shl 24) or
            ((data[infoOffset + 15].toLong() and 0xFFL) shl 16) or
            ((data[infoOffset + 16].toLong() and 0xFFL) shl 8) or
            (data[infoOffset + 17].toLong() and 0xFFL)

        val bitrateKbps = if (fileSizeBytes > 0L && sampleRate > 0 && totalSamples > 0L) {
            val durationSec = totalSamples.toDouble() / sampleRate.toDouble()
            if (durationSec > 0.0) ((fileSizeBytes * 8.0) / durationSec / 1000.0).toInt() else 0
        } else 0

        return InspectedHeader(
            codec = "FLAC",
            isLossless = true,
            sampleRateHz = sampleRate,
            bitDepth = bitsPerSample,
            channelCount = channels,
            bitrateKbps = bitrateKbps,
            totalSamples = totalSamples
        )
    }

    private fun parseWav(data: ByteArray): InspectedHeader? {
        if (data.size < 12 || !matchesAscii(data, 8, "WAVE")) return null
        var offset = 12
        while (offset + 8 <= data.size) {
            val chunkId = ascii(data, offset, 4)
            val chunkSize = u32LE(data, offset + 4).toInt().coerceAtLeast(0)
            val bodyOffset = offset + 8
            if (chunkId == "fmt " && bodyOffset + 16 <= data.size) {
                val formatTag = u16LE(data, bodyOffset)
                val channels = u16LE(data, bodyOffset + 2)
                val sampleRate = u32LE(data, bodyOffset + 4).toInt()
                val byteRate = u32LE(data, bodyOffset + 8)
                val bitsPerSample = u16LE(data, bodyOffset + 14)

                var validBits = bitsPerSample
                var isPcmOrFloat = formatTag == 1 || formatTag == 3
                if (formatTag == 0xFFFE && chunkSize >= 40 && bodyOffset + 40 <= data.size) {
                    val sampleBits = u16LE(data, bodyOffset + 18)
                    if (sampleBits > 0) validBits = sampleBits
                    val subFormatTag = u16LE(data, bodyOffset + 24)
                    isPcmOrFloat = subFormatTag == 1 || subFormatTag == 3
                }
                val bitrateKbps = if (byteRate > 0L) ((byteRate * 8L) / 1000L).toInt() else 0
                return InspectedHeader(
                    codec = "WAV",
                    isLossless = isPcmOrFloat,
                    sampleRateHz = sampleRate,
                    bitDepth = validBits,
                    channelCount = channels,
                    bitrateKbps = bitrateKbps
                )
            }
            val step = 8 + chunkSize + (chunkSize and 1)
            if (step <= 8) break
            offset += step
        }
        return InspectedHeader(codec = "WAV", isLossless = true)
    }

    private fun parseAiff(data: ByteArray): InspectedHeader? {
        if (data.size < 12) return null
        val formType = ascii(data, 8, 4)
        if (formType != "AIFF" && formType != "AIFC") return null

        var offset = 12
        while (offset + 8 <= data.size) {
            val chunkId = ascii(data, offset, 4)
            val chunkSize = u32BE(data, offset + 4).toInt().coerceAtLeast(0)
            val bodyOffset = offset + 8
            if (chunkId == "COMM" && bodyOffset + 18 <= data.size) {
                val channels = u16BE(data, bodyOffset)
                val totalSamples = u32BE(data, bodyOffset + 2)
                val bitDepth = u16BE(data, bodyOffset + 6)
                val sampleRate = decodeExtended80BE(data, bodyOffset + 8).toInt()
                val compressionType = if (formType == "AIFC" && chunkSize >= 22 && bodyOffset + 22 <= data.size) {
                    ascii(data, bodyOffset + 18, 4)
                } else "NONE"
                val isLossless = compressionType in setOf("NONE", "sowt", "fl32", "fl64", "twos", "RAW ", "raw ")
                val bitrateKbps = if (sampleRate > 0 && channels > 0 && bitDepth > 0 && isLossless) {
                    (sampleRate.toLong() * channels * bitDepth / 1000L).toInt()
                } else 0
                return InspectedHeader(
                    codec = "AIFF",
                    isLossless = isLossless,
                    sampleRateHz = sampleRate,
                    bitDepth = bitDepth,
                    channelCount = channels,
                    bitrateKbps = bitrateKbps,
                    totalSamples = totalSamples
                )
            }
            val step = 8 + chunkSize + (chunkSize and 1)
            if (step <= 8) break
            offset += step
        }
        return InspectedHeader(codec = "AIFF", isLossless = true)
    }

    private fun parseDsf(data: ByteArray): InspectedHeader? {
        // DSF layout: "DSD " chunk (28 bytes), then "fmt " chunk (52 bytes)
        val fmtOffset = 28
        if (data.size < fmtOffset + 36 || !matchesAscii(data, fmtOffset, "fmt ")) {
            return InspectedHeader(codec = "DSD", isLossless = true, bitDepth = 1)
        }
        val channels = u32LE(data, fmtOffset + 24).toInt()
        val sampleRate = u32LE(data, fmtOffset + 28).toInt()
        val bitrateKbps = if (sampleRate > 0 && channels > 0) (sampleRate.toLong() * channels / 1000L).toInt() else 0
        return InspectedHeader(
            codec = "DSD",
            isLossless = true,
            sampleRateHz = sampleRate,
            bitDepth = 1,
            channelCount = channels,
            bitrateKbps = bitrateKbps
        )
    }

    private fun parseDff(data: ByteArray): InspectedHeader? {
        if (data.size < 16 || !matchesAscii(data, 12, "DSD ")) return null
        var sampleRate = 0
        var channels = 0
        var offset = 16
        while (offset + 12 <= data.size) {
            val chunkId = ascii(data, offset, 4)
            val chunkSize = u64BE(data, offset + 4).toInt().coerceAtLeast(0)
            val bodyOffset = offset + 12
            when (chunkId) {
                "PROP" -> {
                    // Enter PROP chunk after 4-byte property type ("SND ")
                    offset += 16
                    continue
                }
                "FS  " -> if (bodyOffset + 4 <= data.size) sampleRate = u32BE(data, bodyOffset).toInt()
                "CHNL" -> if (bodyOffset + 2 <= data.size) channels = u16BE(data, bodyOffset)
            }
            if (sampleRate > 0 && channels > 0) break
            val step = 12 + chunkSize + (chunkSize and 1)
            if (step <= 12) break
            offset += step
        }
        val bitrateKbps = if (sampleRate > 0 && channels > 0) (sampleRate.toLong() * channels / 1000L).toInt() else 0
        return InspectedHeader(
            codec = "DSD",
            isLossless = true,
            sampleRateHz = sampleRate,
            bitDepth = 1,
            channelCount = channels,
            bitrateKbps = bitrateKbps
        )
    }

    private fun parseMp4(data: ByteArray): InspectedHeader? {
        return findAlacOrAudioSampleEntry(data, 0, data.size, depth = 0)
    }

    private fun findAlacOrAudioSampleEntry(data: ByteArray, start: Int, end: Int, depth: Int): InspectedHeader? {
        if (depth > 8) return null
        var offset = start
        val containerBoxes = setOf("moov", "trak", "mdia", "minf", "stbl")
        while (offset + 8 <= end && offset + 8 <= data.size) {
            var size = u32BE(data, offset).toInt()
            val type = ascii(data, offset + 4, 4)
            var headerBytes = 8
            if (size == 1 && offset + 16 <= data.size) {
                size = u64BE(data, offset + 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                headerBytes = 16
            } else if (size == 0) {
                size = end - offset
            }
            if (size < headerBytes) break
            val boxEnd = minOf(offset + size, end, data.size)
            val payloadStart = offset + headerBytes

            when {
                type in containerBoxes -> {
                    findAlacOrAudioSampleEntry(data, payloadStart, boxEnd, depth + 1)?.let { return it }
                }
                type == "stsd" && payloadStart + 8 <= boxEnd -> {
                    // stsd has 4-byte version/flags + 4-byte entry count before child sample entry boxes
                    findAlacOrAudioSampleEntry(data, payloadStart + 8, boxEnd, depth + 1)?.let { return it }
                }
                type == "alac" -> {
                    // AudioSampleEntry (28 bytes payload) followed by nested 'alac' specific config box
                    val innerStart = payloadStart + 28
                    if (innerStart + 36 <= boxEnd) {
                        var innerOffset = innerStart
                        while (innerOffset + 8 <= boxEnd) {
                            val innerSize = u32BE(data, innerOffset).toInt()
                            val innerType = ascii(data, innerOffset + 4, 4)
                            if (innerSize < 8) break
                            if (innerType == "alac" && innerOffset + 36 <= data.size) {
                                // ALACSpecificConfig starts after 8-byte box + 4-byte fullbox version/flags = offset+12
                                val cfg = innerOffset + 12
                                val bitDepth = data[cfg + 5].toInt() and 0xFF
                                val channels = data[cfg + 9].toInt() and 0xFF
                                val sampleRate = u32BE(data, cfg + 20).toInt()
                                return InspectedHeader(
                                    codec = "ALAC",
                                    isLossless = true,
                                    sampleRateHz = sampleRate,
                                    bitDepth = bitDepth,
                                    channelCount = channels
                                )
                            }
                            innerOffset += innerSize
                        }
                    }
                    // Fallback ALAC entry reading AudioSampleEntry fields
                    if (payloadStart + 28 <= boxEnd) {
                        val channels = u16BE(data, payloadStart + 16)
                        val sampleSize = u16BE(data, payloadStart + 18)
                        val sampleRate = u16BE(data, payloadStart + 24)
                        return InspectedHeader(
                            codec = "ALAC",
                            isLossless = true,
                            sampleRateHz = sampleRate,
                            bitDepth = sampleSize,
                            channelCount = channels
                        )
                    }
                }
                type == "mp4a" && payloadStart + 28 <= boxEnd -> {
                    val channels = u16BE(data, payloadStart + 16)
                    val sampleRate = u16BE(data, payloadStart + 24)
                    return InspectedHeader(
                        codec = "AAC",
                        isLossless = false,
                        sampleRateHz = sampleRate,
                        bitDepth = 16,
                        channelCount = channels
                    )
                }
            }
            offset = boxEnd
        }
        return null
    }

    private fun parseOgg(data: ByteArray): InspectedHeader? {
        if (data.size < 28) return null
        val pageSegments = data[26].toInt() and 0xFF
        val packetStart = 27 + pageSegments
        if (packetStart + 16 > data.size) return null

        return when {
            matchesAscii(data, packetStart, "OpusHead") && packetStart + 16 <= data.size -> {
                val channels = data[packetStart + 9].toInt() and 0xFF
                InspectedHeader(
                    codec = "Opus",
                    isLossless = false,
                    sampleRateHz = 48_000,
                    bitDepth = 16,
                    channelCount = channels
                )
            }
            data.size > packetStart + 16 && matchesAscii(data, packetStart + 1, "vorbis") -> {
                val channels = data[packetStart + 11].toInt() and 0xFF
                val sampleRate = u32LE(data, packetStart + 12).toInt()
                val bitrateNominal = if (packetStart + 24 <= data.size) {
                    u32LE(data, packetStart + 20).toInt().takeIf { it > 0 }?.div(1000) ?: 0
                } else 0
                InspectedHeader(
                    codec = "Vorbis",
                    isLossless = false,
                    sampleRateHz = sampleRate,
                    bitDepth = 16,
                    channelCount = channels,
                    bitrateKbps = bitrateNominal
                )
            }
            else -> null
        }
    }

    private fun parseMp3Frame(data: ByteArray, startOffset: Int): InspectedHeader? {
        val searchLimit = minOf(data.size - 4, startOffset + 4096)
        for (i in startOffset until searchLimit) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data[i + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03
                if (versionBits == 1 || layerBits == 0) continue

                val b2 = data[i + 2].toInt() and 0xFF
                val bitrateIdx = (b2 ushr 4) and 0x0F
                val sampleRateIdx = (b2 ushr 2) and 0x03
                if (bitrateIdx == 0 || bitrateIdx == 15 || sampleRateIdx == 3) continue

                val b3 = data[i + 3].toInt() and 0xFF
                val channelMode = (b3 ushr 6) and 0x03
                val channels = if (channelMode == 3) 1 else 2

                val sampleRate = when (versionBits) {
                    3 -> intArrayOf(44_100, 48_000, 32_000)[sampleRateIdx] // MPEG-1
                    2 -> intArrayOf(22_050, 24_000, 16_000)[sampleRateIdx] // MPEG-2
                    0 -> intArrayOf(11_025, 12_000, 8_000)[sampleRateIdx]  // MPEG-2.5
                    else -> 0
                }
                val bitrateKbps = if (versionBits == 3 && layerBits == 1) {
                    // MPEG-1 Layer III
                    intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)[bitrateIdx]
                } else 0

                if (sampleRate > 0) {
                    return InspectedHeader(
                        codec = "MP3",
                        isLossless = false,
                        sampleRateHz = sampleRate,
                        bitDepth = 16,
                        channelCount = channels,
                        bitrateKbps = bitrateKbps
                    )
                }
            }
        }
        return null
    }

    private fun decodeExtended80BE(data: ByteArray, offset: Int): Double {
        if (offset + 10 > data.size) return 0.0
        val expon = u16BE(data, offset)
        val sign = if ((expon and 0x8000) != 0) -1.0 else 1.0
        val exp = (expon and 0x7FFF) - 16383
        val hiMant = u32BE(data, offset + 2)
        val loMant = u32BE(data, offset + 6)
        if (expon == 0 && hiMant == 0L && loMant == 0L) return 0.0
        val value = hiMant * 2.0.pow((exp - 31).toDouble()) + loMant * 2.0.pow((exp - 63).toDouble())
        return sign * value
    }

    private fun matchesAscii(data: ByteArray, offset: Int, expected: String): Boolean {
        if (offset < 0 || offset + expected.length > data.size) return false
        for (i in expected.indices) {
            if (data[offset + i].toInt().toChar() != expected[i]) return false
        }
        return true
    }

    private fun ascii(data: ByteArray, offset: Int, len: Int): String =
        String(data, offset, len, StandardCharsets.US_ASCII)

    private fun u16LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u16BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun u24BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)

    private fun u32LE(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)

    private fun u32BE(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xFFL) shl 24) or
            ((data[offset + 1].toLong() and 0xFFL) shl 16) or
            ((data[offset + 2].toLong() and 0xFFL) shl 8) or
            (data[offset + 3].toLong() and 0xFFL)

    private fun u64BE(data: ByteArray, offset: Int): Long =
        (u32BE(data, offset) shl 32) or u32BE(data, offset + 4)
}
