/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.usb

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatConfig
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.media.AudioAttributes as PlatformAudioAttributes

data class BitPerfectUsbStatus(
    val deviceName: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: Int
)

internal data class MixerFormat(
    val encoding: Int,
    val sampleRate: Int,
    val channelCount: Int,
    val bitPerfect: Boolean
)

/**
 * Selects the index in [offered] of the bit-perfect mixer format that carries PCM in
 * [pipelineEncoding] at [sampleRate] and [channelCount] without altering sample values, or -1.
 */
internal fun selectBitPerfectMixerFormat(
    offered: List<MixerFormat>,
    sampleRate: Int,
    channelCount: Int,
    pipelineEncoding: Int
): Int {
    val preference = when (pipelineEncoding) {
        C.ENCODING_PCM_16BIT -> listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)
        C.ENCODING_PCM_24BIT -> listOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)
        C.ENCODING_PCM_32BIT -> listOf(C.ENCODING_PCM_32BIT)
        C.ENCODING_PCM_FLOAT -> listOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)
        else -> emptyList()
    }
    for (encoding in preference) {
        val index = offered.indexOfFirst {
            it.bitPerfect && it.encoding == encoding &&
                it.sampleRate == sampleRate && it.channelCount == channelCount
        }
        if (index >= 0) return index
    }
    return -1
}

