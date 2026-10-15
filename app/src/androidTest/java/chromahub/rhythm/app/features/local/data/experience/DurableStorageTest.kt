// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DurableStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun taste(directory: File): TasteMemory {
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getDatabasePath(name: String) = File(directory, name)
        }
        return TasteMemory::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(isolated)
    }
    @Test fun idleAnalysisWorkRequestsAreAcceptedByAndroidWorkManager() {
        RecommendationTrainingWorker.enqueue(context).result.get(20, java.util.concurrent.TimeUnit.SECONDS)
        AudioAnalysisWorker.enqueue(context)
        val tasks = androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork("audio_feature_analysis").get(20, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(tasks.isEmpty())
    }
    @Test fun analysisIsRefusedWhenTheDeviceIsNotCharging() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() } }
        try {
            shell("dumpsys battery unplug")
            shell("dumpsys battery set status 3")
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (AnalysisBudget(context).allowed() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertFalse(AnalysisBudget(context).allowed())
            assertTrue(runCatching { AnalysisBudget(context).blockingCheckpoint() }.exceptionOrNull() is AnalysisDeferred)
        } finally { shell("dumpsys battery reset"); shell("dumpsys battery set ac 1"); shell("dumpsys battery set status 2") }
    }
    @Test fun independentLikesAndRecurringExplicitReviewsKeepOriginalIntentAndCanBeUndone() {
        val root = File(context.cacheDir, "taste-${UUID.randomUUID()}").apply { mkdirs() }
        val memory = taste(root)
        val enabled = memory.reviewsEnabled
        try {
            memory.reviewsEnabled(true)
            val start = 1_000_000L
            val first = memory.set(TasteKind.ALBUM, "album", "Album", true, start)
            assertNull(memory.state(TasteKind.SONG, "song")); assertNull(memory.state(TasteKind.ARTIST, "artist"))
            // Merely reading or playing a favourite never triggers exposure or drops the real like.
            assertFalse(memory.state(TasteKind.ALBUM, "album")!!.pending)
            val review = memory.inspect(TasteKind.ALBUM, "album", "Album", now = first.nextReviewAt + 1)!!
            assertTrue(review.liked); assertTrue(review.pending); assertFalse(memory.visibleLiked(review)); assertNotNull(review.exposedAt)
            assertEquals(start, review.firstLikedAt)
            val confirmed = memory.set(TasteKind.ALBUM, "album", "Album", true, review.nextReviewAt + 2)
            assertTrue(memory.visibleLiked(confirmed)); assertFalse(confirmed.pending); assertEquals(start, confirmed.firstLikedAt)
            val secondReview = memory.inspect(TasteKind.ALBUM, "album", "Album", now = confirmed.nextReviewAt + 1)!!
            assertTrue(secondReview.pending); assertTrue(secondReview.cycle > review.cycle)
            memory.reviewsEnabled(false)
            assertTrue(memory.visibleLiked(secondReview))
            memory.reviewsEnabled(true)
            assertFalse(memory.set(TasteKind.ALBUM, "album", "Album", false, confirmed.nextReviewAt + 2).liked)
            assertTrue(memory.set(TasteKind.ALBUM, "album", "Album", true, confirmed.nextReviewAt + 3).liked)
            assertEquals(start, memory.state(TasteKind.ALBUM, "album")!!.firstLikedAt)
            assertEquals(1, memory.likedKeys(TasteKind.ALBUM).size)
            assertTrue(memory.likedKeys(TasteKind.SONG).isEmpty())
        } finally { memory.reviewsEnabled(enabled); memory.close(); root.deleteRecursively() }
    }
    @Test fun everyGenerationGetsItsOwnReactionIdentityAndKeepsItsExactQueue() {
        val first = RecommendationShelf.create(context, PersonalCollection.DAYLIST, null, listOf("one", "two"), 1)!!
        val second = RecommendationShelf.create(context, PersonalCollection.DAYLIST, null, listOf("two", "three"), 2)!!
        assertNotEquals(first.feedbackKey, second.feedbackKey)
        assertEquals(listOf("one", "two"), RecommendationShelf.recent(context).single { it.id == first.id }.songKeys)
        assertEquals(second, RecommendationShelf.current(context))
        RecommendationShelf.select(context, first)
        assertEquals(first, RecommendationShelf.current(context))
    }
    @Test fun cumulativeAffinityDoesNotCountRepeatedCheckpointsOrOlderWritesTwice() {
        val ledger = ListeningLedger.get(context); val id = UUID.randomUUID().toString()
        fun row(ms: Long, recorded: Long) = ListeningRecord(id, id, "segment", id, id, "T", "A", "B", "Pop",
            1_000_000, 1_000_000 + ms, ms, 100_000, 0, ms, 1f, "checkpoint", recordedAt = recorded)
        ledger.record(row(10_000, 1)); ledger.record(row(20_000, 2)); ledger.record(row(10_000, 1))
        val affinity = ledger.affinities().single { it.songKey == id }
        assertEquals(20_000, affinity.listenedMs); assertEquals(1, affinity.plays)
        assertEquals(20_000, ledger.records().single { it.id == id }.listenedMs)
    }
    @Test fun recentTasteAndReplayIntervalsSurviveIncrementalContextProjection() {
        val ledger = ListeningLedger.get(context); val key = UUID.randomUUID().toString()
        val now = System.currentTimeMillis(); val scene = ListeningSituation(hour = 9, weekday = 4)
        fun row(index: Int, at: Long, listened: Long, recorded: Long) = ListeningRecord("$key:$index", "$key:$index", "segment", key, key,
            "T", "A", "B", key, at, at + listened, listened, 100_000, 0, listened, 1f, "checkpoint", recordedAt = recorded, situation = scene)
        ledger.record(row(1, now - 4 * 86_400_000L, 30_000, 1))
        ledger.record(row(2, now - 2 * 86_400_000L, 30_000, 2))
        ContextPreferenceIndex(context).use { index ->
            index.rebuild(ledger, AnalysisBudget(context))
            val first = index.preferences(scene, null)
            assertTrue(first.recentGenres.getValue(key) > 0)
            assertEquals(1.0, first.replayDue.getValue(key), .00001)
            ledger.record(row(2, now - 2 * 86_400_000L, 60_000, 3))
            index.rebuild(ledger, AnalysisBudget(context))
            assertEquals(first.replayDue.getValue(key), index.preferences(scene, null).replayDue.getValue(key), .00001)
        }
    }
    @Test fun contextProjectionIncludesTransportDaylightAndActualPerPlayCompletion() {
        val ledger = ListeningLedger.get(context); val id = UUID.randomUUID().toString()
        val scene = ListeningSituation(hour = 9, weekday = 4, motion = MotionSnapshot("walking", .8, "test", 0), daylight = DaylightSnapshot(true, "test"))
        fun row(kind: String, ms: Long, recorded: Long) = ListeningRecord("$id:$kind", id, kind, id, id, "T", "A", "B", id,
            1_000_000, 1_000_000 + ms, ms, 100_000, 0, ms, 1f, "checkpoint", recordedAt = recorded, situation = scene, playCompletionRatio = .8)
        ledger.record(row("segment", 30_000, 1)); ledger.record(row("segment", 60_000, 2)); ledger.record(row("play", 80_000, 3))
        ContextPreferenceIndex(context).use { index ->
            index.rebuild(ledger, AnalysisBudget(context))
            val matched = index.preferences(scene, null)
            assertTrue(matched.motionGenres.getValue(id) > 0); assertTrue(matched.daylightGenres.getValue(id) > 0)
            assertEquals(.6, matched.completionRates.getValue(id), .00001)
            assertFalse(index.preferences(scene.copy(motion = null, daylight = null), null).motionGenres.containsKey(id))
        }
    }
}
