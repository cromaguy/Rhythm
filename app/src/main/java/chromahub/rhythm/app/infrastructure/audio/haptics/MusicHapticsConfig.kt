/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.haptics

enum class MusicHapticsMode {
    BASS_AND_BEATS,
    VOCALS_ONLY,
    WHOLE_SONG
}

data class MusicHapticsConfig(
    val enabled: Boolean = false,
    val intensityScale: Float = 0.7f,
    val mode: MusicHapticsMode = MusicHapticsMode.BASS_AND_BEATS,
    val muteOnExternalOutput: Boolean = true,
    val useSystemHapticGenerator: Boolean = false
)
