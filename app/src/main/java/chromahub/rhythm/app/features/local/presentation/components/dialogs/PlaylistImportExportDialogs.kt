/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.dialogs

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chromahub.rhythm.app.R
import chromahub.rhythm.app.util.PlaylistImportExportUtils
import androidx.compose.animation.core.*
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.shared.presentation.components.common.FileOperationLoader
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Dialog asking user if they want to restart the app after import
 */
@Composable
fun AppRestartDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    onContinue: () -> Unit,
    message: String? = null
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = context.getString(R.string.restart_app),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message ?: context.getString(R.string.dialog_restart_warning_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onRestart()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.restart_now))
                }

                OutlinedButton(
                    onClick = {
                        onContinue()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.continue_without_restart))
                }
            }
        },
        dismissButton = {}
    )
}

/**
 * Dialog for selecting playlist export format and choosing export location
 */
@Composable
fun PlaylistExportDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onExport: (PlaylistImportExportUtils.PlaylistExportFormat) -> Unit,
    onExportToCustomLocation: (PlaylistImportExportUtils.PlaylistExportFormat, Uri) -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(PlaylistImportExportUtils.PlaylistExportFormat.JSON) }
    var showLocationOptions by remember { mutableStateOf(false) }
    
    val directoryPickerLauncher = rememberLauncherForActivityResult<Uri?, Uri?>(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { 
            onExportToCustomLocation(selectedFormat, it)
            onDismiss()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = MaterialSymbolIcon("file_upload", filled = true),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = context.getString(R.string.export_playlist),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = context.getString(R.string.dialog_export_playlist_to, playlistName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PlaylistImportExportUtils.PlaylistExportFormat.values().forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = null
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = format.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${format.extension} • ${format.mimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showLocationOptions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onExport(selectedFormat)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_carousel_default))
                    }
                    
                    Button(
                        onClick = {
                            directoryPickerLauncher.launch(null)
                        }
                    ) {
                        Icon(
                            imageVector = RhythmIcons.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_choose))
                    }
                }
            } else {
                Button(
                    onClick = { showLocationOptions = true }
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("file_upload", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.button_export))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { 
                if (showLocationOptions) {
                    showLocationOptions = false
                } else {
                    onDismiss()
                }
            }) {
                Icon(
                    imageVector = if (showLocationOptions) RhythmIcons.Back else RhythmIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showLocationOptions) context.getString(R.string.common_back) else context.getString(R.string.ui_cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Dialog for bulk export of all playlists
 */
@Composable
fun BulkPlaylistExportDialog(
    playlistCount: Int,
    onDismiss: () -> Unit,
    onExport: (PlaylistImportExportUtils.PlaylistExportFormat, Boolean) -> Unit,
    onExportToCustomLocation: (PlaylistImportExportUtils.PlaylistExportFormat, Boolean, Uri) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var selectedFormat by remember { mutableStateOf(PlaylistImportExportUtils.PlaylistExportFormat.JSON) }
    var includeDefaultPlaylists by remember { mutableStateOf(false) }
    var showLocationOptions by remember { mutableStateOf(false) }
    
    val directoryPickerLauncher = rememberLauncherForActivityResult<Uri?, Uri?>(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { 
            onExportToCustomLocation(selectedFormat, includeDefaultPlaylists, it)
            onDismiss()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = MaterialSymbolIcon("folder_zip", filled = true),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = context.getString(R.string.export_all_playlists),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = pluralStringResource(R.plurals.dialog_export_playlists_to, playlistCount, playlistCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                items(PlaylistImportExportUtils.PlaylistExportFormat.values()) { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = null
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = format.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${format.extension} • ${format.mimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeDefaultPlaylists,
                            onCheckedChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                includeDefaultPlaylists = it
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = context.getString(R.string.include_default_playlists),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showLocationOptions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onExport(selectedFormat, includeDefaultPlaylists)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_carousel_default))
                    }
                    
                    Button(
                        onClick = {
                            directoryPickerLauncher.launch(null)
                        }
                    ) {
                        Icon(
                            imageVector = RhythmIcons.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_choose))
                    }
                }
            } else {
                Button(
                    onClick = { showLocationOptions = true }
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("folder_zip", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_export_all))
                }
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

/**
 * Dialog for importing playlists with file picker
 */
@Composable
fun PlaylistImportDialog(
    onDismiss: () -> Unit,
    onImport: (Uri, (Result<String>) -> Unit, (() -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult<Array<String>, Uri?>(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            // Pass a dummy onResult and onRestartRequired for now
            onImport(selectedUri, { /* no-op */ }, { /* no-op */ })
        }
        onDismiss()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = RhythmIcons.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = context.getString(R.string.import_playlist),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = context.getString(R.string.select_playlist_file),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.supported_formats),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        PlaylistImportExportUtils.PlaylistExportFormat.values().forEach { format ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getFormatIcon(format),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${format.displayName} (${format.extension})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    filePickerLauncher.launch(arrayOf(
                        "application/json",
                        "audio/x-mpegurl",
                        "application/x-mpegURL",
                        "audio/x-scpls",
                        "*/*" // Allow all files as fallback
                    ))
                }
            ) {
                Icon(
                    imageVector = RhythmIcons.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_browse_files))
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

/**
 * Enhanced progress dialog for import/export operations using Material 3 loader
 */
@Composable
fun PlaylistOperationProgressDialog(
    operation: String, // "Importing" or "Exporting"
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { /* Prevent dismissal during operation */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Enhanced File Operation Loader
                FileOperationLoader(
                    modifier = Modifier.size(60.dp),
                    isExpressive = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = operation,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = context.getString(R.string.may_take_moments),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Animated dots indicator
                val infiniteTransition = rememberInfiniteTransition(label = "dots")
                val dotCount by infiniteTransition.animateValue(
                    initialValue = 0,
                    targetValue = 3,
                    typeConverter = Int.VectorConverter,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "dots"
                )
                
                Text(
                    text = "•".repeat(dotCount + 1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 4.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Result dialog showing success/error messages
 */
@Composable
fun PlaylistOperationResultDialog(
    title: String,
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isError) MaterialSymbolIcon("error", filled = true) else RhythmIcons.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Icon(
                    imageVector = if (isError) RhythmIcons.Close else RhythmIcons.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ui_ok))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun getFormatIcon(format: PlaylistImportExportUtils.PlaylistExportFormat): MaterialSymbolIcon {
    return when (format) {
        PlaylistImportExportUtils.PlaylistExportFormat.JSON -> RhythmIcons.Code
        PlaylistImportExportUtils.PlaylistExportFormat.M3U,
        PlaylistImportExportUtils.PlaylistExportFormat.M3U8 -> RhythmIcons.Playlist
        PlaylistImportExportUtils.PlaylistExportFormat.PLS -> RhythmIcons.Queue
        PlaylistImportExportUtils.PlaylistExportFormat.XSPF,
        PlaylistImportExportUtils.PlaylistExportFormat.WPL -> RhythmIcons.List
    }
}
