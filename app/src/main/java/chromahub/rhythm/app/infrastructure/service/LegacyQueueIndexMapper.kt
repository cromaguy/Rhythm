package chromahub.rhythm.app.infrastructure.service

/** Maps an insertion from the one-item legacy view back into the preserved real queue. */
internal fun resolveLegacyQueueInsertionIndex(
    requestedIndex: Int,
    legacyQueueCollapsed: Boolean,
    currentOriginalIndex: Int,
    originalQueueSize: Int
): Int {
    if (
        !legacyQueueCollapsed ||
        requestedIndex !in 0..1 ||
        currentOriginalIndex !in 0 until originalQueueSize
    ) {
        return requestedIndex
    }

    return (currentOriginalIndex + requestedIndex).coerceIn(0, originalQueueSize)
}
