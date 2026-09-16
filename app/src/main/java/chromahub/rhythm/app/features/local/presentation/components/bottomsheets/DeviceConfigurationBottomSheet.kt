/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.foundation.lazy.rememberLazyListState
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.shared.presentation.theme.ExpressiveMaterialShape
import chromahub.rhythm.app.shared.presentation.theme.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.dialogs.AutoEQSuggestionDialog
import chromahub.rhythm.app.shared.presentation.components.common.getDeviceIcon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import chromahub.rhythm.app.shared.presentation.components.common.rhythmMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import chromahub.rhythm.app.shared.data.model.UserAudioDevice
import chromahub.rhythm.app.util.AutoEQImportExport
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.core.net.toUri

@Composable
fun DeviceConfigurationBottomSheet(
    musicViewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // States
    val userDevicesJson by musicViewModel.appSettings.userAudioDevices.collectAsState()
    val activeDeviceId by musicViewModel.appSettings.activeAudioDeviceId.collectAsState()
    val autoEQProfiles by musicViewModel.autoEQProfiles.collectAsState()
    val currentAutoEQProfile by musicViewModel.appSettings.autoEQProfile.collectAsState()
    
    val userDevices = remember(userDevicesJson) {
        UserAudioDevice.fromJson(userDevicesJson)
    }
    
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<UserAudioDevice?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<UserAudioDevice?>(null) }
    var showAutoEQSelector by remember { mutableStateOf(false) }
    var deviceForAutoEQ by remember { mutableStateOf<UserAudioDevice?>(null) }
    
    // Import/Export states
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDetectionBottomSheet by remember { mutableStateOf(false) }
    var detectedDeviceForSheet by remember { mutableStateOf<UserAudioDevice?>(null) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            val content = AutoEQImportExport.readFromUri(context, it)
            if (content != null) {
                importText = content
            } else {
                Toast.makeText(context, R.string.autoeq_failed_read_file, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Load AutoEQ profiles if not loaded
    LaunchedEffect(Unit) {
        if (autoEQProfiles.isEmpty()) {
            musicViewModel.loadAutoEQProfiles()
        }
    }
    
    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.autoeq_manage),
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            
            Column {
                // Current device detection hint
                val currentLocation = musicViewModel.audioDeviceManager.currentDevice.collectAsState().value
                if (currentLocation != null && currentLocation.id != "speaker") {
                    val matchedDevice = musicViewModel.findMatchingUserDevice(currentLocation.name)
                    val detectedType = matchedDevice?.type ?: UserAudioDevice.inferDeviceType(currentLocation.name, currentLocation.id)
                    val detectedBrand = if (matchedDevice != null && matchedDevice.brand.isNotEmpty()) {
                        matchedDevice.brand
                    } else {
                        UserAudioDevice.inferDeviceBrand(currentLocation.name)
                    }
                    val effectiveDevice = matchedDevice?.copy(
                        brand = if (matchedDevice.brand.isNotEmpty()) matchedDevice.brand else detectedBrand
                    ) ?: UserAudioDevice(
                        id = currentLocation.id,
                        name = currentLocation.name,
                        type = detectedType,
                        brand = detectedBrand,
                        autoEQProfileName = null
                    )
                    
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            detectedDeviceForSheet = effectiveDevice
                            showDetectionBottomSheet = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (matchedDevice != null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            }
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = getDeviceIcon(effectiveDevice.type),
                                contentDescription = null,
                                tint = if (matchedDevice != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (matchedDevice != null) stringResource(R.string.device_configuration_device_recognized) else stringResource(R.string.device_configuration_new_device_detected),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (matchedDevice != null) {
                                        if (matchedDevice.autoEQProfileName != null) {
                                            stringResource(R.string.device_configuration_configured_with, currentLocation.name, matchedDevice.autoEQProfileName)
                                        } else {
                                            stringResource(R.string.device_configuration_configured_no_profile, currentLocation.name)
                                        }
                                    } else {
                                        stringResource(R.string.device_configuration_connected_not_configured, currentLocation.name)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                    
                Spacer(modifier = Modifier.height(12.dp))
                
                RhythmGroupedButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            showAddDeviceDialog = true
                        },
                        weight = 1f,
                        isFirst = true,
                        type = RhythmButtonType.Filled,
                        icon = RhythmIcons.Add,
                        text = stringResource(R.string.button_add)
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            showImportDialog = true
                        },
                        weight = 1f,
                        type = RhythmButtonType.Tonal,
                        icon = RhythmIcons.Download,
                        text = stringResource(R.string.button_import)
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            showExportDialog = true
                        },
                        weight = 1f,
                        isLast = true,
                        type = RhythmButtonType.Tonal,
                        icon = MaterialSymbolIcon("file_upload"),
                        text = stringResource(R.string.button_export)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Devices List
                if (userDevices.isEmpty()) {
                    // Empty state matching LibraryScreen
                    val cookieShape = rememberExpressiveShape(ExpressiveMaterialShape.COOKIE_12)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp)
                            ) {
                                Surface(
                                    shape = cookieShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.HeadphonesFilled,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = stringResource(R.string.autoeq_no_devices),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = stringResource(R.string.autoeq_add_prompt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    val deviceListState = rememberLazyListState()

                    AdaptiveSheetScrollContainer(
                        lazyListState = deviceListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) { endPadding ->
                        LazyColumn(
                            state = deviceListState,
                            contentPadding = PaddingValues(end = endPadding, top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(userDevices, key = { it.id }) { device ->
                                DeviceCard(
                                    device = device,
                                    isActive = device.autoEQProfileName == currentAutoEQProfile && currentAutoEQProfile.isNotEmpty(),
                                    onSelect = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        musicViewModel.setActiveAudioDevice(device)
                                    },
                                    onEdit = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        deviceToEdit = device
                                        showAddDeviceDialog = true
                                    },
                                    onDelete = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        showDeleteConfirmDialog = device
                                    },
                                    onConfigureAutoEQ = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        deviceForAutoEQ = device
                                        showAutoEQSelector = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Tips Card
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.autoeq_configure_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }

    // AutoEQ Suggestion BottomSheet for detected device
    if (showDetectionBottomSheet && detectedDeviceForSheet != null) {
        val equalizerEnabled by musicViewModel.appSettings.equalizerEnabled.collectAsState()
        AutoEQSuggestionDialog(
            deviceName = detectedDeviceForSheet!!.name,
            savedDevice = detectedDeviceForSheet!!,
            equalizerEnabled = equalizerEnabled,
            onApplyProfile = {
                val profile = autoEQProfiles.find { it.name == detectedDeviceForSheet!!.autoEQProfileName }
                if (profile != null) {
                    musicViewModel.applyAutoEQProfile(profile)
                    musicViewModel.setActiveAudioDevice(detectedDeviceForSheet!!)
                    Toast.makeText(context, "Applied ${profile.name} profile", Toast.LENGTH_SHORT).show()
                }
                showDetectionBottomSheet = false
            },
            onDismiss = {
                showDetectionBottomSheet = false
            },
            onDontAskAgain = {
                musicViewModel.dismissAutoEQSuggestion(detectedDeviceForSheet!!.id)
                showDetectionBottomSheet = false
            },
            onConfigureDevice = {
                showDetectionBottomSheet = false
                deviceForAutoEQ = detectedDeviceForSheet
                showAutoEQSelector = true
            }
        )
    }
    
    // Add/Edit Device Dialog
    if (showAddDeviceDialog) {
        AddEditDeviceDialog(
            existingDevice = deviceToEdit,
            onDismiss = {
                showAddDeviceDialog = false
                deviceToEdit = null
            },
            onSave = { device ->
                musicViewModel.saveUserAudioDevice(device)
                showAddDeviceDialog = false
                deviceToEdit = null
            }
        )
    }
    
    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { device ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(R.string.autoeq_delete_device_title)) },
            text = { Text(stringResource(R.string.deviceconfiguration_delete_confirm, device.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        musicViewModel.deleteUserAudioDevice(device.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    // AutoEQ Profile Selector for Device
    if (showAutoEQSelector && deviceForAutoEQ != null) {
        AutoEQPresetPickerBottomSheet(
            device = deviceForAutoEQ,
            onDismissRequest = {
                showAutoEQSelector = false
                deviceForAutoEQ = null
            },
            onProfileSelected = { profile ->
                val isNone = profile.name.isBlank() || profile.name.equals("None", ignoreCase = true)
                // Save profile to device
                val updatedDevice = deviceForAutoEQ!!.copy(
                    autoEQProfileName = if (isNone) null else profile.name
                )
                musicViewModel.saveUserAudioDevice(updatedDevice)
                
                // Apply or reset the profile immediately
                musicViewModel.applyAutoEQProfile(profile)
                
                // Show feedback
                val message = if (isNone) {
                    context.getString(R.string.device_configuration_disable_compensation)
                } else {
                    context.getString(R.string.device_configuration_applied_profile, profile.name)
                }
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
                
                showAutoEQSelector = false
                deviceForAutoEQ = null
            }
        )
    }
    
    // Import Dialog
    if (showImportDialog) {
        val importSheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        LaunchedEffect(Unit) {
            importSheetState.show()
        }
        val dismissImport = {
            scope.launch {
                importSheetState.hide()
                showImportDialog = false
                importText = ""
                importError = null
            }
        }

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { 
                showImportDialog = false
                importText = ""
                importError = null
            },
            sheetState = importSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.autoeq_import_profile),
                visible = true
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.autoeq_import_paste_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.deviceconfigurationbottomsheet_supported_formats_fixedbandeq_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                OutlinedTextField(
                    value = importText,
                    onValueChange = { 
                        importText = it
                        importError = null
                    },
                    label = { Text(stringResource(R.string.autoeq_eq_settings_label)) },
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_paste_eq_data_here)) },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    isError = importError != null,
                    supportingText = if (importError != null) {
                        { Text(importError!!, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    shape = RoundedCornerShape(16.dp)
                )
                
                RhythmGroupedButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = RhythmButtonSize.Medium
                ) {
                    RhythmButtonWeighted(
                        onClick = { filePickerLauncher.launch("*/*") },
                        weight = 1f,
                        isFirst = true,
                        isLast = false,
                        size = RhythmButtonSize.Medium,
                        type = RhythmButtonType.Tonal,
                        icon = MaterialSymbolIcon("file_upload"),
                        text = stringResource(R.string.text_file)
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            clipboardManager.primaryClip?.getItemAt(0)?.text?.let {
                                importText = it.toString()
                            }
                        },
                        weight = 1f,
                        isFirst = false,
                        isLast = true,
                        size = RhythmButtonSize.Medium,
                        type = RhythmButtonType.Tonal,
                        icon = MaterialSymbolIcon("content_paste"),
                        text = stringResource(R.string.text_paste)
                    )
                }
                
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, ("https://autoeq.app").toUri())
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(RhythmIcons.Link, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.deviceconfigurationbottomsheet_open_autoeqapp))
                }

                RhythmGroupedButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = { dismissImport() },
                        weight = 1f,
                        isFirst = true,
                        isLast = false,
                        size = RhythmButtonSize.Large,
                        type = RhythmButtonType.Tonal,
                        icon = RhythmIcons.Close,
                        text = stringResource(R.string.ui_cancel)
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            val parsedProfiles = AutoEQImportExport.autoDetectAndParse(importText, context.getString(R.string.device_configuration_imported_profile))
                            
                            if (parsedProfiles.isNotEmpty()) {
                                val profile = parsedProfiles.first()
                                musicViewModel.applyAutoEQProfile(profile)
                                scope.launch {
                                    importSheetState.hide()
                                    showImportDialog = false
                                    importText = ""
                                    importError = null
                                }
                                Toast.makeText(context, context.getString(R.string.deviceconfiguration_profile_imported, profile.name), Toast.LENGTH_SHORT).show()
                            } else {
                                importError = context.getString(R.string.device_configuration_parse_error)
                            }
                        },
                        enabled = importText.isNotBlank(),
                        weight = 1.3f,
                        isFirst = false,
                        isLast = true,
                        size = RhythmButtonSize.Large,
                        type = RhythmButtonType.Filled,
                        icon = RhythmIcons.Check,
                        text = stringResource(R.string.button_import)
                    )
                }
            }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        val currentAutoEQProfile = musicViewModel.appSettings.autoEQProfile.collectAsState().value
        val currentPreset = musicViewModel.appSettings.equalizerPreset.collectAsState().value
        val currentBandLevels = musicViewModel.appSettings.equalizerBandLevels.collectAsState().value
        val equalizerEnabled = musicViewModel.appSettings.equalizerEnabled.collectAsState().value
        
        // Try to find profile in database, or create from current settings
        val profileToExport = if (currentAutoEQProfile.isNotEmpty() && currentAutoEQProfile != "None") {
            // Try to find in database
            autoEQProfiles.find { it.name == currentAutoEQProfile }
                ?: run {
                    // AutoEQ profile exists but not in current database, create from current band levels
                    val bands = currentBandLevels.split(",")
                        .mapNotNull { it.toFloatOrNull() }
                        .take(10)
                    if (bands.size == 10) {
                        AutoEQProfile(
                            name = currentAutoEQProfile,
                            brand = "",
                            type = "",
                            bands = bands
                        )
                    } else null
                }
        } else if (currentPreset != "Custom" && currentPreset != "Flat") {
            // Custom preset active
            val bands = currentBandLevels.split(",")
                .mapNotNull { it.toFloatOrNull() }
                .take(10)
            if (bands.size == 10) {
                AutoEQProfile(
                    name = currentPreset,
                    brand = "",
                    type = context.getString(R.string.device_configuration_custom_preset),
                    bands = bands
                )
            } else null
        } else {
            // Custom/manual EQ settings
            val bands = currentBandLevels.split(",")
                .mapNotNull { it.toFloatOrNull() }
                .take(10)
            if (bands.size == 10 && equalizerEnabled) {
                AutoEQProfile(
                    name = context.getString(R.string.device_configuration_custom_eq),
                    brand = "",
                    type = context.getString(R.string.device_configuration_custom),
                    bands = bands
                )
            } else null
        }
        
        val exportSheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        LaunchedEffect(Unit) {
            exportSheetState.show()
        }
        val dismissExport = {
            scope.launch {
                exportSheetState.hide()
                showExportDialog = false
            }
        }

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showExportDialog = false },
            sheetState = exportSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.deviceconfigurationbottomsheet_export_eq_profile),
                visible = true
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (profileToExport != null) {
                    val exportText = AutoEQImportExport.generateShareableText(profileToExport)
                    
                    Text(
                        text = stringResource(R.string.bottomsheet_export_eq),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = context.getString(R.string.device_configuration_active_profile_label, profileToExport.name),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = exportText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    RhythmGroupedButton(
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Medium
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.deviceconfiguration_clip_label), exportText))
                                Toast.makeText(context, R.string.deviceconfigurationbottomsheet_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            },
                            weight = 1f,
                            isFirst = true,
                            isLast = false,
                            size = RhythmButtonSize.Medium,
                            type = RhythmButtonType.Tonal,
                            icon = MaterialSymbolIcon("content_paste"),
                            text = stringResource(R.string.deviceconfigurationbottomsheet_copy)
                        )
                        RhythmButtonWeighted(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.device_configuration_share_subject, profileToExport.name))
                                    putExtra(Intent.EXTRA_TEXT, exportText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.device_configuration_share_title)))
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = true,
                            size = RhythmButtonSize.Medium,
                            type = RhythmButtonType.Tonal,
                            icon = RhythmIcons.Share,
                            text = stringResource(R.string.crashactivity_share)
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(R.string.bottomsheet_no_eq_export),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                RhythmGroupedButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = { dismissExport() },
                        weight = 1f,
                        isFirst = true,
                        isLast = true,
                        size = RhythmButtonSize.Large,
                        type = RhythmButtonType.Filled,
                        icon = RhythmIcons.Check,
                        text = stringResource(R.string.ui_done)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeviceCard(
    device: UserAudioDevice,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfigureAutoEQ: () -> Unit
) {
    val deviceShape = rememberExpressiveShape(ExpressiveMaterialShape.COOKIE_4)

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device Info Section
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(deviceShape)
                        .background(
                            if (isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getDeviceIcon(device.type),
                        contentDescription = null,
                        tint = if (isActive)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Info with marquee support
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.rhythmMarquee()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = device.type.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!device.autoEQProfileName.isNullOrBlank()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = device.autoEQProfileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .rhythmMarquee()
                            )
                        }
                        if (device.monoAudioEnabled) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.settings_mono_audio),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Active Indicator
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Check,
                            contentDescription = stringResource(R.string.bottomsheet_active_device),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // Action Buttons
            RhythmGroupedButton(
                modifier = Modifier.fillMaxWidth(),
                size = RhythmButtonSize.Medium
            ) {
                RhythmButtonWeighted(
                    onClick = onConfigureAutoEQ,
                    weight = 1.1f,
                    isFirst = true,
                    isLast = false,
                    size = RhythmButtonSize.Medium,
                    type = RhythmButtonType.Tonal,
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    icon = MaterialSymbolIcon("headset_mic", filled = true),
                    text = stringResource(R.string.license_autoeq_name)
                )
                RhythmButtonWeighted(
                    onClick = onEdit,
                    weight = 0.95f,
                    isFirst = false,
                    isLast = false,
                    size = RhythmButtonSize.Medium,
                    type = RhythmButtonType.Tonal,
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    icon = RhythmIcons.Edit,
                    text = stringResource(R.string.bottomsheet_timer_edit)
                )
                RhythmButtonWeighted(
                    onClick = onDelete,
                    weight = 0.95f,
                    isFirst = false,
                    isLast = true,
                    size = RhythmButtonSize.Medium,
                    type = RhythmButtonType.Tonal,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = RhythmIcons.Delete,
                    text = stringResource(R.string.button_delete)
                )
            }
        }
    }
}

