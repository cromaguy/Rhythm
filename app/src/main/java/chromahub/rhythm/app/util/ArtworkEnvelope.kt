// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.util

import java.io.*
import java.security.MessageDigest

/** Portable, lossless artwork container. Not encryption: no device key can strand a backup.
 * Gallery scanners see neither an image extension, image MIME nor an image header. */
object ArtworkEnvelope {
    const val EXTENSION = ".t2art"
    const val MAX_BYTES = 64L * 1024 * 1024
    private val magic = byteArrayOf(84, 50, 65, 82, 84, 13, 10, 1)
    fun isImage(file: File): Boolean = runCatching {
        val header = ByteArray(12)
        file.inputStream().use { it.read(header) }
        isImage(header)
    }.getOrDefault(false)
    fun isImage(b: ByteArray): Boolean = b.size >= 12 && (
        b[0] == 0xff.toByte() && b[1] == 0xd8.toByte() ||
            b[0] == 0x89.toByte() && String(b, 1, 3, Charsets.US_ASCII) == "PNG" ||
            String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" ||
            String(b, 0, 3, Charsets.US_ASCII) == "GIF")

    fun wrap(source: File, destination: File) {
        require(source.length() in 12..MAX_BYTES && isImage(source))
        val hash = source.inputStream().use(::digest)
        DataOutputStream(destination.outputStream().buffered()).use { out ->
            out.write(magic); out.writeLong(source.length()); out.write(hash)
            source.inputStream().use { it.copyTo(out, 32 * 1024) }
        }
        destination.inputStream().use { verify(it) }
    }
    fun unwrap(input: InputStream, destination: File) {
        DataInputStream(input.buffered()).use { data ->
            val header = ByteArray(magic.size); data.readFully(header); require(header.contentEquals(magic))
            val size = data.readLong(); require(size in 12..MAX_BYTES)
            val expected = ByteArray(32); data.readFully(expected)
            val hash = MessageDigest.getInstance("SHA-256")
            destination.outputStream().buffered().use { out ->
                val buffer = ByteArray(32 * 1024); var copied = 0L
                while (copied < size) {
                    val n = data.read(buffer, 0, minOf(buffer.size.toLong(), size - copied).toInt())
                    require(n > 0) { "Incomplete artwork" }
                    out.write(buffer, 0, n); hash.update(buffer, 0, n); copied += n
                }
                require(data.read() == -1 && hash.digest().contentEquals(expected)) { "Artwork checksum mismatch" }
            }
        }
        require(isImage(destination))
    }
    fun verify(input: InputStream) {
        val data = DataInputStream(input.buffered())
        val header = ByteArray(magic.size); data.readFully(header); require(header.contentEquals(magic))
        val size = data.readLong(); require(size in 12..MAX_BYTES)
        val expected = ByteArray(32); data.readFully(expected)
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32 * 1024); var count = 0L
        while (count < size) {
            val n = data.read(buffer, 0, minOf(buffer.size.toLong(), size - count).toInt()); require(n > 0)
            hash.update(buffer, 0, n); count += n
        }
        require(data.read() == -1 && hash.digest().contentEquals(expected))
    }
    fun matches(input: InputStream, expected: File): Boolean = MessageDigest.isEqual(digest(input), expected.inputStream().use(::digest))

    private fun digest(input: InputStream): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        return hash.digest()
    }
}
