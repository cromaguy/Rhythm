/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.replaygain

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs

@androidx.media3.common.util.UnstableApi
class ReplayGainAudioProcessor : BaseAudioProcessor() {
    companion object {
        private const val TAG = "ReplayGainAP"
    }

    private var compressor: AdaptiveDynamicRangeCompression? = null
    var mode = ReplayGainUtil.Mode.None
        private set
    var rgGain = 0 // dB
        private set
    var nonRgGain = 0 // dB
        private set
    var boostGain = 0 // dB
        private set
    var offloadEnabled = false
        private set
    var reduceGain = false
        private set
    var settingsChangedListener: (() -> Unit)? = null
    var boostGainChangedListener: (() -> Unit)? = null
    var offloadEnabledChangedListener: (() -> Unit)? = null
    private val toFloatPcmAudioProcessor = ToFloatPcmAudioProcessor()
    var targetGain = 1f
        private set
    var currentGain = 1f
        private set
    val gain: Float
        get() = targetGain
    private var kneeThresholdDb: Float? = null
    private var outputFloat: Boolean? = null
    private var pendingOutputFloat: Boolean? = null
    @Volatile
    private var tags: ReplayGainUtil.ReplayGainInfo? = null

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)
        if (inputBuffer.hasRemaining()) {
            val localTargetGain = targetGain
            val localCurrentGain = currentGain

            if (compressor != null) {
                var inputForCompressor = inputBuffer
                if (toFloatPcmAudioProcessor.isActive) {
                    toFloatPcmAudioProcessor.queueInput(inputBuffer)
                    inputForCompressor = toFloatPcmAudioProcessor.output
                }
                compressor!!.compress(
                    inputAudioFormat.channelCount,
                    localTargetGain,
                    kneeThresholdDb!!, 1f, inputForCompressor,
                    outputBuffer, frameCount
                )
                inputForCompressor.position(inputForCompressor.limit())
                outputBuffer.position(frameCount * outputAudioFormat.bytesPerFrame)
                currentGain = localTargetGain
            } else {
                if (localCurrentGain == 1f && localTargetGain == 1f) {
                    outputBuffer.put(inputBuffer)
                } else {
                    val needsRamp = abs(localTargetGain - localCurrentGain) > 0.0001f
                    if (!needsRamp) {
                        val g = localTargetGain
                        while (inputBuffer.hasRemaining()) {
                            when (inputAudioFormat.encoding) {
                                C.ENCODING_PCM_8BIT -> outputBuffer.put(
                                    (inputBuffer.get() * g).toInt().toByte()
                                )

                                C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
                                    outputBuffer.putShort(
                                        (inputBuffer.getShort() * g).toInt().toShort()
                                    )

                                C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                                    Util.putInt24(
                                        outputBuffer, (Util.getInt24(
                                            inputBuffer,
                                            inputBuffer.position()
                                        ) * g).toInt()
                                            .shl(8).shr(8)
                                    )
                                    inputBuffer.position(inputBuffer.position() + 3)
                                }

                                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN ->
                                    outputBuffer.putInt((inputBuffer.getInt() * g).toInt())

                                C.ENCODING_PCM_FLOAT -> outputBuffer.putFloat(
                                    inputBuffer.getFloat() * g
                                )

                                C.ENCODING_PCM_DOUBLE -> outputBuffer.putDouble(
                                    inputBuffer.getDouble() * g
                                )

                                else -> throw IllegalStateException("unreachable, bad encoding")
                            }
                        }
                        currentGain = localTargetGain
                    } else {
                        val gainStep = if (frameCount > 0) (localTargetGain - localCurrentGain) / frameCount else 0f
                        var effectiveGain = localCurrentGain
                        val channels = inputAudioFormat.channelCount
                        for (f in 0 until frameCount) {
                            effectiveGain += gainStep
                            for (c in 0 until channels) {
                                when (inputAudioFormat.encoding) {
                                    C.ENCODING_PCM_8BIT -> outputBuffer.put(
                                        (inputBuffer.get() * effectiveGain).toInt().toByte()
                                    )

                                    C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
                                        outputBuffer.putShort(
                                            (inputBuffer.getShort() * effectiveGain).toInt().toShort()
                                        )

                                    C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                                        Util.putInt24(
                                            outputBuffer, (Util.getInt24(
                                                inputBuffer,
                                                inputBuffer.position()
                                            ) * effectiveGain).toInt()
                                                .shl(8).shr(8)
                                        )
                                        inputBuffer.position(inputBuffer.position() + 3)
                                    }

                                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN ->
                                        outputBuffer.putInt((inputBuffer.getInt() * effectiveGain).toInt())

                                    C.ENCODING_PCM_FLOAT -> outputBuffer.putFloat(
                                        inputBuffer.getFloat() * effectiveGain
                                    )

                                    C.ENCODING_PCM_DOUBLE -> outputBuffer.putDouble(
                                        inputBuffer.getDouble() * effectiveGain
                                    )

                                    else -> throw IllegalStateException("unreachable, bad encoding")
                                }
                            }
                        }
                        currentGain = localTargetGain
                    }
                }
            }
        }
        outputBuffer.flip()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        val isSupportedPcm = encoding == C.ENCODING_PCM_8BIT ||
                encoding == C.ENCODING_PCM_16BIT ||
                encoding == C.ENCODING_PCM_24BIT ||
                encoding == C.ENCODING_PCM_32BIT ||
                encoding == C.ENCODING_PCM_FLOAT ||
                encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
                encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
                encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
        if (!isSupportedPcm) {
            throw IllegalStateException("unsupported pcm encoding $encoding")
        }
        
        val hasCompressor = synchronized(this) {
            computeGain()?.second != null || (computeGain() == null && reduceGain && nonRgGain > 0)
        }
        if (hasCompressor) { // we need a compressor which outputs float32
            pendingOutputFloat = true
            toFloatPcmAudioProcessor.configure(inputAudioFormat)
            return AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate, inputAudioFormat.channelCount,
                C.ENCODING_PCM_FLOAT
            )
        }
        pendingOutputFloat = false
        return inputAudioFormat
    }

    fun setMode(mode: ReplayGainUtil.Mode, doNotNotifyListener: Boolean = false): Boolean {
        val listener: (() -> Unit)?
        synchronized(this) {
            if (this.mode == mode) {
                return true
            }
            listener = settingsChangedListener
            this.mode = mode
        }
        if (!doNotNotifyListener) {
            listener?.invoke()
            return applyGain()
        } else return true
    }

    fun setRgGain(rgGain: Int): Boolean {
        val listener: (() -> Unit)?
        synchronized(this) {
            if (this.rgGain == rgGain) {
                return true
            }
            listener = settingsChangedListener
            this.rgGain = rgGain
        }
        listener?.invoke()
        return applyGain()
    }

    fun setNonRgGain(nonRgGain: Int): Boolean {
        val listener: (() -> Unit)?
        synchronized(this) {
            if (this.nonRgGain == nonRgGain) {
                return true
            }
            listener = settingsChangedListener
            this.nonRgGain = nonRgGain
        }
        listener?.invoke()
        return applyGain()
    }

    fun setBoostGain(boostGain: Int): Boolean {
        val listener: (() -> Unit)?
        synchronized(this) {
            if (this.boostGain == boostGain) {
                return true
            }
            listener = boostGainChangedListener
            this.boostGain = boostGain
        }
        listener?.invoke()
        return applyGain()
    }

    fun setReduceGain(reduceGain: Boolean): Boolean {
        val listener: (() -> Unit)?
        synchronized(this) {
            if (this.reduceGain == reduceGain) {
                return true
            }
            listener = settingsChangedListener
            this.reduceGain = reduceGain
        }
        listener?.invoke()
        return applyGain()
    }

    fun setOffloadEnabled(offloadEnabled: Boolean): Boolean {
        val listener: (() -> Unit)?
        synchronized(this) {
            if (this.offloadEnabled == offloadEnabled) {
                return true
            }
            listener = offloadEnabledChangedListener
            this.offloadEnabled = offloadEnabled
        }
        listener?.invoke()
        return applyGain()
    }

    fun setRootFormat(inputFormat: Format) {
        val parsedTags = ReplayGainUtil.parse(inputFormat)
        synchronized(this) {
            tags = parsedTags
        }
        applyGain()
    }

    fun setTags(newTags: ReplayGainUtil.ReplayGainInfo?) {
        synchronized(this) {
            tags = newTags
        }
        applyGain()
    }

    fun getTags(): ReplayGainUtil.ReplayGainInfo? = synchronized(this) { tags }

    private fun computeGain(): Pair<Float, Float?>? {
        val mode: ReplayGainUtil.Mode
        val rgGain: Int
        val reduceGain: Boolean
        synchronized(this) {
            mode = this.mode
            rgGain = this.rgGain
            reduceGain = this.reduceGain
        }
        return ReplayGainUtil.calculateGain(
            tags, mode, rgGain,
            reduceGain, ReplayGainUtil.RATIO
        )
    }

    private fun applyGain(): Boolean {
        val nonRgGain: Int
        val reduceGain: Boolean
        synchronized(this) {
            nonRgGain = this.nonRgGain
            reduceGain = this.reduceGain
        }
        val gainPair = computeGain()
        val hasCompressor = gainPair?.second != null || (gainPair == null && reduceGain && nonRgGain > 0)

        // Always update targetGain so gain attenuation is never blocked even if float configuration is pending
        val newTargetGain = gainPair?.first ?: ReplayGainUtil.dbToAmpl(nonRgGain.toFloat())
        this.targetGain = newTargetGain

        // If currentGain was at default full volume (1f) and we now have an attenuation target,
        // snap currentGain directly to newTargetGain so audio starts immediately at normalized level
        if (currentGain == 1f && newTargetGain != 1f && mode != ReplayGainUtil.Mode.None) {
            currentGain = newTargetGain
        }

        if (hasCompressor != outputFloat) {
            return false
        }
        if (gainPair != null) {
            this.kneeThresholdDb = gainPair.second
        } else {
            if (reduceGain && nonRgGain > 0) {
                val postGainPeakDb = nonRgGain.toFloat()
                this.kneeThresholdDb = postGainPeakDb - postGainPeakDb * ReplayGainUtil.RATIO / (ReplayGainUtil.RATIO - 1f)
            } else {
                this.kneeThresholdDb = null
            }
        }
        if (kneeThresholdDb != null) {
            if (compressor == null)
                compressor = AdaptiveDynamicRangeCompression()
            compressor!!.init(
                inputAudioFormat.sampleRate,
                ReplayGainUtil.TAU_ATTACK, ReplayGainUtil.TAU_RELEASE,
                ReplayGainUtil.RATIO
            )
        } else {
            onReset() // delete compressor
        }
        return true
    }

    override fun isActive(): Boolean {
        synchronized(this) {
            return super.isActive() && mode != ReplayGainUtil.Mode.None
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        outputFloat = pendingOutputFloat
        toFloatPcmAudioProcessor.flush(streamMetadata)
        resolveTagsFromStreamMetadata(streamMetadata)
        if (!applyGain())
            Log.d(TAG, "raced between flush and configure, do nothing for now")
        if (mode != ReplayGainUtil.Mode.None) {
            currentGain = targetGain
        }
    }

    private fun resolveTagsFromStreamMetadata(streamMetadata: AudioProcessor.StreamMetadata) {
        try {
            val timeline = streamMetadata.timeline
            val periodUid = streamMetadata.periodUid
            if (!timeline.isEmpty && periodUid != null) {
                val period = Timeline.Period()
                val periodIndex = timeline.getIndexOfPeriod(periodUid)
                if (periodIndex != C.INDEX_UNSET) {
                    timeline.getPeriod(periodIndex, period)
                    val window = Timeline.Window()
                    timeline.getWindow(period.windowIndex, window)
                    val mediaId = window.mediaItem.mediaId
                    val cachedTags = ReplayGainCache.get(mediaId)
                    if (cachedTags != null) {
                        synchronized(this) {
                            tags = cachedTags
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve ReplayGain tags from streamMetadata", e)
        }
    }

    override fun onReset() {
        toFloatPcmAudioProcessor.reset()
        compressor?.release()
        compressor = null
    }
}
