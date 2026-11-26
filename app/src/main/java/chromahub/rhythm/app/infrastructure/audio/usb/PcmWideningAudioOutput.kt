/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.usb

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Feeds [output], which runs in a 24- or 32-bit integer [outputEncoding], with PCM written in
 * 16-bit, 24-bit, or float [inputEncoding]. Every 16- and 24-bit sample value survives losslessly;
 * only the container width expands to satisfy USB Audio Class 2 DAC mixer contracts.
 */
@OptIn(UnstableApi::class)
internal class PcmWideningAudioOutput(
    private val output: AudioOutput,
    private val inputEncoding: Int,
    private val outputEncoding: Int
) : AudioOutput {

    companion object {
        fun bytesPerSample(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
        }
    }

    private val inputBytes = bytesPerSample(inputEncoding)
    private val outputBytes = bytesPerSample(outputEncoding)
    private var widened: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private var pendingInput: ByteBuffer? = null
    private var pendingStart = 0

    init {
        require(
            inputEncoding == C.ENCODING_PCM_16BIT ||
                inputEncoding == C.ENCODING_PCM_24BIT ||
                inputEncoding == C.ENCODING_PCM_FLOAT
        )
        require(outputEncoding == C.ENCODING_PCM_24BIT || outputEncoding == C.ENCODING_PCM_32BIT)
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (buffer !== pendingInput) {
            widen(buffer)
            pendingInput = buffer
            pendingStart = buffer.position()
        }
        val fullyWritten = output.write(widened, encodedAccessUnitCount, presentationTimeUs)
        buffer.position(pendingStart + widened.position() / outputBytes * inputBytes)
        if (fullyWritten) pendingInput = null
        return fullyWritten
    }

    private fun widen(buffer: ByteBuffer) {
        val count = buffer.remaining() / inputBytes
        if (widened.capacity() < count * outputBytes) {
            widened = ByteBuffer.allocateDirect(count * outputBytes).order(ByteOrder.nativeOrder())
        }
        widened.clear()
        val source = buffer.duplicate().order(ByteOrder.nativeOrder())
        val base = buffer.position()
        for (i in 0 until count) {
            val value: Long = when (inputEncoding) {
                C.ENCODING_PCM_FLOAT -> {
                    val scale = if (outputEncoding == C.ENCODING_PCM_24BIT) 8_388_608.0 else 2_147_483_648.0
                    (source.getFloat(base + 4 * i) * scale).roundToLong()
                }
                C.ENCODING_PCM_24BIT -> {
                    val off = base + 3 * i
                    var v = (source.get(off).toInt() and 0xFF) or
                        ((source.get(off + 1).toInt() and 0xFF) shl 8) or
                        ((source.get(off + 2).toInt() and 0xFF) shl 16)
                    if ((v and 0x800000) != 0) v = v or -0x1000000
                    if (outputEncoding == C.ENCODING_PCM_32BIT) (v.toLong() shl 8) else v.toLong()
                }
                else -> {
                    val shift = if (outputEncoding == C.ENCODING_PCM_24BIT) 8 else 16
                    source.getShort(base + 2 * i).toLong() shl shift
                }
            }
            if (outputEncoding == C.ENCODING_PCM_24BIT) {
                val bits = value.coerceIn(-8_388_608L, 8_388_607L).toInt()
                widened.put(bits.toByte())
                widened.put((bits shr 8).toByte())
                widened.put((bits shr 16).toByte())
            } else {
                widened.putInt(value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            }
        }
        widened.flip()
    }

    override fun flush() {
        pendingInput = null
        output.flush()
    }

    override fun release() {
        pendingInput = null
        output.release()
    }

    override fun play() = output.play()
    override fun pause() = output.pause()
    override fun stop() = output.stop()
    override fun setVolume(volume: Float) = output.setVolume(volume)
    override fun isOffloadedPlayback(): Boolean = output.isOffloadedPlayback
    override fun getAudioSessionId(): Int = output.audioSessionId
    override fun getSampleRate(): Int = output.sampleRate
    override fun getBufferSizeInFrames(): Long = output.bufferSizeInFrames
    override fun getPositionUs(): Long = output.positionUs
    override fun getPlaybackParameters(): PlaybackParameters = output.playbackParameters
    override fun isStalled(): Boolean = output.isStalled
    override fun addListener(listener: AudioOutput.Listener) = output.addListener(listener)
    override fun removeListener(listener: AudioOutput.Listener) = output.removeListener(listener)
    override fun setPlaybackParameters(params: PlaybackParameters) = output.setPlaybackParameters(params)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) =
        output.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun setOffloadEndOfStream() = output.setOffloadEndOfStream()
    override fun setPlayerId(playerId: PlayerId) = output.setPlayerId(playerId)
    override fun canReuseAudioOutput(
        oldConfig: AudioOutputProvider.OutputConfig,
        newFormatConfig: AudioOutputProvider.FormatConfig,
        newConfig: AudioOutputProvider.OutputConfig
    ): Boolean = newConfig == oldConfig
    override fun attachAuxEffect(effectId: Int) = output.attachAuxEffect(effectId)
    override fun setAuxEffectSendLevel(level: Float) = output.setAuxEffectSendLevel(level)
    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = output.setPreferredDevice(preferredDevice)
}
