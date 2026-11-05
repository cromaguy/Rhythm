package chromahub.rhythm.app.infrastructure.service.player.autoloop

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.*

class LoopPointDetectorTest {
    @Test fun silenceHasNoLoop() = runBlocking {
        assertNull(LoopPointDetector.find(FloatArray(80000), 80000))
    }

    private fun music(): FloatArray {
        val notes = doubleArrayOf(261.63, 329.63, 392.0, 293.66, 349.23, 440.0, 329.63, 392.0)
        return FloatArray(4000 * 24) { i ->
            val time = i / 4000.0
            val frequency = notes[time.toInt() % notes.size]
            // Different phase and gain on each repetition: raw waveform correlation fails.
            val phase = (time / 8).toInt() * 1.9
            val gain = 0.20 + 0.015 * sin(time * 0.7)
            (gain * (sin(2 * PI * frequency * time + phase) +
                0.3 * sin(4 * PI * frequency * time + phase))).toFloat()
        }
    }

    @Test fun matchesMusicDespitePhaseAndGainChanges() = runBlocking {
        val audio = music()
        val loop = requireNotNull(LoopPointDetector.find(audio, audio.size))
        assertTrue(loop.first >= 0)
        assertTrue(loop.second < 24000)
        val length = loop.second - loop.first
        assertTrue("Expected an eight-second musical period, got $length",
            min(abs(length - 8000), abs(length - 16000)) < 100)
    }

    @Test fun unrelatedNoiseHasNoLoop() = runBlocking {
        val random = Random(17)
        val audio = FloatArray(48000) { random.nextFloat() - 0.5f }
        assertNull(LoopPointDetector.find(audio, audio.size))
    }

    @Test fun removesTwoHundredMillisecondOverlapAtSplice() = runBlocking {
        val random = Random(113)
        val period = 24549 // Deliberately not on the 200 ms grid or four-sample stride.
        val passage = FloatArray(period) { random.nextFloat() - 0.5f }
        val audio = FloatArray(period * 3) { passage[it % period] }
        val start = 8000
        val coarseEnd = start + period + 800 // 200 ms too late.
        val refined = LoopSpliceRefiner.refine(audio, audio.size, start, coarseEnd)
        assertEquals(start, refined.first)
        assertEquals("The repeated 200 ms must not remain in the loop", period, refined.second - refined.first)
    }

    @Test fun doesNotApplyAnArbitraryOffsetToUnrelatedAudio() = runBlocking {
        val random = Random(12)
        val audio = FloatArray(80000) { random.nextFloat() - 0.5f }
        assertEquals(8000 to 48000, LoopSpliceRefiner.refine(audio, audio.size, 8000, 48000))
    }

    @Test fun findsLoopAfterUnrelatedIntro() = runBlocking {
        val random = Random(93)
        val intro = FloatArray(4000 * 16) { (random.nextFloat() - 0.5f) * 0.4f }
        val audio = intro + music()
        val loop = requireNotNull(LoopPointDetector.find(audio, audio.size))
        assertTrue("Loop should begin after the unrelated intro", loop.first >= 16000)
        assertTrue(loop.second > loop.first)
    }
}
