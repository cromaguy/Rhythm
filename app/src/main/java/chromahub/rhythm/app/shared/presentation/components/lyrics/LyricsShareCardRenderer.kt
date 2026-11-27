/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.lyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.util.KaraokeLineSplitter
import chromahub.rhythm.app.util.LyricsParser
import chromahub.rhythm.app.util.RhythmLyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class ShareCardMode {
    SONG_CARD,
    QUOTE_CARD
}

data class LyricShareLine(
    val index: Int,
    val text: String,
    val timestampMs: Long = -1L
)

data class ShareColorSwatch(
    val label: String,
    val backgroundArgb: Int,
    val accentArgb: Int,
    val textArgb: Int
)

/**
 * Pure Kotlin + Canvas lyric share card extraction, adaptive artwork palette quantization,
 * bitmap card rendering, and FileProvider PNG export.
 */
object LyricsShareCardRenderer {

    private const val CARD_WIDTH = 1080
    private const val MAX_CARD_HEIGHT = 1680

    val innerShapeIds = listOf(
        "SUNNY",
        "FLOWER",
        "CLOVER_4_LEAF",
        "CLOVER_8_LEAF",
        "SOFT_BURST",
        "PENTAGON",
        "COOKIE_4",
        "COOKIE_6",
        "SOFT_BOOM",
        "ARCH",
        "PILL",
        "SQUARE"
    )

    fun randomInnerShapeId(exclude: String? = null): String {
        val pool = innerShapeIds.filter { it != exclude }.ifEmpty { innerShapeIds }
        return pool.random()
    }

    private fun secondaryInnerShapeId(primary: String): String {
        val index = innerShapeIds.indexOf(primary).coerceAtLeast(0)
        return innerShapeIds[(index + 2) % innerShapeIds.size]
    }

    fun extractShareableLines(lyrics: LyricsData?): List<LyricShareLine> {
        if (lyrics == null) return emptyList()

        val wordJson = lyrics.getWordByWordLyricsOrNull()
        if (!wordJson.isNullOrBlank()) {
            val parsedWord = RhythmLyricsParser.parseWordByWordLyrics(wordJson)
            if (parsedWord.isNotEmpty()) {
                return parsedWord.mapIndexedNotNull { idx, line ->
                    val text = KaraokeLineSplitter.buildRenderedTextAndRanges(line.words).first.trim()
                    if (text.isBlank()) null else LyricShareLine(idx, text, line.lineTimestamp)
                }.mapIndexed { seqIdx, item -> item.copy(index = seqIdx) }
            }
        }

        val synced = lyrics.getSyncedLyricsOrNull()
        if (!synced.isNullOrBlank()) {
            val parsedLrc = LyricsParser.parseLyrics(synced)
            if (parsedLrc.isNotEmpty()) {
                return parsedLrc.mapNotNull { line ->
                    val clean = line.text.trim()
                    if (clean.isBlank()) null else clean to line.timestamp
                }.mapIndexed { seqIdx, (text, ts) ->
                    LyricShareLine(seqIdx, text, ts)
                }
            }
        }

        val plain = lyrics.getPlainLyricsOrNull()
        if (!plain.isNullOrBlank()) {
            return plain.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("[") }
                .mapIndexed { seqIdx, text -> LyricShareLine(seqIdx, text) }
        }

