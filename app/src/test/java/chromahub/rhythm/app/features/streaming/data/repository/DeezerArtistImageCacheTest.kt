package chromahub.rhythm.app.features.streaming.data.repository

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeezerArtistImageCacheTest {

    private var now = 1_000_000L
    private val clock = { now }

    private fun tempFile(): File =
        File(Files.createTempDirectory("deezer-cache").toFile(), "deezer_artist_images.json")

    @Test
    fun hitIsServedUntilTtlExpires() {
        val cache = DeezerArtistImageCache(null, clock, hitTtlMs = 100, missTtlMs = 10, failureTtlMs = 5)
        assertNull(cache.get("artist"))
        cache.put("artist", "https://img/1.jpg")
        now += 99
        assertEquals("https://img/1.jpg", cache.get("artist")?.imageUrl)
        now += 1
        assertNull(cache.get("artist"))
    }

    @Test
    fun missesAreCachedWithTheirOwnTtl() {
        val cache = DeezerArtistImageCache(null, clock, hitTtlMs = 100, missTtlMs = 10, failureTtlMs = 5)
        cache.put("unknown", null)
        now += 9
        val entry = cache.get("unknown")
        assertNotNull(entry)
        assertNull(entry!!.imageUrl)
        now += 1
        assertNull(cache.get("unknown"))
    }

    @Test
    fun failuresAreRetriedAfterBackoffAndNotPersisted() {
        val file = tempFile()
        val cache = DeezerArtistImageCache(file, clock, hitTtlMs = 100, missTtlMs = 10, failureTtlMs = 5)
        cache.markFailed("flaky")
        assertNotNull(cache.get("flaky"))
        cache.flush()
        assertNull(DeezerArtistImageCache(file, clock).get("flaky"))
        now += 5
        assertNull(cache.get("flaky"))
    }

    @Test
    fun entriesSurviveARestart() {
        val file = tempFile()
        val first = DeezerArtistImageCache(file, clock)
        first.put("a", "https://img/a.jpg")
        first.put("b", null)
        first.flush()

        val second = DeezerArtistImageCache(file, clock)
        assertEquals("https://img/a.jpg", second.get("a")?.imageUrl)
        assertNotNull(second.get("b"))
        assertNull(second.get("c"))
    }

    @Test
    fun corruptFileIsIgnored() {
        val file = tempFile().apply { writeText("{not json") }
        val cache = DeezerArtistImageCache(file, clock)
        assertNull(cache.get("a"))
        cache.put("a", "https://img/a.jpg")
        assertEquals("https://img/a.jpg", cache.get("a")?.imageUrl)
    }
}
