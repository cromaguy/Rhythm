// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import chromahub.rhythm.app.features.local.data.experience.*
import chromahub.rhythm.app.util.ArtworkStorage
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class PersonalMediaTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(directory: File) = object : ContextWrapper(context) { override fun getFilesDir(): File = directory }
    @Test fun artworkIsActualWebPWithTransparentPixelsAndBoundedDimensions() {
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0x80ff0000.toInt())
        val file = File.createTempFile("artwork-", ".webp", context.cacheDir)
        try {
            ArtworkStorage.write(bitmap, file)
            val data = file.readBytes(); assertEquals("RIFF", String(data.copyOfRange(0, 4))); assertEquals("WEBP", String(data.copyOfRange(8, 12)))
            val embedded = ArtworkStorage.forEmbedding(data)
            assertEquals("image/png", embedded.second); assertEquals("image/png", ArtworkStorage.mime(embedded.first))
            val decoded = BitmapFactory.decodeFile(file.path)
            assertEquals(1600, decoded.width); assertEquals(800, decoded.height); assertTrue(decoded.hasAlpha()); decoded.recycle()
        } finally { bitmap.recycle(); file.delete() }
    }
    @Test fun localVideoImportUsesRealContainersDropsAudioAndPreservesLinks() = runBlocking {
        val directory = File(context.cacheDir, "video-test-${System.nanoTime()}").apply { mkdirs() }
        val repo = MusicVideoRepository(isolated(directory))
        try {
            for (extension in listOf("mp4", "webm")) {
                val source = File(directory, "source.$extension")
                InstrumentationRegistry.getInstrumentation().context.assets.open("video-test.$extension").use { input -> source.outputStream().use { input.copyTo(it) } }
                val imported = repo.import("test:$extension", Uri.fromFile(source), "Test")
                assertTrue(imported.fileName!!.endsWith(".$extension")); assertEquals(imported, repo.get("test:$extension"))
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(requireNotNull(repo.uri(imported)).path!!)
                    if (imported.videoOnly) assertEquals(1, extractor.trackCount)
                    val videoTrack = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
                    extractor.selectTrack(videoTrack)
                    assertTrue(extractor.readSampleData(ByteBuffer.allocate(1024 * 1024), 0) > 0)
                    assertTrue(imported.bytes <= source.length())
                } finally { extractor.release() }
                val duplicate = repo.import("second:$extension", Uri.fromFile(source), "Test")
                assertEquals(imported.fileName, duplicate.fileName)
                assertTrue(imported.bytes > 0)
                repo.remove("test:$extension"); assertNull(repo.get("test:$extension")); assertNotNull(repo.get("second:$extension"))
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun recommendationJournalRebuildsTheSameModelAndIgnoresDuplicateEvents() {
        val directory = File(context.cacheDir, "learning-test-${System.nanoTime()}").apply { mkdirs() }
        ListeningLedger.get(context)
        val constructor = RecommendationLearning::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val learning = constructor.newInstance(isolated(directory))
        try {
            val feature = List(AdaptiveRecommendation.featureNames.size) { if (it == 8) 1.0 else 0.0 }
            learning.offer(listOf(RecommendationScore("song", 1.0, listOf("place"), feature)))
            learning.feedback("song", 1.0)
            val model = learning.model(); assertEquals(1, model.samples); assertEquals(1.0, model.explicit["song"])
            val file = File(directory, "music_metadata/recommendations").listFiles()!!.single()
            file.appendText(file.readText()) // same event ID, not a second learning outcome
            val restored = constructor.newInstance(isolated(directory)).model()
            assertEquals(model, restored)
            learning.feedback("song", 0.0); assertEquals(0.0, learning.model().explicit["song"])
            learning.feedback("song", null); assertFalse(learning.model().explicit.containsKey("song"))
        } finally { directory.deleteRecursively() }
    }
    @Test fun previousRecommendationSchemaRemainsRecoverableAfterAddingWeather() {
        val directory = File(context.cacheDir, "learning-migration-${System.nanoTime()}").apply { mkdirs() }
        try {
            val root = File(directory, "music_metadata/recommendations").apply { mkdirs() }
            val event = RecommendationEvent("old-event", 1, "outcome", "song", schema = 1, features = List(13) { .5 }, reward = 1.0, prediction = .5)
            File(root, "old.jsonl").writeText(Gson().toJson(event) + "\n")
            val constructor = RecommendationLearning::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
            val model = constructor.newInstance(isolated(directory)).model()
            assertEquals(1, model.samples); assertEquals(AdaptiveRecommendation.featureNames.size, model.weights.size); assertEquals(0.0, model.weights.last(), .000001)
        } finally { directory.deleteRecursively() }
    }
    @Test fun fixedPlayerThemesIgnoreObsoleteChoices() {
        val settings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context)
        val favorites = settings.favoriteSongs.value
        settings.setPlayerThemeId("MATERIAL"); settings.setMiniPlayerThemeId("EXPRESSIVE")
        assertEquals("EXPRESSIVE", settings.playerThemeId.value); assertEquals("MATERIAL", settings.miniPlayerThemeId.value)
        assertEquals(favorites, settings.favoriteSongs.value)
        val originalOrder = settings.homeSectionOrder.value
        val legacyOrder = listOf("DISCOVER", "RECENTLY_PLAYED", "ARTISTS", "RHYTHM_GUARD", "NEW_RELEASES", "RECENTLY_ADDED", "RECOMMENDED", "STATS", "MOOD")
        try {
            settings.setHomeSectionOrder(legacyOrder); settings.prefs.edit().commit()
            val constructor = chromahub.rhythm.app.shared.data.model.AppSettings::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
            assertEquals(legacyOrder, constructor.newInstance(context).homeSectionOrder.value)
        } finally { settings.setHomeSectionOrder(originalOrder) }
    }
    @Test fun personalizedInsertKeepsOriginalOrderAndNeverDuplicatesAQueuedSong() {
        fun song(id: String) = chromahub.rhythm.app.shared.data.model.Song(id, id, "A", "B", duration = 2000, uri = Uri.parse("content://tests/$id"))
        val original = (1..6).map { song(it.toString()) }
        val mixed = PersonalCollections.smartInsert(original, listOf(song("3"), song("7"), song("7"), song("8")))
        assertEquals(listOf("1", "2", "3", "7", "4", "5", "6", "8"), mixed.map { it.id })
        assertEquals(original.map { it.id }, mixed.filter { it.id in original.map { row -> row.id } }.map { it.id })
    }
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    @Test fun seekingToTheEndDoesNotRecordAFullListen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(context.cacheDir, "coverage-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("video-test.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val key = Uri.fromFile(file).toString()
        val song = chromahub.rhythm.app.shared.data.model.Song(key, "Coverage test", "Test", "Test", duration = 2000, uri = Uri.fromFile(file))
        val ready = java.util.concurrent.CountDownLatch(1); val ended = java.util.concurrent.CountDownLatch(1)
        lateinit var player: androidx.media3.exoplayer.ExoPlayer
        lateinit var recorder: ListeningRecorder
        try {
            instrumentation.runOnMainSync {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) { if (state == androidx.media3.common.Player.STATE_READY) ready.countDown(); if (state == androidx.media3.common.Player.STATE_ENDED) ended.countDown() }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { ready.countDown(); ended.countDown() }
                    })
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(song.uri)); prepare()
                }
                recorder = ListeningRecorder(context) { song }; recorder.attach(player)
            }
            assertTrue(ready.await(20, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync { player.play() }
            Thread.sleep(200)
            instrumentation.runOnMainSync { player.seekTo(1950) }
            assertTrue(ended.await(20, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync { recorder.close(); player.release() }
            var summary: ListeningRecord? = null
            repeat(100) { if (summary == null) { Thread.sleep(50); summary = ListeningLedger.get(context).records(kind = "play", limit = 50).firstOrNull { it.songKey == key } } }
            assertNotNull(summary); assertTrue(summary!!.playCompletionRatio!! < .3); assertFalse(summary!!.completed)
        } finally {
            instrumentation.runOnMainSync { runCatching { player.release() } }
            file.delete()
        }
    }
    @Test fun playlistFeedbackCanSwitchAndUndoWithoutPermanentlyHidingItsSongs() {
        val directory = File(context.cacheDir, "mix-reactions-${System.nanoTime()}").apply { mkdirs() }
        val constructor = RecommendationLearning::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val learning = constructor.newInstance(isolated(directory))
        try {
            val features = List(AdaptiveRecommendation.featureNames.size) { if (it == 0) 1.0 else 0.0 }
            learning.offer(listOf(RecommendationScore("song", 1.0, emptyList(), features)))
            val id = "collection:DAYLIST:"
            learning.collectionFeedback(id, listOf("song"), 0.0)
            assertTrue(learning.model().weights[0] < 0); assertEquals(0.0, learning.model().collections[id])
            learning.collectionFeedback(id, emptyList(), null)
            assertTrue(learning.model().weights.all { it == 0.0 }); assertFalse(learning.model().collections.containsKey(id))
            learning.collectionFeedback(id, emptyList(), 1.0)
            assertTrue(learning.model().weights[0] > 0); assertEquals(1, learning.model().samples)
            learning.collectionFeedback(id, emptyList(), 0.0)
            assertTrue(learning.model().weights[0] < 0); assertEquals(1, learning.model().samples)
            assertFalse(learning.model().explicit.containsKey("song"))
            assertEquals(learning.model(), constructor.newInstance(isolated(directory)).model())
        } finally { directory.deleteRecursively() }
    }
    @Test fun updaterVerifiesTheInstalledPackageAndRejectsWrongVersionAndPackage() {
        val own = File(context.applicationInfo.sourceDir)
        chromahub.rhythm.app.util.UpdateIntegrity.verifyPackage(context, own, chromahub.rhythm.app.BuildConfig.VERSION_CODE)
        assertTrue(runCatching { chromahub.rhythm.app.util.UpdateIntegrity.verifyPackage(context, own, chromahub.rhythm.app.BuildConfig.VERSION_CODE + 1) }.isFailure)
        val system = File(context.packageManager.getApplicationInfo("com.android.settings", 0).sourceDir)
        assertTrue(runCatching { chromahub.rhythm.app.util.UpdateIntegrity.verifyPackage(context, system) }.isFailure)
    }
    @Test fun locationOffProducesNoCoordinatesEvenIfPermissionExists() {
        val preferences = context.getSharedPreferences("listening_context", Context.MODE_PRIVATE)
        val original = preferences.all
        val collector = ListeningContext.get(context)
        try {
            collector.configure(enabled = false, precise = false)
            val snapshot = collector.snapshot()
            assertNull(snapshot.latitude); assertNull(snapshot.longitude); assertNull(snapshot.speedMps); assertFalse(snapshot.precise)
            assertTrue(snapshot.systemVolumeMax >= 0)
        } finally {
            preferences.edit().clear().apply {
                original.forEach { (key, value) -> when (value) { is Boolean -> putBoolean(key, value); is String -> putString(key, value); is Long -> putLong(key, value); is Float -> putFloat(key, value); is Int -> putInt(key, value) } }
            }.commit()
        }
    }
}