@Composable
internal fun AddEditDeviceDialog(
    existingDevice: UserAudioDevice?,
    onDismiss: () -> Unit,
    onSave: (UserAudioDevice) -> Unit
) {
    var deviceName by remember { mutableStateOf(existingDevice?.name ?: "") }
    var deviceBrand by remember {
        mutableStateOf(
            existingDevice?.brand ?: if (deviceName.isNotBlank()) UserAudioDevice.inferDeviceBrand(deviceName) else ""
        )
    }
    var selectedType by remember {
        mutableStateOf(
            existingDevice?.type ?: if (deviceName.isNotBlank()) UserAudioDevice.inferDeviceType(deviceName) else UserAudioDevice.DeviceType.HEADPHONES
        )
    }
    var monoAudioEnabled by remember { mutableStateOf(existingDevice?.monoAudioEnabled ?: false) }
    
    val isEditing = existingDevice != null
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isEditing) RhythmIcons.Edit else RhythmIcons.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(if (isEditing) stringResource(R.string.device_configuration_edit_device) else stringResource(R.string.device_configuration_add_device))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { 
                        deviceName = it
                        if (!isEditing) {
                            val inferredBrand = UserAudioDevice.inferDeviceBrand(it)
                            if (inferredBrand.isNotEmpty()) {
                                deviceBrand = inferredBrand
                            }
                            selectedType = UserAudioDevice.inferDeviceType(it)
                        }
                    },
                    label = { Text(stringResource(R.string.deviceconfigurationbottomsheet_device_name)) },
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_eg_sony_wh1000xm4)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = deviceBrand,
                    onValueChange = { deviceBrand = it },
                    label = { Text(stringResource(R.string.deviceconfigurationbottomsheet_brand_optional)) },
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_eg_sony)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Text(
                    text = stringResource(R.string.bottomsheet_device_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(UserAudioDevice.DeviceType.entries.toList()) { type ->
                        val isSelected = selectedType == type
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 12.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "chipCornerRadius"
                        )
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) RhythmIcons.Check else getDeviceIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            shape = RoundedCornerShape(cornerRadius),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.device_mono_audio_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.device_mono_audio_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TunerAnimatedSwitch(
                        checked = monoAudioEnabled,
                        onCheckedChange = { monoAudioEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (deviceName.isNotBlank()) {
                        val device = if (isEditing) {
                            existingDevice.copy(
                                name = deviceName,
                                brand = deviceBrand,
                                type = selectedType,
                                monoAudioEnabled = monoAudioEnabled
                            )
                        } else {
                            UserAudioDevice(
                                name = deviceName,
                                brand = deviceBrand,
                                type = selectedType,
                                monoAudioEnabled = monoAudioEnabled
                            )
                        }
                        onSave(device)
                    }
                },
                enabled = deviceName.isNotBlank()
            ) {
                Icon(
                    imageVector = RhythmIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditing) stringResource(R.string.device_configuration_save) else stringResource(R.string.device_configuration_add))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(
                    imageVector = RhythmIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ui_cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
