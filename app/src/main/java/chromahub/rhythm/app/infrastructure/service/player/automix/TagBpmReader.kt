/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.automix

import android.content.Context
import android.net.Uri
import android.util.Log
import java.nio.charset.StandardCharsets

data class AudioTagTempoKey(
    val bpm: Float? = null,
    val initialKey: String? = null
)

/**
 * Lightweight zero-allocation metadata reader extracting `TBPM` / `TKEY` (ID3v2), `BPM=` /
 * `INITIALKEY=` (FLAC/Vorbis comments), and `tmpo` (MP4/M4A atoms) from container headers.
 */
object TagBpmReader {
    private const val TAG = "TagBpmReader"
    private const val HEADER_PEEK_BYTES = 131_072

    fun readFromUri(context: Context, uri: Uri): AudioTagTempoKey {
        if (uri.scheme == "streaming") return AudioTagTempoKey()
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(HEADER_PEEK_BYTES)
                var total = 0
                while (total < buf.size) {
                    val r = stream.read(buf, total, buf.size - total)
                    if (r <= 0) break
                    total += r
                }
                if (total > 0) buf.copyOf(total) else null
            }
        }.onFailure {
            Log.v(TAG, "Unable to read header bytes for BPM tags", it)
        }.getOrNull() ?: return AudioTagTempoKey()

        return inspectBytes(head)
    }

    fun inspectBytes(data: ByteArray): AudioTagTempoKey {
        if (data.size < 16) return AudioTagTempoKey()

        if (data[0].toInt() == 'I'.code && data[1].toInt() == 'D'.code && data[2].toInt() == '3'.code) {
            val id3Result = parseId3v2TempoAndKey(data)
            if (id3Result.bpm != null || id3Result.initialKey != null) return id3Result
        }

        val vorbis = parseVorbisComments(data)
        if (vorbis.bpm != null || vorbis.initialKey != null) return vorbis

        val mp4Bpm = parseMp4Tmpo(data)
        if (mp4Bpm != null) return AudioTagTempoKey(bpm = mp4Bpm)

        return AudioTagTempoKey()
    }

    private fun parseId3v2TempoAndKey(data: ByteArray): AudioTagTempoKey {
        if (data.size < 10) return AudioTagTempoKey()
        val majorVersion = data[3].toInt() and 0xFF
        val flags = data[5].toInt() and 0xFF
        val tagSize = syncSafeInt(data, 6).coerceAtMost(data.size - 10)
        var offset = 10
        if (majorVersion == 4 && (flags and 0x40) != 0 && offset + 4 <= data.size) {
            offset += syncSafeInt(data, offset).coerceAtLeast(4)
        }

        var bpm: Float? = null
        var key: String? = null
        val limit = (10 + tagSize).coerceAtMost(data.size)

        while (offset + 10 <= limit) {
            val frameId = String(data, offset, 4, StandardCharsets.US_ASCII)
            if (frameId.all { it.code == 0 }) break
            val frameSize = if (majorVersion >= 4) syncSafeInt(data, offset + 4) else u32Be(data, offset + 4)
            if (frameSize <= 0 || offset + 10 + frameSize > limit) break

            val bodyStart = offset + 10
            if (frameId == "TBPM") {
                val text = decodeId3TextFrame(data, bodyStart, frameSize)
                bpm = text.toFloatOrNull()?.takeIf { it in 40f..250f }
            } else if (frameId == "TKEY") {
                val text = decodeId3TextFrame(data, bodyStart, frameSize)
                if (text.isNotBlank()) key = text
            }
            if (bpm != null && key != null) break
            offset += 10 + frameSize
        }
        return AudioTagTempoKey(bpm = bpm, initialKey = key)
    }

    private fun decodeId3TextFrame(data: ByteArray, start: Int, len: Int): String {
        if (len <= 1 || start + len > data.size) return ""
        val enc = data[start].toInt() and 0xFF
        val payloadLen = len - 1
        val raw = when (enc) {
            1, 2 -> String(data, start + 1, payloadLen, if (enc == 1) StandardCharsets.UTF_16 else StandardCharsets.UTF_16BE)
            3 -> String(data, start + 1, payloadLen, StandardCharsets.UTF_8)
            else -> String(data, start + 1, payloadLen, StandardCharsets.ISO_8859_1)
        }
        return raw.trim { it <= ' ' || it == '\u0000' }
    }

    private fun parseVorbisComments(data: ByteArray): AudioTagTempoKey {
        val text = String(data, StandardCharsets.ISO_8859_1)
        val bpmMatch = Regex("""(?:BPM|TEMPO)=([0-9]{2,3}(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE).find(text)
        val keyMatch = Regex("""(?:INITIALKEY|KEY)=([A-Za-z0-9#b♯♭\s]{1,16})""", RegexOption.IGNORE_CASE).find(text)
        val bpm = bpmMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()?.takeIf { it in 40f..250f }
        val key = keyMatch?.groupValues?.getOrNull(1)
            ?.takeWhile { it.code in 0x20..0x7E }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return AudioTagTempoKey(bpm = bpm, initialKey = key)
    }

    private fun parseMp4Tmpo(data: ByteArray): Float? {
        for (i in 0 until data.size - 18) {
            if (data[i] == 't'.code.toByte() && data[i + 1] == 'm'.code.toByte() &&
                data[i + 2] == 'p'.code.toByte() && data[i + 3] == 'o'.code.toByte()
            ) {
                if (data[i + 8] == 'd'.code.toByte() && data[i + 9] == 'a'.code.toByte() &&
                    data[i + 10] == 't'.code.toByte() && data[i + 11] == 'a'.code.toByte()
                ) {
                    val value = ((data[i + 16].toInt() and 0xFF) shl 8) or (data[i + 17].toInt() and 0xFF)
                    if (value in 40..250) return value.toFloat()
                }
            }
        }
        return null
    }

    private fun syncSafeInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)

    private fun u32Be(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
}
