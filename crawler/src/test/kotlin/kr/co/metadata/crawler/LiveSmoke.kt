// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlinx.coroutines.runBlocking

/** Live smoke check. Prints field availability and provenance, never prints lyric text. */
fun main() = runBlocking {
    val http = PageClient()
    val bugs = BugsMobileProvider(http)
    val melon = MelonProvider(http)

    val bugsArtist = bugs.artist("80049126")
    val bugsAlbum = bugs.album("4154773")
    val bugsTrack = bugs.track("6506727")
    val melonArtist = melon.artist("261143")
    val melonTrack = melon.track("37323516")
    val melonSearch = CatalogSearch(http).search(Provider.MELON, EntityKind.ARTIST, "아이유", limit = 3)
    val genie = GenieProvider(http)
    val genieAlbum = genie.album("81867444")
    val genieArtist = genie.artist("67872918")

    fun <T> show(name: String, value: Evidence<T>?) {
        println("$name=${value?.value ?: "<not found>"} [source=${value?.provider?.label ?: "-"}, confidence=${value?.confidence ?: 0.0}]")
    }
    show("Bugs artist", bugsArtist?.name)
    show("Bugs debut", bugsArtist?.debutDate)
    show("Bugs artist genres", bugsArtist?.genres)
    show("Bugs album", bugsAlbum?.title)
    show("Bugs album release", bugsAlbum?.releaseDate)
    show("Bugs album genres", bugsAlbum?.genres)
    show("Bugs track", bugsTrack?.title)
    show("Bugs track lyrics available", bugsTrack?.lyrics?.let { Evidence(it.value.isNotBlank(), it.provider, it.pageUrl, it.confidence) })
    show("Melon artist", melonArtist?.name)
    show("Melon debut", melonArtist?.debutDate)
    show("Melon track", melonTrack?.title)
    show("Melon release", melonTrack?.releaseDate)
    show("Melon genre", melonTrack?.genres)
    println("Melon search results=${melonSearch.size}; first provider ID=${melonSearch.firstOrNull()?.providerId ?: "<not found>"}")
    show("Genie album", genieAlbum?.title)
    show("Genie album release", genieAlbum?.releaseDate)
    show("Genie album genres", genieAlbum?.genres)
    show("Genie artist", genieArtist?.name)
    for (provider in listOf(Provider.BUGS_MOBILE, Provider.GENIE, Provider.MELON)) {
        val hits = CatalogSearch(http).search(provider, EntityKind.TRACK, "아이유", limit = 3)
        println("${provider.label} track search: ${hits.size} results; first=${hits.firstOrNull()?.title ?: "<not found>"}; artist=${hits.firstOrNull()?.artists ?: "<not found>"}")
    }
}
