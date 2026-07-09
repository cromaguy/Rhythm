package chromahub.rhythm.app.util

import kotlin.math.ceil

object BluetoothLyricsFormatter {
    const val DEFAULT_MAX_CHUNK_CHARS = 30
    const val MIN_MAX_CHUNK_CHARS = 16
    const val MAX_MAX_CHUNK_CHARS = 56

    const val DEFAULT_SCROLL_CHARS_PER_SECOND = 2
    const val MIN_SCROLL_CHARS_PER_SECOND = 0
    const val MAX_SCROLL_CHARS_PER_SECOND = 12

    const val DEFAULT_MIN_CHUNK_HOLD_MS = 900
    const val MIN_MIN_CHUNK_HOLD_MS = 600
    const val MAX_MIN_CHUNK_HOLD_MS = 2000

    private const val DEFAULT_LAST_LINE_MS = 3000L
    private const val HARD_MAX_CHUNKS = 6
    private const val HARD_MAX_CHARS = 96
    private const val MIN_CHUNK_CHARS = 12

    data class Tuning(
        val maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
        val scrollCharsPerSecond: Int = DEFAULT_SCROLL_CHARS_PER_SECOND,
        val minChunkHoldMs: Int = DEFAULT_MIN_CHUNK_HOLD_MS
    ) {
        fun normalized(): Tuning = copy(
            maxChunkChars = coerceMaxChunkChars(maxChunkChars),
            scrollCharsPerSecond = coerceScrollCharsPerSecond(scrollCharsPerSecond),
            minChunkHoldMs = coerceMinChunkHoldMs(minChunkHoldMs)
        )
    }

    data class ResolvedLine(
        val text: String,
        val sourceLineIndex: Int,
        val chunkIndex: Int,
        val chunkCount: Int
    )

    fun coerceMaxChunkChars(value: Int): Int =
        value.coerceIn(MIN_MAX_CHUNK_CHARS, MAX_MAX_CHUNK_CHARS)

    fun coerceScrollCharsPerSecond(value: Int): Int =
        value.coerceIn(MIN_SCROLL_CHARS_PER_SECOND, MAX_SCROLL_CHARS_PER_SECOND)

    fun coerceMinChunkHoldMs(value: Int): Int =
        value.coerceIn(MIN_MIN_CHUNK_HOLD_MS, MAX_MIN_CHUNK_HOLD_MS)

    fun resolveLine(
        positionMs: Long,
        timestamps: LongArray,
        texts: List<String>,
        tuning: Tuning = Tuning()
    ): ResolvedLine? {
        if (timestamps.isEmpty() || texts.isEmpty()) return null
        val position = positionMs.coerceAtLeast(0L)
        val index = findLineIndex(position, timestamps)
        if (index < 0) return null

        val start = timestamps[index]
        val end = timestamps.getOrNull(index + 1) ?: (start + DEFAULT_LAST_LINE_MS)
        val duration = (end - start).coerceAtLeast(1L)
        val chunks = chunksForLine(texts.getOrNull(index).orEmpty(), duration, tuning)
        if (chunks.isEmpty()) return null

        val progress = (position - start).coerceIn(0L, duration - 1)
        val chunkIndex = ((progress * chunks.size) / duration)
            .toInt()
            .coerceIn(0, chunks.lastIndex)
        return ResolvedLine(
            text = chunks[chunkIndex],
            sourceLineIndex = index,
            chunkIndex = chunkIndex,
            chunkCount = chunks.size
        )
    }

    fun chunksForLine(
        text: String,
        durationMs: Long,
        tuning: Tuning = Tuning()
    ): List<String> {
        val cleanText = normalizeText(text)
        if (cleanText.isEmpty()) return emptyList()

        val safeTuning = tuning.normalized()
        val duration = durationMs.coerceAtLeast(1L)
        val readableBudget = readableCharsForDuration(duration, safeTuning)
            .coerceAtMost(HARD_MAX_CHARS)
        if (cleanText.length <= readableBudget) return listOf(cleanText)

        val maxChunksByTime = (duration / safeTuning.minChunkHoldMs)
            .toInt()
            .coerceIn(1, HARD_MAX_CHUNKS)
        if (maxChunksByTime == 1) return listOf(cleanText)

        val neededChunks = ceil(cleanText.length.toDouble() / readableBudget.toDouble())
            .toInt()
            .coerceIn(2, maxChunksByTime)
        val targetChars = maxOf(
            safeTuning.maxChunkChars,
            ceil(cleanText.length.toDouble() / neededChunks.toDouble()).toInt()
        ).coerceIn(MIN_CHUNK_CHARS, HARD_MAX_CHARS)

        var chunks = splitByWords(cleanText, targetChars)
        if (chunks.size > maxChunksByTime) {
            val rebalancedTarget = ceil(cleanText.length.toDouble() / maxChunksByTime.toDouble())
                .toInt()
                .coerceIn(MIN_CHUNK_CHARS, HARD_MAX_CHARS)
            chunks = splitByWords(cleanText, rebalancedTarget)
        }
        return chunks
    }

    private fun findLineIndex(positionMs: Long, timestamps: LongArray): Int {
        var index = -1
        for (i in timestamps.indices) {
            if (timestamps[i] <= positionMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }

    private fun readableCharsForDuration(durationMs: Long, tuning: Tuning): Int {
        val scrollAllowance = (durationMs * tuning.scrollCharsPerSecond / 1000L).toInt()
        return (tuning.maxChunkChars + scrollAllowance).coerceAtLeast(MIN_CHUNK_CHARS)
    }

    private fun normalizeText(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")

    private fun splitByWords(text: String, targetChars: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flushCurrent() {
            val chunk = current.toString().trim()
            if (chunk.isNotEmpty()) chunks.add(chunk)
            current.clear()
        }

        normalizeText(text).split(' ').filter { it.isNotBlank() }.forEach { word ->
            if (word.length > targetChars) {
                flushCurrent()
                word.chunked(targetChars).forEach { chunks.add(it) }
                return@forEach
            }

            val separatorLength = if (current.isEmpty()) 0 else 1
            if (current.length + separatorLength + word.length <= targetChars) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                flushCurrent()
                current.append(word)
            }
        }
        flushCurrent()
        return chunks
    }
}
