/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.os.Handler
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertNotNull
import org.junit.Test

class RhythmDecoderRendererTest {

    @Test
    fun ffmpegAudioRendererClass_existsAndHasRequiredConstructor() {
        val clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
        assertNotNull("FfmpegAudioRenderer class should be found in classpath", clazz)
        val constructor = clazz.getConstructor(
            Handler::class.java,
            AudioRendererEventListener::class.java,
            AudioSink::class.java
        )
        assertNotNull("FfmpegAudioRenderer constructor should be available for DefaultRenderersFactory", constructor)
    }
}
