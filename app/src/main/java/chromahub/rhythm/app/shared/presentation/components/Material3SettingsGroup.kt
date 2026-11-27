/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingHighlightController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

/**
 * Stock Android / Pixel Settings Badge Palette with uniform luminous colors across light & dark themes.
 */
data class SettingsBadgePalette(
    val container: Color,
    val tint: Color
) {
    constructor(
        containerLight: Color,
        tintLight: Color,
        containerDark: Color = containerLight,
        tintDark: Color = tintLight
    ) : this(containerLight, tintLight)

    val containerLight: Color get() = container
    val tintLight: Color get() = tint
    val containerDark: Color get() = container
    val tintDark: Color get() = tint

    @Composable
    fun containerColor(isDark: Boolean = false): Color = container

    @Composable
    fun iconTintColor(isDark: Boolean = false): Color = tint
}

/**
 * Predefined Stock Android / Pixel Settings Badge Palettes (Material 3 Expressive)
 */
object SettingsPalettes {
    val Amber = SettingsBadgePalette(
        container = Color(0xFFFFDCBE),
        tint = Color(0xFF7C4113)
    )
    val Yellow = SettingsBadgePalette(
        container = Color(0xFFFFF0A4),
        tint = Color(0xFF624F00)
    )
    val Emerald = SettingsBadgePalette(
        container = Color(0xFFC4EED0),
        tint = Color(0xFF18532A)
    )
    val Cyan = SettingsBadgePalette(
        container = Color(0xFFC0EAE6),
        tint = Color(0xFF006A6F)
    )
    val SkyBlue = SettingsBadgePalette(
        container = Color(0xFFC2E7FF),
        tint = Color(0xFF004D77)
    )
    val Indigo = SettingsBadgePalette(
        container = Color(0xFFD6E3FF),
        tint = Color(0xFF1D5FA9)
    )
    val Purple = SettingsBadgePalette(
        container = Color(0xFFEEDCFF),
        tint = Color(0xFF5D367B)
    )
    val Rose = SettingsBadgePalette(
        container = Color(0xFFF7D8EB),
        tint = Color(0xFF82276C)
    )
    val Coral = SettingsBadgePalette(
        container = Color(0xFFFFD8E2),
        tint = Color(0xFF962846)
    )
    val Lime = SettingsBadgePalette(
        container = Color(0xFFE0F3B8),
        tint = Color(0xFF3B5606)
    )
    val Slate = SettingsBadgePalette(
        container = Color(0xFFE2E2E6),
        tint = Color(0xFF44474E)
    )
    val Teal = SettingsBadgePalette(
        container = Color(0xFFBCEBEB),
        tint = Color(0xFF006874)
    )
    val Orange = SettingsBadgePalette(
        container = Color(0xFFFFDAC5),
        tint = Color(0xFF763710)
    )
}

object SettingsExpressiveShapes {
    val Circle: Shape = CircleShape
    val Squircle: Shape = RoundedCornerShape(12.dp)
}