        return emptyList()
    }

    fun defaultSelectionRange(totalLines: Int, currentPositionMs: Long, lines: List<LyricShareLine>): IntRange {
        if (totalLines <= 0) return IntRange.EMPTY
        val activeIdx = lines.indexOfLast { it.timestampMs in 0L..currentPositionMs }.takeIf { it >= 0 } ?: 0
        val end = (activeIdx + 2).coerceAtMost(totalLines - 1)
        val start = (end - 2).coerceAtLeast(0)
        return start..end
    }

    fun loadCoverBitmap(context: Context, artworkUri: Uri?): Bitmap? {
        if (artworkUri == null) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            context.contentResolver.openInputStream(artworkUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }

    fun loadBrandLogo(context: Context): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.rhythm_splash_logo) ?: return null
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun extractSwatches(coverBitmap: Bitmap?, fallbackPrimaryArgb: Int): List<ShareColorSwatch> {
        val list = mutableListOf<ShareColorSwatch>()
        if (coverBitmap != null) {
            val scaled = Bitmap.createScaledBitmap(coverBitmap, 16, 16, true)
            val buckets = HashMap<Int, Int>()
            for (y in 0 until scaled.height) {
                for (x in 0 until scaled.width) {
                    val px = scaled.getPixel(x, y)
                    if (AndroidColor.alpha(px) < 180) continue
                    val hsv = FloatArray(3)
                    AndroidColor.colorToHSV(px, hsv)
                    if (hsv[1] < 0.18f || hsv[2] < 0.16f) continue
                    val q = AndroidColor.rgb(
                        (AndroidColor.red(px) and 0xE0),
                        (AndroidColor.green(px) and 0xE0),
                        (AndroidColor.blue(px) and 0xE0)
                    )
                    buckets[q] = (buckets[q] ?: 0) + 1
                }
            }
            if (scaled != coverBitmap) scaled.recycle()

            val topColors = buckets.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(3)

            topColors.forEach { argb ->
                val bg = ColorUtils.blendARGB(argb, AndroidColor.BLACK, 0.82f)
                val accent = ColorUtils.blendARGB(argb, AndroidColor.WHITE, 0.42f)
                list.add(ShareColorSwatch("Artwork", bg, accent, pickContrastForeground(bg)))
            }
        }

        val themeBg = ColorUtils.blendARGB(fallbackPrimaryArgb, AndroidColor.BLACK, 0.84f)
        list.add(ShareColorSwatch("Theme", themeBg, fallbackPrimaryArgb, pickContrastForeground(themeBg)))

        addSwatch(list, "Obsidian", "#121318", "#9EC6FF", "#E3E2E6")
        addSwatch(list, "Warm Paper", "#F4EEE4", "#8C4A2F", "#1D1B18")
        addSwatch(list, "Crimson", "#3B0D16", "#FFB1C1", "#FFE8EC")
        addSwatch(list, "Moss", "#101B12", "#9CD7A8", "#E4EFE6")
        addSwatch(list, "Ocean", "#0B1622", "#9FCBF7", "#E2EDF7")
        addSwatch(list, "Plum", "#1B1020", "#E4B7F5", "#F3E7F8")
        addSwatch(list, "Amber", "#20180A", "#F3C46D", "#F8EDD8")
        addSwatch(list, "Terracotta", "#2A120D", "#F4B9A3", "#F9E7E0")
        addSwatch(list, "Indigo", "#111329", "#BCC5FF", "#E6E8FF")
        addSwatch(list, "Slate", "#E9EDF2", "#334C6B", "#14181D")
        addSwatch(list, "Mint", "#E8F5EE", "#1F6B4A", "#12211A")
        addSwatch(list, "Blush", "#FAEDEF", "#8F4A5B", "#1F1416")

        return list.distinctBy { "${it.backgroundArgb}-${it.accentArgb}" }
    }

    private fun addSwatch(
        list: MutableList<ShareColorSwatch>,
        label: String,
        backgroundHex: String,
        accentHex: String,
        textHex: String
    ) {
        val bg = AndroidColor.parseColor(backgroundHex)
        list.add(ShareColorSwatch(label, bg, AndroidColor.parseColor(accentHex), AndroidColor.parseColor(textHex)))
    }

    suspend fun renderCardBitmap(
        songTitle: String,
        artistName: String,
        albumName: String?,
        selectedLines: List<String>,
        mode: ShareCardMode,
        swatch: ShareColorSwatch,
        coverBitmap: Bitmap?,
        innerShapeId: String,
        artworkShape: Shape? = null,
        logoBitmap: Bitmap? = null,
        brandName: String = "Rhythm"
    ): Bitmap = withContext(Dispatchers.Default) {
        val hPad = 88f
        val contentW = (CARD_WIDTH - hPad * 2).roundToInt()

        val bodyText = selectedLines.joinToString("\n")
        val lyricSize = when {
            bodyText.length > 180 -> 44f
            bodyText.length > 110 -> 54f
            else -> 64f
        }

        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = swatch.textArgb
            textSize = lyricSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lyricLayout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, lyricPaint, contentW)
            .setAlignment(if (mode == ShareCardMode.QUOTE_CARD) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1.15f)
            .build()

        val headerHeight = if (mode == ShareCardMode.SONG_CARD) 250f else 96f
        val footerHeight = 216f
        val totalHeight = (headerHeight + lyricLayout.height + footerHeight + 96f)
            .roundToInt()
            .coerceIn(CARD_WIDTH, MAX_CARD_HEIGHT)

        val out = Bitmap.createBitmap(CARD_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cardRect = RectF(0f, 0f, CARD_WIDTH.toFloat(), totalHeight.toFloat())

        canvas.drawRect(cardRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = swatch.backgroundArgb })

        drawExpressiveOrnament(
            canvas = canvas,
            shapeId = innerShapeId,
            rect = RectF(CARD_WIDTH * 0.52f, -totalHeight * 0.08f, CARD_WIDTH * 1.2f, totalHeight * 0.26f),
            colorArgb = swatch.accentArgb,
            alpha = 40
        )
        drawExpressiveOrnament(
            canvas = canvas,
            shapeId = secondaryInnerShapeId(innerShapeId),
            rect = RectF(-CARD_WIDTH * 0.18f, totalHeight * 0.74f, CARD_WIDTH * 0.42f, totalHeight * 1.06f),
            colorArgb = swatch.accentArgb,
            alpha = 28
        )

        var yCursor = 84f
        if (mode == ShareCardMode.SONG_CARD) {
            val coverSize = 148f
            val artRect = RectF(hPad, yCursor, hPad + coverSize, yCursor + coverSize)
            val artPath = artworkShapePath(artworkShape, artRect)
            if (coverBitmap != null) {
                canvas.save()
                canvas.clipPath(artPath)
                canvas.drawBitmap(coverBitmap, null, artRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            } else {
                canvas.drawPath(
                    artPath,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = ColorUtils.setAlphaComponent(swatch.textArgb, 36)
                    }
                )
            }

            val metaX = hPad + coverSize + 36f
            val metaW = (CARD_WIDTH - hPad - metaX).roundToInt().coerceAtLeast(100)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = swatch.textArgb
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(swatch.textArgb, 200)
                textSize = 32f
            }

            canvas.save()
            canvas.translate(metaX, yCursor + 20f)
            val titleLayout = StaticLayout.Builder.obtain(songTitle, 0, songTitle.length, titlePaint, metaW)
                .setMaxLines(2)
                .build()
            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height + 10f)
            val artistLine = if (!albumName.isNullOrBlank()) "$artistName — $albumName" else artistName
            val artistLayout = StaticLayout.Builder.obtain(artistLine, 0, artistLine.length, artistPaint, metaW)
                .setMaxLines(1)
                .build()
            artistLayout.draw(canvas)
            canvas.restore()

            yCursor += coverSize + 72f
        } else {
            val quoteMarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(swatch.textArgb, 110)
                textSize = 96f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("\u201C", hPad, yCursor + 60f, quoteMarkPaint)
            yCursor += 90f
        }

        val availableBodyHeight = totalHeight - yCursor - footerHeight
        val centeredY = yCursor + ((availableBodyHeight - lyricLayout.height).coerceAtLeast(0f) * 0.42f)
        canvas.save()
        canvas.translate(hPad, centeredY)
        lyricLayout.draw(canvas)
        canvas.restore()

        val watermarkWidth = drawRhythmWatermark(
            canvas = canvas,
            logoBitmap = logoBitmap,
            swatch = swatch,
            brandName = brandName,
            bottom = totalHeight.toFloat()
        )

        if (mode == ShareCardMode.QUOTE_CARD) {
            val attributionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(swatch.textArgb, 190)
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val attribution = "$songTitle — $artistName"
            val attributionW = (CARD_WIDTH - hPad * 2 - watermarkWidth - 32f).roundToInt().coerceAtLeast(120)
            val attributionLayout = StaticLayout.Builder
                .obtain(attribution, 0, attribution.length, attributionPaint, attributionW)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(hPad, totalHeight - 92f)
            attributionLayout.draw(canvas)
            canvas.restore()
        }

        out
    }

    private fun artworkShapePath(shape: Shape?, rect: RectF): Path {
        if (shape == null) {
            return Path().apply { addRoundRect(rect, 28f, 28f, Path.Direction.CW) }
        }

        val outline = shape.createOutline(
            size = Size(rect.width(), rect.height()),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )
        val composePath = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> ComposePath().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> ComposePath().apply { addRect(outline.rect) }
        }

        return composePath.asAndroidPath().apply { offset(rect.left, rect.top) }
    }

    private fun drawRhythmWatermark(
        canvas: Canvas,
        logoBitmap: Bitmap?,
        swatch: ShareColorSwatch,
        brandName: String,
        bottom: Float
    ): Float {
        val logoSize = 104f
        val gap = 16f
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = swatch.textArgb
            alpha = 150
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val word = brandName
        val wordWidth = wordPaint.measureText(word)
        val totalWidth = logoSize + gap + wordWidth
        val right = CARD_WIDTH - 88f
        val left = right - totalWidth
        val top = bottom - 56f - logoSize

        if (logoBitmap != null) {
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = 150
            }
            canvas.drawBitmap(
                logoBitmap,
                null,
                RectF(left, top, left + logoSize, top + logoSize),
                logoPaint
            )
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(swatch.textArgb, 70)
            }
            canvas.drawCircle(left + logoSize / 2f, top + logoSize / 2f, logoSize / 2f, fallbackPaint)
        }

        canvas.drawText(
            word,
            left + logoSize + gap,
            top + logoSize / 2f + wordPaint.textSize * 0.36f,
            wordPaint
        )
        return totalWidth
    }

    private fun drawExpressiveOrnament(
        canvas: Canvas,
        shapeId: String,
        rect: RectF,
        colorArgb: Int,
        alpha: Int
    ) {
        val path = shapePathFor(shapeId, rect, fallbackCornerRadius = 0f, preserveAspect = true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            this.alpha = alpha
        }
        canvas.drawPath(path, paint)
    }

    private fun shapePathFor(
        shapeId: String,
        rect: RectF,
        fallbackCornerRadius: Float,
        preserveAspect: Boolean = false
    ): Path {
        val fallback = Path().apply {
            addRoundRect(rect, fallbackCornerRadius, fallbackCornerRadius, Path.Direction.CW)
        }
        val polygon = polygonFor(shapeId) ?: return fallback

        val raw = polygon.toPath()
        val bounds = RectF()
        raw.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return fallback

        val scaleX = rect.width() / bounds.width()
        val scaleY = rect.height() / bounds.height()
        val uniform = if (preserveAspect) maxOf(scaleX, scaleY) else 0f
        val finalScaleX = if (preserveAspect) uniform else scaleX
        val finalScaleY = if (preserveAspect) uniform else scaleY
        val scaledWidth = bounds.width() * finalScaleX
        val scaledHeight = bounds.height() * finalScaleY
        val matrix = Matrix().apply {
            setScale(finalScaleX, finalScaleY)
            postTranslate(
                rect.centerX() - scaledWidth / 2f - bounds.left * finalScaleX,
                rect.centerY() - scaledHeight / 2f - bounds.top * finalScaleY
            )
        }
        raw.transform(matrix)
        return raw
    }

    private fun polygonFor(shapeId: String): RoundedPolygon? = when (shapeId) {
        "CIRCLE" -> MaterialShapes.Circle
        "SQUARE" -> MaterialShapes.Square
        "OVAL" -> MaterialShapes.Oval
        "PILL" -> MaterialShapes.Pill
        "DIAMOND" -> MaterialShapes.Diamond
        "TRIANGLE" -> MaterialShapes.Triangle
        "PENTAGON" -> MaterialShapes.Pentagon
        "FLOWER" -> MaterialShapes.Flower
        "CLOVER_4_LEAF" -> MaterialShapes.Clover4Leaf
        "CLOVER_8_LEAF" -> MaterialShapes.Clover8Leaf
        "HEART" -> MaterialShapes.Heart
        "BOOM" -> MaterialShapes.Boom
        "SOFT_BOOM" -> MaterialShapes.SoftBoom
        "BURST" -> MaterialShapes.Burst
        "SOFT_BURST" -> MaterialShapes.SoftBurst
        "SUNNY" -> MaterialShapes.Sunny
        "VERY_SUNNY" -> MaterialShapes.VerySunny
        "COOKIE_4" -> MaterialShapes.Cookie4Sided
        "COOKIE_6" -> MaterialShapes.Cookie6Sided
        "COOKIE_7" -> MaterialShapes.Cookie7Sided
        "COOKIE_9" -> MaterialShapes.Cookie9Sided
        "COOKIE_12" -> MaterialShapes.Cookie12Sided
        "GHOSTISH" -> MaterialShapes.Ghostish
        "PUFFY" -> MaterialShapes.Puffy
        "PUFFY_DIAMOND" -> MaterialShapes.PuffyDiamond
        "BUN" -> MaterialShapes.Bun
        "FAN" -> MaterialShapes.Fan
        "ARROW" -> MaterialShapes.Arrow
        "ARCH" -> MaterialShapes.Arch
        "CLAM_SHELL" -> MaterialShapes.ClamShell
        "GEM" -> MaterialShapes.Gem
        "SEMI_CIRCLE" -> MaterialShapes.SemiCircle
        else -> null
    }

    suspend fun exportAndShareCard(context: Context, bitmap: Bitmap, songTitle: String): Intent? =
        withContext(Dispatchers.IO) {
            runCatching {
                val shareDir = File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
                val outFile = File(shareDir, "rhythm_lyric_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 96, stream)
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    outFile
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, songTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.getOrNull()
        }

    private fun pickContrastForeground(bgArgb: Int): Int =
        if (ColorUtils.calculateLuminance(bgArgb) > 0.38) AndroidColor.parseColor("#181818") else AndroidColor.WHITE
}
