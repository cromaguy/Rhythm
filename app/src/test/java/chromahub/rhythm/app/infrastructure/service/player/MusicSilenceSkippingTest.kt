/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

@androidx.media3.common.util.UnstableApi
class MusicSilenceSkippingTest {

    private fun createStereo16BitFormat(sampleRate: Int = 44100): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    private fun createPcmBuffer(amplitude: Short, frameCount: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frameCount * 2 * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            buffer.putShort(amplitude) // Left channel
            buffer.putShort(amplitude) // Right channel
        }
        buffer.flip()
        return buffer
    }

    private fun feedAndDrain(processor: SilenceSkippingAudioProcessor, input: ByteBuffer) {
        val chunkSize = 4096
        while (input.hasRemaining()) {
            val oldLimit = input.limit()
            input.limit(min(input.position() + chunkSize, oldLimit))
            processor.queueInput(input)
            input.limit(oldLimit)
            while (processor.output.hasRemaining()) {
                val out = processor.output
                out.position(out.limit())
            }
        }
    }

    @Test
    fun musicTunedSilenceSkipping_preservesQuietMusicalIntros() {
        val sampleRate = 44100
        val format = createStereo16BitFormat(sampleRate)

        // Music-tuned processor as configured in RhythmPlayerEngine
        val musicProcessor = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs = */ 500_000L,
            /* silenceRetentionRatio = */ 0.1f,
            /* maxSilenceToKeepDurationUs = */ 1_000_000L,
            /* minVolumeToKeepPercentageWhenMuting = */ 0,
            /* silenceThresholdLevel = */ 128.toShort()
        )
        musicProcessor.setEnabled(true)
        musicProcessor.configure(format)
        musicProcessor.flush()

        // Default ExoPlayer processor (the buggy behavior reported in #652)
        val defaultProcessor = SilenceSkippingAudioProcessor()
        defaultProcessor.setEnabled(true)
        defaultProcessor.configure(format)
        defaultProcessor.flush()

        // Simulate a song: 1 second of quiet intro (amplitude 400 ~ -38 dBFS)
        // followed by 1 second of regular music (amplitude 10000 ~ -10 dBFS)
        val introFrames = sampleRate
        val bodyFrames = sampleRate

        val intro1 = createPcmBuffer(400.toShort(), introFrames)
        val body1 = createPcmBuffer(10000.toShort(), bodyFrames)

        val intro2 = createPcmBuffer(400.toShort(), introFrames)
        val body2 = createPcmBuffer(10000.toShort(), bodyFrames)

        // Feed to default processor
        feedAndDrain(defaultProcessor, intro1)
        feedAndDrain(defaultProcessor, body1)

        // Feed to music-tuned processor
        feedAndDrain(musicProcessor, intro2)
        feedAndDrain(musicProcessor, body2)

        // Default processor treated amplitude 400 (< 1024) as silence and skipped frames,
        // causing song intro to speed up and distort
        assertTrue(
            "Default processor should have skipped frames in quiet intro (skipped: ${defaultProcessor.skippedFrames})",
            defaultProcessor.skippedFrames > 0
        )

        // Music-tuned processor recognizes amplitude 400 (> 128) as valid audio and skips ZERO frames
        assertEquals(
            "Music-tuned processor must preserve quiet intro and not skip any frames",
            0L,
            musicProcessor.skippedFrames
        )
    }

    @Test
    fun musicTunedSilenceSkipping_skipsGenuineSilence() {
        val sampleRate = 44100
        val format = createStereo16BitFormat(sampleRate)

        val musicProcessor = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs = */ 500_000L,
            /* silenceRetentionRatio = */ 0.1f,
            /* maxSilenceToKeepDurationUs = */ 1_000_000L,
            /* minVolumeToKeepPercentageWhenMuting = */ 0,
            /* silenceThresholdLevel = */ 128.toShort()
        )
        musicProcessor.setEnabled(true)
        musicProcessor.configure(format)
        musicProcessor.flush()

        // 2.0 seconds of true silence (amplitude = 0) followed by 1.0 second of music
        val silenceFrames = sampleRate * 2
        val musicFrames = sampleRate

        val silenceBuffer = createPcmBuffer(0.toShort(), silenceFrames)
        val musicBuffer = createPcmBuffer(10000.toShort(), musicFrames)

        feedAndDrain(musicProcessor, silenceBuffer)
        feedAndDrain(musicProcessor, musicBuffer)

        // True silence lasting > 500ms MUST be skipped
        assertTrue(
            "Music-tuned processor must skip genuine silence (> 500ms, skipped: ${musicProcessor.skippedFrames})",
            musicProcessor.skippedFrames > 0
        )
    }
}
