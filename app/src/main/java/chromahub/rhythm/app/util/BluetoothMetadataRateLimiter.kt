package chromahub.rhythm.app.util

/**
 * Coalesces rapidly changing lyric text before it is exposed as MediaSession metadata.
 *
 * A real MediaItem replacement is expensive for Android 16's MediaSession/AVRCP bridge. The
 * most recent line remains pending while the interval is active, so a short lyric line never
 * creates a backlog or gets lost.
 */
internal class BluetoothMetadataRateLimiter(
    private val minimumIntervalMs: Long
) {
    private var lastPublishedSongId: String? = null
    private var lastPublishedLine: String? = null
    private var lastPublishedAtMs = 0L

    init {
        require(minimumIntervalMs >= 0L)
    }

    fun shouldPublish(songId: String, line: String?, nowMs: Long): Boolean {
        if (songId == lastPublishedSongId && line == lastPublishedLine) return false
        if (
            songId == lastPublishedSongId &&
                nowMs - lastPublishedAtMs < minimumIntervalMs
        ) {
            return false
        }

        lastPublishedSongId = songId
        lastPublishedLine = line
        lastPublishedAtMs = nowMs
        return true
    }

    fun reset() {
        lastPublishedSongId = null
        lastPublishedLine = null
        lastPublishedAtMs = 0L
    }
}
