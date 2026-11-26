/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import chromahub.rhythm.app.infrastructure.audio.RhythmMonoAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.SignalPathEvaluator
import chromahub.rhythm.app.infrastructure.audio.SignalPathInput
import chromahub.rhythm.app.infrastructure.audio.StreamHealthTracker
import chromahub.rhythm.app.infrastructure.audio.codec.AiffMedia3Extractor
import chromahub.rhythm.app.infrastructure.audio.dsp.SampleRateCapAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.dsp.SurroundDownmixAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.usb.MixerFormat
import chromahub.rhythm.app.infrastructure.audio.usb.selectBitPerfectMixerFormat
import chromahub.rhythm.app.infrastructure.service.player.AudioDecoderPolicy
import chromahub.rhythm.app.infrastructure.service.player.SafeSilenceSkippingAudioProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@androidx.media3.common.util.UnstableApi
class AudioOutputAndCodecTest {

    @Test
    fun selectBitPerfectMixerFormatMatchesOrWidensLosslessly() {
        val offered = listOf(
            MixerFormat(C.ENCODING_PCM_16BIT, 44100, 2, bitPerfect = false),
            MixerFormat(C.ENCODING_PCM_24BIT, 96000, 2, bitPerfect = true),
            MixerFormat(C.ENCODING_PCM_32BIT, 96000, 2, bitPerfect = true)
        )
        // Float32 at 96kHz should pick 24-bit integer bit-perfect mixer before 32-bit
        assertEquals(1, selectBitPerfectMixerFormat(offered, 96000, 2, C.ENCODING_PCM_FLOAT))
        assertEquals(-1, selectBitPerfectMixerFormat(offered, 48000, 2, C.ENCODING_PCM_16BIT))
    }

    @Test
    fun surroundDownmixReduces51ChannelAudioToStereoWithCenterWeight() {
        val processor = SurroundDownmixAudioProcessor().apply { setEnabled(true) }
        val inFmt = AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_FLOAT)
        val outFmt = processor.configure(inFmt)
        assertEquals(2, outFmt.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, outFmt.encoding)
        assertTrue(processor.isActive())
        processor.flush()

        // 1 frame: FL=0.8, FR=0.2, FC=0.5, LFE=0.1, Ls=0.0, Rs=0.4
        val buf = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        buf.putFloat(0.8f).putFloat(0.2f).putFloat(0.5f).putFloat(0.1f).putFloat(0.0f).putFloat(0.4f)
        buf.flip()

