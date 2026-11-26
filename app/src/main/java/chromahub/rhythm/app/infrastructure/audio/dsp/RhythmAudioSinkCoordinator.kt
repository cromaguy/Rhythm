/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.dsp

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainAudioProcessor
import java.nio.ByteBuffer

/**
 * Signal-path snapshot describing the active Media3 PCM encoding, sample rate,
 * channels, Double-precision DSP activity, and fallback status.
 */
data class AudioPipelineTelemetry(
    val inputEncoding: Int = C.ENCODING_INVALID,
    val outputEncoding: Int = C.ENCODING_INVALID,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
    val floatOutputEnabled: Boolean = false,
    val fallbackActive: Boolean = false,
    val bitPerfectMode: Boolean = false
)

/**
 * Coordinates Rhythm's primary [AudioSink] (which supports Hi-Res Float / 24-bit / 32-bit PCM
 * and Double-precision DSP processors) with a fail-open fallback [AudioSink] when the
 * device HAL rejects float or high-rate sink configuration, while updating [ReplayGainAudioProcessor]
 * root format metadata and live telemetry.
 */
@OptIn(UnstableApi::class)
class RhythmAudioSinkCoordinator(
    private val primarySink: AudioSink,
    private val fallbackSink: AudioSink? = null,
    private val replayGainProcessor: ReplayGainAudioProcessor? = null,
    private val isBitPerfectActive: () -> Boolean = { false },
    private val onTelemetryChanged: ((AudioPipelineTelemetry) -> Unit)? = null
) : ForwardingAudioSink(primarySink) {

    companion object {
        private const val TAG = "RhythmSinkCoord"
    }

    @Volatile
    private var usingFallback: Boolean = false

    @Volatile
    var telemetry: AudioPipelineTelemetry = AudioPipelineTelemetry()
        private set

    private fun delegate(): AudioSink = if (usingFallback && fallbackSink != null) fallbackSink else primarySink

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val inputFormat = audioSinkConfig.format
        replayGainProcessor?.setRootFormat(inputFormat)
        usingFallback = false
        try {
            primarySink.configure(audioSinkConfig)
            publishTelemetry(inputFormat, fallback = false)
        } catch (e: AudioSink.ConfigurationException) {
            val fb = fallbackSink
            if (fb != null) {
                Log.w(TAG, "Primary AudioSink rejected format (${inputFormat.sampleRate}Hz enc=${inputFormat.pcmEncoding}), falling back safely", e)
                usingFallback = true
                fb.configure(audioSinkConfig)
                publishTelemetry(inputFormat, fallback = true)
            } else {
                throw e
            }
        }
    }

    private fun publishTelemetry(inputFormat: Format, fallback: Boolean) {
        val next = AudioPipelineTelemetry(
            inputEncoding = inputFormat.pcmEncoding,
            outputEncoding = if (fallback) C.ENCODING_PCM_16BIT else inputFormat.pcmEncoding,
            sampleRateHz = inputFormat.sampleRate,
            channelCount = inputFormat.channelCount,
            floatOutputEnabled = !fallback && inputFormat.pcmEncoding == C.ENCODING_PCM_FLOAT,
            fallbackActive = fallback,
            bitPerfectMode = isBitPerfectActive()
        )
        telemetry = next
        onTelemetryChanged?.invoke(next)
    }

    override fun setListener(listener: AudioSink.Listener) {
        primarySink.setListener(listener)
        fallbackSink?.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) {
        primarySink.setPlayerId(playerId)
        fallbackSink?.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        primarySink.setClock(clock)
        fallbackSink?.setClock(clock)
    }

    override fun supportsFormat(format: Format): Boolean = delegate().supportsFormat(format)

    override fun getFormatSupport(format: Format): Int = delegate().getFormatSupport(format)

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        delegate().getFormatOffloadSupport(format)

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        delegate().getCurrentPositionUs(sourceEnded)

    override fun play() = delegate().play()

    override fun pause() = delegate().pause()

    override fun handleDiscontinuity() = delegate().handleDiscontinuity()

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean =
        delegate().handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

    override fun playToEndOfStream() = delegate().playToEndOfStream()

    override fun isEnded(): Boolean = delegate().isEnded()

    override fun hasPendingData(): Boolean = delegate().hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        primarySink.setPlaybackParameters(playbackParameters)
        fallbackSink?.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters = delegate().getPlaybackParameters()

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        primarySink.setSkipSilenceEnabled(skipSilenceEnabled)
        fallbackSink?.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = delegate().getSkipSilenceEnabled()

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        primarySink.setAudioAttributes(audioAttributes)
        fallbackSink?.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = delegate().getAudioAttributes()

    override fun getAudioCapabilities(): AudioCapabilities? = delegate().getAudioCapabilities()

    override fun setAudioSessionId(audioSessionId: Int) {
        primarySink.setAudioSessionId(audioSessionId)
        fallbackSink?.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        primarySink.setAuxEffectInfo(auxEffectInfo)
        fallbackSink?.setAuxEffectInfo(auxEffectInfo)
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        primarySink.setPreferredDevice(audioDeviceInfo)
        fallbackSink?.setPreferredDevice(audioDeviceInfo)
    }

    override fun setVirtualDeviceId(virtualDeviceId: Int) {
        primarySink.setVirtualDeviceId(virtualDeviceId)
        fallbackSink?.setVirtualDeviceId(virtualDeviceId)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        primarySink.setOutputStreamOffsetUs(outputStreamOffsetUs)
        fallbackSink?.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun getAudioTrackBufferSizeUs(): Long = delegate().getAudioTrackBufferSizeUs()

    override fun enableTunnelingV21() {
        primarySink.enableTunnelingV21()
        fallbackSink?.enableTunnelingV21()
    }

    override fun disableTunneling() {
        primarySink.disableTunneling()
        fallbackSink?.disableTunneling()
    }

    override fun setOffloadMode(offloadMode: Int) {
        primarySink.setOffloadMode(offloadMode)
        fallbackSink?.setOffloadMode(offloadMode)
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) {
        primarySink.setOffloadDelayPadding(delayInFrames, paddingInFrames)
        fallbackSink?.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    }

    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) {
        primarySink.setAudioOutputProvider(audioOutputProvider)
        fallbackSink?.setAudioOutputProvider(audioOutputProvider)
    }

    override fun setVolume(volume: Float) {
        primarySink.setVolume(volume)
        fallbackSink?.setVolume(volume)
    }

    override fun flush() {
        primarySink.flush()
        fallbackSink?.flush()
    }

    override fun reset() {
        primarySink.reset()
        fallbackSink?.reset()
        usingFallback = false
    }

    override fun release() {
        primarySink.release()
        fallbackSink?.release()
    }
}
