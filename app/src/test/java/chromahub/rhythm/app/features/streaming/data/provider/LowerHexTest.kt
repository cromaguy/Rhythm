package chromahub.rhythm.app.features.streaming.data.provider

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class LowerHexTest {

    private fun formatHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun matchesStringFormatForAllByteValues() {
        val all = ByteArray(256) { it.toByte() }
        assertEquals(formatHex(all), all.toLowerHex())
    }

    @Test
    fun md5OfKnownInputMatchesSubsonicSpecExample() {
        // Subsonic API docs: password "sesame" + salt "c19b2d" -> token 26719a1196d2a940705a59634eb18eab
        val digest = MessageDigest.getInstance("MD5").digest("sesamec19b2d".toByteArray(Charsets.UTF_8))
        assertEquals("26719a1196d2a940705a59634eb18eab", digest.toLowerHex())
    }

    @Test
    fun emptyInputGivesEmptyString() {
        assertEquals("", ByteArray(0).toLowerHex())
    }
}
