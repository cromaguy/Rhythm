package chromahub.rhythm.app.features.streaming.infrastructure.notification

/**
 * Rate-limits sync progress notification updates.
 *
 * The library sync reports progress once per album, which can be many times per second.
 * Android drops notification updates above ~5/s per app ("Shedding notify (update) ... rate
 * limit exceeded"), so posting every tick only wastes work and log spam.
 */
class SyncProgressThrottle(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastEmitAtMs: Long? = null

    /** Returns true if an update should be posted now. [force] always emits (e.g. final state). */
    @Synchronized
    fun tryAcquire(force: Boolean = false): Boolean {
        val now = clock()
        val last = lastEmitAtMs
        if (force || last == null || now - last >= minIntervalMs) {
            lastEmitAtMs = now
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 500L
    }
}
