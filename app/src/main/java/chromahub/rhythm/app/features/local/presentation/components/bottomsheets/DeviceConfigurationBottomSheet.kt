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
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.autoeq_manage),
            subtitle = pluralStringResource(R.plurals.device_configuration_devices_configured, userDevices.size, userDevices.size),
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            
            Column {
                // Description text
                Text(
                    text = stringResource(R.string.autoeq_configure_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                // Current device detection hint
                val currentLocation = musicViewModel.audioDeviceManager.currentDevice.collectAsState().value
                if (currentLocation != null && currentLocation.id != "speaker") {
                    val matchedDevice = musicViewModel.findMatchingUserDevice(currentLocation.name)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (matchedDevice != null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (matchedDevice != null) RhythmIcons.Check else RhythmIcons.Info,
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
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = RhythmIcons.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.autoeq_no_devices),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.autoeq_add_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
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
            }
        }
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
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    // AutoEQ Profile Selector for Device
    if (showAutoEQSelector && deviceForAutoEQ != null) {
        DeviceAutoEQSelector(
            musicViewModel = musicViewModel,
            device = deviceForAutoEQ!!,
            onDismiss = {
                showAutoEQSelector = false
                deviceForAutoEQ = null
            },
            onProfileSelected = { profile ->
                // Save profile to device
                val updatedDevice = deviceForAutoEQ!!.copy(
                    autoEQProfileName = profile.name
                )
                musicViewModel.saveUserAudioDevice(updatedDevice)
                
                // Apply the profile immediately
                musicViewModel.applyAutoEQProfile(profile)
                
                // Show feedback
                Toast.makeText(
                    context,
                    context.getString(R.string.device_configuration_applied_profile, profile.name),
                    Toast.LENGTH_SHORT
                ).show()
                
                showAutoEQSelector = false
                deviceForAutoEQ = null
            }
        )
    }
    
    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                importText = ""
                importError = null
            },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(stringResource(R.string.autoeq_import_profile))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.autoeq_import_paste_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        isError = importError != null,
                        supportingText = if (importError != null) {
                            { Text(importError!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(MaterialSymbolIcon("file_upload"), null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.text_file), style = MaterialTheme.typography.labelLarge)
                        }
                        FilledTonalButton(
                            onClick = {
                                clipboardManager.primaryClip?.getItemAt(0)?.text?.let {
                                    importText = it.toString()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(MaterialSymbolIcon("content_paste"), null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.text_paste), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, ("https://autoeq.app").toUri())
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(RhythmIcons.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.deviceconfigurationbottomsheet_open_autoeqapp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedProfiles = AutoEQImportExport.autoDetectAndParse(importText, context.getString(R.string.device_configuration_imported_profile))
                        
                        if (parsedProfiles.isNotEmpty()) {
                            val profile = parsedProfiles.first()
                            musicViewModel.applyAutoEQProfile(profile)
                            showImportDialog = false
                            importText = ""
                            importError = null
                            Toast.makeText(context, context.getString(R.string.deviceconfiguration_profile_imported, profile.name), Toast.LENGTH_SHORT).show()
                        } else {
                            importError = context.getString(R.string.device_configuration_parse_error)
                        }
                    },
                    enabled = importText.isNotBlank()
                ) {
                    Icon(RhythmIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.button_import))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showImportDialog = false
                        importText = ""
                        importError = null
                    }
                ) {
                    Icon(RhythmIcons.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
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
        
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("file_upload"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(stringResource(R.string.deviceconfigurationbottomsheet_export_eq_profile))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (profileToExport != null) {
                        val exportText = AutoEQImportExport.generateShareableText(profileToExport)
                        
                        Text(
                            text = stringResource(R.string.bottomsheet_export_eq),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.deviceconfiguration_clip_label), exportText))
                                    Toast.makeText(context, R.string.deviceconfigurationbottomsheet_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(MaterialSymbolIcon("content_paste"), null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.deviceconfigurationbottomsheet_copy), style = MaterialTheme.typography.labelLarge)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.device_configuration_share_subject, profileToExport.name))
                                        putExtra(Intent.EXTRA_TEXT, exportText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.device_configuration_share_title)))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(RhythmIcons.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.crashactivity_share), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.bottomsheet_no_eq_export),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false }
                ) {
                    Icon(RhythmIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_done))
                }
            },
            dismissButton = null,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun DeviceCard(
    device: UserAudioDevice,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfigureAutoEQ: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 0.dp else 0.dp
        ),
        shape = RoundedCornerShape(24.dp),
