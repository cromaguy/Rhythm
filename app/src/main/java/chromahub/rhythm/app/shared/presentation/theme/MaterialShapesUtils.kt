/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Enum representing available expressive Material Shapes from Material 3 Expressive API.
 * These shapes are designed for creating organic, playful, and expressive UI elements.
 */
enum class ExpressiveMaterialShape(
    val displayName: String,
    val description: String,
    val category: ShapeCategory
) {
    // Basic Geometric Shapes
    CIRCLE("Circle", "A perfect circle shape", ShapeCategory.BASIC),
    SQUARE("Square", "A rounded square shape", ShapeCategory.BASIC),
    OVAL("Oval", "An elongated oval shape", ShapeCategory.BASIC),
    PILL("Pill", "A pill/capsule shape", ShapeCategory.BASIC),
    DIAMOND("Diamond", "A classic diamond shape", ShapeCategory.BASIC),
    TRIANGLE("Triangle", "A rounded triangle shape", ShapeCategory.BASIC),
    PENTAGON("Pentagon", "A five-sided polygon", ShapeCategory.BASIC),
    
    // Organic/Nature Shapes
    FLOWER("Flower", "A flower shape with petals", ShapeCategory.ORGANIC),
    CLOVER_4_LEAF("4-Leaf Clover", "A four-leaf clover shape", ShapeCategory.ORGANIC),
    CLOVER_8_LEAF("8-Leaf Clover", "An eight-leaf clover shape", ShapeCategory.ORGANIC),
    HEART("Heart", "A heart shape", ShapeCategory.ORGANIC),
    
    // Playful Shapes
    BOOM("Boom", "An explosion/boom shape", ShapeCategory.PLAYFUL),
    SOFT_BOOM("Soft Boom", "A softer explosion shape", ShapeCategory.PLAYFUL),
    BURST("Burst", "A starburst shape", ShapeCategory.PLAYFUL),
    SOFT_BURST("Soft Burst", "A softer starburst shape", ShapeCategory.PLAYFUL),
    SUNNY("Sunny", "A sun-like shape with rays", ShapeCategory.PLAYFUL),
    VERY_SUNNY("Very Sunny", "A sun with more rays", ShapeCategory.PLAYFUL),
    
    // Cookie Shapes
    COOKIE_4("Cookie 4", "A 4-sided cookie shape", ShapeCategory.COOKIE),
    COOKIE_6("Cookie 6", "A 6-sided cookie shape", ShapeCategory.COOKIE),
    COOKIE_7("Cookie 7", "A 7-sided cookie shape", ShapeCategory.COOKIE),
    COOKIE_9("Cookie 9", "A 9-sided cookie shape", ShapeCategory.COOKIE),
    COOKIE_12("Cookie 12", "A 12-sided cookie shape", ShapeCategory.COOKIE),
    
    // Fun/Whimsical Shapes
    GHOSTISH("Ghostish", "A ghost-like shape", ShapeCategory.WHIMSICAL),
    PUFFY("Puffy", "A puffy cloud-like shape", ShapeCategory.WHIMSICAL),
    PUFFY_DIAMOND("Puffy Diamond", "A puffy diamond shape", ShapeCategory.WHIMSICAL),
    BUN("Bun", "A bun/bread shape", ShapeCategory.WHIMSICAL),
    FAN("Fan", "A fan shape", ShapeCategory.WHIMSICAL),
    ARROW("Arrow", "An arrow pointer shape", ShapeCategory.WHIMSICAL),
    
    // Special Shapes
    ARCH("Arch", "An arch shape", ShapeCategory.SPECIAL),
    CLAM_SHELL("Clam Shell", "A clam shell shape", ShapeCategory.SPECIAL),
    GEM("Gem", "A gemstone shape", ShapeCategory.SPECIAL),
    SEMI_CIRCLE("Semi Circle", "A half circle shape", ShapeCategory.SPECIAL),
    SLANTED("Slanted", "A slanted square shape", ShapeCategory.SPECIAL),
    
    // Pixel Art Shapes
    PIXEL_CIRCLE("Pixel Circle", "A pixelated circle", ShapeCategory.PIXEL),
    PIXEL_TRIANGLE("Pixel Triangle", "A pixelated triangle", ShapeCategory.PIXEL);
    
    /**
     * Get the corresponding MaterialShapes RoundedPolygon
     */
    fun getRoundedPolygon(): RoundedPolygon {
        return when (this) {
            CIRCLE -> MaterialShapes.Circle
            SQUARE -> MaterialShapes.Square
            OVAL -> MaterialShapes.Oval
            PILL -> MaterialShapes.Pill
            DIAMOND -> MaterialShapes.Diamond
            TRIANGLE -> MaterialShapes.Triangle
            PENTAGON -> MaterialShapes.Pentagon
            FLOWER -> MaterialShapes.Flower
            CLOVER_4_LEAF -> MaterialShapes.Clover4Leaf
            CLOVER_8_LEAF -> MaterialShapes.Clover8Leaf
            HEART -> MaterialShapes.Heart
            BOOM -> MaterialShapes.Boom
            SOFT_BOOM -> MaterialShapes.SoftBoom
            BURST -> MaterialShapes.Burst
            SOFT_BURST -> MaterialShapes.SoftBurst
            SUNNY -> MaterialShapes.Sunny
            VERY_SUNNY -> MaterialShapes.VerySunny
            COOKIE_4 -> MaterialShapes.Cookie4Sided
            COOKIE_6 -> MaterialShapes.Cookie6Sided
            COOKIE_7 -> MaterialShapes.Cookie7Sided
            COOKIE_9 -> MaterialShapes.Cookie9Sided
            COOKIE_12 -> MaterialShapes.Cookie12Sided
            GHOSTISH -> MaterialShapes.Ghostish
            PUFFY -> MaterialShapes.Puffy
            PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
            BUN -> MaterialShapes.Bun
            FAN -> MaterialShapes.Fan
            ARROW -> MaterialShapes.Arrow
            ARCH -> MaterialShapes.Arch
            CLAM_SHELL -> MaterialShapes.ClamShell
            GEM -> MaterialShapes.Gem
            SEMI_CIRCLE -> MaterialShapes.SemiCircle
            SLANTED -> MaterialShapes.Slanted
            PIXEL_CIRCLE -> MaterialShapes.PixelCircle
            PIXEL_TRIANGLE -> MaterialShapes.PixelTriangle
        }
    }
}

