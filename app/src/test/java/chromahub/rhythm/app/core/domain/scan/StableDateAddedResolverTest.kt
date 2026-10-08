/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.scan

import android.content.SharedPreferences
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class StableDateAddedResolverTest {

    private lateinit var mockPrefs: MockPreferences
    private lateinit var resolver: StableDateAddedResolver

    @Before
    fun setUp() {
        mockPrefs = MockPreferences()
        resolver = StableDateAddedResolver(mockPrefs)
    }

    private fun createSong(
        id: String,
        title: String = "Test Title",
        artist: String = "Test Artist",
        album: String = "Test Album",
        path: String? = "/storage/emulated/0/Music/song.flac",
        duration: Long = 200_000L,
        dateAdded: Long = 1_600_000_000_000L
    ): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = "album_$id",
            duration = duration,
            uri = "content://media/external/audio/media/$id",
            artworkUri = null,
            trackNumber = 1,
            year = 2020,
            genre = "Rock",
            dateAdded = dateAdded,
            dateModified = dateAdded,
            albumArtist = null,
            bitrate = null,
            sampleRate = null,
            channels = null,
            codec = "FLAC",
            discNumber = 1,
            path = path
        )
    }

    @Test
    fun testResolveDateAdded_brandNewSong_returnsObservedAndPersistsOnFlush() {
        val observed = 1_700_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "101",
            filePath = "/storage/emulated/0/Music/Artist/NewSong.flac",
            title = "New Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            observedDateAddedMs = observed
        )

        assertEquals(observed, resolved)

        resolver.flush()
        val pathKey = StableDateAddedResolver.pathKey(
            StableDateAddedResolver.normalizePath("/storage/emulated/0/Music/Artist/NewSong.flac")
        )
        assertEquals(observed, mockPrefs.getLong(pathKey, -1L))
    }

    @Test
    fun testResolveDateAdded_recreatedFileSamePath_preservesHistoricalDateFromCache() {
        val historicalDate = 1_600_000_000_000L
        val path = "/storage/emulated/0/Music/Artist/Recreated.flac"
        val pathKey = StableDateAddedResolver.pathKey(StableDateAddedResolver.normalizePath(path))

        mockPrefs.storage[pathKey] = historicalDate

        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "202",
            filePath = path,
            title = "Recreated",
            artist = "Artist",
            album = "Album",
            durationMs = 210_000L,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(historicalDate, resolved)
    }

    @Test
    fun testResolveDateAdded_recreatedFileSamePath_preservesHistoricalDateFromDbSnapshot() {
        val historicalDate = 1_600_000_000_000L
        val path = "/storage/emulated/0/Music/Syncthing/Track.flac"
        val oldSong = createSong(id = "10", path = path, dateAdded = historicalDate)

        resolver.prepareForScan(listOf(oldSong))

        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "99",
            filePath = path,
            title = oldSong.title,
            artist = oldSong.artist,
            album = oldSong.album,
            durationMs = oldSong.duration,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(historicalDate, resolved)
    }

    @Test
    fun testResolveDateAdded_relocatedFile_preservesDateByFullMetadata() {
        val historicalDate = 1_550_000_000_000L
        val oldSong = createSong(
            id = "1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            path = "/storage/emulated/0/Music/Unsorted/track1.flac",
            duration = 354_000L,
            dateAdded = historicalDate
        )

        resolver.prepareForScan(listOf(oldSong))

        val newPath = "/storage/emulated/0/Music/Queen/A Night at the Opera/04 Bohemian Rhapsody.flac"
        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "50",
            filePath = newPath,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationMs = 354_000L,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(historicalDate, resolved)
    }

    @Test
    fun testResolveDateAdded_relocatedFile_retaggedAlbum_preservesDateByCoreMetadata() {
        val historicalDate = 1_550_000_000_000L
        val oldSong = createSong(
            id = "1",
            title = "Comfortably Numb",
            artist = "Pink Floyd",
            album = "The Wall",
            path = "/storage/emulated/0/Music/Old/Comfortably Numb.mp3",
            duration = 382_000L,
            dateAdded = historicalDate
        )

        resolver.prepareForScan(listOf(oldSong))

        val newPath = "/storage/emulated/0/Music/Pink Floyd/The Wall (Deluxe)/06 Comfortably Numb.mp3"
        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "88",
            filePath = newPath,
            title = "Comfortably Numb",
            artist = "Pink Floyd",
            album = "The Wall (Deluxe Edition)",
            durationMs = 382_000L,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(historicalDate, resolved)
    }

    @Test
    fun testResolveDateAdded_relocatedFile_distinctiveFilenameAndDuration() {
        val historicalDate = 1_550_000_000_000L
        val oldSong = createSong(
            id = "1",
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            path = "/storage/emulated/0/Download/01 - Hotel California.flac",
            duration = 391_000L,
            dateAdded = historicalDate
        )

        resolver.prepareForScan(listOf(oldSong))

        val newPath = "/storage/emulated/0/Music/Eagles/01 - Hotel California.flac"
        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "99",
            filePath = newPath,
            title = "Unknown Title",
            artist = "Unknown Artist",
            album = "Unknown Album",
            durationMs = 391_000L,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(historicalDate, resolved)
    }

    @Test
    fun testResolveDateAdded_genericFilename_doesNotFalseMatchDifferentSong() {
        val historicalDate = 1_550_000_000_000L
        val oldSong = createSong(
            id = "1",
            title = "Track A",
            artist = "Artist A",
            album = "Album A",
            path = "/storage/emulated/0/Music/AlbumA/01.mp3",
            duration = 180_000L,
            dateAdded = historicalDate
        )

        resolver.prepareForScan(listOf(oldSong))

        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "2",
            filePath = "/storage/emulated/0/Music/AlbumB/01.mp3",
            title = "Track B",
            artist = "Artist B",
            album = "Album B",
            durationMs = 180_000L,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(newObservedDate, resolved)
    }

    @Test
    fun testResolveDateAdded_wholeLibraryRelocation_matchesByRelativePath() {
        val historicalDate = 1_550_000_000_000L
        val oldSong = createSong(
            id = "1",
            title = "Stairway to Heaven",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            path = "/storage/emulated/0/Music/Led Zeppelin IV/04 - Stairway.flac",
            duration = 482_000L,
            dateAdded = historicalDate
        )

        resolver.prepareForScan(listOf(oldSong))

        val newPath = "/storage/0000-0000/Music/Led Zeppelin IV/04 - Stairway.flac"
        val newObservedDate = 1_760_000_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "300",
            filePath = newPath,
            title = "Stairway to Heaven",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            durationMs = 482_000L,
            observedDateAddedMs = newObservedDate
        )

        assertEquals(historicalDate, resolved)
    }

    @Test
    fun testResolveDateAdded_normalizesSecondsToMilliseconds() {
        val observedInSeconds = 1_600_000_000L
        val resolved = resolver.resolveDateAdded(
            songId = "1",
            filePath = "/storage/emulated/0/Music/song.mp3",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L,
            observedDateAddedMs = observedInSeconds
        )

        assertEquals(1_600_000_000_000L, resolved)
    }

    @Test
    fun testResolveDateAdded_backwardCompatibility_readsExistingPathKeys() {
        val path = "/storage/emulated/0/Music/legacy_track.flac"
        val legacyKey = StableDateAddedResolver.pathKey(StableDateAddedResolver.normalizePath(path))
        val legacyTimestamp = 1_500_000_000_000L

        mockPrefs.storage[legacyKey] = legacyTimestamp

        val resolved = resolver.resolveDateAdded(
            songId = "55",
            filePath = path,
            title = "Legacy Track",
            artist = "Artist",
            album = "Album",
            durationMs = 240_000L,
            observedDateAddedMs = 1_760_000_000_000L
        )

        assertEquals(legacyTimestamp, resolved)
    }

    @Test
    fun testResolveDateAdded_multipleRelocatedSongs_claimsUniquely() {
        val date1 = 1_500_000_000_000L
        val date2 = 1_550_000_000_000L

        val song1 = createSong(id = "1", title = "Track", artist = "Artist", album = "Album 1", duration = 200_000L, path = "/Music/A/01.flac", dateAdded = date1)
        val song2 = createSong(id = "2", title = "Track", artist = "Artist", album = "Album 2", duration = 200_000L, path = "/Music/B/01.flac", dateAdded = date2)

        resolver.prepareForScan(listOf(song1, song2))

        val resolvedNew1 = resolver.resolveDateAdded(
            songId = "101",
            filePath = "/Music/NewA/01.flac",
            title = "Track",
            artist = "Artist",
            album = "Album 1",
            durationMs = 200_000L,
            observedDateAddedMs = 1_760_000_000_000L
        )
        assertEquals(date1, resolvedNew1)

        val resolvedNew2 = resolver.resolveDateAdded(
            songId = "102",
            filePath = "/Music/NewB/01.flac",
            title = "Track",
            artist = "Artist",
            album = "Album 2",
            durationMs = 200_000L,
            observedDateAddedMs = 1_760_000_000_000L
        )
        assertEquals(date2, resolvedNew2)
    }

    @Test
    fun testNormalizeTimestamp_handlesUnitsAndBounds() {
        assertEquals(0L, StableDateAddedResolver.normalizeTimestamp(0L))
        assertEquals(0L, StableDateAddedResolver.normalizeTimestamp(-100L))

        assertEquals(1_600_000_000_000L, StableDateAddedResolver.normalizeTimestamp(1_600_000_000L))
        assertEquals(1_600_000_000_000L, StableDateAddedResolver.normalizeTimestamp(1_600_000_000_000L))
        assertEquals(1_600_000_000_000L, StableDateAddedResolver.normalizeTimestamp(1_600_000_000_000_000L))

        val farFuture = System.currentTimeMillis() + 100_000_000_000L
        val clamped = StableDateAddedResolver.normalizeTimestamp(farFuture)
        assertTrue(clamped <= System.currentTimeMillis())
        assertTrue(clamped > 0L)
    }

    @Test
    fun testIsUnknownTitle_identifiesPlaceholders() {
        assertTrue(StableDateAddedResolver.isUnknownTitle(""))
        assertTrue(StableDateAddedResolver.isUnknownTitle("   "))
        assertTrue(StableDateAddedResolver.isUnknownTitle("<unknown>"))
        assertTrue(StableDateAddedResolver.isUnknownTitle("Unknown"))
        assertTrue(StableDateAddedResolver.isUnknownTitle("unknown title"))
        assertTrue(StableDateAddedResolver.isUnknownTitle("untitled"))
        assertTrue(StableDateAddedResolver.isUnknownTitle("-"))

        assertFalse(StableDateAddedResolver.isUnknownTitle("Imagine"))
    }

    @Test
    fun testIsGenericAudioFilename_detectsGenericNames() {
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("01.mp3"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("12.flac"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("track01.m4a"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("track 1.mp3"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("audio.wav"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("sound_01.mp3"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("untitled.flac"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("cd1_01.mp3"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("disc 02.flac"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("1-01.mp3"))
        assertTrue(StableDateAddedResolver.isGenericAudioFilename("01_02.flac"))

        assertFalse(StableDateAddedResolver.isGenericAudioFilename("01 - Bohemian Rhapsody.flac"))
        assertFalse(StableDateAddedResolver.isGenericAudioFilename("hotel_california.mp3"))
        assertFalse(StableDateAddedResolver.isGenericAudioFilename("yesterday.flac"))
    }

    private class MockPreferences : SharedPreferences {
        val storage = ConcurrentHashMap<String, Any>()

        override fun getAll(): Map<String, *> = storage
        override fun getString(key: String, defValue: String?) = storage[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) =
            @Suppress("UNCHECKED_CAST") (storage[key] as? Set<String> ?: defValues)
        override fun getInt(key: String, defValue: Int) = storage[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = storage[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = storage[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = storage[key] as? Boolean ?: defValue
        override fun contains(key: String) = storage.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun edit(): SharedPreferences.Editor = MockEditor(storage)
    }

    private class MockEditor(private val storage: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val modifications = HashMap<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            modifications[key] = values
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            modifications[key] = this
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearRequested) storage.clear()
            modifications.forEach { (k, v) ->
                if (v === this) storage.remove(k)
                else if (v != null) storage[k] = v
                else storage.remove(k)
            }
        }
    }
}
