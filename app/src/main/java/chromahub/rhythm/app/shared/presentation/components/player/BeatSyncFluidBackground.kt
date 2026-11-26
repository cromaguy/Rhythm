/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import chromahub.rhythm.app.infrastructure.audio.haptics.MusicHapticsController
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val FLOW_AGSL = """
uniform shader curArt;
uniform shader prevArt;
uniform float2 curSize;
uniform float2 prevSize;
uniform float2 viewSize;
uniform float3 angles;
uniform float3 motion;
uniform float3 lobes;
uniform float morph;
uniform float dim;
uniform float2 tone;

float2 rotateAt(float2 p, float2 c, float a) {
    float co = cos(a), s = sin(a);
    p -= c;
    return float2(co * p.x + s * p.y, -s * p.x + co * p.y) + c;
}

half3 sampleArt(shader art, float2 artSize, float2 uv, float q) {
    float2 cropped = uv * 0.5 + (q < 1.5 ? float2(0.0, 0.0) : float2(0.5, 0.5));
    if (q < 0.5) cropped = uv;
    return art.eval(clamp(cropped * artSize, float2(0.5), artSize - 0.5)).rgb;
}

half3 blendCovers(float2 uv, float q) {
    half3 a = sampleArt(curArt, curSize, uv, q);
    if (morph >= 0.999) return a;
    half3 b = sampleArt(prevArt, prevSize, uv, q);
    return mix(b, a, morph);
}

half4 main(float2 fragCoord) {
    float side = max(viewSize.x, viewSize.y);
    float2 center = viewSize * 0.5;
    float2 warp = float2(
        sin(fragCoord.y * 0.012 + angles.x),
        cos(fragCoord.x * 0.012 + angles.y)
    ) * (side * 0.03 * lobes.z);
    float2 p = (fragCoord + warp - center) / side;
    p /= max(0.35, motion.z);
    float2 uvBase = clamp(p + 0.5, float2(0.0), float2(1.0));
    half3 base = blendCovers(uvBase, 0.0);

    float2 pr = rotateAt(fragCoord, center, angles.z);
    float2 pTop = (pr - center * float2(0.65, 0.6)) / (side * 0.65) + 0.5;
    float2 pBot = (pr - center * float2(1.35, 1.4)) / (side * 0.65) + 0.5;
    half3 top = blendCovers(clamp(pTop, 0.0, 1.0), 1.0);
    half3 bot = blendCovers(clamp(pBot, 0.0, 1.0), 2.0);
    half3 col = mix(base, top, 0.45);
    col = mix(col, bot, 0.45);

    half lum = dot(col, half3(0.299, 0.587, 0.114));
    col = clamp(mix(half3(lum), col, tone.y), 0.0, 1.0);
    col = clamp(col * tone.x * dim, 0.0, 1.0);
    return half4(col, 1.0);
}
"""

/**
 * API 33+ AGSL GPU fluid mesh background driven reactively by real-time PCM sub-bass rhythm
 * energy ([MusicHapticsController.globalRhythmLevel]).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun BeatSyncFluidBackground(
    coverBitmap: Bitmap?,
    isPlaying: Boolean,
    dimAlpha: Float = 0.68f,
    modifier: Modifier = Modifier
) {
    if (coverBitmap == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val shader = remember { RuntimeShader(FLOW_AGSL) }
    val paint = remember { Paint().apply { this.shader = shader } }
    var previousBitmap by remember { mutableStateOf(coverBitmap) }
    var morph by remember { mutableStateOf(1f) }
    var phaseTime by remember { mutableDoubleStateOf(0.0) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var smoothedEnergy by remember { mutableStateOf(0f) }

    LaunchedEffect(coverBitmap) {
        if (coverBitmap !== previousBitmap) {
            morph = 0f
            val startNanos = System.nanoTime()
            while (morph < 1f) {
                withFrameNanos { now ->
                    morph = ((now - startNanos).toFloat() / 900_000_000f).coerceIn(0f, 1f)
                }
            }
            previousBitmap = coverBitmap
        }
    }

    LaunchedEffect(isPlaying) {
        lastFrameNanos = 0L
        while (isPlaying) {
            withFrameNanos { now ->
                val prev = lastFrameNanos
                lastFrameNanos = now
                if (prev > 0L) {
                    val dt = ((now - prev) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                    val targetEnergy = MusicHapticsController.globalRhythmLevel.value.coerceIn(0f, 1f)
                    smoothedEnergy += (targetEnergy - smoothedEnergy) * 0.18f
                    val speedBoost = 1.0 + smoothedEnergy * 2.1
                    phaseTime += dt * speedBoost
                }
            }
        }
    }

    val blurEffect = remember {
        RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = blurEffect }
    ) {
        val curW = coverBitmap.width.coerceAtLeast(2).toFloat()
        val curH = coverBitmap.height.coerceAtLeast(2).toFloat()
        val prevW = previousBitmap.width.coerceAtLeast(2).toFloat()
        val prevH = previousBitmap.height.coerceAtLeast(2).toFloat()

        shader.setInputShader(
            "curArt",
            BitmapShader(coverBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        shader.setInputShader(
            "prevArt",
            BitmapShader(previousBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        shader.setFloatUniform("curSize", curW, curH)
        shader.setFloatUniform("prevSize", prevW, prevH)
        shader.setFloatUniform("viewSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))

        val a1 = (phaseTime * 0.42) % (2.0 * PI)
        val a2 = (phaseTime * 0.25) % (2.0 * PI)
        val a3 = (-phaseTime * 0.20) % (2.0 * PI)
        shader.setFloatUniform("angles", a1.toFloat(), a2.toFloat(), a3.toFloat())
        val scaleBoost = 1.0f + smoothedEnergy * 0.14f
        shader.setFloatUniform(
            "motion",
            cos(a1).toFloat() * smoothedEnergy,
            sin(a2).toFloat() * smoothedEnergy,
            scaleBoost
        )
        shader.setFloatUniform("lobes", 0.5f, 0.5f, 0.35f + smoothedEnergy * 0.85f)
        shader.setFloatUniform("morph", morph)
        shader.setFloatUniform("dim", dimAlpha)
        shader.setFloatUniform("tone", 1.0f + smoothedEnergy * 0.18f, 1.45f + smoothedEnergy * 0.25f)

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}
