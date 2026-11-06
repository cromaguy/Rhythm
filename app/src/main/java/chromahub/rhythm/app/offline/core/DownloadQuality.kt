// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.util.Locale

/**
 * What an offline download is fetched as (offline cache, "Download quality"). [ORIGINAL] is the file as it
 * sits in the library (`download.view`, byte for byte); the others ask the server to transcode with
 * `stream.view?format=<format>&maxBitRate=<kbps>`, which Navidrome does with ffmpeg. This is the offline copy
 * only: playback streaming has its own "Streaming quality" setting.
 *
 * [id] is what is persisted. [format] is the Subsonic `format` value (null for [ORIGINAL]) and [kbps] the
 * `maxBitRate` (0 for [ORIGINAL]).
 */
enum class DownloadQuality(val id: String, val format: String?, val kbps: Int) {
    ORIGINAL("original", null, 0),
    OPUS_192("opus192", "opus", 192),
    OPUS_160("opus160", "opus", 160),
    OPUS_128("opus128", "opus", 128),
    AAC_256("aac256", "aac", 256),
    MP3_256("mp3256", "mp3", 256),
    ;

    /** True when the server is asked to re-encode. */
    val transcodes: Boolean get() = format != null

    /** The file extension a faithful answer gets (`.opus` for Ogg Opus), null for [ORIGINAL]. */
    val extension: String? get() = format?.let { ".$it" }

    /** Approximate size of a minute of audio, in bytes (0 for [ORIGINAL], whose size depends on the file). */
    val bytesPerMinute: Long get() = kbps * 1000L / 8 * 60