fun getSectionPalette(title: String?): SettingsBadgePalette {
    if (title.isNullOrBlank()) return SettingsPalettes.Purple
    val lower = title.lowercase()
    return when {
        lower.contains("appearance") || lower.contains("theme") || lower.contains("look") || lower.contains("shape") || lower.contains("style") -> SettingsPalettes.Purple
        lower.contains("home") || lower.contains("widget") || lower.contains("order") -> SettingsPalettes.Orange
        lower.contains("interface") || lower.contains("navigation") || lower.contains("control") || lower.contains("gesture") -> SettingsPalettes.SkyBlue
        lower.contains("queue") || lower.contains("playback") || lower.contains("volume") || lower.contains("persist") || lower.contains("gain") -> SettingsPalettes.Emerald
        lower.contains("audio") || lower.contains("sound") || lower.contains("lyrics") || lower.contains("equalizer") || lower.contains("effect") || lower.contains("format") -> SettingsPalettes.Rose
        lower.contains("library") || lower.contains("media") || lower.contains("scan") || lower.contains("artist") || lower.contains("playlist") || lower.contains("directory") || lower.contains("folder") -> SettingsPalettes.Amber
        lower.contains("notification") || lower.contains("alert") || lower.contains("service") -> SettingsPalettes.Coral
        lower.contains("storage") || lower.contains("data") || lower.contains("cache") || lower.contains("backup") || lower.contains("guard") || lower.contains("stat") || lower.contains("room") || lower.contains("backend") -> SettingsPalettes.Yellow
        lower.contains("update") || lower.contains("about") || lower.contains("info") || lower.contains("version") -> SettingsPalettes.Slate
        lower.contains("advanced") || lower.contains("crash") || lower.contains("experiment") || lower.contains("dev") -> SettingsPalettes.Purple
        lower.contains("performance") || lower.contains("battery") || lower.contains("power") || lower.contains("speed") -> SettingsPalettes.Lime
        lower.contains("network") || lower.contains("api") || lower.contains("source") || lower.contains("connectivity") -> SettingsPalettes.SkyBlue
        lower.contains("display") || lower.contains("screen") || lower.contains("carousel") -> SettingsPalettes.Amber
        else -> {
            val list = listOf(
                SettingsPalettes.Purple,
                SettingsPalettes.SkyBlue,
                SettingsPalettes.Emerald,
                SettingsPalettes.Amber,
                SettingsPalettes.Yellow,
                SettingsPalettes.Coral,
                SettingsPalettes.Cyan,
                SettingsPalettes.Rose,
                SettingsPalettes.Indigo,
                SettingsPalettes.Teal,
                SettingsPalettes.Orange,
                SettingsPalettes.Lime
            )
            val index = (title.hashCode().toLong() and 0x7FFFFFFF).toInt() % list.size
            list[index]
        }
    }
}

fun getIconPalette(icon: Any?): SettingsBadgePalette {
    val rawName = when (icon) {
        is MaterialSymbolIcon -> icon.name
        is ImageVector -> icon.name
        else -> null
    }
    if (rawName.isNullOrBlank()) return SettingsPalettes.Purple

    val lower = rawName.lowercase()
    return when {
        lower.contains("palette") || lower.contains("brush") || lower.contains("color") ||
            lower.contains("theme") || lower.contains("art") || lower.contains("paint") ||
            lower.contains("interests") || lower.contains("shape") || lower.contains("corner") ||
            lower.contains("category") || lower.contains("style") || lower.contains("auto_graph") ||
            lower.contains("stat") -> SettingsPalettes.Purple

        lower.contains("album") || lower.contains("image") || lower.contains("photo") ||
            lower.contains("picture") || lower.contains("wallpaper") || lower.contains("blur") ||
            lower.contains("backdrop") || lower.contains("glow") || lower.contains("artist") ||
            lower.contains("person") || lower.contains("face") || lower.contains("favorite") ||
            lower.contains("heart") || lower.contains("play_circle") -> SettingsPalettes.Rose

        lower.contains("notification") || lower.contains("alert") || lower.contains("bell") ||
            lower.contains("equalizer") || lower.contains("audio") || lower.contains("sound") ||
            lower.contains("volume") || lower.contains("high_quality") || lower.contains("speaker") ||
            lower.contains("music") || lower.contains("bug") || lower.contains("playlist") -> SettingsPalettes.Coral

        lower.contains("wifi") || lower.contains("network") || lower.contains("cloud") ||
            lower.contains("public") || lower.contains("globe") || lower.contains("language") ||
            lower.contains("translate") || lower.contains("update") || lower.contains("download") ||
            lower.contains("search") || lower.contains("explore") || lower.contains("tablet") ||
            lower.contains("phone") || lower.contains("devices") || lower.contains("cast") ||
            lower.contains("touch") || lower.contains("gesture") || lower.contains("hand") -> SettingsPalettes.SkyBlue

        lower.contains("reorder") || lower.contains("queue") || lower.contains("list") ||
            lower.contains("apps") || lower.contains("grid") || lower.contains("call_split") ||
            lower.contains("split") || lower.contains("merge") || lower.contains("alt_route") ||
            lower.contains("sort") || lower.contains("swap") || lower.contains("shuffle") ||
            lower.contains("repeat") || lower.contains("navigation") || lower.contains("compass") -> SettingsPalettes.Indigo

        lower.contains("display") || lower.contains("screen") || lower.contains("brightness") ||
            lower.contains("sun") || lower.contains("contrast") || lower.contains("lightbulb") ||
            lower.contains("bulb") || lower.contains("idea") || lower.contains("tips") ||
            lower.contains("gradient") || lower.contains("aspect") -> SettingsPalettes.Amber

        lower.contains("home") || lower.contains("timer") || lower.contains("time") ||
            lower.contains("clock") || lower.contains("alarm") || lower.contains("scale") ||
            lower.contains("linear") || lower.contains("slider") || lower.contains("forward") ||
            lower.contains("rewind") || lower.contains("fast") || lower.contains("seek") -> SettingsPalettes.Orange

        lower.contains("storage") || lower.contains("folder") || lower.contains("sd") ||
            lower.contains("hard_drive") || lower.contains("disk") || lower.contains("data") ||
            lower.contains("save") || lower.contains("memory") || lower.contains("chip") ||
            lower.contains("document") || lower.contains("file") -> SettingsPalettes.Yellow

        lower.contains("security") || lower.contains("guard") || lower.contains("shield") ||
            lower.contains("lock") || lower.contains("key") || lower.contains("backup") ||
            lower.contains("restore") || lower.contains("check") || lower.contains("verified") ||
            lower.contains("play") || lower.contains("visibility") || lower.contains("eye") ||
            lower.contains("autorenew") || lower.contains("sync") -> SettingsPalettes.Emerald

        lower.contains("speed") || lower.contains("bolt") || lower.contains("battery") ||
            lower.contains("power") || lower.contains("flash") || lower.contains("fast_forward") ||
            lower.contains("rocket") || lower.contains("electric") -> SettingsPalettes.Lime

        lower.contains("widget") || lower.contains("lyrics") || lower.contains("subtitle") ||
            lower.contains("rotate") || lower.contains("refresh") || lower.contains("loop") ||
            lower.contains("bluetooth") || lower.contains("colorize") || lower.contains("tune") -> SettingsPalettes.Cyan

        lower.contains("info") || lower.contains("help") || lower.contains("about") ||
            lower.contains("code") || lower.contains("terminal") || lower.contains("science") ||
            lower.contains("settings") || lower.contains("build") || lower.contains("version") -> SettingsPalettes.Slate

        else -> {
            val list = listOf(
                SettingsPalettes.SkyBlue,
                SettingsPalettes.Emerald,
                SettingsPalettes.Amber,
                SettingsPalettes.Coral,
                SettingsPalettes.Purple,
                SettingsPalettes.Cyan,
                SettingsPalettes.Rose,
                SettingsPalettes.Indigo,
                SettingsPalettes.Yellow,
                SettingsPalettes.Orange,
                SettingsPalettes.Lime,
                SettingsPalettes.Teal
            )
            val index = (lower.hashCode().toLong() and 0x7FFFFFFF).toInt() % list.size
            list[index]
        }
    }
}

