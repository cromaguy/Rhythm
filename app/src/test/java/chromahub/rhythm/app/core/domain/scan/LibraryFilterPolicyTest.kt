// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.core.domain.scan

import chromahub.rhythm.app.shared.data.model.MediaScanMode
import org.junit.Test
import org.junit.Assert.*

class LibraryFilterPolicyTest {
    @Test fun hybridAllowsOnlyWhitelistedScopeAndBlacklistWins() {
        val policy = LibraryFilterPolicy(MediaScanMode.HYBRID, listOf("blocked"), listOf("/Music/private"),
            listOf("explicit", "blocked"), listOf("/Music"))
        assertTrue(policy.includes("ok", "/Music/Album/track.mp3"))
        assertFalse(policy.includes("blocked", "/Music/Album/track.mp3"))
        assertFalse(policy.includes("ok", "/Music/private/track.mp3"))
        assertFalse(policy.includes("other", "/Music2/track.mp3"))
        assertTrue(policy.includes("explicit", null))
        assertFalse(policy.includes("other", null))
    }
    @Test fun anEmptyHybridWhitelistDoesNotExposeTheEntireLibrary() {
        val policy = LibraryFilterPolicy(MediaScanMode.HYBRID, emptyList(), emptyList(), emptyList(), emptyList())
        assertFalse(policy.includes("1", "/Music/track.mp3"))
    }
    @Test fun aDisabledListDoesNotAffectTheOtherMode() {
        assertTrue(LibraryFilterPolicy(MediaScanMode.WHITELIST, listOf("1"), emptyList(), listOf("1"), emptyList()).includes("1", null))
        assertTrue(LibraryFilterPolicy(MediaScanMode.BLACKLIST, emptyList(), emptyList(), listOf("2"), emptyList()).includes("1", null))
    }
}
