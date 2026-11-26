/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic envelope and path-containment helpers for Rhythm v5 modular backups.
 *
 * Uses PBKDF2-HMAC-SHA256 (210,000 iterations) + AES-256-GCM so encrypted `.rhythm` / `.json`
 * backups remain portable across devices (not bound to one phone's Android Keystore).
 */
object BackupCrypto {

    const val ENVELOPE_FORMAT = "RHYTHM_ENC_V1"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()

    fun isEncryptedEnvelope(payload: String): Boolean {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("{")) return false
        return try {
            val json = JSONObject(trimmed)
            json.optString("rhythm_backup_format") == ENVELOPE_FORMAT &&
                json.has("salt") &&
                json.has("iv") &&
                json.has("ciphertext")
        } catch (_: Exception) {
            false
        }
    }

    fun encryptPayload(plaintextJson: String, passphrase: String): String =
        encryptPayloadWithIterations(plaintextJson, passphrase, PBKDF2_ITERATIONS)

    internal fun encryptPayloadWithIterations(
        plaintextJson: String,
        passphrase: String,
        iterations: Int
    ): String {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        val boundedIterations = iterations.coerceIn(10_000, 1_000_000)
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveAesKey(passphrase.toCharArray(), salt, boundedIterations)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(ENVELOPE_FORMAT.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(StandardCharsets.UTF_8))

        val encoder = Base64.getEncoder()
        return JSONObject().apply {
            put("rhythm_backup_format", ENVELOPE_FORMAT)
            put("kdf", KDF_ALGORITHM)
            put("iterations", boundedIterations)
            put("salt", encoder.encodeToString(salt))
            put("iv", encoder.encodeToString(iv))
            put("ciphertext", encoder.encodeToString(ciphertext))
        }.toString()
    }

    fun decryptPayload(envelopeJson: String, passphrase: String): String {
        require(passphrase.isNotEmpty()) { "Passphrase is required to decrypt this backup" }
        val json = JSONObject(envelopeJson.trim())
        require(json.optString("rhythm_backup_format") == ENVELOPE_FORMAT) {
            "Unsupported encrypted backup format"
        }
        val iterations = json.optInt("iterations", PBKDF2_ITERATIONS)
            .coerceIn(10_000, 1_000_000)
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(json.getString("salt"))
        val iv = decoder.decode(json.getString("iv"))
        val ciphertext = decoder.decode(json.getString("ciphertext"))

        val key = deriveAesKey(passphrase.toCharArray(), salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(ENVELOPE_FORMAT.toByteArray(StandardCharsets.UTF_8))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    /**
     * Validates that [untrustedEntryName] stays strictly inside [baseDir] after canonicalization,
     * blocking `../` zip-slip and separator traversal attacks.
     */
    fun sanitizeChildPath(baseDir: File, untrustedEntryName: String): File? {
        if (untrustedEntryName.isBlank() ||
            untrustedEntryName.contains('\u0000') ||
            untrustedEntryName.contains("..") ||
            untrustedEntryName.startsWith("/") ||
            untrustedEntryName.startsWith("\\")
        ) {
            return null
        }
        return try {
            val baseCanonical = baseDir.canonicalFile
            val candidate = File(baseCanonical, untrustedEntryName).canonicalFile
            val basePath = baseCanonical.toPath()
            val candidatePath = candidate.toPath()
            if (candidatePath.startsWith(basePath) && candidatePath != basePath) {
                candidate
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeSingleSegmentFilename(name: String, fallback: String = "item"): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace("..", "_")
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .trim()
        return base.ifBlank { fallback }
    }

    private fun deriveAesKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
