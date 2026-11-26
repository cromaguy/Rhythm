/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles portable `.rhythm` ZIP backup containers (`manifest.json` + `backup.json` with
 * SHA-256 payload digest verification) while remaining 100% backward-compatible with raw JSON
 * and encrypted v1 envelope payloads.
 */
object BackupArchive {

    const val MANIFEST_ENTRY = "manifest.json"
    const val PAYLOAD_ENTRY = "backup.json"
    private const val ARCHIVE_VERSION = 1
    private const val ARCHIVE_FORMAT = "rhythm_backup_zip"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) {
                val hex = (b.toInt() and 0xFF).toString(16)
                if (hex.length == 1) append('0')
                append(hex)
            }
        }
    }

    fun isZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    fun writeArchive(payload: String, outputStream: OutputStream) {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val manifest = JSONObject().apply {
            put("archive_version", ARCHIVE_VERSION)
            put("format", ARCHIVE_FORMAT)
            put("created_at", System.currentTimeMillis())
            put("payload_entry", PAYLOAD_ENTRY)
            put("sha256", sha256Hex(payloadBytes))
            put("encrypted", BackupCrypto.isEncryptedEnvelope(payload))
        }
        val manifestBytes = manifest.toString(2).toByteArray(StandardCharsets.UTF_8)

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PAYLOAD_ENTRY))
            zip.write(payloadBytes)
            zip.closeEntry()
            zip.finish()
        }
    }

    fun createArchiveBytes(payload: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeArchive(payload, out)
        return out.toByteArray()
    }

    fun readPayload(rawBytes: ByteArray): String {
        if (!isZipArchive(rawBytes)) {
            return String(rawBytes, StandardCharsets.UTF_8)
        }

        var manifestJson: String? = null
        val entries = HashMap<String, ByteArray>(4)

        ZipInputStream(ByteArrayInputStream(rawBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!entry.isDirectory && isSafeSingleSegmentEntry(name)) {
                    entries[name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }

        manifestJson = entries[MANIFEST_ENTRY]?.let { String(it, StandardCharsets.UTF_8) }
            ?: throw SecurityException("Backup archive missing $MANIFEST_ENTRY")

        val manifest = JSONObject(manifestJson)
        val payloadEntryName = manifest.optString("payload_entry", PAYLOAD_ENTRY)
        if (!isSafeSingleSegmentEntry(payloadEntryName)) {
            throw SecurityException("Unsafe payload entry path: $payloadEntryName")
        }

        val payloadBytes = entries[payloadEntryName]
            ?: throw SecurityException("Backup archive missing $payloadEntryName")

        val expectedHash = manifest.optString("sha256", "").trim().lowercase()
        if (expectedHash.length != 64) {
            throw SecurityException("Backup manifest missing valid SHA-256 digest")
        }
        val actualHash = sha256Hex(payloadBytes).lowercase()
        val hashesEqual = MessageDigest.isEqual(
            expectedHash.toByteArray(StandardCharsets.US_ASCII),
            actualHash.toByteArray(StandardCharsets.US_ASCII)
        )
        if (!hashesEqual) {
            throw SecurityException("Backup integrity check (SHA-256) failed")
        }

        return String(payloadBytes, StandardCharsets.UTF_8)
    }

    fun readPayload(inputStream: InputStream): String = readPayload(inputStream.readBytes())

    private fun isSafeSingleSegmentEntry(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains("..") || name.contains('/') || name.contains('\\')) return false
        return true
    }
}
