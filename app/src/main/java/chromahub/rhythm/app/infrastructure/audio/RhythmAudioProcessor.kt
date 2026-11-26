/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.infrastructure.audio.dsp.RhythmDoubleDspPipeline
import chromahub.rhythm.app.infrastructure.audio.dsp.TpdfDitherState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * Base audio processor for real-time DSP effects in Rhythm.
 *
 * Accepts 8/16/24/32-bit integer and 32-bit float PCM streams, converts frames into normalized
 * IEEE-754 Double arrays for zero-quantization DSP processing in subclasses overriding
 * [processDoubleSamples], and applies triangular TPDF dither when outputting 16-bit PCM.
 */
@OptIn(UnstableApi::class)
@Suppress("OVERRIDE_DEPRECATION")
abstract class RhythmAudioProcessor : AudioProcessor {

    companion object {
        private const val TAG = "RhythmAudioProcessor"
        private const val MAX_POOL_SIZE = 4

        private val bufferPool = ArrayDeque<ShortArray>(MAX_POOL_SIZE)
        private val doublePool = ArrayDeque<DoubleArray>(MAX_POOL_SIZE)
        private val byteBufferPool = ArrayDeque<ByteBuffer>(MAX_POOL_SIZE)

        fun acquireShortArray(size: Int): ShortArray {
            synchronized(bufferPool) {
                val it = bufferPool.iterator()
                while (it.hasNext()) {
                    val buf = it.next()
                    if (buf.size >= size) {
                        it.remove()
                        return buf
                    }
                }
            }
            return ShortArray(size)
        }

        fun releaseShortArray(buffer: ShortArray) {
            synchronized(bufferPool) {
                if (bufferPool.size < MAX_POOL_SIZE) {
                    bufferPool.addLast(buffer)
                }
            }
        }

        fun acquireDoubleArray(size: Int): DoubleArray {
            synchronized(doublePool) {
                val it = doublePool.iterator()
                while (it.hasNext()) {
                    val buf = it.next()
                    if (buf.size >= size) {
                        it.remove()
                        return buf
                    }
                }
            }
            return DoubleArray(size)
        }

        fun releaseDoubleArray(buffer: DoubleArray) {
            synchronized(doublePool) {
                if (doublePool.size < MAX_POOL_SIZE) {
                    doublePool.addLast(buffer)
                }
            }
        }

        fun acquireByteBuffer(size: Int): ByteBuffer {
            synchronized(byteBufferPool) {
                val it = byteBufferPool.iterator()
                while (it.hasNext()) {
                    val buf = it.next()
                    if (buf.capacity() >= size) {
                        it.remove()
                        buf.clear()
                        return buf
                    }
                }
            }
            return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        }

        fun releaseByteBuffer(buffer: ByteBuffer) {
            synchronized(byteBufferPool) {
                if (byteBufferPool.size < MAX_POOL_SIZE) {
                    buffer.clear()
                    byteBufferPool.addLast(buffer)
                }
            }
        }
    }

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private val ditherState = TpdfDitherState()

    protected var sampleRate: Int = 44100
    protected var channelCount: Int = 2
    protected var encoding: Int = C.ENCODING_PCM_16BIT

    abstract fun processSamples(samples: ShortArray, sampleCount: Int)

    /**
     * Normalized 64-bit Double-precision DSP entry point (`[-1.0, 1.0]` interleaved frames).
     * Subclasses should override this directly to preserve full 24/32-bit/Float dynamic range.
     */
    open fun processDoubleSamples(samples: DoubleArray, sampleCount: Int) {
        val shorts = acquireShortArray(sampleCount)
        try {
            for (i in 0 until sampleCount) {
                shorts[i] = (samples[i].coerceIn(-1.0, 1.0) * 32767.0).roundToInt()
                    .coerceIn(-32768, 32767).toShort()
            }
            processSamples(shorts, sampleCount)
            for (i in 0 until sampleCount) {
                samples[i] = shorts[i].toDouble() / 32768.0
            }
        } finally {
            releaseShortArray(shorts)
        }
    }

    abstract fun isEnabled(): Boolean

    open fun isBypassed(): Boolean = !isEnabled()

    open fun needs16BitDither(): Boolean = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Log.d(TAG, "configure() -> rate: ${inputAudioFormat.sampleRate}, ch: ${inputAudioFormat.channelCount}, enc: ${inputAudioFormat.encoding}")

        if (!RhythmDoubleDspPipeline.isSupportedEncoding(inputAudioFormat.encoding)) {
            this.inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            this.outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        this.inputAudioFormat = inputAudioFormat
        this.sampleRate = inputAudioFormat.sampleRate
        this.channelCount = inputAudioFormat.channelCount
        this.encoding = inputAudioFormat.encoding
        this.outputAudioFormat = inputAudioFormat

        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        return inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET &&
            RhythmDoubleDspPipeline.isSupportedEncoding(encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val size = inputBuffer.remaining()
        if (buffer.capacity() < size) {
            if (buffer !== AudioProcessor.EMPTY_BUFFER) releaseByteBuffer(buffer)
            buffer = acquireByteBuffer(size)
        }

        buffer.clear()

        if (isBypassed()) {
            buffer.put(inputBuffer)
            buffer.flip()
            outputBuffer = buffer
            return
        }

        val bps = RhythmDoubleDspPipeline.bytesPerSample(encoding)
        val sampleCount = size / bps
        if (sampleCount <= 0) {
            buffer.put(inputBuffer)
            buffer.flip()
            outputBuffer = buffer
            return
        }

        val doubles = acquireDoubleArray(sampleCount)
        try {
            val readCount = RhythmDoubleDspPipeline.readToDoubles(inputBuffer, encoding, doubles)
            processDoubleSamples(doubles, readCount)
            RhythmDoubleDspPipeline.writeFromDoubles(
                samples = doubles,
                sampleCount = readCount,
                encoding = encoding,
                output = buffer,
                ditherRng = if (encoding == C.ENCODING_PCM_16BIT && needs16BitDither()) ditherState else null
            )
            // Copy any trailing partial-frame bytes verbatim if present
            if (inputBuffer.hasRemaining()) {
                buffer.put(inputBuffer)
            }
        } finally {
            releaseDoubleArray(doubles)
        }

        buffer.flip()
        outputBuffer = buffer
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
    }
}
