package chromahub.rhythm.app.network

import com.google.gson.JsonElement
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
        @Query("id") id: String,
        @Query("v") version: Int = 2
    ): JsonElement

    @GET("apple-music/lyrics")
    suspend fun getAppleMusicLyrics(
        @Query("id") id: String,
        @Query("v") version: Int = 2
    ): JsonElement

    // Spotify
    @GET("spotify/search")
    suspend fun searchSpotify(
        @Query("q") query: String
    ): JsonElement

    @GET("spotify/lyrics")
    suspend fun getSpotifyLyrics(
        @Query("id") id: String,
        @Query("v") version: Int = 2
    ): JsonElement

    // NetEase
    @GET("netease/search")
    suspend fun searchNetease(
        @Query("q") query: String
    ): JsonElement

    @GET("netease/lyrics")
    suspend fun getNeteaseLyrics(
        @Query("id") id: String,
        @Query("word") word: Boolean,
        @Query("v") version: Int = 2
    ): JsonElement

    // QQ Music
    @GET("qq/search")
    suspend fun searchQQ(
        @Query("q") query: String
    ): JsonElement

    @GET("qq/lyrics")
    suspend fun getQQLyrics(
        @Query("id") id: String,
        @Query("v") version: Int = 2
    ): JsonElement

    // YouTube
    @GET("youtube/search")
    suspend fun searchYouTube(
        @Query("q") query: String
    ): JsonElement

    @GET("youtube/lyrics")
    suspend fun getYouTubeLyrics(
        @Query("id") id: String,
        @Query("v") version: Int = 2
    ): JsonElement

    // Kugou
    @GET("kugou/search")
    suspend fun searchKugou(
        @Query("q") query: String
    ): JsonElement

    @GET("kugou/lyrics")
    suspend fun getKugouLyrics(
        @Query("id") id: String,
        @Query("word") word: Boolean,
        @Query("v") version: Int = 2
    ): JsonElement

    // Deezer
    @GET("deezer/lyrics")
    suspend fun getDeezerLyrics(
        @Query("id") id: String,
        @Query("v") version: Int = 2
    ): JsonElement

    // Musixmatch
    @GET("musixmatch/lyrics")
    suspend fun getMusixmatchLyrics(
        @Query("q") query: String,
        @Query("type") type: String = "default",
        @Query("l") language: String? = null,
        @Query("v") version: Int = 2
    ): JsonElement

    // Genius
    @GET("genius/lyrics")
    suspend fun getGeniusLyrics(
        @Query("url") url: String,
        @Query("v") version: Int = 2
    ): JsonElement
}
