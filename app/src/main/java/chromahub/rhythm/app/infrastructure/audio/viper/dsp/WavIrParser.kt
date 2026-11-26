/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper.dsp

import android.content.Context
import android.net.Uri
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes standard RIFF WAVE / ViPER IRS impulse response files into normalized stereo Float arrays
 * (supporting 8/16/24/32-bit PCM and 32-bit IEEE float, mono or stereo).
 */
object WavIrParser {
    private const val MAX_IR_SAMPLES = 131_072 // ~2.7 s @ 48 kHz
    private const val MAX_FILE_BYTES = 4 * 1024 * 1024

    data class ParsedIr(
        val sampleRate: Int,
        val left: FloatArray,
        val right: FloatArray
    )

    fun loadFromUri(context: Context, uri: Uri): ParsedIr? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseStream(stream)
            }
        }.getOrNull()

    fun parseStream(input: InputStream): ParsedIr? {
        val raw = input.readBytesLimited(MAX_FILE_BYTES) ?: return null
        return parseBytes(raw)
    }

    fun parseBytes(data: ByteArray): ParsedIr? {
        if (data.size < 44) return null
        if (ascii4(data, 0) != "RIFF" || ascii4(data, 8) != "WAVE") return null

        var audioFormat = 0
        var channels = 0
        var sampleRate = 48_000
        var bitsPerSample = 0
        var pcmOffset = -1
        var pcmSize = 0

        var offset = 12
        while (offset + 8 <= data.size) {
            val id = ascii4(data, offset)
            val chunkSize = u32Le(data, offset + 4).toInt().coerceAtLeast(0)
            val payload = offset + 8
            if (payload > data.size) break
            val safeSize = min(chunkSize, data.size - payload)

            when (id) {
                "fmt " -> if (safeSize >= 16) {
                    audioFormat = u16Le(data, payload)
                    channels = u16Le(data, payload + 2)
                    sampleRate = u32Le(data, payload + 4).toInt().coerceIn(8_000, 384_000)
                    bitsPerSample = u16Le(data, payload + 14)
                }
                "data" -> {
                    pcmOffset = payload
                    pcmSize = safeSize
                    break
                }
            }
            offset = payload + chunkSize + (chunkSize and 1)
        }

        if (pcmOffset < 0 || pcmSize <= 0 || channels <= 0) return null
        val isFloat = audioFormat == 3 && bitsPerSample == 32
        val isIntPcm = audioFormat == 1 && (bitsPerSample == 8 || bitsPerSample == 16 || bitsPerSample == 24 || bitsPerSample == 32)
        if (!isFloat && !isIntPcm) return null

        val bytesPerSample = bitsPerSample / 8
        val frameBytes = bytesPerSample * channels
        if (frameBytes <= 0) return null

        val frameCount = min(pcmSize / frameBytes, MAX_IR_SAMPLES)
        if (frameCount <= 0) return null

        val left = FloatArray(frameCount)
        val right = FloatArray(frameCount)
        var energySum = 0.0

        for (i in 0 until frameCount) {
            val frameStart = pcmOffset + i * frameBytes
            val l = readSample(data, frameStart, bitsPerSample, isFloat)
            val r = if (channels >= 2) readSample(data, frameStart + bytesPerSample, bitsPerSample, isFloat) else l
            left[i] = l
            right[i] = r
            energySum += max(abs(l), abs(r)).toDouble()
        }

        // Normalize IR gain against energy sum or peak so large IRs keep unity average gain
        var peak = 0f
        for (i in 0 until frameCount) {
            peak = max(peak, max(abs(left[i]), abs(right[i])))
        }
        val normScale = when {
            energySum > 1.0 -> (1.0 / kotlin.math.sqrt(energySum)).toFloat()
            peak > 1e-5f -> 1f / peak
            else -> 1f
        }.coerceIn(0.01f, 8f)

        if (abs(normScale - 1f) > 0.01f) {
            for (i in 0 until frameCount) {
                left[i] *= normScale
                right[i] *= normScale
            }
        }
        return ParsedIr(sampleRate = sampleRate, left = left, right = right)
    }

    private fun readSample(data: ByteArray, offset: Int, bits: Int, isFloat: Boolean): Float = when {
        isFloat -> {
            val bitsInt = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
            java.lang.Float.intBitsToFloat(bitsInt).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        }
        bits == 8 -> ((data[offset].toInt() and 0xFF) - 128) / 128f
        bits == 16 -> {
            val s = (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
            s.toShort().toFloat() / 32768f
        }
        bits == 24 -> {
            var v = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16)
            if ((v and 0x800000) != 0) v = v or -0x1000000
            v.toFloat() / 8_388_608f
        }
        bits == 32 -> {
            val v = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                (data[offset + 3].toInt() shl 24)
            (v.toDouble() / 2_147_483_648.0).toFloat()
        }
        else -> 0f
    }

    private fun ascii4(data: ByteArray, offset: Int): String =
        String(data, offset, 4, Charsets.US_ASCII)

    private fun u16Le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32Le(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
