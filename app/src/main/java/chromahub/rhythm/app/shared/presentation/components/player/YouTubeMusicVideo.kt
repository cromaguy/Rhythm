// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import chromahub.rhythm.app.features.local.data.video.YouTubeEmbed
import kotlinx.coroutines.delay

/** Kept separate so the real WebView/iframe protocol can also be exercised without internet. */
@SuppressLint("SetJavaScriptEnabled")
internal class YouTubeVideoView(context: Context, onEvent: (String, String) -> Unit) : WebView(context) {
    private var disposed = false

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false; settings.allowContentAccess = false
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webChromeClient = WebChromeClient()
        addJavascriptInterface(object {
            @androidx.annotation.Keep @JavascriptInterface fun event(name: String, value: String) {
                post { if (!disposed) onEvent(name, value) }
            }
        }, "T2Video")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !YouTubeEmbed.allowNavigation(request.url.toString(), request.isForMainFrame)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame || isPlayerDocument(request)) onEvent("network", "")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame || isPlayerDocument(request)) onEvent("network", response.statusCode.toString())
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // A dead renderer must not receive further sync commands or take down the app.
                disposed = true
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
                onEvent("network", "")
                return true
            }

            private fun isPlayerDocument(request: WebResourceRequest): Boolean =
                request.url.host == "www.youtube.com" &&
                    (request.url.path?.startsWith("/embed/") == true || request.url.path == "/iframe_api")
        }
    }

    fun loadVideo(videoId: String, positionMs: Long, playing: Boolean) {
        val html = YouTubeEmbed.page(context.assets.open("music-video.html").bufferedReader().use { it.readText() },
            context.packageName, videoId, positionMs, playing)
        val baseUrl = YouTubeEmbed.origin(context.packageName) + "/"
        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        evaluateJavascript("setPlayback(false,0);", null)
        removeJavascriptInterface("T2Video")
        stopLoading(); loadUrl("about:blank"); destroy()
    }

    fun syncPlayback(playing: Boolean, positionMs: Long) {
        if (!disposed) evaluateJavascript("if(typeof setPlayback==='function')setPlayback($playing,${positionMs / 1000.0});", null)
    }
}

@Composable
internal fun YouTubeMusicVideo(videoId: String, offsetMs: Long, playing: Boolean, position: () -> Long,
    modifier: Modifier, onEvent: (String, String) -> Unit) {
    val context = LocalContext.current
    val currentEvent by rememberUpdatedState(onEvent)
    val currentPosition by rememberUpdatedState(position)
    // Offset changes use the sync command; never rebuild a WebView on a seek or play/pause.
    val web = remember(videoId) { YouTubeVideoView(context) { name, value -> currentEvent(name, value) }.apply {
        loadVideo(videoId, currentPosition() + offsetMs, playing)
    } }
    LaunchedEffect(web, playing, offsetMs) {
        while (true) {
            web.syncPlayback(playing, currentPosition() + offsetMs)
            delay(1000)
        }
    }
    DisposableEffect(web) { onDispose { web.dispose() } }
    key(web) { AndroidView(factory = { web }, modifier = modifier) }
}
