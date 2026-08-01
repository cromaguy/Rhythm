package chromahub.rhythm.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import chromahub.rhythm.app.shared.data.model.LyricsData
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

object MetadataHeuristics {
    private const val TAG = "MetadataHeuristics"

    fun extractArtistFromVorbisCommentTags(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null

        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("opus", "ogg", "oga", "opa")) {
            return null
        }

        val commentEntries = extractVorbisCommentEntriesFromOgg(filePath) ?: return null

        val repeatedArtists = commentEntries
            .asSequence()
            .filter { it.first == "ARTISTS" }
            .map { it.second.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toList()

        if (repeatedArtists.isNotEmpty()) {
            return repeatedArtists.joinToString(" / ")
        }

        return commentEntries
            .asSequence()
            .firstOrNull { it.first == "ARTIST" && it.second.isNotBlank() }
            ?.second
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun extractVorbisCommentEntriesFromOgg(filePath: String): List<Pair<String, String>>? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null

            RandomAccessFile(file, "r").use { raf ->
                val signature = ByteArray(4)
                if (raf.read(signature) != 4 || String(signature, Charsets.ISO_8859_1) != "OggS") {
                    return@use null
                }

                raf.seek(0)
                val packetBuffer = ByteArrayOutputStream()
                var pageCount = 0
                val maxPages = 256

                while (raf.filePointer < raf.length() && pageCount < maxPages) {
                    pageCount++

                    val pageSignature = ByteArray(4)
                    if (raf.read(pageSignature) != 4) break
                    if (String(pageSignature, Charsets.ISO_8859_1) != "OggS") {
                        val nextOggS = findNextOggSPage(raf)
                        if (nextOggS == -1L) break
                        raf.seek(nextOggS)
                        packetBuffer.reset()
                        continue
                    }

                    // Skip version + header type + granule/serial/sequence/checksum.
                    raf.skipBytes(2)
                    raf.skipBytes(20)

                    val segmentCount = raf.read()
                    if (segmentCount < 0) break

                    val segmentTable = ByteArray(segmentCount)
                    if (raf.read(segmentTable) != segmentCount) break

                    val pageSize = segmentTable.sumOf { it.toInt() and 0xFF }
                    if (pageSize < 0 || pageSize > 1_000_000) {
                        if (pageSize > 0) {
                            raf.seek(raf.filePointer + pageSize)
                        }
                        packetBuffer.reset()
                        continue
                    }

                    val pageData = ByteArray(pageSize)
                    if (pageSize > 0 && raf.read(pageData) != pageSize) break

                    var payloadOffset = 0
                    for (segment in segmentTable) {
                        val segmentLength = segment.toInt() and 0xFF

                        if (segmentLength > 0) {
                            if (payloadOffset + segmentLength > pageData.size) {
                                packetBuffer.reset()
                                break
                            }

                            packetBuffer.write(pageData, payloadOffset, segmentLength)
                            payloadOffset += segmentLength
                        }

                        // A lacing value <255 marks the end of a packet.
                        if (segmentLength < 255) {
                            val packet = packetBuffer.toByteArray()
                            packetBuffer.reset()

                            val commentData = extractVorbisCommentDataFromPacket(packet) ?: continue
                            return@use parseVorbisCommentEntries(commentData) ?: emptyList()
                        }
                    }
                }

                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract OGG/Opus comments from $filePath: ${e.message}")
            null
        }
    }

    fun extractVorbisCommentDataFromPacket(packet: ByteArray): ByteArray? {
        if (packet.size >= 8 &&
            String(packet.copyOfRange(0, 8), Charsets.ISO_8859_1) == "OpusTags"
        ) {
            return packet.copyOfRange(8, packet.size)
        }

        if (packet.size >= 7 &&
            packet[0] == 0x03.toByte() &&
            String(packet.copyOfRange(1, 7), Charsets.ISO_8859_1) == "vorbis"
        ) {
            return packet.copyOfRange(7, packet.size)
        }

        return null
    }

