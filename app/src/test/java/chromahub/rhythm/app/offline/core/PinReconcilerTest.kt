// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline cache, keep-downloaded sync: the diff between the server and the ledger's pin reasons. */
class PinReconcilerTest {

    private val fav = PinSource.FAVORITES
    private val pl1 = PinSource.playlist("Road trip")
    private val pl2 = PinSource.playlist("Daily mix")
    private val manual = PinSource.MANUAL

    private fun ledger(vararg entries: Pair<String, Set<String>>) = mapOf(*entries)
    private fun complete(vararg ids: String) = SourceState.Complete(ids.toSet())

    @Test
    fun aShuffledPlaylistIsNoChangeAtAll() {
        val current = ledger("a" to setOf(pl1), "b" to setOf(pl1), "c" to setOf(pl1), "d" to setOf(pl1))
        // The server returns the same songs in another order (a list, as the API gives it; not a set).
        val shuffled = listOf("c", "a", "d", "b")
        val plan = PinReconciler.plan(current, mapOf(pl1 to SourceState.of(Result.success(shuffled), declaredCount = 4)))
        assertTrue("a reorder must cause zero downloads and zero evictions", plan.isEmpty)
    }

    @Test
    fun aRotatedPlaylistQueuesTheNewSongsAndReleasesTheOnesThatLeft() {
        val current = ledger("a" to setOf(pl1), "b" to setOf(pl1), "c" to setOf(pl1))
        val plan = PinReconciler.plan(current, mapOf(pl1 to complete("c", "d", "a", "e")))
        assertEquals(mapOf("d" to setOf(pl1), "e" to setOf(pl1)), plan.adds)
        assertEquals(mapOf("b" to setOf(pl1)), plan.drops)
        assertEquals(setOf("b"), plan.release)
    }

    @Test
    fun aSongAnotherSourceStillPinsIsNotReleased() {
        val current = ledger(
            "a" to setOf(pl1, fav), // starred as well
            "b" to setOf(pl1, manual), // downloaded by hand as well
            "c" to setOf(pl1, pl2), // in another playlist too
            "d" to setOf(pl1),
        )
        val plan = PinReconciler.plan(
            current,
            mapOf(pl1 to complete("x"), fav to complete("a"), pl2 to complete("c")),
        )
        assertEquals(setOf("d"), plan.release)
        assertEquals(setOf(pl1), plan.drops["a"])
        assertEquals(setOf(pl1), plan.drops["b"])
        assertEquals(setOf(pl1), plan.drops["c"])
    }

    @Test
    fun aSongNotYetInTheLedgerIsAddedWithEveryReasonThatWantsIt() {
        val plan = PinReconciler.plan(emptyMap(), mapOf(fav to complete("a", "b"), pl1 to complete("b", "c")))
        assertEquals(setOf(fav), plan.adds["a"])
        assertEquals(setOf(fav, pl1), plan.adds["b"])
        assertEquals(setOf(pl1), plan.adds["c"])
        assertTrue(plan.drops.isEmpty() && plan.release.isEmpty())
    }

    @Test
    fun aFavoriteThatIsAlsoInAPlaylistGainsTheSecondReasonWithoutBeingQueuedTwice() {
        val current = ledger("a" to setOf(fav))
        val plan = PinReconciler.plan(current, mapOf(fav to complete("a"), pl1 to complete("a")))
        assertEquals(mapOf("a" to setOf(pl1)), plan.adds)
        assertTrue(plan.release.isEmpty())
    }

    @Test
    fun unstarringReleasesTheSongOnlyIfNothingElseNeedsIt() {
        val current = ledger("a" to setOf(fav), "b" to setOf(fav, pl1), "c" to setOf(fav, manual))
        val plan = PinReconciler.plan(current, mapOf(fav to complete("zzz"), pl1 to complete("b")))
        assertEquals(setOf("a"), plan.release)
        assertEquals(setOf(fav), plan.drops["b"])
        assertEquals(setOf(fav), plan.drops["c"])
    }

