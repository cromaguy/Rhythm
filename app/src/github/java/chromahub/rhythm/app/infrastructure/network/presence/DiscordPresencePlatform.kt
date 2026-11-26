/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network.presence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal object DiscordPresencePlatform {
    val isSupported: Boolean = true
    fun create(context: Context): DiscordPresenceController = DiscordRichPresenceClient(context.applicationContext)
}

internal object DiscordPresencePayloadBuilder {
    const val TYPE_LISTENING = 2
    const val MAX_FIELD = 120
    const val PROJECT_URL = "https://github.com/cromaguy/Rhythm"

    fun clip(value: String, limit: Int = MAX_FIELD): String {
        val clean = value.trim()
        return if (clean.length > limit) clean.substring(0, limit - 1) + "..." else clean
    }

    fun formatStateLine(state: DiscordPresenceTrackState): String {
        val parts = mutableListOf<String>()
        state.artist.trim().takeIf { it.isNotEmpty() }?.let { parts += it }
        val codec = state.codecLabel?.trim()?.takeIf { it.isNotEmpty() }
        val rate = state.sampleRateHz?.takeIf { it > 0 }?.let { hz ->
            val khz = hz / 1000.0
            if ((khz * 10.0).roundToInt() % 10 == 0) "${khz.roundToInt()} kHz" else "$khz kHz"
        }
        val depth = state.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" }
        val quality = listOfNotNull(codec, depth, rate).joinToString(" · ")
        if (quality.isNotEmpty()) parts += quality
        return clip(parts.joinToString(" · ").ifEmpty { "Rhythm Music Player" })
    }

    fun buildSetActivityFrame(
        state: DiscordPresenceTrackState,
        pid: Int,
        nowWallMs: Long = System.currentTimeMillis(),
        nonce: String = UUID.randomUUID().toString()
    ): String {
        val activity = JSONObject().apply {
            put("name", clip("Rhythm · ${state.title}"))
            put("type", TYPE_LISTENING)
            put("details", clip(state.title))
            put("state", formatStateLine(state))
            if (state.isPlaying && state.durationMs > 0L) {
                val startMs = (nowWallMs - state.positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
                val endMs = startMs + state.durationMs
                put("timestamps", JSONObject().apply {
                    put("start", startMs)
                    put("end", endMs)
                })
            }
            put("buttons", JSONArray().apply {
                put(JSONObject().apply {
                    put("label", "Get Rhythm")
                    put("url", PROJECT_URL)
                })
            })
        }
        return JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("args", JSONObject().apply {
                put("pid", pid)
                put("activity", activity)
            })
            put("nonce", nonce)
        }.toString()
    }

    fun buildClearActivityFrame(pid: Int, nonce: String = UUID.randomUUID().toString()): String {
        return JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("args", JSONObject().apply {
                put("pid", pid)
                put("activity", JSONObject.NULL)
            })
            put("nonce", nonce)
        }.toString()
    }
}

