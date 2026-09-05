/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.presentation.components.bottomsheets

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceId
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.M3FourColorCircularLoader
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job

data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int,
    val url: String,
    val type: String
)

class NearbyServerScanner(
    private val context: Context,
    private val serviceId: String,
    private val scope: CoroutineScope
) {
    val upperServiceId = serviceId.uppercase()
    val discoveredServers = mutableStateListOf<DiscoveredServer>()
    var isScanning by mutableStateOf(false)
        private set

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var multicastLock: WifiManager.MulticastLock? = null
    private var jellyfinUdpJob: Job? = null
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val pendingResolves = mutableListOf<NsdServiceInfo>()
    private var isResolving = false

    fun startScan() {
        if (isScanning) return
        isScanning = true

        try {
            multicastLock = wifiManager.createMulticastLock("RhythmGoDiscovery").apply {
                setReferenceCounted(true)
                try {
                    acquire()
                } catch (e: Exception) {
                    // Ignore lock acquisition failure
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        val targetServiceType = when (upperServiceId) {
            StreamingServiceId.JELLYFIN -> "_jellyfin._tcp"
            StreamingServiceId.SUBSONIC -> "_subsonic._tcp"
            else -> "_http._tcp"
        }

        val serviceTypesToScan = listOf(targetServiceType, "_http._tcp")

        if (upperServiceId == StreamingServiceId.JELLYFIN) {
            jellyfinUdpJob = scope.launch(Dispatchers.IO) {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 2000
                    }
                    val sendData = "Who is JellyfinServer?".toByteArray(Charsets.UTF_8)
                    val broadcastAddresses = getBroadcastAddresses()

                    fun sendBroadcasts() {
                        broadcastAddresses.forEach { addr ->
                            try {
                                val sendPacket = DatagramPacket(sendData, sendData.size, addr, 7359)
                                socket.send(sendPacket)
                            } catch (e: Exception) {
                                // Ignore send failures on specific interfaces
                            }
                        }
                        try {
                            val universalPacket = DatagramPacket(
                                sendData,
                                sendData.size,
                                InetAddress.getByName("255.255.255.255"),
                                7359
                            )
                            socket.send(universalPacket)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }

                    repeat(3) {
                        if (!isActive) return@repeat
                        sendBroadcasts()
                        delay(250)
                    }

                    val receiveBuffer = ByteArray(2048)
                    while (isActive) {
                        try {
                            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                            socket.receive(receivePacket)
                            val responseJson = String(
                                receivePacket.data,
                                0,
                                receivePacket.length,
                                Charsets.UTF_8
                            ).trim()

                            try {
                                val json = org.json.JSONObject(responseJson)
                                val rawAddress = json.optString("Address", "").trim()
                                val name = json.optString("Name", "").trim()
                                val pktHost = receivePacket.address.hostAddress ?: ""
                                if (pktHost.isNotBlank()) {
                                    val formattedPktHost = if (pktHost.contains(":") && !pktHost.startsWith("[")) "[$pktHost]" else pktHost
                                    val finalUrl = if (rawAddress.isNotBlank()) rawAddress else "http://$formattedPktHost:8096"
                                    withContext(Dispatchers.Main) {
                                        if (discoveredServers.none { it.url == finalUrl }) {
                                            discoveredServers.add(
                                                DiscoveredServer(
                                                    name = if (name.isBlank()) "Jellyfin" else name,
                                                    host = pktHost,
                                                    port = 8096,
                                                    url = finalUrl,
                                                    type = "Jellyfin"
                                                )
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore JSON errors
                            }
                        } catch (e: java.io.InterruptedIOException) {
                            sendBroadcasts()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                } catch (e: Exception) {
                    // Ignore fatal socket failures
                } finally {
                    socket?.close()
                }
            }
        }

        @Suppress("DEPRECATION")
        fun resolveNext() {
            if (isResolving || pendingResolves.isEmpty()) return
            isResolving = true
            val info = pendingResolves.removeAt(0)
            try {
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        mainHandler.post {
                            isResolving = false
                            resolveNext()
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                        mainHandler.post {
                            if (serviceInfo != null) {
                                val hostAddress = serviceInfo.host?.hostAddress
                                val port = serviceInfo.port
                                if (!hostAddress.isNullOrBlank()) {
                                    val formattedHost = if (hostAddress.contains(":") && !hostAddress.startsWith("[")) {
                                        "[$hostAddress]"
                                    } else {
                                        hostAddress
                                    }
                                    val url = "http://$formattedHost:$port"
                                    val nameLower = (serviceInfo.serviceName ?: "").lowercase()
                                    val serverType = if (serviceInfo.serviceType.contains("jellyfin", ignoreCase = true) || nameLower.contains("jellyfin")) {
                                        "Jellyfin"
                                    } else if (nameLower.contains("navidrome")) {
                                        "Navidrome"
                                    } else if (serviceInfo.serviceType.contains("subsonic", ignoreCase = true) || nameLower.contains("subsonic")) {
                                        "Subsonic"
                                    } else if (upperServiceId == StreamingServiceId.JELLYFIN) {
                                        "Jellyfin"
                                    } else {
                                        "Subsonic"
                                    }

                                    if (discoveredServers.none { it.url == url }) {
                                        discoveredServers.add(
                                            DiscoveredServer(
                                                name = serviceInfo.serviceName ?: "Unknown Server",
                                                host = hostAddress,
                                                port = port,
                                                url = url,
                                                type = serverType
                                            )
                                        )
                                    }
                                }
                            }
                            isResolving = false
                            resolveNext()
                        }
                    }
                })
            } catch (e: Exception) {
                isResolving = false
                resolveNext()
            }
        }

        serviceTypesToScan.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    isScanning = false
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    isScanning = false
                }

                override fun onDiscoveryStarted(regType: String?) {
                    isScanning = true
                }

                override fun onDiscoveryStopped(serviceType: String?) {
                    isScanning = false
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo != null) {
                        if (type.startsWith("_http._tcp")) {
                            val name = serviceInfo.serviceName?.lowercase() ?: ""
                            val port = serviceInfo.port
                            val isJellyfinMatch = upperServiceId == StreamingServiceId.JELLYFIN && 
                                (port == 8096 || name.contains("jellyfin") || name.contains("emby") || name.contains("media") || name.contains("server") || name.contains("arch"))
                            val isSubsonicMatch = upperServiceId == StreamingServiceId.SUBSONIC && 
                                (port == 4533 || port == 4040 || name.contains("subsonic") || name.contains("navidrome") || name.contains("airsonic") || name.contains("music") || name.contains("server"))
                            
                            if (!isJellyfinMatch && !isSubsonicMatch) {
                                return
                            }
                        }
                        
                        mainHandler.post {
                            pendingResolves.add(serviceInfo)
                            resolveNext()
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo != null) {
                        discoveredServers.removeIf { it.name == serviceInfo.serviceName }
                    }
                }
            }
            listeners.add(listener)
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                // Ignore failure for individual type scans
            }
        }
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        mainHandler.removeCallbacksAndMessages(null)
        jellyfinUdpJob?.cancel()
        jellyfinUdpJob = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            // Ignore release failure
        }
        multicastLock = null
        listeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // Ignore
            }
        }
        listeners.clear()
        pendingResolves.clear()
    }

    fun rescan() {
        stopScan()
        discoveredServers.clear()
        startScan()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyServerDiscoverySheet(
    serviceId: String,
    onDismiss: () -> Unit,
    onServerSelected: (String) -> Unit,
    scanner: NearbyServerScanner? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val upperServiceId = remember(serviceId) { serviceId.uppercase() }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    
    val effectiveScanner = scanner ?: remember(upperServiceId) {
        NearbyServerScanner(context, upperServiceId, scope)
    }

    DisposableEffect(effectiveScanner) {
        if (scanner == null) {
            effectiveScanner.startScan()
        }
        onDispose {
            if (scanner == null) {
                effectiveScanner.stopScan()
            }
        }
    }

    val discoveredServers = effectiveScanner.discoveredServers
    val isScanning = effectiveScanner.isScanning

    // Automatically stop scanning after 10 seconds to conserve battery
    LaunchedEffect(effectiveScanner.isScanning) {
        if (effectiveScanner.isScanning) {
            delay(10000)
            effectiveScanner.stopScan()
        }
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(id = R.string.nearby_server_discovery_title),
            subtitle = stringResource(id = R.string.nearby_server_discovery_desc),
            visible = true
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            if (isScanning && discoveredServers.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    M3FourColorCircularLoader(
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.nearby_server_discovery_scanning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (discoveredServers.isEmpty() && !isScanning) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("wifi_off"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.nearby_server_discovery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    RhythmGroupedButton(
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Large
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                effectiveScanner.rescan()
                            },
                            weight = 1f,
                            isFirst = true,
                            isLast = true,
                            type = RhythmButtonType.Tonal,
                            icon = MaterialSymbolIcon("refresh"),
                            text = stringResource(id = R.string.nearby_server_discovery_rescan)
                        )
                    }
                }
            } else if (discoveredServers.isEmpty() && isScanning) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                ) {
                    M3FourColorCircularLoader(
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(id = R.string.nearby_server_discovery_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val serverItems = remember(discoveredServers) {
                    discoveredServers.map { server ->
                        Material3SettingsItem(
                            leadingContent = {
                                val serverIcon = when {
                                    server.type.contains("Jellyfin", ignoreCase = true) -> R.drawable.ic_jellyfin
                                    server.type.contains("Navidrome", ignoreCase = true) -> R.drawable.ic_navidrome
                                    server.type.contains("Subsonic", ignoreCase = true) -> R.drawable.ic_subsonic
                                    else -> when (upperServiceId) {
                                        StreamingServiceId.JELLYFIN -> R.drawable.ic_jellyfin
                                        StreamingServiceId.SUBSONIC -> R.drawable.ic_subsonic
                                        else -> null
                                    }
                                }
                                if (serverIcon != null) {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = serverIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("dns"),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            description = {
                                Text(
                                    text = "${server.type} • ${server.url}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = MaterialSymbolIcon("chevron_right"),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { onServerSelected(server.url) }
                        )
                    }
                }

                val serverListState = rememberLazyListState()

                AdaptiveSheetScrollContainer(
                    lazyListState = serverListState,
                    blendColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) { endPadding ->
                    LazyColumn(
                        state = serverListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(end = endPadding)
                    ) {
                        item {
                            Material3SettingsGroup(
                                items = serverItems,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
                
                if (!isScanning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    RhythmGroupedButton(
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Large
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                effectiveScanner.rescan()
                            },
                            weight = 1f,
                            isFirst = true,
                            isLast = true,
                            type = RhythmButtonType.Tonal,
                            icon = MaterialSymbolIcon("refresh"),
                            text = stringResource(id = R.string.nearby_server_discovery_rescan)
                        )
                    }
                }
            }
        }
    }
}

private fun getBroadcastAddresses(): List<InetAddress> {
    val addresses = mutableListOf<InetAddress>()
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val broadcast = interfaceAddress.broadcast
                if (broadcast != null) {
                    addresses.add(broadcast)
                }
            }
        }
    } catch (e: Exception) {
        // Ignore errors
    }
    if (addresses.isEmpty()) {
        try {
            addresses.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
            // Ignore
        }
    }
    return addresses
}

