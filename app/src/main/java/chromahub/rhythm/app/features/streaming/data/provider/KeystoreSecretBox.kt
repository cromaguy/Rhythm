/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstracts authenticated AES-GCM cipher creation so secret storage can be
 * backed by AndroidKeyStore on devices and tested deterministically on the JVM.
 */
internal interface SecretCipherBackend {
    fun getOrCreateSecretKey(alias: String): SecretKey
}

private object AndroidKeyStoreCipherBackend : SecretCipherBackend {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    override fun getOrCreateSecretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

/**
 * Pure-Android Keystore AES-256-GCM secret helper for streaming client tokens and passwords.
 *
 * Values stored in [SharedPreferences] carry an explicit `enc:v1:` prefix containing a
 * 12-byte GCM IV concatenated with ciphertext + 128-bit authentication tag. Legacy plaintext
 * values without the prefix are transparently re-encrypted in place on first read.
 */
internal class KeystoreSecretBox(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val backend: SecretCipherBackend = AndroidKeyStoreCipherBackend
) {
    companion object {
        private const val TAG = "KeystoreSecretBox"
        private const val DEFAULT_KEY_ALIAS = "rhythm_streaming_credentials_v1"
        private const val PREFIX_V1 = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }

    fun isEncrypted(storedValue: String?): Boolean {
        return !storedValue.isNullOrBlank() && storedValue.startsWith(PREFIX_V1)
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val secretKey = backend.getOrCreateSecretKey(keyAlias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            require(iv.size == IV_LENGTH_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            PREFIX_V1 + Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt credential with Keystore AES-GCM", e)
            plaintext
        }
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isEmpty()) return ""
        if (!storedValue.startsWith(PREFIX_V1)) {
            return storedValue
        }
        return try {
            val payload = Base64.getDecoder().decode(storedValue.removePrefix(PREFIX_V1))
            if (payload.size <= IV_LENGTH_BYTES) {
                return ""
            }
            val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
            val secretKey = backend.getOrCreateSecretKey(keyAlias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt Keystore AES-GCM credential", e)
            ""
        }
    }

    /**
     * Reads a credential from [prefs] and transparently upgrades legacy plaintext
     * entries to Keystore-backed AES-GCM in place.
     */
    fun readAndMigrate(prefs: SharedPreferences, key: String): String {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return ""
        if (raw.startsWith(PREFIX_V1)) {
            return decrypt(raw)
        }
        val encrypted = encrypt(raw)
        if (encrypted.startsWith(PREFIX_V1)) {
            prefs.edit { putString(key, encrypted) }
        }
        return raw
    }
}
