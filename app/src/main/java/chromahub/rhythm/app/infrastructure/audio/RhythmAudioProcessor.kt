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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.ArrayDeque

/**
 * Base audio processor for real-time DSP effects in Rhythm.
 */
@OptIn(UnstableApi::class)
@Suppress("OVERRIDE_DEPRECATION")
abstract class RhythmAudioProcessor : AudioProcessor {
    
    companion object {
        private const val TAG = "RhythmAudioProcessor"
        private const val MAX_POOL_SIZE = 4
        
        private val bufferPool = ArrayDeque<ShortArray>(MAX_POOL_SIZE)
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
    private var shortBuffer: ShortBuffer = AudioProcessor.EMPTY_BUFFER.asShortBuffer()
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    protected var sampleRate: Int = 44100
    protected var channelCount: Int = 2
    protected var encoding: Int = C.ENCODING_PCM_16BIT
    
    abstract fun processSamples(samples: ShortArray, sampleCount: Int)
    
    abstract fun isEnabled(): Boolean

    open fun isBypassed(): Boolean = !isEnabled()
    
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Log.d(TAG, "configure() -> rate: ${inputAudioFormat.sampleRate}, channels: ${inputAudioFormat.channelCount}")
        
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
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
        return inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET && encoding == C.ENCODING_PCM_16BIT
    }
    
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        
        val size = inputBuffer.remaining()
        if (buffer.capacity() < size) {
            if (buffer !== AudioProcessor.EMPTY_BUFFER) releaseByteBuffer(buffer)
            buffer = acquireByteBuffer(size)
            shortBuffer = buffer.asShortBuffer()
        }
        
        buffer.clear()
        buffer.put(inputBuffer)
        buffer.flip()
        outputBuffer = buffer

        if (isBypassed()) return
        
        val sampleCount = size / 2
        val samples = acquireShortArray(sampleCount)
        try {
            shortBuffer.position(0)
            shortBuffer.get(samples, 0, sampleCount)
            
            processSamples(samples, sampleCount)
            
            shortBuffer.position(0)
            shortBuffer.put(samples, 0, sampleCount)
        } finally {
            releaseShortArray(samples)
        }
        
        buffer.position(0)
        buffer.limit(size)
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
