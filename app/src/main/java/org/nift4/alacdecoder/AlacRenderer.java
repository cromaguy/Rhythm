/*
 *     Copyright (C) 2025 nift4
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.nift4.alacdecoder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;

public class AlacRenderer extends DecoderAudioRenderer<AlacDecoder> {
    public AlacRenderer(Handler eventHandler, AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(eventHandler, eventListener, audioSink);
    }

    @Override
    protected int supportsFormatInternal(@NonNull Format format) {
        if (!MimeTypes.AUDIO_ALAC.equalsIgnoreCase(format.sampleMimeType)) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_DRM;
        }
        if (format.initializationData.isEmpty() || format.initializationData.get(0).length < 6) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE;
        }
        int bitDepth = format.initializationData.get(0)[5];
        int pcmEncoding = bitDepth == 20 ? C.ENCODING_PCM_24BIT : Util.getPcmEncoding(bitDepth);
        if (!sinkSupportsFormat(
                Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate))) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE;
        }
        return C.FORMAT_HANDLED;
    }

    @NonNull
    @Override
    protected AlacDecoder createDecoder(@NonNull Format format, CryptoConfig cryptoConfig) {
        try {
            Log.i("AlacDecoder", "Creating ALAC decoder: " +
                    format.initializationData.get(0)[5] + "bit " + format.sampleRate + "Hz " +
                    format.channelCount + "ch");
            return new AlacDecoder(format, 16, 16);
        } catch (AlacDecoderException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    protected Format getOutputFormat(@NonNull AlacDecoder decoder) {
        Format format = decoder.getInputFormat();
        int bitDepth = (format.initializationData.isEmpty() || format.initializationData.get(0).length < 6)
                ? 16
                : format.initializationData.get(0)[5];
        int pcmEncoding = bitDepth == 20 ? C.ENCODING_PCM_24BIT : Util.getPcmEncoding(bitDepth);
        Format outFormat = Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate);
        if (format.channelMask != Format.NO_VALUE) {
            outFormat = outFormat.buildUpon().setChannelMask(format.channelMask).build();
        } else if (outFormat.channelCount == 4) {
            // ALAC defines 4 channels as CHANNEL_OUT_SURROUND, Android as CHANNEL_OUT_QUAD.
            outFormat = outFormat.buildUpon().setChannelMask(AudioFormat.CHANNEL_OUT_SURROUND).build();
        }
        return outFormat;
    }

    @NonNull
    @Override
    public String getName() {
        return "AlacRenderer";
    }
}
