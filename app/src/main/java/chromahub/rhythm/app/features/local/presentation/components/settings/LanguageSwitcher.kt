/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.components.settings

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.util.Locale
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource

data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String
)

object LanguageHelper {
    val supportedLanguages = listOf(
        LanguageOption("en", "English", "English"),
        LanguageOption("ar", "Arabic", "العربية"),
        LanguageOption("bn", "Bengali", "বাংলা"),
        LanguageOption("de", "German", "Deutsch"),
        LanguageOption("es", "Spanish", "Español"),
        LanguageOption("et", "Estonian", "Eesti"),
        LanguageOption("fr", "French", "Français"),
        LanguageOption("fr-CA", "French (Canada)", "Français (Canada)"),
        LanguageOption("hi", "Hindi", "हिन्दी"),
        LanguageOption("id", "Indonesian", "Bahasa Indonesia"),
        LanguageOption("it", "Italian", "Italiano"),
        LanguageOption("ja", "Japanese", "日本語"),
        LanguageOption("ko", "Korean", "한국어"),
        LanguageOption("nl", "Dutch", "Nederlands"),
        LanguageOption("pl", "Polish", "Polski"),
        LanguageOption("pt", "Portuguese", "Português"),
        LanguageOption("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
        LanguageOption("ru", "Russian", "Русский"),
        LanguageOption("sv", "Swedish", "Svenska"),
        LanguageOption("ta", "Tamil", "தமிழ்"),
        LanguageOption("th", "Thai", "ไทย"),
        LanguageOption("tr", "Turkish", "Türkçe"),
        LanguageOption("uk", "Ukrainian", "Українська"),
        LanguageOption("uz", "Uzbek", "Oʻzbekcha"),
        LanguageOption("vi", "Vietnamese", "Tiếng Việt"),
        LanguageOption("zh", "Chinese (Simplified)", "简体中文"),
        LanguageOption("zh-TW", "Chinese (Traditional)", "繁體中文")
    )
    
    fun getCurrentLanguage(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
            localeManager?.applicationLocales?.get(0) ?: Locale.getDefault()
        } else {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) {
                Locale.getDefault()
            } else {
                locales.get(0) ?: Locale.getDefault()
            }
        }
        
        val languageTag = locale.toLanguageTag()
        // Try to match the exact language tag (e.g. fr-CA, pt-BR, zh-TW)
        val exactMatch = supportedLanguages.firstOrNull { it.code.equals(languageTag, ignoreCase = true) }
        if (exactMatch != null) return exactMatch.code
        
        // Try to match by language code (e.g. fr, pt, zh)
        val languageCode = locale.language
        val codeMatch = supportedLanguages.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }
        if (codeMatch != null) return codeMatch.code
        
        return "en" // default fallback
    }
    
    fun setLanguage(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
            localeManager?.applicationLocales = LocaleList.forLanguageTags(languageCode)
        } else {
            val locale = Locale.forLanguageTag(languageCode)
            val localeList = LocaleListCompat.create(locale)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSwitcherBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var currentLanguage by remember { mutableStateOf(LanguageHelper.getCurrentLanguage(context)) }
    val listState = rememberLazyListState()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.languageswitcher_select_language),
            visible = true
        )

        AdaptiveSheetScrollContainer(
            lazyListState = listState,
            blendColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(LanguageHelper.supportedLanguages, key = { _, it -> "lang_${it.code}" }) { index, language ->
                    val isSelected = currentLanguage == language.code
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            currentLanguage = language.code
                            LanguageHelper.setLanguage(context, language.code)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, LanguageHelper.supportedLanguages.size),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = language.nativeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = language.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = stringResource(R.string.streaming_selected),
                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSwitcherDialog(
    onDismiss: () -> Unit
) {
    LanguageSwitcherBottomSheet(onDismiss = onDismiss)
}
