// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlin.math.*

@Keep
data class AmbientSound(val at: Long, val rmsDbfs: Double, val peakDbfs: Double, val clippedFraction: Double,
    val samples: Int, val source: Int, val musicPlaying: Boolean)

/** Foreground-only opt-in sampling; PCM stays in memory and is never journalled or uploaded. */
class ListeningSound private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("listening_context", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var visible = false
    @Volatile private var latest: AmbientSound? = null
    @Volatile private var recorder: AudioRecord? = null
    private var job: Job? = null
    val enabled get() = prefs.getBoolean("ambient_sound", false)
    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun configure(value: Boolean) { prefs.edit(commit = true) { putBoolean("ambient_sound", value) }; if (!value) latest = null; restart(); ListeningLedger.get(context).invalidate() }
    fun visible(value: Boolean) { visible = value; restart() }
    @Synchronized private fun restart() {
        job?.cancel(); runCatching { recorder?.stop() }
        if (!visible || !enabled || !permitted()) return
        job = scope.launch {
            while (isActive && visible && enabled && permitted()) {
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@launch
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if (!audio.isMicrophoneMute) {
                        val source = if (audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
                        val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        check(size > 0)
                        val input = AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(size, 32_000))
                        recorder = input
                        try {
                            check(input.state == AudioRecord.STATE_INITIALIZED); input.startRecording()
                            val pcm = ShortArray(16_000); var count = 0
                            while (count < pcm.size && isActive && visible && enabled) {
                                val read = input.read(pcm, count, pcm.size - count); if (read <= 0) break; count += read
                            }
                            if (count > 0 && isActive && visible && enabled && permitted()) {
                                latest = summarize(pcm, count, System.currentTimeMillis(), source, audio.isMusicActive)
                                ListeningLedger.get(context).invalidate()
                            }
                        } finally { runCatching { input.stop() }; input.release(); if (recorder === input) recorder = null }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: SecurityException) { latest = null }
                catch (_: Exception) { latest = null }
                delay(60_000)
            }
        }
    }
    fun snapshot(now: Long): AmbientSound? = latest?.takeIf { enabled && permitted() && now - it.at in 0..3 * 60_000L }
    companion object {
        @Volatile private var instance: ListeningSound? = null
        fun get(context: Context): ListeningSound = instance ?: synchronized(this) { instance ?: ListeningSound(context.applicationContext).also { instance = it } }
        fun summarize(pcm: ShortArray, count: Int, at: Long, source: Int, musicPlaying: Boolean): AmbientSound {
            require(count in 1..pcm.size)
            var squares = 0.0; var peak = 0.0; var clipped = 0
            for (i in 0 until count) { val v = pcm[i] / 32768.0; squares += v * v; peak = max(peak, abs(v)); if (abs(v) >= .99) clipped++ }
            return AmbientSound(at, 20 * log10(sqrt(squares / count).coerceAtLeast(1e-6)), 20 * log10(peak.coerceAtLeast(1e-6)), clipped.toDouble() / count, count, source, musicPlaying)
        }
    }
}
