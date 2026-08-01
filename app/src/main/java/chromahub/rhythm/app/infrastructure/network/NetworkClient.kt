package chromahub.rhythm.app.network

import android.util.Log
import chromahub.rhythm.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.pow
import chromahub.rhythm.app.network.LRCLibApiService
import chromahub.rhythm.app.network.DeezerApiService
import chromahub.rhythm.app.network.SpotifySearchApiService
import chromahub.rhythm.app.network.YTMusicApiService

object NetworkClient {
    private const val TAG = "NetworkClient"
    
    private const val LRCLIB_BASE_URL = "https://lrclib.net/"
    private const val DEEZER_BASE_URL = "https://api.deezer.com/"
    private const val YTMUSIC_BASE_URL = "https://music.youtube.com/"
    private const val SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1/"
    private const val LYRICALLY_BASE_URL = "https://lyrics.paxsenix.org/"
    private const val ITUNES_BASE_URL = "https://itunes.apple.com/"
    
    // Connection timeouts
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    private const val LYRICS_CALL_TIMEOUT = 8L
    private const val MAX_RETRIES = 3
    
    private val connectionPool by lazy { ConnectionPool(5, 30, TimeUnit.SECONDS) }
    
    // Store reference to AppSettings for dynamic API key
    private var appSettings: chromahub.rhythm.app.shared.data.model.AppSettings? = null
    
    fun initialize(appSettings: chromahub.rhythm.app.shared.data.model.AppSettings) {
        this.appSettings = appSettings
    }
    
    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor { message ->
            try {
                Log.d(TAG, message)
            } catch (e: Exception) {
                Log.w(TAG, "Error logging HTTP message: ${e.message}")
            }
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val retryInterceptor = Interceptor { chain ->
        var currentRetry = 0
        var response: Response? = null
        var lastException: IOException? = null
        
        while (currentRetry < MAX_RETRIES) {
            try {
                Log.d(TAG, "Attempting request (attempt ${currentRetry + 1}/${MAX_RETRIES}): ${chain.request().url}")
                response = chain.proceed(chain.request())
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Request successful: ${chain.request().url}")
                    return@Interceptor response
                } else {
                    val code = response.code
                    Log.w(TAG, "Request failed with code $code: ${chain.request().url}")
                    
                    if (code in 400..499 && code != 408 && code != 429) {
                        Log.d(TAG, "Client error $code, not retrying")
                        return@Interceptor response
                    }
                    
                    if (code == 429) {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: (currentRetry + 1).toLong()
                        val backoffDelay = minOf(retryAfter * 1000, 30000)
                        Log.d(TAG, "Rate limited, retrying after ${backoffDelay}ms")
                        response.close()
                        Thread.sleep(backoffDelay)
                        currentRetry++
                        continue
                    }
                    
                    response.close()
                }
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                lastException = e
                Log.e(TAG, "Request error (attempt ${currentRetry + 1}): ${e.javaClass.simpleName} - ${e.message}")
                
                val shouldRetry = when (e) {
                    is SocketTimeoutException -> true
                    is UnknownHostException -> true
                    is java.net.ConnectException -> true
                    is java.net.SocketException -> true
                    is javax.net.ssl.SSLException -> false
                    is java.io.FileNotFoundException -> false
                    else -> currentRetry < 1
                }
                
                if (!shouldRetry) {
                    Log.d(TAG, "Error type ${e.javaClass.simpleName} is not retryable")
                    throw e
                }
            }
            
            currentRetry++
            if (currentRetry < MAX_RETRIES) {
                val baseDelay = 1000L
                val backoffDelay = minOf(baseDelay * (2.0.pow(currentRetry.toDouble())).toLong(), 10000L)
                Log.d(TAG, "Retrying after ${backoffDelay}ms delay")
                Thread.sleep(backoffDelay)
            }
        }
        
        response?.let { return@Interceptor it }
        throw lastException ?: IOException("Request failed after $MAX_RETRIES retries")
    }
    
