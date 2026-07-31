package chromahub.rhythm.app.util

import chromahub.rhythm.app.network.NeteaseArtist
import chromahub.rhythm.app.network.NeteaseSearchSong
import chromahub.rhythm.app.network.RhythmLyricsGenericSearchResult
import chromahub.rhythm.app.network.RhythmLyricsLine
import chromahub.rhythm.app.network.RhythmLyricsWord
import chromahub.rhythm.app.shared.data.model.LyricsData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import kotlin.math.abs

/** Normalizes legacy, v2, provider-native, and string Lyrically responses. */
object LyricallyApiParser {
    private const val SUPPLEMENTAL_MATCH_WINDOW_MS = 1_800L
    private val gson = Gson()

    private data class TimedText(val timestamp: Long, val text: String)

    fun parseLyricsResponse(
        payload: JsonElement,
        sourceName: String,
        preferredTranslationLanguage: String = Locale.getDefault().language
    ): LyricsData? {
        if (payload.isJsonNull) return null

        if (payload.isJsonPrimitive) {
            val primitive = payload.asJsonPrimitive
            if (!primitive.isString) return null
            val raw = primitive.asString.trim()
            if (raw.isEmpty()) return null

            if (raw.startsWith("{") || raw.startsWith("[")) {
                runCatching { JsonParser.parseString(raw) }
                    .getOrNull()
                    ?.takeIf { it != payload }
                    ?.let {
                        return parseLyricsResponse(
                            payload = it,
                            sourceName = sourceName,
                            preferredTranslationLanguage = preferredTranslationLanguage
                        )
                    }
            }
            return lyricsDataFromText(raw, sourceName)
        }

        val root = payload.asObjectOrNull() ?: return null
        val metadata = root.objectOrNull("metadata")
        val rawData = metadata?.objectOrNull("rawData")

        val translations = extractMetadataTrack(
            metadata = metadata,
            field = "translations",
            preferredLanguage = preferredTranslationLanguage,
            requireLatinScript = false
        ).ifEmpty {
            extractTimedLrc(
                firstString(
                    root.nested("tlyric"),
                    rawData?.nested("tlyric"),
                    metadata?.nested("translation")
                )
            )
        }
        val romanizations = extractMetadataTrack(
            metadata = metadata,
            field = "transliterations",
            preferredLanguage = preferredTranslationLanguage,
            requireLatinScript = true
        ).ifEmpty {
            extractTimedLrc(
                firstString(
                    root.nested("romalrc"),
                    rawData?.nested("romalrc"),
                    metadata?.nested("romanization")
                )
            )
        }

        val normalizedLines = normalizeLines(
            root.arrayOrNull("lyrics") ?: root.arrayOrNull("content"),
            translations = translations,
            romanizations = romanizations
        )
        val syncType = firstString(root.get("syncType"), root.get("type")).orEmpty()
        val isSyllable = syncType.equals("Syllable", ignoreCase = true) ||
            normalizedLines.any { line ->
                line.text.orEmpty().map { it.timestamp }.distinct().size > 1
            }

        if (normalizedLines.isNotEmpty()) {
            val adjustedLines = if (isSyllable) {
                normalizedLines.map { line ->
                    val words = line.text.orEmpty()
                    if (words.isEmpty()) {
                        line
                    } else {
                        line.copy(
                            text = words.mapIndexed { index, word ->
                                word.copy(part = index > 0 && words[index - 1].part == true)
                            }
                        )
                    }
                }
            } else {
                normalizedLines
            }
            val wordByWordJson = gson.toJson(adjustedLines)
            val parsed = RhythmLyricsParser.parseWordByWordLyrics(wordByWordJson)
            if (
                parsed.isNotEmpty() &&
                LyricsTimingPolicy.hasUsableTimeline(parsed.map { it.lineTimestamp })
            ) {
                return LyricsData(
                    plainLyrics = RhythmLyricsParser.toPlainText(parsed).takeIf(String::isNotBlank),
                    syncedLyrics = RhythmLyricsParser.toLRCFormat(parsed).takeIf(String::isNotBlank),
                    wordByWordLyrics = wordByWordJson.takeIf { isSyllable },
                    source = sourceName,
                    isCorrected = isSyllable
                )
            }
        }

        val ttml = firstString(root.get("ttmlContent"), root.get("ttml_content"))
        if (!ttml.isNullOrBlank()) {
            val parsedTtml = RhythmLyricsParser.parseTtmlLyrics(ttml)
            if (parsedTtml.isNotEmpty()) {
                val enrichedTtml = attachSupplements(parsedTtml, translations, romanizations)
                val wordByWordJson = gson.toJson(enrichedTtml)
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(wordByWordJson)
                if (
                    parsed.isNotEmpty() &&
                    LyricsTimingPolicy.hasUsableTimeline(parsed.map { it.lineTimestamp })
                ) {
                    return LyricsData(
                        plainLyrics = RhythmLyricsParser.toPlainText(parsed).takeIf(String::isNotBlank),
                        syncedLyrics = RhythmLyricsParser.toLRCFormat(parsed).takeIf(String::isNotBlank),
                        wordByWordLyrics = wordByWordJson,
                        source = sourceName,
                        isCorrected = true
                    )
                }
            }
        }

        val lrc = firstString(
            root.nested("lrc"),
            root.nested("elrc"),
            metadata?.nested("lrc"),
            rawData?.nested("lrc")
        )
        val plain = firstString(
            root.get("plain"),
            root.get("plainLyrics"),
            metadata?.get("plain"),
            metadata?.get("plainLyrics"),
            rawData?.get("plain"),
            rawData?.get("plainLyrics")
        )

        if (!lrc.isNullOrBlank()) {
            val enrichedLrc = appendSupplementalLrc(lrc, translations, romanizations)
            val parsed = LyricsParser.parseLyrics(enrichedLrc)
            val derivedPlain = parsed
                .joinToString("\n") { it.text }
                .takeIf(String::isNotBlank)
            if (!LyricsTimingPolicy.hasUsableTimeline(parsed.map { it.timestamp })) {
                return plain?.takeIf(String::isNotBlank)?.let { plainLyrics ->
                    LyricsData(
                        plainLyrics = plainLyrics,
                        syncedLyrics = null,
                        source = sourceName
                    )
                }
            }
            return LyricsData(
                plainLyrics = plain?.takeIf(String::isNotBlank) ?: derivedPlain,
                syncedLyrics = enrichedLrc,
                source = sourceName
            )
        }

        if (!plain.isNullOrBlank()) {
            return LyricsData(
                plainLyrics = plain,
                syncedLyrics = null,
                source = sourceName
            )
        }

        return null
    }

