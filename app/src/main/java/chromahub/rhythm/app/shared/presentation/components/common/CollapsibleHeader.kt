package chromahub.rhythm.app.shared.presentation.components.common

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.ui.theme.RhythmTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.shared.data.model.Artist
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleHeaderScreen(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable () -> Unit = {},
    filterDropdown: @Composable () -> Unit = {}, // New parameter for the filter dropdown
    headerContent: (@Composable () -> Unit)? = null,
    headerContentSpacing: Dp = 8.dp,
    scrollBehaviorKey: String? = null, // Key for preserving scroll behavior state
    showAppIcon: Boolean = false,
    iconVisibilityMode: Int = 0, // 0=Both, 1=Expanded Only, 2=Collapsed Only
    headerDisplayMode: Int = 1, // 0=Icon Only, 1=Name Only, 2=Both
    alwaysCollapsed: Boolean = false, // Whether to start with header collapsed (override for specific screens)
    containerColor: Color = Color.Transparent, // Custom container color for header
    content: @Composable (Modifier) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
    val globalCollapseBehavior by appSettings.headerCollapseBehavior.collectAsState()
    
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        topAppBarState,
        canScroll = { true }
    )
    
    // Apply global collapse behavior or screen-specific override
    val shouldStartCollapsed = alwaysCollapsed || globalCollapseBehavior == 1
    
    // If shouldStartCollapsed is true, set the initial state to fully collapsed
    LaunchedEffect(shouldStartCollapsed) {
        if (shouldStartCollapsed) {
            topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        }
    }

    // Entrance animation state
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(50) // Small delay for smoother transition
        showContent = true
    }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "contentAlpha"
    )
    
    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = tween(durationMillis = 450),
        label = "contentOffset"
    )

    val headerBlendHeight = 24.dp
    val headerBlendBaseColor = if (containerColor == Color.Transparent) {
        MaterialTheme.colorScheme.surface
    } else {
        containerColor
    }

    val lazyListState = rememberLazyListState()
    var expandedHeaderHeight by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            val fontSize = (24 + (32 - 24) * (1 - collapsedFraction)).sp // Interpolate between 24sp and 32sp

            Column(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    if (scrollBehavior.state.heightOffset == 0f || expandedHeaderHeight == 0) {
                        expandedHeaderHeight = coordinates.size.height - scrollBehavior.state.heightOffset.toInt()
                    }
                }
            ) {
                Spacer(modifier = Modifier.height(10.dp)) // Add more padding before the header starts
                LargeTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 14.dp)
                        ) {
                            // Determine visibility based on scroll state
                            // collapsedFraction: 0 = fully expanded, 1 = fully collapsed
                            val isExpanded = collapsedFraction < 0.5f
                            val isCollapsed = collapsedFraction >= 0.5f
                            
                            // Header display mode: 0=Icon Only, 1=Name Only, 2=Both
                            // Visibility mode: 0=Always, 1=Expanded Only, 2=Collapsed Only
                            
                            val shouldShowIcon = when {
                                // "Both" mode - always show both icon and title regardless of visibility setting
                                headerDisplayMode == 2 -> true
                                // "Icon Only" mode - show icon in selected visibility state, show name in opposite state
                                headerDisplayMode == 0 -> when (iconVisibilityMode) {
                                    0 -> true // Always show icon
                                    1 -> isExpanded // Show icon when expanded, name when collapsed
                                    2 -> isCollapsed // Show icon when collapsed, name when expanded
                                    else -> true
                                }
                                // "Name Only" mode - show name in selected visibility state, show icon in opposite state
                                headerDisplayMode == 1 -> when (iconVisibilityMode) {
                                    0 -> false // Always show name only
                                    1 -> isCollapsed // Show name when expanded, icon when collapsed
                                    2 -> isExpanded // Show name when collapsed, icon when expanded
                                    else -> false
                                }
                                else -> false
                            }
                            
                            val shouldShowTitle = when {
                                // "Both" mode - always show both icon and title regardless of visibility setting
                                headerDisplayMode == 2 -> true
                                // "Name Only" mode - show name in selected visibility state, show icon in opposite state
                                headerDisplayMode == 1 -> when (iconVisibilityMode) {
                                    0 -> true // Always show name
                                    1 -> isExpanded // Show name when expanded, icon when collapsed
                                    2 -> isCollapsed // Show name when collapsed, icon when expanded
                                    else -> true
                                }
                                // "Icon Only" mode - show icon in selected visibility state, show name in opposite state
                                headerDisplayMode == 0 -> when (iconVisibilityMode) {
                                    0 -> false // Always show icon only
                                    1 -> isCollapsed // Show icon when expanded, name when collapsed
                                    2 -> isExpanded // Show icon when collapsed, name when expanded
                                    else -> false
                                }
                                else -> false
                            }
                            
                            if (shouldShowIcon) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = chromahub.rhythm.app.R.drawable.rhythm_splash_logo),
                                    contentDescription = stringResource(R.string.collapsibleheader_app_icon),
                                    modifier = Modifier.size((48 + (36 - 28) * (1 - collapsedFraction)).dp)
                                )
                            }
                            if (shouldShowTitle) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fontSize
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.padding(start = 12.dp) // Add padding to the back button
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp) // Adjust size as needed
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh), // Circular background 
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Back,
                                        contentDescription = stringResource(R.string.cd_back),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 10.dp) // Match left-side padding
                        ) {
                            filterDropdown() // Place the filter dropdown here
                            actions()
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        scrolledContainerColor = containerColor
                    )
                )
                if (headerContent != null) {
                    Spacer(modifier = Modifier.height(headerContentSpacing))
                    headerContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerBlendHeight)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    headerBlendBaseColor,
                                    headerBlendBaseColor.copy(alpha = 0.72f),
                                    headerBlendBaseColor.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    ) { paddingValues ->
        val density = LocalDensity.current
        val baseTopPadding = remember(expandedHeaderHeight, paddingValues) {
            if (expandedHeaderHeight > 0) {
                with(density) { expandedHeaderHeight.toDp() }
            } else {
                paddingValues.calculateTopPadding()
            }
        }
        // Dynamically adjust top padding based on header collapse progress.
        // heightOffset is 0 when expanded and negative when collapsing, so adding it
        // shrinks the top padding proportionally — avoiding a visual layout gap at
        // the screen bottom that the old offset() approach used to cause.
        val dynamicTopPadding = with(density) {
            (baseTopPadding.toPx() + scrollBehavior.state.heightOffset).toDp()
                .coerceAtLeast(headerBlendHeight)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = (dynamicTopPadding - headerBlendHeight).coerceAtLeast(0.dp))
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                }
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistCollapsibleHeaderScreen(
    title: String,
    artist: Artist?,
    artworkUri: String?,
    artistName: String,
    artistSongsCount: Int,
    artistAlbumsCount: Int,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable () -> Unit = {},
    filterDropdown: @Composable () -> Unit = {},
    scrollBehaviorKey: String? = null,
    showAppIcon: Boolean = false,
    iconVisibilityMode: Int = 0,
    headerDisplayMode: Int = 1,
    alwaysCollapsed: Boolean = false,
    containerColor: Color = Color.Transparent,
    content: @Composable (Modifier) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
    val globalCollapseBehavior by appSettings.headerCollapseBehavior.collectAsState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        topAppBarState,
        canScroll = { true }
    )

    var expandedHeaderHeight by remember { mutableStateOf(0) }

    // Apply global collapse behavior or screen-specific override
    val shouldStartCollapsed = alwaysCollapsed || globalCollapseBehavior == 1

    // If shouldStartCollapsed is true, set the initial state to fully collapsed
    LaunchedEffect(shouldStartCollapsed) {
        if (shouldStartCollapsed) {
            topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        }
    }

    // Entrance animation state
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50) // Small delay for smoother transition
        showContent = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "contentAlpha"
    )

    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = tween(durationMillis = 450),
        label = "contentOffset"
    )

    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed background art - doesn't scroll with content
        val collapsedFraction = scrollBehavior.state.collapsedFraction
        if (collapsedFraction < 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .apply(
                            chromahub.rhythm.app.util.ImageUtils.buildImageRequest(
                                artworkUri,
                                artistName,
                                context.cacheDir,
                                chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType.ARTIST
                            )
                        )
                        .build(),
                    contentDescription = stringResource(R.string.artist_artwork_description, artistName),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                )

                // Multi-layer gradient overlay for better readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )

                // Artist Info - Bottom aligned, only visible when expanded
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$artistAlbumsCount albums",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons.Music.Song,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "$artistSongsCount songs",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scaffold with transparent topBar that overlays the background
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                val collapsedFraction = scrollBehavior.state.collapsedFraction
                val fontSize = (24 + (32 - 24) * (1 - collapsedFraction)).sp

                Column(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        if (scrollBehavior.state.heightOffset == 0f || expandedHeaderHeight == 0) {
                            expandedHeaderHeight = coordinates.size.height - scrollBehavior.state.heightOffset.toInt()
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LargeTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(start = 14.dp)
                            ) {
                                // For artist screen, only show title when collapsed (normal behavior)
                                val shouldShowTitle = collapsedFraction >= 0.5f

                                if (shouldShowTitle) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = fontSize
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (showBackButton) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Back,
                                            contentDescription = stringResource(R.string.cd_back),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                filterDropdown()
                                actions()
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = containerColor
                        )
                    )
                    // Intentionally no header blend here for artist screen —
                    // the artwork background already includes its own gradient overlay.
                }
            }
        ) { paddingValues ->
            val density = LocalDensity.current
            val baseTopPadding = remember(expandedHeaderHeight, paddingValues) {
                if (expandedHeaderHeight > 0) {
                    with(density) { expandedHeaderHeight.toDp() }
                } else {
                    paddingValues.calculateTopPadding()
                }
            }
            // Dynamically adjust top padding to follow header collapse
            // without the visual bottom-gap caused by offset().
            val dynamicTopPadding = with(density) {
                (baseTopPadding.toPx() + scrollBehavior.state.heightOffset).toDp()
                    .coerceAtLeast(0.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = dynamicTopPadding)
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset
                    }
            ) {
                content(Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedHeaderScreen(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable () -> Unit = {},
    screenModifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    backButtonContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    backButtonContentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable (Modifier) -> Unit
) {
    // Entrance animation state
    var showContent by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(50) // Small delay for smoother transition
        showContent = true
    }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "contentAlpha"
    )
    
    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = tween(durationMillis = 450),
        label = "contentOffset"
    )

    Scaffold(
        modifier = screenModifier.fillMaxSize(),
        containerColor = containerColor,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 14.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(backButtonContainerColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Back,
                                        contentDescription = stringResource(R.string.cd_back),
                                        tint = backButtonContentColor,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            actions()
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        scrolledContainerColor = containerColor
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                }
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CollapsibleHeaderScreenPreview() {
    RhythmTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CollapsibleHeaderScreen(
                title = stringResource(R.string.settings_backup_settings),
                showBackButton = true,
                onBackClick = { /* Handle back click in preview */ }
            ) { modifier ->
                LazyColumn(
                    modifier = modifier.padding(horizontal = 16.dp) // Consistent horizontal padding for content
                ) {
                    items(50) { index ->
                        Text(text = "Item $index", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

