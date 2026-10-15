// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.work.*
import chromahub.rhythm.app.features.local.di.LocalMusicModule
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class AudioAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AnalysisBudget(applicationContext).allowed()) return@withContext Result.retry()
        val ledger = ListeningLedger.get(applicationContext)
        val songs = LocalMusicModule.provideMusicRepository(applicationContext).loadSongs()
        val start = System.currentTimeMillis()
        var done = 0
        for (song in songs) {
            coroutineContext.ensureActive()
            val signature = "${song.dateModified}:${song.duration}"
            if (ledger.feature(song.uri.toString(), signature)?.version == 2) continue
            val failureKey = "audio_failure:${song.uri}"
            if ((ledger.state(failureKey)?.toLongOrNull() ?: 0) > System.currentTimeMillis()) continue
            if (isStopped) return@withContext Result.success()
            val catalog = chromahub.rhythm.app.features.local.data.metadata.CatalogEnrichmentService.get(applicationContext)
            if (catalog.audioBusy(song)) continue
            try {
                catalog.withAudioWrite(song) {
                    ledger.saveFeature(song.uri.toString(), signature, analyze(applicationContext, song)); done++; true
                }
            }
            catch (_: AnalysisDeferred) { return@withContext Result.retry() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { ledger.state(failureKey, (System.currentTimeMillis() + 7 * 24 * 3600_000L).toString()) }
            setProgress(workDataOf("analyzed" to done, "title" to song.title))
            if (System.currentTimeMillis() - start > 8 * 60_000) {
                enqueue(applicationContext, continuation = true); return@withContext Result.success()
            }
        }
        Result.success(workDataOf("analyzed" to done))
    }
    companion object {
        fun enqueue(context: Context, continuation: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork("audio_feature_analysis", if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
                    .setConstraints(Constraints.Builder()
                    .setRequiresCharging(true).setRequiresDeviceIdle(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build())
        }
        suspend fun analyze(context: Context, song: Song): AudioFeatures = withContext(AnalysisBudget.dispatcher) {
            val budget = AnalysisBudget(context); budget.checkpoint()
            val extractor = MediaExtractor(); var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, song.uri, null)
                val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
                if (mime == "audio/raw") {
                    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                    val signal = SignalAnalysis(rate, song.duration.coerceAtLeast(1))
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = java.nio.ByteBuffer.allocate(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                    val bytesPerFrame = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    while (true) {
                        coroutineContext.ensureActive(); buffer.clear()
                        val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                        buffer.position(0); buffer.limit(size); digest.update(buffer.duplicate())
                        while (buffer.remaining() >= bytesPerFrame) {
                            var mono = 0.0
                            repeat(channels) { mono += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float.toDouble() else buffer.short / 32768.0 }
                            signal.add(mono / channels)
                            if (buffer.position() % (bytesPerFrame * 1024) == 0) budget.checkpoint()
                        }
                        extractor.advance()
                    }
                    return@withContext signal.finish(song.uri.toString(), digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }, channels)
                }
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                val decoder = MediaCodec.createDecoderByType(mime); codec = decoder
                decoder.configure(format, null, null, 0); decoder.start()
                var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT
                var signal: SignalAnalysis? = null
                val digest = MessageDigest.getInstance("SHA-256")
                val info = MediaCodec.BufferInfo(); var inputEnded = false; var outputEnded = false
                val started = System.currentTimeMillis()
                while (!outputEnded) {
                    coroutineContext.ensureActive(); budget.checkpoint(); check(System.currentTimeMillis() - started < 30 * 60_000) { "Decode timeout" }
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = requireNotNull(decoder.getInputBuffer(index)); val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                            else { decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val output = decoder.outputFormat; rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                    } else if (index >= 0) {
                        try {
                            val buffer = requireNotNull(decoder.getOutputBuffer(index)); buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            digest.update(buffer.duplicate())
                            if (signal == null) signal = SignalAnalysis(rate, song.duration.coerceAtLeast(1))
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            val bytesPerFrame = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            while (buffer.remaining() >= bytesPerFrame) {
                                var mono = 0.0
                                repeat(channels) { mono += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float.toDouble() else buffer.short / 32768.0 }
                                signal!!.add(mono / channels)
                                if (buffer.position() % (bytesPerFrame * 1024) == 0) budget.checkpoint()
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                }
                requireNotNull(signal).finish(song.uri.toString(), digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }, channels)
            } finally { runCatching { codec?.stop() }; codec?.release(); extractor.release() }
        }
    }
}
