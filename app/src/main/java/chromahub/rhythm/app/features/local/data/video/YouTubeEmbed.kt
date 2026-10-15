// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import java.net.URI
import java.util.Locale

/** The installed app identifies itself; no website identity or browser UA is impersonated. */
internal object YouTubeEmbed {
    fun origin(packageName: String): String {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
        return "https://${packageName.lowercase(Locale.ROOT)}"
    }

    fun page(template: String, packageName: String, videoId: String, positionMs: Long, playing: Boolean): String {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        return template.replace("__APP_ORIGIN__", origin(packageName))
            .replace("__VIDEO_ID__", videoId)
            .replace("__START_SECONDS__", (positionMs.coerceAtLeast(0) / 1000.0).toString())
            .replace("__PLAYING__", playing.toString())
    }

    /** Blocking every navigation also blocks the YouTube iframe itself. Keep HTTPS subframes. */
    fun allowNavigation(url: String, mainFrame: Boolean): Boolean {
        if (url == "about:blank") return true
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return !mainFrame && uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
    }
}
