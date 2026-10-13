// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.net.Uri
import chromahub.rhythm.app.shared.data.model.Song
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class ListeningPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun row(id: String, duration: Long = 20_000, recorded: Long = 1000) = ListeningRecord(id, id, "segment", id, id,
        "Test", "Test artist", "Test album", "Pop", 100_000, 100_000 + duration, duration, 60_000, 0, duration, 1f, "checkpoint", recordedAt = recorded)
    @Test fun checkpointUpdatesOneRecordAndOldImportCannotReplaceIt() {
        val ledger = ListeningLedger.get(context); val id = UUID.randomUUID().toString()
        ledger.record(row(id)); ledger.record(row(id, 30_000, 2000))
        assertEquals(30_000L, ledger.records().single { it.id == id }.listenedMs)
        val input = File.createTempFile("old-checkpoint", ".jsonl", context.cacheDir)
        try { input.writeText(Gson().toJson(row(id)) + "\n"); ledger.importJournal(input) }
        finally { input.delete() }
        assertEquals(30_000L, ledger.records().single { it.id == id }.listenedMs)
    }
    @Test fun rawJournalRecoversARecordMissingFromTheDatabase() {
        val ledger = ListeningLedger.get(context); val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "listening_history").apply { mkdirs() }
        val journal = File(directory, "1970-01-recovery-test.jsonl")
        journal.appendText(Gson().toJson(row(id)) + "\n")
        ledger.recover()
        assertNotNull(ledger.records().firstOrNull { it.id == id })
        ledger.recover()
        assertEquals(1, ledger.records().count { it.id == id })
    }
    @Test fun fullTrackPcmAnalysisProducesAnActualFingerprintAndWaveform() = runBlocking {
        val samples = 8192; val data = ByteBuffer.allocate(samples * 2 + 44).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(samples * 2 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(8192).putInt(16384).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { n -> data.putShort((16384 * sin(2 * PI * 1000 * n / 8192)).toInt().toShort()) }
        val file = File.createTempFile("audio-features-", ".wav", context.cacheDir)
        try {
            file.writeBytes(data.array())
            val song = Song("audio-test", "Tone", "Test", "Test", duration = 1000, uri = Uri.fromFile(file))
            val features = AudioAnalysisWorker.analyze(context, song)
            assertEquals(64, features.pcmSha256.length); assertEquals(1000L, features.decodedDurationMs)
            assertEquals(256, features.waveform.size); assertTrue(features.waveform.max() > .3)
            assertTrue(features.rmsDb in -10.0..-8.0)
        } finally { file.delete() }
    }
    @Test fun explicitArtworkReplacementOverridesAnEarlierRemovalWithoutChangingTheDefault() = runBlocking {
        val catalog = chromahub.rhythm.app.features.local.data.metadata.CatalogEnrichmentService.get(context)
        val originalOptions = catalog.preferences.options.value
        val id = UUID.randomUUID().toString()
        val song = Song(id, "Test", "Test artist", "Test album", duration = 1000, uri = Uri.parse("content://media/external/audio/media/999999"), path = "/test/$id")
        val images = File(catalog.root, "images").apply { mkdirs() }
        val image = File(images, "$id.jpg")
        android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
            image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        }
        val kind = kr.co.metadata.crawler.EntityKind.TRACK
        kr.co.metadata.crawler.CatalogFileStore(File(catalog.root, "records")).merge(kind, catalog.songKey(song), kr.co.metadata.crawler.CatalogDocument(kind,
            listOf(kr.co.metadata.crawler.CatalogValue(kr.co.metadata.crawler.CatalogField.TRACK_IMAGE, "https://example.com/cover.jpg", "test"),
                kr.co.metadata.crawler.CatalogValue(kr.co.metadata.crawler.CatalogField.TRACK_DURATION_MS, "1000", "test"))), imageFile = image.name)
        val prefs = context.getSharedPreferences("artwork_overrides", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("removed_$id", true).commit()
        try {
            val repository = chromahub.rhythm.app.features.local.di.LocalMusicModule.provideMusicRepository(context)
            catalog.preferences.update(originalOptions.copy(replaceTags = false, forceFields = emptySet()))
            assertNull(repository.fetchTrackArtwork(listOf(song)).single().artworkUri)
            assertTrue(catalog.artworkRemoved(song))
            catalog.preferences.update(originalOptions.copy(forceFields = setOf(kr.co.metadata.crawler.CatalogField.TRACK_IMAGE)))
            assertNotNull(repository.fetchTrackArtwork(listOf(song)).single().artworkUri)
            assertFalse(catalog.artworkRemoved(song))
        } finally { catalog.preferences.update(originalOptions); prefs.edit().remove("removed_$id").commit() }
    }
}
