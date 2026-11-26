/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.min

/**
 * Pure stream container-native embedded lyrics extractor for local audio files
 * (supports both `content://` SAF URIs and filesystem paths):
 * - ID3v2.2 / v2.3 / v2.4 (`SYLT` synced milliseconds & `USLT` unsynced / LRC)
 * - FLAC (`fLaC` Vorbis Comment block 4) & Ogg Vorbis / Opus (`OggS` comments)
 * - MP4 / M4A / ALAC (`moov.udta.meta.ilst.©lyr`)
 * - Matroska / MKA / WebM (EBML `SimpleTag` `SYNCEDLYRICS` / `LYRICS` / `UNSYNCEDLYRICS`)
 */
object LocalContainerLyricsReader {

    data class ExtractedContainerLyrics(
        val text: String,
        val isLrcFormatted: Boolean,
        val sourceLabel: String
    )

    private const val MAX_HEADER_BYTES = 1024 * 1024

    fun extract(context: Context, urlOrUri: String?): ExtractedContainerLyrics? {
        if (urlOrUri.isNullOrBlank()) return null
        return runCatching {
            val openStream: () -> InputStream? = {
                if (urlOrUri.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(urlOrUri))
                } else {
                    val path = urlOrUri.removePrefix("file://")
                    File(path).takeIf { it.isFile && it.canRead() }?.inputStream()
                }
            }
            openStream()?.use { raw ->
                extractFromStream(BufferedInputStream(raw, 16 * 1024))
            }
        }.getOrNull()
    }

    internal fun extractFromStream(stream: BufferedInputStream): ExtractedContainerLyrics? {
        stream.mark(16)
        val head = ByteArray(16)
        val read = readAtMost(stream, head, 16)
        stream.reset()
        if (read < 4) return null

        val magic = String(head, 0, 4, Charsets.US_ASCII)
        return when {
            read >= 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() ->
                parseId3v2(stream)
            magic == "fLaC" ->
                parseFlac(stream)
            magic == "OggS" ->
                parseOgg(stream)
            read >= 8 && String(head, 4, 4, Charsets.US_ASCII) == "ftyp" ->
                parseMp4(stream)
            head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() ->
                parseEbmlTags(stream)
            else -> null
        }
    }


    private fun parseId3v2(stream: InputStream): ExtractedContainerLyrics? {
        val header = ByteArray(10)
        if (!readExact(stream, header, 10)) return null
        val major = header[3].toInt() and 0xFF
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 6).coerceIn(0, MAX_HEADER_BYTES * 2)
        if (tagSize <= 0) return null

        val tagBody = ByteArray(tagSize)
        val got = readAtMost(stream, tagBody, tagSize)
        if (got < 10) return null

        var offset = 0
        if ((flags and 0x40) != 0 && got >= 4) {
            val extSize = if (major == 4) syncSafeInt(tagBody, 0) else beInt(tagBody, 0) + 4
            offset = extSize.coerceIn(0, got)
        }

        val candidates = mutableListOf<Pair<String, Boolean>>()
        val minFrameHead = if (major <= 2) 6 else 10

        while (offset + minFrameHead <= got) {
            if (tagBody[offset] == 0.toByte()) break
            val frameId: String
            val frameSize: Int
            val frameFlags: Int
            if (major <= 2) {
                frameId = String(tagBody, offset, 3, Charsets.US_ASCII)
                frameSize = ((tagBody[offset + 3].toInt() and 0xFF) shl 16) or
                    ((tagBody[offset + 4].toInt() and 0xFF) shl 8) or
                    (tagBody[offset + 5].toInt() and 0xFF)
                frameFlags = 0
                offset += 6
            } else {
                frameId = String(tagBody, offset, 4, Charsets.US_ASCII)
                frameSize = if (major == 4) syncSafeInt(tagBody, offset + 4) else beInt(tagBody, offset + 4)
                frameFlags = ((tagBody[offset + 8].toInt() and 0xFF) shl 8) or (tagBody[offset + 9].toInt() and 0xFF)
                offset += 10
            }
            if (frameSize <= 0 || offset + frameSize > got) break

            val rawFrame = if ((flags and 0x80) != 0 || (frameFlags and 0x0002) != 0) {
                deUnsync(tagBody, offset, frameSize)
            } else {
                tagBody.copyOfRange(offset, offset + frameSize)
            }
            offset += frameSize

            when (frameId) {
                "SYLT", "SLT" -> parseSylt(rawFrame)?.let { candidates += it to true }
                "USLT", "ULT" -> parseUslt(rawFrame)?.let { text ->
                    candidates += text to containsLrcTimestamps(text)
                }
            }
        }

        val best = candidates.firstOrNull { it.second } ?: candidates.firstOrNull() ?: return null
        return ExtractedContainerLyrics(
            text = best.first.trim(),
            isLrcFormatted = best.second,
            sourceLabel = "Embedded ID3"
        )
    }

    private fun parseUslt(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val enc = frame[0].toInt() and 0xFF
        val charset = id3Charset(enc)
        val termLen = if (enc == 1 || enc == 2) 2 else 1
        var p = 4 // encoding (1) + language (3)
        p = skipNullTerminated(frame, p, termLen)
        if (p >= frame.size) return null
        return decodeId3String(frame, p, frame.size - p, charset).takeIf { it.isNotBlank() }
    }

    private fun parseSylt(frame: ByteArray): String? {
        if (frame.size < 7) return null
        val enc = frame[0].toInt() and 0xFF
        val timestampFormat = frame[4].toInt() and 0xFF // 2 == milliseconds
        if (timestampFormat != 2) return null
        val charset = id3Charset(enc)
        val termLen = if (enc == 1 || enc == 2) 2 else 1
        var p = skipNullTerminated(frame, 6, termLen) // encoding(1)+lang(3)+fmt(1)+content(1)
        val sb = StringBuilder()
        while (p < frame.size) {
            val textStart = p
            var textEnd = p
            while (textEnd + termLen <= frame.size) {
                val isZero = if (termLen == 1) {
                    frame[textEnd] == 0.toByte()
                } else {
                    frame[textEnd] == 0.toByte() && frame[textEnd + 1] == 0.toByte()
                }
                if (isZero) break
                textEnd += termLen
            }
            val afterTerm = textEnd + termLen
            if (afterTerm + 4 > frame.size) break
            val segmentText = decodeId3String(frame, textStart, textEnd - textStart, charset)
            val ms = beInt(frame, afterTerm).toLong() and 0xFFFFFFFFL
            p = afterTerm + 4
            if (segmentText.isNotEmpty() || ms >= 0L) {
                sb.append(formatLrcTimestamp(ms)).append(segmentText.replace("\n", " ").trim()).append('\n')
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun skipNullTerminated(frame: ByteArray, start: Int, termLen: Int): Int {
        var p = start
        while (p + termLen <= frame.size) {
            val hit = if (termLen == 1) {
                frame[p] == 0.toByte()
            } else {
                frame[p] == 0.toByte() && frame[p + 1] == 0.toByte()
            }
            if (hit) return p + termLen
            p += termLen
        }
        return frame.size
    }


    private fun parseFlac(stream: InputStream): ExtractedContainerLyrics? {
        val sig = ByteArray(4)
        if (!readExact(stream, sig, 4)) return null
        val blockHead = ByteArray(4)
        repeat(64) {
            if (!readExact(stream, blockHead, 4)) return null
            val b0 = blockHead[0].toInt() and 0xFF
            val isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F
            val len = ((blockHead[1].toInt() and 0xFF) shl 16) or
                ((blockHead[2].toInt() and 0xFF) shl 8) or
                (blockHead[3].toInt() and 0xFF)
            if (len < 0 || len > 4 * 1024 * 1024) return null
            if (blockType == 4) {
                val body = ByteArray(min(len, MAX_HEADER_BYTES))
                if (!readExact(stream, body, body.size)) return null
                return parseVorbisCommentBlob(body, 0, "Embedded FLAC")
            }
            if (!skipBytes(stream, len.toLong()) || isLast) return null
        }
        return null
    }


    private fun parseOgg(stream: InputStream): ExtractedContainerLyrics? {
        val buf = ByteArray(min(256 * 1024, MAX_HEADER_BYTES))
        val read = readAtMost(stream, buf, buf.size)
        if (read < 32) return null

        val markerOffset = findAsciiSubsequence(
            buf,
            read,
            byteArrayOf(0x03, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
        )?.plus(7)
            ?: findAsciiSubsequence(buf, read, "OpusTags".toByteArray(Charsets.US_ASCII))?.plus(8)
            ?: return null

        return parseVorbisCommentBlob(buf.copyOf(read), markerOffset, "Embedded Ogg")
    }

    private fun parseVorbisCommentBlob(
        buf: ByteArray,
        startOffset: Int,
        sourceLabel: String
    ): ExtractedContainerLyrics? {
        var pos = startOffset
        if (pos + 4 > buf.size) return null
        val vendorLen = leInt(buf, pos).coerceAtLeast(0)
        pos += 4
        if (pos + vendorLen + 4 > buf.size) return null
        pos += vendorLen

        val listCount = leInt(buf, pos).coerceIn(0, 4096)
        pos += 4

        var synced: String? = null
        var lyrics: String? = null
        var unsynced: String? = null

        for (i in 0 until listCount) {
            if (pos + 4 > buf.size) break
            val entryLen = leInt(buf, pos)
            pos += 4
            if (entryLen <= 0 || pos + entryLen > buf.size) break
            val entry = String(buf, pos, entryLen, Charsets.UTF_8)
            pos += entryLen
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val key = entry.substring(0, eq).trim().uppercase(Locale.US)
            val valStr = entry.substring(eq + 1).trim()
            if (valStr.isBlank()) continue
            when (key) {
                "SYNCEDLYRICS", "LYRICS_SYNCED" -> if (synced == null) synced = valStr
                "LYRICS" -> if (lyrics == null) lyrics = valStr
                "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (unsynced == null) unsynced = valStr
            }
        }

        val chosen = when {
            synced != null -> synced to true
            lyrics != null -> lyrics to containsLrcTimestamps(lyrics)
            unsynced != null -> unsynced to containsLrcTimestamps(unsynced)
            else -> null
        } ?: return null

        return ExtractedContainerLyrics(
            text = chosen.first,
            isLrcFormatted = chosen.second,
            sourceLabel = sourceLabel
        )
    }


    private fun parseMp4(stream: InputStream): ExtractedContainerLyrics? =
        scanMp4Container(stream, Long.MAX_VALUE, depth = 0)

    private fun scanMp4Container(
        stream: InputStream,
        remainingLimit: Long,
        depth: Int
    ): ExtractedContainerLyrics? {
        if (depth > 8) return null
        var consumed = 0L
        val head = ByteArray(8)
        while (consumed + 8 <= remainingLimit) {
            if (!readExact(stream, head, 8)) return null
            consumed += 8
            val size32 = beUInt(head, 0)
            val type = String(head, 4, 4, Charsets.ISO_8859_1)
            val boxSize: Long
            val headerLen: Long
            when (size32) {
                0L -> return null
                1L -> {
                    val ext = ByteArray(8)
                    if (!readExact(stream, ext, 8)) return null
                    consumed += 8
                    boxSize = beLong(ext, 0)
                    headerLen = 16L
                }
                else -> {
                    boxSize = size32
                    headerLen = 8L
                }
            }
            val payloadSize = boxSize - headerLen
            if (payloadSize < 0L) return null

            val recurseResult = when (type) {
                "moov", "udta", "ilst" ->
                    scanMp4Container(stream, payloadSize, depth + 1)
                "meta" ->
                    if (payloadSize >= 4L && skipBytes(stream, 4L)) {
                        scanMp4Container(stream, payloadSize - 4L, depth + 1)
                    } else null
                "\u00A9lyr" ->
                    readMp4IlstText(stream, payloadSize)?.let { text ->
                        ExtractedContainerLyrics(
                            text = text.trim(),
                            isLrcFormatted = containsLrcTimestamps(text),
                            sourceLabel = "Embedded M4A"
                        )
                    }
                else -> {
                    if (!skipBytes(stream, payloadSize)) return null
                    null
                }
            }
            if (recurseResult != null) return recurseResult
            consumed += payloadSize
        }
        return null
    }

    private fun readMp4IlstText(stream: InputStream, containerSize: Long): String? {
        var left = containerSize
        val head = ByteArray(8)
        while (left >= 8L) {
            if (!readExact(stream, head, 8)) return null
            left -= 8L
            val boxSize = beUInt(head, 0)
            val type = String(head, 4, 4, Charsets.US_ASCII)
            val payload = boxSize - 8L
            if (payload < 0L) return null
            if (type == "data" && payload > 8L) {
                val capped = min(payload, MAX_HEADER_BYTES.toLong()).toInt()
                val buf = ByteArray(capped)
                if (!readExact(stream, buf, capped)) return null
                return String(buf, 8, buf.size - 8, Charsets.UTF_8).takeIf { it.isNotBlank() }
            }
            if (!skipBytes(stream, payload)) return null
            left -= payload
        }
        return null
    }


    private fun parseEbmlTags(stream: InputStream): ExtractedContainerLyrics? {
        val buf = ByteArray(512 * 1024)
        val n = readAtMost(stream, buf, buf.size)
        if (n < 32) return null

        val markers = listOf("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS")
        for (marker in markers) {
            val idx = findAsciiSubsequence(buf, n, marker.toByteArray(Charsets.US_ASCII))
            if (idx != null) {
                val searchEnd = min(n - 3, idx + marker.length + 64)
                for (k in (idx + marker.length) until searchEnd) {
                    if (buf[k] == 0x44.toByte() && buf[k + 1] == 0x87.toByte()) {
                        val vint = readEbmlVint(buf, k + 2, n) ?: continue
                        val strStart = vint.first
                        val strLen = vint.second.toInt().coerceAtMost(n - strStart)
                        if (strLen > 0) {
                            val text = String(buf, strStart, strLen, Charsets.UTF_8).trim()
                            if (text.isNotBlank()) {
                                return ExtractedContainerLyrics(
                                    text = text,
                                    isLrcFormatted = containsLrcTimestamps(text),
                                    sourceLabel = "Embedded Matroska"
                                )
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun readEbmlVint(buf: ByteArray, offset: Int, limit: Int): Pair<Int, Long>? {
        if (offset >= limit) return null
        val first = buf[offset].toInt() and 0xFF
        if (first == 0) return null
        var mask = 0x80
        var width = 1
        while (width <= 8 && (first and mask) == 0) {
            mask = mask ushr 1
            width++
        }
        if (width > 4 || offset + width > limit) return null
        var value = (first and (mask - 1)).toLong()
        for (i in 1 until width) {
            value = (value shl 8) or (buf[offset + i].toLong() and 0xFFL)
        }
        return (offset + width) to value
    }


    private fun containsLrcTimestamps(text: String): Boolean =
        Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""").containsMatchIn(text)

    private fun formatLrcTimestamp(ms: Long): String {
        val totalSec = ms / 1000L
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        val centis = (ms % 1000L) / 10L
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis)
    }

    private fun id3Charset(enc: Int): Charset = when (enc) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.UTF_8
    }

    private fun decodeId3String(buf: ByteArray, offset: Int, len: Int, charset: Charset): String {
        if (len <= 0 || offset + len > buf.size) return ""
        return String(buf, offset, len, charset).trimEnd('\u0000')
    }

    private fun deUnsync(src: ByteArray, offset: Int, len: Int): ByteArray {
        val out = ByteArrayOutputStream(len)
        var prevFF = false
        for (i in offset until (offset + len)) {
            val b = src[i].toInt() and 0xFF
            if (prevFF && b == 0x00) {
                prevFF = false
                continue
            }
            out.write(b)
            prevFF = (b == 0xFF)
        }
        return out.toByteArray()
    }

    private fun findAsciiSubsequence(haystack: ByteArray, len: Int, needle: ByteArray): Int? {
        if (needle.isEmpty() || len < needle.size) return null
        val maxStart = len - needle.size
        for (i in 0..maxStart) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return null
    }

    private fun syncSafeInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or
            ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or
            (b[o + 3].toInt() and 0x7F)

    private fun beInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    private fun beUInt(b: ByteArray, o: Int): Long = beInt(b, o).toLong() and 0xFFFFFFFFL

    private fun beLong(b: ByteArray, o: Int): Long {
        var r = 0L
        for (i in 0..7) r = (r shl 8) or (b[o + i].toLong() and 0xFFL)
        return r
    }

    private fun leInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun readAtMost(stream: InputStream, buf: ByteArray, len: Int): Int {
        var total = 0
        while (total < len) {
            val n = stream.read(buf, total, len - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun readExact(stream: InputStream, buf: ByteArray, len: Int): Boolean =
        readAtMost(stream, buf, len) == len

    private fun skipBytes(stream: InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                if (stream.read() == -1) return false
                remaining -= 1L
            }
        }
        return true
    }
}
