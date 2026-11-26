/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Orders IPv4 records ahead of IPv6 while preserving original DNS ordering within each family,
 * avoiding stalled Happy-Eyeballs handshakes on broken IPv6 mobile/LAN routes to self-hosted
 * Navidrome, Subsonic, and Jellyfin instances.
 */
object Ipv4FirstDnsResolver : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = Dns.SYSTEM.lookup(hostname)
        return sortAddresses(resolved)
    }

    fun sortAddresses(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.size <= 1) return addresses
        val (ipv4, other) = addresses.partition { it is Inet4Address }
        return if (ipv4.isEmpty()) addresses else ipv4 + other
    }
}
