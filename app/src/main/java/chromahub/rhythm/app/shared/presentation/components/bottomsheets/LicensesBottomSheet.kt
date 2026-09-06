/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesBottomSheet(
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val licenseItems = listOf(
        licenseItem(
            name = "Better Lyrics",
            description = context.getString(R.string.licenses_desc_better_lyrics),
            license = "GPL License",
            url = "https://github.com/better-lyrics/api",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Gramophone",
            description = context.getString(R.string.licenses_desc_gramophone),
            license = "GPL License",
            url = "https://github.com/FoedusProgramme/Gramophone",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "PixelPlayer",
            description = context.getString(R.string.licenses_desc_pixelplayer),
            license = "GPL License",
            url = "https://github.com/theovilardo/PixelPlayer",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "VIVI Music",
            description = context.getString(R.string.licenses_desc_vivi_music),
            license = "GPL License",
            url = "https://github.com/vivizzz007/vivi-music",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Zenith",
            description = context.getString(R.string.licenses_desc_zenith),
            license = "GPL License",
            url = "https://github.com/1372Slash/Zenith",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AutoEQ",
            description = context.getString(R.string.licenses_desc_autoeq),
            license = "MIT License",
            url = "https://github.com/jaakkopasanen/AutoEq",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Jetpack Compose",
            description = context.getString(R.string.licenses_desc_jetpack_compose),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/compose",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Material 3 Components",
            description = context.getString(R.string.licenses_desc_material3_components),
            license = "Apache License",
            url = "https://m3.material.io/",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Media3 ExoPlayer",
            description = context.getString(R.string.licenses_desc_media3_exoplayer),
            license = "Apache License",
            url = "https://github.com/androidx/media",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Kotlin Coroutines",
            description = context.getString(R.string.licenses_desc_kotlin_coroutines),
            license = "Apache License",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Coil",
            description = context.getString(R.string.licenses_desc_coil),
            license = "Apache License",
            url = "https://coil-kt.github.io/coil/",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Retrofit",
            description = context.getString(R.string.licenses_desc_retrofit),
            license = "Apache License",
            url = "https://square.github.io/retrofit/",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "OkHttp",
            description = context.getString(R.string.licenses_desc_okhttp),
            license = "Apache License",
            url = "https://square.github.io/okhttp/",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Gson",
            description = context.getString(R.string.licenses_desc_gson),
            license = "Apache License",
            url = "https://github.com/google/gson",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AndroidX Navigation",
            description = context.getString(R.string.licenses_desc_androidx_navigation),
            license = "Apache License",
            url = "https://developer.android.com/guide/navigation",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Accompanist Permissions",
            description = context.getString(R.string.licenses_desc_accompanist_permissions),
            license = "Apache License",
            url = "https://google.github.io/accompanist/permissions/",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AndroidX Palette",
            description = context.getString(R.string.licenses_desc_androidx_palette),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/palette",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "JAudioTagger",
            description = context.getString(R.string.licenses_desc_jaudiotagger),
            license = "LGPL License",
            url = "https://github.com/Borewit/jaudiotagger",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "TagLib",
            description = context.getString(R.string.licenses_desc_taglib),
            license = "Apache License",
            url = "https://github.com/kyant0/taglib",
            icon = RhythmIcons.Connectivity.OpenInNew,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AndroidX Fragment",
            description = context.getString(R.string.licenses_desc_androidx_fragment),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/fragment",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AndroidX MediaRouter",
            description = context.getString(R.string.licenses_desc_androidx_mediarouter),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/mediarouter",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AndroidX Window",
            description = context.getString(R.string.licenses_desc_androidx_window),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/window",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Glance AppWidget",
            description = context.getString(R.string.licenses_desc_glance_appwidget),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/glance",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "WorkManager",
            description = context.getString(R.string.licenses_desc_workmanager),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/work",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Material Symbols",
            description = context.getString(R.string.licenses_desc_material_symbols),
            license = "SIL Open Font License",
            url = "https://fonts.google.com/icons",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "LeakCanary",
            description = context.getString(R.string.licenses_desc_leakcanary),
            license = "Apache License",
            url = "https://square.github.io/leakcanary/",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Desugar JDK Libs",
            description = context.getString(R.string.licenses_desc_desugar_jdk_libs),
            license = "Apache License",
            url = "https://github.com/google/desugar_jdk_libs",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Media3 FFmpeg Decoder",
            description = context.getString(R.string.licenses_desc_media3_ffmpeg_decoder),
            license = "Apache License",
            url = "https://github.com/jellyfin/media3",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Room",
            description = context.getString(R.string.licenses_desc_room),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "AndroidX Paging",
            description = context.getString(R.string.licenses_desc_androidx_paging),
            license = "Apache License",
            url = "https://developer.android.com/jetpack/androidx/releases/paging",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        ),
        licenseItem(
            name = "Geom Font",
            description = context.getString(R.string.licenses_desc_geom_font),
            license = "SIL Open Font License",
            url = "https://fonts.google.com/specimen/Geom",
            icon = RhythmIcons.Actions.Info,
            context = context,
            haptic = haptic
        )
    )

    val licenseInfoItems = listOf(
        Material3SettingsItem(
            icon = RhythmIcons.Actions.Info,
            title = {
                Text(
                    text = context.getString(R.string.licenses_apache),
                    fontWeight = FontWeight.Bold
                )
            },
            description = {
                Text(context.getString(R.string.licenses_attribution))
            },
            enabled = false
        )
    )

    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        scrollState = scrollState,
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.licenses_title),
                subtitle = context.getString(R.string.licenses_desc),
                visible = true
            )

            AdaptiveSheetScrollContainer(
                scrollState = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp)
                ) {
                    Material3SettingsGroup(
                        title = stringResource(R.string.settings_about_open_source_libs),
                        items = licenseItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Material3SettingsGroup(
                        title = stringResource(R.string.licensesbottomsheet_license_notes),
                        items = licenseInfoItems,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

private fun licenseItem(
    name: String,
    description: String,
    license: String,
    url: String,
    icon: Any,
    context: android.content.Context,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
): Material3SettingsItem {
    return Material3SettingsItem(
        icon = icon,
        title = {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold
            )
        },
        description = {
            Text("$description • $license")
        },
        trailingContent = {
            Icon(
                imageVector = RhythmIcons.Forward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            val intent = Intent(Intent.ACTION_VIEW, (url).toUri()).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    )
}
