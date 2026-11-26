/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import chromahub.rhythm.app.infrastructure.worker.M3uSyncWorker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupArchiveAndSyncTest {

    @Test
    fun `encryptPayload uses 210k PBKDF2 iterations and decrypts legacy 150k envelopes`() {
        val sample = """{"backup_version":5,"preferences":{"dark_mode":true}}"""
        val encrypted = BackupCrypto.encryptPayload(sample, "correct-horse-battery-staple")
        val envelope = JSONObject(encrypted)
        assertEquals(210_000, envelope.getInt("iterations"))
        assertEquals(sample, BackupCrypto.decryptPayload(encrypted, "correct-horse-battery-staple"))

        val legacy150k = BackupCrypto.encryptPayloadWithIterations(sample, "legacy-passphrase", 150_000)
        assertEquals(150_000, JSONObject(legacy150k).getInt("iterations"))
        assertEquals(sample, BackupCrypto.decryptPayload(legacy150k, "legacy-passphrase"))
    }

    @Test
    fun `BackupArchive round-trips payload and rejects tampered SHA-256`() {
        val payload = """{"backup_version":5,"preferences":{"unified_library_enabled":true}}"""
        val zipBytes = BackupArchive.createArchiveBytes(payload)

        assertTrue(BackupArchive.isZipArchive(zipBytes))
        assertEquals(payload, BackupArchive.readPayload(zipBytes))
        assertEquals(payload, BackupArchive.readPayload(payload.toByteArray(StandardCharsets.UTF_8)))

        // Tamper payload entry while leaving manifest SHA-256 unchanged
        val tamperedOut = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zin ->
            ZipOutputStream(tamperedOut).use { zout ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    zout.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == BackupArchive.PAYLOAD_ENTRY) {
                        zout.write("""{"backup_version":5,"preferences":{"unified_library_enabled":false}}""".toByteArray(StandardCharsets.UTF_8))
                    } else {
                        zout.write(zin.readBytes())
                    }
                    zout.closeEntry()
                }
            }
        }

        var rejected = false
        try {
            BackupArchive.readPayload(tamperedOut.toByteArray())
        } catch (_: SecurityException) {
            rejected = true
        }
        assertTrue("Tampered .rhythm archive must fail SHA-256 verification", rejected)
    }

    @Test
    fun `M3uSyncWorker decides bidirectional sync direction from timestamps`() {
        assertEquals(
            M3uSyncWorker.M3uSyncAction.EXPORT_TO_DOCUMENT,
            M3uSyncWorker.decideSyncAction(localModifiedMs = 10_000L, remoteModifiedMs = 0L, remoteExists = false)
        )
        assertEquals(
            M3uSyncWorker.M3uSyncAction.IMPORT_FROM_DOCUMENT,
            M3uSyncWorker.decideSyncAction(localModifiedMs = 10_000L, remoteModifiedMs = 20_000L, remoteExists = true)
        )
        assertEquals(
            M3uSyncWorker.M3uSyncAction.EXPORT_TO_DOCUMENT,
            M3uSyncWorker.decideSyncAction(localModifiedMs = 30_000L, remoteModifiedMs = 20_000L, remoteExists = true)
        )
        assertEquals(
            M3uSyncWorker.M3uSyncAction.IN_SYNC,
            M3uSyncWorker.decideSyncAction(localModifiedMs = 20_000L, remoteModifiedMs = 21_500L, remoteExists = true)
        )
        assertFalse(M3uSyncWorker.normalizeSyncKey("Road / Trip:Mix?").contains('/'))
        assertFalse(M3uSyncWorker.normalizeSyncKey("Road / Trip:Mix?").contains(':'))
        assertEquals(
            M3uSyncWorker.normalizeSyncKey("Road _ Trip_Mix_"),
            M3uSyncWorker.normalizeSyncKey("Road / Trip:Mix?")
        )
    }
}
