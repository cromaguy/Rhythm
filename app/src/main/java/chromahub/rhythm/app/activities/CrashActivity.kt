/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.activities

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect

import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import chromahub.rhythm.app.ui.theme.RhythmTheme
import kotlin.system.exitProcess
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import chromahub.rhythm.app.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.EaseInOut
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG)

        setContent {
            RhythmTheme {
                CrashScreen(crashLog = crashLog)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CrashScreen(crashLog: String?) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Responsive sizing
        val isTablet = windowScreenWidthDp() >= 600
        val contentMaxWidth = if (isTablet) 1000.dp else 600.dp
        val startPadding = if (isTablet) 60.dp else 30.dp
        val endPadding = if (isTablet) 60.dp else 30.dp
        val topPadding = if (isTablet) 50.dp else 40.dp
        val bottomPadding = if (isTablet) 50.dp else 32.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            RotatingBackgroundCookies(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))

            // Crash card container
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = if (isTablet) RoundedCornerShape(32.dp) else androidx.compose.ui.graphics.RectangleShape,
                border = if (isTablet) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
                tonalElevation = 0.dp,
                modifier = if (isTablet) {
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 750.dp)
                        .fillMaxHeight(0.9f)
                } else {
                    Modifier.fillMaxSize()
                }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
            ) {

                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = startPadding,
                                end = endPadding,
                                top = topPadding,
                                bottom = bottomPadding
                            ),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left column: Icon (centered, 72.dp), Title, Subtitle, branding, and action buttons
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Bug icon with animation
                            val infiniteTransition = rememberInfiniteTransition(label = "bug_shake")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = -5f,
                                targetValue = 5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = EaseInOut),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bug_rotation"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { rotationZ = rotation }
                                    .padding(bottom = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.BugReport,
                                    contentDescription = stringResource(R.string.crash_bug_report),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(72.dp)
                                )
                            }

                            Text(
                                text = stringResource(R.string.crashactivity_uh_oh_looks_like),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = stringResource(R.string.crashactivity_dont_fret_our_app),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // App logo and name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.rhythm_splash_logo),
                                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.cd_rhythm_splash),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Buttons at bottom of left column
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Share button
                                val shareButtonScale = remember { Animatable(1f) }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            shareButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                            shareButtonScale.animateTo(1f, animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessHigh
                                            ))
                                        }
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Rhythm App Crash Log:\n\n$crashLog")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Crash Log"))
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = shareButtonScale.value
                                            scaleY = shareButtonScale.value
                                        },
                                    shape = RoundedCornerShape(32.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.crashactivity_share), style = MaterialTheme.typography.labelLarge)
                                }

                                // Restart button
                                val restartButtonScale = remember { Animatable(1f) }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            restartButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                            restartButtonScale.animateTo(1f, animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessHigh
                                            ))
                                        }
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        exitProcess(0)
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = restartButtonScale.value
                                            scaleY = restartButtonScale.value
                                        },
                                    shape = RoundedCornerShape(32.dp)
                                ) {
                                    Text(stringResource(R.string.crash_restart_app), style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = RhythmIcons.Forward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Right column: Crash log text field (centered vertically/horizontally)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            OutlinedTextField(
                                value = crashLog ?: "No funny business here, just a crash log!",
                                onValueChange = { /* Read-only */ },
                                label = { Text(stringResource(R.string.crashactivity_secret_crash_scrolls)) },
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }

                    }
                } else {
                    // Mobile layout Column
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = startPadding,
                                end = endPadding,
                                top = topPadding,
                                bottom = bottomPadding
                            ),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Bug illustration/icon centered
                        val infiniteTransition = rememberInfiniteTransition(label = "bug_shake")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = -5f,
                            targetValue = 5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = EaseInOut),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bug_rotation"
                        )

                        AnimatedVisibility(
                            visible = true,
                            enter = scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeIn()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { rotationZ = rotation }
                                    .padding(bottom = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.BugReport,
                                    contentDescription = stringResource(R.string.crash_bug_report),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }

                        // Left-aligned texts
                        Text(
                            text = stringResource(R.string.crashactivity_uh_oh_looks_like),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = stringResource(R.string.crashactivity_dont_fret_our_app),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )

                        // App logo and name at center
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 28.dp)
                        ) {
                            AnimatedVisibility(
                                visible = true,
                                enter = scaleIn(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = tween(1000))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.rhythm_splash_logo),
                                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(3.dp))

                            AnimatedVisibility(
                                visible = true,
                                enter = scaleIn(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(animationSpec = tween(800, delayMillis = 200))
                            ) {
                                Text(
                                    text = stringResource(R.string.cd_rhythm_splash),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Crash scrolls text field
                        OutlinedTextField(
                            value = crashLog ?: "No funny business here, just a crash log!",
                            onValueChange = { /* Read-only */ },
                            label = { Text(stringResource(R.string.crashactivity_secret_crash_scrolls)) },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .padding(bottom = 40.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Share button 
                            val shareButtonScale = remember { Animatable(1f) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        shareButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                        shareButtonScale.animateTo(1f, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        ))
                                    }
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Rhythm App Crash Log:\n\n$crashLog")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Crash Log"))
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .weight(1f)
                                    .graphicsLayer {
                                        scaleX = shareButtonScale.value
                                        scaleY = shareButtonScale.value
                                    },
                                shape = RoundedCornerShape(32.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.crashactivity_share), style = MaterialTheme.typography.labelLarge)
                            }

                            // Restart button 
                            val restartButtonScale = remember { Animatable(1f) }
                            Button(
                                onClick = {
                                    scope.launch {
                                        restartButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                        restartButtonScale.animateTo(1f, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        ))
                                    }
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    exitProcess(0)
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .weight(1f)
                                    .graphicsLayer {
                                        scaleX = restartButtonScale.value
                                        scaleY = restartButtonScale.value
                                    },
                                shape = RoundedCornerShape(32.dp)
                            ) {
                                Text(stringResource(R.string.crash_restart_app), style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = RhythmIcons.Forward,
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

    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"

        fun start(context: Context, crashLog: String) {
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewCrashScreen() {
        RhythmTheme {
            CrashScreen(crashLog = "Sample crash log details here.\nAnother line of log.")
        }
    }
}

@Composable
private fun RotatingBackgroundCookies(color: Color) {
    val lowerY = remember { Animatable(-600f) }
    val upperY = remember { Animatable(-1000f) }

    LaunchedEffect(Unit) {
        this.launch {
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
        
        upperY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearEasing)
        )
        
        this.launch {
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
        
        this.launch {
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

