package chromahub.rhythm.app.infrastructure.service.player.autoloop

/** The first pass includes the intro; the native source clock stays in full-track coordinates. */
data class AutoLoopWindow(
    val mediaId: String,
    val startMs: Long,
    val endMs: Long,
    val trackDurationMs: Long
) {
    init { require(startMs >= 0 && endMs > startMs && trackDurationMs >= endMs) }
    fun clipPosition(trackPositionMs: Long) =
        trackPositionMs.coerceIn(0L, endMs - 1L)
}
