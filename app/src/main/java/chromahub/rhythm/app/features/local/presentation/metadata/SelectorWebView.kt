// SPDX-License-Identifier: GPL-3.0-or-later

package chromahub.rhythm.app.features.local.presentation.metadata

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kr.co.metadata.crawler.ElementSelection
import kr.co.metadata.crawler.SelectionMessageCodec
import java.net.URI

/** Main-frame-only messages: embedded frames cannot send selections as the visible page. */
// Instantiated programmatically with callbacks; this view is not inflated from XML.
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class SelectorWebView(
    context: Context,
    onSelection: (ElementSelection) -> Unit,
    onError: () -> Unit,
    onPageReady: (String) -> Unit,
    onLoading: () -> Unit,
) : WebView(context) {
    var picking = true
        set(value) {
            field = value
            evaluateJavascript("window.MusicCrawlerPicker && window.MusicCrawlerPicker.${if (value) "enable" else "disable"}();", null)
        }

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "MusicCrawlerBridge", setOf("*")) { _, message, origin, mainFrame, _ ->
                if (!mainFrame || origin.scheme != "https") return@addWebMessageListener
                val sample = runCatching { SelectionMessageCodec.decode(message.data.orEmpty()) }.getOrNull()
                val current = url
                if (sample == null) onError()
                else if (picking && current != null && sample.pageUrl == current &&
                    runCatching { URI(current).host == origin.host && URI(current).scheme == "https" }.getOrDefault(false)) {
                    onSelection(sample)
                }
            }
        } else onError()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isAllowedUrl(request.url.toString())

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { onLoading() }

            override fun onPageFinished(view: WebView, url: String) {
                if (!isAllowedUrl(url) || view.url != url) return
                val script = context.assets.open("music-crawler-picker.js").bufferedReader().use { it.readText() }
                evaluateJavascript(script) {
                    if (view.url == url) {
                        picking = picking
                        onPageReady(url)
                    }
                }
            }
        }
    }

    companion object {
        fun isAllowedUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null
        }.getOrDefault(false)
    }
}