    companion object {
        /** Opus 160 kbps: near-transparent for music at roughly a quarter of FLAC's size. */
        val DEFAULT = OPUS_160

        fun fromId(id: String?): DownloadQuality = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** What the library knows about a song before it is downloaded (Subsonic `suffix`, `bitRate`, `channels`). */
data class DownloadSource(
    val codec: String? = null,
    /** In kbps, null when unknown. */
    val bitrateKbps: Int? = null,
    val channels: Int? = null,
)

/** The quality a song is actually fetched at, and why it differs from the setting when it does. */
data class EffectiveQuality(val quality: DownloadQuality, val reason: Reason) {
    enum class Reason { AS_SELECTED, ORIGINAL_SELECTED, MULTICHANNEL, ALREADY_SMALL }
}

/**
 * Which songs the "Download quality" setting leaves alone. Re-encoding would hurt more than it saves for:
 *  - multichannel files (E-AC-3 / Atmos beds, 5.1 FLAC): Opus is capped at 8 channels and MP3 at 2 by
 *    Navidrome;
 *  - lossy files already at or under the target bitrate: a second lossy pass would lower the quality
 *    and not make the file smaller. (A lossy file of unknown bitrate is kept too: no saving is provable.)
 * Everything else, lossless or a higher-bitrate lossy file, is transcoded to the selected quality.
 */
object DownloadQualityPolicy {

    private val LOSSLESS = setOf("flac", "alac", "wav", "wave", "aif", "aiff", "ape", "wv", "tta", "dsf", "dff", "mlp", "truehd")

    fun isLossless(codec: String?): Boolean = codec?.trim()?.lowercase(Locale.ROOT)?.removePrefix(".") in LOSSLESS

    fun effective(selected: DownloadQuality, source: DownloadSource): EffectiveQuality {
        if (!selected.transcodes) return EffectiveQuality(DownloadQuality.ORIGINAL, EffectiveQuality.Reason.ORIGINAL_SELECTED)
        if ((source.channels ?: 0) > 2) return EffectiveQuality(DownloadQuality.ORIGINAL, EffectiveQuality.Reason.MULTICHANNEL)
        val codec = source.codec?.trim()?.takeIf { it.isNotEmpty() }
        if (codec != null && !isLossless(codec)) {
            val bitrate = source.bitrateKbps ?: 0
            // m4a may be ALAC, but ALAC never runs at or under a lossy target; a known low bitrate means lossy.
            if (bitrate <= 0 || bitrate <= selected.kbps) {
                return EffectiveQuality(DownloadQuality.ORIGINAL, EffectiveQuality.Reason.ALREADY_SMALL)
            }
        }
        return EffectiveQuality(selected, EffectiveQuality.Reason.AS_SELECTED)
    }
}

/** The result of naming a finished download from the server's answer. */
data class ResolvedFormat(val extension: String, val matchedRequest: Boolean)

/**
 * Naming a download after what the server actually sent, not after what was asked for. Navidrome serves the
 * original when it has no transcoding for the format (or the file is already that format at that bitrate), and
 * a proxy may rewrite headers, so the answer is checked three ways: the `Content-Disposition` file name, the
 * `Content-Type`, and finally the first bytes of the file ([sniff]).
 */
object DownloadFormats {

    /** The extensions the player's resolvers look for (same list as [OfflineFiles.AUDIO_EXTENSIONS]). */
    private val AUDIO_EXTENSIONS = OfflineFiles.AUDIO_EXTENSIONS

    /** The extension for an original-file answer; unchanged behaviour of the repository's old `extractExtension`. */
    fun extensionFor(contentDisposition: String?, contentType: String?): String {
        dispositionExtension(contentDisposition)?.let { return it }
        mimeExtension(contentType)?.let { return it }
        return ".mp3"
    }

    /**
     * The extension for the answer to a request for [requested]. [ResolvedFormat.matchedRequest] is false when
     * the server sent something else (it ignored the transcode and sent the original, or a different codec).
     */
    fun resolve(requested: DownloadQuality, contentDisposition: String?, contentType: String?): ResolvedFormat {
        val format = requested.format ?: return ResolvedFormat(extensionFor(contentDisposition, contentType), true)
        val mime = normalizeMime(contentType)

        mime?.let { m -> expectedExtension(format, m)?.let { return ResolvedFormat(it, true) } }
        dispositionExtension(contentDisposition)?.let { return ResolvedFormat(it, matches(requested, it)) }
        mimeExtension(contentType)?.let { return ResolvedFormat(it, matches(requested, it)) }
        // The server said nothing usable (no type, octet-stream): trust the request; sniff() checks the bytes.
        return ResolvedFormat(requested.extension!!, true)
    }

    /**
     * The extension the first bytes of an audio file say it has, or null when they say nothing. [header] is the
     * first 12 bytes or more. Ogg is `.ogg` (Opus and Vorbis share the container; see [sameContainer]).
     */
    fun sniff(header: ByteArray): String? {
        fun at(i: Int) = if (i < header.size) header[i].toInt() and 0xFF else -1
        fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
        return when {
            ascii(0, "fLaC") -> ".flac"
            ascii(0, "OggS") -> ".ogg"
            ascii(0, "RIFF") && ascii(8, "WAVE") -> ".wav"
            ascii(4, "ftyp") -> ".m4a"
            ascii(0, "ID3") -> ".mp3"
            at(0) == 0xFF && (at(1) and 0xF6) == 0xF0 -> ".aac" // ADTS: sync 0xFFF, layer bits 00
            at(0) == 0xFF && (at(1) and 0xE0) == 0xE0 -> ".mp3" // MPEG audio frame sync
            else -> null
        }
    }

    /** True when two extensions are the same kind of file (`.opus` and `.ogg` are both Ogg; `.m4a` and `.mp4` both MP4). */
    fun sameContainer(a: String, b: String): Boolean = family(a) == family(b)

    /**
     * The extension to store a finished file under: [current] unless the file's bytes say it is a different
     * container (a server that mislabelled its answer), in which case the sniffed one.
     */
    fun correct(current: String, header: ByteArray): ResolvedFormat {
        val sniffed = sniff(header) ?: return ResolvedFormat(current, true)
        return if (sameContainer(current, sniffed)) ResolvedFormat(current, true) else ResolvedFormat(sniffed, false)
    }

    private fun family(extension: String): String = when (extension.lowercase(Locale.ROOT)) {
        ".opus", ".ogg", ".oga" -> "ogg"
        ".m4a", ".mp4" -> "mp4"
        else -> extension.lowercase(Locale.ROOT)
    }

    /**
     * True when a file named [extension] is what [requested] asks for. `.ogg` counts for Opus (an Ogg file; Opus and
     * Vorbis share the container, so only the container can be told from the name) and `.m4a` for AAC.
     */
    fun matches(requested: DownloadQuality, extension: String): Boolean = when (requested.format) {
        null -> true
        "opus" -> extension.lowercase(Locale.ROOT) in setOf(".opus", ".ogg", ".oga")
        "aac" -> extension.lowercase(Locale.ROOT) in setOf(".aac", ".m4a", ".mp4")
        else -> extension.equals(requested.extension, ignoreCase = true)
    }

    private fun normalizeMime(contentType: String?): String? =
        contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    /** The extension a faithful [format] answer gets for a server that sent [mime], or null if [mime] is another kind. */
    private fun expectedExtension(format: String, mime: String): String? = when (format) {
        "opus" -> ".opus".takeIf { mime in OPUS_MIMES }
        "aac" -> when (mime) {
            in AAC_ADTS_MIMES -> ".aac"
            in MP4_MIMES -> ".m4a"
            else -> null
        }
        "mp3" -> ".mp3".takeIf { mime in MP3_MIMES }
        else -> null
    }

    private val OPUS_MIMES = setOf("audio/ogg", "audio/opus", "application/ogg", "audio/x-opus+ogg", "audio/x-ogg")
    private val AAC_ADTS_MIMES = setOf("audio/aac", "audio/x-aac", "audio/aacp")
    private val MP4_MIMES = setOf("audio/mp4", "audio/x-m4a", "audio/m4a")
    private val MP3_MIMES = setOf("audio/mpeg", "audio/mp3", "audio/x-mp3")

    private fun dispositionExtension(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val filename = DISPOSITION_NAME.find(contentDisposition)?.groupValues?.get(1)?.trim()
        if (filename.isNullOrBlank() || !filename.contains('.')) return null
        val ext = "." + filename.substringAfterLast('.').lowercase(Locale.ROOT)
        return ext.takeIf { it in AUDIO_EXTENSIONS }
    }

    /** The extension a (non-generic) audio type stands for; null for a missing, generic or unknown type. */
    private fun mimeExtension(contentType: String?): String? = when (normalizeMime(contentType)) {
        null -> null
        "audio/flac", "audio/x-flac" -> ".flac"
        "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac", "audio/x-aac" -> ".m4a"
        "audio/ogg", "audio/vorbis", "application/ogg" -> ".ogg"
        "audio/opus" -> ".opus"
        "audio/wav", "audio/x-wav", "audio/wave" -> ".wav"
        "audio/webm" -> ".webm"
        "audio/mpeg", "audio/mp3" -> ".mp3"
        else -> null
    }

    private val DISPOSITION_NAME = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";\n]+)['"]?""", RegexOption.IGNORE_CASE)
}

/**
 * The size numbers the Keep-playlists screen shows before anything is downloaded. A transcoded song's size is
 * its duration times the chosen bitrate; only [DownloadQuality.ORIGINAL] needs a measured average.
 */
object DownloadEstimate {

    /** A typical song when no downloaded song has said how long songs here are. */
    const val FALLBACK_SONG_MS = 4L * 60_000L

    /** Lossless audio at about 900 kbps (FLAC of CD-quality music), for an original with nothing to measure. */
    const val LOSSLESS_KBPS = 900

    /** Songs needed before their average is trusted over the fallback. */
    const val MIN_SAMPLES = 5

    fun songBytes(quality: DownloadQuality, durationMs: Long): Long {
        val kbps = if (quality.transcodes) quality.kbps else LOSSLESS_KBPS
        return durationMs.coerceAtLeast(0) * kbps / 8
    }

    /** The mean duration of the songs in the ledger that have one, or [FALLBACK_SONG_MS]. */
    fun averageDurationMs(items: List<DownloadItem>): Long {
        val durations = items.filter { it.durationMs > 0 }.map { it.durationMs }
        return if (durations.size >= MIN_SAMPLES) durations.average().toLong() else FALLBACK_SONG_MS
    }

    /** The mean size of the originals (not transcoded copies) downloaded so far, or null until there are enough. */
    fun averageOriginalBytes(items: List<DownloadItem>): Long? {
        val sizes = items.filter { it.state == DownloadState.COMPLETE && !it.transcoded && it.sourceBytes > 0 }.map { it.sourceBytes }
        return if (sizes.size >= MIN_SAMPLES) sizes.average().toLong() else null
    }

    /** About how many bytes [songCount] songs take at [quality]. */
    fun playlistBytes(quality: DownloadQuality, songCount: Int, items: List<DownloadItem>): Long {
        val perSong = if (quality.transcodes) {
            songBytes(quality, averageDurationMs(items))
        } else {
            averageOriginalBytes(items) ?: songBytes(quality, averageDurationMs(items))
        }
        return songCount.toLong().coerceAtLeast(0) * perSong
    }
}
