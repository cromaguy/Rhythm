package chromahub.rhythm.app.infrastructure.audio

import android.content.Context
import android.media.MediaCodecList
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.ArrayList

/**
 * Custom RenderersFactory that creates audio renderers configured for bit-perfect playback
 * with optional Rhythm audio effects.
 * 
 * This factory creates MediaCodecAudioRenderer instances that use a custom AudioSink
 * capable of outputting audio at its native sample rate without resampling, and
 * applying Rhythm audio filters for bass boost and spatialization.
 */
@OptIn(UnstableApi::class)
class BitPerfectRenderersFactory(
    context: Context,
    private val enableBitPerfect: Boolean = false,
    private val bassBoostProcessor: RhythmBassBoostProcessor? = null,
    private val spatializationProcessor: RhythmSpatializationProcessor? = null
) : DefaultRenderersFactory(context) {

    private val hasOfficialDolbyEac3Decoder = hasOfficialDolbyEac3Decoder()
    
    companion object {
        private const val TAG = "BitPerfectFactory"
    }
    
    init {
        val effectsEnabled = (bassBoostProcessor != null) || (spatializationProcessor != null)
        Log.d(TAG, "Creating BitPerfectRenderersFactory (bit-perfect: $enableBitPerfect, Rhythm effects: $effectsEnabled)")
        
        // Prefer extension renderers when available if available for better format support
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)

        if (hasOfficialDolbyEac3Decoder) {
            Log.i(TAG, "Official Dolby EAC3 C2 decoder detected; platform decoder will be prioritized over FFmpeg")
        }
        
        if (enableBitPerfect) {
            Log.i(TAG, "Bit-perfect mode enabled - audio will output at native sample rate")
        }
        
        if (effectsEnabled) {
            Log.i(TAG, "Rhythm audio effects enabled (bass boost: ${bassBoostProcessor != null}, spatialization: ${spatializationProcessor != null})")
        }
    }
    
    /**
     * Override buildAudioRenderers to inject our custom AudioSink with Rhythm processors
     */
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        Log.d(TAG, "Building audio renderers for bit-perfect playback with Rhythm effects")
        
        // Create our custom audio sink with Rhythm processors
        val customAudioSink = BitPerfectAudioSink.create(
            context, 
            enableBitPerfect,
            bassBoostProcessor,
            spatializationProcessor
        )
        
        val extensionRendererIndex = out.size

        // Add extension renderers and remove default MediaCodec renderer.
        // A custom MediaCodec renderer will be inserted with conditional priority.
        if (extensionRendererMode != EXTENSION_RENDERER_MODE_OFF) {
            super.buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                customAudioSink,
                eventHandler,
                eventListener,
                out
            )
            
            // Log if extension renderers were added
            val extensionCount = out.size - extensionRendererIndex
            if (extensionCount > 0) {
                Log.d(TAG, "Extension audio renderers added: $extensionCount (includes FFmpeg for EAC3-JOC/Dolby Atmos support)")
                
                // Remove the standard MediaCodecAudioRenderer that super added,
                // since we'll add our own custom one with BitPerfectAudioSink
                val iterator = out.listIterator(extensionRendererIndex)
                while (iterator.hasNext()) {
                    val renderer = iterator.next()
                    if (renderer is MediaCodecAudioRenderer) {
                        iterator.remove()
                        Log.d(TAG, "Removed standard MediaCodecAudioRenderer, will add custom one")
                    }
                }
            }
        }
        
        // Add our custom MediaCodec audio renderer with conditional ordering.
        // Dolby-certified devices prioritize MediaCodec; others keep FFmpeg-first fallback behavior.
        val audioRenderer = MediaCodecAudioRenderer(
            context,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            customAudioSink
        )

        if (hasOfficialDolbyEac3Decoder && extensionRendererMode != EXTENSION_RENDERER_MODE_OFF) {
            out.add(extensionRendererIndex, audioRenderer)
            Log.d(TAG, "Prioritized MediaCodec renderer before FFmpeg extensions for Dolby EAC3/JOC")
        } else {
            out.add(audioRenderer)
        }

        Log.d(TAG, "Audio renderer configured: bit-perfect=$enableBitPerfect, Rhythm effects enabled")
    }

    private fun hasOfficialDolbyEac3Decoder(): Boolean {
        return try {
            val dolbyDecoderNames = setOf(
                "c2.dolby.eac3.decoder",
                "c2.dolby.eac3.decoder.ac3",
                "c2.dolby.eac3.decoder.eac3-joc"
            )

            val hasDecoder = MediaCodecList(MediaCodecList.ALL_CODECS)
                .codecInfos
                .asSequence()
                .filter { !it.isEncoder }
                .map { it.name.lowercase() }
                .any { it in dolbyDecoderNames }

            Log.d(TAG, "Official Dolby EAC3 decoder support: $hasDecoder")
            hasDecoder
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query codec list for Dolby support", e)
            false
        }
    }
}
