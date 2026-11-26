/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks

/**
 * Codec-selection and audio-track compatibility helpers preserving Float/Hi-Res fidelity when
 * software/FFmpeg decoders are available and detecting unsupported audio groups reliably.
 */
object AudioDecoderPolicy {

    private val HIGH_RES_LOSSLESS_MIMES = setOf(
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_WAV,
        MimeTypes.AUDIO_RAW,
        "audio/aiff",
        "audio/x-aiff",
        "audio/dsd"
    )

    fun isSoftwareOrFfmpegDecoder(decoderName: String): Boolean {
        val lower = decoderName.lowercase()
        return lower.startsWith("c2.android.") ||
            lower.startsWith("omx.google.") ||
            lower.contains("ffmpeg") ||
            lower.contains("alac") ||
            lower.contains("soft")
    }

    fun <T> prioritizeDecoders(
        decoders: List<T>,
        nameOf: (T) -> String,
        isHardwareOf: (T) -> Boolean,
        disabledDecoders: Set<String>,
        preferHardware: Boolean,
        floatOutputActive: Boolean,
        mimeType: String?
    ): List<T> {
        val filtered = if (disabledDecoders.isEmpty()) {
            decoders
        } else {
            decoders.filter { nameOf(it) !in disabledDecoders }
        }
        val preferSoftFloat = floatOutputActive && mimeType != null && mimeType in HIGH_RES_LOSSLESS_MIMES
        return when {
            preferSoftFloat -> filtered.sortedByDescending { if (isSoftwareOrFfmpegDecoder(nameOf(it))) 1 else 0 }
            preferHardware -> filtered.sortedByDescending { if (isHardwareOf(it)) 1 else 0 }
            else -> filtered
        }
    }

    fun hasUnsupportedAudio(tracks: Tracks): Boolean {
        var sawAudioGroup = false
        var anySupported = false
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                sawAudioGroup = true
                if (group.isSupported) {
                    anySupported = true
                    break
                }
            }
        }
        return sawAudioGroup && !anySupported
    }
}