private class DiscordRichPresenceClient(
    private val context: Context
) : DiscordPresenceController {

    companion object {
        private const val TAG = "DiscordPresenceClient"
        private const val RPC_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
        private const val PUSH_THROTTLE_MS = 12_000L
        private const val RECONNECT_THROTTLE_MS = 15_000L
        private val DISCORD_PACKAGES = listOf(
            "com.discord",
            "com.discord.canary",
            "com.discord.ptb",
            "com.discord.debug"
        )
    }

    override val isSupported: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var serviceConnection: ServiceConnection? = null
    private var rpcConnection: IDiscordRpcConnection? = null
    private var lastSignature: String = ""
    private var lastPushElapsedMs: Long = 0L
    private var lastConnectAttemptElapsedMs: Long = 0L

    override fun onPlaybackUpdated(
        enabled: Boolean,
        applicationId: String?,
        state: DiscordPresenceTrackState?
    ) {
        val parsedAppId = applicationId?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        if (!enabled || parsedAppId == null || state == null || !state.isPlaying) {
            scope.launch { clearInternal() }
            return
        }

        val now = SystemClock.elapsedRealtime()
        val sig = "${parsedAppId}|${state.title}|${state.artist}|${state.isPlaying}|${state.positionMs / 5_000L}"
        if (sig == lastSignature && (now - lastPushElapsedMs) < PUSH_THROTTLE_MS) {
            return
        }

        scope.launch {
            mutex.withLock {
                val conn = ensureConnectedLocked(parsedAppId) ?: return@withLock
                val frame = DiscordPresencePayloadBuilder.buildSetActivityFrame(state, Process.myPid())
                val sent = runCatching {
                    conn.sendFrame(frame)
                    true
                }.onFailure {
                    Log.w(TAG, "Discord sendFrame failed; dropping IPC connection", it)
                    disconnectLocked()
                }.getOrDefault(false)
                if (sent) {
                    lastSignature = sig
                    lastPushElapsedMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    override fun clear() {
        scope.launch {
            clearInternal()
            scope.cancel()
        }
    }

    private suspend fun clearInternal() {
        mutex.withLock {
            val conn = rpcConnection
            if (conn != null && runCatching { conn.asBinder().isBinderAlive }.getOrDefault(false)) {
                runCatching {
                    conn.sendFrame(DiscordPresencePayloadBuilder.buildClearActivityFrame(Process.myPid()))
                }
            }
            disconnectLocked()
            lastSignature = ""
        }
    }

    private suspend fun ensureConnectedLocked(applicationId: Long): IDiscordRpcConnection? {
        val existing = rpcConnection
        if (existing != null && runCatching { existing.asBinder().isBinderAlive }.getOrDefault(false)) {
            return existing
        }
        disconnectLocked()
        val elapsed = SystemClock.elapsedRealtime()
        if ((elapsed - lastConnectAttemptElapsedMs) < RECONNECT_THROTTLE_MS) {
            return null
        }
        lastConnectAttemptElapsedMs = elapsed

        val component = findRpcComponent() ?: return null
        val bound = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<Pair<IBinder, ServiceConnection>?> { cont ->
                val done = AtomicBoolean(false)
                fun finish(v: Pair<IBinder, ServiceConnection>?) {
                    if (done.compareAndSet(false, true) && cont.isActive) cont.resume(v)
                }
                val sc = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        finish(service?.let { it to this })
                    }
                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                    override fun onBindingDied(name: ComponentName?) { finish(null) }
                    override fun onNullBinding(name: ComponentName?) { finish(null) }
                }
                val ok = runCatching {
                    context.bindService(Intent(RPC_ACTION).setComponent(component), sc, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
                if (!ok) {
                    runCatching { context.unbindService(sc) }
                    finish(null)
                }
                cont.invokeOnCancellation { runCatching { context.unbindService(sc) } }
            }
        } ?: return null

        val service = IDiscordRpcService.Stub.asInterface(bound.first) ?: run {
            runCatching { context.unbindService(bound.second) }
            return null
        }

        val callback = object : IDiscordRpcCallback.Stub() {
            override fun onFrame(frame: String?) = Unit
            override fun onClose(code: Int, message: String?) {
                Log.d(TAG, "Discord RPC closed ($code): $message")
            }
        }
        val conn = runCatching { service.connect(applicationId, "1", callback) }.getOrNull()
        if (conn == null) {
            runCatching { context.unbindService(bound.second) }
            return null
        }
        serviceConnection = bound.second
        rpcConnection = conn
        return conn
    }

    private fun disconnectLocked() {
        runCatching { rpcConnection?.disconnect() }
        rpcConnection = null
        serviceConnection?.let { sc -> runCatching { context.unbindService(sc) } }
        serviceConnection = null
    }

    private fun findRpcComponent(): ComponentName? {
        val pm = context.packageManager
        for (pkg in DISCORD_PACKAGES) {
            runCatching {
                val services = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong())).services
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services
                }
                services?.firstOrNull { it.exported && it.name.contains("rpc", ignoreCase = true) }
                    ?.let { return ComponentName(pkg, it.name) }
            }
            val intent = Intent(RPC_ACTION).setPackage(pkg)
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveService(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveService(intent, PackageManager.MATCH_ALL)
            }
            resolved?.serviceInfo?.let { return ComponentName(it.packageName, it.name) }
        }
        return null
    }
}
