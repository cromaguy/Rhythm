// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

interface CooldownPersistence {
    fun read(host: String): Long
    fun write(host: String, until: Long)
}

/** Respect Retry-After, stop blocked hosts, and never retry a challenge within the request. */
class HostBackoff(private val now: () -> Long = System::currentTimeMillis,
                  private val persistence: CooldownPersistence? = null) {
    private val deadlines = mutableMapOf<String, Long>()
    private val failures = mutableMapOf<String, Int>()
    @Synchronized fun until(host: String): Long = maxOf(deadlines[host] ?: 0, persistence?.read(host) ?: 0)
    @Synchronized fun record(host: String, status: Int, retryAfter: String? = null): Long {
        if (status in 200..299) { failures.remove(host); return until(host) }
        val delay = when (status) {
            401, 403 -> 24 * 60 * 60_000L
            429 -> 15 * 60_000L
            in 500..599, 0 -> (60_000L shl ((failures[host] ?: 0).coerceAtMost(5)))
            else -> return until(host)
        }
        failures[host] = (failures[host] ?: 0) + 1
        val retryAt = retryAfter?.toLongOrNull()?.coerceAtLeast(0)?.let {
            now() + it.coerceAtMost(7 * 24 * 3600) * 1000
        } ?: retryAfter?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        } ?: 0L
        val deadline = maxOf(until(host), now() + delay, retryAt)
        deadlines[host] = deadline
        persistence?.write(host, deadline)
        return deadline
    }
}