/**
 * Category for grouping shapes in the UI
 */
enum class ShapeCategory(val displayName: String) {
    BASIC("Basic Shapes"),
    ORGANIC("Organic & Nature"),
    PLAYFUL("Playful & Expressive"),
    COOKIE("Cookie Shapes"),
    WHIMSICAL("Whimsical & Fun"),
    SPECIAL("Special Shapes"),
    PIXEL("Pixel Art")
}

/**
 * Target components that can use expressive shapes
 */
enum class ShapeTarget(
    val displayName: String,
    val description: String
) {
    ALBUM_ART("Album Artwork", "Shape for album artwork on player screen"),
    FAB("Floating Action Button", "Shape for FAB buttons"),
    CARDS("Cards", "Shape for cards and containers"),
    BUTTONS("Buttons", "Shape for buttons"),
    CHIPS("Chips", "Shape for chips and tags"),
    PLAYER_CONTROLS("Player Controls", "Shape for player control buttons"),
    MINI_PLAYER("Mini Player", "Shape for mini player artwork"),
    NAVIGATION_INDICATOR("Navigation Indicator", "Shape for nav bar indicator")
}

/**
 * Convert RoundedPolygon to Compose Shape
 */
fun RoundedPolygon.toComposeShape(): Shape {
    return object : Shape {
        private var lastSize: Size? = null
        private var cachedOutline: Outline? = null

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            if (size == lastSize && cachedOutline != null) {
                return cachedOutline!!
            }
            val path = this@toComposeShape.toPath().asComposePath()
            
            // Scale and center the path to fit the target size
            val matrix = Matrix()
            val bounds = path.getBounds()
            
            // Non-uniform scaling so non-square shapes still fit the target bounds.
            val scaleX = size.width / (bounds.width.takeIf { it > 0f } ?: 1f)
            val scaleY = size.height / (bounds.height.takeIf { it > 0f } ?: 1f)
            
            val scaledWidth = bounds.width * scaleX
            val scaledHeight = bounds.height * scaleY
            val translateX = (size.width - scaledWidth) / 2f - bounds.left * scaleX
            val translateY = (size.height - scaledHeight) / 2f - bounds.top * scaleY
            
            matrix.scale(scaleX, scaleY)
            matrix.translate(translateX / scaleX, translateY / scaleY)
            
            val scaledPath = Path()
            scaledPath.addPath(path)
            scaledPath.transform(matrix)
            
            val outline = Outline.Generic(scaledPath)
            lastSize = size
            cachedOutline = outline
            return outline
        }
    }
}

/**
 * Get a Compose Shape for a specific ExpressiveMaterialShape
 */
@Composable
fun rememberExpressiveShape(shape: ExpressiveMaterialShape): Shape {
    return remember(shape) {
        shape.getRoundedPolygon().toComposeShape()
    }
}

/**
 * Get the default shape for a target (when expressive shapes are disabled)
 */