    @Test
    fun aFailedFetchEvictsNothing() {
        val current = ledger("a" to setOf(fav), "b" to setOf(pl1))
        val plan = PinReconciler.plan(
            current,
            mapOf(
                fav to SourceState.of(Result.failure(java.io.IOException("timeout"))),
                pl1 to SourceState.of(Result.failure(IllegalStateException("not connected")), declaredCount = 2),
            ),
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun anEmptyAnswerForASourceThatHoldsSongsEvictsNothing() {
        val current = ledger("a" to setOf(fav), "b" to setOf(pl1), "c" to setOf(pl1))
        val plan = PinReconciler.plan(current, mapOf(fav to complete(), pl1 to SourceState.of(Result.success(emptyList()))))
        assertTrue("an empty list is not believed", plan.isEmpty)
    }

    @Test
    fun anEmptySourceThatHoldsNothingStaysQuiet() {
        val plan = PinReconciler.plan(emptyMap(), mapOf(fav to complete(), pl1 to complete()))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun aListShorterThanTheServerSaidAddsButNeverRemoves() {
        val current = ledger("a" to setOf(pl1), "b" to setOf(pl1))
        val state = SourceState.of(Result.success(listOf("a", "n")), declaredCount = 600)
        assertTrue(state is SourceState.Partial)
        val plan = PinReconciler.plan(current, mapOf(pl1 to state))
        assertEquals(mapOf("n" to setOf(pl1)), plan.adds)
        assertTrue(plan.drops.isEmpty() && plan.release.isEmpty())
    }

    @Test
    fun aListOfTheAnnouncedLengthIsComplete() {
        assertTrue(SourceState.of(Result.success(listOf("a", "b")), declaredCount = 2) is SourceState.Complete)
        // A server that reports fewer than it lists (dedup, a count that lags) is not suspicious.
        assertTrue(SourceState.of(Result.success(listOf("a", "b", "c")), declaredCount = 2) is SourceState.Complete)
    }

    @Test
    fun switchingASourceOffDropsItsPinsAndKeepsTheOtherReasons() {
        val current = ledger("a" to setOf(fav), "b" to setOf(fav, pl1), "c" to setOf(manual, fav), "d" to setOf(manual))
        // Favorites is off: it is not among the sources at all.
        val plan = PinReconciler.plan(current, mapOf(pl1 to complete("b")))
        assertEquals(setOf("a"), plan.release)
        assertEquals(setOf(fav), plan.drops["b"])
        assertEquals(setOf(fav), plan.drops["c"])
        assertFalse("d" in plan.drops || "d" in plan.release)
    }

    @Test
    fun aPlaylistSwitchedOffDropsEvenWhenItWasTheOnlyOneHolding() {
        val current = ledger("a" to setOf(pl1), "b" to setOf(pl2))
        val plan = PinReconciler.plan(current, mapOf(pl2 to complete("b")))
        assertEquals(setOf("a"), plan.release)
    }

    @Test
    fun manualDownloadsAreNeverTouched() {
        val current = ledger("a" to setOf(manual), "b" to setOf(manual))
        assertTrue(PinReconciler.plan(current, emptyMap()).isEmpty)
        assertTrue(PinReconciler.plan(current, mapOf(fav to complete("c"))).drops.isEmpty())
    }

    @Test
    fun anEntryAlreadyWithoutAReasonIsReleasedWhateverTheSources() {
        // A crash after the last reason was dropped and before the file was deleted.
        val current = ledger("a" to emptySet(), "b" to setOf(fav))
        val plan = PinReconciler.plan(current, mapOf(fav to SourceState.Unavailable))
        assertEquals(setOf("a"), plan.release)
    }

    // 2026-10-27 "streaming downloads are failing sometimes": a keep-downloaded sync releases every song left with no
    // pin reason, and the repository's removeDownload() cancels and deletes the transfer on the wire. A download the
    // user started (or one the sync queued itself) is not something a pin refresh may cancel.
    @Test
    fun aDownloadTheUserStartedIsNotReleasedByAPinSyncEvenBeforeAnyPinIsWritten() {
        val current = ledger("a" to setOf(manual), "b" to setOf(manual) + fav)
        val plan = PinReconciler.plan(current, mapOf(fav to complete("x")))
        assertEquals(mapOf("b" to setOf(fav)), plan.drops)
        assertTrue("the user's own downloads are never released by a sync", plan.release.isEmpty())
    }

    @Test
    fun anEntryAlreadyWithoutAReasonIsReleasedOnlyWhenNothingElseHoldsIt() {
        // A crash after the last reason was dropped and before the file was deleted.
        val current = ledger("a" to emptySet(), "b" to setOf(fav))
        assertEquals(setOf("a"), PinReconciler.plan(current, mapOf(fav to SourceState.Unavailable)).release)
        // ...but an entry a sync is writing its first reason into (pins still empty, the file in flight) is not.
        val fresh = ledger("a" to emptySet())
        assertTrue(PinReconciler.plan(fresh, mapOf(fav to complete("a"))).release.isEmpty())
    }

    @Test
    fun aSongThatLeavesOnePlaylistAndEntersAnotherIsKept() {
        val current = ledger("a" to setOf(pl1))
        val plan = PinReconciler.plan(current, mapOf(pl1 to complete("x"), pl2 to complete("a")))
        assertEquals(mapOf("a" to setOf(pl2), "x" to setOf(pl1)), plan.adds)
        assertEquals(mapOf("a" to setOf(pl1)), plan.drops)
        assertTrue("moved, not removed", plan.release.isEmpty())
    }

    @Test
    fun aPlaylistReCreatedUnderANewIdMatchesByNameAndCarriesOver() {
        // Kept as "Road trip" when its id was 1; the server re-created it as id 7 with the same songs plus one.
        val held = setOf(PinSource.playlist("Road trip"))
        val recreated = PinPlaylist("SUBSONIC::playlist::7", "Road trip", 3)
        val selection = PlaylistSelector.select(Result.success(listOf(recreated)), setOf("Road trip"), held)
        assertEquals(mapOf("Road trip" to listOf(recreated)), selection.fetch)
        assertTrue(selection.absent.isEmpty() && selection.frozen.isEmpty())

        val current = ledger("a" to held, "b" to held)
        val plan = PinReconciler.plan(current, mapOf(PinSource.playlist("Road trip") to complete("a", "b", "c")))
        assertEquals(mapOf("c" to held), plan.adds)
        assertTrue("same name, nothing re-downloaded or released", plan.drops.isEmpty() && plan.release.isEmpty())
    }

    @Test
    fun aPlaylistDeletedOnTheServerKeepsItsPinsAndFilesUntilTheUserSwitchesItOff() {
        val held = setOf(PinSource.playlist("Gone"))
        val selection = PlaylistSelector.select(Result.success(listOf(road)), setOf("Gone", "Road trip"), held)
        assertEquals(setOf(PinSource.playlist("Gone")), selection.absent)
        assertEquals(setOf("Road trip"), selection.fetch.keys)
        assertTrue("the listing was fine: no retry needed", selection.frozen.isEmpty())

        val current = ledger("a" to held, "b" to held, "r" to setOf(PinSource.playlist("Road trip")))
        val sources = LinkedHashMap<String, SourceState>()
        selection.absent.forEach { sources[it] = SourceState.Unavailable }
        sources[PinSource.playlist("Road trip")] = complete("r")
        val plan = PinReconciler.plan(current, sources)
        assertTrue("nothing released, nothing dropped", plan.isEmpty)

        // The user switches "Gone" off: it is no longer a kept name, so it is not a source and the plan releases it.
        val off = PlaylistSelector.select(Result.success(listOf(road)), setOf("Road trip"), held)
        assertTrue(off.absent.isEmpty())
        val offPlan = PinReconciler.plan(current, mapOf(PinSource.playlist("Road trip") to complete("r")))
        assertEquals(setOf("a", "b"), offPlan.release)
    }

    @Test
    fun aDeletedPlaylistsSongThatAnotherSourceHoldsSurvivesSwitchingItOff() {
        val gone = PinSource.playlist("Gone")
        val current = ledger("a" to setOf(gone, fav), "b" to setOf(gone))
        val plan = PinReconciler.plan(current, mapOf(fav to complete("a")))
        assertEquals(setOf("b"), plan.release)
        assertEquals(setOf(gone), plan.drops["a"])
    }

    @Test
    fun playlistsThatShareANameAreOneSourceMadeOfTheUnionOfTheirSongs() {
        val one = PinPlaylist("SUBSONIC::playlist::1", "Road trip", 2)
        val two = PinPlaylist("SUBSONIC::playlist::2", "Road trip", 2)
        val selection = PlaylistSelector.select(Result.success(listOf(one, road2(), two)), setOf("Road trip"), emptySet())
        assertEquals(listOf(one, two), selection.fetch["Road trip"])

        val union = SourceState.union(listOf(SourceState.of(Result.success(listOf("a", "b")), 2), SourceState.of(Result.success(listOf("b", "c")), 2)))
        assertEquals(SourceState.Complete(setOf("a", "b", "c")), union)

        // A song in either playlist is wanted; one in neither is released; a song in only one of them stays.
        val reason = PinSource.playlist("Road trip")
        val plan = PinReconciler.plan(ledger("a" to setOf(reason), "b" to setOf(reason), "x" to setOf(reason)), mapOf(reason to union))
        assertEquals(mapOf("c" to setOf(reason)), plan.adds)
        assertEquals(setOf("x"), plan.release)
    }

    private fun road2() = PinPlaylist("SUBSONIC::playlist::3", "Other", 1)

    @Test
    fun aDuplicateNameIsOnlyAsSureAsItsWeakestPart() {
        val ok = SourceState.Complete(setOf("a"))
        assertEquals(SourceState.Unavailable, SourceState.union(listOf(ok, SourceState.Unavailable)))
        assertEquals(SourceState.Unavailable, SourceState.union(emptyList()))
        assertEquals(SourceState.Partial(setOf("a", "b")), SourceState.union(listOf(ok, SourceState.Partial(setOf("b")))))

        // A failed twin makes the whole name frozen: nothing of it is dropped, even songs neither twin lists.
        val reason = PinSource.playlist("Road trip")
        val plan = PinReconciler.plan(ledger("a" to setOf(reason), "z" to setOf(reason)), mapOf(reason to SourceState.union(listOf(ok, SourceState.Unavailable))))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun aFailedOrEmptyListingFreezesEveryPlaylistSourceInsteadOfDeletingThem() {
        val held = setOf(PinSource.playlist("Daily mix"))
        val failed = PlaylistSelector.select(Result.failure(java.io.IOException("x")), setOf("Road trip"), held)
        assertTrue(failed.fetch.isEmpty() && failed.absent.isEmpty())
        assertEquals(held + PinSource.playlist("Road trip"), failed.frozen)
        val empty = PlaylistSelector.select(Result.success(emptyList()), emptySet(), held)
        assertEquals(held, empty.frozen)
        val emptyWithKept = PlaylistSelector.select(Result.success(emptyList()), setOf("Road trip"), emptySet())
        assertEquals(setOf(PinSource.playlist("Road trip")), emptyWithKept.frozen)

        // And the plan built from it changes nothing, whatever the ledger holds.
        val sources = failed.frozen.associateWith<String, SourceState> { SourceState.Unavailable }
        val plan = PinReconciler.plan(mapOf("a" to held, "b" to setOf(PinSource.playlist("Road trip"))), sources)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun aPlaylistNobodyKeptIsNotAnySourceSoItsPinsAreReleased() {
        val held = setOf(PinSource.playlist("Old"))
        val selection = PlaylistSelector.select(Result.success(listOf(road)), emptySet(), held)
        assertTrue(selection.fetch.isEmpty() && selection.absent.isEmpty() && selection.frozen.isEmpty())
        assertEquals(setOf("a"), PinReconciler.plan(mapOf("a" to held), emptyMap()).release)
    }

    @Test
    fun anEmptyListingWithNothingKeptOrHeldIsJustAnEmptyListing() {
        val s = PlaylistSelector.select(Result.success(emptyList()), emptySet(), emptySet())
        assertTrue(s.fetch.isEmpty() && s.absent.isEmpty() && s.frozen.isEmpty())
    }

    @Test
    fun aLegacyIdReasonIsRecognisedAndMovesToTheNameReasonWithoutReleasingTheFile() {
        val legacy = "playlist:SUBSONIC::playlist::42"
        assertTrue(PinSource.isLegacyPlaylistId(legacy))
        assertFalse(PinSource.isLegacyPlaylistId(PinSource.playlist("Road trip")))
        assertEquals("Road trip", PinSource.playlistName(PinSource.playlist("Road trip")))

        // The song is still in the playlist, now kept by name: the legacy reason drops, the name reason is added, the file stays.
        val name = PinSource.playlist("Road trip")
        val plan = PinReconciler.plan(ledger("a" to setOf(legacy)), mapOf(name to complete("a")))
        assertEquals(mapOf("a" to setOf(name)), plan.adds)
        assertEquals(mapOf("a" to setOf(legacy)), plan.drops)
        assertTrue(plan.release.isEmpty())
    }

    // ---- which playlists a sync reads ----

    private val road = PinPlaylist("SUBSONIC::playlist::2", "Road trip", 12)
}
