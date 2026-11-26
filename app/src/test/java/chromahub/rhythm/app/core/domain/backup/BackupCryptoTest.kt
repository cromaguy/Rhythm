/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupCryptoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun encryptAndDecrypt_roundTripsPlaintextJsonWithPassphrase() {
        val plaintext = """{"backup_version":5,"preferences":{"app_mode":"LOCAL"},"note":"unicode 🎵"}"""
        val envelope = BackupCrypto.encryptPayload(plaintext, "correct-horse-battery-staple")

        assertTrue(BackupCrypto.isEncryptedEnvelope(envelope))
        assertFalse(BackupCrypto.isEncryptedEnvelope(plaintext))
        assertEquals(plaintext, BackupCrypto.decryptPayload(envelope, "correct-horse-battery-staple"))
    }

    @Test
    fun decrypt_failsWithWrongPassphraseOrTamperedCiphertext() {
        val envelope = BackupCrypto.encryptPayload("""{"backup_version":5}""", "right-pass")

        val wrongPassFailed = runCatching { BackupCrypto.decryptPayload(envelope, "wrong-pass") }.isFailure
        assertTrue(wrongPassFailed)

        val tampered = envelope.replace("RHYTHM_ENC_V1", "TAMPERED_FMT")
        val tamperedFailed = runCatching { BackupCrypto.decryptPayload(tampered, "right-pass") }.isFailure
        assertTrue(tamperedFailed)
    }

    @Test
    fun sanitizeChildPath_blocksZipSlipAndTraversalAttacks() {
        val baseDir = tempFolder.newFolder("artist_images")

        val safe = BackupCrypto.sanitizeChildPath(baseDir, "artist_daft_punk.jpg")
        assertNotNull(safe)
        assertEquals(File(baseDir, "artist_daft_punk.jpg").canonicalPath, safe?.canonicalPath)

        assertNull(BackupCrypto.sanitizeChildPath(baseDir, "../outside.jpg"))
        assertNull(BackupCrypto.sanitizeChildPath(baseDir, "../../etc/passwd"))
        assertNull(BackupCrypto.sanitizeChildPath(baseDir, "/absolute/path.jpg"))
        assertNull(BackupCrypto.sanitizeChildPath(baseDir, "sub\\..\\escape.jpg"))
        assertNull(BackupCrypto.sanitizeChildPath(baseDir, "bad\u0000name.jpg"))
        assertNull(BackupCrypto.sanitizeChildPath(baseDir, ""))
    }
}
