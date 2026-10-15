// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import android.content.Context
import android.net.Uri
import androidx.work.*
import chromahub.rhythm.app.network.NetworkClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.util.concurrent.TimeUnit

/** NewPipeExtractor GPL-3.0+: native Android extraction, no bundled Python or re-encoding. */
object YouTubeVideoDownload {
    const val MAX_BYTES = 96L * 1024 * 1024
    internal val lock = Mutex()
    private var initialized = false
    fun name(key: String, id: String) = "youtube_video:${kr.co.metadata.crawler.CatalogFileStore.hash(key)}:$id"
    private fun songTag(key: String) = "video_song:${kr.co.metadata.crawler.CatalogFileStore.hash(key)}"
    suspend fun enqueue(context: Context, key: String, id: String, title: String, mobile: Boolean = false, force: Boolean = false) {
        require(MusicVideoLinks.validId(id))
        val manager = WorkManager.getInstance(context)
        if (!force && manager.getWorkInfosForUniqueWorkFlow(name(key, id)).first().any { !it.state.isFinished }) return
        manager.cancelAllWorkByTag(songTag(key))
        manager.enqueueUniqueWork(name(key, id), ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<YouTubeVideoDownloadWorker>().addTag(songTag(key))
                .setInputData(workDataOf("key" to key, "id" to id, "title" to title))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (mobile) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build()).build())
    }
    fun cancel(context: Context, key: String) { WorkManager.getInstance(context).cancelAllWorkByTag(songTag(key)) }
    @Synchronized internal fun initialize() {
        if (initialized) return
        NewPipe.init(object : org.schabi.newpipe.extractor.downloader.Downloader() {
            override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
                val builder = okhttp3.Request.Builder().url(request.url())
                request.headers().forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
                builder.method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                return NetworkClient.genericHttpClient.newCall(builder.build()).execute().use { response ->
                    require(response.body.contentLength() <= 8 * 1024 * 1024) { "Extractor response too large" }
                    val input = response.body.byteStream(); val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 8 * 1024 * 1024); output.write(buffer, 0, count) }
                    org.schabi.newpipe.extractor.downloader.Response(response.code, response.message, response.headers.toMultimap(), output.toString(Charsets.UTF_8.name()), response.request.url.toString())
                }
            }
        }, Localization("ko", "KR"))
        initialized = true
    }
    internal fun choose(streams: List<VideoStream>, seconds: Long): VideoStream {
        val valid = streams.filter { stream ->
            stream.isUrl && stream.isVideoOnly && stream.height in 1..720 && stream.fps <= 30 &&
                stream.format?.suffix in setOf("webm", "mp4") && runCatching {
                    val url = java.net.URI(stream.content)
                    url.scheme == "https" && url.host?.endsWith(".googlevideo.com") == true
                }.getOrDefault(false)
        }
        val compact = valid.filter { it.bitrate <= 0 || it.bitrate.toLong() * seconds / 8 < 48L * 1024 * 1024 }.ifEmpty { valid }
        return compact.sortedWith(compareByDescending<VideoStream> { it.height }
            .thenBy { if (it.format?.suffix == "webm") 0 else 1 }.thenBy { it.bitrate.takeIf { rate -> rate > 0 } ?: Int.MAX_VALUE }).firstOrNull()
            ?: error("No compatible video-only stream at or below 720p")
    }
}

class YouTubeVideoDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { YouTubeVideoDownload.lock.withLock {
        val key = inputData.getString("key") ?: return@withLock Result.failure()
        val id = inputData.getString("id")?.takeIf(MusicVideoLinks::validId) ?: return@withLock Result.failure()
        val repository = MusicVideoRepository(applicationContext)
        if (repository.get(key)?.let { it.source != "youtube" || it.videoId != id } != false) return@withLock Result.success()
        val temporary = File.createTempFile("youtube-video-", ".part", applicationContext.cacheDir)
        try {
            require(temporary.usableSpace > YouTubeVideoDownload.MAX_BYTES * 2) { "Not enough free storage" }
            YouTubeVideoDownload.initialize()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
            val stream = YouTubeVideoDownload.choose(info.videoOnlyStreams, info.duration)
            val client = NetworkClient.genericHttpClient.newBuilder().callTimeout(4, TimeUnit.MINUTES).build()
            val call = client.newCall(okhttp3.Request.Builder().url(stream.content).build())
            val cancellation = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try { call.execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val length = response.body.contentLength(); require(length <= YouTubeVideoDownload.MAX_BYTES)
                response.body.byteStream().use { input -> temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024); var bytes = 0L; var lastProgress = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        bytes += count; require(bytes <= YouTubeVideoDownload.MAX_BYTES)
                        output.write(buffer, 0, count)
                        if (bytes - lastProgress >= 256 * 1024) {
                            setProgress(workDataOf("bytes" to bytes, "percent" to if (length > 0) (bytes * 100 / length).toInt() else -1))
                            lastProgress = bytes; yield()
                        }
                    }
                    require(bytes > 0 && (length < 0 || bytes == length))
                } }
            }
            } finally { cancellation.cancel() }
            currentCoroutineContext().ensureActive()
            val imported = repository.import(key, Uri.fromFile(temporary), inputData.getString("title").orEmpty(), persist = false)
                .copy(videoId = id, width = stream.width, height = stream.height)
            repository.installDownload(key, id, imported)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Result.failure(workDataOf("download_error" to (error.message?.take(160) ?: "Video download failed")))
        } finally { temporary.delete() }
    } }
}
