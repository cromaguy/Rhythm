package chromahub.rhythm.app.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Service interface for Rhythm lyrics API
 * API Documentation: https://lyrics.paxsenix.org/docs
 */
interface RhythmLyricsApiService {
    /**
     * Get word-by-word synchronized lyrics for a specific song (Apple Music)
     * @param id Lyrics source song ID
     * @return Lyrics response with word-level timing
     */
    @GET("apple-music/lyrics")
    suspend fun getLyrics(
        @Query("id") id: String
    ): RhythmLyricsResponse

    @GET("apple-music/lyrics")
    suspend fun getAppleMusicLyrics(
        @Query("id") id: String
    ): RhythmLyricsResponse

    // Spotify
    @GET("spotify/search")
    suspend fun searchSpotify(
        @Query("q") query: String
    ): List<RhythmLyricsGenericSearchResult>

    @GET("spotify/lyrics")
    suspend fun getSpotifyLyrics(
        @Query("id") id: String
    ): RhythmLyricsResponse

    // NetEase
    @GET("netease/search")
    suspend fun searchNetease(
        @Query("q") query: String
    ): NeteaseSearchResponse

    @GET("netease/lyrics")
    suspend fun getNeteaseLyrics(
        @Query("id") id: String,
        @Query("word") word: Boolean
    ): NeteaseLyricsResponse

    // QQ Music
    @GET("qq/search")
    suspend fun searchQQ(
        @Query("q") query: String
    ): List<RhythmLyricsGenericSearchResult>

    @GET("qq/lyrics")
    suspend fun getQQLyrics(
        @Query("id") id: String
    ): RhythmLyricsResponse

    // YouTube
    @GET("youtube/search")
    suspend fun searchYouTube(
        @Query("q") query: String
    ): List<RhythmLyricsGenericSearchResult>

    @GET("youtube/lyrics")
    suspend fun getYouTubeLyrics(
        @Query("id") id: String
    ): RhythmLyricsResponse

    // Kugou
    @GET("kugou/search")
    suspend fun searchKugou(
        @Query("q") query: String
    ): List<RhythmLyricsGenericSearchResult>

    @GET("kugou/lyrics")
    suspend fun getKugouLyrics(
        @Query("id") id: String,
        @Query("word") word: Boolean
    ): RhythmLyricsResponse

    // Deezer
    @GET("deezer/lyrics")
    suspend fun getDeezerLyrics(
        @Query("id") id: String
    ): RhythmLyricsResponse

    // Musixmatch
    @GET("musixmatch/lyrics")
    suspend fun getMusixmatchLyrics(
        @Query("t") title: String,
        @Query("a") artist: String
    ): RhythmLyricsResponse

    // Genius
    @GET("genius/lyrics")
    suspend fun getGeniusLyrics(
        @Query("url") url: String
    ): RhythmLyricsResponse
}
