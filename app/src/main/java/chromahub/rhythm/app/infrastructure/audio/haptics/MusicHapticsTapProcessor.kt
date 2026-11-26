/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.haptics

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.infrastructure.audio.RhythmAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.dsp.RhythmDoubleDspPipeline
import java.nio.ByteBuffer

/**
 * Non-mutating analysis tap passing decoded PCM through unchanged via [RhythmAudioProcessor]'s
 * zero-math bypass copy while feeding [MusicHapticsController.analyzer] whenever Music Haptics
 * or beat reactivity is active.
 */
@OptIn(UnstableApi::class)
class MusicHapticsTapProcessor(
    private val controller: MusicHapticsController?
) : RhythmAudioProcessor() {

    override fun isEnabled(): Boolean = controller?.shouldAnalyze() == true

    /** Always bypass DSP output path in parent so audio bytes pass through bit-identical. */
    override fun isBypassed(): Boolean = true

    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        // Read-only tap: never modifies PCM samples
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val activeController = controller
        if (activeController != null && activeController.shouldAnalyze() && inputBuffer.hasRemaining()) {
            val dup = inputBuffer.duplicate().order(inputBuffer.order())
            val bps = RhythmDoubleDspPipeline.bytesPerSample(encoding)
            val count = dup.remaining() / bps
            if (count > 0) {
                val scratch = acquireDoubleArray(count)
                try {
                    val read = RhythmDoubleDspPipeline.readToDoubles(dup, encoding, scratch)
                    activeController.analyzer.analyzeInterleaved(scratch, read, channelCount, sampleRate)
                } finally {
                    releaseDoubleArray(scratch)
                }
            }
        }
        super.queueInput(inputBuffer)
    }

    override fun flush() {
        super.flush()
        controller?.analyzer?.reset()
    }
}