    fun normalizeMetadataText(value: String?): String? {
        val raw = value?.trim() ?: return value
        if (raw.isBlank()) return raw

        val hasCommonUtf8MojibakeMarkers =
            raw.contains('Ã') ||
                raw.contains('Â') ||
                raw.contains("\u00E2\u20AC")
        if (!hasCommonUtf8MojibakeMarkers) return raw

        val hasNonLatin1CodePoints = raw.any { it.code > 0xFF }
        if (hasNonLatin1CodePoints && !raw.contains("\u00E2\u20AC")) {
            // Avoid damaging valid Unicode (for example Vietnamese) with a forced Latin-1 round-trip.
            return raw
        }

        return runCatching {
            val repaired = String(raw.toByteArray(java.nio.charset.Charset.forName("windows-1252")), Charsets.UTF_8)
            val repairedHasMoreReplacementChars =
                repaired.count { it == '\uFFFD' } > raw.count { it == '\uFFFD' }
            if (repaired.isBlank() || repairedHasMoreReplacementChars) raw else repaired
        }.getOrDefault(raw)
    }

    fun titleFromDisplayName(displayName: String?): String? {
        val normalized = normalizeMetadataText(displayName)?.trim() ?: return null
        if (normalized.isBlank()) return null

        return normalized.substringBeforeLast('.', normalized)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    fun isLikelyCorruptedMetadata(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.equals("<unknown>", ignoreCase = true)) {
            return true
        }

        if (trimmed.any { it == '\uFFFD' }) {
            return true
        }

        val questionMarkCount = trimmed.count { it == '?' }
        if (questionMarkCount >= 2) {
            return true
        }

        // A question mark embedded inside a word usually indicates lossy character conversion.
        return questionMarkCount > 0 && Regex("\\p{L}\\?\\p{L}").containsMatchIn(trimmed)
    }

    fun metadataQualityScore(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Int.MIN_VALUE

        var score = trimmed.length * 4
        score -= trimmed.count { it == '\uFFFD' } * 40
        score -= trimmed.count { it == '?' } * 25
        if (trimmed.equals("<unknown>", ignoreCase = true)) {
            score -= 200
        }
        if (trimmed.any { it.code > 0x7F }) {
            score += 8
        }

        return score
    }

    fun selectBestMetadataText(vararg candidates: String?): String? {
        return candidates
            .mapNotNull { candidate -> candidate?.trim()?.takeIf { it.isNotBlank() } }
            .maxByOrNull { candidate -> metadataQualityScore(candidate) }
    }

    fun findNextOggSPage(raf: RandomAccessFile): Long {
        val buffer = ByteArray(4096)
        var bytesRead: Int
        val targetPattern = "OggS".toByteArray(Charsets.ISO_8859_1)

        while (raf.filePointer < raf.length()) {
            val filePos = raf.filePointer
            bytesRead = raf.read(buffer)
            if (bytesRead == -1) return -1

            for (i in 0 until bytesRead - 3) {
                if (buffer[i] == targetPattern[0] &&
                    buffer[i + 1] == targetPattern[1] &&
                    buffer[i + 2] == targetPattern[2] &&
                    buffer[i + 3] == targetPattern[3]
                ) {
                    return filePos + i
                }
            }
            // Overlap slightly to not miss boundary matches
            raf.seek(raf.filePointer - 3)
        }
        return -1
    }

