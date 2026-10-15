// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Real Android WebView + production HTML. Only remote YouTube responses are replaced.
 * This verifies our embedding protocol, not YouTube service availability. */
class YouTubeProtocolTest {
    @get:Rule val compose = createComposeRule()

    @Test fun iframeLoadsWithAppRefererFillsCoverAndStaysMutedWhileSeeking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = AtomicBoolean(false)
        val iframeAllowed = AtomicBoolean(false)
        val referer = AtomicReference<String>()
        val error = AtomicReference<String>()
        lateinit var web: YouTubeVideoView
        compose.setContent {
            AndroidView(factory = { ctx -> YouTubeVideoView(ctx) { name, value ->
                if (name == "ready") ready.set(true)
                if (name == "error") error.set(value)
            }.also { view ->
                web = view
                val production = view.webViewClient
                view.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val blocked = production.shouldOverrideUrlLoading(view, request)
                        if (!request.isForMainFrame) iframeAllowed.set(!blocked)
                        return blocked
                    }
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                        val isApi = request.url.path == "/iframe_api"
                        if (request.url.path?.startsWith("/embed/") == true) {
                            // Some WebView versions do not call shouldOverrideUrlLoading for initial iframe loads.
                            // Exercise the production decision on the actual subframe request in either case.
                            iframeAllowed.set(!production.shouldOverrideUrlLoading(view, request))
                            referer.set(request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value)
                        }
                        val body = if (isApi) stubApi else """<html><body><script>parent.postMessage('test-iframe-ready','*');</script></body></html>"""
                        return WebResourceResponse(if (isApi) "application/javascript" else "text/html", "UTF-8", body.byteInputStream())
                    }
                }
                view.loadVideo("abcdefghijk", 13_750L, false)
            } }, modifier = Modifier.requiredSize(300.dp, 360.dp))
            DisposableEffect(Unit) { onDispose { web.dispose() } }
        }
        compose.waitUntil(90000) { ready.get() }
        assertTrue("HTTPS iframe navigation must be allowed", iframeAllowed.get())
        assertEquals("https://${context.packageName}/", referer.get())
        val initial = evaluate(web, "({muted:player.muted,playing:player.playing,time:player.time,origin:player.config.playerVars.origin,w:document.getElementById('mv').getBoundingClientRect().width,h:document.getElementById('mv').getBoundingClientRect().height,vw:innerWidth,vh:innerHeight})")
        assertTrue(initial.getBoolean("muted")); assertFalse(initial.getBoolean("playing"))
        assertEquals(13.75, initial.getDouble("time"), .01)
        assertEquals("https://${context.packageName}", initial.getString("origin"))
        assertTrue(initial.getDouble("w") >= initial.getDouble("vw"))
        assertTrue(initial.getDouble("h") >= initial.getDouble("vh"))
        assertEquals(16.0 / 9, initial.getDouble("w") / initial.getDouble("h"), .01)
        val moved = evaluate(web, "(setPlayback(true,42.5),{muted:player.muted,playing:player.playing,time:player.time})")
        assertTrue(moved.getBoolean("muted")); assertTrue(moved.getBoolean("playing"))
        assertEquals(42.5, moved.getDouble("time"), .01)
        compose.runOnIdle { web.evaluateJavascript("player.config.events.onError({data:153});", null) }
        compose.waitUntil(10000) { error.get() == "153" }
    }

    private fun evaluate(web: WebView, expression: String): JSONObject {
        val result = AtomicReference<String>(); val latch = CountDownLatch(1)
        compose.runOnIdle { web.evaluateJavascript("JSON.stringify($expression)") { result.set(it); latch.countDown() } }
        assertTrue(latch.await(20, TimeUnit.SECONDS))
        return JSONObject(org.json.JSONTokener(result.get()).nextValue() as String)
    }

    private val stubApi = """
        window.YT={Player:function(id,config){
          this.config=config; this.muted=false; this.playing=false; this.time=0;
          this.mute=function(){this.muted=true;}; this.setVolume=function(){};
          this.getCurrentTime=function(){return this.time;}; this.getDuration=function(){return 120;};
          this.seekTo=function(time){this.time=time;};
          this.playVideo=function(){this.playing=true;}; this.pauseVideo=function(){this.playing=false;};
          var iframe=document.createElement('iframe'); iframe.id=id;
          iframe.src='https://www.youtube.com/embed/'+config.videoId;
          document.getElementById(id).replaceWith(iframe);
          window.addEventListener('message',function(event){if(event.data==='test-iframe-ready')config.events.onReady();});
        }};
        onYouTubeIframeAPIReady();
    """.trimIndent()
}
