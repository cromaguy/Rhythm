/*
 * Copyright (C) 2025 nift4 (Gramophone)
 * Modified for Rhythm by Anjishnu Nandi (cromaguy)
 *
 * SPDX-FileCopyrightText: 2025 nift4 <https://github.com/FoedusProgramme/Gramophone>
 * SPDX-FileCopyrightText: 2025-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.os.Parcel
import android.os.Parcelable
import android.util.Xml
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private const val TAG = "SemanticLyrics"

inline fun <reified T> MutableList<T>.forEachSupport(skipFirst: Int = 0, operator: (T) -> Unit) {
    val li = listIterator()
    var skip = skipFirst
    while (skip-- > 0) {
        if (li.hasNext()) li.next()
    }
    while (li.hasNext()) {
        operator(li.next())
    }
}

@Parcelize
enum class SpeakerEntity(
    val isVoice2: Boolean = false, // align opposite
    val isGroup: Boolean = false, // center
    val isBackground: Boolean = false, // small
    val isWidthLimited: Boolean = false,
) : Parcelable {
    Voice,
    VoiceBackground(isBackground = true),
    Voice1(isWidthLimited = true),
    Voice1Background(isWidthLimited = true, isBackground = true),
    Voice2(isWidthLimited = true, isVoice2 = true),
    Voice2Background(isWidthLimited = true, isVoice2 = true, isBackground = true),
    Group(isGroup = true),
    GroupBackground(isGroup = true, isBackground = true)
}

private sealed class SyntacticLrc {
    data class SyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class LineEndSyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class SpeakerTag(val speaker: SpeakerEntity) : SyntacticLrc()
    data class WordSyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class Metadata(val name: String, val value: String) : SyntacticLrc()
    data class LyricText(val text: String) : SyntacticLrc()
    data class InvalidText(val text: String) : SyntacticLrc()
    open class NewLine() : SyntacticLrc() {
        class SyntheticNewLine : NewLine()
    }

    companion object {
        val timeMarksRegex = "\\[(\\d+)[.:](\\d+)([.:]\\d+)?(?:-(\\d+)[.:](\\d+)([.:]\\d+)?)?]".toRegex()
        val timeMarksAfterWsRegex = "([ \t]+)\\[\\d+[.:]\\d+(?:[.:]\\d+)?(?:-\\d+[.:]\\d+(?:[.:]\\d+)?)?]".toRegex()
        val timeWordMarksRegex = "<(\\d+)[.:](\\d+)([.:]\\d+)?>".toRegex()
        val metadataRegex = "\\[([a-zA-Z#]+):([^]]*)]".toRegex()

        private fun normalizeDigits(input: String): String {
            return input.map { ch ->
                val digit = Character.digit(ch.code, 10)
                if (digit >= 0 && ch.isDigit() && ch !in '0'..'9') {
                    '0' + digit
                } else {
                    ch
                }
            }.joinToString("")
        }

        private fun parseTime(match: MatchResult, second: Boolean): ULong {
            val offset = if (second) 3 else 0
            val minute = match.groupValues[offset+1].toULong()
            val milliseconds = ((match.groupValues[offset+2] + match.groupValues[offset+3]
                .replace(':', '.')).toDouble() * 1000L).toULong()
            return minute * 60u * 1000u + milliseconds
        }

        fun parseLrc(text: String, multiLineEnabled: Boolean): List<SyntacticLrc>? {
            val normalizedText = normalizeDigits(text)
            if (normalizedText.isBlank()) return null
            var pos = 0
            val out = mutableListOf<SyntacticLrc>()
            var isBgSpeaker = false
            while (pos < normalizedText.length) {
                var pendingBgNewLine = false
                if (isBgSpeaker && normalizedText[pos] == ']') {
                    pos++
                    isBgSpeaker = false
                    pendingBgNewLine = true
                }
                if (pos < normalizedText.length && pos + 1 < normalizedText.length && normalizedText.regionMatches(
                        pos,
                        "\r\n",
                        0,
                        2
                    )
                ) {
                    out.add(NewLine())
                    pos += 2
                    continue
                }
                if (pos < normalizedText.length && (normalizedText[pos] == '\n' || normalizedText[pos] == '\r')) {
                    out.add(NewLine())
                    pos++
                    continue
                }
                if (pendingBgNewLine) {
                    out.add(NewLine.SyntheticNewLine())
                    continue
                }
                val tmMatch = timeMarksRegex.matchAt(normalizedText, pos)
                if (tmMatch != null) {
                    val lastOrNull = out.lastOrNull()
                    if (!(lastOrNull is NewLine? || lastOrNull is SyncPoint
                                || lastOrNull is LineEndSyncPoint
                                || (lastOrNull is SpeakerTag && lastOrNull.speaker.isBackground)))
                        out.add(NewLine.SyntheticNewLine())
                    out.add(SyncPoint(parseTime(tmMatch, false)))
                    if (tmMatch.groupValues[4].isNotEmpty()) {
                        out.add(LineEndSyncPoint(parseTime(tmMatch, true)))
                    }
                    pos += tmMatch.value.length
                    continue
                }
                val tmwMatch = timeMarksAfterWsRegex.matchAt(normalizedText, pos)
                if (out.lastOrNull() is SyncPoint && pos + 7 < normalizedText.length && tmwMatch != null) {
                    pos += tmwMatch.groupValues[1].length
                    continue
                }
                if (out.lastOrNull() is SyncPoint || out.lastOrNull() is LineEndSyncPoint) {
                    if (pos + 2 < normalizedText.length && normalizedText.regionMatches(pos, "v1:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < normalizedText.length && normalizedText.regionMatches(pos, "v2:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < normalizedText.length && normalizedText.regionMatches(pos, "v3:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Group))
                        pos += 3
                        continue
                    }
                    if (pos + 3 < normalizedText.length && normalizedText.regionMatches(pos, " v1:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < normalizedText.length && normalizedText.regionMatches(pos, " v2:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < normalizedText.length && normalizedText.regionMatches(pos, " v3:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Group))
                        pos += 4
                        continue
                    }
                }
                if (pos + 3 < normalizedText.length && normalizedText.regionMatches(pos, "[bg:", 0, 4)) {
                    if (out.isNotEmpty() && out.last() !is NewLine)
                        out.add(NewLine.SyntheticNewLine())
                    val lastSpeaker = if (out.isNotEmpty()) out.subList(0, out.size - 1)
                        .indexOfLast { it is NewLine }.let { if (it < 0) null else it }?.let {
                            (out.subList(it, out.size - 1).findLast { it is SpeakerTag }
                                    as SpeakerTag?)?.speaker
                        } else null
                    out.add(
                        SpeakerTag(
                            when {
                                lastSpeaker?.isGroup == true -> SpeakerEntity.GroupBackground
                                lastSpeaker?.isVoice2 == true -> SpeakerEntity.Voice2Background
                                else -> SpeakerEntity.Voice1Background
                            }
                        )
                    )
                    pos += 4
                    isBgSpeaker = true
                    continue
                }
                if (out.isEmpty() || out.last() is NewLine) {
                    val mmMatch = metadataRegex.matchAt(normalizedText, pos)
                    if (mmMatch != null) {
                        out.add(Metadata(mmMatch.groupValues[1], mmMatch.groupValues[2]))
                        pos += mmMatch.value.length
                        continue
                    }
                }
                val wmMatch = timeWordMarksRegex.matchAt(normalizedText, pos)
                if (wmMatch != null) {
                    out.add(WordSyncPoint(parseTime(wmMatch, false)))
                    pos += wmMatch.value.length
                    continue
                }
                val firstUnsafeCharPos = (normalizedText.substring(pos).indexOfFirst {
                    it == '[' ||
                            it == '<' || it == '\r' || it == '\n' || (isBgSpeaker && it == ']')
                } + pos)
                    .let { if (it == pos - 1) normalizedText.length else it }
                    .let { if (it == pos) it + 1 else it }
                val subText = normalizedText.substring(pos, firstUnsafeCharPos)
                val last = out.lastOrNull()
                if (out.indexOfLast { it is NewLine } <
                    out.indexOfLast { it is SyncPoint || it is WordSyncPoint }) {
                    if (last is LyricText) {
                        out[out.size - 1] = LyricText(last.text + subText)
                    } else {
                        out.add(LyricText(subText))
                    }
                } else {
                    if (last is InvalidText) {
                        out[out.size - 1] = InvalidText(last.text + subText)
                    } else {
                        out.add(InvalidText(subText))
                    }
                }
                pos = firstUnsafeCharPos
            }
            if (out.lastOrNull() is SyncPoint || out.lastOrNull() is LineEndSyncPoint)
                out.add(InvalidText(""))
            if (out.isNotEmpty() && out.last() !is NewLine)
                out.add(NewLine.SyntheticNewLine())
            out.let {
                if (it.find {
                        it is SyncPoint && it.timestamp > 0u
                                || it is WordSyncPoint && it.timestamp > 0u
                                || it is LineEndSyncPoint && it.timestamp > 0u
                    } == null)
                    return it.flatMap {
                        when (it) {
                            is InvalidText -> listOf(it)
                            is NewLine -> listOf(it)
                            is SpeakerTag -> listOf(it)
                            is LyricText -> listOf(InvalidText(it.text))
                            else -> listOf()
                        }
                    }
            }
            return out.let {
                if (multiLineEnabled) {
                    val a = AtomicReference<String?>(null)
                    it.flatMap {
                        val aa = a.get()
                        when {
                            it is LyricText -> {
                                if (aa == null)
                                    a.set(it.text)
                                else
                                    a.set(aa + it.text)
                                listOf()
                            }
                            it is InvalidText && aa != null -> {
                                a.set(aa + it.text)
                                listOf()
                            }
                            it is NewLine && aa != null -> {
                                a.set(aa + "\n")
                                listOf()
                            }
                            aa != null -> {
                                a.set(null)
                                var aaa: String = aa
                                var i = 0
                                while (aaa.last() == '\n') {
                                    i++
                                    aaa = aaa.dropLast(1)
                                }
                                listOf(LyricText(aaa)).let {
                                    var aaaa: List<SyntacticLrc> = it
                                    while (i-- > 0)
                                        aaaa = aaaa + listOf(NewLine())
                                    aaaa
                                } + it
                            }
                            else -> listOf(it)
                        }
                    }.let {
                        if (a.get() != null)
                            it + if (a.get()!!.last() == '\n')
                                listOf(LyricText(a.get()!!.dropLast(1)), NewLine())
                            else
                                listOf(LyricText(a.get()!!))
                        else it
                    }
                } else it
            }
        }
    }
}

internal fun splitBidirectionalWords(syncedLyrics: SemanticLyrics.SyncedLyrics) {
    syncedLyrics.text.forEach { line ->
        val words = line.words
        if (words.isNullOrEmpty()) return@forEach
        val bidirectionalBarriers = findBidirectionalBarriers(line.text)
        var lastWasRtl = false
        bidirectionalBarriers.forEach { barrier ->
            val evilWordIndex =
                if (barrier.first == -1) -1 else words.indexOfFirst {
                    it.charRange.contains(barrier.first) && it.charRange.first != barrier.first
                }
            if (evilWordIndex == -1) {
                val wordIndex = if (barrier.first == -1) 0 else
                    words.indexOfFirst { it.charRange.first == barrier.first }
                words.forEachSupport(skipFirst = wordIndex) {
                    it.isRtl = barrier.second
                }
                lastWasRtl = barrier.second
                return@forEach
            }
            val evilWord = words[evilWordIndex]
            val barrierTime = min(evilWord.timeRange.first + ((words.map {
                it.timeRange.count() / it.charRange.count().toFloat()
            }.average().let { if (it.isNaN()) 100.0 else it } * (barrier.first -
                    evilWord.charRange.first))).toULong(), evilWord.timeRange.last - 1uL)
            val firstPart = SemanticLyrics.Word(
                charRange = evilWord.charRange.first..<barrier.first,
                timeRange = evilWord.timeRange.first..<barrierTime, isRtl = lastWasRtl
            )
            val secondPart = SemanticLyrics.Word(
                charRange = barrier.first..evilWord.charRange.last,
                timeRange = barrierTime..evilWord.timeRange.last, isRtl = barrier.second
            )
            words[evilWordIndex] = firstPart
            words.add(evilWordIndex + 1, secondPart)
            lastWasRtl = barrier.second
        }
    }
}

private val ltr =
    arrayOf(
        Character.DIRECTIONALITY_LEFT_TO_RIGHT,
        Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
        Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
    )
private val rtl =
    arrayOf(
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
    )

fun applyAutoWordSync(syncedLyrics: SemanticLyrics.SyncedLyrics) {
    syncedLyrics.text.forEach { line ->
        if (line.words != null) return@forEach
        if (line.text.isBlank()) return@forEach
        if (line.end <= line.start) return@forEach
        // Don't inject fake word timings into instrumental / gap markers
        if (LrcUtils.isInstrumentalLine(line.text)) return@forEach
        val tokens = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return@forEach

        // When the end time is implicit (= next line's start), the line duration includes
        // silence/gaps. Cap it to a realistic singing duration so word timings don't bleed
        // into what should be a gap. Heuristic: ~120 ms per character, capped at 8 s.
        val implicitEnd = line.end
        val effectiveEnd: ULong = if (line.endIsImplicit) {
            val totalChars = line.text.trim().length
            val estimatedMs = (totalChars * 120L).coerceIn(500L, 8000L)
            val estimated = line.start + estimatedMs.toULong()
            // Never exceed the actual implicit end
            minOf(estimated, implicitEnd)
        } else {
            implicitEnd
        }

        val lineDuration = effectiveEnd - line.start
        val totalChars = tokens.sumOf { it.length }
        var charIdx = 0
        val newWords = mutableListOf<SemanticLyrics.Word>()
        var accumulatedTime = 0uL
        for ((i, token) in tokens.withIndex()) {
            val tokenStart = line.text.indexOf(token, charIdx)
            val tokenEnd = tokenStart + token.length
            charIdx = tokenEnd
            val charRatio = token.length.toFloat() / totalChars
            val wordDuration = (lineDuration.toFloat() * charRatio).toULong()
            val wordStart = line.start + accumulatedTime
            val wordEndInclusive = if (i == tokens.lastIndex) effectiveEnd - 1uL
                else wordStart + wordDuration - 1uL
            newWords.add(SemanticLyrics.Word(wordStart, wordEndInclusive, tokenStart..<tokenEnd, isRtl = false))
            accumulatedTime += wordDuration
        }
        line.words = newWords
    }
}

fun findBidirectionalBarriers(text: CharSequence): List<Pair<Int, Boolean>> {
    val barriers = mutableListOf<Pair<Int, Boolean>>()
    if (text.isEmpty()) return barriers
    var previousDirection = text.find {
        val dir = Character.getDirectionality(it)
        dir in ltr || dir in rtl
    }?.let { Character.getDirectionality(it) in rtl } == true
    barriers.add(Pair(-1, previousDirection))
    for (i in 0 until text.length) {
        val currentDirection = Character.getDirectionality(text[i])
        val isRtl = currentDirection in rtl
        if (currentDirection !in ltr && !isRtl)
            continue
        if (previousDirection != isRtl)
            barriers.add(Pair(i, isRtl))
        previousDirection = isRtl
    }
    return barriers
}

sealed class SemanticLyrics : Parcelable {
    abstract val unsyncedText: List<Pair<String, SpeakerEntity?>>

    @Parcelize
    data class UnsyncedLyrics(override val unsyncedText: List<Pair<String, SpeakerEntity?>>) :
        SemanticLyrics()

    @Parcelize
    data class SyncedLyrics(val text: List<LyricLine>) : SemanticLyrics() {
        override val unsyncedText
            get() = text.map { it.text to it.speaker }
    }

    @Parcelize
    data class LyricLine(
        val text: String,
        val start: ULong,
        var end: ULong,
        var endIsImplicit: Boolean,
        var words: MutableList<Word>?,
        var speaker: SpeakerEntity?,
        var isTranslated: Boolean
    ) : Parcelable {
        val isClickable: Boolean
            get() = text.isNotBlank()
        val timeRange: ULongRange
            get() = start..end
    }

    @Parcelize
    data class Word(
        var begin: ULong,
        var endInclusive: ULong?,
        var charRange: @WriteWith<IntRangeParceler>() IntRange,
        var isRtl: Boolean
    ) : Parcelable {
        constructor(timeRange: ULongRange, charRange: IntRange, isRtl: Boolean)
                : this(timeRange.first, timeRange.last, charRange, isRtl)

        val timeRange
            get() = begin..endInclusive!!
    }

    object IntRangeParceler : Parceler<IntRange> {
        override fun create(parcel: Parcel) = parcel.readInt()..parcel.readInt()

        override fun IntRange.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(first)
            parcel.writeInt(last)
        }
    }
}

fun parseLrc(lyricText: String, trimEnabled: Boolean, multiLineEnabled: Boolean): SemanticLyrics? {
    val lyricSyntax = SyntacticLrc.parseLrc(lyricText, multiLineEnabled)
        ?: return null
    if (lyricSyntax.find { it is SyntacticLrc.SyncPoint || it is SyntacticLrc.WordSyncPoint } == null) {
        val out = mutableListOf<Pair<String?, SpeakerEntity?>>()
        var emptyIsEnd = false
        var shouldStartNewLine = false
        for (element in lyricSyntax) {
            when (element) {
                is SyntacticLrc.Metadata -> {
                    if (element.name == "emptyIsEnd") {
                        emptyIsEnd = element.value == "1"
                    }
                }
                is SyntacticLrc.SpeakerTag -> {
                    if (out.lastOrNull()?.let { it.first == null } == true && !shouldStartNewLine)
                        out[out.size - 1] = null to element.speaker
                    else
                        out += null to element.speaker
                    shouldStartNewLine = false
                }
                is SyntacticLrc.NewLine.SyntheticNewLine -> {
                    shouldStartNewLine = true
                }
                is SyntacticLrc.NewLine -> {
                    if (!emptyIsEnd || out.lastOrNull()?.let { it.first == null } != true)
                        out += null to null
                    else
                        out[out.size - 1] = null to null
                    shouldStartNewLine = false
                }
                is SyntacticLrc.InvalidText -> {
                    if (out.isEmpty() || shouldStartNewLine)
                        out += null to null
                    shouldStartNewLine = false
                    out[out.size - 1] = (out.last().first ?: "") + element.text to out.last().second
                }
                else -> throw IllegalStateException("unexpected type ${element.javaClass.name}")
            }
        }
        val out2 = out.map { if (it.first == null) "" to it.second else
            @Suppress("UNCHECKED_CAST") (it as Pair<String, SpeakerEntity?>) }
            .toMutableList()
        while (out2.firstOrNull()?.first?.isBlank() == true)
            out2.removeAt(0)
        return SemanticLyrics.UnsyncedLyrics(out2)
    }
    val out = mutableListOf<SemanticLyrics.LyricLine>()
    var offset = 0L
    var lastSyncPoint: ULong? = null
    var lastWordSyncPoint: ULong? = null
    var pendingLineEndSyncPoint: ULong? = null
    var speaker: SpeakerEntity? = null
    var hadVoice2 = false
    var hadLyricSinceWordSync = true
    var hadWordSyncSinceNewLine = false
    var emptyIsEnd = false
    val currentLine = mutableListOf<Pair<ULong, String?>>()
    var syncPointStreak = 0
    val compressed = mutableListOf<ULong>()
    val compressedEnds = mutableListOf<ULong>()
    for (element in lyricSyntax) {
        if (syncPointStreak > 1 && element !is SyntacticLrc.LineEndSyncPoint &&
            compressedEnds.isNotEmpty() && compressed.size == compressedEnds.size + 1) {
            compressedEnds.add(compressed.last() + compressed[compressed.size - 2] -
                    compressedEnds.last())
        }
        if (element is SyntacticLrc.SyncPoint)
            syncPointStreak++
        else if (element !is SyntacticLrc.LineEndSyncPoint)
            syncPointStreak = 0
        when (element) {
            is SyntacticLrc.Metadata -> {
                if (element.name == "offset") {
                    offset = element.value.toLong() * -1
                } else if (element.name == "emptyIsEnd") {
                    emptyIsEnd = element.value == "1"
                }
            }
            is SyntacticLrc.SyncPoint -> {
                val ts = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (syncPointStreak > 1) {
                    compressed.add(ts)
                } else {
                    if (compressed.isNotEmpty())
                        throw IllegalStateException("while parsing lrc, $compressed not empty but syncPointStreak is 1; lrc file: $lyricText")
                    lastSyncPoint = ts
                }
            }
            is SyntacticLrc.LineEndSyncPoint -> {
                val ts = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (syncPointStreak > 1) {
                    compressedEnds.add(ts)
                    if (compressedEnds.size != syncPointStreak - 1) {
                        throw IllegalStateException("while parsing lrc, compressedEnds too small: $compressedEnds, ${syncPointStreak - 1}, $lyricText")
                    }
                } else {
                    if (compressedEnds.isNotEmpty())
                        throw IllegalStateException("while parsing lrc, $compressedEnds not empty but syncPointStreak is 1; lrc file: $lyricText")
                    pendingLineEndSyncPoint = ts
                }
            }
            is SyntacticLrc.SpeakerTag -> {
                speaker = element.speaker
                if (element.speaker.isVoice2) {
                    hadVoice2 = true
                }
            }
            is SyntacticLrc.WordSyncPoint -> {
                if (!hadLyricSinceWordSync && lastWordSyncPoint != null)
                    currentLine.add(Pair(lastWordSyncPoint, null))
                lastWordSyncPoint = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (lastSyncPoint == null)
                    lastSyncPoint = lastWordSyncPoint
                hadLyricSinceWordSync = false
                hadWordSyncSinceNewLine = true
            }
            is SyntacticLrc.LyricText -> {
                hadLyricSinceWordSync = true
                currentLine.add(Pair(lastWordSyncPoint ?: lastSyncPoint!!, element.text))
            }
            is SyntacticLrc.NewLine -> {
                val words = if (currentLine.size > 1 || hadWordSyncSinceNewLine) {
                    val wout = mutableListOf<SemanticLyrics.Word>()
                    var idx = 0
                    for (i in currentLine.indices) {
                        val current = currentLine[i]
                        if (current.second == null)
                            continue
                        val oIdx = idx
                        idx += current.second!!.length
                        val textWithoutStartWhitespace = current.second!!.trimStart()
                        val startWhitespaceLength =
                            current.second!!.length - textWithoutStartWhitespace.length
                        val textWithoutWhitespaces = textWithoutStartWhitespace.trimEnd()
                        val endWhitespaceLength =
                            textWithoutStartWhitespace.length - textWithoutWhitespaces.length
                        val startIndex = oIdx + startWhitespaceLength
                        val endIndex = idx - endWhitespaceLength
                        if (startIndex == endIndex)
                            continue
                        val endInclusive = if (i + 1 < currentLine.size) {
                            currentLine[i + 1].first - 1uL
                        } else if (lastWordSyncPoint != null &&
                            lastWordSyncPoint > current.first
                        ) {
                            lastWordSyncPoint - 1uL
                        } else if (pendingLineEndSyncPoint != null &&
                            pendingLineEndSyncPoint > current.first
                        ) {
                            pendingLineEndSyncPoint - 1uL
                        } else null
                        if (endInclusive == null || endInclusive > current.first)
                            wout.add(
                                SemanticLyrics.Word(
                                    current.first, endInclusive,
                                    startIndex..<endIndex,
                                    isRtl = false
                                )
                            )
                    }
                    wout
                } else null
                var text = currentLine.joinToString("") { it.second ?: "" }
                if (text.isNotBlank() || !emptyIsEnd &&
                    (lastWordSyncPoint != null || lastSyncPoint != null)) {
                    if (trimEnabled) {
                        val orig = text
                        text = orig.trimStart()
                        val startDiff = orig.length - text.length
                        text = text.trimEnd()
                        val iter = words?.listIterator()
                        iter?.forEach {
                            if (it.charRange.last.toLong() - startDiff < 0
                                || it.charRange.first.toLong() - startDiff >= text.length
                            )
                                iter.remove()
                            else
                                it.charRange = (it.charRange.first - startDiff)
                                    .coerceAtLeast(0)..(it.charRange.last - startDiff)
                                    .coerceAtMost(text.length - 1)
                        }
                    }
                    val start = if (currentLine.isNotEmpty()) lastSyncPoint
                        ?: currentLine.first().first
                    else lastWordSyncPoint ?: lastSyncPoint!!
                    val lastWordSyncForEnd = lastWordSyncPoint?.let { it - 1uL }
                    val lastWordBegin = words?.lastOrNull()?.begin
                    val end = pendingLineEndSyncPoint?.also { pendingLineEndSyncPoint = null }
                        ?: if (lastWordSyncForEnd != null && lastWordBegin != null &&
                            lastWordBegin < lastWordSyncForEnd) lastWordSyncForEnd else null
                    out.add(
                        SemanticLyrics.LyricLine(
                            text, start, end ?: 0uL,
                            end == null, words, speaker, false
                        )
                    )
                    compressed.forEachIndexed { i, it ->
                        val diff = it - start
                        out.add(
                            out.last().copy(
                                start = it,
                                end = end?.plus(diff) ?: 0uL,
                                words = words?.map { w ->
                                    w.copy(
                                        begin = w.begin + diff,
                                        endInclusive = if (compressedEnds.isNotEmpty())
                                            compressedEnds[i] else w.endInclusive?.plus(diff)
                                    )
                                }?.toMutableList()
                            )
                        )
                    }
                } else if (lastWordSyncPoint != null || lastSyncPoint != null) {
                    out.lastOrNull()?.let {
                        out[out.size - 1] = it.copy(end = lastWordSyncPoint ?: lastSyncPoint!!,
                            endIsImplicit = false)
                        val lrcWords = it.words
                        if (lrcWords?.lastOrNull()?.let { w -> w.endInclusive == null } == true) {
                            lrcWords.last().endInclusive = lastWordSyncPoint ?: lastSyncPoint!!
                        }
                    }
                }
                compressed.clear()
                compressedEnds.clear()
                currentLine.clear()
                lastSyncPoint = null
                lastWordSyncPoint = null
                hadWordSyncSinceNewLine = false
                speaker = null
                hadLyricSinceWordSync = true
            }
            else -> {}
        }
    }
    out.sortBy { it.start }
    var previousLyric: SemanticLyrics.LyricLine? = null
    out.forEach { lyric ->
        if (!hadVoice2) {
            lyric.speaker = when (lyric.speaker) {
                SpeakerEntity.Voice1 -> SpeakerEntity.Voice
                SpeakerEntity.Voice1Background -> SpeakerEntity.VoiceBackground
                else -> lyric.speaker
            }
        }
        val mainEnd = if (lyric.start == previousLyric?.start) out.firstOrNull {
            it.start == lyric.start && !it.endIsImplicit
        }?.end else null
        val words = lyric.words
        val wordWithoutEnd = words?.lastOrNull()
        if (wordWithoutEnd != null && wordWithoutEnd.endInclusive == null) {
            wordWithoutEnd.endInclusive = mainEnd?.takeIf { it > wordWithoutEnd.begin }
                ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                    ?.takeIf { it > wordWithoutEnd.begin }
                ?: run {
                    wordWithoutEnd.begin + (words.subList(0, words.size - 1)
                        .map { it.timeRange.count() / it.charRange.count().toFloat() }
                        .average().let { if (it.isNaN()) 100.0 else it } *
                            lyric.text.substring(wordWithoutEnd.charRange).length).toULong()
                }
        }
        if (lyric.endIsImplicit) {
            if (mainEnd != null) {
                lyric.end = mainEnd
                lyric.endIsImplicit = false
            } else {
                lyric.end = wordWithoutEnd?.endInclusive
                    ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                            ?: Long.MAX_VALUE.toULong()
                lyric.endIsImplicit = wordWithoutEnd == null
            }
        }
        lyric.isTranslated = lyric.start == previousLyric?.start &&
                (previousLyric.text.isNotBlank() || lyric.text.isBlank())
        previousLyric = lyric
    }
    while (out.firstOrNull()?.text?.isBlank() == true)
        out.removeAt(0)
    return SemanticLyrics.SyncedLyrics(out).also { splitBidirectionalWords(it) }
}

object UsltFrameDecoder {
    sealed class Result {
        data class Uslt(val language: String, val description: String, val text: String) : Result()
        data class Sylt(
            val language: String,
            val contentType: Int,
            val description: String,
            val text: List<Line>
        ) : Result() {
            data class Line(val timestamp: UInt, val text: String)
        }
    }

    private const val ID3_TEXT_ENCODING_ISO_8859_1 = 0
    private const val ID3_TEXT_ENCODING_UTF_16 = 1
    private const val ID3_TEXT_ENCODING_UTF_16BE = 2
    private const val ID3_TEXT_ENCODING_UTF_8 = 3

    fun decode(id3Data: ParsableByteArray): Result.Uslt? {
        if (id3Data.limit() < 4) {
            return null
        }

        val encoding = id3Data.readUnsignedByte()
        val charset = getCharsetName(encoding)
        val lang = ByteArray(3)
        id3Data.readBytes(lang, 0, 3)
        val language = decodeStringIfValid(lang, 0, 3, Charset.forName("ISO-8859-1"))
        if (delimiterLength(encoding) == 1 &&
            (id3Data.peekUnsignedByte() == 1 || id3Data.peekUnsignedByte() == 2)
        ) {
            return null
        }
        val rest = ByteArray(id3Data.limit() - 4)
        id3Data.readBytes(rest, 0, id3Data.limit() - 4)

        val descriptionEndIndex = indexOfEos(rest, 0, encoding)
        val description = decodeStringIfValid(rest, 0, descriptionEndIndex, charset)
        val textStartIndex = descriptionEndIndex + delimiterLength(encoding)
        val textEndIndex = indexOfEos(rest, textStartIndex, encoding)
        val text = decodeStringIfValid(rest, textStartIndex, textEndIndex, charset)
        return Result.Uslt(language, description, text)
    }

    fun decodeSylt(sampleRate: Int, id3Data: ParsableByteArray): Result.Sylt? {
        if (id3Data.limit() < 1) {
            return null
        }
        val encoding = id3Data.readUnsignedByte()
        if (id3Data.limit() < 8 + 2 * delimiterLength(encoding)) {
            return null
        }
        val charset = getCharsetName(encoding)
        val lang = ByteArray(3)
        id3Data.readBytes(lang, 0, 3)
        val language = decodeStringIfValid(lang, 0, 3, Charset.forName("ISO-8859-1"))
        val timestampFormat = id3Data.readUnsignedByte()
        val contentType = id3Data.readUnsignedByte()
        val rest = ByteArray(id3Data.limit() - 6)
        id3Data.readBytes(rest, 0, id3Data.limit() - 6)

        val descriptionEndIndex = indexOfEos(rest, 0, encoding)
        val description = decodeStringIfValid(rest, 0, descriptionEndIndex, charset)
        var processed = descriptionEndIndex + delimiterLength(encoding)
        val syltLines = mutableListOf<Result.Sylt.Line>()
        while (rest.size - processed > 1) {
            val textEndIndex = indexOfEos(rest, processed, encoding)
            val text = decodeStringIfValid(rest, processed, textEndIndex, charset)
            processed = textEndIndex + delimiterLength(encoding) + 4
            if (processed > rest.size) {
                return null
            }
            val timestamp = decodeTimestamp(rest, processed - 4, timestampFormat, sampleRate)
            syltLines.add(Result.Sylt.Line(timestamp, text))
        }
        return Result.Sylt(language, contentType, description, syltLines)
    }

    private fun getCharsetName(encodingByte: Int): Charset {
        val name = when (encodingByte) {
            ID3_TEXT_ENCODING_UTF_16 -> "UTF-16"
            ID3_TEXT_ENCODING_UTF_16BE -> "UTF-16BE"
            ID3_TEXT_ENCODING_UTF_8 -> "UTF-8"
            ID3_TEXT_ENCODING_ISO_8859_1 -> "ISO-8859-1"
            else -> throw IllegalArgumentException("unsupported charset $encodingByte")
        }
        return Charset.forName(name)
    }

    private fun indexOfEos(data: ByteArray, fromIndex: Int, encoding: Int): Int {
        var terminationPos = indexOfZeroByte(data, fromIndex)

        if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
            return terminationPos
        }

        while (terminationPos < data.size - 1) {
            if (terminationPos % 2 == 0 && data[terminationPos + 1] == 0.toByte()) {
                return terminationPos
            }
            terminationPos = indexOfZeroByte(data, terminationPos + 1)
        }

        return data.size
    }

    private fun indexOfZeroByte(data: ByteArray, fromIndex: Int): Int {
        for (i in fromIndex until data.size) {
            if (data[i] == 0.toByte()) {
                return i
            }
        }
        return data.size
    }

    private fun delimiterLength(encodingByte: Int): Int {
        return if (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
            1
        else
            2
    }

    private fun decodeStringIfValid(
        data: ByteArray,
        from: Int,
        to: Int,
        charset: Charset
    ): String {
        return if (to <= from || to > data.size) {
            ""
        } else String(data, from, to - from, charset)
    }

    private fun decodeTimestamp(data: ByteArray, pos: Int, format: Int, sampleRate: Int): UInt {
        val dec =
            ((((data[pos].toUInt() shl 24) and 0xff000000U) or ((data[pos + 1].toUInt() shl 16)
                    and 0xff0000U)) or ((data[pos + 2].toUInt() shl 8) and 0xff00U)) or (data[pos + 3].toUInt() and 0xffU)
        return when (format) {
            1 -> mpegFramePositionToMs(sampleRate, dec.toLong()).toUInt()
            2 -> dec
            else -> throw IllegalArgumentException("bad id3 timestamp format $format")
        }
    }

    private fun mpegFramePositionToMs(sampleRate: Int, pos: Long): Long {
        val samplesPerFrame = when (sampleRate) {
            32000, 44100, 48000 -> 1152
            16000, 22050, 24000 -> 576
            8000, 11025, 12000 -> 576
            else -> throw IllegalArgumentException("bad mpeg sample rate $sampleRate")
        }
        return (pos * samplesPerFrame * 1000L) / sampleRate
    }
}

fun UsltFrameDecoder.Result.Sylt.toSyncedLyrics(trimEnabled: Boolean): SemanticLyrics.SyncedLyrics {
    val out = mutableListOf<SemanticLyrics.LyricLine>()
    var i = 0
    while (i < text.size) {
        var j = i + 1
        while (j < text.size &&
            !text[j].text.trimStart { it == '\t' || it == ' ' || it == '\r' }.startsWith("\n")
        ) {
            j++
        }
        var idx = 0
        val wout = mutableListOf<SemanticLyrics.Word>()
        for (k in i..<j) {
            val it = text[k]
            val next = if (k + 1 < j) text[k + 1] else null
            val oIdx = idx
            idx += it.text.length
            val textWithoutStartWhitespace = it.text.trimStart()
            val startWhitespaceLength =
                it.text.length - textWithoutStartWhitespace.length
            val textWithoutWhitespaces = textWithoutStartWhitespace.trimEnd()
            val endWhitespaceLength =
                textWithoutStartWhitespace.length - textWithoutWhitespaces.length
            val startIndex = oIdx + startWhitespaceLength
            val endIndex = idx - endWhitespaceLength
            if (startIndex == endIndex)
                continue
            val endInclusive = if (next != null && next.timestamp > 0uL) {
                next.timestamp - 1uL
            } else null
            if (endInclusive == null || endInclusive > it.timestamp)
                wout.add(
                    SemanticLyrics.Word(
                        it.timestamp.toULong(), endInclusive,
                        startIndex..<endIndex,
                        isRtl = false
                    )
                )
        }
        var string = text.subList(i, j).joinToString("") { it.text }
        val nli1 = string.indexOf('\n')
        if (nli1 != -1 && string.take(nli1)
                .trimStart { it == '\t' || it == ' ' || it == '\r' }.isEmpty()
        ) {
            string = string.substring(nli1 + 1)
        }
        if (j < text.size) {
            var nli = text[j].text.indexOf('\n')
            if (nli == -1) {
                throw IllegalStateException("nli == -1, can't happen")
            }
            if (nli > 0 && text[j].text[nli - 1] == '\r')
                nli--
            string += text[j].text.substring(0, nli)
        }
        if (trimEnabled) {
            val orig = string
            string = orig.trimStart()
            val startDiff = orig.length - string.length
            string = string.trimEnd()
            val iter = wout.listIterator()
            iter.forEach {
                if (it.charRange.last.toLong() - startDiff < 0
                    || it.charRange.first.toLong() - startDiff >= string.length
                )
                    iter.remove()
                else
                    it.charRange = (it.charRange.first - startDiff)
                        .coerceAtLeast(0)..(it.charRange.last - startDiff)
                        .coerceAtMost(string.length - 1)
            }
        }
        val explicitEnd = if (i < j - 1 && text[j - 1].text.isBlank())
            text[j - 1].timestamp.toULong() - 1uL
        else if (wout.size > 1) wout.last().endInclusive else null
        out.add(
            SemanticLyrics.LyricLine(
                string, text[i].timestamp.toULong(), explicitEnd ?: 0uL, explicitEnd == null,
                if (wout.size > 1) wout else null, null,
                false
            )
        )
        i = j
    }
    out.sortBy { it.start }
    var previousTimestamp = ULong.MAX_VALUE
    out.forEach { lyric ->
        val mainEnd = if (lyric.start == previousTimestamp) out.firstOrNull {
            it.start == lyric.start && !it.endIsImplicit
        }?.end else null
        val syltWords = lyric.words
        val wordWithoutEnd = syltWords?.lastOrNull()
        if (wordWithoutEnd != null && wordWithoutEnd.endInclusive == null) {
            wordWithoutEnd.endInclusive = mainEnd?.takeIf { it > wordWithoutEnd.begin }
                ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                    ?.takeIf { it > wordWithoutEnd.begin }
                ?: run {
                    wordWithoutEnd.begin + (syltWords.subList(0, syltWords.size - 1)
                        .map { it.timeRange.count() / it.charRange.count().toFloat() }
                        .average().let { if (it.isNaN()) 100.0 else it } *
                            lyric.text.substring(wordWithoutEnd.charRange).length).toULong()
                }
        }
        if (lyric.endIsImplicit) {
            if (mainEnd != null) {
                lyric.end = mainEnd
                lyric.endIsImplicit = false
            } else {
                lyric.end = wordWithoutEnd?.endInclusive
                    ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                            ?: Long.MAX_VALUE.toULong()
                lyric.endIsImplicit = wordWithoutEnd == null
            }
        }
        lyric.isTranslated = lyric.start == previousTimestamp
        previousTimestamp = lyric.start
    }
    while (out.firstOrNull()?.text?.isBlank() == true)
        out.removeAt(0)
    return SemanticLyrics.SyncedLyrics(out).also { splitBidirectionalWords(it) }
}

private const val tt = "http://www.w3.org/ns/ttml"
private const val ttm = "http://www.w3.org/ns/ttml#metadata"
private const val ttp = "http://www.w3.org/ns/ttml#parameter"
private const val itunes = "http://itunes.apple.com/lyric-ttml-extensions"
private const val itunesInternal = "http://music.apple.com/lyric-ttml-internal"

private fun XmlPullParser.skipToEndOfTag() {
    if (eventType != XmlPullParser.START_TAG) return
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

private fun XmlPullParser.nextAndThrowIfNotEnd() {
    if (next() != XmlPullParser.END_TAG)
        throw XmlPullParserException("expected end tag in nextAndThrowIfNotEnd()")
}

private fun XmlPullParser.nextAndThrowIfNotText() {
    if (next() != XmlPullParser.TEXT)
        throw XmlPullParserException("expected end tag in nextAndThrowIfNotText()")
}

private class TtmlTimeTracker(private val parser: XmlPullParser, private val isApple: Boolean) {
    private val effectiveFrameRate: Float
    private val subFrameRate: Int
    private val tickRate: Int

    init {
        val frameRate = parser.getAttributeValue(ttp, "frameRate")?.toInt()
            ?: parser.getAttributeValue(null, "frameRate")?.toInt() ?: 30
        val frameRateMultiplier = (parser.getAttributeValue(ttp, "frameRateMultiplier")
            ?: parser.getAttributeValue(null, "frameRateMultiplier"))
            ?.split(" ")?.let { parts ->
                parts[0].toInt() / parts[1].toInt().toFloat()
            } ?: 1f
        effectiveFrameRate = frameRate * frameRateMultiplier
        subFrameRate = parser.getAttributeValue(ttp, "subFrameRate")?.toInt()
            ?: parser.getAttributeValue(null, "subFrameRate")?.toInt() ?: 1
        tickRate = parser.getAttributeValue(ttp, "tickRate")?.toInt()
            ?: parser.getAttributeValue(null, "tickRate")?.toInt() ?: 1
    }

    private val appleTimeRegex = Regex("^(?:([0-9]+):)?(?:([0-9]+):)?([0-9]+(?:\\.[0-9]+)?)?$")
    private val clockTimeRegex =
        Regex("^([0-9]{2,}):([0-9]{2}):([0-9]{2})(?:(\\.[0-9]+)|:([0-9]{2})(?:\\.([0-9]+))?)?$")
    private val offsetTimeRegex = Regex("^([0-9]+(?:\\.[0-9]+)?)(h|m|s|ms|f|t)$")
    var audioOffset: Long? = null
    fun parseTimestampMs(input: String?, offset: Long, negative: Boolean): Long? {
        if (input?.isEmpty() != false) return null
        val multiplier = if (negative && input.startsWith('-')) -1 else 1
        val cleanInput = if (multiplier == -1) input.substring(1) else input
        
        // Try clock time (HH:MM:SS.mmm)
        val clockMatch = clockTimeRegex.matchEntire(cleanInput)
        if (clockMatch != null) {
            val hours = clockMatch.groupValues[1].toDouble()
            val minutes = clockMatch.groupValues[2].toDouble()
            val seconds = (clockMatch.groupValues[3] + clockMatch.groupValues[4]).toDouble()
            val frameSecs = clockMatch.groupValues[5].toDoubleOrNull()
                ?.div(effectiveFrameRate) ?: 0.0
            val subFrameSecs = clockMatch.groupValues[6].toDoubleOrNull()
                ?.div(subFrameRate)?.div(effectiveFrameRate) ?: 0.0
            return ((hours * 3600000 + minutes * 60000 + (seconds + frameSecs +
                    subFrameSecs) * 1000).toLong() + offset + (audioOffset ?: 0L)) * multiplier
        }

        // Try apple/simple time (MM:SS.mmm or HH:MM:SS.mmm or SS.mmm)
        val appleMatch = appleTimeRegex.matchEntire(cleanInput)
        if (appleMatch != null && appleMatch.groupValues[3].isNotEmpty()) {
            val hours = if (appleMatch.groupValues[2].isNotEmpty())
                appleMatch.groupValues[1].toDoubleOrNull() ?: 0.0 else 0.0
            val minutes = if (appleMatch.groupValues[2].isNotEmpty())
                appleMatch.groupValues[2].toDoubleOrNull() ?: 0.0 else
                appleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val seconds = appleMatch.groupValues[3].toDoubleOrNull() ?: 0.0
            return ((hours * 3600000 + minutes * 60000 + seconds * 1000).toLong() + (if (isApple) 0L else offset) + (audioOffset ?: 0L)) * multiplier
        }

        // Try offset time with metric suffix (e.g. 5.5s, 500ms)
        val offsetMatch = offsetTimeRegex.matchEntire(cleanInput)
        if (offsetMatch != null) {
            var time = offsetMatch.groupValues[1].toDouble()
            when (offsetMatch.groupValues[2]) {
                "h" -> time *= 3600000.0
                "m" -> time *= 60000.0
                "s" -> time *= 1000.0
                "ms" -> {}
                "f" -> time /= effectiveFrameRate / 1000.0
                "t" -> time /= tickRate / 1000.0
            }
            if (isApple) {
                return (time.toLong() + (audioOffset ?: 0L)) * multiplier
            }
            return (time.toLong() + offset + (audioOffset ?: 0L)) * multiplier
        }

        // Try raw seconds or milliseconds string
        val num = cleanInput.toDoubleOrNull()
        if (num != null) {
            val ms = if (num > 100000) num.toLong() else (num * 1000).toLong()
            return (ms + offset + (audioOffset ?: 0L)) * multiplier
        }

        return null
    }

    private fun parseRange(offset: ULong): ULongRange? {
        var begin = parseTimestampMs(
            parser.getAttributeValue(null, "begin") ?: parser.getAttributeValue("", "begin"),
            offset.toLong(),
            false
        )?.toULong()
        val dur = parseTimestampMs(
            parser.getAttributeValue(null, "dur") ?: parser.getAttributeValue("", "dur"),
            0L,
            false
        )?.toULong()
        var end = parseTimestampMs(
            parser.getAttributeValue(null, "end") ?: parser.getAttributeValue("", "end"),
            offset.toLong(),
            false
        )?.toULong()
        if (begin == null && end == null || end == null && dur == null
            || begin == null && dur == null
        )
            return null
        if (begin == null && dur != null)
            begin = (end ?: 0uL) - dur
        if (end == null && dur != null)
            end = (begin ?: 0uL) + dur
        return (begin ?: 0uL)..(end ?: 0uL)
    }

    private class TtmlLevel(val time: ULongRange?, val level: Int, var seq: ULong?)
    private val stack = mutableListOf<TtmlLevel>()
    fun beginBlock() {
        val isSeq = parser.getAttributeValue("", "timeContainer").let {
            when (it) {
                "par", null -> false
                "seq" -> true
                else -> throw XmlPullParserException("unknown timeContainer value $it")
            }
        }
        val last = stack.lastOrNull()
        val range = parseRange(last?.seq ?: last?.time?.first ?: 0uL)
        val frange = range ?: last?.time
        stack.add(
            TtmlLevel(
                frange,
                (last?.level ?: 0) + if (range != null) 1 else 0,
                if (isSeq) frange?.first else null
            )
        )
    }

    fun getTime(): ULongRange? {
        return stack.lastOrNull()?.time
    }

    fun getLevel(): Int {
        return stack.lastOrNull()?.level ?: 0
    }

    fun endBlock() {
        val removed = stack.removeAt(stack.size - 1)
        stack.lastOrNull()?.let {
            it.seq = if (it.seq != null) removed.time?.last else null
        }
    }
}

private class TtmlParserState(
    private val parser: XmlPullParser,
    private val timer: TtmlTimeTracker
) {
    data class Text(val text: String, val time: ULongRange?, val role: String?)
    data class P(
        val texts: List<Text>, val time: ULongRange?, val agent: String?,
        val songPart: String?, val key: String?, val role: String?, val translated: Boolean = false
    )

    private var texts: MutableList<Text>? = null
    val paragraphs = mutableListOf<P>()

    fun parse(
        time: ULongRange? = null,
        level: Int = 0,
        plevel: Int = 0,
        agent: String? = null,
        songPart: String? = null,
        key: String? = null,
        role: String? = null
    ) {
        var activeTime = time
        var activeAgent = agent
        var activeSongPart = songPart
        var activeKey = key
        var activeRole = role
        var activeLevel = level
        var activePLevel = plevel
        if (parser.eventType == XmlPullParser.TEXT) {
            if (parser.text.isBlank() && parser.text.contains("\n"))
                return
            if (texts == null) {
                if (parser.text.isNotBlank())
                    throw IllegalStateException("found TEXT \"${parser.text}\" but text isn't allowed here (forgot <p>?)")
                return
            }
            if (activeLevel < activePLevel)
                activeTime = null
            texts!!.add(Text(parser.text, activeTime, activeRole))
            return
        }
        if (parser.eventType != XmlPullParser.START_TAG)
            throw IllegalStateException("expected START_TAG or TEXT, found ${parser.eventType}!")
        if (parser.name != "span")
            parser.getAttributeValue(ttm, "agent")?.let { activeAgent = it }
        parser.getAttributeValue(ttm, "role")?.let { activeRole = it }
        timer.beginBlock()
        activeTime = timer.getTime()
        activeLevel = timer.getLevel()
        var isP = false
        when (parser.name) {
            "div" -> {
                parser.getAttributeValue(itunes, "song-part")?.let { activeSongPart = it }
                parser.getAttributeValue(itunesInternal, "songPart")?.let { activeSongPart = it }
            }
            "p" -> {
                parser.getAttributeValue(itunesInternal, "key")?.let { activeKey = it }
                texts = mutableListOf()
                isP = true
                activePLevel = activeLevel
            }
            "body", "span" -> {}
            else -> throw IllegalStateException("unknown tag ${parser.name}, wanted body/span/div/p")
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                activePLevel = activeLevel
            }
            parse(activeTime, activeLevel, activePLevel, activeAgent, activeSongPart, activeKey, activeRole)
        }
        timer.endBlock()
        if (isP) {
            while (texts!!.isNotEmpty() && texts!![0].text.isBlank())
                texts!!.removeAt(0)
            while (texts!!.isNotEmpty() && texts!![texts!!.size - 1].text.isBlank())
                texts!!.removeAt(texts!!.size - 1)
            paragraphs.add(P(texts!!, activeTime, activeAgent, activeSongPart, activeKey, activeRole))
            texts = null
        }
        if (parser.eventType != XmlPullParser.END_TAG)
            throw IllegalStateException("expected END_TAG, found ${parser.eventType}!")
    }
}

fun parseTtml(audioMimeType: String?, lyricText: String): SemanticLyrics? {
    // Reject oversized payloads and external DTD/entity declarations (XXE / billion-laughs hardening).
    if (lyricText.isBlank() || lyricText.length > 2_000_000) return null
    val upperPrefix = lyricText.take(4096).uppercase()
    if (upperPrefix.contains("<!DOCTYPE") || upperPrefix.contains("<!ENTITY")) {
        try {
            Log.w(TAG, "Rejected TTML containing DOCTYPE/ENTITY declaration")
        } catch (_: Throwable) {}
        return null
    }
    val formattedLyricText = lyricText
        .replace(Regex("&(?!#?[a-zA-Z0-9]+;)"), "&amp;")
    val parser = try {
        Xml.newPullParser()
    } catch (_: Throwable) {
        try {
            org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        } catch (_: Throwable) {
            return null
        }
    } ?: return null
    try {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    } catch (_: Throwable) {}
    try {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    } catch (_: Throwable) {}
    try {
        parser.setInput(StringReader(formattedLyricText))
        var ev = parser.eventType
        while (ev != XmlPullParser.START_TAG && ev != XmlPullParser.END_DOCUMENT) {
            ev = parser.next()
        }
        if (ev == XmlPullParser.END_DOCUMENT || !parser.name.equals("tt", ignoreCase = true)) {
            return null
        }
    } catch (_: Exception) {
        return null
    }
    var timing: String? = parser.getAttributeValue(itunesInternal, "timing")
        ?: parser.getAttributeValue(itunes, "timing")
        ?: parser.getAttributeValue(null, "timing")
        ?: parser.getAttributeValue("", "timing")
    if (timing == null) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName.equals("timing", ignoreCase = true) || attrName.endsWith(":timing", ignoreCase = true)) {
                timing = parser.getAttributeValue(i)
                break
            }
        }
    }
    var hasItunesNamespace = timing != null
    if (!hasItunesNamespace) {
        for (i in 0..<parser.getNamespaceCount(parser.depth)) {
            if (parser.getNamespaceUri(i) == itunes || parser.getNamespaceUri(i) == itunesInternal) {
                hasItunesNamespace = true
                break
            }
        }
    }
    val peopleToType = hashMapOf<String, String>()
    val people = hashMapOf<String, MutableList<String>>()
    val itunesTranslations = hashMapOf<String, HashMap<String, out List<Pair<String?, String>>>>()
    val timer = TtmlTimeTracker(parser, hasItunesNamespace)
    try {
        parser.nextTag()
    } catch (_: Exception) {
        return null
    }
    if (parser.eventType == XmlPullParser.END_TAG && parser.name.equals("tt", ignoreCase = true)) {
        return null
    }
    if (parser.name.equals("head", ignoreCase = true)) {
        try {
            while (parser.nextTag() != XmlPullParser.END_TAG) {
                if (parser.name == "metadata") {
                    while (parser.nextTag() != XmlPullParser.END_TAG) {
                        if (parser.name == "agent") {
                            val id = parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "id")
                                ?: parser.getAttributeValue(null, "id")
                                ?: parser.getAttributeValue("", "id")
                            val type = parser.getAttributeValue("", "type") ?: parser.getAttributeValue(null, "type") ?: "person"
                            if (id != null) {
                                people.getOrPut(type) { mutableListOf() }.add(id)
                                peopleToType[id] = type
                            }
                            while (parser.nextTag() != XmlPullParser.END_TAG) {
                                if (parser.name == "name") {
                                    parser.nextAndThrowIfNotText()
                                    parser.nextAndThrowIfNotEnd()
                                } else {
                                    parser.skipToEndOfTag()
                                }
                            }
                        } else if (parser.name == "iTunesMetadata") {
                            while (parser.nextTag() != XmlPullParser.END_TAG) {
                                when (parser.name) {
                                    "songwriters" -> {
                                        while (parser.nextTag() != XmlPullParser.END_TAG) {
                                            if (parser.name == "songwriter") {
                                                parser.nextAndThrowIfNotText()
                                                parser.nextAndThrowIfNotEnd()
                                            } else {
                                                parser.skipToEndOfTag()
                                            }
                                        }
                                    }
                                    "audio" -> {
                                        val role = parser.getAttributeValue(null, "role")
                                        if (when (role) {
                                                "spatial" -> audioMimeType == MimeTypes.AUDIO_AC3 ||
                                                        audioMimeType == MimeTypes.AUDIO_E_AC3 ||
                                                        audioMimeType == MimeTypes.AUDIO_AC4
                                                "ext-not-spatial" -> !(audioMimeType == MimeTypes.AUDIO_AC3 ||
                                                        audioMimeType == MimeTypes.AUDIO_E_AC3 ||
                                                        audioMimeType == MimeTypes.AUDIO_AC4)
                                                "ext-always" -> true
                                                else -> true
                                            }
                                        ) {
                                            val parsed = timer.parseTimestampMs(
                                                parser.getAttributeValue(
                                                    null,
                                                    "lyricOffset"
                                                ), 0L, true
                                            )
                                            if (timer.audioOffset != null)
                                                timer.audioOffset = timer.audioOffset!! + (parsed ?: 0L)
                                            else
                                                timer.audioOffset = parsed
                                        }
                                        parser.skipToEndOfTag()
                                    }
                                    "translations" -> {
                                        while (parser.nextTag() != XmlPullParser.END_TAG) {
                                            if (parser.name == "translation") {
                                                val lang = parser.getAttributeValue(
                                                    "http://www.w3.org/XML/1998/namespace",
                                                    "lang"
                                                ) ?: parser.getAttributeValue(null, "lang") ?: "en"
                                                val out = hashMapOf<String, ArrayList<Pair<String?, String>>>()
                                                while (parser.nextTag() != XmlPullParser.END_TAG) {
                                                    if (parser.name == "text") {
                                                        val `for` = parser.getAttributeValue(null, "for")
                                                            ?: parser.getAttributeValue("", "for")
                                                        val roleStack = mutableListOf<String>()
                                                        var textContent = ""
                                                        while (true) {
                                                            when (parser.next()) {
                                                                XmlPullParser.TEXT -> {
                                                                    textContent += parser.text
                                                                }
                                                                XmlPullParser.START_TAG -> {
                                                                    if (parser.name == "span") {
                                                                        val role = parser.getAttributeValue(ttm, "role")
                                                                            ?: parser.getAttributeValue(null, "role")
                                                                        roleStack.add(role ?: "")
                                                                    }
                                                                }
                                                                XmlPullParser.END_TAG -> {
                                                                    if (parser.name == "span" && roleStack.isNotEmpty()) {
                                                                        roleStack.removeAt(roleStack.lastIndex)
                                                                    } else if (parser.name == "text" && roleStack.isEmpty()) {
                                                                        if (`for` != null && textContent.isNotBlank()) {
                                                                            out.getOrPut(`for`) { ArrayList() }
                                                                                .add(roleStack.lastOrNull()?.ifEmpty { null } to textContent)
                                                                        }
                                                                        break
                                                                    }
                                                                }
                                                                XmlPullParser.END_DOCUMENT -> break
                                                                else -> {}
                                                            }
                                                        }
                                                    } else {
                                                        parser.skipToEndOfTag()
                                                    }
                                                }
                                                itunesTranslations[lang] = out
                                            } else {
                                                parser.skipToEndOfTag()
                                            }
                                        }
                                    }
                                    else -> parser.skipToEndOfTag()
                                }
                            }
                        } else parser.skipToEndOfTag()
                    }
                } else
                    parser.skipToEndOfTag()
            }
            parser.nextTag()
            if (parser.eventType == XmlPullParser.END_TAG && parser.name.equals("tt", ignoreCase = true)) {
                return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error while parsing TTML head/metadata: ${e.message}")
        }
    }
    if (!parser.name.equals("body", ignoreCase = true)) {
        while (parser.eventType != XmlPullParser.END_DOCUMENT && !parser.name.equals("body", ignoreCase = true)) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("body", ignoreCase = true)) {
                break
            }
            try {
                parser.next()
            } catch (_: Exception) {
                break
            }
        }
    }
    if (parser.eventType == XmlPullParser.END_DOCUMENT || !parser.name.equals("body", ignoreCase = true)) {
        return null
    }
    val state = TtmlParserState(parser, timer)
    try {
        state.parse()
    } catch (e: Exception) {
        Log.w(TAG, "Error in TtmlParserState.parse: ${e.message}")
    }
    val paragraphs = state.paragraphs.flatMap {
        if (it.texts.isEmpty()) return@flatMap listOf(it)
        val out = mutableListOf<TtmlParserState.P>()
        var idx = it.texts.indexOfFirst { i -> i.role != it.texts[0].role }
        var cur = 0
        do {
            if (cur == 0 && idx == -1 && !(it.texts.firstOrNull()?.text?.startsWith('(') == true
                        && it.texts.lastOrNull()?.text?.endsWith(')') == true &&
                        (it.texts.firstOrNull()?.role ?: it.role) == "x-bg")
            )
                out.add(it.copy(role = it.texts.firstOrNull()?.role ?: it.role))
            else {
                while ((cur < idx || (idx == -1 && cur < it.texts.size)) && it.texts[cur].text.isBlank())
                    cur++
                var iIdx = idx
                while (cur < iIdx && iIdx > 0 && it.texts[iIdx - 1].text.isBlank())
                    iIdx--
                if (cur < iIdx || (iIdx == -1 && cur < it.texts.size)) {
                    val t =
                        it.texts.subList(cur, iIdx.let { i -> if (i == -1) it.texts.size else i })
                            .toMutableList()
                    if (t.firstOrNull()?.text?.startsWith('(') == true
                        && t.lastOrNull()?.text?.endsWith(')') == true
                        && (t.firstOrNull()?.role ?: it.role) == "x-bg"
                    ) {
                        t[0] = t.first().copy(text = t.first().text.substring(1))
                        t[t.size - 1] = t.last()
                            .copy(text = t.last().text.substring(0, t.last().text.length - 1))
                    }
                    out.add(
                        it.copy(
                            texts = t,
                            time = t.lastOrNull()?.time?.last?.let { other ->
                                t.firstOrNull()?.time?.first?.rangeTo(other)
                            } ?: it.time, role = t.firstOrNull()?.role ?: it.role))
                }
            }
            cur = idx
            if (cur != -1)
                idx = it.texts.subList(cur, it.texts.size)
                    .indexOfFirst { i -> i.role != it.texts[cur].role }
            if (idx != -1) idx += cur
        } while (cur != -1)
        out
    }.toMutableList()
    itunesTranslations.forEach { lang ->
        lang.value.forEach { line ->
            val indices = paragraphs.flatMapIndexed { i, it -> if (it.key == line.key && !it.translated)
                listOf(i) else emptyList() }
            if (indices.isNotEmpty()) {
                var offset = 0
                line.value.forEachIndexed { occurrenceIndex, roleAndText ->
                    val targetIdx = indices.getOrNull(occurrenceIndex) ?: indices.last()
                    if (targetIdx + offset < paragraphs.size) {
                        val untranslated = paragraphs[targetIdx + offset]
                        paragraphs.add(
                            targetIdx + ++offset,
                            untranslated.copy(
                                texts = listOf(
                                    TtmlParserState.Text(
                                        roleAndText.second.let {
                                            if (roleAndText.first == "x-bg" && it.startsWith('(') && it.endsWith(')'))
                                                it.substring(1, it.length - 1) else it
                                        },
                                        time = untranslated.time,
                                        role = null
                                    )
                                ),
                                translated = true
                            )
                        )
                    }
                }
            }
        }
    }
    val pToSide = if (Flags.TTML_AGENT_SMART_SIDES) {
        val calculatedSideMap = hashMapOf<Int, Boolean>()
        var agent = paragraphs.firstOrNull()?.agent
        var side = paragraphs.firstOrNull()?.let { peopleToType[it.agent] == "other" } == true
        paragraphs.forEachIndexed { i, it ->
            if (peopleToType[it.agent] != "group") {
                if (agent != it.agent) {
                    agent = it.agent
                    side = !side
                }
                calculatedSideMap[i] = side
            }
        }
        val countLeft = calculatedSideMap.count { !it.value }
        val countRight = calculatedSideMap.count { it.value }
        if (countRight * 100 >= (countLeft + countRight) * 85) {
            calculatedSideMap.keys.toList().forEach {
                calculatedSideMap[it] = !calculatedSideMap[it]!!
            }
        }
        calculatedSideMap
    } else null
    val hasAtLeastTwoPeople = people["person"]?.let { it.size > 1 } == true
    if (paragraphs.find { it.time != null } == null) {
        return SemanticLyrics.UnsyncedLyrics(paragraphs.mapIndexed { j, it ->
            val text = it.texts.joinToString("") { i -> i.text }
            val isBg = it.role == "x-bg"
            val isGroup = peopleToType[it.agent] == "group"
            val isOther = peopleToType[it.agent] == "other"
            val isVoice2 = if (pToSide != null) !isGroup && pToSide[j]!! else
                (it.agent != null && (people[peopleToType[it.agent]] ?: throw NullPointerException(
                    "expected to find ${it.agent} (${peopleToType[it.agent]}) in $people"
                )).indexOf(it.agent) % 2 == (if (isOther) 0 else 1))
            val speaker = when {
                isGroup && isBg -> SpeakerEntity.GroupBackground
                isGroup -> SpeakerEntity.Group
                isVoice2 && isBg -> SpeakerEntity.Voice2Background
                isVoice2 -> SpeakerEntity.Voice2
                (hasAtLeastTwoPeople || isOther) && isBg -> SpeakerEntity.Voice1Background
                (hasAtLeastTwoPeople || isOther) -> SpeakerEntity.Voice1
                isBg -> SpeakerEntity.VoiceBackground
                else -> SpeakerEntity.Voice
            }
            Pair(text, speaker)
        })
    }
    return SemanticLyrics.SyncedLyrics(paragraphs.mapIndexed { j, it ->
        val text = StringBuilder()
        val words = mutableListOf<IntRange>()
        for (i in it.texts) {
            val start = text.length
            text.append(i.text)
            words += start..<text.length
        }
        val isLineOrNoneTiming = timing.equals("Line", ignoreCase = true) || timing.equals("None", ignoreCase = true)
        val theWords = if (!isLineOrNoneTiming) it.texts.mapIndexed { i, item -> item to words[i] }
            .filter { item -> item.first.time != null }
            .map { item -> SemanticLyrics.Word(item.first.time!!, item.second, false) }
            .takeIf { item -> item.isNotEmpty() }
            ?.toMutableList() else null
        val isBg = it.role == "x-bg"
        val isGroup = peopleToType[it.agent] == "group"
        val isOther = peopleToType[it.agent] == "other"
        val isVoice2 = if (pToSide != null) !isGroup && pToSide[j]!! else
            (it.agent != null && (people[peopleToType[it.agent]] ?: throw NullPointerException(
                "expected to find ${it.agent} (${peopleToType[it.agent]}) in $people"
            )).indexOf(it.agent) % 2 == (if (isOther) 0 else 1))
        val speaker = when {
            isGroup && isBg -> SpeakerEntity.GroupBackground
            isGroup -> SpeakerEntity.Group
            isVoice2 && isBg -> SpeakerEntity.Voice2Background
            isVoice2 -> SpeakerEntity.Voice2
            hasAtLeastTwoPeople && isBg -> SpeakerEntity.Voice1Background
            hasAtLeastTwoPeople -> SpeakerEntity.Voice1
            isBg -> SpeakerEntity.VoiceBackground
            else -> SpeakerEntity.Voice
        }
        val effectiveTime = it.time ?: paragraphs.getOrNull(j - 1)?.time ?: 0uL..0uL
        val next = paragraphs.getOrNull(j + 1)?.time?.first
        SemanticLyrics.LyricLine(
            text.toString(), effectiveTime.first, effectiveTime.last, theWords == null
                    && next != null && (effectiveTime.last == next || effectiveTime.last == next - 1uL),
            theWords, speaker, it.translated
        )
    }).also { splitBidirectionalWords(it) }
}

fun parseSrt(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    if (!lyricText.startsWith("1\n") && !lyricText.startsWith("1\r")) return null
    val cues = mutableListOf<CuesWithTiming>()
    val parser = SubripParser()
    try {
        parser.parse(
            lyricText.toByteArray(),
            SubtitleParser.OutputOptions.allCues()
        ) { cues.add(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse something which looks like SRT: ${Log.getThrowableString(e)}")
        return null
    }
    var lastTs: ULong? = null
    return SemanticLyrics.SyncedLyrics(cues.map {
        val ts = (it.startTimeUs / 1000).toULong()
        val l = lastTs == ts
        lastTs = ts
        SemanticLyrics.LyricLine(
            it.cues[0].text!!.toString().let { text ->
                if (trimEnabled)
                    text.trim()
                else text
            }, ts, (it.endTimeUs / 1000).toULong(), false, null,
            null, l
        )
    })
}
