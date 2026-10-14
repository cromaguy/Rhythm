// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

/** Coverage counts actual played audio, never a seek jump or the same interval twice. */
class PlayCoverage {
    private val spans = mutableListOf<Pair<Long, Long>>()
    fun record(start: Long, end: Long, playedAudioMs: Long, duration: Long) {
        if (duration <= 0 || playedAudioMs <= 0) return
        val a = start.coerceIn(0, duration)
        val b = minOf(end, a + playedAudioMs + 500).coerceIn(a, duration)
        if (b <= a) return
        var left = a; var right = b; var inserted = false
        val merged = mutableListOf<Pair<Long, Long>>()
        for ((oldStart, oldEnd) in spans) {
            when {
                oldEnd < left -> merged += oldStart to oldEnd
                oldStart > right -> { if (!inserted) { merged += left to right; inserted = true }; merged += oldStart to oldEnd }
                else -> { left = minOf(left, oldStart); right = maxOf(right, oldEnd) }
            }
        }
        if (!inserted) merged += left to right
        spans.clear(); spans.addAll(merged)
    }
    fun coveredMs(): Long = spans.sumOf { it.second - it.first }
    fun ratio(duration: Long): Double? = duration.takeIf { it > 0 }?.let { (coveredMs().toDouble() / it).coerceIn(0.0, 1.0) }
    companion object {
        fun ratio(records: List<ListeningRecord>): Double? {
            val duration = records.map { it.audioDurationMs }.filter { it > 0 }.maxOrNull() ?: return null
            val coverage = PlayCoverage()
            records.filter { it.kind == "segment" }.distinctBy { it.id }.forEach {
                coverage.record(it.startPositionMs, it.endPositionMs, it.playedAudioMs ?: (it.listenedMs * it.speed).toLong(), duration)
            }
            return coverage.ratio(duration)
        }
    }
}
