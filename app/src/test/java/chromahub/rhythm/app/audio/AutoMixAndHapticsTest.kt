/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import chromahub.rhythm.app.infrastructure.audio.haptics.HapticPulseListener
import chromahub.rhythm.app.infrastructure.audio.haptics.MusicHapticsAnalyzer
import chromahub.rhythm.app.infrastructure.service.player.automix.AutoMixCamelotClassifier
import chromahub.rhythm.app.infrastructure.service.player.automix.DeckEqAudioProcessor
import chromahub.rhythm.app.infrastructure.service.player.automix.EndingStyle
import chromahub.rhythm.app.infrastructure.service.player.automix.HarmonicCompatibilityTier
import chromahub.rhythm.app.infrastructure.service.player.automix.SmartMixInput
import chromahub.rhythm.app.infrastructure.service.player.automix.SmartTransitionPlanner
import chromahub.rhythm.app.infrastructure.service.player.automix.TagBpmReader
import chromahub.rhythm.app.infrastructure.service.player.automix.TrackAnalyzer
import chromahub.rhythm.app.shared.data.model.Curve
import chromahub.rhythm.app.shared.data.model.TransitionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

@androidx.media3.common.util.UnstableApi
class AutoMixAndHapticsTest {

    @Test
    fun camelotClassifierNormalizesKeysAndScoresCompatibility() {
        val k1 = AutoMixCamelotClassifier.parseToCamelotKey("A minor")
        val k2 = AutoMixCamelotClassifier.parseToCamelotKey("C Major")
        val k3 = AutoMixCamelotClassifier.parseToCamelotKey("E minor")
        val k4 = AutoMixCamelotClassifier.parseToCamelotKey("F# minor")

        assertEquals("8A", k1.code)
        assertEquals("8B", k2.code)
        assertEquals("9A", k3.code)
        assertEquals("11A", k4.code)

        assertEquals(HarmonicCompatibilityTier.PERFECT_LOCK, AutoMixCamelotClassifier.evaluateCompatibility(k1, k2).tier)
        assertEquals(HarmonicCompatibilityTier.PERFECT_LOCK, AutoMixCamelotClassifier.evaluateCompatibility(k1, k3).tier)
        assertEquals(HarmonicCompatibilityTier.DISSONANT, AutoMixCamelotClassifier.evaluateCompatibility(k1, k4).tier)
    }

    @Test
    fun tagBpmReaderExtractsId3AndVorbisMetadata() {
        // ID3v2.3 header with TBPM=124 and TKEY=8A
        val tbpmBody = "\u0000124".toByteArray(Charsets.ISO_8859_1)
        val tkeyBody = "\u00008A".toByteArray(Charsets.ISO_8859_1)
        val totalTagBody = 10 + tbpmBody.size + 10 + tkeyBody.size
        val id3 = ByteBuffer.allocate(10 + totalTagBody).order(ByteOrder.BIG_ENDIAN)
        id3.put("ID3".toByteArray(Charsets.US_ASCII))
        id3.put(3.toByte()).put(0.toByte()).put(0.toByte())
        // syncsafe integer totalTagBody
        id3.put(0.toByte()).put(0.toByte()).put(0.toByte()).put(totalTagBody.toByte())
        id3.put("TBPM".toByteArray(Charsets.US_ASCII)).putInt(tbpmBody.size).putShort(0).put(tbpmBody)
        id3.put("TKEY".toByteArray(Charsets.US_ASCII)).putInt(tkeyBody.size).putShort(0).put(tkeyBody)

        val parsedId3 = TagBpmReader.inspectBytes(id3.array())
        assertEquals(124f, parsedId3.bpm ?: 0f, 0.1f)
        assertEquals("8A", parsedId3.initialKey)

        // Vorbis comment snippet
        val vorbisBytes = "ENCODER=flac\u0000BPM=128.5\u0000INITIALKEY=Am\u0000".toByteArray(Charsets.ISO_8859_1)
        val parsedVorbis = TagBpmReader.inspectBytes(vorbisBytes)
        assertEquals(128.5f, parsedVorbis.bpm ?: 0f, 0.1f)
        assertEquals("Am", parsedVorbis.initialKey)
    }

    @Test
    fun trackAnalyzerRecoversSynthetic120BpmPulseAndFadeOut() {
        val sr = 22_050
        val seconds = 12
        val samples = FloatArray(sr * seconds)
        // Beat every 0.500 s = 120 BPM
        val beatSamples = sr / 2
        for (i in samples.indices) {
            val posInBeat = i % beatSamples
            val env = exp(-posInBeat.toDouble() / (sr * 0.04))
            val kick = (sin(2.0 * PI * 75.0 * i / sr) * env * 0.85).toFloat()
            val fade = if (i > sr * 8) ((sr * 12 - i).toFloat() / (sr * 4)).coerceIn(0.01f, 1f) else 1f
            samples[i] = kick * fade
        }

        val analysis = TrackAnalyzer.analyzeMonoPcm(samples, sr, windowStartMs = 0L)
        val detectedBpm = analysis.grid?.bpm ?: 0f
        assertTrue("Expected ~120 BPM, got $detectedBpm", abs(detectedBpm - 120f) < 3.5f)
        assertEquals(EndingStyle.FADE_OUT, analysis.ending)
    }

