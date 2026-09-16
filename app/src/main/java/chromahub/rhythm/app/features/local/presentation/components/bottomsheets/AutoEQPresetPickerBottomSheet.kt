/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.util.AutoEQManager
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsSearchBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.data.model.UserAudioDevice
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import chromahub.rhythm.app.shared.presentation.theme.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.theme.ExpressiveMaterialShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEQPresetPickerBottomSheet(
    initialProfileName: String? = null,
    currentProfileName: String? = null,
    device: UserAudioDevice? = null,
    onDismissRequest: () -> Unit,
    onProfileSelected: (AutoEQProfile) -> Unit,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val effectiveCurrentProfileName = device?.autoEQProfileName ?: currentProfileName

    var profiles by remember { mutableStateOf<List<AutoEQProfile>>(emptyList()) }
    var allBrands by remember { mutableStateOf<List<String>>(emptyList()) }
    var allTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var initialDeviceFilterApplied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val manager = AutoEQManager(context.applicationContext)
            manager.loadProfiles()
            profiles = manager.getAllProfiles()
            allBrands = manager.getAllBrands()
            allTypes = manager.getAllTypes()
        } catch (e: Exception) {
            profiles = emptyList()
            allBrands = emptyList()
            allTypes = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(allBrands, profiles, device) {
        if (device != null && !initialDeviceFilterApplied && allBrands.isNotEmpty()) {
            val inferredBrand = if (device.brand.isNotBlank()) device.brand else UserAudioDevice.inferDeviceBrand(device.name, allBrands)
            val matchingBrand = allBrands.firstOrNull { it.equals(inferredBrand, ignoreCase = true) }
                ?: allBrands.firstOrNull { device.name.contains(it, ignoreCase = true) }
            if (matchingBrand != null) {
                selectedBrand = matchingBrand
                showFilters = true
                val remainder = if (device.name.startsWith(matchingBrand, ignoreCase = true)) {
                    device.name.substring(matchingBrand.length).trim()
                } else if (inferredBrand.isNotBlank() && device.name.startsWith(inferredBrand, ignoreCase = true)) {
                    device.name.removePrefix(inferredBrand).trim()
                } else {
                    val regex = Regex("""\b${Regex.escape(matchingBrand)}\b""", RegexOption.IGNORE_CASE)
                    device.name.replace(regex, "").trim()
                }
                searchQuery = remainder
            } else {
                searchQuery = if (inferredBrand.isNotBlank()) inferredBrand else device.name
            }
            initialDeviceFilterApplied = true
        }
    }

    val filtered = remember(profiles, searchQuery, selectedBrand, selectedType, effectiveCurrentProfileName) {
        var result = profiles

        if (searchQuery.isNotBlank()) {
            result = result.filter { p ->
                p.name.contains(searchQuery, ignoreCase = true) ||
                        p.brand.contains(searchQuery, ignoreCase = true)
            }
        }

        if (selectedBrand != null) {
            result = result.filter { it.brand.equals(selectedBrand, ignoreCase = true) }
        }

        if (selectedType != null) {
            result = result.filter { it.type.equals(selectedType, ignoreCase = true) }
        }

        result.sortedWith(compareByDescending { it.name == effectiveCurrentProfileName })
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        lazyListState = listState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        StandardBottomSheetHeader(
            title = if (device != null) {
                stringResource(R.string.deviceconfigurationbottomsheet_select_autoeq_profile)
            } else {
                stringResource(R.string.autoeqpresetpickerbottomsheet_choose_autoeq_preset)
            },
            subtitle = if (loading) "" else if (device != null) {
                stringResource(R.string.device_configuration_for_device, device.name)
            } else {
                "${filtered.size} presets"
            },
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    hint = context.getString(R.string.autoeqpresetpickerbottomsheet_search_presets)
                )

                FilledTonalIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showFilters = !showFilters
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (selectedBrand != null || selectedType != null)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selectedBrand != null || selectedType != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.FilterList,
                        contentDescription = stringResource(R.string.autoeqpresetpickerbottomsheet_toggle_filters),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = !showFilters && (selectedBrand != null || selectedType != null),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedBrand != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    selectedBrand = null
                                },
                                label = { Text(selectedBrand!!) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.Close,
                                        contentDescription = stringResource(R.string.ui_clear),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = null
                            )
                        }
                    }

                    if (selectedType != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    selectedType = null
                                },
                                label = { Text(selectedType!!) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.Close,
                                        contentDescription = stringResource(R.string.ui_clear),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = null
                            )
                        }
                    }

                    item {
                        TextButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                selectedBrand = null
                                selectedType = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ui_clear_all),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.cd_filters),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            AnimatedVisibility(
                                visible = selectedBrand != null || selectedType != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedBrand = null
                                        selectedType = null
                                    }
                                ) {
                                    Text(stringResource(R.string.ui_clear_all), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        if (allBrands.isNotEmpty()) {
                            FilterSection(
                                title = stringResource(R.string.autoeqpresetpickerbottomsheet_brand),
                                items = allBrands,
                                selectedItem = selectedBrand,
                                onItemSelected = { selectedBrand = if (selectedBrand == it) null else it },
                                onClear = { selectedBrand = null }
                            )
                        }

                        if (allTypes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilterSection(
                                title = stringResource(R.string.autoeqpresetpickerbottomsheet_type),
                                items = allTypes,
                                selectedItem = selectedType,
                                onItemSelected = { selectedType = if (selectedType == it) null else it },
                                onClear = { selectedType = null }
                            )
                        }
                    }
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    M3CircularLoader(modifier = Modifier.size(48.dp), strokeWidth = 4f)
                }
            } else {
                AdaptiveSheetScrollContainer(
                    lazyListState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { endPadding ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp + endPadding,
                            top = 8.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            val isCurrentlyActive = effectiveCurrentProfileName.isNullOrBlank() || effectiveCurrentProfileName == "None"
                            Surface(
                                onClick = {
                                    onProfileSelected(AutoEQProfile("None", "", "", List(10) { 0f }))
                                    scope.launch { sheetState.hide() }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isCurrentlyActive)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = groupedPresetItemShape(0, filtered.size + 1),
                                tonalElevation = 0.dp
                            ) {
                                ListItem(
                                    supportingContent = {
                                        Text(
                                            context.getString(R.string.autoeqpresetpickerbottomsheet_disable_desc),
                                            color = if (isCurrentlyActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        if (isCurrentlyActive) {
                                            Icon(
                                                imageVector = RhythmIcons.Check,
                                                contentDescription = stringResource(R.string.autoeqpresetpickerbottomsheet_currently_active),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        context.getString(R.string.autoeqpresetpickerbottomsheet_disable),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrentlyActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isCurrentlyActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            item {
                                val animatedScale by animateFloatAsState(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.6f,
                                        stiffness = 100f
                                    ),
                                    label = "autoEQEmptyScale"
                                )
                                val animatedAlpha by animateFloatAsState(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 500,
                                        delayMillis = 100
                                    ),
                                    label = "autoEQEmptyAlpha"
                                )
                                val cookieShape = rememberExpressiveShape(ExpressiveMaterialShape.COOKIE_12)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = animatedScale
                                                scaleY = animatedScale
                                                alpha = animatedAlpha
                                            },
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
                                                modifier = Modifier.size(68.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = RhythmIcons.Headphones,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = stringResource(R.string.autoeqpresetpickerbottomsheet_no_presets_found),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = if (searchQuery.isNotBlank() || selectedBrand != null || selectedType != null) {
                                                    "No profiles match your search criteria or active filters. Try adjusting them."
                                                } else {
                                                    "No AutoEQ presets available."
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                                            )

                                            if (searchQuery.isNotBlank() || selectedBrand != null || selectedType != null) {
                                                Spacer(modifier = Modifier.height(18.dp))
                                                FilledTonalButton(
                                                    onClick = {
                                                        searchQuery = ""
                                                        selectedBrand = null
                                                        selectedType = null
                                                    },
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = RhythmIcons.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(stringResource(R.string.ui_clear_all))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filtered,
                                key = { _, profile -> profile.name }
                            ) { index, profile ->
                                val isCurrentlyActive = profile.name == effectiveCurrentProfileName
                                Surface(
                                    onClick = {
                                        onProfileSelected(profile)
                                        scope.launch { sheetState.hide() }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isCurrentlyActive)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = groupedPresetItemShape(index + 1, filtered.size + 1),
                                    tonalElevation = 0.dp
                                ) {
                                    ListItem(
                                        supportingContent = {
                                            Text(
                                                profile.brand,
                                                color = if (isCurrentlyActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        trailingContent = {
                                            if (isCurrentlyActive) {
                                                Icon(
                                                    imageVector = RhythmIcons.Check,
                                                    contentDescription = stringResource(R.string.autoeqpresetpickerbottomsheet_currently_active),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            profile.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isCurrentlyActive) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isCurrentlyActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    title: String,
    items: List<String>,
    selectedItem: String?,
    onItemSelected: (String) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedVisibility(
                visible = selectedItem != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TextButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onClear()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_reset),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            items(items.size) { index ->
                val item = items[index]
                val isSelected = selectedItem == item
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 12.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "chipCornerRadius"
                )

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onItemSelected(item)
                    },
                    label = {
                        Text(
                            text = item,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = RhythmIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(cornerRadius),
                    border = null
                )
            }
        }
    }
}

private fun groupedPresetItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}
