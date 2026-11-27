/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.usb

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainAudioProcessor
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainUtil
import kotlin.math.ln

/**
 * Applies hardware [DynamicsProcessing] limiter & positive ReplayGain headroom compensation on
 * Android 9+ (API 28+) when playing non-bit-perfect or hardware-offloaded streams, allowing quiet
 * classical/audiophile masters with positive ReplayGain tags to gain loudness without clipping.
 */
@OptIn(UnstableApi::class)
class PostAmpAudioOutputProvider(
    delegate: AudioOutputProvider,
    private val context: Context,
    private val replayGainProcessorProvider: () -> ReplayGainAudioProcessor?,
    private val isBitPerfectActive: () -> Boolean
) : ForwardingAudioOutputProvider(delegate) {

    companion object {
        private const val TAG = "PostAmpOutput"
        private const val MAX_BOOST_DB = 9.0f

        internal fun computePositivePostAmpDb(
            tags: ReplayGainUtil.ReplayGainInfo?,
            mode: ReplayGainUtil.Mode,
            rgPreampDb: Int,
            bitPerfect: Boolean
        ): Float {
            if (bitPerfect || mode == ReplayGainUtil.Mode.None || tags == null) return 0f
            val baseGain = when (mode) {
                ReplayGainUtil.Mode.Track -> tags.trackGain ?: tags.albumGain
                ReplayGainUtil.Mode.Album -> tags.albumGain ?: tags.trackGain
                ReplayGainUtil.Mode.None -> null
            } ?: return 0f
            val rawDb = baseGain + rgPreampDb
            if (rawDb <= 0f) return 0f
            val peak = when (mode) {
                ReplayGainUtil.Mode.Track -> tags.trackPeak ?: tags.albumPeak
                ReplayGainUtil.Mode.Album -> tags.albumPeak ?: tags.trackPeak
                ReplayGainUtil.Mode.None -> null
            }
            val safeCeilingDb = if (peak != null && peak > 0.0001f) {
                (-20.0 * ln(peak.toDouble()) / ln(10.0)).toFloat().coerceAtLeast(0f)
            } else {
                MAX_BOOST_DB
            }
            return minOf(rawDb, safeCeilingDb, MAX_BOOST_DB).coerceAtLeast(0f)
        }
    }

    private var dpe: DynamicsProcessing? = null
    private var dpeSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val output = super.getAudioOutput(config)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return output

        val sessionId = config.audioSessionId.takeIf { it != C.AUDIO_SESSION_ID_UNSET && it > 0 }
            ?: return output
        syncDynamicsProcessing(sessionId, config.isOffload)

        return object : ForwardingAudioOutput(output) {
            override fun play() {
                syncDynamicsProcessing(sessionId, config.isOffload)
                super.play()
            }

            override fun flush() {
                syncDynamicsProcessing(sessionId, config.isOffload)
                super.flush()
            }

            override fun release() {
                super.release()
            }
        }
    }

    override fun release() {
        releaseDpe()
        super.release()
    }

    private fun syncDynamicsProcessing(sessionId: Int, isOffload: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val rgAp = replayGainProcessorProvider()
        val boostDb = if (rgAp != null && (isOffload || rgAp.targetGain > 1.005f)) {
            computePositivePostAmpDb(
                tags = rgAp.getTags(),
                mode = rgAp.mode,
                rgPreampDb = rgAp.rgGain,
                bitPerfect = isBitPerfectActive()
            )
        } else 0f

        if (boostDb <= 0.05f || isBitPerfectActive()) {
            if (dpe != null && dpeSessionId == sessionId) {
                runCatching { dpe?.enabled = false }
            }
            return
        }

        try {
            if (dpe == null || dpeSessionId != sessionId) {
                releaseDpe()
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    1,
                    false,
                    0,
                    false,
                    0,
                    false,
                    0,
                    true
                ).apply {
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        ReplayGainUtil.TAU_ATTACK * 1000f,
                        ReplayGainUtil.TAU_RELEASE * 1000f,
                        ReplayGainUtil.RATIO,
                        -0.2f,
                        0f
                    )
                    setLimiterAllChannelsTo(limiter)
                    setInputGainAllChannelsTo(boostDb)
                }.build()
                dpe = DynamicsProcessing(0, sessionId, cfg).apply {
                    enabled = true
                }
                dpeSessionId = sessionId
            } else {
                dpe?.setInputGainAllChannelsTo(boostDb)
                dpe?.enabled = true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DynamicsProcessing unavailable on session $sessionId", t)
            releaseDpe()
        }
    }

    private fun releaseDpe() {
        runCatching { dpe?.release() }
        dpe = null
        dpeSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