    fun parseVorbisCommentEntries(data: ByteArray): List<Pair<String, String>>? {
        return try {
            var pos = 0

            // Read vendor string length (little-endian 32-bit).
            if (pos + 4 > data.size) return null
            val vendorLength = readLittleEndianInt(data, pos)
            pos += 4

            // Skip vendor string.
            if (vendorLength < 0 || pos + vendorLength > data.size) return null
            pos += vendorLength

            // Read number of comments.
            if (pos + 4 > data.size) return null
            val commentCount = readLittleEndianInt(data, pos)
            pos += 4
            if (commentCount < 0 || commentCount > 10_000) return null

            val comments = mutableListOf<Pair<String, String>>()
            for (i in 0 until commentCount) {
                if (pos + 4 > data.size) break

                val commentLength = readLittleEndianInt(data, pos)
                pos += 4
                if (commentLength < 0 || pos + commentLength > data.size) break

                val comment = String(data, pos, commentLength, Charsets.UTF_8)
                pos += commentLength

                val parts = comment.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].uppercase()
                    val value = normalizeMetadataText(parts[1])?.trim().orEmpty()
                    if (key.isNotBlank() && value.isNotBlank()) {
                        comments.add(key to value)
                    }
                }
            }

            comments
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vorbis comments: ${e.message}")
            null
        }
    }

    fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun parseVorbisComments(data: ByteArray): LyricsData? {
        val commentEntries = parseVorbisCommentEntries(data) ?: return null

        for ((key, value) in commentEntries) {
            if ((key == "LYRICS" || key == "UNSYNCEDLYRICS") && value.isNotBlank()) {
                Log.d(TAG, "Found $key tag in Vorbis comments (${value.length} chars)")
                return parseLyricsData(value)
            }
        }

        return null
    }

    fun extractLyricsFromOGG(filePath: String): LyricsData? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null

            RandomAccessFile(file, "r").use { raf ->
                // Check OGG signature
                val signature = ByteArray(4)
                if (raf.read(signature) != 4) return@use null
                if (String(signature, Charsets.ISO_8859_1) != "OggS") {
                    Log.d(TAG, "Not a valid OGG file")
                    return@use null
                }

                var foundCommentHeader = false
                var attemptCount = 0
                val maxAttempts = 100

                raf.seek(0)

                while (raf.filePointer < file.length() && attemptCount < maxAttempts) {
                    attemptCount++

                    val pageSignature = ByteArray(4)
                    if (raf.read(pageSignature) != 4) break
                    if (String(pageSignature, Charsets.ISO_8859_1) != "OggS") {
                        val nextOggS = findNextOggSPage(raf)
                        if (nextOggS == -1L) break
                        raf.seek(nextOggS)
                        continue
                    }

                    raf.skipBytes(2)
                    raf.skipBytes(20)

                    val numSegments = raf.read()
                    if (numSegments == -1 || numSegments < 0) break

                    val segmentTable = ByteArray(numSegments)
                    if (raf.read(segmentTable) != numSegments) break

                    val pageSize = segmentTable.sumOf { (it.toInt() and 0xFF) }

                    if (pageSize > 0 && pageSize < 1_000_000) {
                        val pageData = ByteArray(pageSize)
                        if (raf.read(pageData) != pageSize) break

                        if (pageData.size >= 7 &&
                            pageData[0] == 0x03.toByte() &&
                            String(pageData.copyOfRange(1, 7), Charsets.ISO_8859_1) == "vorbis"
                        ) {
                            Log.d(TAG, "Found Vorbis comment header in OGG file")
                            foundCommentHeader = true

                            val commentData = pageData.copyOfRange(7, pageData.size)
                            val lyrics = parseVorbisComments(commentData)
                            if (lyrics != null) {
                                Log.d(TAG, "Found lyrics in OGG Vorbis comments")
                                return@use lyrics
                            }
                        }
                    } else {
                        if (pageSize > 0) {
                            raf.seek(raf.filePointer + pageSize)
                        }
                    }

                    if (foundCommentHeader) break
                }

                Log.d(TAG, "No LYRICS tag found in OGG Vorbis comments (checked $attemptCount pages)")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "OGG lyrics extraction failed: ${e.message}")
            null
        }
    }

    fun looksLikeLyrics(text: String): Boolean {
        val trimmed = text.trim()

        if (trimmed.length < 50) return false

        val lines = trimmed.lines().filter { it.trim().isNotEmpty() }
        if (lines.size < 3) return false

        val hasTimestamp = text.contains(Regex("\\[\\d{2}:\\d{2}"))
        if (hasTimestamp) return true

        val lowerText = trimmed.lowercase()
        val metadataKeywords = listOf(
            "track", "album", "artist", "genre", "year", "composer",
            "copyright", "encoded", "encoder", "itunes", "id3"
        )
        val hasMetadataKeywords = metadataKeywords.any { lowerText.contains(it) }

        val hasCommonLyricsWords = lowerText.let {
            it.contains("verse") || it.contains("chorus") || it.contains("bridge") ||
                    it.contains("refrain") || it.contains("intro") || it.contains("outro")
        }

        val avgLineLength = lines.map { it.length }.average()
        val hasRepeatingPatterns = lines.distinct().size < lines.size * 0.8
        val isProseLength = avgLineLength in 20.0..80.0

        return (lines.size >= 5 && isProseLength && !hasMetadataKeywords) ||
                (hasCommonLyricsWords && lines.size >= 3) ||
                (hasRepeatingPatterns && lines.size >= 8 && avgLineLength < 100)
    }

    fun parseLyricsData(lyrics: String): LyricsData? {
        if (lyrics.isBlank()) {
            return null
        }

        Log.d(TAG, "Parsing lyrics data: ${lyrics.take(200)}${if (lyrics.length > 200) "..." else ""}")

        val cleanedLyrics = sanitizeLyricsText(lyrics)

        if (cleanedLyrics.isBlank()) {
            Log.w(TAG, "Rejected lyrics: text became empty after sanitization")
            return null
        }

        val lines = cleanedLyrics.lines().filter { it.trim().isNotEmpty() }
        if (lines.size == 1) {
            Log.w(TAG, "Rejected lyrics: single line detected (likely metadata): ${cleanedLyrics.take(100)}")
            return null
        }

        val lrcPattern = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{2,3})?]")
        val isSynced = lrcPattern.containsMatchIn(cleanedLyrics)

        return if (isSynced) {
            val hasLyricsContent = cleanedLyrics.lines().any { line ->
                lrcPattern.replace(line, "").trim().isNotEmpty()
            }

            if (hasLyricsContent) {
                val hasKaraokeTimestamps = cleanedLyrics.lines().any { line ->
                    val timestampPattern = Regex("\\[\\d{1,2}:\\d{2}\\.\\d{3}\\]")
                    val timestampCount = timestampPattern.findAll(line).count()
                    timestampCount > 2
                }

                if (hasKaraokeTimestamps) {
                    Log.d(TAG, "Detected karaoke format with syllable-level timestamps")

                    val enhancedLines = parseKaraokeLyrics(cleanedLyrics)

                    if (enhancedLines.isNotEmpty()) {
                        val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)

                        val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            line.words.joinToString("") { word: EnhancedWord -> word.text }
                        }

                        val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            val timestamp = formatLRCTimestamp(line.lineTimestamp)
                            val text = line.words.joinToString("") { word: EnhancedWord -> word.text }
                            "[$timestamp]$text"
                        }

                        Log.d(TAG, "Successfully converted karaoke lyrics to word-by-word format (${enhancedLines.size} lines)")
                        return LyricsData(plainText, syncedLrc, wordByWordJson)
                    }
                }

                val hasWordTimestamps = LyricsParser.hasWordTimestamps(cleanedLyrics)

                if (hasWordTimestamps) {
                    Log.d(TAG, "Detected Enhanced LRC format with word-level timestamps")

                    val enhancedLines = LyricsParser.parseEnhancedLRC(cleanedLyrics)

                    if (enhancedLines.isNotEmpty()) {
                        val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)

                        val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                        }

                        val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            val timestamp = formatLRCTimestamp(line.lineTimestamp)
                            val text = line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                            "[$timestamp]$text"
                        }

                        Log.d(TAG, "Successfully converted Enhanced LRC to word-by-word format (${enhancedLines.size} lines)")
                        return LyricsData(plainText, syncedLrc, wordByWordJson)
                    }
                }

                val normalizedLyrics = normalizePlainLRC(cleanedLyrics)
                LyricsData(null, normalizedLyrics, null)
            } else {
                null
            }
        } else {
            val meaningfulLines = cleanedLyrics.lines().filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() &&
                        !trimmed.startsWith("//") &&
                        !trimmed.startsWith("#") &&
                        trimmed.length > 2 &&
                        isLikelyLyricsLine(trimmed)
            }

            if (meaningfulLines.isNotEmpty()) {
                LyricsData(cleanedLyrics, null, null)
            } else {
                null
            }
        }
    }

    fun sanitizeLyricsText(input: String): String {
        val normalized = input
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val cleanedLines = normalized.lines().mapNotNull { rawLine ->
            val cleaned = rawLine
                .filter { ch ->
                    when {
                        ch == '\t' -> true
                        ch == '\uFFFD' -> false
                        Character.isISOControl(ch) -> false
                        else -> true
                    }
                }
                .trimEnd()

            if (cleaned.isBlank()) {
                null
            } else {
                cleaned
            }
        }.filter { line ->
            isLikelyLyricsLine(line)
        }

        return cleanedLines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun isLikelyLyricsLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false

        if (trimmed.matches(Regex("\\[[a-zA-Z]{1,10}:[^\\]]*]"))) return true
        if (trimmed.matches(Regex("(\\[\\d{1,2}:\\d{2}(?:\\.\\d{2,3})?])+.*"))) return true

        val body = trimmed
            .replace(Regex("\\[[^\\]]*]"), "")
            .replace(Regex("<[^>]*>"), "")
            .trim()

        if (body.isEmpty()) return true
        if (body.length < 20) return true

        val readableChars = body.count { ch ->
            ch.isLetterOrDigit() ||
                    ch.isWhitespace() ||
                    isLyricsPunctuation(ch)
        }

        val ratio = readableChars.toDouble() / body.length.toDouble()
        return ratio >= 0.55
    }

    fun isLyricsPunctuation(ch: Char): Boolean {
        return when (Character.getType(ch)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt() -> true
            else -> false
        }
    }

    fun convertEnhancedLRCToWordByWord(enhancedLines: List<EnhancedLyricLine>): String {
        val rhythmWordLines = enhancedLines.map { line: EnhancedLyricLine ->
            val words = line.words.map { word: EnhancedWord ->
                mapOf(
                    "text" to word.text,
                    "part" to word.isPart,
                    "timestamp" to word.timestamp,
                    "endtime" to word.endtime
                )
            }

            val lineMap = mutableMapOf<String, Any>(
                "text" to words,
                "background" to false,
                "timestamp" to line.lineTimestamp,
                "endtime" to line.lineEndtime
            )

            val backgroundText = mutableListOf<String>()
            line.translation?.let { backgroundText.add(it) }
            line.romanization?.let { backgroundText.add(it) }
            if (backgroundText.isNotEmpty()) {
                lineMap["backgroundText"] = backgroundText
            }

            lineMap
        }

        return com.google.gson.Gson().toJson(rhythmWordLines)
    }

    fun parseKaraokeLyrics(lyrics: String): List<EnhancedLyricLine> {
        val enhancedLines = mutableListOf<EnhancedLyricLine>()
        val lines = lyrics.trim().split("\n", "\r\n", "\r")

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            val karaokePattern = Regex("\\[(\\d{1,2}):(\\d{2})\\.(\\d{3})\\]([^\\[]*)")
            val matches = karaokePattern.findAll(trimmedLine)

            val words = mutableListOf<EnhancedWord>()
            var lineStartTime = Long.MAX_VALUE
            var lineEndTime = Long.MIN_VALUE

            for (match in matches) {
                try {
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val milliseconds = match.groupValues[3].toLong()
                    val text = match.groupValues[4]

                    val timestamp = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds

                    if (text.isNotBlank()) {
                        val endtime = timestamp + 100

                        words.add(EnhancedWord(
                            text = text,
                            timestamp = timestamp,
                            endtime = endtime
                        ))

                        lineStartTime = minOf(lineStartTime, timestamp)
                        lineEndTime = maxOf(lineEndTime, endtime)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing karaoke timestamp: ${match.value}", e)
                }
            }

            val adjustedWords = words.mapIndexed { index, word ->
                if (index < words.size - 1) {
                    word.copy(endtime = words[index + 1].timestamp)
                } else {
                    if (word.endtime == word.timestamp + 100) {
                        word.copy(endtime = word.timestamp + 500)
                    } else {
                        word
                    }
                }
            }

            if (adjustedWords.isNotEmpty()) {
                lineEndTime = adjustedWords.last().endtime
            }

            if (adjustedWords.isNotEmpty()) {
                val actualLineStart = if (lineStartTime != Long.MAX_VALUE) lineStartTime else adjustedWords.first().timestamp
                val actualLineEnd = if (lineEndTime != Long.MIN_VALUE) lineEndTime else adjustedWords.last().endtime

                enhancedLines.add(EnhancedLyricLine(
                    words = adjustedWords,
                    lineTimestamp = actualLineStart,
                    lineEndtime = actualLineEnd
                ))
            }
        }

        return enhancedLines.sortedBy { it.lineTimestamp }
    }

    fun normalizePlainLRC(lrcContent: String): String {
        val lrcRegex = Regex("""^\[(\d{1,2}):(\d{2}(?:\.\d{2,3})?)\](.*)$""", RegexOption.MULTILINE)
        return lrcContent.lines().map { line ->
            val matchResult = lrcRegex.matchEntire(line.trim())
            if (matchResult != null) {
                val timestamp = matchResult.groupValues[0].substring(0, matchResult.groupValues[0].indexOf(']') + 1)
                val text = matchResult.groupValues[3]
                val normalizedText = LyricsParser.normalizeWordFlowText(text)
                "$timestamp$normalizedText"
            } else {
                line
            }
        }.joinToString("\n")
    }

    fun formatLRCTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = (milliseconds % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, millis)
    }

    fun exportToEnhancedLRC(lyricsData: LyricsData): String? {
        val wordByWordJson = lyricsData.wordByWordLyrics ?: return null

        try {
            val gson = com.google.gson.Gson()
            val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val parsedLines: List<Map<String, Any>> = gson.fromJson(wordByWordJson, listType)

            if (parsedLines.isEmpty()) return null

            val enhancedLines = parsedLines.mapNotNull { lineMap ->
                @Suppress("UNCHECKED_CAST")
                val wordsData = lineMap["text"] as? List<Map<String, Any>> ?: return@mapNotNull null
                val lineTimestamp = (lineMap["timestamp"] as? Number)?.toLong() ?: 0L
                val lineEndtime = (lineMap["endtime"] as? Number)?.toLong() ?: 0L

                val words = wordsData.map { wordMap ->
                    EnhancedWord(
                        text = wordMap["text"] as? String ?: "",
                        timestamp = (wordMap["timestamp"] as? Number)?.toLong() ?: 0L,
                        endtime = (wordMap["endtime"] as? Number)?.toLong() ?: 0L
                    )
                }

                EnhancedLyricLine(
                    words = words,
                    lineTimestamp = lineTimestamp,
                    lineEndtime = lineEndtime
                )
            }

            return LyricsParser.toEnhancedLRC(enhancedLines)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export lyrics to Enhanced LRC: ${e.message}")
            return null
        }
    }
}
