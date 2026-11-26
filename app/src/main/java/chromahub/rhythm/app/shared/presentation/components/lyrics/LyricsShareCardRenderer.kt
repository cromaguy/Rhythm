/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
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

            topColors.forEachIndexed { i, argb ->
                val bg = darkenColor(argb, 0.42f)
                val accent = lightenColor(argb, 0.25f)
                val ink = pickContrastForeground(bg)
                list.add(ShareColorSwatch("Art ${i + 1}", bg, accent, ink))
            }
        }

        val primaryDarkBg = darkenColor(fallbackPrimaryArgb, 0.48f)
        list.add(ShareColorSwatch("Theme", primaryDarkBg, fallbackPrimaryArgb, pickContrastForeground(primaryDarkBg)))
        list.add(ShareColorSwatch("Obsidian", AndroidColor.parseColor("#111318"), AndroidColor.parseColor("#4D5D7A"), AndroidColor.WHITE))
        list.add(ShareColorSwatch("Warm Paper", AndroidColor.parseColor("#F5EFE6"), AndroidColor.parseColor("#D8C3A5"), AndroidColor.parseColor("#1D1B18")))
        list.add(ShareColorSwatch("Crimson", AndroidColor.parseColor("#3B0D16"), AndroidColor.parseColor("#9B2242"), AndroidColor.WHITE))
        return list.distinctBy { it.backgroundArgb }
    }

    suspend fun renderCardBitmap(
        songTitle: String,
        artistName: String,
        albumName: String?,
        selectedLines: List<String>,
        mode: ShareCardMode,
        swatch: ShareColorSwatch,
        coverBitmap: Bitmap?
    ): Bitmap = withContext(Dispatchers.Default) {
        val hPad = 76
        val contentW = CARD_WIDTH - hPad * 2

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

        val headerHeight = if (mode == ShareCardMode.SONG_CARD) 230 else 80
        val footerHeight = 140
        val totalHeight = (headerHeight + lyricLayout.height + footerHeight + 96).coerceIn(CARD_WIDTH, MAX_CARD_HEIGHT)

        val out = Bitmap.createBitmap(CARD_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, CARD_WIDTH.toFloat(), totalHeight.toFloat(),
                swatch.backgroundArgb,
                darkenColor(swatch.accentArgb, 0.55f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, CARD_WIDTH.toFloat(), totalHeight.toFloat()), 48f, 48f, bgPaint)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(swatch.accentArgb, 52)
        }
        canvas.drawCircle(CARD_WIDTH * 0.85f, totalHeight * 0.16f, 280f, glowPaint)

        var yCursor = 76f
        if (mode == ShareCardMode.SONG_CARD) {
            val coverSize = 148f
            val artRect = RectF(hPad.toFloat(), yCursor, hPad + coverSize, yCursor + coverSize)
            if (coverBitmap != null) {
                val clipPath = Path().apply { addRoundRect(artRect, 24f, 24f, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(clipPath)
                canvas.drawBitmap(coverBitmap, null, artRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.setAlphaComponent(swatch.textArgb, 36)
                }
                canvas.drawRoundRect(artRect, 24f, 24f, placeholderPaint)
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
            canvas.drawText("\u201C", hPad.toFloat(), yCursor + 60f, quoteMarkPaint)
            yCursor += 90f
        }

        val availableBodyHeight = totalHeight - yCursor - footerHeight
        val centeredY = yCursor + ((availableBodyHeight - lyricLayout.height).coerceAtLeast(0f) * 0.42f)
        canvas.save()
        canvas.translate(hPad.toFloat(), centeredY)
        lyricLayout.draw(canvas)
        canvas.restore()

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(swatch.textArgb, 185)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val footerText = if (mode == ShareCardMode.QUOTE_CARD) {
            "Rhythm  \u2022  $songTitle \u2014 $artistName"
        } else {
            "Rhythm"
        }
        canvas.drawText(footerText, hPad.toFloat(), totalHeight - 54f, footerPaint)

        out
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

    private fun darkenColor(argb: Int, factor: Float): Int =
        ColorUtils.blendARGB(argb, AndroidColor.BLACK, factor.coerceIn(0f, 1f))

    private fun lightenColor(argb: Int, factor: Float): Int =
        ColorUtils.blendARGB(argb, AndroidColor.WHITE, factor.coerceIn(0f, 1f))

    private fun pickContrastForeground(bgArgb: Int): Int =
        if (ColorUtils.calculateLuminance(bgArgb) > 0.38) AndroidColor.parseColor("#181818") else AndroidColor.WHITE
}
