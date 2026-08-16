package chromahub.rhythm.app.network

import chromahub.rhythm.app.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

object WikipediaProvider {
    private val client by lazy { OkHttpClient() }

    suspend fun getAlbumDescription(albumTitle: String, artistName: String?): String? = withContext(Dispatchers.IO) {
        try {
            // Clean album title (strip Deluxe, Remastered, Bonus Track Version, etc.)
            val cleanedTitle = albumTitle
                .replace(Regex("(?i)\\s*[\\[\\(](deluxe|remastered|expanded|anniversary|bonus track|edition|special|explicit|live|version)[\\]\\)]"), "")
                .trim()

            // Try precise queries first: "Album (Artist album)" or "Album (Artist)"
            if (!artistName.isNullOrBlank()) {
                val preciseQueries = listOf(
                    "$cleanedTitle ($artistName album)",
                    "$cleanedTitle ($artistName)",
                    "$albumTitle ($artistName album)",
                    "$albumTitle ($artistName)"
                )
                for (query in preciseQueries) {
                    val summary = fetchPageSummary(query)
                    if (summary != null && !summary.contains("may refer to", ignoreCase = true)) {
                        return@withContext summary
                    }
                }
            }

            // Try generic queries: "Album (album)" or just "Album"
            val genericQueries = listOf(
                "$cleanedTitle (album)",
                cleanedTitle,
                "$albumTitle (album)",
                albumTitle
            )
            for (query in genericQueries) {
                val summary = fetchPageSummary(query)
                if (summary != null && !summary.contains("may refer to", ignoreCase = true)) {
                    if (!artistName.isNullOrBlank()) {
                        if (summary.contains(artistName, ignoreCase = true)) {
                            return@withContext summary
                        }
                    } else {
                        return@withContext summary
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchPageSummary(title: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Rhythm/1.0 (contact: github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO})")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val body = response.body.string()
                    val obj = JsonParser.parseString(body).asJsonObject
                    return@use obj.get("extract")?.asString
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