fun getDefaultShape(target: ShapeTarget): Shape {
    return when (target) {
        ShapeTarget.ALBUM_ART -> RoundedCornerShape(28.dp)
        ShapeTarget.FAB -> CircleShape
        ShapeTarget.CARDS -> RoundedCornerShape(20.dp)
        ShapeTarget.BUTTONS -> CircleShape
        ShapeTarget.CHIPS -> CircleShape
        ShapeTarget.PLAYER_CONTROLS -> CircleShape
        ShapeTarget.MINI_PLAYER -> RoundedCornerShape(16.dp)
        ShapeTarget.NAVIGATION_INDICATOR -> CircleShape
    }
}

/**
 * Object containing commonly used expressive shape presets for different UI contexts
 */
object ExpressiveShapePresets {
    // Default preset - gentle expressive shapes suitable for all ages
    val Default = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.COOKIE_6,
        ShapeTarget.FAB to ExpressiveMaterialShape.CIRCLE,
        ShapeTarget.CARDS to ExpressiveMaterialShape.CLOVER_8_LEAF,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.OVAL,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.PILL,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.CIRCLE,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.COOKIE_4,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.CIRCLE
    )
    
    // Friendly preset - warm and approachable shapes
    val Friendly = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.CLOVER_8_LEAF,
        ShapeTarget.FAB to ExpressiveMaterialShape.SOFT_BURST,
        ShapeTarget.CARDS to ExpressiveMaterialShape.COOKIE_6,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.CLOVER_8_LEAF,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.BUN,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.CIRCLE,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.OVAL,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.HEART
    )
    
    // Modern preset - contemporary expressive design
    val Modern = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.SLANTED,
        ShapeTarget.FAB to ExpressiveMaterialShape.DIAMOND,
        ShapeTarget.CARDS to ExpressiveMaterialShape.COOKIE_7,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.PENTAGON,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.DIAMOND,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.CIRCLE,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.SLANTED,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.DIAMOND
    )
    
    // Playful preset - for fun, casual music apps
    val Playful = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.FLOWER,
        ShapeTarget.FAB to ExpressiveMaterialShape.SOFT_BURST,
        ShapeTarget.CARDS to ExpressiveMaterialShape.COOKIE_6,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.CLOVER_8_LEAF,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.BUN,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.SUNNY,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.SQUARE,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.HEART
    )
    
    // Organic preset - nature-inspired
    val Organic = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.CLOVER_4_LEAF,
        ShapeTarget.FAB to ExpressiveMaterialShape.FLOWER,
        ShapeTarget.CARDS to ExpressiveMaterialShape.CLOVER_8_LEAF,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.OVAL,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.PILL,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.CIRCLE,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.COOKIE_4,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.CLOVER_4_LEAF
    )
    
    // Geometric preset - clean and modern
    val Geometric = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.SQUARE,
        ShapeTarget.FAB to ExpressiveMaterialShape.DIAMOND,
        ShapeTarget.CARDS to ExpressiveMaterialShape.SQUARE,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.PENTAGON,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.DIAMOND,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.CIRCLE,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.SQUARE,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.DIAMOND
    )
    
    // Retro/Pixel preset - nostalgic gaming feel
    val Retro = mapOf(
        ShapeTarget.ALBUM_ART to ExpressiveMaterialShape.PIXEL_CIRCLE,
        ShapeTarget.FAB to ExpressiveMaterialShape.PIXEL_CIRCLE,
        ShapeTarget.CARDS to ExpressiveMaterialShape.SQUARE,
        ShapeTarget.BUTTONS to ExpressiveMaterialShape.PIXEL_CIRCLE,
        ShapeTarget.CHIPS to ExpressiveMaterialShape.PIXEL_TRIANGLE,
        ShapeTarget.PLAYER_CONTROLS to ExpressiveMaterialShape.PIXEL_CIRCLE,
        ShapeTarget.MINI_PLAYER to ExpressiveMaterialShape.SQUARE,
        ShapeTarget.NAVIGATION_INDICATOR to ExpressiveMaterialShape.PIXEL_CIRCLE
    )
}

/**
 * Preset identifiers for saving/loading
 */
enum class ShapePreset(val displayName: String, val description: String) {
    DEFAULT("Default", "Gentle expressive shapes for all ages"),
    FRIENDLY("Friendly", "Warm and approachable shapes"),
    MODERN("Modern", "Contemporary expressive design"),
    PLAYFUL("Playful", "Fun and expressive shapes"),
    ORGANIC("Organic", "Nature-inspired shapes"),
    GEOMETRIC("Geometric", "Clean and modern shapes"),
    RETRO("Retro", "Pixelated nostalgic shapes"),
    CUSTOM("Custom", "Your personalized shape selection")
}
