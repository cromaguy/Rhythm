/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network.scrobble

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.PendingScrobbleEntity
import chromahub.rhythm.app.features.streaming.data.provider.UserTrustManager
import chromahub.rhythm.app.infrastructure.worker.ScrobbleFlushWorker
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Multi-service privacy-first scrobbler supporting ListenBrainz, Libre.fm (GNU FM API),
 * and self-hosted Maloja instances, backed by Room v10 [PendingScrobbleDao] and WorkManager retries.
 */
class MultiScrobblerManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MultiScrobbler"
        private const val PROVIDER_LISTENBRAINZ = "listenbrainz"
        private const val PROVIDER_LIBREFM = "librefm"
        private const val PROVIDER_MALOJA = "maloja"

        private const val LISTENBRAINZ_BASE = "https://api.listenbrainz.org"
        private const val LIBREFM_BASE = "https://libre.fm"
        private const val LIBREFM_KEY = "00000000000000000000000000000000"
        private const val LIBREFM_SECRET = "00000000000000000000000000000000"

        @Volatile
        private var instance: MultiScrobblerManager? = null

        fun getInstance(context: Context): MultiScrobblerManager =
            instance ?: synchronized(this) {
                instance ?: MultiScrobblerManager(context.applicationContext).also { instance = it }
            }

        internal fun buildListenBrainzPayload(
            listenType: String,
            artist: String,
            track: String,
            album: String?,
            listenedAtSec: Long?
        ): String {
            val root = JSONObject()
            root.put("listen_type", listenType)
            val entry = JSONObject()
            if (listenType == "single" && listenedAtSec != null) {
                entry.put("listened_at", listenedAtSec)
            }
            val meta = JSONObject().apply {
                put("artist_name", artist)
                put("track_name", track)
                if (!album.isNullOrBlank()) put("release_name", album)
                put("additional_info", JSONObject().apply {
                    put("media_player", "Rhythm")
                    put("submission_client", "Rhythm Android")
                    put("submission_client_version", BuildConfig.VERSION_NAME)
                })
            }
            entry.put("track_metadata", meta)
            root.put("payload", JSONArray().put(entry))
            return root.toString()
        }

        internal fun signGnuFm(params: Map<String, String>, secret: String): String {
            val concatenated = buildString {
                params.entries.sortedBy { it.key }.forEach { (k, v) ->
                    append(k).append(v)
                }
                append(secret)
            }
            val digest = MessageDigest.getInstance("MD5").digest(concatenated.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appSettings by lazy { AppSettings.getInstance(context) }
    private val dao by lazy { RhythmDatabase.getInstance(context).pendingScrobbleDao() }
    private val http: OkHttpClient by lazy {
        UserTrustManager.buildUserTrustingHttpClientBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun nowPlaying(song: Song) {
        if (song.title.isBlank() || song.artist.isBlank()) return
        scope.launch {
            val store = appSettings.streamingPrefs
            val lbToken = store.listenBrainzToken.value
            val lfmSk = store.libreFmSessionKey.value
            val malojaUrl = store.malojaServerUrl.value
            val malojaTok = store.malojaToken.value
            if (lbToken == null && lfmSk == null && (malojaUrl == null || malojaTok == null)) return@launch

            if (!lbToken.isNullOrBlank()) {
                postListenBrainz(lbToken, "playing_now", song.artist, song.title, song.album, null)
            }
            if (!lfmSk.isNullOrBlank()) {
                postLibreFm(
                    method = "track.updateNowPlaying",
                    sessionKey = lfmSk,
                    params = mapOf(
                        "artist" to song.artist,
                        "track" to song.title,
                        "album" to song.album
                    )
                )
            }
            // Note: Maloja records completed listens only on `/apis/mlj_1/newscrobble`
        }
    }

    fun submit(song: Song, durationPlayedMs: Long, endedAtMs: Long = System.currentTimeMillis()) {
        if (song.title.isBlank() || song.artist.isBlank()) return
        // Standard scrobble eligibility: at least 30s played or half of track >= 10s
        val qualifies = durationPlayedMs >= 30_000L || (song.duration > 10_000L && durationPlayedMs >= song.duration / 2L)
        if (!qualifies) return

        scope.launch {
            val store = appSettings.streamingPrefs
            val playedAtSec = ((endedAtMs - durationPlayedMs) / 1000L).coerceAtLeast(1L)

            val lbToken = store.listenBrainzToken.value
            if (!lbToken.isNullOrBlank()) {
                val ok = postListenBrainz(lbToken, "single", song.artist, song.title, song.album, playedAtSec)
                if (!ok) enqueuePending(PROVIDER_LISTENBRAINZ, song, durationPlayedMs, endedAtMs)
            }

            val lfmSk = store.libreFmSessionKey.value
            if (!lfmSk.isNullOrBlank()) {
                val ok = postLibreFm(
                    method = "track.scrobble",
                    sessionKey = lfmSk,
                    params = mapOf(
                        "artist[0]" to song.artist,
                        "track[0]" to song.title,
                        "album[0]" to song.album,
                        "timestamp[0]" to playedAtSec.toString()
                    )
                )
                if (!ok) enqueuePending(PROVIDER_LIBREFM, song, durationPlayedMs, endedAtMs)
            }

            val malojaUrl = store.malojaServerUrl.value
            val malojaTok = store.malojaToken.value
            if (!malojaUrl.isNullOrBlank() && !malojaTok.isNullOrBlank()) {
                val ok = postMaloja(malojaUrl, malojaTok, song.artist, song.title, song.album, playedAtSec)
                if (!ok) enqueuePending(PROVIDER_MALOJA, song, durationPlayedMs, endedAtMs)
            }
        }
    }

    suspend fun flushPending(): Boolean {
        val batch = dao.getPendingBatch(limit = 50)
        if (batch.isEmpty()) return true

        val store = appSettings.streamingPrefs
        var allOk = true
        val now = System.currentTimeMillis()
        dao.pruneExpired(now - TimeUnit.DAYS.toMillis(30))

        for (item in batch) {
            val playedAtSec = ((item.playedAtMs - item.durationMs) / 1000L).coerceAtLeast(1L)
            val ok = when (item.provider) {
                PROVIDER_LISTENBRAINZ -> store.listenBrainzToken.value?.let { tok ->
                    postListenBrainz(tok, "single", item.artist, item.title, item.album, playedAtSec)
                } ?: true
                PROVIDER_LIBREFM -> store.libreFmSessionKey.value?.let { sk ->
                    postLibreFm(
                        method = "track.scrobble",
                        sessionKey = sk,
                        params = mapOf(
                            "artist[0]" to item.artist,
                            "track[0]" to item.title,
                            "album[0]" to item.album,
                            "timestamp[0]" to playedAtSec.toString()
                        )
                    )
                } ?: true
                PROVIDER_MALOJA -> {
                    val url = store.malojaServerUrl.value
                    val tok = store.malojaToken.value
                    if (url != null && tok != null) postMaloja(url, tok, item.artist, item.title, item.album, playedAtSec)
                    else true
                }
                else -> true
            }

            if (ok) {
                dao.deleteById(item.id)
            } else {
                dao.recordFailure(item.id, now, "HTTP delivery failed")
                allOk = false
            }
        }
        return allOk
    }

    suspend fun authenticateLibreFm(username: String, password: String): Result<String> = runCatching {
        val cleanUser = username.trim()
        require(cleanUser.isNotEmpty() && password.isNotEmpty()) { "Username and password required" }
        val passMd5 = md5(password)
        val authToken = md5(cleanUser.lowercase(Locale.ROOT) + passMd5)
        val params = mapOf(
            "method" to "auth.getMobileSession",
            "username" to cleanUser,
            "authToken" to authToken,
            "api_key" to LIBREFM_KEY
        )
        val body = executeGnuFmCall(params) ?: error("Could not reach Libre.fm")
        val json = JSONObject(body)
        val key = json.optJSONObject("session")?.optString("key").orEmpty()
        require(key.isNotBlank()) { json.optString("message", "Authentication failed") }
        appSettings.streamingPrefs.setLibreFmSessionKey(key)
        key
    }

    private suspend fun enqueuePending(provider: String, song: Song, durationMs: Long, endedAtMs: Long) {
        dao.insert(
            PendingScrobbleEntity(
                provider = provider,
                songId = song.id,
                artist = song.artist,
                title = song.title,
                album = song.album,
                durationMs = durationMs,
                playedAtMs = endedAtMs
            )
        )
        val request = OneTimeWorkRequestBuilder<ScrobbleFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "rhythm_scrobble_flush",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun postListenBrainz(
        token: String,
        listenType: String,
        artist: String,
        track: String,
        album: String?,
        playedAtSec: Long?
    ): Boolean = runCatching {
        val payload = buildListenBrainzPayload(listenType, artist, track, album, playedAtSec)
        val req = Request.Builder()
            .url("$LISTENBRAINZ_BASE/1/submit-listens")
            .header("Authorization", "Token $token")
            .header("User-Agent", "Rhythm/${BuildConfig.VERSION_NAME}")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            resp.isSuccessful || (listenType == "single" && resp.code == 400)
        }
    }.onFailure { e ->
        Log.w(TAG, "ListenBrainz submit failed: ${e.message}")
    }.getOrDefault(false)

    private fun postLibreFm(method: String, sessionKey: String, params: Map<String, String>): Boolean = runCatching {
        val signed = mutableMapOf(
            "method" to method,
            "sk" to sessionKey,
            "api_key" to LIBREFM_KEY
        ).apply {
            params.forEach { (k, v) -> if (v.isNotBlank()) put(k, v) }
        }
        val body = executeGnuFmCall(signed) ?: return false
        !JSONObject(body).has("error")
    }.onFailure { e ->
        Log.w(TAG, "Libre.fm $method failed: ${e.message}")
    }.getOrDefault(false)

    private fun executeGnuFmCall(params: Map<String, String>): String? {
        val sig = signGnuFm(params, LIBREFM_SECRET)
        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            add("api_sig", sig)
            add("format", "json")
        }.build()
        val req = Request.Builder()
            .url("$LIBREFM_BASE/2.0/")
            .header("User-Agent", "Rhythm/${BuildConfig.VERSION_NAME}")
            .post(form)
            .build()
        return http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body.string() else null
        }
    }

    private fun postMaloja(
        serverUrl: String,
        token: String,
        artist: String,
        track: String,
        album: String?,
        playedAtSec: Long
    ): Boolean = runCatching {
        val base = serverUrl.trim().trimEnd('/').let { if (it.contains("://")) it else "https://$it" }
        val json = JSONObject().apply {
            put("artist", artist)
            put("title", track)
            if (!album.isNullOrBlank()) put("album", album)
            put("time", playedAtSec)
            put("key", token)
        }
        val req = Request.Builder()
            .url("$base/apis/mlj_1/newscrobble")
            .header("User-Agent", "Rhythm/${BuildConfig.VERSION_NAME}")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp -> resp.isSuccessful }
    }.onFailure { e ->
        Log.w(TAG, "Maloja submit failed: ${e.message}")
    }.getOrDefault(false)

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
