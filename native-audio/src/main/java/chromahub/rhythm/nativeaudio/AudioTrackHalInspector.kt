/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.nativeaudio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build

data class HalAudioCapabilities(
    val nativeLibraryLoaded: Boolean,
    val safetyGuardPermitsNative: Boolean,
    val aaudioExclusiveSupported: Boolean,
    val aaudioMmapSupported: Boolean,
    val aaudioHalSampleRateHz: Int,
    val aaudioFramesPerBurst: Int,
    val directBitStreamSupported: Boolean,
    val directOffloadSupported: Boolean
)

/**
 * Inspects safe public HAL, AAudio Exclusive/MMAP, and Android 13+ direct playback support while
 * enforcing [NativeAudioSafetyGuard] for GrapheneOS / Tecno firmware protections.
 */
object AudioTrackHalInspector {

    fun inspect(context: Context): HalAudioCapabilities {
        val safetyPermits = NativeAudioSafetyGuard.canLoadNativeLibrary()
        val nativeProbe = if (safetyPermits) {
            NativeAudioBridge.probeAAudioDirectOutput(preferredChannels = 2)
        } else {
            AAudioDirectProbeResult(false, -1, false, false, 0, 0, 0, 0, 0, 0)
        }

        var directBitStream = false
        var directOffload = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED)
                        .setSampleRate(96_000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    val support = AudioManager.getDirectPlaybackSupport(format, attrs)
                    directBitStream = (support and AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED) != 0
                    directOffload = (support and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED) != 0
                }
            }
        }

        return HalAudioCapabilities(
            nativeLibraryLoaded = NativeAudioBridge.isLoaded,
            safetyGuardPermitsNative = safetyPermits,
            aaudioExclusiveSupported = nativeProbe.exclusive,
            aaudioMmapSupported = nativeProbe.mmap,
            aaudioHalSampleRateHz = nativeProbe.sampleRateHz,
            aaudioFramesPerBurst = nativeProbe.framesPerBurst,
            directBitStreamSupported = directBitStream,
            directOffloadSupported = directOffload
        )
    }
}
