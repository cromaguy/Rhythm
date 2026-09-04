/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceRules
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceId
import chromahub.rhythm.app.features.streaming.presentation.model.StreamingServiceOptions
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.features.streaming.presentation.components.bottomsheets.NearbyServerDiscoverySheet
import chromahub.rhythm.app.shared.presentation.components.common.M3LinearLoader
import chromahub.rhythm.app.shared.presentation.components.common.M3FourColorCircularLoader

@Composable
fun StreamingServiceSetupScreen(
    serviceId: String,
    viewModel: StreamingMusicViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings = remember(context) { AppSettings.getInstance(context) }
    val sessions by viewModel.serviceSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val rememberStreamingPasswords by appSettings.rememberStreamingPasswords.collectAsState()
    val isBusy = isLoading

    val option = remember(serviceId) {
        StreamingServiceOptions.defaults.firstOrNull { it.id == serviceId }
    }
    val optionName = option?.let { stringResource(id = it.nameRes) } ?: serviceId
    val optionDescription = option?.let { stringResource(id = it.descriptionRes) }
    val session = sessions[serviceId] ?: viewModel.getServiceSession(serviceId)
    val requiresServerUrl = remember(serviceId) { StreamingServiceRules.requiresServerUrl(serviceId) }

    var serverUrl by rememberSaveable(serviceId) { mutableStateOf(session.serverUrl) }
    var username by rememberSaveable(serviceId) { mutableStateOf(session.username) }
    var password by rememberSaveable(serviceId) { mutableStateOf("") }
    var showDiscoverySheet by remember { mutableStateOf(false) }

    val canSubmit = username.isNotBlank() && password.isNotBlank() && (!requiresServerUrl || serverUrl.isNotBlank())
    val primaryActionText = when {
        isBusy && session.isConnected -> R.string.streaming_service_setup_disconnecting
        isBusy -> R.string.streaming_service_setup_connecting
        session.isConnected -> R.string.streaming_service_setup_reconnect
        else -> R.string.streaming_connect
    }
    val statusText = when {
        isBusy && session.isConnected -> stringResource(id = R.string.streaming_service_setup_disconnecting)
        isBusy -> stringResource(id = R.string.streaming_service_setup_connecting)
        session.isConnected -> stringResource(id = R.string.streaming_status_connected)
        else -> stringResource(id = R.string.streaming_status_not_connected)
    }
    val oneLinerStatus = if (session.isConnected && session.username.isNotBlank()) {
        stringResource(id = R.string.streaming_service_setup_connected_as, session.username)
    } else {
        statusText
    }

    CollapsibleHeaderScreen(
        title = oneLinerStatus,
        showBackButton = true,
        onBackClick = onBackClick,
        headerDisplayMode = 1
    ) { contentModifier ->
        LazyColumn(
            modifier = modifier
                .then(contentModifier)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {


            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val providerIconRes = when (serviceId) {
                        StreamingServiceId.SUBSONIC -> R.drawable.ic_subsonic
                        StreamingServiceId.JELLYFIN -> R.drawable.ic_jellyfin
                        else -> null
                    }
                    if (providerIconRes != null) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = providerIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text(
                        text = optionName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isBusy) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            M3FourColorCircularLoader(
                                modifier = Modifier.size(56.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val loadingText = if (session.isConnected) {
                                    stringResource(id = R.string.streaming_service_setup_disconnecting)
                                } else {
                                    stringResource(id = R.string.streaming_service_setup_connecting)
                                }
                                Text(
                                    text = loadingText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.streamingservicesetup_establishing_connection),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (requiresServerUrl) {
                                OutlinedTextField(
                                    value = serverUrl,
                                    onValueChange = { serverUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(text = stringResource(id = R.string.streaming_service_setup_server_url)) },
                                    placeholder = { Text(text = stringResource(id = R.string.streaming_service_setup_server_url_hint)) },
                                    trailingIcon = {
                                        IconButton(onClick = { showDiscoverySheet = true }) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("radar"),
                                                contentDescription = stringResource(R.string.streaming_service_setup_auto_detect)
                                            )
                                        }
                                    },
                                    supportingText = {
                                        Text(text = stringResource(id = R.string.streaming_service_setup_server_url_supporting))
                                    },
                                    singleLine = true,
                                    enabled = !isBusy
                                )
                            }

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(text = stringResource(id = R.string.streaming_service_setup_username)) },
                                placeholder = { Text(text = stringResource(id = R.string.streaming_service_setup_username_hint)) },
                                singleLine = true,
                                enabled = !isBusy
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(text = stringResource(id = R.string.streaming_service_setup_password)) },
                                placeholder = { Text(text = stringResource(id = R.string.streaming_service_setup_password_hint)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !isBusy
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(id = R.string.streaming_service_setup_remember_password),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(id = R.string.streaming_service_setup_remember_password_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TunerAnimatedSwitch(
                                    checked = rememberStreamingPasswords,
                                    onCheckedChange = { enabled -> appSettings.setRememberStreamingPasswords(enabled) },
                                    enabled = !isBusy
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = error.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                val isDomainUrl = remember(serverUrl) {
                                    val clean = serverUrl.trim().lowercase()
                                        .removePrefix("http://")
                                        .removePrefix("https://")
                                        .substringBefore(":")
                                        .substringBefore("/")
                                    clean.isNotEmpty() && !clean.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")) && clean != "localhost"
                                }
                                if (isDomainUrl) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.streaming_service_setup_lan_hairpin_tip),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.connectService(
                                    serviceId = serviceId,
                                    serverUrl = serverUrl,
                                    username = username,
                                    password = password
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy && canSubmit
                        ) {
                            Text(text = stringResource(id = primaryActionText))
                        }

                        if (session.isConnected) {
                            OutlinedButton(
                                onClick = { viewModel.disconnectService(serviceId) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text(text = stringResource(id = R.string.streaming_service_setup_disconnect))
                            }
                        }

                        if (!canSubmit && !isBusy) {
                            Text(
                                text = stringResource(id = R.string.streaming_service_setup_action_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showDiscoverySheet) {
        NearbyServerDiscoverySheet(
            serviceId = serviceId,
            onDismiss = { showDiscoverySheet = false },
            onServerSelected = { detectedUrl ->
                serverUrl = detectedUrl
                showDiscoverySheet = false
            }
        )
    }
}
