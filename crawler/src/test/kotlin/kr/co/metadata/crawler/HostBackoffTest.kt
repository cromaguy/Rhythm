// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.*

class HostBackoffTest {
    @Test fun `respects Retry-After and survives process recreation without blocking other services`() {
        val deadlines = mutableMapOf<String, Long>()
        val persistence = object : CooldownPersistence {
            override fun read(host: String) = deadlines[host] ?: 0L
            override fun write(host: String, until: Long) { deadlines[host] = until }
        }
        val now = 1_700_000_000_000L
        HostBackoff({ now }, persistence).record("bugs.co.kr", 429, "7200")
        val restarted = HostBackoff({ now }, persistence)
        assertEquals(now + 7_200_000L, restarted.until("bugs.co.kr"))
        assertEquals(0L, restarted.until("genie.co.kr"))
    }
    @Test fun `HTTP date retry deadline takes precedence over minimum rate limit pause`() {
        val now = 1_700_000_000_000L
        val deadline = now + 3_600_000
        val header = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(deadline).atZone(ZoneOffset.UTC))
        assertEquals(deadline, HostBackoff({ now }).record("host", 429, header))
    }
    @Test fun `denied responses stop requests for a day and transient errors back off exponentially`() {
        val now = 100L
        val cooldown = HostBackoff({ now })
        assertEquals(now + 86_400_000L, cooldown.record("blocked", 403))
        assertEquals(now + 60_000L, cooldown.record("unstable", 503))
        assertEquals(now + 120_000L, cooldown.record("unstable", 503))
        assertEquals(now + 240_000L, cooldown.record("unstable", 0))
    }
    @Test fun `malformed and overflowing Retry-After cannot crash the scheduler`() {
        assertEquals(900_000L, HostBackoff({ 0L }).record("host", 429, "99999999999999999999999999"))
        assertEquals(900_000L, HostBackoff({ 0L }).record("host", 429, "nonsense"))
    }
}
