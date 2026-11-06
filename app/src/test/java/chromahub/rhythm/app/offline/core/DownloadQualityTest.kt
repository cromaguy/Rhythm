// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Offline cache, "Download quality": which format a download asks for, how an answer is named, what it costs. */
class DownloadQualityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- the options and their request parameters ----

    @Test
    fun theDefaultIsOpus160() {
        assertEquals(DownloadQuality.OPUS_160, DownloadQuality.DEFAULT)
        assertEquals(DownloadQuality.DEFAULT, DownloadQuality.fromId(null))
        assertEquals(DownloadQuality.DEFAULT, DownloadQuality.fromId("no-such-option"))
    }

    @Test
    fun everyOptionRoundTripsThroughItsPersistedId() {
        DownloadQuality.entries.forEach { assertEquals(it, DownloadQuality.fromId(it.id)) }
        assertEquals(DownloadQuality.entries.size, DownloadQuality.entries.map { it.id }.toSet().size)
    }

    @Test
    fun theRequestParametersPerOption() {
        val expected = mapOf(
            DownloadQuality.ORIGINAL to (null to 0),
            DownloadQuality.OPUS_192 to ("opus" to 192),
            DownloadQuality.OPUS_160 to ("opus" to 160),
            DownloadQuality.OPUS_128 to ("opus" to 128),
            DownloadQuality.AAC_256 to ("aac" to 256),
            DownloadQuality.MP3_256 to ("mp3" to 256),
        )
        assertEquals(expected.keys, DownloadQuality.entries.toSet())
        expected.forEach { (quality, params) ->
            assertEquals(quality.name, params.first, quality.format)
            assertEquals(quality.name, params.second, quality.kbps)
            assertEquals(quality.name, params.first != null, quality.transcodes)
        }
        assertNull(DownloadQuality.ORIGINAL.extension)
        assertEquals(".opus", DownloadQuality.OPUS_160.extension)
        assertEquals(".aac", DownloadQuality.AAC_256.extension)
        assertEquals(".mp3", DownloadQuality.MP3_256.extension)
    }

    // ---- which songs the setting leaves alone ----

    private fun effective(q: DownloadQuality, codec: String?, kbps: Int?, channels: Int?) =
        DownloadQualityPolicy.effective(q, DownloadSource(codec, kbps, channels))

    @Test
    fun aLosslessStereoSongIsTranscodedToTheSelectedQuality() {
        val e = effective(DownloadQuality.OPUS_160, "flac", 987, 2)
        assertEquals(DownloadQuality.OPUS_160, e.quality)
        assertEquals(EffectiveQuality.Reason.AS_SELECTED, e.reason)
        assertEquals(DownloadQuality.OPUS_160, effective(DownloadQuality.OPUS_160, null, null, null).quality) // nothing known: ask
        assertEquals(DownloadQuality.AAC_256, effective(DownloadQuality.AAC_256, "wav", 1411, 2).quality)
    }

    @Test
    fun originalSelectedMeansNoTranscode() {
        val e = effective(DownloadQuality.ORIGINAL, "flac", 900, 2)
        assertEquals(DownloadQuality.ORIGINAL, e.quality)
        assertEquals(EffectiveQuality.Reason.ORIGINAL_SELECTED, e.reason)
    }

    @Test
    fun aMultichannelFileIsNeverTranscoded() {
        // E-AC-3 / Atmos beds and 5.1 FLAC: Navidrome would cap Opus at 8 channels and MP3 at 2, and the
        // renderer wants the channels it was given.
        val e = effective(DownloadQuality.OPUS_160, "m4a", 768, 6)
        assertEquals(DownloadQuality.ORIGINAL, e.quality)
        assertEquals(EffectiveQuality.Reason.MULTICHANNEL, e.reason)
        assertEquals(DownloadQuality.ORIGINAL, effective(DownloadQuality.MP3_256, "flac", 3000, 8).quality)
        assertEquals(DownloadQuality.OPUS_128, effective(DownloadQuality.OPUS_128, "flac", 900, 1).quality) // mono is fine
    }

    @Test
    fun aLossyFileAtOrUnderTheTargetIsKeptAsItIs() {
        assertEquals(EffectiveQuality.Reason.ALREADY_SMALL, effective(DownloadQuality.OPUS_160, "mp3", 128, 2).reason)
        assertEquals(DownloadQuality.ORIGINAL, effective(DownloadQuality.OPUS_160, "opus", 160, 2).quality)
        assertEquals(DownloadQuality.ORIGINAL, effective(DownloadQuality.OPUS_160, "m4a", 96, 2).quality)
        // A lossy file of unknown bitrate: no saving can be shown, so it is not re-encoded.
        assertEquals(DownloadQuality.ORIGINAL, effective(DownloadQuality.OPUS_160, "mp3", null, 2).quality)
    }

    @Test
    fun aLossyFileAboveTheTargetIsTranscoded() {
        assertEquals(DownloadQuality.OPUS_160, effective(DownloadQuality.OPUS_160, "mp3", 320, 2).quality)
        assertEquals(DownloadQuality.OPUS_160, effective(DownloadQuality.OPUS_160, "m4a", 900, 2).quality) // ALAC in m4a
        assertEquals(DownloadQuality.OPUS_128, effective(DownloadQuality.OPUS_128, "mp3", 192, 2).quality)
    }

    // ---- naming a download after what the server sent ----

    @Test
    fun aFaithfulOpusAnswerIsStoredAsOpus() {
        for (type in listOf("audio/ogg", "audio/opus", "application/ogg", "audio/ogg; codecs=opus")) {
            val r = DownloadFormats.resolve(DownloadQuality.OPUS_160, null, type)
            assertEquals(type, ".opus", r.extension)
            assertTrue(type, r.matchedRequest)
        }
    }

    @Test
    fun faithfulAacAndMp3AnswersAreStoredByTheirContainer() {
        assertEquals(".aac", DownloadFormats.resolve(DownloadQuality.AAC_256, null, "audio/aac").extension)
        assertEquals(".m4a", DownloadFormats.resolve(DownloadQuality.AAC_256, null, "audio/mp4").extension)
        assertTrue(DownloadFormats.resolve(DownloadQuality.AAC_256, null, "audio/mp4").matchedRequest)
        val mp3 = DownloadFormats.resolve(DownloadQuality.MP3_256, null, "audio/mpeg")
        assertEquals(".mp3", mp3.extension)
        assertTrue(mp3.matchedRequest)
    }

    @Test
    fun aServerThatIgnoredTheTranscodeAndSentTheOriginalIsStoredAsTheOriginal() {
        val flac = DownloadFormats.resolve(DownloadQuality.OPUS_160, null, "audio/flac")
        assertEquals(".flac", flac.extension)
        assertFalse(flac.matchedRequest)
        val byName = DownloadFormats.resolve(DownloadQuality.OPUS_160, """attachment; filename="01 Song.flac"""", "application/octet-stream")
        assertEquals(".flac", byName.extension)
        assertFalse(byName.matchedRequest)
        val mp3 = DownloadFormats.resolve(DownloadQuality.OPUS_160, null, "audio/mpeg")
        assertEquals(".mp3", mp3.extension)
        assertFalse(mp3.matchedRequest)
        val wav = DownloadFormats.resolve(DownloadQuality.AAC_256, null, "audio/x-wav")
        assertEquals(".wav", wav.extension)
        assertFalse(wav.matchedRequest)
    }

    @Test
    fun theFileNameOutranksAGenericType() {
        val r = DownloadFormats.resolve(DownloadQuality.OPUS_160, """attachment; filename="a.opus"""", "application/octet-stream")
        assertEquals(".opus", r.extension)
        assertTrue(r.matchedRequest)
    }

    @Test
    fun anAnswerWithNoUsableTypeIsTakenAtTheRequestAndLeftToTheByteCheck() {
        for (type in listOf(null, "", "application/octet-stream", "binary/octet-stream")) {
            val r = DownloadFormats.resolve(DownloadQuality.OPUS_160, null, type)
            assertEquals(".opus", r.extension)
            assertTrue(r.matchedRequest)
        }
    }

    @Test
    fun anOriginalAnswerIsNamedExactlyAsBefore() {
        assertEquals(".flac", DownloadFormats.extensionFor("""attachment; filename="a.FLAC"""", null))
        assertEquals(".flac", DownloadFormats.extensionFor(null, "audio/flac"))
        assertEquals(".ogg", DownloadFormats.extensionFor(null, "audio/ogg"))
        assertEquals(".m4a", DownloadFormats.extensionFor(null, "audio/mp4"))
        assertEquals(".mp3", DownloadFormats.extensionFor(null, "text/plain"))
        assertEquals(".mp3", DownloadFormats.extensionFor(null, null))
        assertEquals(".mp3", DownloadFormats.resolve(DownloadQuality.ORIGINAL, null, null).extension)
    }

    @Test
    fun theFirstBytesNameAFile() {
        fun h(vararg b: Int) = ByteArray(16).also { b.forEachIndexed { i, v -> it[i] = v.toByte() } }
        assertEquals(".flac", DownloadFormats.sniff("fLaC".toByteArray() + ByteArray(12)))
        assertEquals(".ogg", DownloadFormats.sniff("OggS".toByteArray() + ByteArray(12)))
        assertEquals(".mp3", DownloadFormats.sniff("ID3".toByteArray() + ByteArray(13)))
        assertEquals(".mp3", DownloadFormats.sniff(h(0xFF, 0xFB, 0x90)))
        assertEquals(".aac", DownloadFormats.sniff(h(0xFF, 0xF1, 0x50)))
        assertEquals(".m4a", DownloadFormats.sniff(ByteArray(4) + "ftypM4A ".toByteArray() + ByteArray(4)))
        assertEquals(".wav", DownloadFormats.sniff("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()))
        assertNull(DownloadFormats.sniff(ByteArray(16)))
        assertNull(DownloadFormats.sniff(ByteArray(0)))
        assertNull(DownloadFormats.sniff("{\"subsonic-response\"".toByteArray()))
    }

    @Test
    fun aMislabelledFileIsCorrectedAndOggVariantsAreNot() {
        val flacBytes = "fLaC".toByteArray() + ByteArray(12)
        val wrong = DownloadFormats.correct(".opus", flacBytes)
        assertEquals(".flac", wrong.extension)
        assertFalse(wrong.matchedRequest)
        val oggBytes = "OggS".toByteArray() + ByteArray(12)
        assertEquals(".opus", DownloadFormats.correct(".opus", oggBytes).extension) // Opus and Vorbis share the container
        assertTrue(DownloadFormats.correct(".opus", oggBytes).matchedRequest)
        assertEquals(".m4a", DownloadFormats.correct(".m4a", ByteArray(4) + "ftypmp42".toByteArray()).extension)
        assertEquals(".opus", DownloadFormats.correct(".opus", ByteArray(16)).extension) // unrecognised bytes: leave the name
    }

    @Test
    fun whatMatchesWhatWasAskedFor() {
        assertTrue(DownloadFormats.matches(DownloadQuality.OPUS_160, ".opus"))
        assertTrue(DownloadFormats.matches(DownloadQuality.OPUS_160, ".ogg"))
        assertFalse(DownloadFormats.matches(DownloadQuality.OPUS_160, ".flac"))
        assertTrue(DownloadFormats.matches(DownloadQuality.AAC_256, ".m4a"))
        assertTrue(DownloadFormats.matches(DownloadQuality.AAC_256, ".aac"))
        assertFalse(DownloadFormats.matches(DownloadQuality.MP3_256, ".aac"))
        assertTrue(DownloadFormats.matches(DownloadQuality.MP3_256, ".mp3"))
    }

    // ---- the ledger records what landed ----

    @Test
    fun theLedgerKeepsTheActualFormatAndBitrateAcrossARestart() {
        val file = File(tmp.root, "d/download_state.json")
        val store = DownloadStateStore(file)
        store.enqueue(DownloadItem("S::1", "T", "A", "Al", durationMs = 215_000))
        store.markComplete("S::1", 4_300_000, format = "opus", bitrateKbps = 160, transcoded = true)
        store.enqueue(DownloadItem("S::2", "T2", "A", "Al"))
        // The server ignored the transcode: it is a FLAC, and the ledger says so.
        store.markComplete("S::2", 38_000_000, format = "flac", bitrateKbps = 987, transcoded = false)

        val again = DownloadStateStore(file)
        val one = again.get("S::1")!!
        assertEquals("opus", one.format)
        assertEquals(160, one.bitrateKbps)
        assertTrue(one.transcoded)
        assertEquals(215_000L, one.durationMs)
        val two = again.get("S::2")!!
        assertEquals("flac", two.format)
        assertFalse(two.transcoded)
    }

    @Test
    fun anOldLedgerWithoutTheNewFieldsStillLoads() {
        val file = File(tmp.root, "d/download_state.json").also { it.parentFile.mkdirs() }
        file.writeText("""{"version":1,"items":[{"songId":"S::1","title":"T","artist":"A","album":"Al","state":"COMPLETE","attempts":0,"sourceBytes":40000000,"queuedAt":5}]}""")
        val item = DownloadStateStore(file).get("S::1")!!
        assertNull(item.format)
        assertEquals(0, item.bitrateKbps)
        assertFalse(item.transcoded)
        assertEquals(0L, item.durationMs)
        assertEquals(40_000_000L, item.sourceBytes)
    }

    @Test
    fun markCompleteWithoutAFormatKeepsWhatTheEntryHas() {
        val store = DownloadStateStore(File(tmp.root, "d/download_state.json"))
        store.enqueue(DownloadItem("S::1", "T", "A", "Al", format = "flac", bitrateKbps = 900))
        store.markComplete("S::1", 1)
        assertEquals("flac", store.get("S::1")!!.format)
        assertEquals(900, store.get("S::1")!!.bitrateKbps)
    }

    @Test
    fun migrateRewritesOnlyWhatChangesAndWritesOnce() {
        val store = DownloadStateStore(File(tmp.root, "d/download_state.json"))
        store.enqueueAll(listOf(DownloadItem("S::1", "T", "A", "Al"), DownloadItem("S::2", "T", "A", "Al", durationMs = 100)))
        val writes = store.writeCount
        val n = store.migrate { if (it.durationMs == 0L) it.copy(durationMs = 200_000) else it }
        assertEquals(1, n)
        assertEquals(writes + 1, store.writeCount)
        assertEquals(200_000L, store.get("S::1")!!.durationMs)
        assertEquals(100L, store.get("S::2")!!.durationMs)
        assertEquals(0, store.migrate { it })
        assertEquals(writes + 1, store.writeCount)
    }

    // ---- size estimates ----

    private fun done(id: Int, bytes: Long, durationMs: Long, transcoded: Boolean = false) =
        DownloadItem("S::$id", "T", "A", "Al", state = DownloadState.COMPLETE, sourceBytes = bytes, durationMs = durationMs, transcoded = transcoded)

    @Test
    fun aSongAtAChosenBitrateCostsDurationTimesKbpsOverEight() {
        // 4 minutes at 160 kbps = 240 s * 20_000 B/s = 4_800_000 B.
        assertEquals(4_800_000L, DownloadEstimate.songBytes(DownloadQuality.OPUS_160, 240_000))
        assertEquals(6_000_000L, DownloadEstimate.songBytes(DownloadQuality.OPUS_192, 250_000))
        assertEquals(0L, DownloadEstimate.songBytes(DownloadQuality.OPUS_160, 0))
        assertEquals(1_200_000L, DownloadQuality.OPUS_160.bytesPerMinute)
    }

    @Test
    fun aPlaylistEstimateUsesTheChosenBitrateAndTheAverageDurationNotTheAverageFileSize() {
        val items = (1..6).map { done(it, bytes = 40_000_000, durationMs = 300_000) } // FLAC, 5 minutes each
        // 300 s * 160_000 / 8 = 6_000_000 per song, whatever the files on disk weigh.
        assertEquals(10 * 6_000_000L, DownloadEstimate.playlistBytes(DownloadQuality.OPUS_160, 10, items))
        assertEquals(10 * 4_800_000L, DownloadEstimate.playlistBytes(DownloadQuality.OPUS_128, 10, items))
    }

    @Test
    fun withTooFewSamplesTheEstimateAssumesAFourMinuteSong() {
        val items = listOf(done(1, 40_000_000, 600_000))
        assertEquals(DownloadEstimate.FALLBACK_SONG_MS, DownloadEstimate.averageDurationMs(items))
        assertEquals(DownloadEstimate.FALLBACK_SONG_MS, DownloadEstimate.averageDurationMs(emptyList()))
        assertEquals(3 * 4_800_000L, DownloadEstimate.playlistBytes(DownloadQuality.OPUS_160, 3, items))
    }

    @Test
    fun anOriginalEstimateUsesTheMeasuredAverageOfOriginalsOnly() {
        val originals = (1..5).map { done(it, 40_000_000, 300_000) }
        val opus = (6..20).map { done(it, 5_000_000, 300_000, transcoded = true) }
        assertEquals(40_000_000L, DownloadEstimate.averageOriginalBytes(originals + opus))
        assertEquals(2 * 40_000_000L, DownloadEstimate.playlistBytes(DownloadQuality.ORIGINAL, 2, originals + opus))
        // Only transcodes downloaded so far: no original to measure, so duration at ~900 kbps stands in.
        assertNull(DownloadEstimate.averageOriginalBytes(opus))
        assertEquals(300_000L * DownloadEstimate.LOSSLESS_KBPS / 8, DownloadEstimate.playlistBytes(DownloadQuality.ORIGINAL, 1, opus))
    }
}
