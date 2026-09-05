/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.dialogs.FdroidUpdateWarningDialog

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
 
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
 
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AlbumViewType
import chromahub.rhythm.app.shared.data.model.ArtistViewType
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.MediaScanMode
import chromahub.rhythm.app.shared.presentation.components.common.DataProcessingLoader
import chromahub.rhythm.app.shared.presentation.components.common.InitializationLoader
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.M3LinearLoader
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.SettingsBadgePalette
import chromahub.rhythm.app.shared.presentation.components.SettingsPalettes
import chromahub.rhythm.app.features.local.presentation.components.settings.LanguageSwitcherDialog
import chromahub.rhythm.app.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AutoEQPresetPickerBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistArtworkSourceBottomSheet
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import chromahub.rhythm.app.shared.data.model.ArtistArtworkSource
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.OnboardingStep
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.PermissionScreenState
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.rememberAppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppVersion
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.features.streaming.presentation.components.bottomsheets.NearbyServerDiscoverySheet
import chromahub.rhythm.app.features.streaming.presentation.components.bottomsheets.NearbyServerScanner
import chromahub.rhythm.app.shared.presentation.components.common.RhythmWavyProgressLoader
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.ui.theme.ColorSchemeOption
import chromahub.rhythm.app.ui.theme.getPresetColorSchemeOptions
import chromahub.rhythm.app.shared.presentation.screens.settings.ColorSchemePaletteRow
import chromahub.rhythm.app.shared.presentation.screens.settings.ActionPickerSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.ColorSource
import chromahub.rhythm.app.shared.presentation.screens.settings.ColorSourceDialog
import chromahub.rhythm.app.shared.presentation.screens.settings.FontOption
import chromahub.rhythm.app.shared.presentation.screens.settings.FontSelectionDialog
import chromahub.rhythm.app.shared.presentation.screens.settings.FontSource
import chromahub.rhythm.app.shared.presentation.screens.settings.MiniPlayerArtworkSizeSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.WidgetCornerRadiusSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.WidgetThemeSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.MiniPlayerCornerRadiusSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.PickerOption
import chromahub.rhythm.app.shared.presentation.screens.settings.PlayerTextAlignmentBottomSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.ProgressStyleBottomSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.ThumbStyleBottomSheet
import chromahub.rhythm.app.shared.presentation.screens.settings.cookieActionIcon
import chromahub.rhythm.app.shared.presentation.screens.settings.cookieActionLabel
import chromahub.rhythm.app.shared.presentation.screens.settings.statsGemIcon
import chromahub.rhythm.app.shared.presentation.screens.settings.statsGemLabel
import chromahub.rhythm.app.shared.presentation.screens.settings.statsRangeIcon
import chromahub.rhythm.app.shared.presentation.screens.settings.statsRangeLabel
import chromahub.rhythm.app.shared.presentation.screens.settings.updateAllWidgets
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LyricallySourcesBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ShapePresetsBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.getLocalizedShapePresetName
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistDelimitersBottomSheet
import chromahub.rhythm.app.util.ArtistSeparator
import chromahub.rhythm.app.shared.presentation.screens.settings.CanvasNetworkModeDialog
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import java.util.Locale
import kotlin.math.absoluteValue
import android.app.Activity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import androidx.compose.ui.draw.shadow
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun OnboardingScreen(
    currentStep: OnboardingStep,
    onNextStep: () -> Unit,
    onPrevStep: () -> Unit,
    onContinueFullTour: () -> Unit,
    onSkipFullTour: () -> Unit,
    onRequestAgain: () -> Unit,
    permissionScreenState: PermissionScreenState,
    isParentLoading: Boolean,
    themeViewModel: ThemeViewModel,
    appSettings: AppSettings,
    musicViewModel: MusicViewModel,
    updaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel(),
    streamingViewModel: StreamingMusicViewModel = viewModel(),
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)

    // Bottom sheet states
    var showLibraryTabOrderBottomSheet by remember { mutableStateOf(false) }
    var showLyricallySourcesBottomSheet by remember { mutableStateOf(false) }
    var showCanvasNetworkModeDialog by remember { mutableStateOf(false) }
    var showAutoEQSelector by remember { mutableStateOf(false) }
    var showArtistArtworkSourceBottomSheet by remember { mutableStateOf(false) }
    var showDelimiterBottomSheet by remember { mutableStateOf(false) }

    // Responsive sizing
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium || windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val contentMaxWidth = if (isTablet) 1080.dp else androidx.compose.ui.unit.Dp.Infinity
    val horizontalPadding = if (isTablet) 40.dp else 20.dp
    val cardPadding = if (isTablet) 32.dp else 20.dp

    val appMode by appSettings.appMode.collectAsState()
    val sessions by streamingViewModel.serviceSessions.collectAsState()
    val streamingService by appSettings.streamingService.collectAsState()
    val isStreamingServiceConnected = remember(sessions, streamingService) {
        sessions[streamingService]?.isConnected == true
    }
    
    val visibleSteps = remember(appMode) {
        val list = mutableListOf<OnboardingStep>()
        list.add(OnboardingStep.APP_MODE_CHOICE)
        if (appMode == "STREAMING") {
            list.add(OnboardingStep.STREAMING_SERVICE_CHOICE)
            list.add(OnboardingStep.STREAMING_SETUP)
        } else {
            list.add(OnboardingStep.PERMISSIONS)
            list.add(OnboardingStep.MEDIA_SCAN)
        }
        list.add(OnboardingStep.FULL_TOUR_PROMPT)
        list.add(OnboardingStep.RHYTHM_GUARD)
        list.add(OnboardingStep.AUDIO_PLAYBACK)
        list.add(OnboardingStep.THEMING)
        list.add(OnboardingStep.PLAYER_THEME_CHOICE)
        list.add(OnboardingStep.GESTURES)
        if (appMode != "STREAMING") {
            list.add(OnboardingStep.LIBRARY_SETUP)
        }
        list.add(OnboardingStep.WIDGETS)
        list.add(OnboardingStep.INTEGRATIONS)
        list.add(OnboardingStep.UPDATER)
        if (appMode != "STREAMING") {
            list.add(OnboardingStep.BACKUP_RESTORE)
        }
        list.add(OnboardingStep.RHYTHM_STATS)
        list.add(OnboardingStep.SETUP_FINISHED)
        list.add(OnboardingStep.COMPLETE)
        list
    }

    val stepIndex = remember(currentStep, visibleSteps) {
        val index = visibleSteps.indexOf(currentStep)
        if (index >= 0) index else {
            when (currentStep) {
                OnboardingStep.NOTIFICATIONS -> visibleSteps.indexOf(OnboardingStep.UPDATER)
                else -> 0
            }.coerceAtLeast(0)
        }
    }

    val totalSteps = remember(visibleSteps) { visibleSteps.size }

    // Create pager state
    val pagerState = rememberPagerState(
        initialPage = stepIndex,
        pageCount = { totalSteps }
    )

    // Sync pager with step changes
    LaunchedEffect(stepIndex) {
        if (currentStep == OnboardingStep.WELCOME) return@LaunchedEffect
        if (pagerState.currentPage != stepIndex) {
            val pageJump = (pagerState.currentPage - stepIndex).absoluteValue
            if (pageJump > 1) {
                pagerState.scrollToPage(stepIndex)
            } else {
                pagerState.animateScrollToPage(stepIndex)
            }
        }
    }

    // Sync step with pager changes.
    LaunchedEffect(pagerState.currentPage) {
        if (currentStep == OnboardingStep.WELCOME) return@LaunchedEffect
        val newStep = visibleSteps.getOrNull(pagerState.currentPage) ?: OnboardingStep.COMPLETE
        if (newStep != currentStep && pagerState.currentPage < stepIndex) {
            onPrevStep()
        } else if (newStep != currentStep && pagerState.currentPage > stepIndex) {
            onNextStep()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RotatingBackgroundCookies(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))

        if (currentStep == OnboardingStep.WELCOME) {
            EnhancedWelcomeContent(
                onNextStep = onNextStep,
                themeViewModel = themeViewModel,
                isTablet = isTablet,
                contentMaxWidth = contentMaxWidth
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .let {
                            if (isTablet) {
                                it
                                    .widthIn(max = contentMaxWidth)
                                    .fillMaxWidth(0.9f)
                                    .heightIn(max = 750.dp)
                                    .fillMaxHeight(0.9f)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceHigh)
                                    .border(
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(32.dp)
                                    )
                            } else {
                                it
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceHigh)
                            }
                        }
                ) {
                    // Single onboarding card container for all pager content
                    OnboardingCard(
                        isTablet = isTablet,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = when {
                        currentStep != OnboardingStep.PERMISSIONS && currentStep != OnboardingStep.GESTURES && currentStep != OnboardingStep.STREAMING_SETUP -> true
                        permissionScreenState == PermissionScreenState.PermissionsGranted -> true
                        else -> true // Allow scrolling to let user review info before granting
                    },
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> page } // Add key to preserve page state
                ) { page ->
                    val step = visibleSteps.getOrNull(page) ?: OnboardingStep.COMPLETE
                    val pageOffset by remember(page) {
                        derivedStateOf { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction }
                    }
                    // Container for step-specific content - positioned at top within pager page
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 30.dp, start = horizontalPadding, end = horizontalPadding, bottom = 0.dp)
                            .graphicsLayer {
                                val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                                alpha = 1f - absOffset
                                scaleX = 0.96f + (1f - absOffset) * 0.04f
                                scaleY = 0.96f + (1f - absOffset) * 0.04f
                            },
                        contentAlignment = Alignment.TopCenter
                    )    {
                        // Use key to preserve composable state across recompositions
                        androidx.compose.runtime.key(step) {
                            when (step) {
                                OnboardingStep.WELCOME -> {
                                    // Standalone welcome step is rendered outside the pager
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                                OnboardingStep.APP_MODE_CHOICE -> {
                                EnhancedAppModeChoiceContent(
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.STREAMING_SERVICE_CHOICE -> {
                                EnhancedStreamingServiceChoiceContent(
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.STREAMING_SETUP -> {
                                EnhancedStreamingSetupContent(
                                    appSettings = appSettings,
                                    streamingViewModel = streamingViewModel,
                                    onSkip = onNextStep,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            enabled = isStreamingServiceConnected,
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.PERMISSIONS -> {
                                EnhancedPermissionContent(
                                    permissionScreenState = permissionScreenState,
                                    onGrantAccess = {
                                        onNextStep() // Trigger permission request
                                    },
                                    onOpenSettings = {
                                        val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                                        context.startActivity(intent)
                                        onRequestAgain() // Set loading state
                                    },
                                    isButtonLoading = isParentLoading,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                // For permission step, handle based on state
                                                when (permissionScreenState) {
                                                    PermissionScreenState.RedirectToSettings -> {
                                                        val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                        intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                                                        context.startActivity(intent)
                                                        onRequestAgain()
                                                    }
                                                    PermissionScreenState.PermissionsGranted -> onNextStep()
                                                    PermissionScreenState.Loading -> { /* Do nothing while loading */ }
                                                    else -> onNextStep() // Trigger permission request
                                                }
                                            },
                                            enabled = !isParentLoading && permissionScreenState != PermissionScreenState.Loading,
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = when (permissionScreenState) {
                                                    PermissionScreenState.PermissionsGranted -> MaterialTheme.colorScheme.primary
                                                    PermissionScreenState.RedirectToSettings -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                contentColor = when (permissionScreenState) {
                                                    PermissionScreenState.PermissionsGranted -> MaterialTheme.colorScheme.onPrimary
                                                    PermissionScreenState.RedirectToSettings -> MaterialTheme.colorScheme.onError
                                                    else -> MaterialTheme.colorScheme.onPrimary
                                                }
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Crossfade(
                                                targetState = isParentLoading,
                                                animationSpec = tween(300),
                                                label = "buttonContent"
                                            ) { loading ->
                                                if (loading) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        DataProcessingLoader(
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            context.getString(R.string.onboarding_checking),
                                                            style = MaterialTheme.typography.labelLarge
                                                        )
                                                    }
                                                } else {
                                                    Row(
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val buttonText = when (permissionScreenState) {
                                                            PermissionScreenState.PermissionsGranted -> context.getString(R.string.onboarding_continue)
                                                            PermissionScreenState.RedirectToSettings -> context.getString(R.string.onboarding_open_settings)
                                                            else -> context.getString(R.string.onboarding_grant_access)
                                                        }
                                                        val buttonIcon = when (permissionScreenState) {
                                                            PermissionScreenState.PermissionsGranted -> RhythmIcons.Forward
                                                            PermissionScreenState.RedirectToSettings -> RhythmIcons.Security
                                                            else -> RhythmIcons.Security
                                                        }

                                                        Text(
                                                            buttonText,
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = buttonIcon,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            OnboardingStep.RHYTHM_GUARD -> {
                                EnhancedRhythmGuardContent(
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.BACKUP_RESTORE -> {
                                EnhancedBackupRestoreContent(
                                    onNextStep = onNextStep,
                                    onSkip = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.AUDIO_PLAYBACK -> {
                                EnhancedAudioPlaybackContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    onOpenAutoEQSelector = { showAutoEQSelector = true },
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.THEMING -> {
                                EnhancedThemingContent(
                                    onNextStep = onNextStep,
                                    onSkip = onNextStep,
                                    themeViewModel = themeViewModel,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.PLAYER_THEME_CHOICE -> {
                                EnhancedPlayerThemeChoiceContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.GESTURES -> {
                                EnhancedGesturesContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.LIBRARY_SETUP -> {
                                EnhancedLibrarySetupContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    onOpenTabOrderBottomSheet = { showLibraryTabOrderBottomSheet = true },
                                    onOpenArtistArtworkSource = { showArtistArtworkSourceBottomSheet = true },
                                    onOpenDelimiterBottomSheet = { showDelimiterBottomSheet = true },
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.MEDIA_SCAN -> {
                                EnhancedMediaScanContent(
                                    onNextStep = onNextStep,
                                    onSkip = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.WIDGETS -> {
                                EnhancedWidgetsContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.INTEGRATIONS -> {
                                EnhancedIntegrationsContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    onLyricallyConfigure = { showLyricallySourcesBottomSheet = true },
                                    onAppleCanvasConfigure = { showCanvasNetworkModeDialog = true },
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.RHYTHM_STATS -> {
                                EnhancedRhythmStatsContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.UPDATER -> {
                                EnhancedUpdaterContent(
                                    onNextStep = onNextStep,
                                    appSettings = appSettings,
                                    updaterViewModel = updaterViewModel,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNextStep()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_next),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Forward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.FULL_TOUR_PROMPT -> {
                                EnhancedFullTourPromptContent(
                                    onContinueFullTour = onContinueFullTour,
                                    onSkipFullTour = onSkipFullTour,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null
                                )
                            }
                            OnboardingStep.SETUP_FINISHED -> {
                                EnhancedSetupFinishedContent(
                                    onFinish = onFinish,
                                    isTablet = isTablet,
                                    backButton = if (stepIndex > 0) {
                                        {
                                            val buttonScale = remember { Animatable(1f) }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                                        buttonScale.animateTo(1f, animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        ))
                                                    }
                                                    onPrevStep()
                                                },
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        scaleX = buttonScale.value
                                                        scaleY = buttonScale.value
                                                    },
                                                shape = RoundedCornerShape(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Back,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    } else null,
                                    nextButton = {
                                        val nextButtonScale = remember { Animatable(1f) }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                                    nextButtonScale.animateTo(1f, animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessHigh
                                                    ))
                                                }
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onFinish()
                                            },
                                            modifier = Modifier
                                                .height(56.dp)
                                                .graphicsLayer {
                                                    scaleX = nextButtonScale.value
                                                    scaleY = nextButtonScale.value
                                                },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        ) {
                                            Text(
                                                context.getString(R.string.onboarding_finish_setup),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = RhythmIcons.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            OnboardingStep.COMPLETE -> {
                                // This should not be visible as we transition to the main app
                                Box(modifier = Modifier.fillMaxSize())
                            }
                            OnboardingStep.NOTIFICATIONS -> {
                                // Legacy step not shown in the current onboarding flow.
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }

        // Bottom navigation bar
        AnimatedVisibility(
            visible = currentStep != OnboardingStep.WELCOME && currentStep != OnboardingStep.FULL_TOUR_PROMPT && !isTablet,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceHigh,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = if (isTablet) 48.dp else 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button with spring animation
                    AnimatedVisibility(
                        visible = stepIndex > 0,
                        enter = fadeIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + expandHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        exit = fadeOut(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + shrinkHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        val buttonScale = remember { Animatable(1f) }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    buttonScale.animateTo(0.92f, animationSpec = tween(100))
                                    buttonScale.animateTo(1f, animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessHigh
                                    ))
                                }
                                onPrevStep()
                            },
                            modifier = Modifier
                                .height(if (isTablet) 56.dp else 48.dp)
                                .graphicsLayer {
                                    scaleX = buttonScale.value
                                    scaleY = buttonScale.value
                                },
                            shape = RoundedCornerShape(32.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Back,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // App logo and step count - centered between back and next buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.rhythm_splash_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        androidx.compose.animation.AnimatedContent(
                            targetState = stepIndex,
                            transitionSpec = {
                                (slideInVertically { height -> height / 2 } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height / 2 } + fadeOut()
                                )
                            },
                            modifier = Modifier.padding(top = 4.dp),
                            label = "progressText"
                        ) { step ->
                            Text(
                                text = context.getString(R.string.onboarding_step_progress, step + 1, totalSteps),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Next/Finish button with spring animation
                    val nextButtonScale = remember { Animatable(1f) }

                    Button(
                        onClick = {
                            scope.launch {
                                nextButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                nextButtonScale.animateTo(1f, animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessHigh
                                ))
                            }
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            when (currentStep) {
                                OnboardingStep.PERMISSIONS -> {
                                    // For permission step, handle based on state
                                    when (permissionScreenState) {
                                        PermissionScreenState.RedirectToSettings -> {
                                            val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                                            context.startActivity(intent)
                                            onRequestAgain()
                                        }
                                        PermissionScreenState.PermissionsGranted -> onNextStep()
                                        PermissionScreenState.Loading -> { /* Do nothing while loading */ }
                                        else -> onNextStep() // Trigger permission request
                                    }
                                }
                                else -> onNextStep() // All other steps just go next
                            }
                        },
                        enabled = when (currentStep) {
                            OnboardingStep.PERMISSIONS -> !isParentLoading && permissionScreenState != PermissionScreenState.Loading
                            OnboardingStep.STREAMING_SETUP -> isStreamingServiceConnected
                            else -> true
                        },
                        modifier = Modifier
                            .height(if (isTablet) 56.dp else 48.dp)
                            .graphicsLayer {
                                scaleX = nextButtonScale.value
                                scaleY = nextButtonScale.value
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (currentStep) {
                                OnboardingStep.PERMISSIONS -> when (permissionScreenState) {
                                    PermissionScreenState.PermissionsGranted -> MaterialTheme.colorScheme.primary
                                    PermissionScreenState.RedirectToSettings -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                else -> MaterialTheme.colorScheme.primary
                            },
                            contentColor = when (currentStep) {
                                OnboardingStep.PERMISSIONS -> when (permissionScreenState) {
                                    PermissionScreenState.PermissionsGranted -> MaterialTheme.colorScheme.onPrimary
                                    PermissionScreenState.RedirectToSettings -> MaterialTheme.colorScheme.onError
                                    else -> MaterialTheme.colorScheme.onPrimary
                                }
                                else -> MaterialTheme.colorScheme.onPrimary
                            }
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Crossfade(
                            targetState = currentStep == OnboardingStep.PERMISSIONS && isParentLoading,
                            animationSpec = tween(300),
                            label = "buttonContent"
                        ) { loading ->
                            if (loading) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DataProcessingLoader(
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        context.getString(R.string.onboarding_checking),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val buttonText = when {
                                        currentStep == OnboardingStep.SETUP_FINISHED -> context.getString(R.string.onboarding_lets_go)
                                        currentStep == OnboardingStep.PERMISSIONS -> when (permissionScreenState) {
                                            PermissionScreenState.PermissionsGranted -> context.getString(R.string.onboarding_continue)
                                            PermissionScreenState.RedirectToSettings -> context.getString(R.string.onboarding_open_settings)
                                            else -> context.getString(R.string.onboarding_grant_access)
                                        }
                                        else -> context.getString(R.string.onboarding_next)
                                    }
                                    val buttonIcon = when {
                                        currentStep == OnboardingStep.SETUP_FINISHED -> RhythmIcons.Check
                                        currentStep == OnboardingStep.PERMISSIONS -> when (permissionScreenState) {
                                            PermissionScreenState.PermissionsGranted -> RhythmIcons.Forward
                                            PermissionScreenState.RedirectToSettings -> RhythmIcons.Security
                                            else -> RhythmIcons.Security
                                        }
                                        else -> RhythmIcons.Forward
                                    }

                                    Text(
                                        buttonText,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = buttonIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
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
    }

    // Bottom sheets for advanced configuration
    if (showLibraryTabOrderBottomSheet) {
        LibraryTabOrderBottomSheet(
            onDismiss = { showLibraryTabOrderBottomSheet = false },
            appSettings = appSettings,
            haptics = haptic
        )
    }

    if (showArtistArtworkSourceBottomSheet) {
        ArtistArtworkSourceBottomSheet(
            onDismiss = { showArtistArtworkSourceBottomSheet = false },
            appSettings = appSettings
        )
    }

    if (showDelimiterBottomSheet) {
        ArtistDelimitersBottomSheet(
            onDismiss = { showDelimiterBottomSheet = false },
            appSettings = appSettings
        )
    }

    val currentAutoEQProfile by appSettings.autoEQProfile.collectAsState()
    if (showAutoEQSelector) {
        AutoEQPresetPickerBottomSheet(
            currentProfileName = currentAutoEQProfile,
            onDismissRequest = { showAutoEQSelector = false },
            onProfileSelected = { profile ->
                musicViewModel.applyAutoEQProfile(profile)
                showAutoEQSelector = false
            }
        )
    }

    if (showLyricallySourcesBottomSheet) {
        LyricallySourcesBottomSheet(
            onDismiss = { showLyricallySourcesBottomSheet = false },
            appSettings = appSettings,
            haptics = haptic
        )
    }

    if (showCanvasNetworkModeDialog) {
        CanvasNetworkModeDialog(
            onDismiss = { showCanvasNetworkModeDialog = false },
            appSettings = appSettings,
            context = context,
            haptic = haptic
        )
    }
}

@Composable
private fun OnboardingTopBackButton(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val buttonScale = remember { Animatable(1f) }

    IconButton(
        onClick = {
            scope.launch {
                buttonScale.animateTo(0.92f, animationSpec = tween(100))
                buttonScale.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    )
                )
            }
            onBackClick()
        },
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 12.dp)
            .graphicsLayer {
                scaleX = buttonScale.value
                scaleY = buttonScale.value
            }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = RhythmIcons.Back,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Unified card container for all onboarding steps (except welcome)
 * Provides consistent Material You styling with rounded corners and elevated surface
 */
@Composable
private fun OnboardingCard(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val contentMaxWidth = if (isTablet) 1000.dp else androidx.compose.ui.unit.Dp.Infinity
    val cardPadding = if (isTablet) 20.dp else 10.dp

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = if (isTablet) 0.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .let { if (isTablet) it.width(contentMaxWidth) else it }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        // Remove vertical scroll since we're not constraining height anymore
        // and let pager handle its own sizing and scrolling behavior
        Column(
            modifier = Modifier.padding(cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun OnboardingStepHeaderIcon(
    imageVector: MaterialSymbolIcon,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun OnboardingStepHeaderIcon(
    painter: androidx.compose.ui.graphics.painter.Painter,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun EnhancedWelcomeContent(
    onNextStep: () -> Unit,
    themeViewModel: ThemeViewModel,
    isTablet: Boolean = false,
    contentMaxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Infinity
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showLanguageSwitcher by remember { mutableStateOf(false) }

    val useSystemTheme by themeViewModel.useSystemTheme.collectAsState()
    val darkMode by themeViewModel.darkMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        // Draw rotating background cookies
        RotatingBackgroundCookies(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f))

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (isTablet) Modifier.width(contentMaxWidth) else Modifier.fillMaxWidth())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isTablet) 48.dp else 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Welcome to & Rhythm centered vertically
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_to),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isTablet) 48.sp else 38.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = if (isTablet) 72.sp else 56.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom row of three pill-shaped actions
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Action: Vertically elongated Pill for Light/Dark Mode toggle
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 80.dp)
                        .clip(RoundedCornerShape(34.dp)) // pill shape
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            themeViewModel.setUseSystemTheme(false)
                            themeViewModel.setDarkMode(!darkMode)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (darkMode) MaterialSymbolIcon("light_mode") else MaterialSymbolIcon("dark_mode"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Middle Action: Large pill-shaped "Get started" button
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                        onNextStep()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(40.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = context.getString(R.string.onboarding_get_started),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                }

                // Right Action: Vertically elongated Pill for Language Switcher
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 80.dp)
                        .clip(RoundedCornerShape(34.dp)) // pill shape
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            showLanguageSwitcher = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Language,
                        contentDescription = stringResource(R.string.cd_change_language),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Language switcher dialog
            if (showLanguageSwitcher) {
                LanguageSwitcherDialog(
                    onDismiss = { showLanguageSwitcher = false }
                )
            }
        }
    }
}

@Composable
private fun WelcomeFeatureChip(
    icon: MaterialSymbolIcon,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EnhancedPermissionContent(
    permissionScreenState: PermissionScreenState,
    onGrantAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    isButtonLoading: Boolean,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Define permissions based on Android version within the composable
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val essentialPermissions = storagePermissions + bluetoothPermissions + notificationPermissions
    val permissionsState = rememberMultiplePermissionsState(essentialPermissions)
    val scrollState = rememberScrollState()

    if (isTablet) {
        // Tablet layout: Left side - icon, description, permission tips; Right side - permission cards and Android 13 notice
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, description, and permission tips
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced icon with dynamic state
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = when (permissionScreenState) {
                            PermissionScreenState.PermissionsGranted -> RhythmIcons.Check
                            PermissionScreenState.RedirectToSettings -> RhythmIcons.Security
                            else -> RhythmIcons.Security
                        },
                        tint = when (permissionScreenState) {
                            PermissionScreenState.PermissionsGranted -> MaterialTheme.colorScheme.primary
                            PermissionScreenState.RedirectToSettings -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = when (permissionScreenState) {
                        PermissionScreenState.PermissionsGranted -> context.getString(R.string.onboarding_permissions_granted_title)
                        PermissionScreenState.RedirectToSettings -> context.getString(R.string.onboarding_action_required_settings)
                        PermissionScreenState.ShowRationale -> context.getString(R.string.onboarding_permissions_needed)
                        else -> context.getString(R.string.onboarding_grant_permissions)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = when (permissionScreenState) {
                        PermissionScreenState.PermissionsGranted -> context.getString(R.string.onboarding_permissions_granted_desc)
                        PermissionScreenState.RedirectToSettings -> context.getString(R.string.onboarding_redirect_settings_desc)
                        PermissionScreenState.ShowRationale -> context.getString(R.string.onboarding_rationale_desc)
                        else -> context.getString(R.string.onboarding_permissions_required_desc)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Permission tips card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_permission_tips),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        PermissionTipItem(
                            icon = RhythmIcons.CheckCircle,
                            text = context.getString(R.string.onboarding_permission_tip_1)
                        )
                        PermissionTipItem(
                            icon = RhythmIcons.SettingsFilled,
                            text = context.getString(R.string.onboarding_permission_tip_2)
                        )
                        PermissionTipItem(
                            icon = RhythmIcons.Security,
                            text = context.getString(R.string.onboarding_permission_tip_3)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Permission cards and Android 13 notice
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced permission explanation cards
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnhancedPermissionCard(
                        icon = RhythmIcons.MusicNote,
                        title = context.getString(R.string.onboarding_permission_music_title),
                        description = context.getString(R.string.onboarding_permission_music_desc),
                        isGranted = storagePermissions.all { permission ->
                            permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
                        }
                    )

                    EnhancedPermissionCard(
                        icon = RhythmIcons.Devices.Bluetooth,
                        title = context.getString(R.string.onboarding_permission_bluetooth_title),
                        description = context.getString(R.string.onboarding_permission_bluetooth_desc),
                        isGranted = bluetoothPermissions.all { permission ->
                            permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        EnhancedPermissionCard(
                            icon = RhythmIcons.Notifications,
                            title = context.getString(R.string.onboarding_permission_notifications_title),
                            description = context.getString(R.string.onboarding_permission_notifications_desc),
                            isGranted = notificationPermissions.all { permission ->
                                permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
                            }
                        )
                    }
                }

                // Android 13 permission notice
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissionScreenState == PermissionScreenState.PermissionsRequired) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.BugReport,
                                    contentDescription = null,
                                    
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = context.getString(R.string.onboarding_android13_notice),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = context.getString(R.string.onboarding_android13_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Enhanced icon with dynamic state
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn()
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = when (permissionScreenState) {
                        PermissionScreenState.PermissionsGranted -> RhythmIcons.Check
                        PermissionScreenState.RedirectToSettings -> RhythmIcons.Security
                        else -> RhythmIcons.Security
                    },
                    tint = when (permissionScreenState) {
                        PermissionScreenState.PermissionsGranted -> MaterialTheme.colorScheme.primary
                        PermissionScreenState.RedirectToSettings -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (permissionScreenState) {
                    PermissionScreenState.PermissionsGranted -> context.getString(R.string.onboarding_permissions_granted_title)
                    PermissionScreenState.RedirectToSettings -> context.getString(R.string.onboarding_action_required_settings)
                    PermissionScreenState.ShowRationale -> context.getString(R.string.onboarding_permissions_needed)
                    else -> context.getString(R.string.onboarding_grant_permissions)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = when (permissionScreenState) {
                    PermissionScreenState.PermissionsGranted -> context.getString(R.string.onboarding_permissions_granted_desc)
                    PermissionScreenState.RedirectToSettings -> context.getString(R.string.onboarding_redirect_settings_desc)
                    PermissionScreenState.ShowRationale -> context.getString(R.string.onboarding_rationale_desc)
                    else -> context.getString(R.string.onboarding_permissions_required_desc)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 16.dp else 32.dp)
            )

            // Android 13+ permission notice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissionScreenState == PermissionScreenState.PermissionsRequired) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.BugReport,
                                contentDescription = null,
                                
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.onboarding_android13_notice),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = context.getString(R.string.onboarding_android13_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Enhanced permission explanation cards
            Column(
                modifier = Modifier.padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EnhancedPermissionCard(
                    icon = RhythmIcons.MusicNote,
                    title = context.getString(R.string.onboarding_permission_music_title),
                    description = context.getString(R.string.onboarding_permission_music_desc),
                    isGranted = storagePermissions.all { permission ->
                        permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
                    }
                )

                EnhancedPermissionCard(
                    icon = RhythmIcons.Devices.Bluetooth,
                    title = context.getString(R.string.onboarding_permission_bluetooth_title),
                    description = context.getString(R.string.onboarding_permission_bluetooth_desc),
                    isGranted = bluetoothPermissions.all { permission ->
                        permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    EnhancedPermissionCard(
                        icon = RhythmIcons.Notifications,
                        title = context.getString(R.string.onboarding_permission_notifications_title),
                        description = context.getString(R.string.onboarding_permission_notifications_desc),
                        isGranted = notificationPermissions.all { permission ->
                            permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
                        }
                    )
                }
            }

            // Button removed - now handled by bottom navigation bar
        }
    }
}

@Composable
fun EnhancedPermissionCard(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    isGranted: Boolean = false
) {
    val context = LocalContext.current
    // Animated state changes
    val containerColor by animateColorAsState(
        targetValue = if (isGranted)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "containerColor"
    )

    val iconBackgroundColor by animateColorAsState(
        targetValue = if (isGranted)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "iconBackgroundColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isGranted)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "iconTint"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = if (isGranted)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                // Crossfade between icon and checkmark
                Crossfade(
                    targetState = isGranted,
                    animationSpec = tween(400),
                    label = "iconCrossfade"
                ) { granted ->
                    Icon(
                        imageVector = if (granted) RhythmIcons.Check else icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Success badge with animation
                    AnimatedVisibility(
                        visible = isGranted,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.onboarding_granted),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionTipItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun EnhancedBackupRestoreContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    onSkip: () -> Unit = {},
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val musicViewModel: MusicViewModel = viewModel()
    val scrollState = rememberScrollState()

    // State for backup settings
    val autoBackupEnabled by appSettings.autoBackupEnabled.collectAsState()
    val lastBackupTimestamp by appSettings.lastBackupTimestamp.collectAsState()
    val isLibraryRefreshing by musicViewModel.isLibraryRefreshing.collectAsState()
    val restoreResult by musicViewModel.restoreResult.collectAsState()

    // Local UI state
    var isCreatingBackup by remember { mutableStateOf(false) }
    var isRestoringFromClipboard by remember { mutableStateOf(false) }
    var isRestoringFromFile by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusIsError by remember { mutableStateOf(false) }
    var showRestartHint by remember { mutableStateOf(false) }

    val isBusy = isCreatingBackup || isRestoringFromClipboard || isRestoringFromFile

    fun restartApp() {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        (context as? Activity)?.finish()
        Runtime.getRuntime().exit(0)
    }

    LaunchedEffect(restoreResult) {
        when (val result = restoreResult) {
            is MusicViewModel.RestoreResult.Queued -> {
                Toast.makeText(context, context.getString(R.string.onboarding_restore_queued_scan), Toast.LENGTH_LONG).show()
                musicViewModel.clearRestoreResult()
            }
            is MusicViewModel.RestoreResult.Success -> {
                backupStatusIsError = false
                backupStatusMessage = if (result.wasQueued) {
                    "Queued restore completed successfully! Please restart the app."
                } else {
                    context.getString(R.string.backup_restored_success)
                }
                showRestartHint = true
                musicViewModel.clearRestoreResult()
            }
            is MusicViewModel.RestoreResult.Failure -> {
                backupStatusIsError = true
                backupStatusMessage = result.errorMessage
                showRestartHint = false
                musicViewModel.clearRestoreResult()
            }
            else -> {}
        }
    }

    fun handleRestorePayload(backupJson: String?) {
        if (backupJson.isNullOrEmpty()) {
            backupStatusIsError = true
            backupStatusMessage = context.getString(R.string.backup_unable_to_read)
            showRestartHint = false
            return
        }

        musicViewModel.restoreFromBackup(backupJson)
    }

    val backupLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                if (isLibraryRefreshing) {
                    Toast.makeText(context, context.getString(R.string.onboarding_cannot_backup_scanning), Toast.LENGTH_LONG).show()
                    isCreatingBackup = false
                    return@let
                }
                scope.launch {
                    try {
                        isCreatingBackup = true
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                        musicViewModel.ensurePlaylistsSaved()

                        val backupJson = appSettings.createBackup()
                        val outputStream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Unable to open backup destination")
                        outputStream.use { stream ->
                            stream.write(backupJson.toByteArray())
                            stream.flush()
                        }

                        appSettings.setLastBackupTimestamp(System.currentTimeMillis())
                        appSettings.setBackupLocation(uri.toString())

                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(context.getString(R.string.backup_clip_label), backupJson)
                        clipboard.setPrimaryClip(clip)

                        backupStatusIsError = false
                        backupStatusMessage = context.getString(R.string.backup_copied_to_clipboard)
                        showRestartHint = false
                    } catch (e: Exception) {
                        backupStatusIsError = true
                        backupStatusMessage = context.getString(R.string.backup_failed_to_create, e.message ?: "")
                        showRestartHint = false
                    } finally {
                        isCreatingBackup = false
                    }
                }
            } ?: run {
                isCreatingBackup = false
            }
        } else {
            isCreatingBackup = false
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                scope.launch {
                    try {
                        isRestoringFromFile = true
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                        val backupJson = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                        handleRestorePayload(backupJson)
                    } catch (e: Exception) {
                        backupStatusIsError = true
                        backupStatusMessage = context.getString(R.string.backup_failed_to_restore_file, e.message ?: "")
                        showRestartHint = false
                    } finally {
                        isRestoringFromFile = false
                    }
                }
            } ?: run {
                isRestoringFromFile = false
            }
        } else {
            isRestoringFromFile = false
        }
    }

    fun restoreFromClipboard() {
        scope.launch {
            try {
                isRestoringFromClipboard = true
                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val backupJson = if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(context)?.toString()
                } else {
                    null
                }

                if (backupJson == null) {
                    backupStatusIsError = true
                    backupStatusMessage = context.getString(R.string.backup_no_backup_clipboard)
                    showRestartHint = false
                } else {
                    handleRestorePayload(backupJson)
                }
            } catch (e: Exception) {
                backupStatusIsError = true
                backupStatusMessage = context.getString(R.string.backup_failed_to_restore_clipboard, e.message ?: "")
                showRestartHint = false
            } finally {
                isRestoringFromClipboard = false
            }
        }
    }

    fun launchCreateBackup() {
        if (isBusy) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(
                Intent.EXTRA_TITLE,
                "rhythm_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.json"
            )
        }
        backupLocationLauncher.launch(intent)
    }

    fun launchRestoreFile() {
        if (isBusy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "*/*"))
        }
        restoreFileLauncher.launch(intent)
    }

    if (isTablet) {
        // Tablet layout: Left side - icon, title, description, tips, action buttons; Right side - toggles and cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, title, description, tips, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced icon with animation
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("backup", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_backup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_backup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Backup features info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_what_backed_up),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        BackupFeatureTipItem(
                            icon = MaterialSymbolIcon("save", filled = true),
                            text = context.getString(R.string.onboarding_backed_up_1)
                        )
                        BackupFeatureTipItem(
                            icon = MaterialSymbolIcon("restore_from_trash", filled = true),
                            text = context.getString(R.string.onboarding_backed_up_2)
                        )
                        BackupFeatureTipItem(
                            icon = RhythmIcons.Security,
                            text = context.getString(R.string.onboarding_backed_up_3)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Standard settings groups
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BackupRestoreSettingsSection(
                    autoBackupEnabled = autoBackupEnabled,
                    isCreatingBackup = isCreatingBackup,
                    isRestoringFromClipboard = isRestoringFromClipboard,
                    isRestoringFromFile = isRestoringFromFile,
                    isBusy = isBusy,
                    backupStatusMessage = backupStatusMessage,
                    backupStatusIsError = backupStatusIsError,
                    showRestartHint = showRestartHint,
                    onAutoBackupChange = { appSettings.setAutoBackupEnabled(it) },
                    onCreateBackup = { launchCreateBackup() },
                    onRestoreFromClipboard = { restoreFromClipboard() },
                    onRestoreFromFile = { launchRestoreFile() },
                    onRestartApp = { restartApp() },
                    context = context,
                    hapticFeedback = hapticFeedback
                )
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header with icon and title
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Enhanced icon with animation
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("backup", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 56.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = context.getString(R.string.onboarding_backup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_backup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            // Standard settings groups area
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                BackupRestoreSettingsSection(
                    autoBackupEnabled = autoBackupEnabled,
                    isCreatingBackup = isCreatingBackup,
                    isRestoringFromClipboard = isRestoringFromClipboard,
                    isRestoringFromFile = isRestoringFromFile,
                    isBusy = isBusy,
                    backupStatusMessage = backupStatusMessage,
                    backupStatusIsError = backupStatusIsError,
                    showRestartHint = showRestartHint,
                    onAutoBackupChange = { appSettings.setAutoBackupEnabled(it) },
                    onCreateBackup = { launchCreateBackup() },
                    onRestoreFromClipboard = { restoreFromClipboard() },
                    onRestoreFromFile = { launchRestoreFile() },
                    onRestartApp = { restartApp() },
                    context = context,
                    hapticFeedback = hapticFeedback
                )

                // Backup features info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_what_backed_up),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        BackupFeatureTipItem(
                            icon = MaterialSymbolIcon("save", filled = true),
                            text = context.getString(R.string.onboarding_backed_up_1)
                        )
                        BackupFeatureTipItem(
                            icon = MaterialSymbolIcon("restore_from_trash", filled = true),
                            text = context.getString(R.string.onboarding_backed_up_2)
                        )
                        BackupFeatureTipItem(
                            icon = RhythmIcons.Security,
                            text = context.getString(R.string.onboarding_backed_up_3)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreSettingsSection(
    autoBackupEnabled: Boolean,
    isCreatingBackup: Boolean,
    isRestoringFromClipboard: Boolean,
    isRestoringFromFile: Boolean,
    isBusy: Boolean,
    backupStatusMessage: String?,
    backupStatusIsError: Boolean,
    showRestartHint: Boolean,
    onAutoBackupChange: (Boolean) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreFromClipboard: () -> Unit,
    onRestoreFromFile: () -> Unit,
    onRestartApp: () -> Unit,
    context: Context,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    TourSectionTitle(context.getString(R.string.settings_backup_action_short))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = MaterialSymbolIcon("save", filled = true),
                title = { Text(context.getString(R.string.settings_create_backup)) },
                description = { Text(context.getString(R.string.settings_create_backup_desc)) },
                trailingContent = if (isCreatingBackup) {
                    {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                enabled = !isBusy,
                onClick = {
                    if (!isBusy) {
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                        onCreateBackup()
                    }
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("autorenew", filled = true),
                title = { Text(context.getString(R.string.onboarding_auto_backup)) },
                description = { Text(context.getString(R.string.onboarding_auto_backup_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = autoBackupEnabled,
                        onCheckedChange = {
                            onAutoBackupChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                    onAutoBackupChange(!autoBackupEnabled)
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )

    TourSectionTitle(context.getString(R.string.settings_restore_action_short))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = RhythmIcons.ContentCopy,
                title = { Text(context.getString(R.string.settings_restore_clipboard)) },
                description = { Text(context.getString(R.string.settings_restore_clipboard_desc)) },
                trailingContent = if (isRestoringFromClipboard) {
                    {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                enabled = !isBusy,
                onClick = {
                    if (!isBusy) {
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                        onRestoreFromClipboard()
                    }
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.FolderOpen,
                title = { Text(context.getString(R.string.settings_restore_file)) },
                description = { Text(context.getString(R.string.settings_restore_file_desc)) },
                trailingContent = if (isRestoringFromFile) {
                    {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                enabled = !isBusy,
                onClick = {
                    if (!isBusy) {
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                        onRestoreFromFile()
                    }
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )

    AnimatedVisibility(
        visible = backupStatusMessage != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        BackupRestoreStatusCard(
            message = backupStatusMessage ?: "",
            isError = backupStatusIsError,
            showRestart = showRestartHint,
            onRestart = onRestartApp
        )
    }

    AnimatedVisibility(
        visible = autoBackupEnabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.onboarding_manual_backup_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackupRestoreStatusCard(
    message: String,
    isError: Boolean,
    showRestart: Boolean,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isError) MaterialSymbolIcon("error", filled = true) else RhythmIcons.CheckCircle,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isError) context.getString(R.string.ui_error) else "Success",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (!isError && showRestart) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onRestart,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.settings_restart_now))
                }
            }
        }
    }
}

@Composable
private fun BackupFeatureTipItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun LibraryTipItem(
    icon: MaterialSymbolIcon,
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
fun EnhancedAudioPlaybackContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    onOpenAutoEQSelector: () -> Unit = {},
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useSystemVolume by appSettings.useSystemVolume.collectAsState()
    val stopPlaybackOnZeroVolume by appSettings.stopPlaybackOnZeroVolume.collectAsState()
    val resumeOnDeviceReconnect by appSettings.resumeOnDeviceReconnect.collectAsState()
    val autoAddToQueue by appSettings.autoAddToQueue.collectAsState()
    val showLyrics by appSettings.showLyrics.collectAsState()
    val lyricsSourcePreference by appSettings.lyricsSourcePreference.collectAsState()
    val replayGain by appSettings.replayGain.collectAsState()
    val gaplessPlayback by appSettings.gaplessPlayback.collectAsState()
    val skipSilenceEnabled by appSettings.skipSilenceEnabled.collectAsState()
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val autoEQProfile by appSettings.autoEQProfile.collectAsState()
    val scrollState = rememberScrollState()

    if (isTablet) {
        // Tablet layout: Left side - icon, title, description, tips, action buttons; Right side - toggles and dropdowns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, title, description, tips, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced audio icon
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.Player.VolumeUp,
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_audio_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_audio_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Equalizer and Sleep Timer info card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_additional_features),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        LibraryTipItem(
                            icon = MaterialSymbolIcon("graphic_eq", filled = true),
                            text = context.getString(R.string.onboarding_equalizer_desc)
                        )
                        LibraryTipItem(
                            icon = RhythmIcons.AccessTime,
                            text = context.getString(R.string.onboarding_sleep_timer_desc)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Consolidated settings card
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Consolidated Audio Settings Card
                AudioPlaybackSettingsCard(
                    useSystemVolume = useSystemVolume,
                    stopPlaybackOnZeroVolume = stopPlaybackOnZeroVolume,
                    resumeOnDeviceReconnect = resumeOnDeviceReconnect,
                    autoAddToQueue = autoAddToQueue,
                    showLyrics = showLyrics,
                    gaplessPlayback = gaplessPlayback,
                    skipSilenceEnabled = skipSilenceEnabled,
                    isAudioOffloadActive = isAudioOffloadActive,
                    replayGain = replayGain,
                    autoEQProfile = autoEQProfile,
                    onSystemVolumeChange = { appSettings.setUseSystemVolume(it) },
                    onStopPlaybackOnZeroVolumeChange = { appSettings.setStopPlaybackOnZeroVolume(it) },
                    onResumeOnReconnectChange = { appSettings.setResumeOnDeviceReconnect(it) },
                    onAutoQueueChange = { appSettings.setAutoAddToQueue(it) },
                    onShowLyricsChange = { appSettings.setShowLyrics(it) },
                    onGaplessChange = { appSettings.setGaplessPlayback(it) },
                    onSkipSilenceChange = { appSettings.setSkipSilenceEnabled(it) },
                    onReplayGainChange = { appSettings.setReplayGain(it) },
                    onOpenAutoEQSelector = onOpenAutoEQSelector
                )

                // Lyrics Source Priority dropdown (shown when lyrics are enabled)
                AnimatedVisibility(
                    visible = showLyrics,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsDropdownItem(
                            title = context.getString(R.string.onboarding_lyrics_source_title),
                            description = context.getString(R.string.onboarding_lyrics_source_desc),
                            selectedOption = lyricsSourcePreference.displayName,
                            icon = MaterialSymbolIcon("cloud", filled = true),
                            options = chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.values().map { it.displayName },
                            onOptionSelected = { displayName ->
                                val preference = chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.values()
                                    .find { it.displayName == displayName }
                                if (preference != null) {
                                    appSettings.setLyricsSourcePreference(preference)
                                }
                            }
                        )

                        // Lyrics sources info
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    contentDescription = null,
                                    
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = context.getString(R.string.onboarding_lyrics_sources),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Enhanced audio icon
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn()
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.Player.VolumeUp,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_audio_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_audio_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Vertically centered content area
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                // Consolidated Audio Settings Card
                AudioPlaybackSettingsCard(
                    useSystemVolume = useSystemVolume,
                    stopPlaybackOnZeroVolume = stopPlaybackOnZeroVolume,
                    resumeOnDeviceReconnect = resumeOnDeviceReconnect,
                    autoAddToQueue = autoAddToQueue,
                    showLyrics = showLyrics,
                    gaplessPlayback = gaplessPlayback,
                    skipSilenceEnabled = skipSilenceEnabled,
                    isAudioOffloadActive = isAudioOffloadActive,
                    replayGain = replayGain,
                    autoEQProfile = autoEQProfile,
                    onSystemVolumeChange = { appSettings.setUseSystemVolume(it) },
                    onStopPlaybackOnZeroVolumeChange = { appSettings.setStopPlaybackOnZeroVolume(it) },
                    onResumeOnReconnectChange = { appSettings.setResumeOnDeviceReconnect(it) },
                    onAutoQueueChange = { appSettings.setAutoAddToQueue(it) },
                    onShowLyricsChange = { appSettings.setShowLyrics(it) },
                    onGaplessChange = { appSettings.setGaplessPlayback(it) },
                    onSkipSilenceChange = { appSettings.setSkipSilenceEnabled(it) },
                    onReplayGainChange = { appSettings.setReplayGain(it) },
                    onOpenAutoEQSelector = onOpenAutoEQSelector
                )

                // Lyrics Source Priority dropdown (shown when lyrics are enabled)
                AnimatedVisibility(
                    visible = showLyrics,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsDropdownItem(
                            title = context.getString(R.string.onboarding_lyrics_source_title),
                            description = context.getString(R.string.onboarding_lyrics_source_desc),
                            selectedOption = lyricsSourcePreference.displayName,
                            icon = MaterialSymbolIcon("cloud", filled = true),
                            options = chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.values().map { it.displayName },
                            onOptionSelected = { displayName ->
                                val preference = chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.values()
                                    .find { it.displayName == displayName }
                                if (preference != null) {
                                    appSettings.setLyricsSourcePreference(preference)
                                }
                            }
                        )

                        // Lyrics sources info
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    contentDescription = null,
                                    
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = context.getString(R.string.onboarding_lyrics_sources),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

            } // End vertically centered content

            Spacer(modifier = Modifier.height(16.dp))

            // Equalizer and Sleep Timer info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Info,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.onboarding_additional_features),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    LibraryTipItem(
                        icon = MaterialSymbolIcon("graphic_eq", filled = true),
                        text = context.getString(R.string.onboarding_equalizer_desc)
                    )
                    LibraryTipItem(
                        icon = RhythmIcons.AccessTime,
                        text = context.getString(R.string.onboarding_sleep_timer_desc)
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedLibrarySetupContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    onOpenTabOrderBottomSheet: () -> Unit = {},
    onOpenArtistArtworkSource: () -> Unit = {},
    onOpenDelimiterBottomSheet: () -> Unit = {},
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val losslessArtwork by appSettings.losslessArtwork.collectAsState()
    val preferSongArtwork by appSettings.preferSongArtwork.collectAsState()
    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()
    val artistArtworkSource by appSettings.artistArtworkSource.collectAsState()
    val scrollState = rememberScrollState()

    if (isTablet) {
        // Tablet layout: Left side - icon, title, description, tips, action buttons; Right side - toggles and cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, title, description, tips, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced library icon
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.Library,
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = context.getString(R.string.onboarding_library_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = context.getString(R.string.onboarding_library_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Educational cards for library features
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = MaterialSymbolIcon("lightbulb"),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_library_how_works),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        LibraryTipItem(
                            icon = MaterialSymbolIcon("reorder", filled = true),
                            text = context.getString(R.string.onboarding_library_1)
                        )
                        LibraryTipItem(
                            icon = RhythmIcons.Queue,
                            text = context.getString(R.string.onboarding_library_2)
                        )
                        LibraryTipItem(
                            icon = RhythmIcons.Library,
                            text = context.getString(R.string.onboarding_library_4)
                        )
                        LibraryTipItem(
                            icon = RhythmIcons.Tune,
                            text = context.getString(R.string.onboarding_library_3)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Library settings
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LibrarySettingsCard(
                    losslessArtwork = losslessArtwork,
                    preferSongArtwork = preferSongArtwork,
                    artistSeparatorEnabled = artistSeparatorEnabled,
                    artistSeparatorDelimiters = artistSeparatorDelimiters,
                    artistArtworkSource = artistArtworkSource,
                    onLosslessArtworkChange = { appSettings.setLosslessArtwork(it) },
                    onPreferSongArtworkChange = { appSettings.setPreferSongArtwork(it) },
                    onArtistSeparatorChange = { appSettings.setArtistSeparatorEnabled(it) },
                    onOpenArtistArtworkSource = onOpenArtistArtworkSource,
                    onOpenTabOrderBottomSheet = onOpenTabOrderBottomSheet,
                    onOpenDelimiterBottomSheet = onOpenDelimiterBottomSheet
                )
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Enhanced library icon
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn()
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.Library,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_library_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_library_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LibrarySettingsCard(
                losslessArtwork = losslessArtwork,
                preferSongArtwork = preferSongArtwork,
                artistSeparatorEnabled = artistSeparatorEnabled,
                artistSeparatorDelimiters = artistSeparatorDelimiters,
                artistArtworkSource = artistArtworkSource,
                onLosslessArtworkChange = { appSettings.setLosslessArtwork(it) },
                onPreferSongArtworkChange = { appSettings.setPreferSongArtwork(it) },
                onArtistSeparatorChange = { appSettings.setArtistSeparatorEnabled(it) },
                onOpenArtistArtworkSource = onOpenArtistArtworkSource,
                onOpenTabOrderBottomSheet = onOpenTabOrderBottomSheet,
                onOpenDelimiterBottomSheet = onOpenDelimiterBottomSheet
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Educational cards for library features
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MaterialSymbolIcon("lightbulb"),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.onboarding_library_how_works),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    LibraryTipItem(
                        icon = MaterialSymbolIcon("reorder", filled = true),
                        text = context.getString(R.string.onboarding_library_1)
                    )
                    LibraryTipItem(
                        icon = RhythmIcons.Queue,
                        text = context.getString(R.string.onboarding_library_2)
                    )
                    LibraryTipItem(
                        icon = RhythmIcons.Library,
                        text = context.getString(R.string.onboarding_library_4)
                    )
                    LibraryTipItem(
                        icon = RhythmIcons.Tune,
                        text = context.getString(R.string.onboarding_library_3)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySettingsCard(
    losslessArtwork: Boolean,
    preferSongArtwork: Boolean,
    artistSeparatorEnabled: Boolean,
    artistSeparatorDelimiters: String,
    artistArtworkSource: ArtistArtworkSource,
    onLosslessArtworkChange: (Boolean) -> Unit,
    onPreferSongArtworkChange: (Boolean) -> Unit,
    onArtistSeparatorChange: (Boolean) -> Unit,
    onOpenArtistArtworkSource: () -> Unit,
    onOpenTabOrderBottomSheet: () -> Unit,
    onOpenDelimiterBottomSheet: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val currentDelimitersList = remember(artistSeparatorDelimiters) {
        ArtistSeparator.parseDelimiters(artistSeparatorDelimiters)
    }

    val artistArtworkSourceSubtitle = when (artistArtworkSource) {
        ArtistArtworkSource.PREFER_LOCAL_THEN_API -> stringResource(R.string.settings_artist_artwork_source_prefer_local)
        ArtistArtworkSource.LOCAL_ONLY -> stringResource(R.string.settings_artist_artwork_source_local_only)
        ArtistArtworkSource.API_ONLY -> stringResource(R.string.settings_artist_artwork_source_api_only)
        ArtistArtworkSource.DISABLED -> stringResource(R.string.settings_artist_artwork_source_disabled)
    }

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = MaterialSymbolIcon("reorder", filled = true),
                title = { Text(stringResource(R.string.onboarding_library_tab_order_title)) },
                description = { Text(stringResource(R.string.onboarding_library_tab_order_desc)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onOpenTabOrderBottomSheet()
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("high_quality"),
                title = { Text(stringResource(R.string.onboarding_lossless_artwork_title)) },
                description = { Text(stringResource(R.string.onboarding_lossless_artwork_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = losslessArtwork,
                        onCheckedChange = {
                            onLosslessArtworkChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onLosslessArtworkChange(!losslessArtwork)
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.Image,
                title = { Text(stringResource(R.string.settings_ignore_mediastore_covers)) },
                description = { Text(stringResource(R.string.settings_ignore_mediastore_covers_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = preferSongArtwork,
                        onCheckedChange = {
                            onPreferSongArtworkChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onPreferSongArtworkChange(!preferSongArtwork)
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("call_split"),
                title = { Text(stringResource(R.string.onboarding_multi_artist_title)) },
                description = { Text(stringResource(R.string.onboarding_multi_artist_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = artistSeparatorEnabled,
                        onCheckedChange = {
                            onArtistSeparatorChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onArtistSeparatorChange(!artistSeparatorEnabled)
                }
            )
        ) + (if (artistSeparatorEnabled) {
            listOf(
                Material3SettingsItem(
                    icon = RhythmIcons.Settings,
                    title = { Text(stringResource(R.string.artist_configure_delimiters)) },
                    description = { Text(context.getString(R.string.artist_current_delimiters, currentDelimitersList.joinToString(", "))) },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onOpenDelimiterBottomSheet()
                    }
                )
            )
        } else emptyList()) + listOf(
            Material3SettingsItem(
                icon = RhythmIcons.Artist,
                title = { Text(stringResource(R.string.settings_artist_artwork_source)) },
                description = { Text(artistArtworkSourceSubtitle) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onOpenArtistArtworkSource()
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun FolderManagementCard(
    isBlacklistMode: Boolean,
    blacklistedFolders: List<String>,
    whitelistedFolders: List<String>,
    onModeChange: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentFolders = if (isBlacklistMode) blacklistedFolders else whitelistedFolders
    val currentModeLabel = if (isBlacklistMode) "Blacklist" else "Whitelist"

    val folderItems = buildList {
        add(
            Material3SettingsItem(
                icon = RhythmIcons.FilterList,
                title = { Text(stringResource(R.string.onboardingscreen_media_scan_mode)) },
                description = {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            text = context.getString(R.string.onboarding_media_scan_current_mode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ExpressiveButtonGroup(
                            items = listOf("Blacklist", "Whitelist"),
                            selectedIndex = if (isBlacklistMode) 0 else 1,
                            onItemClick = { index ->
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onModeChange(index == 0)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        )

        add(
            Material3SettingsItem(
                icon = MaterialSymbolIcon("create_new_folder"),
                title = { Text(stringResource(R.string.settings_add_folder_button)) },
                description = {
                    Text(
                        if (isBlacklistMode) {
                            "Select folders to block from library"
                        } else {
                            "Select folders to include in library"
                        }
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onAddFolder()
                }
            )
        )

        if (currentFolders.isNotEmpty()) {
            add(
                Material3SettingsItem(
                    icon = RhythmIcons.Folder,
                    title = {
                        Text("${currentFolders.size} ${if (isBlacklistMode) "blocked" else "whitelisted"} folders")
                    },
                    description = {
                        Text(stringResource(R.string.onboardingscreen_tap_the_remove_action))
                    }
                )
            )

            currentFolders.forEach { folder ->
                add(
                    Material3SettingsItem(
                        icon = RhythmIcons.Folder,
                        title = { Text(folder.substringAfterLast("/")) },
                        description = {
                            Text(
                                text = folder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveFolder(folder) }) {
                                Icon(
                                    imageVector = RhythmIcons.Close,
                                    contentDescription = stringResource(R.string.content_desc_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                )
            }
        }
    }

    Material3SettingsGroup(
        items = folderItems,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun AudioPlaybackSettingsCard(
    useSystemVolume: Boolean,
    stopPlaybackOnZeroVolume: Boolean,
    resumeOnDeviceReconnect: Boolean,
    autoAddToQueue: Boolean,
    showLyrics: Boolean,
    gaplessPlayback: Boolean,
    skipSilenceEnabled: Boolean,
    isAudioOffloadActive: Boolean,
    replayGain: Boolean,
    autoEQProfile: String,
    onSystemVolumeChange: (Boolean) -> Unit,
    onStopPlaybackOnZeroVolumeChange: (Boolean) -> Unit,
    onResumeOnReconnectChange: (Boolean) -> Unit,
    onAutoQueueChange: (Boolean) -> Unit,
    onShowLyricsChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onReplayGainChange: (Boolean) -> Unit,
    onOpenAutoEQSelector: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = RhythmIcons.Player.VolumeUp,
                title = { Text(context.getString(R.string.onboarding_system_volume_title)) },
                description = { Text(context.getString(R.string.onboarding_system_volume_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = useSystemVolume,
                        onCheckedChange = {
                            onSystemVolumeChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onSystemVolumeChange(!useSystemVolume)
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("headphones"),
                title = { Text(stringResource(R.string.onboarding_autoeq_title)) },
                description = {
                    Text(
                        if (autoEQProfile.isNotBlank()) autoEQProfile else stringResource(R.string.onboarding_autoeq_desc)
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onOpenAutoEQSelector()
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("equalizer"),
                title = { Text(stringResource(R.string.onboarding_replaygain_title)) },
                description = { Text(stringResource(R.string.onboarding_replaygain_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = replayGain,
                        onCheckedChange = {
                            onReplayGainChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onReplayGainChange(!replayGain)
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("graphic_eq"),
                title = { Text(stringResource(R.string.onboarding_gapless_crossfade_title)) },
                description = { Text(stringResource(R.string.onboarding_gapless_crossfade_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = gaplessPlayback,
                        onCheckedChange = {
                            onGaplessChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onGaplessChange(!gaplessPlayback)
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("hearing"),
                title = { Text(context.getString(R.string.settings_skip_silence)) },
                description = {
                    Text(
                        if (isAudioOffloadActive) "Disabled while Audio Offload is active" else context.getString(R.string.settings_skip_silence_desc)
                    )
                },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = if (isAudioOffloadActive) false else skipSilenceEnabled,
                        onCheckedChange = {
                            if (!isAudioOffloadActive) {
                                onSkipSilenceChange(it)
                            }
                        }
                    )
                },
                onClick = {
                    if (!isAudioOffloadActive) {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onSkipSilenceChange(!skipSilenceEnabled)
                    }
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.Player.Stop,
                title = { Text(context.getString(R.string.settings_stop_playback_on_zero_volume)) },
                description = { Text(context.getString(R.string.settings_stop_playback_on_zero_volume_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = stopPlaybackOnZeroVolume,
                        onCheckedChange = {
                            onStopPlaybackOnZeroVolumeChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onStopPlaybackOnZeroVolumeChange(!stopPlaybackOnZeroVolume)
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.Devices.Bluetooth,
                title = { Text(context.getString(R.string.settings_resume_on_device_reconnect)) },
                description = { Text(context.getString(R.string.settings_resume_on_device_reconnect_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = resumeOnDeviceReconnect,
                        onCheckedChange = {
                            onResumeOnReconnectChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onResumeOnReconnectChange(!resumeOnDeviceReconnect)
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.Queue,
                title = { Text(context.getString(R.string.onboarding_auto_queue_title)) },
                description = { Text(context.getString(R.string.onboarding_auto_queue_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = autoAddToQueue,
                        onCheckedChange = {
                            onAutoQueueChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onAutoQueueChange(!autoAddToQueue)
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("lyrics", filled = true),
                title = { Text(context.getString(R.string.onboarding_show_lyrics_title)) },
                description = { Text(context.getString(R.string.onboarding_show_lyrics_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = showLyrics,
                        onCheckedChange = {
                            onShowLyricsChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onShowLyricsChange(!showLyrics)
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ThemeSettingsCard(
    useSystemTheme: Boolean,
    darkMode: Boolean,
    onSystemThemeChange: (Boolean) -> Unit,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val themeItems = buildList {
        add(
            Material3SettingsItem(
                icon = RhythmIcons.Settings,
                title = { Text(context.getString(R.string.settings_theme_mode)) },
                description = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.settings_theme_mode_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExpressiveButtonGroup(
                            items = listOf(
                                context.getString(R.string.settings_theme_system),
                                context.getString(R.string.settings_theme_light),
                                context.getString(R.string.settings_theme_dark)
                            ),
                            selectedIndex = when {
                                useSystemTheme -> 0
                                !darkMode -> 1
                                else -> 2
                            },
                            onItemClick = { index ->
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                when (index) {
                                    0 -> onSystemThemeChange(true)
                                    1 -> {
                                        onSystemThemeChange(false)
                                        onDarkModeChange(false)
                                    }
                                    2 -> {
                                        onSystemThemeChange(false)
                                        onDarkModeChange(true)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        )

    }

    Material3SettingsGroup(
        items = themeItems,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun LibraryFeatureCard(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    usePrimaryStyle: Boolean = false,
    useTertiaryStyle: Boolean = false
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                useTertiaryStyle -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                usePrimaryStyle -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    useTertiaryStyle -> MaterialTheme.colorScheme.onTertiaryContainer
                    usePrimaryStyle -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        useTertiaryStyle -> MaterialTheme.colorScheme.onTertiaryContainer
                        usePrimaryStyle -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        useTertiaryStyle -> MaterialTheme.colorScheme.onTertiaryContainer
                        usePrimaryStyle -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    lineHeight = 16.sp
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = RhythmIcons.Forward,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    tint = if (usePrimaryStyle)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EnhancedThemingContent(
    onNextStep: () -> Unit,
    themeViewModel: ThemeViewModel,
    appSettings: AppSettings,
    onSkip: () -> Unit = {},
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val useSystemTheme by themeViewModel.useSystemTheme.collectAsState()
    val darkMode by themeViewModel.darkMode.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    if (isTablet) {
        // Tablet layout: Left side - icon, title, description, tips, action buttons; Right side - toggles and cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, title, description, tips, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced icon with animation
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.Palette,
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_theme_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_theme_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Guide to Tuner settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.SettingsFilled,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_more_tuner),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        LibraryTipItem(
                            icon = RhythmIcons.Palette,
                            text = context.getString(R.string.onboarding_tuner_1)
                        )
                        LibraryTipItem(
                            icon = MaterialSymbolIcon("font_download", filled = true),
                            text = context.getString(R.string.onboarding_tuner_2)
                        )
                        LibraryTipItem(
                            icon = RhythmIcons.AutoAwesome,
                            text = context.getString(R.string.onboarding_tuner_3)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Preview card, toggles, and font selection
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                ThemeSettingsCard(
                    useSystemTheme = useSystemTheme,
                    darkMode = darkMode,
                    onSystemThemeChange = { enabled ->
                        scope.launch {
                            themeViewModel.setUseSystemTheme(enabled)
                        }
                    },
                    onDarkModeChange = { enabled ->
                        scope.launch {
                            themeViewModel.setDarkMode(enabled)
                        }
                    }
                )

                ThemingCustomizationSection(
                    appSettings = appSettings,
                    context = context
                )

                // Default Landing Screen dropdown
                SettingsDropdownItem(
                    title = context.getString(R.string.onboarding_default_screen_title),
                    description = context.getString(R.string.onboarding_default_screen_desc),
                    selectedOption = if (appSettings.defaultScreen.collectAsState().value == "library") context.getString(R.string.option_library) else context.getString(R.string.option_home),
                    icon = RhythmIcons.HomeFilled,
                    options = listOf(context.getString(R.string.option_home), context.getString(R.string.option_library)),
                    onOptionSelected = { selectedOption ->
                        val selectedScreen = if (selectedOption == context.getString(R.string.option_library)) {
                            "library"
                        } else {
                            "home"
                        }
                        appSettings.setDefaultScreen(selectedScreen)
                    }
                )
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Enhanced icon with animation
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn()
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.Palette,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_theme_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_theme_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )


            // Theme options - consolidated settings card
            ThemeSettingsCard(
                useSystemTheme = useSystemTheme,
                darkMode = darkMode,
                onSystemThemeChange = { enabled ->
                    scope.launch {
                        themeViewModel.setUseSystemTheme(enabled)
                    }
                },
                onDarkModeChange = { enabled ->
                    scope.launch {
                        themeViewModel.setDarkMode(enabled)
                    }
                }
            )

            ThemingCustomizationSection(
                appSettings = appSettings,
                context = context
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Default Landing Screen dropdown
            SettingsDropdownItem(
                title = context.getString(R.string.onboarding_default_screen_title),
                description = context.getString(R.string.onboarding_default_screen_desc),
                selectedOption = if (appSettings.defaultScreen.collectAsState().value == "library") context.getString(R.string.option_library) else context.getString(R.string.option_home),
                icon = RhythmIcons.HomeFilled,
                options = listOf(context.getString(R.string.option_home), context.getString(R.string.option_library)),
                onOptionSelected = { selectedOption ->
                    val selectedScreen = if (selectedOption == context.getString(R.string.option_library)) {
                        "library"
                    } else {
                        "home"
                    }
                    appSettings.setDefaultScreen(selectedScreen)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))


            Spacer(modifier = Modifier.height(16.dp))

            // Guide to Tuner settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RhythmIcons.SettingsFilled,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.onboarding_more_tuner),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    LibraryTipItem(
                        icon = RhythmIcons.Palette,
                        text = context.getString(R.string.onboarding_tuner_1)
                    )
                    LibraryTipItem(
                        icon = MaterialSymbolIcon("font_download", filled = true),
                        text = context.getString(R.string.onboarding_tuner_2)
                    )
                    LibraryTipItem(
                        icon = RhythmIcons.AutoAwesome,
                        text = context.getString(R.string.onboarding_tuner_3)
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedThemeOption(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                onToggle(!isEnabled)
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            OnboardingAnimatedSwitch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    onToggle(enabled)
                }
            )
        }
    }
}

@Composable
fun OnboardingDropdownOption(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                showDropdown = true
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Current selection badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = selectedOption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = RhythmIcons.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_show_options),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Dropdown Menu
        Box {
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                shape = RoundedCornerShape(12.dp)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = when (option) {
                                        "API" -> MaterialSymbolIcon("cloud", filled = true)
                                        "Embedded" -> RhythmIcons.MusicNote
                                        "Local" -> RhythmIcons.Folder
                                        else -> RhythmIcons.MusicNote
                                    },
                                    contentDescription = null,
                                    tint = if (selectedOption == option)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedOption == option) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedOption == option)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (selectedOption == option) {
                                    Icon(
                                        imageVector = RhythmIcons.Check,
                                        contentDescription = stringResource(R.string.streaming_selected),
                                        
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onOptionSelected(option)
                            showDropdown = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedRhythmGuardContent(
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val rhythmGuardMode by appSettings.rhythmGuardMode.collectAsState()
    val rhythmGuardAge by appSettings.rhythmGuardAge.collectAsState()
    val rhythmGuardEnabled = rhythmGuardMode != AppSettings.RHYTHM_GUARD_MODE_OFF

    fun setMode(mode: String) {
        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
        appSettings.setRhythmGuardMode(mode)
    }

    @Composable
    fun ModeSelectionCard() {
        val modeItems = buildList {
            add(
                Material3SettingsItem(
                    icon = RhythmIcons.Security,
                    title = { Text(context.getString(R.string.settings_rhythm_guard)) },
                    description = { Text(context.getString(R.string.settings_rhythm_guard_mode_desc)) },
                    trailingContent = {
                        OnboardingAnimatedSwitch(
                            checked = rhythmGuardEnabled,
                            onCheckedChange = { enabled ->
                                setMode(if (enabled) AppSettings.RHYTHM_GUARD_MODE_AUTO else AppSettings.RHYTHM_GUARD_MODE_OFF)
                            }
                        )
                    },
                    onClick = {
                        setMode(
                            if (rhythmGuardEnabled) AppSettings.RHYTHM_GUARD_MODE_OFF
                            else AppSettings.RHYTHM_GUARD_MODE_AUTO
                        )
                    }
                )
            )

            if (rhythmGuardEnabled) {
                add(
                    Material3SettingsItem(
                        icon = RhythmIcons.Tune,
                        title = { Text(context.getString(R.string.onboarding_rhythm_guard_mode_title)) },
                        description = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = context.getString(R.string.settings_rhythm_guard_mode_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ExpressiveButtonGroup(
                                    items = listOf(
                                        context.getString(R.string.settings_rhythm_guard_mode_auto),
                                        context.getString(R.string.settings_rhythm_guard_mode_manual)
                                    ),
                                    selectedIndex = if (rhythmGuardMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL) 1 else 0,
                                    onItemClick = { index ->
                                        when (index) {
                                            0 -> setMode(AppSettings.RHYTHM_GUARD_MODE_AUTO)
                                            else -> setMode(AppSettings.RHYTHM_GUARD_MODE_MANUAL)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    )
                )
            }

            add(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("cake", filled = true),
                    title = { Text(context.getString(R.string.settings_rhythm_guard_age_search_title)) },
                    description = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceHighest
                                ) {
                                    IconButton(onClick = { appSettings.setRhythmGuardAge((rhythmGuardAge - 1).coerceAtLeast(8)) }) {
                                        Icon(imageVector = RhythmIcons.Remove, contentDescription = null)
                                    }
                                }
                                Text(
                                    text = rhythmGuardAge.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceHighest
                                ) {
                                    IconButton(onClick = { appSettings.setRhythmGuardAge((rhythmGuardAge + 1).coerceAtMost(80)) }) {
                                        Icon(imageVector = RhythmIcons.Add, contentDescription = null)
                                    }
                                }
                            }
                            Text(
                                text = context.getString(R.string.onboarding_rhythm_guard_age_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            )
        }

        Material3SettingsGroup(
            items = modeItems,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.Security,
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_rhythm_guard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_rhythm_guard_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OnboardingTipItem(
                            icon = RhythmIcons.CheckCircle,
                            text = context.getString(R.string.onboarding_rhythm_guard_tip_1)
                        )
                        OnboardingTipItem(
                            icon = RhythmIcons.AccessTime,
                            text = context.getString(R.string.onboarding_rhythm_guard_tip_2)
                        )
                        OnboardingTipItem(
                            icon = RhythmIcons.Tune,
                            text = context.getString(R.string.onboarding_rhythm_guard_tip_3)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModeSelectionCard()
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.Security,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_rhythm_guard_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_rhythm_guard_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            ModeSelectionCard()

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OnboardingTipItem(
                        icon = RhythmIcons.CheckCircle,
                        text = context.getString(R.string.onboarding_rhythm_guard_tip_1)
                    )
                    OnboardingTipItem(
                        icon = RhythmIcons.AccessTime,
                        text = context.getString(R.string.onboarding_rhythm_guard_tip_2)
                    )
                    OnboardingTipItem(
                        icon = RhythmIcons.Tune,
                        text = context.getString(R.string.onboarding_rhythm_guard_tip_3)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun EnhancedFullTourPromptContent(
    onContinueFullTour: () -> Unit,
    onSkipFullTour: () -> Unit,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Icon, Title, Description, logo info, and back button
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.AutoAwesome,
                        tint = MaterialTheme.colorScheme.secondary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_full_tour_prompt_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_full_tour_prompt_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rhythm_splash_logo),
                        contentDescription = context.getString(R.string.updates_rhythm_logo_cd),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = context.getString(R.string.common_rhythm),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Right Column: Tips card and primary buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OnboardingTipItem(
                            icon = RhythmIcons.Tune,
                            text = context.getString(R.string.onboarding_full_tour_prompt_tip_1)
                        )
                        OnboardingTipItem(
                            icon = RhythmIcons.Library,
                            text = context.getString(R.string.onboarding_full_tour_prompt_tip_2)
                        )
                        OnboardingTipItem(
                            icon = RhythmIcons.Info,
                            text = context.getString(R.string.onboarding_full_tour_prompt_tip_3)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                        onContinueFullTour()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text(
                        text = context.getString(R.string.onboarding_continue_full_tour),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onSkipFullTour()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text(
                        text = context.getString(R.string.onboarding_finish_now),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = RhythmIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.AutoAwesome,
                    tint = MaterialTheme.colorScheme.secondary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_full_tour_prompt_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_full_tour_prompt_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OnboardingTipItem(
                        icon = RhythmIcons.Tune,
                        text = context.getString(R.string.onboarding_full_tour_prompt_tip_1)
                    )
                    OnboardingTipItem(
                        icon = RhythmIcons.Library,
                        text = context.getString(R.string.onboarding_full_tour_prompt_tip_2)
                    )
                    OnboardingTipItem(
                        icon = RhythmIcons.Info,
                        text = context.getString(R.string.onboarding_full_tour_prompt_tip_3)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.rhythm_splash_logo),
                    contentDescription = context.getString(R.string.updates_rhythm_logo_cd),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = context.getString(R.string.common_rhythm),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    onContinueFullTour()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Text(
                    text = context.getString(R.string.onboarding_continue_full_tour),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = RhythmIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onSkipFullTour()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Text(
                    text = context.getString(R.string.onboarding_finish_now),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = RhythmIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun EnhancedUpdaterContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    updaterViewModel: AppUpdaterViewModel = viewModel(),
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val autoCheckForUpdates by appSettings.autoCheckForUpdates.collectAsState()
    val updateNotificationsEnabled by appSettings.updateNotificationsEnabled.collectAsState()
    val updateChannel by appSettings.updateChannel.collectAsState()
    val updateCheckIntervalHours by appSettings.updateCheckIntervalHours.collectAsState()
    val updatesEnabled by appSettings.updatesEnabled.collectAsState()
    val scope = rememberCoroutineScope()

    // Collect updater states
    val isCheckingForUpdates by updaterViewModel.isCheckingForUpdates.collectAsState()
    val updateAvailable by updaterViewModel.updateAvailable.collectAsState()
    val latestVersion by updaterViewModel.latestVersion.collectAsState()
    val currentVersion by updaterViewModel.currentVersion.collectAsState()
    val isDownloading by updaterViewModel.isDownloading.collectAsState()
    val downloadProgress by updaterViewModel.downloadProgress.collectAsState()
    val downloadedFile by updaterViewModel.downloadedFile.collectAsState()
    val error by updaterViewModel.error.collectAsState()

    var showFdroidWarningDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Infinite transition for continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "update_animations")

    // Rotating icon for checking state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing glow animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Success scale animation
    val successScale = remember { Animatable(0.7f) }
    LaunchedEffect(downloadedFile) {
        if (downloadedFile != null) {
            successScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    if (isTablet) {
        // Tablet layout: Left side - icon, title, description, update actions, action buttons; Right side - toggles and dropdowns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, title, description, update actions, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced icon with animation - shows status
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = when {
                            error != null -> RhythmIcons.BugReport
                            downloadedFile != null -> RhythmIcons.CheckCircle
                            updateAvailable -> RhythmIcons.Download
                            isDownloading -> MaterialSymbolIcon("autorenew", filled = true)
                            else -> RhythmIcons.SystemUpdate
                        },
                        tint = when {
                            error != null -> MaterialTheme.colorScheme.error
                            downloadedFile != null -> MaterialTheme.colorScheme.tertiary
                            updateAvailable -> MaterialTheme.colorScheme.primary
                            isDownloading -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        iconSize = 72.dp
                    )
                }

                // Title shows status
                Text(
                    text = when {
                        error != null -> context.getString(R.string.onboarding_update_check_failed)
                        downloadedFile != null -> context.getString(R.string.onboarding_ready_to_install)
                        isDownloading -> context.getString(R.string.onboarding_downloading_update)
                        isCheckingForUpdates -> context.getString(R.string.onboarding_checking_updates)
                        updateAvailable -> context.getString(R.string.onboarding_update_available)
                        else -> context.getString(R.string.onboarding_stay_up_to_date)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        error != null -> MaterialTheme.colorScheme.error
                        downloadedFile != null -> MaterialTheme.colorScheme.tertiary
                        updateAvailable -> MaterialTheme.colorScheme.primary
                        isCheckingForUpdates || isDownloading -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isCheckingForUpdates) {
                    M3LinearLoader(
                        progress = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Description shows version info or default text
                Text(
                    text = when {
                        error != null -> error ?: context.getString(R.string.updater_error_occurred)
                        downloadedFile != null -> context.getString(R.string.updater_ready_to_install, latestVersion?.versionName ?: "?")
                        isDownloading -> context.getString(R.string.updater_download_progress, downloadProgress.toInt(), ((latestVersion?.apkSize ?: 0) * downloadProgress / 100).toLong().let { updaterViewModel.getReadableFileSize(it) }, latestVersion?.let { updaterViewModel.getReadableFileSize(it.apkSize) } ?: "")
                        isCheckingForUpdates -> context.getString(R.string.fetching_latest_version)
                        updateAvailable -> context.getString(R.string.updater_version_info, latestVersion?.versionName ?: "?", latestVersion?.let { updaterViewModel.getReadableFileSize(it.apkSize) } ?: "")
                        else -> context.getString(R.string.onboarding_update_default_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        error != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        downloadedFile != null -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                        updateAvailable -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        isCheckingForUpdates || isDownloading -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Update Actions UI - Only buttons and progress
                val showUpdateActions = isDownloading || updateAvailable || downloadedFile != null || error != null

                AnimatedVisibility(
                    visible = showUpdateActions,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    OnboardingExpressiveUpdateStatus(
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        downloadedFile = downloadedFile,
                        error = error,
                        updateAvailable = updateAvailable,
                        latestVersion = latestVersion,
                        updaterViewModel = updaterViewModel,
                        successScale = successScale,
                        onDownload = { updaterViewModel.downloadUpdate() },
                        onInstall = { updaterViewModel.installDownloadedApk() },
                        onCancelDownload = { updaterViewModel.cancelDownload() },
                        onDismissError = { updaterViewModel.clearError() },
                        onRetry = { updaterViewModel.checkForUpdates(force = true) }
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Update options (toggles and dropdowns)
            val haptic = LocalHapticFeedback.current
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Material3SettingsGroup(
                    items = listOf(
                        Material3SettingsItem(
                            icon = RhythmIcons.SystemUpdate,
                            title = { Text(context.getString(R.string.onboarding_enable_updates_title)) },
                            description = { Text(context.getString(R.string.onboarding_enable_updates_desc)) },
                            trailingContent = {
                                OnboardingAnimatedSwitch(
                                    checked = updatesEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            if (enabled) {
                                                if (chromahub.rhythm.app.BuildConfig.FLAVOR == "fdroid") {
                                                    showFdroidWarningDialog = true
                                                } else {
                                                    appSettings.setUpdatesEnabled(true)
                                                }
                                            } else {
                                                appSettings.setUpdatesEnabled(false)
                                            }
                                        }
                                    }
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                scope.launch {
                                    val target = !updatesEnabled
                                    if (target) {
                                        if (chromahub.rhythm.app.BuildConfig.FLAVOR == "fdroid") {
                                            showFdroidWarningDialog = true
                                        } else {
                                            appSettings.setUpdatesEnabled(true)
                                        }
                                    } else {
                                        appSettings.setUpdatesEnabled(false)
                                    }
                                }
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surface
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = updatesEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Material3SettingsGroup(
                            items = listOf(
                                Material3SettingsItem(
                                    icon = when (updateChannel) {
                                        "stable" -> RhythmIcons.Public
                                        "beta" -> RhythmIcons.BugReport
                                        else -> RhythmIcons.Public
                                    },
                                    title = { Text(context.getString(R.string.onboarding_update_channel_title)) },
                                    description = {
                                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            Text(
                                                text = context.getString(R.string.onboarding_update_channel_desc),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            ExpressiveButtonGroup(
                                                items = listOf(
                                                    context.getString(R.string.option_stable),
                                                    context.getString(R.string.option_beta)
                                                ),
                                                selectedIndex = if (updateChannel == "beta") 1 else 0,
                                                onItemClick = { index ->
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    scope.launch { appSettings.setUpdateChannel(if (index == 0) "stable" else "beta") }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                ),
                                Material3SettingsItem(
                                    icon = MaterialSymbolIcon("autorenew", filled = true),
                                    title = { Text(context.getString(R.string.onboarding_periodic_check_title)) },
                                    description = { Text(context.getString(R.string.onboarding_periodic_check_desc)) },
                                    trailingContent = {
                                        OnboardingAnimatedSwitch(
                                            checked = autoCheckForUpdates,
                                            onCheckedChange = { enabled ->
                                                scope.launch { appSettings.setAutoCheckForUpdates(enabled) }
                                            }
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        scope.launch { appSettings.setAutoCheckForUpdates(!autoCheckForUpdates) }
                                    }
                                ),
                                Material3SettingsItem(
                                    icon = RhythmIcons.Notifications,
                                    title = { Text(context.getString(R.string.onboarding_update_notifications_title)) },
                                    description = { Text(context.getString(R.string.onboarding_update_notifications_desc)) },
                                    trailingContent = {
                                        OnboardingAnimatedSwitch(
                                            checked = updateNotificationsEnabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch { appSettings.setUpdateNotificationsEnabled(enabled) }
                                            }
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        scope.launch { appSettings.setUpdateNotificationsEnabled(!updateNotificationsEnabled) }
                                    }
                                 )
                            ),
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Enhanced icon with animation - shows status
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn()
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = when {
                        error != null -> RhythmIcons.BugReport
                        downloadedFile != null -> RhythmIcons.CheckCircle
                        updateAvailable -> RhythmIcons.Download
                        isDownloading -> MaterialSymbolIcon("autorenew", filled = true)
                        else -> RhythmIcons.SystemUpdate
                    },
                    tint = when {
                        error != null -> MaterialTheme.colorScheme.error
                        downloadedFile != null -> MaterialTheme.colorScheme.tertiary
                        updateAvailable -> MaterialTheme.colorScheme.primary
                        isDownloading -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title shows status
            Text(
                text = when {
                    error != null -> context.getString(R.string.onboarding_update_check_failed)
                    downloadedFile != null -> context.getString(R.string.onboarding_ready_to_install)
                    isDownloading -> context.getString(R.string.onboarding_downloading_update)
                    isCheckingForUpdates -> context.getString(R.string.onboarding_checking_updates)
                    updateAvailable -> context.getString(R.string.onboarding_update_available)
                    else -> context.getString(R.string.onboarding_stay_up_to_date)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    error != null -> MaterialTheme.colorScheme.error
                    downloadedFile != null -> MaterialTheme.colorScheme.tertiary
                    updateAvailable -> MaterialTheme.colorScheme.primary
                    isCheckingForUpdates || isDownloading -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Description shows version info or default text
            Text(
                text = when {
                    error != null -> error ?: context.getString(R.string.updater_error_occurred)
                    downloadedFile != null -> context.getString(R.string.updater_ready_to_install, latestVersion?.versionName ?: "?")
                    isDownloading -> context.getString(R.string.updater_download_progress, downloadProgress.toInt(), ((latestVersion?.apkSize ?: 0) * downloadProgress / 100).toLong().let { updaterViewModel.getReadableFileSize(it) }, latestVersion?.let { updaterViewModel.getReadableFileSize(it.apkSize) } ?: "")
                    isCheckingForUpdates -> context.getString(R.string.fetching_latest_version)
                    updateAvailable -> context.getString(R.string.updater_version_info, latestVersion?.versionName ?: "?", latestVersion?.let { updaterViewModel.getReadableFileSize(it.apkSize) } ?: "")
                    else -> context.getString(R.string.onboarding_update_default_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    error != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    downloadedFile != null -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                    updateAvailable -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    isCheckingForUpdates || isDownloading -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (isCheckingForUpdates) {
                M3LinearLoader(
                    progress = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Update Actions UI - Only buttons and progress
            val showUpdateActions = isDownloading || updateAvailable || downloadedFile != null || error != null

            AnimatedVisibility(
                visible = showUpdateActions,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn() + scaleIn(initialScale = 0.9f),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                OnboardingExpressiveUpdateStatus(
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    downloadedFile = downloadedFile,
                    error = error,
                    updateAvailable = updateAvailable,
                    latestVersion = latestVersion,
                    updaterViewModel = updaterViewModel,
                    successScale = successScale,
                    onDownload = { updaterViewModel.downloadUpdate() },
                    onInstall = { updaterViewModel.installDownloadedApk() },
                    onCancelDownload = { updaterViewModel.cancelDownload() },
                    onDismissError = { updaterViewModel.clearError() },
                    onRetry = { updaterViewModel.checkForUpdates(force = true) }
                )
            }

            // Update options
            val haptic = LocalHapticFeedback.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Material3SettingsGroup(
                    items = listOf(
                        Material3SettingsItem(
                            icon = RhythmIcons.SystemUpdate,
                            title = { Text(context.getString(R.string.onboarding_enable_updates_title)) },
                            description = { Text(context.getString(R.string.onboarding_enable_updates_desc)) },
                            trailingContent = {
                                OnboardingAnimatedSwitch(
                                    checked = updatesEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            if (enabled) {
                                                if (chromahub.rhythm.app.BuildConfig.FLAVOR == "fdroid") {
                                                    showFdroidWarningDialog = true
                                                } else {
                                                    appSettings.setUpdatesEnabled(true)
                                                }
                                            } else {
                                                appSettings.setUpdatesEnabled(false)
                                            }
                                        }
                                    }
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                scope.launch {
                                    val target = !updatesEnabled
                                    if (target) {
                                        if (chromahub.rhythm.app.BuildConfig.FLAVOR == "fdroid") {
                                            showFdroidWarningDialog = true
                                        } else {
                                            appSettings.setUpdatesEnabled(true)
                                        }
                                    } else {
                                        appSettings.setUpdatesEnabled(false)
                                    }
                                }
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surface
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = updatesEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Material3SettingsGroup(
                            items = listOf(
                                Material3SettingsItem(
                                    icon = when (updateChannel) {
                                        "stable" -> RhythmIcons.Public
                                        "beta" -> RhythmIcons.BugReport
                                        else -> RhythmIcons.Public
                                    },
                                    title = { Text(context.getString(R.string.onboarding_update_channel_title)) },
                                    description = {
                                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            Text(
                                                text = context.getString(R.string.onboarding_update_channel_desc),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            ExpressiveButtonGroup(
                                                items = listOf(
                                                    context.getString(R.string.option_stable),
                                                    context.getString(R.string.option_beta)
                                                ),
                                                selectedIndex = if (updateChannel == "beta") 1 else 0,
                                                onItemClick = { index ->
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    scope.launch { appSettings.setUpdateChannel(if (index == 0) "stable" else "beta") }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                ),
                                Material3SettingsItem(
                                    icon = MaterialSymbolIcon("autorenew", filled = true),
                                    title = { Text(context.getString(R.string.onboarding_periodic_check_title)) },
                                    description = { Text(context.getString(R.string.onboarding_periodic_check_desc)) },
                                    trailingContent = {
                                        OnboardingAnimatedSwitch(
                                            checked = autoCheckForUpdates,
                                            onCheckedChange = { enabled ->
                                                scope.launch { appSettings.setAutoCheckForUpdates(enabled) }
                                            }
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        scope.launch { appSettings.setAutoCheckForUpdates(!autoCheckForUpdates) }
                                    }
                                ),
                                Material3SettingsItem(
                                    icon = RhythmIcons.Notifications,
                                    title = { Text(context.getString(R.string.onboarding_update_notifications_title)) },
                                    description = { Text(context.getString(R.string.onboarding_update_notifications_desc)) },
                                    trailingContent = {
                                        OnboardingAnimatedSwitch(
                                            checked = updateNotificationsEnabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch { appSettings.setUpdateNotificationsEnabled(enabled) }
                                            }
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        scope.launch { appSettings.setUpdateNotificationsEnabled(!updateNotificationsEnabled) }
                                    }
                                 )
                            ),
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }

    if (showFdroidWarningDialog) {
        FdroidUpdateWarningDialog(
            onDismiss = { showFdroidWarningDialog = false },
            onConfirm = {
                scope.launch {
                    appSettings.setUpdatesEnabled(true)
                }
            }
        )
    }
}

@Composable
fun EnhancedUpdateOption(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                onToggle(!isEnabled)
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            OnboardingAnimatedSwitch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    onToggle(enabled)
                }
            )
        }
    }
}

@Composable
fun EnhancedUpdateChannelOption(
    channel: String,
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isSelected) {
                    Icon(
                        imageVector = RhythmIcons.Check,
                        contentDescription = stringResource(R.string.streaming_selected),
                        
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        onClick = onSelect
    )
}

@Composable
fun SettingsDropdownItem(
    title: String,
    description: String,
    selectedOption: String,
    icon: MaterialSymbolIcon,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var showDropdown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                showDropdown = true
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Selected option badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = selectedOption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = RhythmIcons.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_show_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // Enhanced Dropdown Menu
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(4.dp)
        ) {
            options.forEach { option ->
                Surface(
                    color = if (selectedOption == option)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else
                        androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedOption == option) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedOption == option)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when {
                                    option.contains("Track Number") -> RhythmIcons.FormatListNumbered
                                    option.contains("Title A-Z") || option.contains("Title Z-A") -> RhythmIcons.SortByAlpha
                                    option.contains("Duration") -> RhythmIcons.AccessTime
                                    option.contains("List") -> RhythmIcons.Actions.List
                                    option.contains("Grid") -> RhythmIcons.GridView
                                    option.contains("Hour") -> RhythmIcons.AccessTime
                                    option.contains("Stable") -> RhythmIcons.Public
                                    option.contains("Beta") -> RhythmIcons.BugReport
                                    option == "Home" -> RhythmIcons.HomeFilled
                                    option == "Library" -> RhythmIcons.Library
                                    option == "API" -> MaterialSymbolIcon("cloud", filled = true)
                                    option == "Embedded" -> RhythmIcons.Library
                                    option == "Local" -> RhythmIcons.Folder
                                    else -> RhythmIcons.Check // Fallback
                                },
                                contentDescription = null,
                                tint = if (selectedOption == option)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            onOptionSelected(option)
                            showDropdown = false
                        },
                        colors = androidx.compose.material3.MenuDefaults.itemColors(
                            textColor = if (selectedOption == option)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Step indicator dots with enhanced animations
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                val isCompleted = index < currentStep
                val isCurrent = index == currentStep

                // Animated dot size and color
                val dotSize by animateDpAsState(
                    targetValue = when {
                        isCurrent -> 14.dp
                        isCompleted -> 10.dp
                        else -> 8.dp
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "dotSize_$index"
                )

                val dotColor by animateColorAsState(
                    targetValue = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    animationSpec = tween(300),
                    label = "dotColor_$index"
                )

                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Show checkmark for completed steps
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCompleted && !isCurrent,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Check,
                            contentDescription = stringResource(R.string.cd_completed),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(6.dp)
                        )
                    }

                    // Pulsing ring for current step
                    if (isCurrent) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_$index")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.4f,
                            animationSpec = infiniteRepeatable<Float>(
                                animation = tween(1000),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale_$index"
                        )

                        Box(
                            modifier = Modifier
                                .size(dotSize * pulseScale)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Animated progress text with smooth transitions
        androidx.compose.animation.AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (slideInVertically { height -> height / 2 } + fadeIn()).togetherWith(
                    slideOutVertically { height -> -height / 2 } + fadeOut()
                )
            },
            label = "progressText"
        ) { step ->
            Text(
                text = stringResource(R.string.onboarding_step_counter, step + 1, totalSteps),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EnhancedMediaScanContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    onSkip: () -> Unit = {},
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Get current media scan mode preference
    val mediaScanMode by appSettings.mediaScanMode.collectAsState()
    val isBlacklistMode = mediaScanMode == MediaScanMode.BLACKLIST
    val blacklistedFolders by appSettings.blacklistedFolders.collectAsState()
    val whitelistedFolders by appSettings.whitelistedFolders.collectAsState()

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val split = docId.split(":")

                    if (split.size >= 2) {
                        val storageType = split[0]
                        val relativePath = split[1]

                        val fullPath = when (storageType) {
                            "primary" -> "/storage/emulated/0/$relativePath"
                            "home" -> "/storage/emulated/0/$relativePath"
                            else -> {
                                if (storageType.contains("-")) {
                                    "/storage/$storageType/$relativePath"
                                } else {
                                    "/storage/emulated/0/$relativePath"
                                }
                            }
                        }

                        if (isBlacklistMode) {
                            appSettings.addFolderToBlacklist(fullPath)
                        } else {
                            appSettings.addFolderToWhitelist(fullPath)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OnboardingMediaScan", "Error parsing folder path", e)
                }
            }
        }
    }

    if (isTablet) {
        // Tablet layout: Left side - icon, description, media scan tips, action buttons; Right side - storage info and configuration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, description, media scan tips, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced icon with animation
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn()
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.FilterList,
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_media_scan_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_media_scan_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Media scan tips card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_how_it_works),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        MediaScanTipItem(
                            icon = RhythmIcons.Block,
                            text = context.getString(R.string.onboarding_media_scan_blacklist)
                        )
                        MediaScanTipItem(
                            icon = RhythmIcons.CheckCircle,
                            text = context.getString(R.string.onboarding_media_scan_whitelist)
                        )
                        MediaScanTipItem(
                            icon = RhythmIcons.SettingsFilled,
                            text = context.getString(R.string.onboarding_media_scan_configure_in_tuner)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Scan mode settings and folder management
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FolderManagementCard(
                    isBlacklistMode = isBlacklistMode,
                    blacklistedFolders = blacklistedFolders,
                    whitelistedFolders = whitelistedFolders,
                    onModeChange = { useBlacklist ->
                        appSettings.setMediaScanMode(if (useBlacklist) MediaScanMode.BLACKLIST else MediaScanMode.WHITELIST)
                    },
                    onAddFolder = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        try {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            folderPickerLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.error_no_document_app), Toast.LENGTH_LONG).show()
                        }
                    },
                    onRemoveFolder = { folder ->
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (isBlacklistMode) {
                            appSettings.removeFolderFromBlacklist(folder)
                        } else {
                            appSettings.removeFolderFromWhitelist(folder)
                        }
                    }
                )
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Enhanced icon with animation
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn()
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.FilterList,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_media_scan_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_media_scan_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            FolderManagementCard(
                isBlacklistMode = isBlacklistMode,
                blacklistedFolders = blacklistedFolders,
                whitelistedFolders = whitelistedFolders,
                onModeChange = { useBlacklist ->
                    appSettings.setMediaScanMode(if (useBlacklist) MediaScanMode.BLACKLIST else MediaScanMode.WHITELIST)
                },
                onAddFolder = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        folderPickerLauncher.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, context.getString(R.string.error_no_document_app), Toast.LENGTH_LONG).show()
                    }
                },
                onRemoveFolder = { folder ->
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    if (isBlacklistMode) {
                        appSettings.removeFolderFromBlacklist(folder)
                    } else {
                        appSettings.removeFolderFromWhitelist(folder)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Media scan tips card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Info,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.onboarding_how_it_works),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    MediaScanTipItem(
                        icon = RhythmIcons.Block,
                        text = context.getString(R.string.onboarding_media_scan_blacklist)
                    )
                    MediaScanTipItem(
                        icon = RhythmIcons.CheckCircle,
                        text = context.getString(R.string.onboarding_media_scan_whitelist)
                    )
                    MediaScanTipItem(
                        icon = RhythmIcons.SettingsFilled,
                        text = context.getString(R.string.onboarding_media_scan_configure_in_tuner)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaScanSettingsCard(
    isBlacklistMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = RhythmIcons.Block,
                title = { Text(context.getString(R.string.settings_blacklist_mode)) },
                description = { Text(context.getString(R.string.settings_blacklist_mode_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = isBlacklistMode,
                        onCheckedChange = {
                            onModeChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onModeChange(!isBlacklistMode)
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.CheckCircle,
                title = { Text(context.getString(R.string.settings_whitelist_mode)) },
                description = { Text(context.getString(R.string.settings_whitelist_mode_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = !isBlacklistMode,
                        onCheckedChange = {
                            onModeChange(!it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onModeChange(!isBlacklistMode)
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun MediaScanTipItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun MediaScanModeOption(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    example: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Animated scale for press effect
    val cardScale = remember { Animatable(1f) }

    // Animated colors
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "containerColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                scope.launch {
                    cardScale.animateTo(0.95f, tween(100))
                    cardScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                onSelect()
            },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(if (isSelected) 3.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (isSelected) {
                Icon(
                    imageVector = RhythmIcons.CheckCircle,
                    contentDescription = stringResource(R.string.streaming_selected),
                    
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EnhancedSetupFinishedContent(
    onFinish: () -> Unit,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    if (isTablet) {
        // Tablet layout: Left side - icon, description, next steps, action buttons; Right side - feature highlights and reminder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Icon, description, next steps, and action buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success icon with animation
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(
                        animationSpec = tween(1000)
                    )
                ) {
                    OnboardingStepHeaderIcon(
                        imageVector = RhythmIcons.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_complete_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_complete_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Next steps card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.onboarding_whats_next),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        NextStepItem(
                            icon = RhythmIcons.Library,
                            text = context.getString(R.string.onboarding_next_browse)
                        )
                        NextStepItem(
                            icon = RhythmIcons.Queue,
                            text = context.getString(R.string.onboarding_next_create)
                        )
                        NextStepItem(
                            icon = MaterialSymbolIcon("graphic_eq", filled = true),
                            text = context.getString(R.string.onboarding_next_finetune)
                        )
                        NextStepItem(
                            icon = RhythmIcons.SettingsFilled,
                            text = context.getString(R.string.onboarding_next_explore)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right side: Feature highlights and reminder
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Feature highlights
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Material3SettingsGroup(
                        items = listOf(
                            Material3SettingsItem(
                                icon = RhythmIcons.Library,
                                title = { Text(context.getString(R.string.onboarding_library_configured)) },
                                description = { Text(context.getString(R.string.onboarding_library_configured_desc)) },
                                trailingContent = {
                                    Icon(
                                        imageVector = RhythmIcons.Check,
                                        contentDescription = context.getString(R.string.onboarding_complete_title)
                                    )
                                }
                            ),
                            Material3SettingsItem(
                                icon = RhythmIcons.Palette,
                                title = { Text(context.getString(R.string.onboarding_theme_applied)) },
                                description = { Text(context.getString(R.string.onboarding_theme_applied_desc)) },
                                trailingContent = {
                                    Icon(
                                        imageVector = RhythmIcons.Check,
                                        contentDescription = context.getString(R.string.onboarding_complete_title)
                                    )
                                }
                            ),
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("backup", filled = true),
                                title = { Text(context.getString(R.string.onboarding_backup_options)) },
                                description = { Text(context.getString(R.string.onboarding_backup_options_desc)) },
                                trailingContent = {
                                    Icon(
                                        imageVector = RhythmIcons.Check,
                                        contentDescription = context.getString(R.string.onboarding_complete_title)
                                    )
                                }
                            )
                        ),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                // Reminder text
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = context.getString(R.string.onboarding_settings_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Success icon with animation
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(
                    animationSpec = tween(1000)
                )
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_complete_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_complete_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Feature highlights - vertically centered
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                Material3SettingsGroup(
                    items = listOf(
                        Material3SettingsItem(
                            icon = RhythmIcons.Library,
                            title = { Text(context.getString(R.string.onboarding_library_configured)) },
                            description = { Text(context.getString(R.string.onboarding_library_configured_desc)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = context.getString(R.string.onboarding_complete_title)
                                )
                            }
                        ),
                        Material3SettingsItem(
                            icon = RhythmIcons.Palette,
                            title = { Text(context.getString(R.string.onboarding_theme_applied)) },
                            description = { Text(context.getString(R.string.onboarding_theme_applied_desc)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = context.getString(R.string.onboarding_complete_title)
                                )
                            }
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("backup", filled = true),
                            title = { Text(context.getString(R.string.onboarding_backup_options)) },
                            description = { Text(context.getString(R.string.onboarding_backup_options_desc)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = context.getString(R.string.onboarding_complete_title)
                                )
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Next steps card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = null,
                            
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.onboarding_whats_next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    NextStepItem(
                        icon = RhythmIcons.Library,
                        text = context.getString(R.string.onboarding_next_browse)
                    )
                    NextStepItem(
                        icon = RhythmIcons.Queue,
                        text = context.getString(R.string.onboarding_next_create)
                    )
                    NextStepItem(
                        icon = MaterialSymbolIcon("graphic_eq", filled = true),
                        text = context.getString(R.string.onboarding_next_finetune)
                    )
                    NextStepItem(
                        icon = RhythmIcons.SettingsFilled,
                        text = context.getString(R.string.onboarding_next_explore)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Reminder text
            Text(
                text = context.getString(R.string.onboarding_settings_change),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NextStepItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Material 3 Expressive Update Actions UI - Shows only buttons and progress
 * Simplified to display action buttons and download progress, status is shown in main heading
 */
@Composable
private fun OnboardingExpressiveUpdateStatus(
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedFile: java.io.File?,
    error: String?,
    updateAvailable: Boolean,
    latestVersion: AppVersion?,
    updaterViewModel: AppUpdaterViewModel,
    successScale: Animatable<Float, AnimationVector1D>,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissError: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    // Main Column - NO BOX OR CARD WRAPPING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Download progress section - expressive, no containers
        AnimatedVisibility(
            visible = isDownloading,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Progress header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InitializationLoader(
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = context.getString(R.string.onboarding_in_progress),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                }

                // Plain accent color progress bar using Canvas - no Box container
                val accentColor = MaterialTheme.colorScheme.primary
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    val cornerRadius = 8.dp.toPx()
                    val progressWidth = size.width * (downloadProgress / 100f)

                    // Background track
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                    )

                    // Plain accent progress
                    if (progressWidth > 0) {
                        drawRoundRect(
                            color = accentColor,
                            size = androidx.compose.ui.geometry.Size(progressWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                        )
                    }
                }
            }
        }

        // Action buttons - expressive, no containers
        AnimatedVisibility(
            visible = error != null || downloadedFile != null || updateAvailable || isDownloading,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn() + scaleIn(initialScale = 0.9f),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    error != null -> {
                        OutlinedButton(
                            onClick = onDismissError,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = context.getString(R.string.onboarding_dismiss),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.onboarding_retry),
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    downloadedFile != null -> {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(successScale.value),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 12.dp
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.install_update_now),
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    isDownloading -> {
                        OutlinedButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Block,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = context.getString(R.string.cancel_download),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    updateAvailable && latestVersion?.apkAssetName?.isNotEmpty() == true -> {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 12.dp
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = context.getString(R.string.download_update),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = latestVersion.let { updaterViewModel.getReadableFileSize(it.apkSize) },
                                    fontWeight = FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subtle gradient divider - no Spacer container
        AnimatedVisibility(
            visible = isDownloading || updateAvailable || downloadedFile != null || error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                )
            }
        }
    }
}

// =====================================================
// PLAYER & MINIPLAYER THEMES ONBOARDING STEP
// =====================================================

@Composable
fun EnhancedPlayerThemeChoiceContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedViewIndex by remember { mutableIntStateOf(0) } // 0 = Player, 1 = Mini Player

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Icon, Title, Description, Switcher Tabs, Navigation Buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OnboardingStepHeaderIcon(
                    imageVector = RhythmIcons.Palette,
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 72.dp
                )

                Text(
                    text = context.getString(R.string.onboarding_player_theme_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_player_theme_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Tab Switcher between Player and Miniplayer
                ExpressiveButtonGroup(
                    items = listOf(
                        context.getString(R.string.settings_player),
                        context.getString(R.string.settings_miniplayer)
                    ),
                    selectedIndex = selectedViewIndex,
                    onItemClick = { index ->
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        selectedViewIndex = index
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right Column: The Preview Card & Selector
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlayerThemeSettingsSection(
                    isPlayer = selectedViewIndex == 0,
                    appSettings = appSettings,
                    context = context
                )
            }
        }
    } else {
        // Mobile layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            OnboardingStepHeaderIcon(
                imageVector = RhythmIcons.Palette,
                tint = MaterialTheme.colorScheme.primary,
                iconSize = 56.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_player_theme_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_player_theme_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Switcher Tabs for Player vs Miniplayer
            ExpressiveButtonGroup(
                items = listOf(
                    context.getString(R.string.settings_player),
                    context.getString(R.string.settings_miniplayer)
                ),
                selectedIndex = selectedViewIndex,
                onItemClick = { index ->
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    selectedViewIndex = index
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            PlayerThemeSettingsSection(
                isPlayer = selectedViewIndex == 0,
                appSettings = appSettings,
                context = context
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom navigation buttons removed on mobile view as they are rendered globally by OnboardingScreen bottom nav bar.
        }
    }
}


// =====================================================
// GESTURES ONBOARDING STEP
// =====================================================

@Composable
fun EnhancedGesturesContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Gesture settings
    val miniPlayerSwipeGestures by appSettings.miniPlayerSwipeGestures.collectAsState()
    val gesturePlayerSwipeDismiss by appSettings.gesturePlayerSwipeDismiss.collectAsState()
    val gesturePlayerSwipeTracks by appSettings.gesturePlayerSwipeTracks.collectAsState()
    val gestureArtworkDoubleTap by appSettings.gestureArtworkDoubleTap.collectAsState()
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("gesture", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_gestures_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_gestures_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                GestureTipsCard()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GestureSettingsCards(
                    miniPlayerSwipeGestures = miniPlayerSwipeGestures,
                    gesturePlayerSwipeDismiss = gesturePlayerSwipeDismiss,
                    gesturePlayerSwipeTracks = gesturePlayerSwipeTracks,
                    gestureArtworkDoubleTap = gestureArtworkDoubleTap,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    onMiniPlayerSwipeChange = { appSettings.setMiniPlayerSwipeGestures(it) },
                    onSwipeDismissChange = { appSettings.setGesturePlayerSwipeDismiss(it) },
                    onSwipeTracksChange = { appSettings.setGesturePlayerSwipeTracks(it) },
                    onDoubleTapChange = { appSettings.setGestureArtworkDoubleTap(it) },
                    onHapticFeedbackChange = { appSettings.setHapticFeedbackEnabled(it) }
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = MaterialSymbolIcon("gesture", filled = true),
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_gestures_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_gestures_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            GestureSettingsCards(
                miniPlayerSwipeGestures = miniPlayerSwipeGestures,
                gesturePlayerSwipeDismiss = gesturePlayerSwipeDismiss,
                gesturePlayerSwipeTracks = gesturePlayerSwipeTracks,
                gestureArtworkDoubleTap = gestureArtworkDoubleTap,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                onMiniPlayerSwipeChange = { appSettings.setMiniPlayerSwipeGestures(it) },
                onSwipeDismissChange = { appSettings.setGesturePlayerSwipeDismiss(it) },
                onSwipeTracksChange = { appSettings.setGesturePlayerSwipeTracks(it) },
                onDoubleTapChange = { appSettings.setGestureArtworkDoubleTap(it) },
                onHapticFeedbackChange = { appSettings.setHapticFeedbackEnabled(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            GestureTipsCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GestureSettingsCards(
    miniPlayerSwipeGestures: Boolean,
    gesturePlayerSwipeDismiss: Boolean,
    gesturePlayerSwipeTracks: Boolean,
    gestureArtworkDoubleTap: Boolean,
    hapticFeedbackEnabled: Boolean,
    onMiniPlayerSwipeChange: (Boolean) -> Unit,
    onSwipeDismissChange: (Boolean) -> Unit,
    onSwipeTracksChange: (Boolean) -> Unit,
    onDoubleTapChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val onboardingToggleItem: (MaterialSymbolIcon, String, String, Boolean, (Boolean) -> Unit) -> Material3SettingsItem =
        { icon, title, description, isEnabled, onToggle ->
            Material3SettingsItem(
                icon = icon,
                title = { Text(title) },
                description = { Text(description) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = isEnabled,
                        onCheckedChange = {
                            onToggle(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onToggle(!isEnabled)
                }
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // General Interaction Settings
        Text(
            text = stringResource(R.string.onboardingscreen_interaction),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        
        Material3SettingsGroup(
            items = listOf(
                onboardingToggleItem(
                    MaterialSymbolIcon("touch_app", filled = true),
                    "Haptic Feedback",
                    "Enable vibration feedback for interactions",
                    hapticFeedbackEnabled,
                    onHapticFeedbackChange
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mini Player Section
        Text(
            text = context.getString(R.string.onboarding_gestures_miniplayer),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        
        Material3SettingsGroup(
            items = listOf(
                onboardingToggleItem(
                    MaterialSymbolIcon("swipe", filled = true),
                    context.getString(R.string.onboarding_gesture_swipe),
                    context.getString(R.string.onboarding_gesture_swipe_desc),
                    miniPlayerSwipeGestures,
                    onMiniPlayerSwipeChange
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Full Player Section
        Text(
            text = context.getString(R.string.onboarding_gestures_player),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )

        Material3SettingsGroup(
            items = listOf(
                onboardingToggleItem(
                    MaterialSymbolIcon("swipe_down", filled = true),
                    context.getString(R.string.onboarding_gesture_dismiss),
                    context.getString(R.string.onboarding_gesture_dismiss_desc),
                    gesturePlayerSwipeDismiss,
                    onSwipeDismissChange
                ),
                onboardingToggleItem(
                    MaterialSymbolIcon("swipe_left", filled = true),
                    context.getString(R.string.onboarding_gesture_tracks),
                    context.getString(R.string.onboarding_gesture_tracks_desc),
                    gesturePlayerSwipeTracks,
                    onSwipeTracksChange
                ),
                onboardingToggleItem(
                    MaterialSymbolIcon("touch_app", filled = true),
                    context.getString(R.string.onboarding_gesture_doubletap),
                    context.getString(R.string.onboarding_gesture_doubletap_desc),
                    gestureArtworkDoubleTap,
                    onDoubleTapChange
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun GestureTipsCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.onboarding_gestures_tips_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OnboardingTipItem(
                icon = MaterialSymbolIcon("swipe_vertical"),
                text = context.getString(R.string.onboarding_gesture_tip_1)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("speed"),
                text = context.getString(R.string.onboarding_gesture_tip_2)
            )
        }
    }
}

// =====================================================
// WIDGETS ONBOARDING STEP
// =====================================================

@Composable
fun EnhancedWidgetsContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Widget settings
    val showArtist by appSettings.widgetShowArtist.collectAsState()
    val showAlbum by appSettings.widgetShowAlbum.collectAsState()
    val showFavorite by appSettings.widgetShowFavoriteButton.collectAsState()

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("widgets", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_widgets_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_widgets_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                WidgetTipsCard()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WidgetSettingsCard(
                    showArtist = showArtist,
                    showAlbum = showAlbum,
                    showFavorite = showFavorite,
                    onArtistChange = { appSettings.setWidgetShowArtist(it) },
                    onAlbumChange = { appSettings.setWidgetShowAlbum(it) },
                    onFavoriteChange = { appSettings.setWidgetShowFavoriteButton(it) }
                )

                WidgetAppearanceSection(
                    appSettings = appSettings,
                    context = context
                )

                WidgetCustomizationSection(
                    appSettings = appSettings,
                    context = context
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = MaterialSymbolIcon("widgets", filled = true),
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_widgets_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_widgets_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            WidgetSettingsCard(
                showArtist = showArtist,
                showAlbum = showAlbum,
                showFavorite = showFavorite,
                onArtistChange = { appSettings.setWidgetShowArtist(it) },
                onAlbumChange = { appSettings.setWidgetShowAlbum(it) },
                onFavoriteChange = { appSettings.setWidgetShowFavoriteButton(it) }
            )

            WidgetAppearanceSection(
                appSettings = appSettings,
                context = context
            )

            WidgetCustomizationSection(
                appSettings = appSettings,
                context = context
            )

            Spacer(modifier = Modifier.height(16.dp))

            WidgetTipsCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WidgetAppearanceSection(
    appSettings: AppSettings,
    context: Context
) {
    val haptic = LocalHapticFeedback.current
    val cornerRadius by appSettings.widgetCornerRadius.collectAsState()
    val widgetTheme by appSettings.widgetTheme.collectAsState()
    var showCornerRadiusSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }

    val themeName = when (widgetTheme) {
        1 -> context.getString(R.string.widget_theme_solid_dark)
        2 -> context.getString(R.string.widget_theme_translucent_dark)
        3 -> context.getString(R.string.widget_theme_solid_purple)
        else -> context.getString(R.string.widget_theme_dynamic)
    }

    TourSectionTitle(context.getString(R.string.widget_appearance))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = MaterialSymbolIcon("rounded_corner"),
                title = { Text(context.getString(R.string.settings_miniplayer_corner_radius)) },
                description = { Text(context.getString(R.string.widget_settings_radius_desc, cornerRadius)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showCornerRadiusSheet = true
                }
            ),
            Material3SettingsItem(
                icon = MaterialSymbolIcon("palette"),
                title = { Text(context.getString(R.string.widgetsettingsscreen_widget_theme)) },
                description = { Text(context.getString(R.string.widget_theme_glance_suffix, themeName)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showThemeSheet = true
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showCornerRadiusSheet) {
        WidgetCornerRadiusSheet(
            currentRadius = cornerRadius,
            onDismiss = { showCornerRadiusSheet = false },
            appSettings = appSettings
        )
    }
    if (showThemeSheet) {
        WidgetThemeSheet(
            currentTheme = widgetTheme,
            onDismiss = { showThemeSheet = false },
            appSettings = appSettings
        )
    }
}

@Composable
private fun WidgetSettingsCard(
    showArtist: Boolean,
    showAlbum: Boolean,
    showFavorite: Boolean,
    onArtistChange: (Boolean) -> Unit,
    onAlbumChange: (Boolean) -> Unit,
    onFavoriteChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = RhythmIcons.Artist,
                title = { Text(context.getString(R.string.onboarding_widget_artist)) },
                description = { Text(context.getString(R.string.onboarding_widget_artist_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = showArtist,
                        onCheckedChange = {
                            onArtistChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onArtistChange(!showArtist)
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.Album,
                title = { Text(context.getString(R.string.onboarding_widget_album)) },
                description = { Text(context.getString(R.string.onboarding_widget_album_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = showAlbum,
                        onCheckedChange = {
                            onAlbumChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onAlbumChange(!showAlbum)
                }
            ),
            Material3SettingsItem(
                icon = RhythmIcons.FavoriteFilled,
                title = { Text(context.getString(R.string.widgetsettingsscreen_show_favorite_button)) },
                description = { Text(context.getString(R.string.widget_show_favorite_button_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = showFavorite,
                        onCheckedChange = {
                            onFavoriteChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onFavoriteChange(!showFavorite)
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun WidgetTipsCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RhythmIcons.Info,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.onboarding_widgets_tips_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OnboardingTipItem(
                icon = MaterialSymbolIcon("cookie"),
                text = context.getString(R.string.widget_tip_cookie_corners)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("auto_graph"),
                text = context.getString(R.string.widget_tip_stats_widget)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("touch_app"),
                text = context.getString(R.string.widget_tip_controls)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("aspect_ratio"),
                text = context.getString(R.string.widget_tip_resize_settings)
            )
        }
    }
}

// =====================================================
// TOUR SETTINGS SECTIONS (mirror the Settings screens)
// =====================================================

@Composable
private fun WidgetCustomizationSection(
    appSettings: AppSettings,
    context: Context
) {
    val haptic = LocalHapticFeedback.current
    var showCookieLeftSheet by remember { mutableStateOf(false) }
    var showCookieRightSheet by remember { mutableStateOf(false) }
    var showStatsRangeSheet by remember { mutableStateOf(false) }
    var showStatsGemSheet by remember { mutableStateOf(false) }

    val cookieBottomLeft by appSettings.widgetCookieBottomLeft.collectAsState()
    val cookieBottomRight by appSettings.widgetCookieBottomRight.collectAsState()
    val statsRange by appSettings.widgetStatsRange.collectAsState()
    val statsGem by appSettings.widgetStatsGem.collectAsState()

    TourSectionTitle(context.getString(R.string.widget_cookie_section_title))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = cookieActionIcon(cookieBottomLeft, isLeft = true),
                title = { Text(context.getString(R.string.widget_cookie_bottom_left)) },
                description = { Text(cookieActionLabel(cookieBottomLeft)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showCookieLeftSheet = true
                }
            ),
            Material3SettingsItem(
                icon = cookieActionIcon(cookieBottomRight, isLeft = false),
                title = { Text(context.getString(R.string.widget_cookie_bottom_right)) },
                description = { Text(cookieActionLabel(cookieBottomRight)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showCookieRightSheet = true
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )

    TourSectionTitle(context.getString(R.string.widget_stats_section_title))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = statsRangeIcon(statsRange),
                title = { Text(context.getString(R.string.widget_stats_time_range)) },
                description = { Text(statsRangeLabel(statsRange)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showStatsRangeSheet = true
                }
            ),
            Material3SettingsItem(
                icon = statsGemIcon(statsGem),
                title = { Text(context.getString(R.string.widget_stats_gem)) },
                description = { Text(statsGemLabel(statsGem)) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showStatsGemSheet = true
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showCookieLeftSheet) {
        ActionPickerSheet(
            title = context.getString(R.string.widget_cookie_bottom_left),
            selectedValue = cookieBottomLeft,
            options = listOf(
                PickerOption(0, context.getString(R.string.widget_cookie_action_skip), context.getString(R.string.widget_cookie_action_skip_left_desc), MaterialSymbolIcon("skip_previous")),
                PickerOption(1, context.getString(R.string.widget_cookie_action_shuffle), context.getString(R.string.widget_cookie_action_shuffle_desc), MaterialSymbolIcon("shuffle")),
                PickerOption(2, context.getString(R.string.widget_cookie_action_repeat), context.getString(R.string.widget_cookie_action_repeat_desc), MaterialSymbolIcon("repeat")),
                PickerOption(3, context.getString(R.string.widget_cookie_action_favorite), context.getString(R.string.widget_cookie_action_favorite_desc), MaterialSymbolIcon("favorite")),
                PickerOption(4, context.getString(R.string.widget_cookie_action_none), context.getString(R.string.widget_cookie_action_none_desc), MaterialSymbolIcon("block"))
            ),
            onDismiss = { showCookieLeftSheet = false },
            onSelect = { value ->
                appSettings.setWidgetCookieBottomLeft(value)
                updateAllWidgets(context)
                showCookieLeftSheet = false
            }
        )
    }

    if (showCookieRightSheet) {
        ActionPickerSheet(
            title = context.getString(R.string.widget_cookie_bottom_right),
            selectedValue = cookieBottomRight,
            options = listOf(
                PickerOption(0, context.getString(R.string.widget_cookie_action_skip), context.getString(R.string.widget_cookie_action_skip_right_desc), MaterialSymbolIcon("skip_next")),
                PickerOption(1, context.getString(R.string.widget_cookie_action_shuffle), context.getString(R.string.widget_cookie_action_shuffle_desc), MaterialSymbolIcon("shuffle")),
                PickerOption(2, context.getString(R.string.widget_cookie_action_repeat), context.getString(R.string.widget_cookie_action_repeat_desc), MaterialSymbolIcon("repeat")),
                PickerOption(3, context.getString(R.string.widget_cookie_action_favorite), context.getString(R.string.widget_cookie_action_favorite_desc), MaterialSymbolIcon("favorite")),
                PickerOption(4, context.getString(R.string.widget_cookie_action_none), context.getString(R.string.widget_cookie_action_none_desc), MaterialSymbolIcon("block"))
            ),
            onDismiss = { showCookieRightSheet = false },
            onSelect = { value ->
                appSettings.setWidgetCookieBottomRight(value)
                updateAllWidgets(context)
                showCookieRightSheet = false
            }
        )
    }

    if (showStatsRangeSheet) {
        ActionPickerSheet(
            title = context.getString(R.string.widget_stats_time_range),
            selectedValue = statsRange,
            options = listOf(
                PickerOption(0, context.getString(R.string.widget_stats_range_all_time), context.getString(R.string.widget_stats_range_all_time_desc), MaterialSymbolIcon("all_inclusive")),
                PickerOption(1, context.getString(R.string.widget_stats_range_today), context.getString(R.string.widget_stats_range_today_desc), MaterialSymbolIcon("today")),
                PickerOption(2, context.getString(R.string.widget_stats_range_week), context.getString(R.string.widget_stats_range_week_desc), MaterialSymbolIcon("date_range")),
                PickerOption(3, context.getString(R.string.widget_stats_range_month), context.getString(R.string.widget_stats_range_month_desc), MaterialSymbolIcon("calendar_month"))
            ),
            onDismiss = { showStatsRangeSheet = false },
            onSelect = { value ->
                appSettings.setWidgetStatsRange(value)
                updateAllWidgets(context)
                showStatsRangeSheet = false
            }
        )
    }

    if (showStatsGemSheet) {
        ActionPickerSheet(
            title = context.getString(R.string.widget_stats_gem),
            selectedValue = statsGem,
            options = listOf(
                PickerOption(0, context.getString(R.string.widget_stats_gem_longest_streak), context.getString(R.string.widget_stats_gem_longest_streak_desc), MaterialSymbolIcon("workspace_premium")),
                PickerOption(1, context.getString(R.string.widget_stats_gem_current_streak), context.getString(R.string.widget_stats_gem_current_streak_desc), MaterialSymbolIcon("local_fire_department")),
                PickerOption(2, context.getString(R.string.widget_stats_gem_active_days), context.getString(R.string.widget_stats_gem_active_days_desc), MaterialSymbolIcon("event_available")),
                PickerOption(3, context.getString(R.string.widget_stats_gem_sessions), context.getString(R.string.widget_stats_gem_sessions_desc), MaterialSymbolIcon("history"))
            ),
            onDismiss = { showStatsGemSheet = false },
            onSelect = { value ->
                appSettings.setWidgetStatsGem(value)
                updateAllWidgets(context)
                showStatsGemSheet = false
            }
        )
    }
}

@Composable
private fun TourSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 8.dp, top = 12.dp)
    )
}

@Composable
private fun tourToggleItem(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    checked: Boolean,
    context: Context,
    enabled: Boolean = true,
    palette: SettingsBadgePalette? = null,
    onCheckedChange: (Boolean) -> Unit
): Material3SettingsItem {
    val haptic = LocalHapticFeedback.current
    return Material3SettingsItem(
        icon = icon,
        palette = palette,
        title = { Text(title) },
        description = { Text(description) },
        enabled = enabled,
        trailingContent = {
            OnboardingAnimatedSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = {
                    onCheckedChange(it)
                }
            )
        },
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            onCheckedChange(!checked)
        }
    )
}

@Composable
private fun tourSheetRow(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    context: Context,
    enabled: Boolean = true,
    palette: SettingsBadgePalette? = null,
    onClick: () -> Unit
): Material3SettingsItem {
    val haptic = LocalHapticFeedback.current
    return Material3SettingsItem(
        icon = icon,
        palette = palette,
        title = { Text(title) },
        description = { Text(description) },
        enabled = enabled,
        trailingContent = {
            Icon(
                imageVector = RhythmIcons.Forward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            onClick()
        }
    )
}

private fun playerAlignmentLabel(value: String, context: Context): String {
    return when (value) {
        "START" -> context.getString(R.string.settings_left_aligned)
        "END" -> context.getString(R.string.settings_right_aligned)
        else -> context.getString(R.string.settings_center_aligned)
    }
}

@Composable
private fun PlayerThemeSettingsSection(
    isPlayer: Boolean,
    appSettings: AppSettings,
    context: Context
) {
    val haptic = LocalHapticFeedback.current

    var showTextAlignmentSheet by remember { mutableStateOf(false) }
    var showProgressStyleSheet by remember { mutableStateOf(false) }
    var showThumbStyleSheet by remember { mutableStateOf(false) }
    var showArtworkSizeSheet by remember { mutableStateOf(false) }
    var showCornerRadiusSheet by remember { mutableStateOf(false) }

    // Full player settings
    val playerThemeId by appSettings.playerThemeId.collectAsState()
    val playerIsExpressive = playerThemeId != "MATERIAL"
    val showGradientOverlay by appSettings.playerShowGradientOverlay.collectAsState()
    val showSongInfo by appSettings.playerShowSongInfoOnArtwork.collectAsState()
    val showQualityBadges by appSettings.playerShowAudioQualityBadges.collectAsState()
    val showSeekButtons by appSettings.playerShowSeekButtons.collectAsState()
    val playerTextAlignment by appSettings.playerTextAlignment.collectAsState()
    val playerProgressStyle by appSettings.playerProgressStyle.collectAsState()
    val playerThumbStyle by appSettings.playerProgressThumbStyle.collectAsState()
    val thumbRotate by appSettings.playerProgressThumbRotate.collectAsState()
    val ambientBackdrop by appSettings.playerAmbientBackdropEnabled.collectAsState()
    val accentBackground by appSettings.playerAccentBackgroundEnabled.collectAsState()
    val mergeControls by appSettings.playerMergeControlsToBottom.collectAsState()

    // Mini player settings
    val miniPlayerThemeId by appSettings.miniPlayerThemeId.collectAsState()
    val miniIsExpressive = miniPlayerThemeId == "EXPRESSIVE"
    val miniShowProgress by appSettings.miniPlayerShowProgress.collectAsState()
    val miniProgressStyle by appSettings.miniPlayerProgressStyle.collectAsState()
    val miniShowArtwork by appSettings.miniPlayerShowArtwork.collectAsState()
    val miniArtworkSize by appSettings.miniPlayerArtworkSize.collectAsState()
    val miniCornerRadius by appSettings.miniPlayerCornerRadius.collectAsState()
    val miniShowTime by appSettings.miniPlayerShowTime.collectAsState()
    val miniTabletLayout by appSettings.miniPlayerAlwaysShowTablet.collectAsState()

    if (isPlayer) {
        TourSectionTitle(context.getString(R.string.settings_theme))
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("palette"),
                    title = { Text(context.getString(R.string.playercustomizationsettingsscreen_playback_theme)) },
                    description = {
                        Column {
                            Text(context.getString(R.string.miniplayercustomizationsettingsscreen_choose_between_rhythm_default))
                            Spacer(modifier = Modifier.height(12.dp))
                            ExpressiveButtonGroup(
                                items = listOf(
                                    context.getString(R.string.theme_rhythm),
                                    context.getString(R.string.theme_expressive)
                                ),
                                selectedIndex = if (playerIsExpressive) 1 else 0,
                                onItemClick = { index ->
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    if (index == 1) {
                                        appSettings.setPlayerThemeId("EXPRESSIVE")
                                    } else {
                                        appSettings.setPlayerThemeId("MATERIAL")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        TourSectionTitle(context.getString(R.string.settings_display_options))
        Material3SettingsGroup(
            items = listOf(
                tourToggleItem(
                    icon = MaterialSymbolIcon("gradient"),
                    title = context.getString(R.string.settings_artwork_overlay),
                    description = if (playerIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_artwork_overlay_desc),
                    checked = showGradientOverlay,
                    context = context,
                    enabled = !playerIsExpressive,
                    onCheckedChange = { appSettings.setPlayerShowGradientOverlay(it) }
                ),
                tourToggleItem(
                    icon = RhythmIcons.Info,
                    title = context.getString(R.string.settings_song_info_artwork),
                    description = if (playerIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_song_info_artwork_desc),
                    checked = showSongInfo,
                    context = context,
                    enabled = !playerIsExpressive,
                    onCheckedChange = { appSettings.setPlayerShowSongInfoOnArtwork(it) }
                ),
                tourToggleItem(
                    icon = MaterialSymbolIcon("high_quality"),
                    title = context.getString(R.string.settings_audio_quality_badges),
                    description = context.getString(R.string.settings_audio_quality_badges_desc),
                    checked = showQualityBadges,
                    context = context,
                    onCheckedChange = { appSettings.setPlayerShowAudioQualityBadges(it) }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        if (playerIsExpressive) {
            TourSectionTitle(context.getString(R.string.settings_expressive_player))
            Material3SettingsGroup(
                items = listOf(
                    tourToggleItem(
                        icon = MaterialSymbolIcon("blur_on"),
                        title = context.getString(R.string.player_ambient_backdrop),
                        description = context.getString(R.string.player_ambient_desc),
                        checked = ambientBackdrop,
                        context = context,
                        onCheckedChange = { appSettings.setPlayerAmbientBackdropEnabled(it) }
                    ),
                    tourToggleItem(
                        icon = MaterialSymbolIcon("colorize"),
                        title = context.getString(R.string.player_accent_background),
                        description = context.getString(R.string.player_accent_background_desc),
                        checked = accentBackground,
                        context = context,
                        onCheckedChange = { appSettings.setPlayerAccentBackgroundEnabled(it) }
                    ),
                    tourToggleItem(
                        icon = MaterialSymbolIcon("merge_type"),
                        title = context.getString(R.string.player_merge_controls),
                        description = context.getString(R.string.player_merge_controls_desc),
                        checked = mergeControls,
                        context = context,
                        onCheckedChange = { appSettings.setPlayerMergeControlsToBottom(it) }
                    )
                ),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        TourSectionTitle(context.getString(R.string.settings_layout_options))
        Material3SettingsGroup(
            items = listOf(
                tourToggleItem(
                    icon = RhythmIcons.Forward10,
                    title = context.getString(R.string.settings_seek_buttons),
                    description = if (playerIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_seek_buttons_desc),
                    checked = showSeekButtons,
                    context = context,
                    enabled = !playerIsExpressive,
                    onCheckedChange = { appSettings.setPlayerShowSeekButtons(it) }
                ),
                tourSheetRow(
                    icon = MaterialSymbolIcon("format_align_center"),
                    title = context.getString(R.string.settings_text_alignment),
                    description = playerAlignmentLabel(playerTextAlignment, context),
                    context = context,
                    enabled = !playerIsExpressive,
                    onClick = { showTextAlignmentSheet = true }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        TourSectionTitle(context.getString(R.string.settings_progress_display))
        Material3SettingsGroup(
            items = listOf(
                tourSheetRow(
                    icon = MaterialSymbolIcon("linear_scale"),
                    title = context.getString(R.string.settings_miniplayer_progress_style),
                    description = playerProgressStyle.lowercase().replaceFirstChar { it.uppercase() },
                    context = context,
                    onClick = { showProgressStyleSheet = true }
                ),
                tourSheetRow(
                    icon = MaterialSymbolIcon("touch_app"),
                    title = context.getString(R.string.settings_thumb_style),
                    description = playerThumbStyle.lowercase().replaceFirstChar { it.uppercase() },
                    context = context,
                    onClick = { showThumbStyleSheet = true }
                ),
                tourToggleItem(
                    icon = MaterialSymbolIcon("rotate_right"),
                    title = context.getString(R.string.settings_thumb_rotate),
                    description = context.getString(R.string.settings_thumb_rotate_desc),
                    checked = thumbRotate,
                    context = context,
                    onCheckedChange = { appSettings.setPlayerProgressThumbRotate(it) }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )
    } else {
        TourSectionTitle(context.getString(R.string.settings_theme))
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("palette"),
                    title = { Text(context.getString(R.string.miniplayercustomizationsettingsscreen_miniplayer_theme)) },
                    description = {
                        Column {
                            Text(context.getString(R.string.miniplayercustomizationsettingsscreen_choose_between_rhythm_default))
                            Spacer(modifier = Modifier.height(12.dp))
                            ExpressiveButtonGroup(
                                items = listOf(
                                    context.getString(R.string.theme_rhythm),
                                    context.getString(R.string.theme_expressive)
                                ),
                                selectedIndex = if (miniIsExpressive) 1 else 0,
                                onItemClick = { index ->
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    if (index == 1) {
                                        appSettings.setMiniPlayerThemeId("EXPRESSIVE")
                                    } else {
                                        appSettings.setMiniPlayerThemeId("MATERIAL")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        TourSectionTitle(context.getString(R.string.settings_progress_display))
        Material3SettingsGroup(
            items = listOf(
                tourToggleItem(
                    icon = RhythmIcons.Visibility,
                    title = context.getString(R.string.settings_show_progress),
                    description = if (miniIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_progress_desc),
                    checked = miniShowProgress,
                    context = context,
                    enabled = !miniIsExpressive,
                    onCheckedChange = { appSettings.setMiniPlayerShowProgress(it) }
                ),
                tourSheetRow(
                    icon = MaterialSymbolIcon("linear_scale"),
                    title = context.getString(R.string.settings_miniplayer_progress_style),
                    description = miniProgressStyle.lowercase().replaceFirstChar { it.uppercase() },
                    context = context,
                    enabled = miniShowProgress && !miniIsExpressive,
                    onClick = { showProgressStyleSheet = true }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        TourSectionTitle(context.getString(R.string.settings_artwork))
        Material3SettingsGroup(
            items = listOf(
                tourToggleItem(
                    icon = RhythmIcons.Album,
                    title = context.getString(R.string.settings_show_artwork),
                    description = if (miniIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_artwork_desc),
                    checked = miniShowArtwork,
                    context = context,
                    enabled = !miniIsExpressive,
                    onCheckedChange = { appSettings.setMiniPlayerShowArtwork(it) }
                ),
                tourSheetRow(
                    icon = MaterialSymbolIcon("photo_size_select_large"),
                    title = context.getString(R.string.settings_miniplayer_artwork_size),
                    description = if (miniIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else "${miniArtworkSize}dp",
                    context = context,
                    enabled = !miniIsExpressive,
                    onClick = { showArtworkSizeSheet = true }
                ),
                tourSheetRow(
                    icon = MaterialSymbolIcon("rounded_corner"),
                    title = context.getString(R.string.settings_miniplayer_corner_radius),
                    description = if (miniIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else "${miniCornerRadius}dp",
                    context = context,
                    enabled = !miniIsExpressive,
                    onClick = { showCornerRadiusSheet = true }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )

        TourSectionTitle(context.getString(R.string.settings_display_options))
        Material3SettingsGroup(
            items = listOf(
                tourToggleItem(
                    icon = MaterialSymbolIcon("timer"),
                    title = context.getString(R.string.settings_show_time),
                    description = if (miniIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_time_desc),
                    checked = miniShowTime,
                    context = context,
                    enabled = !miniIsExpressive,
                    onCheckedChange = { appSettings.setMiniPlayerShowTime(it) }
                ),
                tourToggleItem(
                    icon = MaterialSymbolIcon("tablet"),
                    title = context.getString(R.string.settings_tablet_layout),
                    description = if (miniIsExpressive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_tablet_layout_desc),
                    checked = miniTabletLayout,
                    context = context,
                    enabled = !miniIsExpressive,
                    onCheckedChange = { appSettings.setMiniPlayerAlwaysShowTablet(it) }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Bottom sheets - same components as the Settings screens
    if (showTextAlignmentSheet) {
        PlayerTextAlignmentBottomSheet(
            currentAlignment = playerTextAlignment,
            onAlignmentSelected = { value ->
                appSettings.setPlayerTextAlignment(value)
                showTextAlignmentSheet = false
            },
            onDismiss = { showTextAlignmentSheet = false },
            context = context,
            haptics = haptic
        )
    }
    if (showProgressStyleSheet) {
        ProgressStyleBottomSheet(
            title = context.getString(R.string.settings_miniplayer_progress_style),
            currentStyle = if (isPlayer) playerProgressStyle else miniProgressStyle,
            onStyleSelected = { style ->
                if (isPlayer) {
                    appSettings.setPlayerProgressStyle(style)
                } else {
                    appSettings.setMiniPlayerProgressStyle(style)
                }
                showProgressStyleSheet = false
            },
            onDismiss = { showProgressStyleSheet = false },
            context = context,
            haptics = haptic
        )
    }
    if (showThumbStyleSheet) {
        ThumbStyleBottomSheet(
            title = context.getString(R.string.settings_thumb_style),
            currentStyle = playerThumbStyle,
            onStyleSelected = { style ->
                appSettings.setPlayerProgressThumbStyle(style)
                showThumbStyleSheet = false
            },
            onDismiss = { showThumbStyleSheet = false },
            context = context,
            haptics = haptic
        )
    }
    if (showArtworkSizeSheet) {
        MiniPlayerArtworkSizeSheet(
            currentSize = miniArtworkSize,
            onSizeSelected = { size ->
                appSettings.setMiniPlayerArtworkSize(size)
                showArtworkSizeSheet = false
            },
            onDismiss = { showArtworkSizeSheet = false },
            context = context,
            haptics = haptic
        )
    }
    if (showCornerRadiusSheet) {
        MiniPlayerCornerRadiusSheet(
            currentRadius = miniCornerRadius,
            onRadiusSelected = { radius ->
                appSettings.setMiniPlayerCornerRadius(radius)
                showCornerRadiusSheet = false
            },
            onDismiss = { showCornerRadiusSheet = false },
            context = context,
            haptics = haptic
        )
    }
}


@Composable
private fun ThemingCustomizationSection(
    appSettings: AppSettings,
    context: Context
) {
    val haptic = LocalHapticFeedback.current
    var showColorSourceDialog by remember { mutableStateOf(false) }
    var showFontSelectionDialog by remember { mutableStateOf(false) }
    var showShapePresetsBottomSheet by remember { mutableStateOf(false) }

    val colorSource by appSettings.colorSource.collectAsState()
    val customColorScheme by appSettings.customColorScheme.collectAsState()
    val customFont by appSettings.customFont.collectAsState()

    // Color schemes - dynamic Material 3 preset schemes
    val isSystemDark = isSystemInDarkTheme()
    val colorSchemes = remember(context, isSystemDark) {
        getPresetColorSchemeOptions(context, isSystemDark)
    }

    val fontOptions = remember(context) {
        listOf(
            FontOption("Geom", "Geom", context.getString(R.string.font_geom_desc)),
            FontOption("System", context.getString(R.string.font_system_title), context.getString(R.string.font_system_desc)),
            FontOption("Slate", "Slate", context.getString(R.string.font_slate_desc)),
            FontOption("Inter", "Inter", context.getString(R.string.font_inter_desc)),
            FontOption("JetBrains", "JetBrains Mono", context.getString(R.string.font_jetbrains_desc)),
            FontOption("Quicksand", "Quicksand", context.getString(R.string.font_quicksand_desc))
        )
    }

    val colorSourceDescription = when (colorSource) {
        "ALBUM_ART" -> context.getString(R.string.color_source_album_art_desc)
        "MONET" -> context.getString(R.string.color_source_system_colors_desc)
        else -> context.getString(R.string.color_source_custom_scheme_desc)
    }
    val fontIndex = fontOptions.indexOfFirst { it.name == customFont }.coerceAtLeast(0)

    TourSectionTitle(context.getString(R.string.settings_color_customization))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = RhythmIcons.Palette,
                title = { Text(context.getString(R.string.settings_color_source)) },
                description = { Text(colorSourceDescription) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showColorSourceDialog = true
                }
            )
        ) + if (colorSource == "CUSTOM") {
            listOf(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("color_lens"),
                    title = { Text(context.getString(R.string.settings_color_schemes)) },
                    description = {
                        Column {
                            Text(
                                text = context.getString(R.string.settings_color_schemes_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            ColorSchemePaletteRow(
                                schemes = colorSchemes,
                                currentScheme = customColorScheme,
                                onSchemeSelected = { scheme ->
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setCustomColorScheme(scheme)
                                }
                            )
                        }
                    }
                )
            )
        } else {
            emptyList()
        },
        containerColor = MaterialTheme.colorScheme.surface
    )

    TourSectionTitle(context.getString(R.string.settings_font_customization))
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = MaterialSymbolIcon("text_fields"),
                title = { Text(context.getString(R.string.settings_font_selection)) },
                description = { Text(fontOptions[fontIndex].description) },
                trailingContent = {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    showFontSelectionDialog = true
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )

    val expressiveShapesEnabled by appSettings.expressiveShapesEnabled.collectAsState()
    val expressiveShapePreset by appSettings.expressiveShapePreset.collectAsState()

    TourSectionTitle(stringResource(R.string.onboarding_expressive_shapes_title))
    Material3SettingsGroup(
        items = listOf(
            tourToggleItem(
                icon = MaterialSymbolIcon("interests"),
                title = stringResource(R.string.settings_shapes),
                description = stringResource(R.string.onboarding_expressive_shapes_desc),
                checked = expressiveShapesEnabled,
                context = context,
                onCheckedChange = { appSettings.setExpressiveShapesEnabled(it) }
            )
        ) + if (expressiveShapesEnabled) {
            listOf(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("category"),
                    title = { Text(stringResource(R.string.settings_shape_preset)) },
                    description = {
                        Text(
                            text = getLocalizedShapePresetName(expressiveShapePreset),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        showShapePresetsBottomSheet = true
                    }
                )
            )
        } else emptyList(),
        containerColor = MaterialTheme.colorScheme.surface
    )

    ColorSourceDialog(
        showDialog = showColorSourceDialog,
        onDismiss = { showColorSourceDialog = false },
        selectedColorSource = when (colorSource) {
            "ALBUM_ART" -> ColorSource.ALBUM_ART
            "MONET" -> ColorSource.MONET
            else -> ColorSource.CUSTOM
        },
        onColorSourceSelected = { _ -> /* applied internally via appSettings */ },
        appSettings = appSettings,
        context = context,
        haptic = haptic
    )

    FontSelectionDialog(
        showDialog = showFontSelectionDialog,
        onDismiss = { showFontSelectionDialog = false },
        fontOptions = fontOptions,
        currentFont = customFont,
        selectedFontSource = FontSource.SYSTEM,
        onFontSelected = { selectedFont ->
            appSettings.setCustomFont(selectedFont)
            showFontSelectionDialog = false
        },
        appSettings = appSettings,
        context = context,
        haptic = haptic
    )

    if (showShapePresetsBottomSheet) {
        ShapePresetsBottomSheet(
            onDismiss = { showShapePresetsBottomSheet = false },
            appSettings = appSettings
        )
    }
}

// INTEGRATIONS ONBOARDING STEP
// =====================================================

@Composable
fun EnhancedIntegrationsContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    isTablet: Boolean = false,
    onLyricallyConfigure: () -> Unit = {},
    onAppleCanvasConfigure: () -> Unit = {},
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Integration settings
    val deezerApiEnabled by appSettings.deezerApiEnabled.collectAsState()
    val lrclibApiEnabled by appSettings.lrclibApiEnabled.collectAsState()
    val lyricallyApiEnabled by appSettings.lyricallyApiEnabled.collectAsState()
    val betterLyricsApiEnabled by appSettings.betterLyricsApiEnabled.collectAsState()
    val ytMusicApiEnabled by appSettings.ytMusicApiEnabled.collectAsState()
    val spotifyApiEnabled by appSettings.spotifyApiEnabled.collectAsState()
    val wikipediaApiEnabled by appSettings.wikipediaApiEnabled.collectAsState()
    val broadcastStatusEnabled by appSettings.broadcastStatusEnabled.collectAsState()
    val bluetoothLyricsEnabled by appSettings.bluetoothLyricsEnabled.collectAsState()
    val appleCanvasEnabled by appSettings.appleCanvasEnabled.collectAsState()
    val appleCanvasNetworkMode by appSettings.appleCanvasNetworkMode.collectAsState()

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("api", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_integrations_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_integrations_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                IntegrationsInfoCard()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IntegrationsSettingsCards(
                    deezerApiEnabled = deezerApiEnabled,
                    lrclibApiEnabled = lrclibApiEnabled,
                    lyricallyApiEnabled = lyricallyApiEnabled,
                    betterLyricsApiEnabled = betterLyricsApiEnabled,
                    ytMusicApiEnabled = ytMusicApiEnabled,
                    spotifyApiEnabled = spotifyApiEnabled,
                    wikipediaApiEnabled = wikipediaApiEnabled,
                    broadcastStatusEnabled = broadcastStatusEnabled,
                    bluetoothLyricsEnabled = bluetoothLyricsEnabled,
                    appleCanvasEnabled = appleCanvasEnabled,
                    appleCanvasNetworkMode = appleCanvasNetworkMode,
                    onDeezerChange = { appSettings.setDeezerApiEnabled(it) },
                    onLrcLibChange = { appSettings.setLrcLibApiEnabled(it) },
                    onLyricallyChange = { appSettings.setLyricallyApiEnabled(it) },
                    onBetterLyricsChange = { appSettings.setBetterLyricsApiEnabled(it) },
                    onYtMusicChange = { appSettings.setYTMusicApiEnabled(it) },
                    onSpotifyChange = { appSettings.setSpotifyApiEnabled(it) },
                    onWikipediaChange = { appSettings.setWikipediaApiEnabled(it) },
                    onBroadcastChange = { appSettings.setBroadcastStatusEnabled(it) },
                    onBluetoothLyricsChange = {
                        appSettings.setBluetoothLyricsEnabled(it)
                        if (it && !broadcastStatusEnabled) {
                            appSettings.setBroadcastStatusEnabled(true)
                        }
                    },
                    onAppleCanvasChange = { appSettings.setAppleCanvasEnabled(it) },
                    onAppleCanvasConfigure = onAppleCanvasConfigure,
                    onLyricallyConfigure = onLyricallyConfigure
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = MaterialSymbolIcon("api", filled = true),
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_integrations_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_integrations_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            IntegrationsSettingsCards(
                deezerApiEnabled = deezerApiEnabled,
                lrclibApiEnabled = lrclibApiEnabled,
                lyricallyApiEnabled = lyricallyApiEnabled,
                betterLyricsApiEnabled = betterLyricsApiEnabled,
                ytMusicApiEnabled = ytMusicApiEnabled,
                spotifyApiEnabled = spotifyApiEnabled,
                wikipediaApiEnabled = wikipediaApiEnabled,
                broadcastStatusEnabled = broadcastStatusEnabled,
                bluetoothLyricsEnabled = bluetoothLyricsEnabled,
                appleCanvasEnabled = appleCanvasEnabled,
                appleCanvasNetworkMode = appleCanvasNetworkMode,
                onDeezerChange = { appSettings.setDeezerApiEnabled(it) },
                onLrcLibChange = { appSettings.setLrcLibApiEnabled(it) },
                onLyricallyChange = { appSettings.setLyricallyApiEnabled(it) },
                onBetterLyricsChange = { appSettings.setBetterLyricsApiEnabled(it) },
                onYtMusicChange = { appSettings.setYTMusicApiEnabled(it) },
                onSpotifyChange = { appSettings.setSpotifyApiEnabled(it) },
                onWikipediaChange = { appSettings.setWikipediaApiEnabled(it) },
                onBroadcastChange = { appSettings.setBroadcastStatusEnabled(it) },
                onBluetoothLyricsChange = {
                    appSettings.setBluetoothLyricsEnabled(it)
                    if (it && !broadcastStatusEnabled) {
                        appSettings.setBroadcastStatusEnabled(true)
                    }
                },
                onAppleCanvasChange = { appSettings.setAppleCanvasEnabled(it) },
                onAppleCanvasConfigure = onAppleCanvasConfigure,
                onLyricallyConfigure = onLyricallyConfigure
            )

            Spacer(modifier = Modifier.height(16.dp))

            IntegrationsInfoCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IntegrationsSettingsCards(
    deezerApiEnabled: Boolean,
    lrclibApiEnabled: Boolean,
    lyricallyApiEnabled: Boolean,
    betterLyricsApiEnabled: Boolean,
    ytMusicApiEnabled: Boolean,
    spotifyApiEnabled: Boolean,
    wikipediaApiEnabled: Boolean,
    broadcastStatusEnabled: Boolean,
    bluetoothLyricsEnabled: Boolean,
    appleCanvasEnabled: Boolean,
    appleCanvasNetworkMode: chromahub.rhythm.app.shared.data.model.CanvasNetworkMode,
    onDeezerChange: (Boolean) -> Unit,
    onLrcLibChange: (Boolean) -> Unit,
    onLyricallyChange: (Boolean) -> Unit,
    onBetterLyricsChange: (Boolean) -> Unit,
    onYtMusicChange: (Boolean) -> Unit,
    onSpotifyChange: (Boolean) -> Unit,
    onWikipediaChange: (Boolean) -> Unit,
    onBroadcastChange: (Boolean) -> Unit,
    onBluetoothLyricsChange: (Boolean) -> Unit,
    onAppleCanvasChange: (Boolean) -> Unit,
    onAppleCanvasConfigure: () -> Unit,
    onLyricallyConfigure: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val onboardingToggleItem: (MaterialSymbolIcon, String, String, Boolean, (Boolean) -> Unit, (() -> Unit)?) -> Material3SettingsItem =
        { icon, title, description, isEnabled, onToggle, onConfigure ->
            Material3SettingsItem(
                icon = icon,
                title = { Text(title) },
                description = { Text(description) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onConfigure != null) {
                            Icon(
                                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                contentDescription = context.getString(R.string.cd_navigate),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        OnboardingAnimatedSwitch(
                            checked = isEnabled,
                            onCheckedChange = {
                                onToggle(it)
                                if (it && onConfigure != null) {
                                    onConfigure()
                                }
                            }
                        )
                    }
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    if (isEnabled && onConfigure != null) {
                        onConfigure()
                    } else {
                        onToggle(!isEnabled)
                        if (!isEnabled && onConfigure != null) {
                            onConfigure()
                        }
                    }
                }
            )
        }

    val apiItems = buildList {
        val appleCanvasDesc = "Dynamic animated album artwork - " + when (appleCanvasNetworkMode) {
            chromahub.rhythm.app.shared.data.model.CanvasNetworkMode.WIFI_ONLY -> "Only on Wi-Fi"
            chromahub.rhythm.app.shared.data.model.CanvasNetworkMode.BOTH -> "Wi-Fi & Cellular"
        }
        add(
            onboardingToggleItem(
                MaterialSymbolIcon("movie"),
                "Apple Music Motion Canvas",
                appleCanvasDesc,
                appleCanvasEnabled,
                onAppleCanvasChange,
                onAppleCanvasConfigure
            )
        )
        if (chromahub.rhythm.app.BuildConfig.ENABLE_DEEZER) {
            add(
                onboardingToggleItem(
                    RhythmIcons.Public,
                    "Deezer",
                    "Get high-quality album covers and track details automatically",
                    deezerApiEnabled,
                    onDeezerChange,
                    null
                )
            )
        }
        if (chromahub.rhythm.app.BuildConfig.ENABLE_BETTERLYRICS) {
            add(
                onboardingToggleItem(
                    MaterialSymbolIcon("music_note"),
                    context.getString(R.string.onboarding_integration_betterlyrics),
                    context.getString(R.string.api_betterlyrics_desc),
                    betterLyricsApiEnabled,
                    onBetterLyricsChange,
                    null
                )
            )
        }
        if (chromahub.rhythm.app.BuildConfig.ENABLE_LYRICALLY_API) {
            add(
                onboardingToggleItem(
                    MaterialSymbolIcon("music_note"),
                    "Lyrically",
                    "Enjoy beautiful, word-by-word synchronized lyrics",
                    lyricallyApiEnabled,
                    onLyricallyChange,
                    onLyricallyConfigure
                )
            )
        }
        if (chromahub.rhythm.app.BuildConfig.ENABLE_LRCLIB) {
            add(
                onboardingToggleItem(
                    MaterialSymbolIcon("lyrics"),
                    "LrcLib",
                    "Find and download synchronized scrolling lyrics",
                    lrclibApiEnabled,
                    onLrcLibChange,
                    null
                )
            )
        }
        if (chromahub.rhythm.app.BuildConfig.ENABLE_YOUTUBE_MUSIC) {
            add(
                onboardingToggleItem(
                    MaterialSymbolIcon("music_video"),
                    "YouTube Music",
                    "Access matching song details and recommendations",
                    ytMusicApiEnabled,
                    onYtMusicChange,
                    null
                )
            )
        }
        if (chromahub.rhythm.app.BuildConfig.ENABLE_WIKIPEDIA) {
            add(
                onboardingToggleItem(
                    MaterialSymbolIcon("article"),
                    "Wikipedia",
                    "Fetch album details and descriptions for About section",
                    wikipediaApiEnabled,
                    onWikipediaChange,
                    null
                )
            )
        }
    }

    val socialItems = listOf(
        onboardingToggleItem(
            RhythmIcons.Share,
            context.getString(R.string.broadcast_status_enabled),
            context.getString(R.string.broadcast_status_desc),
            broadcastStatusEnabled,
            onBroadcastChange,
            null
        ),
        onboardingToggleItem(
            MaterialSymbolIcon("lyrics"),
            context.getString(R.string.bluetooth_lyrics_enabled),
            context.getString(R.string.bluetooth_lyrics_desc),
            bluetoothLyricsEnabled,
            onBluetoothLyricsChange,
            null
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // API Services
        Text(
            text = context.getString(R.string.onboarding_integrations_apis),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )

        if (apiItems.isNotEmpty()) {
            Material3SettingsGroup(
                items = apiItems,
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Material3SettingsGroup(
            items = socialItems,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun IntegrationsInfoCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RhythmIcons.Info,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.onboarding_integrations_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = context.getString(R.string.onboarding_integrations_info_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                lineHeight = 20.sp
            )
        }
    }
}

// =====================================================
// RHYTHM STATS ONBOARDING STEP
// =====================================================

@Composable
fun EnhancedRhythmStatsContent(
    onNextStep: () -> Unit,
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Stats settings
    val homeShowListeningStats by appSettings.homeShowListeningStats.collectAsState()

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("auto_graph", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = context.getString(R.string.onboarding_stats_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = context.getString(R.string.onboarding_stats_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                StatsFeaturesAndInfoCard()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatsSettingsCard(
                    showOnHome = homeShowListeningStats,
                    onShowOnHomeChange = { appSettings.setHomeShowListeningStats(it) }
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = MaterialSymbolIcon("auto_graph", filled = true),
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = context.getString(R.string.onboarding_stats_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = context.getString(R.string.onboarding_stats_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            StatsSettingsCard(
                showOnHome = homeShowListeningStats,
                onShowOnHomeChange = { appSettings.setHomeShowListeningStats(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatsFeaturesAndInfoCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatsSettingsCard(
    showOnHome: Boolean,
    onShowOnHomeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = RhythmIcons.Home,
                title = { Text(context.getString(R.string.onboarding_stats_show_home)) },
                description = { Text(context.getString(R.string.onboarding_stats_show_home_desc)) },
                trailingContent = {
                    OnboardingAnimatedSwitch(
                        checked = showOnHome,
                        onCheckedChange = {
                            onShowOnHomeChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onShowOnHomeChange(!showOnHome)
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun StatsFeaturesAndInfoCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Features section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MaterialSymbolIcon("stars", filled = true),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.onboarding_stats_features_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OnboardingTipItem(
                icon = RhythmIcons.AccessTime,
                text = context.getString(R.string.onboarding_stats_feature_1)
            )
            OnboardingTipItem(
                icon = RhythmIcons.MusicNote,
                text = context.getString(R.string.onboarding_stats_feature_2)
            )
            OnboardingTipItem(
                icon = RhythmIcons.Artist,
                text = context.getString(R.string.onboarding_stats_feature_3)
            )
            OnboardingTipItem(
                icon = RhythmIcons.Album,
                text = context.getString(R.string.onboarding_stats_feature_4)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// =====================================================
// SHARED COMPOSABLES
// =====================================================

@Composable
fun OnboardingAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier
    )
}

@Composable
fun OnboardingTipItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun EnhancedAppModeChoiceContent(
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val appMode by appSettings.appMode.collectAsState()
    val scrollState = rememberScrollState()

    var selectedMode by remember { mutableStateOf(appMode) }

    val onSelectMode: (String) -> Unit = { mode ->
        selectedMode = mode
        scope.launch {
            appSettings.setAppMode(mode)
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Icon, Title, Description, Tips Card, Navigation Buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rhythm_splash_logo),
                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                        modifier = Modifier.size(80.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.common_rhythm),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedMode == "STREAMING",
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.splashscreen_go),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.onboardingscreen_choose_your_playback_mode),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = stringResource(R.string.onboarding_configure_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                AppModeChoiceTipsCard()

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right Column: Mode Selection List
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppModeSelectionList(
                    selectedMode = selectedMode,
                    onModeSelected = onSelectMode
                )
            }
        }
    } else {
        // Mobile Layout: Single column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rhythm_splash_logo),
                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                        modifier = Modifier.size(100.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.common_rhythm),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedMode == "STREAMING",
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.splashscreen_go),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboardingscreen_choose_your_playback_mode),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = stringResource(R.string.onboarding_configure_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            AppModeSelectionList(
                selectedMode = selectedMode,
                onModeSelected = onSelectMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            AppModeChoiceTipsCard()
        }
    }
}

@Composable
private fun AppModeSelectionList(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                leadingContent = {
                    Icon(
                        imageVector = MaterialSymbolIcon("music_note", filled = true),
                        contentDescription = null,
                        tint = if (selectedMode == "LOCAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.common_rhythm),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                description = {
                    Text(
                        text = stringResource(R.string.onboardingscreen_mode_local_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = selectedMode == "LOCAL",
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onModeSelected("LOCAL")
                }
            ),
            Material3SettingsItem(
                leadingContent = {
                    Icon(
                        imageVector = MaterialSymbolIcon("cloud_queue", filled = true),
                        contentDescription = null,
                        tint = if (selectedMode == "STREAMING") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.onboardingscreen_rhythm_go),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                description = {
                    Text(
                        text = stringResource(R.string.onboardingscreen_mode_streaming_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = selectedMode == "STREAMING",
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onModeSelected("STREAMING")
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun AppModeChoiceTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RhythmIcons.Info,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboardingscreen_playback_mode_tips),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OnboardingTipItem(
                icon = MaterialSymbolIcon("settings"),
                text = stringResource(R.string.onboardingscreen_switch_easily_anytime_in)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("folder_open"),
                text = stringResource(R.string.onboardingscreen_local_mode_scans_and)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("cloud_sync"),
                text = stringResource(R.string.onboardingscreen_go_streaming_connects_via)
            )
        }
    }
}

@Composable
fun EnhancedStreamingServiceChoiceContent(
    appSettings: AppSettings,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val streamingService by appSettings.streamingService.collectAsState()
    val scrollState = rememberScrollState()

    var selectedProvider by rememberSaveable {
        mutableStateOf(streamingService.ifBlank { "SUBSONIC" })
    }

    LaunchedEffect(streamingService) {
        if (streamingService.isNotBlank()) {
            selectedProvider = streamingService
        }
    }

    val onSelectProvider: (String) -> Unit = { id ->
        selectedProvider = id
        scope.launch {
            appSettings.setStreamingService(id)
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Icon, Title, Description, Tips Card, Navigation Buttons
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    OnboardingStepHeaderIcon(
                        imageVector = MaterialSymbolIcon("dns", filled = true),
                        tint = MaterialTheme.colorScheme.primary,
                        iconSize = 72.dp
                    )
                }

                Text(
                    text = stringResource(R.string.onboardingscreen_choose_streaming_provider),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = stringResource(R.string.onboardingscreen_choose_streaming_provider_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                StreamingServiceChoiceTipsCard()

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right Column: Service Selection List
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StreamingProviderSelectionList(
                    selectedProvider = selectedProvider,
                    onProviderSelected = onSelectProvider
                )
            }
        }
    } else {
        // Mobile Layout
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                OnboardingStepHeaderIcon(
                    imageVector = MaterialSymbolIcon("dns", filled = true),
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 56.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboardingscreen_choose_streaming_provider),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = stringResource(R.string.onboardingscreen_choose_streaming_provider_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            StreamingProviderSelectionList(
                selectedProvider = selectedProvider,
                onProviderSelected = onSelectProvider
            )

            Spacer(modifier = Modifier.height(24.dp))

            StreamingServiceChoiceTipsCard()
        }
    }
}

@Composable
private fun StreamingProviderSelectionList(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_subsonic),
                        contentDescription = null,
                        tint = if (selectedProvider == "SUBSONIC") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.onboardingscreen_subsonic_navidrome),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                description = {
                    Text(
                        text = stringResource(R.string.streaming_service_subsonic_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = selectedProvider == "SUBSONIC",
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onProviderSelected("SUBSONIC")
                }
            ),
            Material3SettingsItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_jellyfin),
                        contentDescription = null,
                        tint = if (selectedProvider == "JELLYFIN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.streaming_service_jellyfin),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                description = {
                    Text(
                        text = stringResource(R.string.streaming_service_jellyfin_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = selectedProvider == "JELLYFIN",
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onProviderSelected("JELLYFIN")
                }
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun StreamingServiceChoiceTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RhythmIcons.Info,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboardingscreen_service_choice_tips),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OnboardingTipItem(
                icon = MaterialSymbolIcon("dns"),
                text = stringResource(R.string.onboardingscreen_service_subsonic_tip)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("video_settings"),
                text = stringResource(R.string.onboardingscreen_service_jellyfin_tip)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("offline_pin"),
                text = stringResource(R.string.onboardingscreen_service_caching_tip)
            )
        }
    }
}

@Composable
fun EnhancedStreamingSetupContent(
    appSettings: AppSettings,
    streamingViewModel: StreamingMusicViewModel,
    onSkip: (() -> Unit)? = null,
    isTablet: Boolean = false,
    backButton: @Composable (() -> Unit)? = null,
    nextButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val sessions by streamingViewModel.serviceSessions.collectAsState()
    val isLoading by streamingViewModel.isLoading.collectAsState()
    val error by streamingViewModel.error.collectAsState()
    val rememberStreamingPasswords by appSettings.rememberStreamingPasswords.collectAsState()
    val currentStreamingService by appSettings.streamingService.collectAsState()
    val selectedProvider = currentStreamingService.ifBlank { "SUBSONIC" }
    
    val currentSession = sessions[selectedProvider] ?: streamingViewModel.getServiceSession(selectedProvider)
    val requiresServerUrl = remember(selectedProvider) { selectedProvider == "SUBSONIC" || selectedProvider == "JELLYFIN" }

    var serverUrl by rememberSaveable(selectedProvider) { mutableStateOf(currentSession.serverUrl) }
    var username by rememberSaveable(selectedProvider) { mutableStateOf(currentSession.username) }
    var password by rememberSaveable(selectedProvider) { mutableStateOf("") }
    var showDiscoverySheet by remember { mutableStateOf(false) }

    val scanner = remember(selectedProvider) { NearbyServerScanner(context, selectedProvider, scope) }
    var isAutoScanning by rememberSaveable(selectedProvider) { mutableStateOf(serverUrl.isBlank()) }

    DisposableEffect(scanner) {
        onDispose {
            scanner.stopScan()
        }
    }

    LaunchedEffect(selectedProvider) {
        if (serverUrl.isBlank()) {
            isAutoScanning = true
            scanner.startScan()
            delay(3500)
            isAutoScanning = false
            if (scanner.discoveredServers.isNotEmpty() && serverUrl.isBlank()) {
                showDiscoverySheet = true
            }
        }
    }

    val isConnected = currentSession.isConnected
    val canSubmit = username.isNotBlank() && password.isNotBlank() && (!requiresServerUrl || serverUrl.isNotBlank())
    val scrollState = rememberScrollState()

    val providerDisplayName = if (selectedProvider == "JELLYFIN") {
        stringResource(R.string.streaming_service_jellyfin)
    } else {
        stringResource(R.string.onboardingscreen_subsonic_navidrome)
    }
    val providerDescription = if (selectedProvider == "JELLYFIN") {
        stringResource(R.string.streaming_service_jellyfin_desc)
    } else {
        stringResource(R.string.streaming_service_subsonic_desc)
    }
    val providerIconRes = if (selectedProvider == "JELLYFIN") {
        R.drawable.ic_jellyfin
    } else {
        R.drawable.ic_subsonic
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isAutoScanning,
                        label = "setup_header_icon_tablet"
                    ) { scanning ->
                        if (scanning) {
                            RhythmWavyProgressLoader(
                                progress = null,
                                modifier = Modifier.size(72.dp),
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                centerContent = {
                                    Icon(
                                        painter = painterResource(id = providerIconRes),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = providerIconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }

                Text(
                    text = if (isAutoScanning) {
                        stringResource(R.string.onboarding_scanning_for_provider, providerDisplayName)
                    } else {
                        stringResource(R.string.onboardingscreen_configure_provider, providerDisplayName)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = if (isAutoScanning) {
                        stringResource(R.string.onboarding_scanning_for_provider_desc)
                    } else {
                        providerDescription
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                StreamingSetupTipsCard()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backButton?.invoke()
                    nextButton()
                }
            }

            // Right Column
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StreamingSetupSelectionAndForm(
                    selectedProvider = selectedProvider,
                    requiresServerUrl = requiresServerUrl,
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    isConnected = isConnected,
                    isLoading = isLoading,
                    error = error,
                    canSubmit = canSubmit,
                    selectedProviderName = providerDisplayName,
                    rememberStreamingPasswords = rememberStreamingPasswords,
                    onRememberPasswordChange = { enabled ->
                        scope.launch { appSettings.setRememberStreamingPasswords(enabled) }
                    },
                    onDiscoverClick = {
                        scanner.rescan()
                        showDiscoverySheet = true
                    },
                    onConnect = {
                        appSettings.setStreamingService(selectedProvider)
                        streamingViewModel.connectService(
                            serviceId = selectedProvider,
                            serverUrl = serverUrl,
                            username = username,
                            password = password
                        )
                    },
                    onDisconnect = {
                        streamingViewModel.disconnectService(selectedProvider)
                    },
                    onSkip = onSkip
                )
            }
        }
    } else {
        // Mobile Column
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isAutoScanning,
                    label = "setup_header_icon_mobile"
                ) { scanning ->
                    if (scanning) {
                        RhythmWavyProgressLoader(
                            progress = null,
                            modifier = Modifier.size(56.dp),
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            centerContent = {
                                Icon(
                                    painter = painterResource(id = providerIconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = providerIconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isAutoScanning) {
                    stringResource(R.string.onboarding_scanning_for_provider, providerDisplayName)
                } else {
                    stringResource(R.string.onboardingscreen_configure_provider, providerDisplayName)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = if (isAutoScanning) {
                    stringResource(R.string.onboarding_scanning_for_provider_desc)
                } else {
                    providerDescription
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            StreamingSetupSelectionAndForm(
                selectedProvider = selectedProvider,
                requiresServerUrl = requiresServerUrl,
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                isConnected = isConnected,
                isLoading = isLoading,
                error = error,
                canSubmit = canSubmit,
                selectedProviderName = providerDisplayName,
                rememberStreamingPasswords = rememberStreamingPasswords,
                onRememberPasswordChange = { enabled ->
                    scope.launch { appSettings.setRememberStreamingPasswords(enabled) }
                },
                onDiscoverClick = {
                    scanner.rescan()
                    showDiscoverySheet = true
                },
                onConnect = {
                    appSettings.setStreamingService(selectedProvider)
                    streamingViewModel.connectService(
                        serviceId = selectedProvider,
                        serverUrl = serverUrl,
                        username = username,
                        password = password
                    )
                },
                onDisconnect = {
                    streamingViewModel.disconnectService(selectedProvider)
                },
                onSkip = onSkip
            )

            Spacer(modifier = Modifier.height(16.dp))

            StreamingSetupTipsCard()
        }
    }

    if (showDiscoverySheet) {
        NearbyServerDiscoverySheet(
            serviceId = selectedProvider,
            scanner = scanner,
            onDismiss = { showDiscoverySheet = false },
            onServerSelected = { detectedUrl ->
                serverUrl = detectedUrl
                showDiscoverySheet = false
            }
        )
    }
}

@Composable
private fun StreamingSetupTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RhythmIcons.Info,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboardingscreen_streaming_setup_tips),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OnboardingTipItem(
                icon = MaterialSymbolIcon("wifi_find"),
                text = stringResource(R.string.onboardingscreen_setup_auto_scan_tip)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("lock"),
                text = stringResource(R.string.onboardingscreen_setup_remote_tip)
            )
            OnboardingTipItem(
                icon = MaterialSymbolIcon("verified_user"),
                text = stringResource(R.string.onboardingscreen_connection_test_ensures_server)
            )
        }
    }
}

@Composable
private fun StreamingSetupSelectionAndForm(
    selectedProvider: String,
    requiresServerUrl: Boolean,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isConnected: Boolean,
    isLoading: Boolean,
    error: String?,
    canSubmit: Boolean,
    selectedProviderName: String,
    rememberStreamingPasswords: Boolean,
    onRememberPasswordChange: (Boolean) -> Unit,
    onDiscoverClick: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    // Connection Form
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.streaming_service_setup_server_url)) },
                    placeholder = { Text(stringResource(R.string.onboardingscreen_httpsyourservercom)) },
                    trailingIcon = {
                        IconButton(onClick = onDiscoverClick, enabled = !isLoading && !isConnected) {
                            Icon(
                                imageVector = MaterialSymbolIcon("radar"),
                                contentDescription = stringResource(R.string.streaming_service_setup_auto_detect)
                            )
                        }
                    },
                    supportingText = { Text(stringResource(R.string.onboardingscreen_remember_to_include_http)) },
                    singleLine = true,
                    enabled = !isLoading && !isConnected
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboardingscreen_username)) },
                placeholder = { Text(stringResource(R.string.onboardingscreen_enter_server_username)) },
                singleLine = true,
                enabled = !isLoading && !isConnected
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.streaming_service_setup_password)) },
                placeholder = { Text(stringResource(R.string.onboardingscreen_enter_server_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isLoading && !isConnected
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
                    onCheckedChange = onRememberPasswordChange,
                    enabled = !isLoading && !isConnected
                )
            }

            if (isConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("check_circle", filled = true),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.onboardingscreen_successfully_connected),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.onboarding_connected_to_format, selectedProviderName, username),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            } else if (error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("warning", filled = true),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = error,
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

            // Connect/Disconnect Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text(stringResource(R.string.streaming_service_setup_disconnect))
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && canSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.onboardingscreen_connecting))
                        } else {
                            Text(stringResource(R.string.onboardingscreen_connect_verify))
                        }
                    }
                }
            }

            if (!isConnected && onSkip != null) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_streaming_setup_later),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    palette: SettingsBadgePalette? = null,
    itemShape: Shape? = null,
    lastItemShape: Shape? = null,
    iconShape: Shape? = null
) {
    chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup(
        title = title,
        items = items,
        containerColor = containerColor,
        palette = palette,
        itemShape = itemShape,
        lastItemShape = lastItemShape,
        iconShape = iconShape
    )
}

@Composable
private fun RotatingBackgroundCookies(color: Color) {
    val lowerY = remember { Animatable(-600f) }
    val upperY = remember { Animatable(-1000f) }

    LaunchedEffect(Unit) {
        // 1. Fall down
        launch {
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
        
        upperY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearEasing)
        )
        
        // 2. Collision impact
        launch {
            lowerY.animateTo(
                targetValue = 40f,
                animationSpec = tween(durationMillis = 80, easing = androidx.compose.animation.core.FastOutLinearInEasing)
            )
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        
        launch {
            upperY.animateTo(
                targetValue = -60f,
                animationSpec = tween(durationMillis = 120, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )
            upperY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cookieRotation")
    val rotationLower by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 50000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lowerRotation"
    )
    val rotationUpper by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "upperRotation"
    )

    val cookie6Shape = rememberExpressiveShape("COOKIE_6")
    val cookie12Shape = rememberExpressiveShape("COOKIE_12")

    Box(modifier = Modifier.fillMaxSize()) {
        // Lower cookie (COOKIE_6) located at bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(460.dp)
                .graphicsLayer {
                    translationX = -120.dp.toPx()
                    translationY = (140.dp.toPx() + lowerY.value.dp.toPx())
                    rotationZ = rotationLower + 15f
                }
                .clip(cookie6Shape)
                .background(color)
        )

        // Upper cookie (COOKIE_12) located at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(340.dp)
                .graphicsLayer {
                    translationX = 80.dp.toPx()
                    translationY = (-100.dp.toPx() + upperY.value.dp.toPx())
                    rotationZ = rotationUpper - 20f
                }
                .clip(cookie12Shape)
                .background(color)
        )
    }
}

private val androidx.compose.material3.ColorScheme.surfaceHigh: androidx.compose.ui.graphics.Color
    get() = this.surfaceContainer

private val androidx.compose.material3.ColorScheme.surfaceHighest: androidx.compose.ui.graphics.Color
    get() = this.surfaceContainerHighest

@Composable
private fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.ButtonDefaults.outlinedShape,
    colors: androidx.compose.material3.ButtonColors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: androidx.compose.material3.ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.material3.ButtonDefaults.ContentPadding,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
private fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.OutlinedTextFieldDefaults.shape,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}