    @Test
    fun smartTransitionPlannerHonorsSameAlbumGaplessAndSafeBpmStretch() {
        val gapless = SmartTransitionPlanner.plan(
            SmartMixInput(
                baseDurationMs = 6000,
                baseMode = TransitionMode.OVERLAP,
                curveIn = Curve.EQUAL_POWER,
                curveOut = Curve.EQUAL_POWER,
                outgoingDurationMs = 200_000L,
                incomingDurationMs = 200_000L,
                sameAlbumSequence = true
            )
        )
        assertEquals(TransitionMode.NONE, gapless.settings.mode)

        val matched = SmartTransitionPlanner.plan(
            SmartMixInput(
                baseDurationMs = 6000,
                baseMode = TransitionMode.OVERLAP,
                curveIn = Curve.EQUAL_POWER,
                curveOut = Curve.EQUAL_POWER,
                outgoingDurationMs = 200_000L,
                incomingDurationMs = 200_000L,
                outgoingBpm = 124f,
                incomingBpm = 126f,
                outgoingKey = "8A",
                incomingKey = "9A",
                bpmSyncEnabled = true
            )
        )
        assertEquals(124f / 126f, matched.tempoRatio, 0.005f)
        assertTrue(matched.useBassSwap)
        assertEquals(Curve.S_CURVE, matched.settings.curveIn)
        assertEquals(7500, matched.settings.durationMs) // 1.25x extended for PERFECT_LOCK

        // > 6% difference falls back safely to 1.0f
        assertEquals(1.0f, SmartTransitionPlanner.findSafeTempoRatio(120f, 140f), 0.001f)
    }

    @Test
    fun deckEqBassCutAttenuatesSubBassAndPassesBypassUnmodified() {
        val deckEq = DeckEqAudioProcessor()
        deckEq.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        deckEq.setBassCut(true)

        val frames = 9600
        val subBass = DoubleArray(frames * 2)
        val highFreq = DoubleArray(frames * 2)
        for (i in 0 until frames) {
            val low = 0.6 * sin(2.0 * PI * 50.0 * i / 48_000.0)
            val hi = 0.6 * sin(2.0 * PI * 4000.0 * i / 48_000.0)
            subBass[i * 2] = low; subBass[i * 2 + 1] = low
            highFreq[i * 2] = hi; highFreq[i * 2 + 1] = hi
        }
        deckEq.processDoubleSamples(subBass, subBass.size)
        deckEq.flush()
        deckEq.processDoubleSamples(highFreq, highFreq.size)

        val tailStart = (frames * 3 / 4) * 2
        val tailSampleCount = frames * 2 - tailStart
        var rmsLow = 0.0
        var rmsHi = 0.0
        for (i in tailStart until frames * 2) {
            rmsLow += subBass[i] * subBass[i]
            rmsHi += highFreq[i] * highFreq[i]
        }
        rmsLow = sqrt(rmsLow / tailSampleCount)
        rmsHi = sqrt(rmsHi / tailSampleCount)

        assertTrue("50 Hz sub-bass should be heavily attenuated ($rmsLow vs $rmsHi)", rmsLow < rmsHi * 0.25)

        deckEq.resetCutImmediately()
        assertFalse(deckEq.isEnabled())
        assertTrue(deckEq.isBypassed())
    }

    @Test
    fun musicHapticsAnalyzerEmitsKickPulsesOnPeriodicSubBassHits() {
        var kickCount = 0
        var maxRhythmLevel = 0f
        val analyzer = MusicHapticsAnalyzer(object : HapticPulseListener {
            override fun onHapticPulse(bassEnergy: Float, vocalEnergy: Float, isKick: Boolean, isSharpTransient: Boolean) {
                if (isKick) kickCount++
            }

            override fun onRhythmLevel(level: Float) {
                if (level > maxRhythmLevel) maxRhythmLevel = level
            }
        })

        val sr = 48_000
        val totalFrames = sr * 2
        val stereo = DoubleArray(totalFrames * 2)
        // Generate 3 distinct kick hits at 0.2s, 0.8s, 1.4s
        val kickCenters = intArrayOf((0.2 * sr).toInt(), (0.8 * sr).toInt(), (1.4 * sr).toInt())
        for (c in kickCenters) {
            for (k in 0 until 6000) {
                if (c + k < totalFrames) {
                    val env = exp(-k.toDouble() / 900.0)
                    val s = 0.85 * env * sin(2.0 * PI * 75.0 * k / sr)
                    stereo[(c + k) * 2] = s
                    stereo[(c + k) * 2 + 1] = s
                }
            }
        }

        analyzer.analyzeInterleaved(stereo, stereo.size, 2, sr)
        assertTrue("Expected at least 2 kick haptic pulses, got $kickCount", kickCount >= 2)
        assertTrue("Expected noticeable rhythm envelope level, got $maxRhythmLevel", maxRhythmLevel > 0.3f)
    }
}