/**
 * Negotiates Android 14+ (API 34+) `MIXER_BEHAVIOR_BIT_PERFECT` USB DAC mixer attributes when a
 * connected USB DAC advertises a compatible rate/channel mixer, transparently widening 16-bit or
 * Float32 PCM containers via [PcmWideningAudioOutput] when the DAC demands 24/32-bit integers.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@OptIn(UnstableApi::class)
class BitPerfectUsbAudioOutputProvider(
    context: Context,
    private val delegate: AudioOutputProvider = AudioTrackAudioOutputProvider.Builder(context).build(),
    private val isEnabled: () -> Boolean = { true }
) : AudioOutputProvider {

    companion object {
        private const val TAG = "BitPerfectUsb"
        private const val MAX_PLANS = 8
        private val USB_TYPES = setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY
        )
        private val mixerLock = Any()
        private var currentOwner: BitPerfectUsbAudioOutputProvider? = null
        private val _activeStatus = MutableStateFlow<BitPerfectUsbStatus?>(null)
        val activeStatus: StateFlow<BitPerfectUsbStatus?> = _activeStatus.asStateFlow()
    }

    private class Plan(
        val device: AudioDeviceInfo,
        val attributes: PlatformAudioAttributes,
        val mixer: AudioMixerAttributes,
        val outputConfig: OutputConfig
    )

    private class AppliedMixer(val device: AudioDeviceInfo, val attributes: PlatformAudioAttributes)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val plans = HashMap<OutputConfig, Plan>()
    private var appliedMixer: AppliedMixer? = null

    override fun getFormatSupport(formatConfig: FormatConfig) = delegate.getFormatSupport(formatConfig)

    override fun getOutputConfig(formatConfig: FormatConfig): OutputConfig {
        val config = delegate.getOutputConfig(formatConfig)
        if (!isEnabled()) return config
        return try {
            planBitPerfectOutput(formatConfig, config)
        } catch (e: Exception) {
            Log.w(TAG, "Bit-perfect USB mixer setup failed; falling back to standard AudioTrack", e)
            config
        }
    }

    private fun planBitPerfectOutput(formatConfig: FormatConfig, config: OutputConfig): OutputConfig {
        val format = formatConfig.format
        if (config.isOffload || config.isTunneling || format.sampleMimeType != MimeTypes.AUDIO_RAW) return config

        val attributes = formatConfig.audioAttributes.platformAudioAttributes
        val preferredDevice = formatConfig.preferredDevice
        val device = if (preferredDevice != null) {
            preferredDevice.takeIf { it.type in USB_TYPES }
        } else {
            audioManager?.getAudioDevicesForAttributes(attributes)?.firstOrNull { it.type in USB_TYPES }
        } ?: return config

        val offered = audioManager?.getSupportedMixerAttributes(device) ?: return config
        val index = selectBitPerfectMixerFormat(
            offered = offered.map { it.toMixerFormat() },
            sampleRate = format.sampleRate,
            channelCount = format.channelCount,
            pipelineEncoding = format.pcmEncoding
        )
        if (index < 0) return config

        val mixer = offered[index]
        val mixerEncoding = mixer.format.encoding
        if (plans.size >= MAX_PLANS) plans.clear()
        if (mixerEncoding == format.pcmEncoding) {
            plans[config] = Plan(device, attributes, mixer, config)
            return config
        }

        val widened = delegate.getOutputConfig(formatConfig.withPcmEncoding(mixerEncoding))
        val channelCount = format.channelCount.coerceAtLeast(1)
        val widenedFrameBytes = channelCount * PcmWideningAudioOutput.bytesPerSample(mixerEncoding)
        val sinkFrameBytes = channelCount * PcmWideningAudioOutput.bytesPerSample(format.pcmEncoding)
        val sinkBufferSize = (widened.bufferSize / widenedFrameBytes) * sinkFrameBytes
        val forSink = widened.buildUpon()
            .setEncoding(format.pcmEncoding)
            .setBufferSize(sinkBufferSize)
            .build()
        plans[forSink] = Plan(device, attributes, mixer, widened)
        return forSink
    }

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        val plan = plans[config]
        if (plan == null || !isEnabled()) {
            clearPreferredMixer()
            return delegate.getAudioOutput(config)
        }
        if (!applyPreferredMixer(plan)) {
            clearPreferredMixer()
            return delegate.getAudioOutput(config)
        }
        val output = delegate.getAudioOutput(plan.outputConfig)
        return if (plan.outputConfig.encoding == config.encoding) {
            output
        } else {
            PcmWideningAudioOutput(output, config.encoding, plan.outputConfig.encoding)
        }
    }

    override fun addListener(listener: AudioOutputProvider.Listener) = delegate.addListener(listener)

    override fun removeListener(listener: AudioOutputProvider.Listener) = delegate.removeListener(listener)

    override fun setClock(clock: Clock) = delegate.setClock(clock)

    override fun release() {
        clearPreferredMixer()
        plans.clear()
        delegate.release()
    }

    private fun applyPreferredMixer(plan: Plan): Boolean {
        synchronized(mixerLock) {
            return try {
                val mgr = audioManager ?: return false
                val previous = appliedMixer
                if (previous != null && previous.device.id != plan.device.id && currentOwner === this) {
                    runCatching { mgr.clearPreferredMixerAttributes(previous.attributes, previous.device) }
                    appliedMixer = null
                }
                val current = mgr.getPreferredMixerAttributes(plan.attributes, plan.device)
                if (current != plan.mixer) {
                    val applied = mgr.setPreferredMixerAttributes(plan.attributes, plan.device, plan.mixer)
                    Log.i(TAG, "Preferred mixer on ${plan.device.productName} -> ${plan.mixer.toMixerFormat()}: $applied")
                    if (!applied) return false
                }
                appliedMixer = AppliedMixer(plan.device, plan.attributes)
                currentOwner = this
                _activeStatus.value = BitPerfectUsbStatus(
                    deviceName = plan.device.productName?.toString().orEmpty(),
                    sampleRateHz = plan.mixer.format.sampleRate,
                    channelCount = plan.mixer.format.channelCount,
                    encoding = plan.mixer.format.encoding
                )
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply bit-perfect USB mixer attributes", e)
                false
            }
        }
    }

    private fun clearPreferredMixer() {
        synchronized(mixerLock) {
            val applied = appliedMixer ?: return
            appliedMixer = null
            if (currentOwner !== this) return
            currentOwner = null
            _activeStatus.value = null
            runCatching { audioManager?.clearPreferredMixerAttributes(applied.attributes, applied.device) }
        }
    }

    private fun AudioMixerAttributes.toMixerFormat() = MixerFormat(
        encoding = format.encoding,
        sampleRate = format.sampleRate,
        channelCount = format.channelCount,
        bitPerfect = mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
    )

    private fun FormatConfig.withPcmEncoding(encoding: Int): FormatConfig =
        FormatConfig.Builder(format.buildUpon().setPcmEncoding(encoding).build())
            .setAudioAttributes(audioAttributes)
            .setPreferredDevice(preferredDevice)
            .setEnableHighResolutionPcmOutput(enableHighResolutionPcmOutput)
            .setEnablePlaybackParameters(enablePlaybackParameters)
            .setEnableOffload(false)
            .setAudioSessionId(audioSessionId)
            .setVirtualDeviceId(virtualDeviceId)
            .setEnableTunneling(false)
            .setPreferredBufferSize(preferredBufferSize)
            .build()
}