/**
 * Material 3 Expressive settings group — card stack with dynamic corner radii.
 *
 * Single item → fully rounded 24dp
 * First item → 24dp top, 6dp bottom
 * Middle items → 6dp all
 * Last item → 6dp top, 24dp bottom
 *
 * When [itemShape] is provided it overrides the corner radii for every card in
 * the group, letting the group blend into a larger connected card stack. When
 * [lastItemShape] is provided it overrides only the last card (taking precedence
 * over [itemShape]) so the group can cap the bottom of a connected stack.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    palette: SettingsBadgePalette? = null,
    itemShape: Shape? = null,
    lastItemShape: Shape? = null,
    iconShape: Shape? = null
) {
    val sectionPalette = palette ?: if (title != null) getSectionPalette(title) else null

    Column(modifier = Modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 18.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isLast = index == items.size - 1
                val shape = when {
                    lastItemShape != null && isLast -> lastItemShape
                    itemShape != null -> itemShape
                    items.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(
                        topStart = 24.dp, topEnd = 24.dp,
                        bottomStart = 6.dp, bottomEnd = 6.dp
                    )
                    isLast -> RoundedCornerShape(
                        topStart = 6.dp, topEnd = 6.dp,
                        bottomStart = 24.dp, bottomEnd = 24.dp
                    )
                    else -> RoundedCornerShape(6.dp)
                }

                val itemPalette = item.palette ?: sectionPalette ?: getIconPalette(item.icon)
                val activeHighlightKey by SettingHighlightController.highlightedKey.collectAsState()
                val activeHighlightTitle by SettingHighlightController.highlightedTitle.collectAsState()
                val isSearchHighlighted = SettingHighlightController.matches(
                    targetKey = activeHighlightKey,
                    targetTitle = activeHighlightTitle,
                    itemKey = item.settingKey,
                    itemTitle = item.settingTitle
                )
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                if (isSearchHighlighted) {
                    LaunchedEffect(activeHighlightKey, activeHighlightTitle) {
                        delay(150L)
                        runCatching { bringIntoViewRequester.bringIntoView() }
                        delay(2200L)
                        SettingHighlightController.clear()
                    }
                }
                val resolvedContainerColor by animateColorAsState(
                    targetValue = if (isSearchHighlighted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                    } else {
                        containerColor
                    },
                    label = "settings_search_highlight_bg"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .animateContentSize(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = resolvedContainerColor
                    ),
                    border = if (isSearchHighlighted) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Material3SettingsItemRow(
                        item = item,
                        fallbackPalette = itemPalette,
                        fallbackShape = item.iconShape ?: iconShape ?: CircleShape
                    )
                }
            }
        }
    }
}

@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
    fallbackPalette: SettingsBadgePalette? = null,
    fallbackShape: Shape = CircleShape
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemPalette = item.palette ?: fallbackPalette

    val defaultIconBg = when {
        item.palette != null -> item.palette.containerColor(isDark)
        fallbackPalette != null -> fallbackPalette.containerColor(isDark)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val defaultIconTint = when {
        item.palette != null -> item.palette.iconTintColor(isDark)
        fallbackPalette != null -> fallbackPalette.iconTintColor(isDark)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Expressive press scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "setting_item_scale"
    )

    // Animated icon background color
    val iconBgColor by animateColorAsState(
        targetValue = when {
            !item.enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
            isPressed -> (item.iconBackgroundTint ?: defaultIconBg).copy(alpha = 0.86f)
            else -> (item.iconBackgroundTint ?: defaultIconBg)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_bg_color"
    )

    // Animated icon color
    val iconColor by animateColorAsState(
        targetValue = if (!item.enabled)
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else
            (item.iconTint ?: defaultIconTint),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = item.enabled && item.onClick != null,
                interactionSource = interactionSource,
                indication = null,
                onClick = { item.onClick?.invoke() }
            )
            .padding(horizontal = 21.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with circle background or custom leadingContent
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(16.dp))
        } else item.icon?.let { icon ->
            Surface(
                modifier = Modifier.size(40.dp),
                shape = item.iconShape ?: fallbackShape,
                color = iconBgColor,
                tonalElevation = if (item.isHighlighted) 2.dp else 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (icon) {
                        is MaterialSymbolIcon -> {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        is ImageVector -> {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Title + description
        Column(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(
                    color = if (!item.enabled)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            ) {
                item.title()
            }

            val scope = item.scope
            if (scope != SettingScope.BOTH) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = when (scope) {
                        SettingScope.LOCAL -> MaterialTheme.colorScheme.secondaryContainer
                        SettingScope.STREAMING -> MaterialTheme.colorScheme.primaryContainer
                        SettingScope.BOTH -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = when (scope) {
                            SettingScope.LOCAL -> "Local"
                            SettingScope.STREAMING -> "Streaming"
                            SettingScope.BOTH -> "Both"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = when (scope) {
                            SettingScope.LOCAL -> MaterialTheme.colorScheme.onSecondaryContainer
                            SettingScope.STREAMING -> MaterialTheme.colorScheme.onPrimaryContainer
                            SettingScope.BOTH -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    desc()
                }
            }
        }

        // Trailing content (switch, chevron, etc.)
        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Data class for a Material 3 Expressive settings item.
 */
data class Material3SettingsItem(
    val icon: Any? = null,
    val iconTint: Color? = null,
    val iconBackgroundTint: Color? = null,
    val palette: SettingsBadgePalette? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val isHighlighted: Boolean = false,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val scope: SettingScope = SettingScope.BOTH,
    val leadingContent: (@Composable () -> Unit)? = null,
    val settingKey: String? = null,
    val settingTitle: String? = null,
    val onClick: (() -> Unit)? = null
)

enum class SettingScope {
    LOCAL,
    STREAMING,
    BOTH
}

