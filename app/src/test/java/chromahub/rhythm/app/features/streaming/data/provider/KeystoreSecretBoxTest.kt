/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class KeystoreSecretBoxTest {

    private class InMemoryAesBackend : SecretCipherBackend {
        private val keys = mutableMapOf<String, SecretKey>()
        override fun getOrCreateSecretKey(alias: String): SecretKey {
            return keys.getOrPut(alias) {
                val raw = ByteArray(32) { idx -> (idx * 7 + 13).toByte() }
                SecretKeySpec(raw, "AES")
            }
        }
    }

    private class MapSharedPreferences(
        initial: Map<String, String> = emptyMap()
    ) : SharedPreferences {
        val data = initial.toMutableMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) data.clear()
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                return true
            }
            override fun apply() { commit() }
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun encryptAndDecrypt_roundTripsWithRandomizedIv() {
        val box = KeystoreSecretBox(keyAlias = "test_alias", backend = InMemoryAesBackend())
        val secret = "s3cr3t-p@ssw0rd-with-utf8-日本語"

        val enc1 = box.encrypt(secret)
        val enc2 = box.encrypt(secret)

        assertTrue(box.isEncrypted(enc1))
        assertNotEquals("Different random IVs must produce distinct ciphertexts", enc1, enc2)
        assertEquals(secret, box.decrypt(enc1))
        assertEquals(secret, box.decrypt(enc2))
    }

    @Test
    fun readAndMigrate_upgradesPlaintextPreferenceInPlace() {
        val box = KeystoreSecretBox(keyAlias = "test_alias", backend = InMemoryAesBackend())
        val prefs = MapSharedPreferences(mapOf("access_token" to "legacy-plaintext-token"))

        assertFalse(box.isEncrypted(prefs.getString("access_token", null)))
        val firstRead = box.readAndMigrate(prefs, "access_token")
        assertEquals("legacy-plaintext-token", firstRead)

        val storedAfterRead = prefs.getString("access_token", null)
        assertTrue("Legacy secret must be migrated on disk", box.isEncrypted(storedAfterRead))
        assertEquals("legacy-plaintext-token", box.readAndMigrate(prefs, "access_token"))
    }

    @Test
    fun decrypt_tamperedCiphertextFailsSafelyWithoutThrowing() {
        val box = KeystoreSecretBox(keyAlias = "test_alias", backend = InMemoryAesBackend())
        assertEquals("", box.decrypt("enc:v1:!!!not-valid-base64!!!"))
        assertEquals("plain-value", box.decrypt("plain-value"))
    }
}
