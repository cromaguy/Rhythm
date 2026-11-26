/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.util.Log
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Utility that creates an [OkHttpClient.Builder] pre-configured with an
 * [X509TrustManager] that trusts **both** system pre-installed CAs **and**
 * user-installed CAs (e.g. a custom root CA imported in Android Settings →
 * Security → Trusted credentials → User tab).
 *
 * Without this, OkHttp defaults to a TrustManager backed only by the system
 * KeyStore, which silently ignores user-installed certificates even though
 * Android's own browser and apps would accept them.  This causes:
 *
 *   java.security.cert.CertPathValidatorException:
 *       Trust anchor for certification path not found.
 *
 * when connecting to a Navidrome / Jellyfin server that is secured by a
 * private PKI whose root CA the user has imported into Android.
 *
 * Usage:
 *   val client = buildUserTrustingHttpClientBuilder().connectTimeout(...).build()
 */
internal object UserTrustManager {

    private const val TAG = "UserTrustManager"

    /**
     * Returns an [OkHttpClient.Builder] whose SSL layer trusts both the
     * Android system trust store and the Android user trust store.
     *
     * Falls back to a plain [OkHttpClient.Builder] (default TrustManager) if
     * anything goes wrong so that the app never crashes due to SSL setup.
     */
    fun buildUserTrustingHttpClientBuilder(): OkHttpClient.Builder {
        val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
        return try {
            val trustManager = buildUserAwareTrustManager()
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), null)
            }
            OkHttpClient.Builder()
                .dns(chromahub.rhythm.app.infrastructure.network.Ipv4FirstDnsResolver)
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { hostname, session ->
                    isPrivateOrLocalHost(hostname) || defaultVerifier.verify(hostname, session)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build user-trusting TrustManager, falling back to default: ${e.message}", e)
            OkHttpClient.Builder()
                .dns(chromahub.rhythm.app.infrastructure.network.Ipv4FirstDnsResolver)
                .hostnameVerifier { hostname, session ->
                    isPrivateOrLocalHost(hostname) || defaultVerifier.verify(hostname, session)
                }
        }
    }

    /**
     * Checks if a hostname corresponds to a local RFC1918 private network, loopback, or LAN domain.
     */
    fun isPrivateOrLocalHost(host: String): Boolean {
        val lower = host.lowercase().trim().trimStart('[').trimEnd(']')
        if (lower == "localhost" ||
            lower.endsWith(".local") ||
            lower.endsWith(".localdomain") ||
            lower.endsWith(".lan") ||
            lower.endsWith(".home") ||
            lower.endsWith(".home.arpa") ||
            lower.endsWith(".ts.net") ||
            lower.endsWith(".mesh") ||
            lower.endsWith(".internal") ||
            lower.endsWith(".host") ||
            lower.endsWith(".priv") ||
            !lower.contains(".")
        ) {
            return true
        }

        if (lower == "::1" || lower.startsWith("fe80:") || lower.startsWith("fd") || lower.startsWith("fc")) {
            return true
        }

        val parts = lower.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 127) ||
            (first == 100 && second in 64..127)
    }

    /**
     * Builds a composite [X509TrustManager] that delegates validation to
     * **both** the system CA store and the user CA store.
     *
     * Android exposes the combined trust store (system + user) via the
     * "AndroidCAStore" [KeyStore] type. Feeding that KeyStore into a
     * [TrustManagerFactory] is the simplest and most correct way to obtain
     * a TrustManager that honours user-installed certificates.
     */
    private fun buildUserAwareTrustManager(): X509TrustManager {
        // "AndroidCAStore" contains system CAs + any user-installed CAs.
        val androidCaStore = KeyStore.getInstance("AndroidCAStore").also { it.load(null) }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).also {
            it.init(androidCaStore)
        }

        return tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("No X509TrustManager found in AndroidCAStore TrustManagerFactory")
    }
}
