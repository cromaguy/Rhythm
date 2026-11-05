package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.MimeTypes
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLoopAudioBoundsTest {
    @Test fun lowSampleRateFlacKeepsTheWholeBlockSpanningTheSplice() {
        val info = java.nio.ByteBuffer.allocate(42)
        info.put("fLaC".toByteArray()).putInt(0x80000022.toInt())
        info.putShort(4096).putShort(4096)
        repeat(6) { info.put(0) }
        info.putLong((8000L shl 44) or (15L shl 36))
        val format = androidx.media3.common.Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setSampleRate(8000).setInitializationData(listOf(info.array())).build()
        assertEquals(1_024_000L, AutoLoopAudioBounds.prerollUs(format))
    }

    @Test fun aacKeepsDecoderWarmupAndFlacWithoutStreamInfoUsesSafeFallback() {
        val aac = androidx.media3.common.Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        val flac = androidx.media3.common.Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC).build()
        assertEquals(250_000L, AutoLoopAudioBounds.prerollUs(aac))
        assertEquals(1_000_000L, AutoLoopAudioBounds.prerollUs(flac))
    }

    @Test fun aacInsideM4aUsesDecodedSampleTrimming() {
        assertTrue(AutoLoopAudioBounds.supports(MimeTypes.AUDIO_AAC))
    }

    @Test fun retainsMp3AndFlacSupportWithoutTreatingContainerTypesAsCodecs() {
        assertTrue(AutoLoopAudioBounds.supports(MimeTypes.AUDIO_MPEG))
        assertTrue(AutoLoopAudioBounds.supports(MimeTypes.AUDIO_FLAC))
        assertFalse(AutoLoopAudioBounds.supports(MimeTypes.AUDIO_RAW))
        assertFalse(AutoLoopAudioBounds.supports("audio/mp4"))
        assertFalse(AutoLoopAudioBounds.supports(null))
    }
}