//        border = if (isActive) {
//            androidx.compose.foundation.BorderStroke(
//                1.dp,
//                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
//            )
//        } else null
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
                        .clip(CircleShape)
                        .background(
                            if (isActive)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getDeviceIcon(device.type),
                        contentDescription = null,
                        tint = if (isActive)
                            MaterialTheme.colorScheme.primary
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
                            MaterialTheme.colorScheme.primaryContainer
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (device.autoEQProfileName != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            .size(32.dp)
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
            
            // Action Buttons - Centered Horizontally
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onConfigureAutoEQ,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("headset_mic", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.license_autoeq_name),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.bottomsheet_timer_edit),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.button_delete),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditDeviceDialog(
    existingDevice: UserAudioDevice?,
    onDismiss: () -> Unit,
    onSave: (UserAudioDevice) -> Unit
) {
    var deviceName by remember { mutableStateOf(existingDevice?.name ?: "") }
    var deviceBrand by remember { mutableStateOf(existingDevice?.brand ?: "") }
    var selectedType by remember { mutableStateOf(existingDevice?.type ?: UserAudioDevice.DeviceType.HEADPHONES) }
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
                    onValueChange = { deviceName = it },
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

@Composable
private fun DeviceAutoEQSelector(
    musicViewModel: MusicViewModel,
    device: UserAudioDevice,
    onDismiss: () -> Unit,
    onProfileSelected: (AutoEQProfile) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val profiles by musicViewModel.autoEQProfiles.collectAsState()
    var searchQuery by remember { mutableStateOf(device.brand.ifEmpty { device.name }) }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    
    // Extract unique brands and types
    val brands = remember(profiles) {
        profiles.map { it.brand }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    
    val types = remember(profiles) {
        profiles.map { it.type }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    
    val filteredProfiles = remember(profiles, searchQuery, selectedBrand, selectedType) {
        var result = profiles
        
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter { 
                it.name.lowercase().contains(query) ||
                it.brand.lowercase().contains(query) ||
                it.type.lowercase().contains(query)
            }
        }
        
        if (selectedBrand != null) {
            result = result.filter { it.brand == selectedBrand }
        }
        
        if (selectedType != null) {
            result = result.filter { it.type == selectedType }
        }
        
        result
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = MaterialSymbolIcon("headset_mic", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = stringResource(R.string.deviceconfigurationbottomsheet_select_autoeq_profile),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.device_configuration_for_device, device.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // Enhanced Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_find_a_profile_for)) },
                    leadingIcon = {
                        Icon(
                            imageVector = RhythmIcons.SearchFilled,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = RhythmIcons.Close,
                                    contentDescription = stringResource(R.string.ui_clear),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.device_configuration_profiles_available, filteredProfiles.size, filteredProfiles.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                
                // Profile List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        val isNoneSelected = device.autoEQProfileName.isNullOrBlank() || device.autoEQProfileName == "None"
                        EQProfileCard(
                            profile = AutoEQProfile(
                                name = context.getString(R.string.common_none),
                                brand = context.getString(R.string.device_configuration_disable_compensation),
                                type = "",
                                bands = List(10) { 0f }
                            ),
                            isSelected = isNoneSelected,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                onProfileSelected(AutoEQProfile("None", "", "", List(10) { 0f }))
                            }
                        )
                    }

                    if (filteredProfiles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("headset_mic", filled = true),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.deviceconfigurationbottomsheet_no_matching_profiles_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredProfiles, key = { it.name }) { profile ->
                            EQProfileCard(
                                profile = profile,
                                isSelected = device.autoEQProfileName == profile.name,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    onProfileSelected(profile)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(RhythmIcons.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun EQProfileCard(
    profile: AutoEQProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 0.dp else 0.dp
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("headset_mic", filled = true),
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                )
                
                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (profile.brand.isNotEmpty()) {
                            Text(
                                text = profile.brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (profile.brand.isNotEmpty() && profile.type.isNotEmpty()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (profile.type.isNotEmpty()) {
                            Text(
                                text = profile.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Selection Indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Check,
                        contentDescription = stringResource(R.string.streaming_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun getDeviceIcon(type: UserAudioDevice.DeviceType): MaterialSymbolIcon {
    return when (type) {
        UserAudioDevice.DeviceType.HEADPHONES -> RhythmIcons.Headphones
        UserAudioDevice.DeviceType.EARBUDS -> MaterialSymbolIcon("headset_mic")
        UserAudioDevice.DeviceType.IEM -> MaterialSymbolIcon("headset_mic")
        UserAudioDevice.DeviceType.SPEAKERS -> RhythmIcons.Speaker
        UserAudioDevice.DeviceType.BLUETOOTH_SPEAKER -> RhythmIcons.BluetoothFilled
        UserAudioDevice.DeviceType.CAR_AUDIO -> MaterialSymbolIcon("directions_car")
        UserAudioDevice.DeviceType.STUDIO_MONITORS -> MaterialSymbolIcon("speaker_group")
        UserAudioDevice.DeviceType.OTHER -> RhythmIcons.Headphones
    }
}