    fun parseGenericSearch(payload: JsonElement): List<RhythmLyricsGenericSearchResult> {
        val items = findSearchItems(payload) ?: return emptyList()
        return items.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = firstString(
                obj.get("trackId"),
                obj.get("id"),
                obj.get("videoId"),
                obj.get("songmid"),
                obj.get("hash")
            )
            val name = firstString(obj.get("name"), obj.get("title"))
            val artist = firstString(
                obj.get("artistName"),
                obj.get("author"),
                obj.get("singerName"),
                obj.get("artist")
            ) ?: extractArtistNames(obj)

            if (id.isNullOrBlank() || name.isNullOrBlank()) return@mapNotNull null

            RhythmLyricsGenericSearchResult(
                trackId = obj.stringOrNull("trackId"),
                id = obj.stringOrNull("id"),
                videoId = obj.stringOrNull("videoId"),
                songmid = obj.stringOrNull("songmid"),
                hash = obj.stringOrNull("hash"),
                name = obj.stringOrNull("name"),
                title = obj.stringOrNull("title"),
                artistName = obj.stringOrNull("artistName"),
                author = obj.stringOrNull("author"),
                artist = artist
            )
        }
    }

    fun parseNeteaseSearch(payload: JsonElement): List<NeteaseSearchSong> {
        val root = payload.asObjectOrNull() ?: return emptyList()
        val songs = root.objectOrNull("result")?.arrayOrNull("songs")
            ?: root.arrayOrNull("songs")
            ?: return emptyList()

        return songs.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.longOrNull("id") ?: return@mapNotNull null
            val artists = (obj.arrayOrNull("artists") ?: obj.arrayOrNull("ar"))
                ?.mapNotNull { artist ->
                    val name = artist.asObjectOrNull()?.stringOrNull("name")
                        ?: elementText(artist)
                    name?.takeIf(String::isNotBlank)?.let(::NeteaseArtist)
                }
                .orEmpty()

            NeteaseSearchSong(
                id = id,
                name = firstString(obj.get("name"), obj.get("title")),
                artists = artists
            )
        }
    }

    private fun normalizeLines(
        lines: JsonArray?,
        translations: List<TimedText>,
        romanizations: List<TimedText>
    ): List<RhythmLyricsLine> {
        if (lines == null) return emptyList()
        val normalized = lines.mapNotNull(::normalizeLine)
        return attachSupplements(normalized, translations, romanizations)
    }

    private fun normalizeLine(element: JsonElement): RhythmLyricsLine? {
        val obj = element.asObjectOrNull() ?: return null
        val timestamp = firstLong(
            obj.get("timestamp"),
            obj.get("startTimeMs"),
            obj.get("start")
        ) ?: 0L
        val endtime = firstLong(
            obj.get("endtime"),
            obj.get("endTimeMs"),
            obj.get("end")
        ) ?: timestamp
        val textElement = obj.get("text") ?: obj.get("words") ?: return null
        val words = when {
            textElement.isJsonArray -> textElement.asJsonArray.mapNotNull { word ->
                normalizeWord(word, timestamp, endtime)
            }
            textElement.isJsonPrimitive -> {
                val text = textElement.asString.trim()
                if (text.isEmpty()) emptyList() else {
                    listOf(
                        RhythmLyricsWord(
                            text = text,
                            part = false,
                            timestamp = timestamp,
                            endtime = endtime
                        )
                    )
                }
            }
            textElement.isJsonObject -> {
                listOfNotNull(normalizeWord(textElement, timestamp, endtime))
            }
            else -> emptyList()
        }
        if (words.isEmpty()) return null

        val backgroundText = obj.arrayOrNull("backgroundText")
            ?.mapNotNull(::elementText)
            ?.filter(String::isNotBlank)
            ?.takeIf(List<String>::isNotEmpty)

        return RhythmLyricsLine(
            text = words,
            background = obj.booleanOrNull("background"),
            backgroundText = backgroundText,
            oppositeTurn = obj.booleanOrNull("oppositeTurn"),
            timestamp = timestamp,
            endtime = maxOf(endtime, words.maxOfOrNull { it.endtime } ?: endtime),
            endIsImplicit = obj.booleanOrNull("endIsImplicit")
        )
    }

    private fun normalizeWord(
        element: JsonElement,
        fallbackStart: Long,
        fallbackEnd: Long
    ): RhythmLyricsWord? {
        if (element.isJsonPrimitive) {
            val text = element.asString.trim()
            return text.takeIf(String::isNotEmpty)?.let {
                RhythmLyricsWord(it, false, fallbackStart, fallbackEnd)
            }
        }

        val obj = element.asObjectOrNull() ?: return null
        val text = firstString(obj.get("text"), obj.get("word"))?.trim().orEmpty()
        if (text.isEmpty()) return null
        val timestamp = firstLong(
            obj.get("timestamp"),
            obj.get("startTimeMs"),
            obj.get("begin")
        ) ?: fallbackStart
        val endtime = firstLong(
            obj.get("endtime"),
            obj.get("endTimeMs"),
            obj.get("end")
        ) ?: fallbackEnd

        return RhythmLyricsWord(
            text = text,
            part = obj.booleanOrNull("part") ?: false,
            timestamp = timestamp,
            endtime = maxOf(timestamp, endtime)
        )
    }

    private fun attachSupplements(
        lines: List<RhythmLyricsLine>,
        translations: List<TimedText>,
        romanizations: List<TimedText>
    ): List<RhythmLyricsLine> {
        if (translations.isEmpty() && romanizations.isEmpty()) return lines

        return lines.map { line ->
            val timestamp = line.timestamp ?: line.text?.firstOrNull()?.timestamp ?: 0L
            val existing = line.backgroundText.orEmpty().toMutableList()
            nearestText(translations, timestamp)?.let { translation ->
                val wrapped = "($translation)"
                if (existing.none { canonicalText(it) == canonicalText(wrapped) }) existing += wrapped
            }
            nearestText(romanizations, timestamp)?.let { romanization ->
                val wrapped = "[$romanization]"
                if (existing.none { canonicalText(it) == canonicalText(wrapped) }) existing += wrapped
            }
            line.copy(backgroundText = existing.takeIf(List<String>::isNotEmpty))
        }
    }

    private fun appendSupplementalLrc(
        lrc: String,
        translations: List<TimedText>,
        romanizations: List<TimedText>
    ): String {
        val supplementalLines = buildList {
            translations.forEach { add("${formatLrcTimestamp(it.timestamp)}(${it.text})") }
            romanizations.forEach { add("${formatLrcTimestamp(it.timestamp)}[${it.text}]") }
        }.distinct()
        return if (supplementalLines.isEmpty()) lrc else {
            "$lrc\n${supplementalLines.joinToString("\n")}"
        }
    }

    private fun extractMetadataTrack(
        metadata: JsonObject?,
        field: String,
        preferredLanguage: String,
        requireLatinScript: Boolean
    ): List<TimedText> {
        val tracks = metadata?.arrayOrNull(field) ?: return emptyList()
        val candidates = tracks.mapNotNull { track ->
            val obj = track.asObjectOrNull() ?: return@mapNotNull null
            val lines = obj.arrayOrNull("lines")
                ?.mapNotNull(::timedTextFromObject)
                .orEmpty()
            if (lines.isEmpty()) return@mapNotNull null
            Triple(
                firstString(obj.get("lang"), obj.get("language")).orEmpty(),
                lines,
                obj.booleanOrNull("automaticallyCreated") == true
            )
        }
        val eligibleCandidates = if (requireLatinScript) {
            candidates.filter { (language, lines, _) ->
                language.lowercase(Locale.ROOT).endsWith("-latn") ||
                    lines.count { LyricsRomanizationPolicy.isLatinDominant(it.text) }
                        .toDouble() / lines.size >= 0.60
            }
        } else {
            candidates
        }
        if (eligibleCandidates.isEmpty()) return emptyList()

        val preferred = eligibleCandidates.minByOrNull { (language, _, automatic) ->
            val normalizedLanguage = language.lowercase(Locale.ROOT)
            val preferredLanguageMatch =
                normalizedLanguage == preferredLanguage.lowercase(Locale.ROOT) ||
                    normalizedLanguage.startsWith("${preferredTranslationLanguage(preferredLanguage)}-")
            when {
                requireLatinScript && normalizedLanguage.endsWith("-latn") && !automatic -> 0
                requireLatinScript && normalizedLanguage.endsWith("-latn") -> 1
                requireLatinScript -> 10
                preferredLanguageMatch && !automatic -> 0
                preferredLanguageMatch -> 1
                normalizedLanguage.startsWith("en") && !automatic -> 2
                normalizedLanguage.startsWith("en") -> 3
                !automatic -> 4
                else -> 5
            }
        }
        return preferred?.second.orEmpty()
    }

    private fun preferredTranslationLanguage(language: String): String =
        language.substringBefore('-').substringBefore('_')

    private fun extractTimedLrc(lrc: String?): List<TimedText> {
        if (lrc.isNullOrBlank()) return emptyList()
        val timestampedLine =
            Regex("^((?:\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?\\])+)(.*)$")
        val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

        return lrc.lineSequence().flatMap { rawLine ->
            val match = timestampedLine.matchEntire(rawLine.trim())
                ?: return@flatMap emptySequence()
            val text = match.groupValues[2].trim()
            if (text.isEmpty()) return@flatMap emptySequence()
            timestamp.findAll(match.groupValues[1]).mapNotNull { stamp ->
                val minutes = stamp.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val seconds = stamp.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val fraction = stamp.groupValues[3]
                val millis = when (fraction.length) {
                    1 -> fraction.toLongOrNull()?.times(100L)
                    2 -> fraction.toLongOrNull()?.times(10L)
                    3 -> fraction.toLongOrNull()
                    else -> 0L
                } ?: 0L
                TimedText(minutes * 60_000L + seconds * 1_000L + millis, text)
            }
        }.toList()
    }

    private fun timedTextFromObject(element: JsonElement): TimedText? {
        val obj = element.asObjectOrNull() ?: return null
        val timestamp = firstLong(
            obj.get("timestamp"),
            obj.get("startTimeMs"),
            obj.get("begin")
        ) ?: return null
        val text = firstString(obj.get("text"), obj.get("line"))?.trim().orEmpty()
        return text.takeIf(String::isNotEmpty)?.let { TimedText(timestamp, it) }
    }

    private fun lyricsDataFromText(raw: String, sourceName: String): LyricsData {
        val hasTimestamps = Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(raw)
        if (!hasTimestamps) return LyricsData(raw, null, source = sourceName)

        val plain = LyricsParser.parseLyrics(raw)
            .joinToString("\n") { it.text }
            .takeIf(String::isNotBlank)
        return LyricsData(plain, raw, source = sourceName)
    }

    private fun findSearchItems(payload: JsonElement): JsonArray? {
        if (payload.isJsonArray) return payload.asJsonArray
        val root = payload.asObjectOrNull() ?: return null
        for (field in listOf("data", "results", "songs", "tracks", "items")) {
            root.arrayOrNull(field)?.let { return it }
        }
        val result = root.objectOrNull("result")
        for (field in listOf("data", "results", "songs", "tracks", "items")) {
            result?.arrayOrNull(field)?.let { return it }
        }
        return null
    }

    private fun extractArtistNames(obj: JsonObject): String? {
        val artists = obj.arrayOrNull("artists") ?: obj.arrayOrNull("ar") ?: return null
        return artists.mapNotNull { artist ->
            artist.asObjectOrNull()?.stringOrNull("name") ?: elementText(artist)
        }.filter(String::isNotBlank).joinToString(", ").takeIf(String::isNotBlank)
    }

    private fun elementText(element: JsonElement): String? {
        if (element.isJsonPrimitive) return element.asString
        val obj = element.asObjectOrNull() ?: return null
        return firstString(obj.get("text"), obj.get("name"), obj.get("value"))
    }

    private fun nearestText(values: List<TimedText>, timestamp: Long): String? =
        values.minByOrNull { abs(it.timestamp - timestamp) }
            ?.takeIf { abs(it.timestamp - timestamp) <= SUPPLEMENTAL_MATCH_WINDOW_MS }
            ?.text

    private fun canonicalText(text: String): String =
        text.lowercase(Locale.ROOT).filter(Character::isLetterOrDigit)

    private fun formatLrcTimestamp(timestampMs: Long): String {
        val minutes = timestampMs / 60_000L
        val seconds = (timestampMs % 60_000L) / 1_000L
        val centiseconds = (timestampMs % 1_000L) / 10L
        return "[%02d:%02d.%02d]".format(Locale.ROOT, minutes, seconds, centiseconds)
    }

    private fun firstString(vararg elements: JsonElement?): String? =
        elements.firstNotNullOfOrNull { element ->
            when {
                element == null || element.isJsonNull -> null
                element.isJsonPrimitive -> runCatching { element.asString }.getOrNull()
                element.isJsonObject -> firstString(
                    element.asJsonObject.get("lyric"),
                    element.asJsonObject.get("text"),
                    element.asJsonObject.get("name"),
                    element.asJsonObject.get("value")
                )
                else -> null
            }?.takeIf(String::isNotBlank)
        }

    private fun firstLong(vararg elements: JsonElement?): Long? =
        elements.firstNotNullOfOrNull { element ->
            if (element == null || element.isJsonNull || !element.isJsonPrimitive) null
            else runCatching { element.asLong }.getOrNull()
        }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.asObjectOrNull()

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.stringOrNull(name: String): String? =
        firstString(get(name))

    private fun JsonObject.longOrNull(name: String): Long? =
        firstLong(get(name))

    private fun JsonObject.booleanOrNull(name: String): Boolean? =
        get(name)
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asBoolean }.getOrNull() }

    private fun JsonObject.nested(name: String): JsonElement? = get(name)
}