        processor.queueInput(buf)
        val out = processor.getOutput()
        assertEquals(8, out.remaining()) // 2 stereo float samples
        val left = out.getFloat()
        val right = out.getFloat()
        assertEquals((0.8 + 0.70710678 * 0.5) * 0.5, left.toDouble(), 1e-4)
        assertEquals((0.2 + 0.70710678 * (0.5 + 0.4)) * 0.5, right.toDouble(), 1e-4)

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)))
    }

    @Test
    fun sampleRateCapResamples96kTo48kSmoothly() {
        val capProc = SampleRateCapAudioProcessor().apply { setMaxSampleRateHz(48_000) }
        val inFmt = AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_16BIT)
        val outFmt = capProc.configure(inFmt)
        assertEquals(48_000, outFmt.sampleRate)
        assertTrue(capProc.isActive())
        capProc.flush()

        val inFrames = 960
        val buf = ByteBuffer.allocateDirect(inFrames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until inFrames) {
            val s = (sin(2.0 * PI * 1000.0 * i / 96_000.0) * 16000.0).toInt().toShort()
            buf.putShort(s).putShort(s)
        }
        buf.flip()
        capProc.queueInput(buf)
        val out = capProc.getOutput()
        val outFrames = out.remaining() / 4
        assertTrue("Expected ~480 frames, got $outFrames", abs(outFrames - 480) <= 2)

        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            capProc.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        )
    }

    @Test
    fun aiffExtractorSniffsAndDecodesBigEndianPcm() {
        val pcmSamples = shortArrayOf(1000, -1000, 2000, -2000)
        val ssndPayload = 8 + pcmSamples.size * 2
        val commSize = 18
        val formPayloadSize = 4 + (8 + commSize) + (8 + ssndPayload)
        val buf = ByteBuffer.allocate(8 + formPayloadSize).order(ByteOrder.BIG_ENDIAN)
        buf.put("FORM".toByteArray(Charsets.US_ASCII))
        buf.putInt(formPayloadSize)
        buf.put("AIFF".toByteArray(Charsets.US_ASCII))
        buf.put("COMM".toByteArray(Charsets.US_ASCII))
        buf.putInt(commSize)
        buf.putShort(2)
        buf.putInt(2)
        buf.putShort(16)
        // 44100 Hz IEEE-80 extended: exponent=0x400E, mantissa=0xAC440000_00000000
        buf.putShort(0x400E)
        buf.putInt(0xAC440000.toInt())
        buf.putInt(0)
        buf.put("SSND".toByteArray(Charsets.US_ASCII))
        buf.putInt(ssndPayload)
        buf.putInt(0)
        buf.putInt(0)
        for (s in pcmSamples) buf.putShort(s)

        val extractor = AiffMedia3Extractor()
        val input = InMemoryExtractorInput(buf.array())
        assertTrue(extractor.sniff(input))
        input.resetPeekPosition()

        var emittedFormat: androidx.media3.common.Format? = null
        val sampleOut = ByteBuffer.allocate(pcmSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = object : TrackOutput {
                override fun format(format: androidx.media3.common.Format) { emittedFormat = format }
                override fun sampleData(input: androidx.media3.common.DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int = 0
                override fun sampleData(data: androidx.media3.common.util.ParsableByteArray, length: Int, sampleDataPart: Int) {
                    sampleOut.put(data.data, data.position, length)
                }
                override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {}
            }
            override fun endTracks() {}
            override fun seekMap(seekMap: SeekMap) {}
        })

        val posHolder = PositionHolder()
        while (extractor.read(input, posHolder) == Extractor.RESULT_CONTINUE) {
            // drain
        }
        assertEquals(44100, emittedFormat?.sampleRate)
        assertEquals(2, emittedFormat?.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, emittedFormat?.pcmEncoding)
        sampleOut.flip()
        assertEquals(pcmSamples[0], sampleOut.getShort())
        assertEquals(pcmSamples[1], sampleOut.getShort())
    }

    @Test
    fun signalPathEvaluatorDistinguishesBitPerfectFromProcessed() {
        val bitPerfectReport = SignalPathEvaluator.evaluate(
            SignalPathInput(
                sourceCodec = "FLAC",
                sourceSampleRateHz = 96_000,
                sourceBitDepth = 24,
                sourceChannels = 2,
                isLosslessSource = true,
                outputSampleRateHz = 96_000,
                outputBitDepth = 24,
                outputDeviceName = "USB DAC",
                isUsbDevice = true,
                bitPerfectMixerActive = true,
                bitPerfectRequested = true,
                hasActiveEq = false,
                hasActiveViper = false,
                hasActiveOtherDsp = false,
                playbackSpeed = 1.0f,
                sampleRateCapHz = 0,
                surroundDownmixActive = false,
                floatPipelineActive = false
            )
        )
        assertTrue(bitPerfectReport.isBitPerfect)
        assertEquals(100, bitPerfectReport.fidelityScore)

        val tracker = StreamHealthTracker()
        tracker.sample(0, 1000, true)
        val ppm = tracker.sample(1000, 2000, true)
        assertEquals(0.0, ppm ?: 1.0, 0.1)
    }

    @Test
    fun audioDecoderPolicy_prefersSoftwareForLosslessFloatAndFiltersDisabled() {
        val candidates = listOf("c2.qti.flac.decoder", "c2.android.flac.decoder", "omx.google.raw")
        val prioritized = AudioDecoderPolicy.prioritizeDecoders(
            decoders = candidates,
            nameOf = { it },
            isHardwareOf = { it.startsWith("c2.qti.") },
            disabledDecoders = setOf("omx.google.raw"),
            preferHardware = true,
            floatOutputActive = true,
            mimeType = androidx.media3.common.MimeTypes.AUDIO_FLAC
        )
        assertEquals(listOf("c2.android.flac.decoder", "c2.qti.flac.decoder"), prioritized)
    }

    private class InMemoryExtractorInput(private val data: ByteArray) : ExtractorInput {
        private var pos = 0
        private var peekPos = 0

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= data.size) return C.RESULT_END_OF_INPUT
            val toRead = minOf(length, data.size - pos)
            System.arraycopy(data, pos, buffer, offset, toRead)
            pos += toRead
            peekPos = pos
            return toRead
        }
        override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            if (pos + length > data.size) return false
            System.arraycopy(data, pos, target, offset, length)
            pos += length
            peekPos = pos
            return true
        }
        override fun readFully(target: ByteArray, offset: Int, length: Int) { readFully(target, offset, length, false) }
        override fun skip(length: Int): Int { val s = minOf(length, data.size - pos); pos += s; return s }
        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean { pos += length; return true }
        override fun skipFully(length: Int) { pos += length }
        override fun peek(target: ByteArray, offset: Int, length: Int): Int {
            val n = minOf(length, data.size - peekPos); System.arraycopy(data, peekPos, target, offset, n); peekPos += n; return n
        }
        override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            if (peekPos + length > data.size) return false
            System.arraycopy(data, peekPos, target, offset, length)
            peekPos += length
            return true
        }
        override fun peekFully(target: ByteArray, offset: Int, length: Int) { peekFully(target, offset, length, false) }
        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean { peekPos += length; return true }
        override fun advancePeekPosition(length: Int) { peekPos += length }
        override fun resetPeekPosition() { peekPos = pos }
        override fun getPeekPosition(): Long = peekPos.toLong()
        override fun getPosition(): Long = pos.toLong()
        override fun getLength(): Long = data.size.toLong()
        override fun <E : Throwable?> setRetryPosition(position: Long, e: E) { pos = position.toInt() }
    }

    @Test
    fun safeSilenceSkippingAndRhythmProcessors_supportMedia3FlushOverloadsAcrossFormatSwitches() {
        val silenceDelegate = SilenceSkippingAudioProcessor().apply { setEnabled(true) }
        val safeSilence = SafeSilenceSkippingAudioProcessor(silenceDelegate)
        val mono = RhythmMonoAudioProcessor()
        val pipeline = AudioProcessingPipeline(ImmutableList.of(safeSilence, mono))

        val pcm16 = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        pipeline.configure(pcm16)
        pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertTrue(safeSilence.isActive())
        assertTrue(pipeline.isOperational())

        val pcmFloat = AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_FLOAT)
        pipeline.configure(pcmFloat)
        pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertFalse(safeSilence.isActive())
        assertTrue(mono.isActive())
    }
}
