// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.annotation.Keep
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.shared.data.model.Song
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.ByteBuffer

@Keep
data class MusicVideo(val songKey: String, val source: String, val videoId: String? = null, val fileName: String? = null,
    val title: String = "", val offsetMs: Long = 0, val addedAt: Long = System.currentTimeMillis(), val bytes: Long = 0, val videoOnly: Boolean = true)
@Keep
data class VideoCandidate(val id: String, val title: String, val artist: String)

object MusicVideoLinks {
    fun youtubeId(text: String): String? = runCatching {
        val url = java.net.URI(text.trim())
        require(url.scheme in setOf("https", "http"))
        val host = url.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")
        val id = when (host) {
            "youtu.be" -> url.path.trim('/').substringBefore('/')
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> if (url.path.startsWith("/watch")) url.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=')
                else url.path.split('/').let { parts -> if (parts.getOrNull(1) in setOf("embed", "shorts", "live")) parts.getOrNull(2) else null }
            else -> null
        }
        id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }.getOrNull()
    fun validId(id: String?) = id?.matches(Regex("[A-Za-z0-9_-]{11}")) == true
    fun muxerFormat(mime: String): Pair<Int, String> = if (mime in setOf("video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9")) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM to "webm"
        else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 to "mp4"
}

class MusicVideoRepository(private val context: Context) {
    private val root = File(context.filesDir, "music_metadata/videos").apply { mkdirs() }
    private val records = File(root, "records").apply { mkdirs() }
    private val gson = Gson()
    private fun record(key: String) = File(records, "${kr.co.metadata.crawler.CatalogFileStore.hash(key)}.json")
    fun get(key: String): MusicVideo? = runCatching { gson.fromJson(record(key).readText(), MusicVideo::class.java) }.getOrNull()?.takeIf {
        it.songKey == key && (it.source == "youtube" && MusicVideoLinks.validId(it.videoId) || it.source == "local" && it.fileName?.let { name -> File(name).name == name && File(root, name).isFile } == true)
    }
    fun save(video: MusicVideo) { require(video.offsetMs in -600_000..600_000); require(video.source == "local" || MusicVideoLinks.validId(video.videoId)); PersonalArchive.atomicWrite(record(video.songKey), gson.toJson(video).toByteArray()); PersonalArchive.enqueue(context) }
    fun link(key: String, url: String, title: String): MusicVideo = MusicVideo(key, "youtube", MusicVideoLinks.youtubeId(url) ?: error("Invalid YouTube link"), title = title).also(::save)
    fun uri(video: MusicVideo): Uri? = video.fileName?.takeIf { File(it).name == it }?.let { Uri.fromFile(File(root, it)) }
    fun remove(key: String) { PersonalArchive.atomicWrite(record(key), gson.toJson(MusicVideo(key, "removed")).toByteArray()); PersonalArchive.enqueue(context) } // Shared video assets are preserved for recovery.
    suspend fun search(song: Song): List<VideoCandidate> = withContext(Dispatchers.IO) {
        val body = com.google.gson.JsonObject().apply {
            addProperty("query", "${song.artist} ${song.title} official music video")
            add("context", JsonParser.parseString("""{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20241211.01.00","hl":"ko","gl":"KR"}}"""))
        }
        val request = Request.Builder().url("https://music.youtube.com/youtubei/v1/search").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        NetworkClient.genericHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            val text = response.body.string(); require(text.length <= 4_000_000)
            val found = linkedMapOf<String, VideoCandidate>()
            fun visit(node: JsonElement, depth: Int = 0) {
                if (depth > 40 || found.size >= 15) return
                if (node.isJsonArray) node.asJsonArray.forEach { visit(it, depth + 1) }
                else if (node.isJsonObject) {
                    val obj = node.asJsonObject
                    obj.getAsJsonObject("musicResponsiveListItemRenderer")?.let { item ->
                        val id = runCatching { item.getAsJsonObject("overlay").getAsJsonObject("musicItemThumbnailOverlayRenderer").getAsJsonObject("content").getAsJsonObject("musicPlayButtonRenderer").getAsJsonObject("playNavigationEndpoint").getAsJsonObject("watchEndpoint").get("videoId").asString }.getOrNull()
                            ?: runCatching { item.getAsJsonObject("navigationEndpoint").getAsJsonObject("watchEndpoint").get("videoId").asString }.getOrNull()
                        val labels = item.getAsJsonArray("flexColumns")?.mapNotNull { column -> runCatching { column.asJsonObject.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer").getAsJsonObject("text").getAsJsonArray("runs").joinToString("") { it.asJsonObject.get("text").asString } }.getOrNull() }.orEmpty()
                        if (MusicVideoLinks.validId(id) && labels.isNotEmpty()) found[id!!] = VideoCandidate(id, labels[0], labels.drop(1).joinToString(" · "))
                    }
                    obj.entrySet().forEach { visit(it.value, depth + 1) }
                }
            }
            visit(JsonParser.parseString(text)); found.values.toList()
        }
    }
    /** Stream-copy the video track; VP8/VP9 become real WebM, other supported codecs remain MP4.
     * No re-encoding or duplicated audio track, and no YouTube downloading. */
    suspend fun import(key: String, uri: Uri, title: String): MusicVideo = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor(); var temporary: File? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: error("No video track")
            val format = extractor.getTrackFormat(track)
            val (muxFormat, extension) = MusicVideoLinks.muxerFormat(format.getString(MediaFormat.KEY_MIME).orEmpty())
            temporary = File.createTempFile("import-", ".$extension", root)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(format.getString(MediaFormat.KEY_MIME).orEmpty().toByteArray())
            listOf(MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT, MediaFormat.KEY_ROTATION).forEach { field -> digest.update(ByteBuffer.allocate(4).putInt(if (format.containsKey(field)) format.getInteger(field) else 0).array()) }
            listOf("csd-0", "csd-1", "csd-2").forEach { field -> if (format.containsKey(field)) format.getByteBuffer(field)?.duplicate()?.let { digest.update(it) } }
            val muxer = MediaMuxer(temporary.path, muxFormat); var started = false
            try {
                val outputTrack = muxer.addTrack(format)
                if (format.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                muxer.start(); started = true; extractor.selectTrack(track)
                val buffer = ByteBuffer.allocate(8 * 1024 * 1024); val info = android.media.MediaCodec.BufferInfo()
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive(); buffer.clear()
                    val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                    require(size < buffer.capacity()); total += size; require(total <= 1024L * 1024 * 1024 && root.usableSpace > 32 * 1024 * 1024)
                    info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                    digest.update(buffer.duplicate().apply { position(0); limit(size) })
                    digest.update(ByteBuffer.allocate(8).putLong(extractor.sampleTime).array())
                    muxer.writeSampleData(outputTrack, buffer, info); extractor.advance()
                }
                require(total > 0)
            } finally { try { if (started) muxer.stop() } finally { muxer.release() } }
            val name = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } + ".$extension"
            val target = File(root, name)
            var original: File? = null
            try {
                // Platform WebM muxers can reserve substantial metadata space. Keep a smaller compatible
                // original container instead of claiming that every stream-copy reduces storage.
                val sourceSize = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length >= 0 } ?: it.parcelFileDescriptor.statSize } }.getOrNull() ?: -1
                val videoTracks = (0 until extractor.trackCount).count { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                if (sourceSize in 1 until temporary.length() && sourceSize <= 1024L * 1024 * 1024 && videoTracks == 1) {
                    val header = context.contentResolver.openInputStream(uri)?.use { input -> ByteArray(256).let { bytes -> val count = input.read(bytes); if (count > 0) bytes.copyOf(count) else byteArrayOf() } } ?: byteArrayOf()
                    val compatible = if (extension == "mp4") header.size >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp"
                        else header.size >= 4 && header.take(4) == listOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte()) && String(header, Charsets.ISO_8859_1).contains("webm")
                    if (compatible) {
                        original = File.createTempFile("original-", ".$extension", root)
                        context.contentResolver.openInputStream(uri)?.use { input -> original!!.outputStream().use { output ->
                            val buffer = ByteArray(8192); var size = 0L
                            while (true) { coroutineContext.ensureActive(); val n = input.read(buffer); if (n < 0) break; size += n; require(size <= sourceSize); output.write(buffer, 0, n) }
                            require(size == sourceSize)
                        } } ?: error("Cannot read video")
                    }
                }
                val chosen = original ?: temporary
                if (!target.exists()) check(chosen.renameTo(target))
                else if (chosen.length() < target.length()) {
                    val atomic = android.util.AtomicFile(target); val output = atomic.startWrite()
                    try { chosen.inputStream().use { it.copyTo(output) }; atomic.finishWrite(output) } catch (error: Exception) { atomic.failWrite(output); throw error }
                }
                val actual = MediaExtractor()
                val onlyVideo = try { actual.setDataSource(target.path); actual.trackCount == 1 } finally { actual.release() }
                MusicVideo(key, "local", fileName = name, title = title, bytes = target.length(), videoOnly = onlyVideo).also(::save)
            } finally { original?.delete() }
        } finally { extractor.release(); temporary?.delete() }
    }
}
