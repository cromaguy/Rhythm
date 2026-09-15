/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package chromahub.rhythm.app.shared.presentation.components.dialogs

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import androidx.compose.foundation.lazy.rememberLazyListState

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import chromahub.rhythm.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

@Composable
fun AutoEQProfileSelector(
    musicViewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onProfileSelected: (AutoEQProfile) -> Unit
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // Animation state
    var showContent by remember { mutableStateOf(false) }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "contentAlpha"
    )
    
    val contentTranslation by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "contentTranslation"
    )
    
    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }
    
    // States
    val profiles by musicViewModel.autoEQProfiles.collectAsState()
    val isLoading by musicViewModel.autoEQLoading.collectAsState()
    val currentAutoEQProfile by musicViewModel.appSettings.autoEQProfile.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    
    // Load profiles on first composition
    LaunchedEffect(Unit) {
        if (profiles.isEmpty()) {
            musicViewModel.loadAutoEQProfiles()
        }
    }
    
    // Filtered profiles based on search and filters
    var filteredProfiles by remember { mutableStateOf(emptyList<AutoEQProfile>()) }
    
    LaunchedEffect(profiles, searchQuery, selectedBrand, selectedType, currentAutoEQProfile) {
        filteredProfiles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            var filtered = profiles
            
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase()
                filtered = filtered.filter { 
                    it.name.lowercase().contains(query) ||
                    it.brand.lowercase().contains(query)
                }
            }
            
            if (selectedBrand != null) {
                filtered = filtered.filter { it.brand == selectedBrand }
            }
            
            if (selectedType != null) {
                filtered = filtered.filter { it.type == selectedType }
            }
            
            // Sort active profile to the top
            filtered.sortedWith(compareByDescending { it.name == currentAutoEQProfile })
        }
    }
    
    var brands by remember { mutableStateOf(emptyList<String>()) }
    var types by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(profiles) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            brands = profiles.map { it.brand }.distinct().sorted()
            types = profiles.map { it.type }.distinct().sorted()
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .graphicsLayer(alpha = contentAlpha)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.equalizerscreen_autoeq_profiles),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            text = pluralStringResource(R.plurals.autoeq_profiles_available, filteredProfiles.size, filteredProfiles.size),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                // Enhanced Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.autoeqprofileselector_find_eq_profiles_or)) },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Brand filters
                Text(
                    text = stringResource(R.string.autoeqpresetpickerbottomsheet_brand),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp)
                )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            val isSelected = selectedBrand == null
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isSelected) 24.dp else 12.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipCornerRadius"
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedBrand = null },
                                label = { Text(stringResource(R.string.autoeqprofileselector_all)) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
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
                        items(brands) { brand ->
                            val isSelected = selectedBrand == brand
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isSelected) 24.dp else 12.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipCornerRadius"
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedBrand = brand },
                                label = { Text(brand) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Type filters
                    Text(
                        text = stringResource(R.string.autoeqpresetpickerbottomsheet_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            val isSelected = selectedType == null
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isSelected) 24.dp else 12.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipCornerRadius"
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedType = null },
                                label = { Text(stringResource(R.string.autoeqprofileselector_all)) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
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
                        items(types) { type ->
                            val isSelected = selectedType == type
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isSelected) 24.dp else 12.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipCornerRadius"
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedType = type },
                                label = { Text(type) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
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

                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Loading indicator
                AnimatedVisibility(visible = isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                // Profile list
                AnimatedVisibility(
                    visible = !isLoading && showContent,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val autoEqListState = rememberLazyListState()

                    AdaptiveSheetScrollContainer(
                        lazyListState = autoEqListState,
                        modifier = Modifier.fillMaxWidth()
                    ) { endPadding ->
                        LazyColumn(
                            state = autoEqListState,
                            contentPadding = PaddingValues(start = 5.dp, end = 5.dp + endPadding, top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.graphicsLayer {
                                alpha = contentAlpha
                            }
                        ) {
                            items(filteredProfiles, key = { it.name }) { profile ->
                                val isCurrentlyActive = profile.name == currentAutoEQProfile
                                ProfileCard(
                                    profile = profile,
                                    isActive = isCurrentlyActive,
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onProfileSelected(profile)
                                    },
                                    onDisable = if (isCurrentlyActive) {
                                        {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            onProfileSelected(AutoEQProfile(
                                                name = "",
                                                brand = "",
                                                type = "",
                                                bands = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                                            ))
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun ProfileCard(
    profile: AutoEQProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    onDisable: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 2.dp else 0.dp
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
                // Icon
                Surface(
                    shape = CircleShape,
                    color = if (isActive)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = RhythmIcons.Equalizer,
                            contentDescription = null,
                            tint = if (isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
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
                                color = if (isActive)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
                                color = if (isActive)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Selection indicator
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
                        contentDescription = stringResource(R.string.streaming_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