    private fun deezerHeadersInterceptor() = Interceptor { chain ->
        try {
            val request = chain.request().newBuilder()
                .header("User-Agent", "RhythmApp/${BuildConfig.VERSION_NAME} (Android)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error in deezer headers interceptor: ${e.message}")
            throw e
        }
    }
    
    private val deezerHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(deezerHeadersInterceptor())
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build()
    }
    
    private val deezerRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DEEZER_BASE_URL)
            .client(deezerHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val lrclibHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(LYRICS_CALL_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build()
    }
    
    private val lrclibRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(lrclibHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val ytmusicHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(deezerHeadersInterceptor())
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build()
    }
    
    private val ytmusicRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(YTMUSIC_BASE_URL)
            .client(ytmusicHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val spotifyHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build()
    }
    
    private val spotifyRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SPOTIFY_API_BASE_URL)
            .client(spotifyHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
        
    private val lyricallyHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(deezerHeadersInterceptor())
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(LYRICS_CALL_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build()
    }
    
    private val lyricallyRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(LYRICALLY_BASE_URL)
            .client(lyricallyHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val itunesHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(LYRICS_CALL_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build()
    }

    private val itunesRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ITUNES_BASE_URL)
            .client(itunesHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    val deezerApiService: DeezerApiService? by lazy {
        if (BuildConfig.ENABLE_DEEZER) deezerRetrofit.create(DeezerApiService::class.java) else null
    }
    
    val lrclibApiService: LRCLibApiService? by lazy {
        if (BuildConfig.ENABLE_LRCLIB) lrclibRetrofit.create(LRCLibApiService::class.java) else null
    }
    
    val ytmusicApiService: YTMusicApiService? by lazy {
        if (BuildConfig.ENABLE_YOUTUBE_MUSIC) ytmusicRetrofit.create(YTMusicApiService::class.java) else null
    }
    
    val spotifySearchApiService: SpotifySearchApiService? by lazy {
        if (BuildConfig.ENABLE_SPOTIFY_SEARCH) spotifyRetrofit.create(SpotifySearchApiService::class.java) else null
    }

    val rhythmLyricsApiService: RhythmLyricsApiService? by lazy {
        if (BuildConfig.ENABLE_LYRICALLY_API) lyricallyRetrofit.create(RhythmLyricsApiService::class.java) else null
    }

    val itunesSearchApiService: ITunesSearchApiService? by lazy {
        if (BuildConfig.ENABLE_LYRICALLY_API) itunesRetrofit.create(ITunesSearchApiService::class.java) else null
    }
    
    val genericHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(deezerHeadersInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }
    
    // Helper methods to check if APIs are enabled (respects both BuildConfig AND runtime settings)
    fun isDeezerApiEnabled(): Boolean = BuildConfig.ENABLE_DEEZER && (appSettings?.deezerApiEnabled?.value ?: false)
    fun isLrcLibApiEnabled(): Boolean = BuildConfig.ENABLE_LRCLIB && (appSettings?.lrclibApiEnabled?.value ?: false)
    fun isYTMusicApiEnabled(): Boolean = BuildConfig.ENABLE_YOUTUBE_MUSIC && (appSettings?.ytMusicApiEnabled?.value ?: false)
    fun isSpotifyApiEnabled(): Boolean = BuildConfig.ENABLE_SPOTIFY_SEARCH && (appSettings?.spotifyApiEnabled?.value ?: false)
    fun isLyricallyApiEnabled(): Boolean = BuildConfig.ENABLE_LYRICALLY_API && (appSettings?.lyricallyApiEnabled?.value ?: false)
    fun isWikipediaApiEnabled(): Boolean = BuildConfig.ENABLE_WIKIPEDIA && (appSettings?.wikipediaApiEnabled?.value ?: false)
    
    // Get Spotify API credentials
    fun getSpotifyClientId(): String = appSettings?.spotifyClientId?.value ?: ""
    fun getSpotifyClientSecret(): String = appSettings?.spotifyClientSecret?.value ?: ""
}
