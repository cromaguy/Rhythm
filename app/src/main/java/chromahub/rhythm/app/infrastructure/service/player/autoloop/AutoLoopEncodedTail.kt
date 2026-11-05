package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/** Decoder-only look-ahead. Rendered PCM and the visible timeline still end at the loop point. */
internal object AutoLoopEncodedTail {
    fun inputEndUs(format: Format, endUs: Long): Long {
        if (format.sampleMimeType != MimeTypes.AUDIO_MPEG || format.sampleRate <= 0) return endUs
        // Two MPEG-1 Layer III frames also cover lower-version MP3s and the decoder's
        // synthesis delay. Without them the final decoded block may be padded with silence.
        return endUs + (2304_000_000L + format.sampleRate - 1) / format.sampleRate
    }
}
