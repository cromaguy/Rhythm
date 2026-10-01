/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.replaygain

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.media3.common.util.UnstableApi
class ReplayGainAudioProcessorTest {

    private lateinit var processor: ReplayGainAudioProcessor

    @Before
    fun setUp() {
        processor = ReplayGainAudioProcessor()
        ReplayGainCache.clear()
    }

    @After
    fun tearDown() {
        ReplayGainCache.clear()
    }

    private fun createStereo16BitFormat(sampleRate: Int = 44100): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    private fun createByteBufferWithShorts(vararg values: Short): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder())
        for (v in values) {
            buffer.putShort(v)
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun replayGainCache_putGetAndClear() {
        val tags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -5.0f,
            trackPeak = 0.95f,
            albumGain = -3.5f,
            albumPeak = 0.85f
        )

        assertNull(ReplayGainCache.get("track_1"))

        ReplayGainCache.put("track_1", tags)
        val retrieved = ReplayGainCache.get("track_1")
        assertNotNull(retrieved)
        assertEquals(-5.0f, retrieved?.trackGain)
        assertEquals(0.95f, retrieved?.trackPeak)
        assertEquals(-3.5f, retrieved?.albumGain)
        assertEquals(0.85f, retrieved?.albumPeak)

        ReplayGainCache.clear()
        assertNull(ReplayGainCache.get("track_1"))
    }

    @Test
    fun setTags_immediatelyUpdatesTargetGain() {
        // Configure processor with stereo 16-bit format
        val format = createStereo16BitFormat()
        processor.configure(format)

        // Set mode to Track
        processor.setMode(ReplayGainUtil.Mode.Track)

        // Default untagged gain is 1.0f (0 dB)
        assertEquals(1.0f, processor.targetGain, 0.001f)

        // Setting tags with -6 dB track gain
        val tags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -6.0f,
            trackPeak = null,
            albumGain = null,
            albumPeak = null
        )
        processor.setTags(tags)

        // Target gain should immediately reflect -6 dB (~0.501187)
        val expectedGain = ReplayGainUtil.dbToAmpl(-6.0f)
        assertEquals(expectedGain, processor.targetGain, 0.001f)
        assertEquals(processor.targetGain, processor.gain, 0.001f)
    }

    @Test
    fun flush_snapsCurrentGainToTargetGain_preventingJumpScare() {
        val format = createStereo16BitFormat()
        processor.configure(format)
        processor.setMode(ReplayGainUtil.Mode.Track)

        // Set tags with -10 dB track gain
        val tags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -10.0f,
            trackPeak = null,
            albumGain = null,
            albumPeak = null
        )
        processor.setTags(tags)

        val expectedGain = ReplayGainUtil.dbToAmpl(-10.0f)
        assertEquals(expectedGain, processor.targetGain, 0.001f)

        // Call flush (as ExoPlayer does when starting a new track or seeking)
        processor.flush()

        // currentGain MUST snap immediately to targetGain
        assertEquals(expectedGain, processor.currentGain, 0.001f)
    }

    @Test
    fun onFlush_withStreamMetadata_resolvesFromReplayGainCache() {
        val format = createStereo16BitFormat()
        processor.configure(format)
        processor.setMode(ReplayGainUtil.Mode.Track)

        // Populate cache for "song_123"
        val cachedTags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -8.0f,
            trackPeak = 0.9f,
            albumGain = null,
            albumPeak = null
        )
        ReplayGainCache.put("song_123", cachedTags)

        // Create a Timeline containing the mediaItem with id "song_123"
        val mediaItem = MediaItem.Builder().setMediaId("song_123").build()
        val periodUid = "period_0"
        val timeline = object : Timeline() {
            override fun getWindowCount(): Int = 1
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                return window.set(
                    Window.SINGLE_WINDOW_UID,
                    mediaItem,
                    null,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    true,
                    false,
                    null,
                    0L,
                    C.TIME_UNSET,
                    0,
                    0,
                    0L
                )
            }
            override fun getPeriodCount(): Int = 1
            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return period.set(periodUid, periodUid, 0, C.TIME_UNSET, 0L)
            }
            override fun getIndexOfPeriod(uid: Any): Int {
                return if (uid == periodUid) 0 else C.INDEX_UNSET
            }
            override fun getUidOfPeriod(periodIndex: Int): Any = periodUid
        }

        val streamMetadata = AudioProcessor.StreamMetadata.Builder()
            .setTimeline(timeline)
            .setPeriodUid(periodUid)
            .build()
        processor.flush(streamMetadata)

        val expectedGain = ReplayGainUtil.dbToAmpl(-8.0f)
        assertEquals(expectedGain, processor.targetGain, 0.001f)
        assertEquals(expectedGain, processor.currentGain, 0.001f)
    }

    @Test
    fun queueInput_whenCurrentGainEqualsTargetGain_appliesConstantGain() {
        val format = createStereo16BitFormat()
        processor.configure(format)
        processor.setMode(ReplayGainUtil.Mode.Track)

        val tags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -6.0206f, // ~0.5 linear gain
            trackPeak = null,
            albumGain = null,
            albumPeak = null
        )
        processor.setTags(tags)
        processor.flush() // Snaps currentGain to targetGain (~0.5)

        // Input 2 stereo frames: (10000, 20000), (-10000, -20000)
        val input = createByteBufferWithShorts(10000, 20000, -10000, -20000)
        processor.queueInput(input)

        val output = processor.output
        val shortBuffer = output.asShortBuffer()
        assertEquals(4, shortBuffer.remaining())

        val s0 = shortBuffer.get()
        val s1 = shortBuffer.get()
        val s2 = shortBuffer.get()
        val s3 = shortBuffer.get()

        // 10000 * 0.5 = ~5000
        assertEquals(5000f, s0.toFloat(), 50f)
        assertEquals(10000f, s1.toFloat(), 50f)
        assertEquals(-5000f, s2.toFloat(), 50f)
        assertEquals(-10000f, s3.toFloat(), 50f)
    }

    @Test
    fun queueInput_whenTargetGainDiffersFromCurrentGain_smoothlyRampsGain() {
        val sampleRate = 44100
        val format = createStereo16BitFormat(sampleRate)
        processor.configure(format)
        processor.setMode(ReplayGainUtil.Mode.Track)

        // Set initial track tags with -3 dB (~0.707 gain) and flush
        val initialTags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -3.0f,
            trackPeak = null,
            albumGain = null,
            albumPeak = null
        )
        processor.setTags(initialTags)
        processor.flush()
        val initialGain = ReplayGainUtil.dbToAmpl(-3.0f)
        assertEquals(initialGain, processor.currentGain, 0.001f)

        // Now during playback, gain target changes to -12 dB (~0.25 gain) without flushing
        val updatedTags = ReplayGainUtil.ReplayGainInfo(
            trackGain = -12.0f,
            trackPeak = null,
            albumGain = null,
            albumPeak = null
        )
        processor.setTags(updatedTags)
        val newTargetGain = ReplayGainUtil.dbToAmpl(-12.0f)
        assertEquals(newTargetGain, processor.targetGain, 0.001f)
        // currentGain should still be initialGain (not snapped, because currentGain != 1f)
        assertEquals(initialGain, processor.currentGain, 0.001f)

        // Process audio frames with constant amplitude 10000
        val frameCount = 1000
        val shorts = ShortArray(frameCount * 2) { 10000.toShort() }
        val input = createByteBufferWithShorts(*shorts)

        processor.queueInput(input)

        val output = processor.output
        val outShorts = output.asShortBuffer()
        val firstFrameVal = outShorts.get() // First sample
        outShorts.position(outShorts.limit() - 1)
        val lastFrameVal = outShorts.get()

        // First frame should be close to 10000 * initialGain (~7079)
        // Last frame should be ramped down close to 10000 * newTargetGain (~2512)
        assertTrue("First frame ($firstFrameVal) should be greater than last frame ($lastFrameVal)",
            firstFrameVal > lastFrameVal)
        assertEquals(newTargetGain, processor.currentGain, 0.01f)
    }
}
