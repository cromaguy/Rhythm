/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network

import okhttp3.Dns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Orders IPv4 records ahead of IPv6 while preserving original DNS ordering within each family,
 * avoiding stalled Happy-Eyeballs handshakes on broken IPv6 mobile/LAN routes to self-hosted
 * Navidrome, Subsonic, and Jellyfin instances. Falls back to DNS-over-HTTPS when the platform
 * resolver fails for public internet hostnames.
 */
object Ipv4FirstDnsResolver : Dns {

    private val DOH_ENDPOINTS = listOf(
        "https://1.1.1.1/dns-query",
        "https://dns.google/resolve"
    )
    private val IPV4_LITERAL_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    override fun lookup(hostname: String): List<InetAddress> {
        val systemResult = runCatching { Dns.SYSTEM.lookup(hostname) }
        val systemAddresses = systemResult.getOrNull().orEmpty()
        if (systemAddresses.isNotEmpty()) {
            return sortAddresses(systemAddresses)
        }

        if (shouldUseDohFallback(hostname)) {
            val dohAddresses = resolveViaDoH(hostname)
            if (dohAddresses.isNotEmpty()) {
                return sortAddresses(dohAddresses)
            }
        }

        val exception = systemResult.exceptionOrNull()
        if (exception is UnknownHostException) throw exception
        throw UnknownHostException("Unable to resolve host: $hostname").apply {
            if (exception != null) initCause(exception)
        }
    }

    fun sortAddresses(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.size <= 1) return addresses
        val (ipv4, other) = addresses.partition { it is Inet4Address }
        return if (ipv4.isEmpty()) addresses else ipv4 + other
    }

    internal fun shouldUseDohFallback(hostname: String): Boolean {
        val trimmed = hostname.trim().lowercase()
        if (trimmed.isEmpty() || !trimmed.contains('.')) return false
        if (trimmed.endsWith(".local") || trimmed.endsWith(".lan") || trimmed.endsWith(".internal")) return false
        if (IPV4_LITERAL_REGEX.matches(trimmed) || trimmed.contains(':')) return false
        return true
    }

    internal fun parseDnsJsonResponse(jsonPayload: String): List<InetAddress> {
        val root = runCatching { JSONObject(jsonPayload) }.getOrNull() ?: return emptyList()
        if (root.optInt("Status", -1) != 0) return emptyList()
        val answers = root.optJSONArray("Answer") ?: return emptyList()
        val addresses = ArrayList<InetAddress>(answers.length())
        for (i in 0 until answers.length()) {
            val entry = answers.optJSONObject(i) ?: continue
            val type = entry.optInt("type", -1)
            if (type != 1 && type != 28) continue
            val data = entry.optString("data").trim()
            if (data.isEmpty()) continue
            runCatching { InetAddress.getByName(data) }.getOrNull()?.let(addresses::add)
        }
        return sortAddresses(addresses)
    }

    private fun resolveViaDoH(hostname: String): List<InetAddress> {
        val encodedHost = runCatching { URLEncoder.encode(hostname, "UTF-8") }.getOrNull() ?: return emptyList()
        for (endpoint in DOH_ENDPOINTS) {
            for (recordType in listOf("A", "AAAA")) {
                val result = queryDohEndpoint("$endpoint?name=$encodedHost&type=$recordType")
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }
        return emptyList()
    }

    private fun queryDohEndpoint(urlString: String): List<InetAddress> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                setRequestProperty("Accept", "application/dns-json")
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseDnsJsonResponse(body)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }
}
