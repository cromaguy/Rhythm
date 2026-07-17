package chromahub.rhythm.app.network

import com.google.gson.annotations.SerializedName

/**
 * Rhythm lyrics song search result
 */
data class RhythmLyricsSearchResult(
    @SerializedName("id") val id: String,
    @SerializedName("songName") val songName: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("albumName") val albumName: String?,
    @SerializedName("artwork") val artwork: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("isrc") val isrc: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("contentRating") val contentRating: String?,
    @SerializedName("albumId") val albumId: String?
)

/**
 * Rhythm lyrics response containing word-by-word synchronized lyrics
 */
data class RhythmLyricsResponse(
    @SerializedName("info") val info: String?,
    @SerializedName("type") val type: String?, // "Syllable" for word-by-word
    @SerializedName("content") val content: List<RhythmLyricsLine>?,
    @SerializedName("ttml_content") val ttmlContent: String?,
    @SerializedName("source") val source: String?,
    @SerializedName("track") val track: RhythmLyricsTrackInfo?
)

/**
 * Represents a line of lyrics with word-level synchronization
 */
data class RhythmLyricsLine(
    @SerializedName("text") val text: List<RhythmLyricsWord>?,
    @SerializedName("background") val background: Boolean?,
    @SerializedName("backgroundText") val backgroundText: List<String>?,
    @SerializedName("oppositeTurn") val oppositeTurn: Boolean?,
    @SerializedName("timestamp") val timestamp: Long?, // Line start timestamp in milliseconds
    @SerializedName("endtime") val endtime: Long?, // Line end timestamp in milliseconds
    @SerializedName("endIsImplicit") val endIsImplicit: Boolean? = null
)

/**
 * Represents a single word or syllable with precise timing
 */
data class RhythmLyricsWord(
    @SerializedName("text") val text: String,
    @SerializedName("part") val part: Boolean?, // true if this is part of a split word (syllable)
    @SerializedName("timestamp") val timestamp: Long, // Word start timestamp in milliseconds
    @SerializedName("endtime") val endtime: Long // Word end timestamp in milliseconds
)

/**
 * Track information from Rhythm lyrics source
 */
data class RhythmLyricsTrackInfo(
    @SerializedName("albumName") val albumName: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("hasLyrics") val hasLyrics: Boolean?,
    @SerializedName("hasTimeSyncedLyrics") val hasTimeSyncedLyrics: Boolean?
)

/**
 * Represents a generic track search result from various Lyrically API search endpoints (Spotify, NetEase, QQ, Kugou, YouTube).
 */
data class RhythmLyricsGenericSearchResult(
    @SerializedName("trackId") val trackId: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("songmid") val songmid: String?,
    @SerializedName("hash") val hash: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("artist") val artist: String?
) {
    fun getCanonicalId(): String? {
        return trackId ?: id ?: videoId ?: songmid ?: hash
    }
    
    fun getCanonicalName(): String? {
        return name ?: title
    }
    
    fun getCanonicalArtist(): String? {
        return artistName ?: author ?: artist
    }
}

/**
 * NetEase keeps its search result and timed lyric tracks in provider-native
 * envelopes. In particular, `romalrc` is a separately timestamped romaji
 * track, rather than a field on the normal Lyrically response.
 */
data class NeteaseSearchResponse(
    @SerializedName("result") val result: NeteaseSearchResult? = null
)

data class NeteaseSearchResult(
    @SerializedName("songs") val songs: List<NeteaseSearchSong>? = null
)

data class NeteaseSearchSong(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String? = null,
    @SerializedName("artists") val artists: List<NeteaseArtist>? = null
)

data class NeteaseArtist(
    @SerializedName("name") val name: String? = null
)

data class NeteaseLyricsResponse(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("lrc") val lyrics: NeteaseTimedLyrics? = null,
    @SerializedName("tlyric") val translation: NeteaseTimedLyrics? = null,
    @SerializedName("romalrc") val romanization: NeteaseTimedLyrics? = null
)

data class NeteaseTimedLyrics(
    @SerializedName("lyric") val lyric: String? = null
)
