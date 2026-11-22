/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.R
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.core.net.toUri

/**
 * Modern Glance-based Music Widget with Material 3 Expressive Design
 * 
 * Features:
 * - SizeMode.Exact for pixel-perfect responsive layouts
 * - Material 3 Expressive theming with dynamic colors
 * - Dynamic play/pause button corner radius (rounded when paused, more square when playing)
 * - Adaptive layouts for every widget size (1x1 to 5x5+)
 * - Album art with LruCache bitmap management
 * - Expressive rounded corners and spacing
 */
class RhythmMusicWidget : GlanceAppWidget() {
    
    companion object {
        // Widget state keys
        const val KEY_SONG_ID = "song_id"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_ARTIST_NAME = "artist_name"
        const val KEY_ALBUM_NAME = "album_name"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_ARTWORK_URI = "artwork_uri"
        const val KEY_HAS_PREVIOUS = "has_previous"
        const val KEY_HAS_NEXT = "has_next"
        const val KEY_IS_FAVORITE = "is_favorite"

        // Layout size breakpoints for responsive widget sizing
        private val VERY_THIN_SIZE = DpSize(200.dp, 60.dp)
        private val THIN_SIZE = DpSize(250.dp, 80.dp)
        private val SMALL_HORIZONTAL_SIZE = DpSize(110.dp, 60.dp)
        private val ONE_BY_ONE_SIZE = DpSize(110.dp, 110.dp)
        private val GABE_SIZE = DpSize(110.dp, 220.dp)
        private val GABE_TWO_HEIGHT_SIZE = DpSize(110.dp, 200.dp)
        private val SMALL_SIZE = DpSize(120.dp, 100.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 150.dp)
        private val LARGE_SIZE = DpSize(300.dp, 180.dp)
        private val EXTRA_LARGE_SIZE = DpSize(300.dp, 220.dp)
        private val EXTRA_LARGE_PLUS_SIZE = DpSize(350.dp, 260.dp)
        private val HUGE_SIZE = DpSize(400.dp, 300.dp)

        internal fun computeResponsiveScale(width: Float, height: Float): Float {
            if (width <= 0f || height <= 0f) return 1.0f
            val widthScale = width / 250f
            val heightScale = if (height < 100f) height / 80f else height / 180f
            return minOf(widthScale, heightScale).coerceIn(0.65f, 1.2f)
        }

        // LruCache for album art bitmaps
        private object AlbumArtCache {
            private const val CACHE_SIZE = 4 * 1024 * 1024 // 4 MiB
            private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
                override fun sizeOf(key: String, value: Bitmap) = value.byteCount
            }
            fun get(key: String): Bitmap? = cache.get(key)
            fun put(key: String, bitmap: Bitmap) { if (get(key) == null) cache.put(key, bitmap) }
            fun keyFor(data: ByteArray): String = data.contentHashCode().toString()
            fun clear() { cache.evictAll() }
        }

        fun cacheBitmap(uri: String, bitmap: Bitmap) {
            AlbumArtCache.put(uri, bitmap)
        }

        fun getCachedBitmap(uri: String): Bitmap? {
            return AlbumArtCache.get(uri)
        }

        /** Released on app onTrimMemory so background bitmap memory is freed. */
        fun clearArtCache() {
            AlbumArtCache.clear()
        }
    }
    
    // Use preferences-based state definition for reactive updates
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    
    // Exact size mode for pixel-perfect responsive layouts
    override val sizeMode = SizeMode.Exact
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appSettings = try {
            chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context)
        } catch (e: Exception) {
            null
        }
        
        provideContent {
            val currentPrefs = currentState<Preferences>()
            val artworkUriString = currentPrefs[stringPreferencesKey(KEY_ARTWORK_URI)]
            
            // Resolve bitmap instantly from cache or asynchronously load it
            var bitmap by remember(artworkUriString) {
                mutableStateOf<Bitmap?>(artworkUriString?.let { getCachedBitmap(it) })
            }
            
            if (bitmap == null && !artworkUriString.isNullOrBlank()) {
                val glanceContext = LocalContext.current
                LaunchedEffect(artworkUriString) {
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            val imageLoader = coil.Coil.imageLoader(glanceContext)
                            val request = ImageRequest.Builder(glanceContext)
                                .data(artworkUriString)
                                .size(Size(512, 512))
                                .build()
                            val result = imageLoader.execute(request)
                            val loadedBmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (loadedBmp != null) {
                                cacheBitmap(artworkUriString, loadedBmp)
                            }
                            loadedBmp
                        }
                        if (loaded != null) {
                            bitmap = loaded
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RhythmMusicWidget", "Error fetching bitmap in content", e)
                    }
                }
            }
            
            val widgetData = try {
                getWidgetData(currentPrefs, appSettings).copy(preloadedBitmap = bitmap)
            } catch (e: Exception) {
                android.util.Log.e("RhythmMusicWidget", "Widget data error, using fallback", e)
                WidgetData(
                    songTitle = "Rhythm",
                    artistName = "",
                    albumName = "",
                    isPlaying = false,
                    artworkUri = null,
                    hasPrevious = false,
                    hasNext = false,
                    isFavorite = false,
                    preloadedBitmap = null
                )
            }
            val currentSize = LocalSize.current
            GlanceTheme {
                WidgetUi(widgetData, currentSize)
            }
        }
    }
    
    @Composable
    private fun WidgetUi(data: WidgetData, size: DpSize) {
        val aspectRatio = size.height.value / size.width.value
        val minWidth = size.width.value.toInt()
        val minHeight = size.height.value.toInt()
        
        val baseModifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())
        
        Box(GlanceModifier.fillMaxSize().cornerRadius(data.cornerRadius.dp).background(getBgColor(data.widgetTheme))) {

            when {
                // Extremely tall & narrow (1 cell wide):
                minWidth < 100 -> {

                    when {
                        minHeight >= 200 -> GabeLayout(baseModifier, data)
                        minHeight >= 100 -> GabeTwoHeightLayout(baseModifier, data)
                        else -> OneByOneLayout(baseModifier, data)
                    }
                }
                
                // Short strip (1 cell tall):
                minHeight < 100 -> {
                    when {
                        minWidth >= 320 -> VeryThinLayout(baseModifier, data)
                        minWidth >= 220 -> ThinLayout(baseModifier, data)
                        minWidth >= 110 -> SmallHorizontalLayout(baseModifier, data)
                        else -> OneByOneLayout(baseModifier, data)
                    }
                }
                
                // 5x5+ Huge widget (>= 340dp x >= 340dp)
                minWidth >= 340 && minHeight >= 340 -> HugeWidgetLayout(baseModifier, data)
                
                // 5x4 or 4x5 Tall wide widget (>= 340dp x >= 280dp)
                minWidth >= 340 && minHeight >= 280 -> ExtraLargeWidgetLayout(baseModifier, data)
                
                // 4x4+ Extra large widget (>= 280dp x >= 280dp)
                minWidth >= 280 && minHeight >= 280 -> ExtraLargeWidgetLayout(baseModifier, data)
                
                // 5x3 Extra Large Plus widget (>= 340dp x >= 210dp)
                minWidth >= 340 && minHeight >= 210 -> ExtraLargePlusWidgetLayout(baseModifier, data)
                
                // 3x3 / 4x3 Large horizontal widget (>= 210dp x >= 210dp)
                minWidth >= 210 && minHeight >= 210 -> LargeWidgetLayout(baseModifier, data)
                
                // 2x3 Tall vertical widget (>= 100dp width & >= 210dp height)
                minWidth >= 100 && minHeight >= 210 -> VerticalLayout(baseModifier, data)
                
                // 5x2 / 4x2 Wide horizontal strip (>= 320dp width & < 210dp height)
                minWidth >= 320 && minHeight < 210 -> WideLayout(baseModifier, data)
                
                // 3x2 Medium horizontal widget (>= 180dp width & >= 100dp height)
                minWidth >= 180 && minHeight >= 100 -> MediumWidgetLayout(baseModifier, data)
                
                // 2x2 Small widget (>= 100dp width & >= 100dp height)
                minWidth >= 100 && minHeight >= 100 -> SmallWidgetLayout(baseModifier, data)
                
                // Default fallback
                else -> MediumWidgetLayout(baseModifier, data)
            }
        }
    }
    
    // ==================== 1x1 Layout: Play/Pause only ====================
    @Composable
    private fun OneByOneLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp),
            contentAlignment = Alignment.Center
        ) {
            PlayPauseButton(
                modifier = GlanceModifier.fillMaxSize(),
                isPlaying = data.isPlaying,
                iconSize = (36 * scale).dp,
                cornerRadius = 30.dp
            )
        }
    }
    
    // ==================== Gabe Two Height Layout: Art + Buttons vertical ====================
    @Composable
    private fun GabeTwoHeightLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.defaultWeight().height((44 * scale).dp),
                    preloadedBitmap = data.preloadedBitmap
                )
                Spacer(GlanceModifier.height((12 * scale).dp))
                Column(
                    modifier = GlanceModifier.defaultWeight().cornerRadius(60.dp)
                ) {
                    PlayPauseButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        isPlaying = data.isPlaying,
                        iconSize = (24 * scale).dp
                    )
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    NextButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        iconSize = (24 * scale).dp
                    )
                }
            }
        }
    }
    
    // ==================== Gabe Layout: Art + Prev/Play/Next vertical ====================
    @Composable
    private fun GabeLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth().height((44 * scale).dp),
                    preloadedBitmap = data.preloadedBitmap
                )
                Spacer(GlanceModifier.height((12 * scale).dp))
                Column(modifier = GlanceModifier.defaultWeight().cornerRadius(60.dp)) {
                    PreviousButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        iconSize = (24 * scale).dp
                    )
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    PlayPauseButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        isPlaying = data.isPlaying,
                        iconSize = (24 * scale).dp
                    )
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    NextButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        iconSize = (24 * scale).dp
                    )
                }
            }
        }
    }
    
    // ==================== Vertical Layout: Tall widget with centered content ====================
    @Composable
    private fun VerticalLayout(modifier: GlanceModifier, data: WidgetData) {
        val textColor = getTextColor(data.widgetTheme)
        val subtextColor = getSubtextColor(data.widgetTheme)
        val scale = responsiveScale()
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Album Art
                AlbumArtImage(
                    modifier = GlanceModifier.size((104 * scale).dp),
                    preloadedBitmap = data.preloadedBitmap
                )
                
                Spacer(GlanceModifier.height((14 * scale).dp))
                
                // Song Info
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = data.songTitle,
                        style = TextStyle(
                            fontSize = (16 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        maxLines = 2
                    )
                    
                    if (data.showArtist && data.artistName.isNotEmpty()) {
                        Spacer(GlanceModifier.height((4 * scale).dp))
                        Text(
                            text = data.artistName,
                            style = TextStyle(
                                fontSize = (14 * scale).sp,
                                color = subtextColor
                            ),
                            maxLines = 1
                        )
                    }
                    
                    if (data.showAlbum && data.albumName.isNotEmpty()) {
                        Spacer(GlanceModifier.height((2 * scale).dp))
                        Text(
                            text = data.albumName,
                            style = TextStyle(
                                fontSize = (12 * scale).sp,
                                color = subtextColor
                            ),
                            maxLines = 1
                        )
                    }
                }
                
                Spacer(GlanceModifier.height((16 * scale).dp))
                
                // Control buttons
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PreviousButton(
                        modifier = GlanceModifier.fillMaxWidth().height((48 * scale).dp),
                        iconSize = (24 * scale).dp,
                        cornerRadius = 24.dp
                    )
                    
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    
                    PlayPauseButton(
                        modifier = GlanceModifier.fillMaxWidth().height((56 * scale).dp),
                        isPlaying = data.isPlaying,
                        iconSize = (28 * scale).dp,
                        cornerRadius = if (data.isPlaying) 16.dp else 28.dp
                    )
                    
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    
                    NextButton(
                        modifier = GlanceModifier.fillMaxWidth().height((48 * scale).dp),
                        iconSize = (24 * scale).dp,
                        cornerRadius = 24.dp
                    )
                }
            }
        }
    }
    
    // ==================== Wide Layout: Very wide horizontal strip ====================
    @Composable
    private fun WideLayout(modifier: GlanceModifier, data: WidgetData) {
        val textColor = getTextColor(data.widgetTheme)
        val subtextColor = getSubtextColor(data.widgetTheme)
        val size = LocalSize.current
        val scale = responsiveScale()
        // Fill the height when the widget has extra vertical space, capped so it
        // never swallows the whole row on huge widgets.
        val albumArtSize = (size.height - (32 * scale).dp).coerceIn((56 * scale).dp, (96 * scale).dp)
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                AlbumArtImage(
                    modifier = GlanceModifier.size(albumArtSize),
                    preloadedBitmap = data.preloadedBitmap
                )
                
                Spacer(GlanceModifier.width((14 * scale).dp))
                
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = data.songTitle,
                        style = TextStyle(
                            fontSize = (17 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        maxLines = 1
                    )
                    
                    if (data.showArtist && data.artistName.isNotEmpty()) {
                        Spacer(GlanceModifier.height((2 * scale).dp))
                        Text(
                            text = data.artistName,
                            style = TextStyle(
                                fontSize = (14 * scale).sp,
                                color = subtextColor
                            ),
                            maxLines = 1
                        )
                    }
                }
                
                Spacer(GlanceModifier.width((14 * scale).dp))
                
                // Control buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviousButton(
                        modifier = GlanceModifier.size((52 * scale).dp),
                        iconSize = (26 * scale).dp,
                        cornerRadius = 26.dp
                    )
                    
                    Spacer(GlanceModifier.width((8 * scale).dp))
                    
                    PlayPauseButton(
                        modifier = GlanceModifier.size((58 * scale).dp),
                        isPlaying = data.isPlaying,
                        iconSize = (30 * scale).dp,
                        cornerRadius = if (data.isPlaying) 16.dp else 29.dp
                    )
                    
                    Spacer(GlanceModifier.width((8 * scale).dp))
                    
                    NextButton(
                        modifier = GlanceModifier.size((52 * scale).dp),
                        iconSize = (26 * scale).dp,
                        cornerRadius = 26.dp
                    )
                }
            }
        }
    }
    
    // ==================== Small Horizontal: Art + Play (strip) ====================
    @Composable
    private fun SmallHorizontalLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(data.cornerRadius.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.padding(vertical = (6 * scale).dp),
                    preloadedBitmap = data.preloadedBitmap,
                    size = (48 * scale).dp
                )
                Spacer(GlanceModifier.width((12 * scale).dp))
                PlayPauseButton(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    isPlaying = data.isPlaying,
                    iconSize = (24 * scale).dp
                )
            }
        }
    }
    
    // ==================== Very Thin: Art + Title + Progress + Controls ====================
    @Composable
    private fun VeryThinLayout(modifier: GlanceModifier, data: WidgetData) {
        val textColor = getTextColor(data.widgetTheme)
        val subtextColor = getSubtextColor(data.widgetTheme)
        val size = LocalSize.current
        val scale = responsiveScale()
        val albumArtSize = (size.height - (24 * scale).dp).coerceAtLeast((40 * scale).dp)
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((14 * scale).dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(data.cornerRadius.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.size(albumArtSize),
                    preloadedBitmap = data.preloadedBitmap
                )
                Spacer(GlanceModifier.width((12 * scale).dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = data.songTitle,
                        style = TextStyle(fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold, color = textColor),
                        maxLines = 1
                    )
                    if (data.showArtist && data.artistName.isNotEmpty()) {
                        Text(
                            text = data.artistName,
                            style = TextStyle(fontSize = (13 * scale).sp, color = subtextColor),
                            maxLines = 1
                        )
                    }
                    
                }
                Spacer(GlanceModifier.width((10 * scale).dp))
                PlayPauseButton(
                    modifier = GlanceModifier.defaultWeight().size((48 * scale).dp).fillMaxHeight(),
                    isPlaying = data.isPlaying,
                    iconSize = (24 * scale).dp
                )
                Spacer(GlanceModifier.width((10 * scale).dp))
                NextButton(
                    modifier = GlanceModifier.defaultWeight().size((48 * scale).dp).fillMaxHeight(),
                    iconSize = (24 * scale).dp
                )
            }
        }
    }
    
    // ==================== Thin: Full strip with art, info, buttons ====================
    @Composable
    private fun ThinLayout(modifier: GlanceModifier, data: WidgetData) {
        val textColor = getTextColor(data.widgetTheme)
        val subtextColor = getSubtextColor(data.widgetTheme)
        val size = LocalSize.current
        val scale = responsiveScale()
        val albumArtSize = (size.height - (24 * scale).dp).coerceAtLeast((40 * scale).dp)
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((14 * scale).dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(data.cornerRadius.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.size(albumArtSize),
                    preloadedBitmap = data.preloadedBitmap
                )
                Spacer(GlanceModifier.width((12 * scale).dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = data.songTitle,
                        style = TextStyle(fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold, color = textColor),
                        maxLines = 1
                    )
                    if (data.showArtist && data.artistName.isNotEmpty()) {
                        Text(
                            text = data.artistName,
                            style = TextStyle(fontSize = (13 * scale).sp, color = subtextColor),
                            maxLines = 1
                        )
                    }
                    
                }
                Spacer(GlanceModifier.width((10 * scale).dp))
                PlayPauseButton(
                    modifier = GlanceModifier.defaultWeight().size((48 * scale).dp).fillMaxHeight(),
                    isPlaying = data.isPlaying,
                    iconSize = (24 * scale).dp
                )
                Spacer(GlanceModifier.width((10 * scale).dp))
                NextButton(
                    modifier = GlanceModifier.defaultWeight().size((48 * scale).dp).fillMaxHeight(),
                    iconSize = (24 * scale).dp
                )
            }
        }
    }
    
    // ==================== Small Widget: Art + Play + Prev/Next ====================
    @Composable
    private fun SmallWidgetLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        val playButtonCornerRadius = if (data.isPlaying) 20.dp else 60.dp
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((16 * scale).dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Album Art with shadow effect — bounded size, centered, so the
                // cookie never balloons to fill the whole top area
                Box(
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArtImage(
                        modifier = GlanceModifier.size((92 * scale).dp),
                        preloadedBitmap = data.preloadedBitmap
                    )
                }
                Spacer(GlanceModifier.height((10 * scale).dp))
                // Play/Pause button
                PlayPauseButton(
                    modifier = GlanceModifier.fillMaxWidth().height((50 * scale).dp),
                    isPlaying = data.isPlaying,
                    iconSize = (26 * scale).dp,
                    cornerRadius = playButtonCornerRadius
                )
                Spacer(GlanceModifier.height((8 * scale).dp))
                // Previous and Next buttons row
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height((48 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PreviousButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        iconSize = (26 * scale).dp,
                        cornerRadius = 26.dp
                    )
                    Spacer(GlanceModifier.width((10 * scale).dp))
                    NextButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        iconSize = (26 * scale).dp,
                        cornerRadius = 26.dp
                    )
                }
            }
        }
    }
    
    // ==================== Medium Widget: Art + Info + Controls row ====================
    @Composable
    private fun MediumWidgetLayout(modifier: GlanceModifier, data: WidgetData) {
        val textColor = getTextColor(data.widgetTheme)
        val subtextColor = getSubtextColor(data.widgetTheme)
        val scale = responsiveScale()
        val playButtonCornerRadius = if (data.isPlaying) 22.dp else 60.dp
        
        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding((18 * scale).dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Top: Album Art + Title/Artist with improved spacing
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtImage(
                        preloadedBitmap = data.preloadedBitmap,
                        size = (68 * scale).dp
                    )
                    Spacer(GlanceModifier.width((14 * scale).dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = data.songTitle,
                            style = TextStyle(
                                fontSize = (17 * scale).sp, 
                                fontWeight = FontWeight.Bold, 
                                color = textColor
                            ),
                            maxLines = 2
                        )
                        Spacer(GlanceModifier.height((6 * scale).dp))
                        if (data.showArtist && data.artistName.isNotEmpty()) {
                            Text(
                                text = data.artistName,
                                style = TextStyle(
                                    fontSize = (14 * scale).sp, 
                                    color = subtextColor
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
                
                
                
                Spacer(GlanceModifier.height((12 * scale).dp))
                
                // Bottom: Control buttons row
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height((54 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviousButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        iconSize = (26 * scale).dp,
                        cornerRadius = 26.dp
                    )
                    Spacer(GlanceModifier.width((10 * scale).dp))
                    PlayPauseButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        isPlaying = data.isPlaying,
                        iconSize = (28 * scale).dp,
                        cornerRadius = playButtonCornerRadius
                    )
                    Spacer(GlanceModifier.width((10 * scale).dp))
                    NextButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        iconSize = (26 * scale).dp,
                        cornerRadius = 26.dp
                    )
                }
            }
        }
    }
    
    // ==================== Large Widget: Art + info + full controls ====================
    @Composable
    private fun LargeWidgetLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        BigWidgetContent(
            modifier = modifier,
            data = data,
            minArtSize = (76 * scale).dp,
            artFraction = 0.42f,
            controlsHeight = (64 * scale).dp,
            titleFontSize = (18 * scale).sp,
            subFontSize = (15 * scale).sp,
            playIconSize = (32 * scale).dp,
            sideIconSize = (30 * scale).dp,
            contentPadding = (20 * scale).dp
        )
    }

    @Composable
    private fun ExtraLargeWidgetLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        BigWidgetContent(
            modifier = modifier,
            data = data,
            minArtSize = (96 * scale).dp,
            artFraction = 0.42f,
            controlsHeight = (70 * scale).dp,
            titleFontSize = (21 * scale).sp,
            subFontSize = (16 * scale).sp,
            playIconSize = (36 * scale).dp,
            sideIconSize = (34 * scale).dp,
            contentPadding = (20 * scale).dp
        )
    }

    @Composable
    private fun ExtraLargePlusWidgetLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        BigWidgetContent(
            modifier = modifier,
            data = data,
            minArtSize = (96 * scale).dp,
            artFraction = 0.46f,
            controlsHeight = (76 * scale).dp,
            titleFontSize = (22 * scale).sp,
            subFontSize = (17 * scale).sp,
            playIconSize = (38 * scale).dp,
            sideIconSize = (34 * scale).dp,
            contentPadding = (18 * scale).dp
        )
    }

    @Composable
    private fun HugeWidgetLayout(modifier: GlanceModifier, data: WidgetData) {
        val scale = responsiveScale()
        BigWidgetContent(
            modifier = modifier,
            data = data,
            minArtSize = (120 * scale).dp,
            artFraction = 0.48f,
            controlsHeight = (84 * scale).dp,
            titleFontSize = (24 * scale).sp,
            subFontSize = (18 * scale).sp,
            playIconSize = (44 * scale).dp,
            sideIconSize = (38 * scale).dp,
            contentPadding = (20 * scale).dp
        )
    }

    /**
     * Shared body for the large widget sizes (Large / Extra Large / Extra Large Plus / Huge).
     *
     * - Square & wide widgets show the cookie beside the song info (row header).
     * - Tall widgets (height notably greater than width) switch to a stacked header: the cookie
     *   sits on top with the song info centered below it, so the text is never cut off beside an
     *   oversized cookie. In that mode the favorite shows as a wide button under the controls.
     */
    @Composable
    private fun BigWidgetContent(
        modifier: GlanceModifier,
        data: WidgetData,
        minArtSize: Dp,
        artFraction: Float,
        controlsHeight: Dp,
        titleFontSize: TextUnit,
        subFontSize: TextUnit,
        playIconSize: Dp,
        sideIconSize: Dp,
        contentPadding: Dp
    ) {
        val textColor = getTextColor(data.widgetTheme)
        val subtextColor = getSubtextColor(data.widgetTheme)
        val size = LocalSize.current
        val scale = responsiveScale()
        val playButtonCornerRadius = if (data.isPlaying) 24.dp else 60.dp

        // Tall widgets get the stacked header so song info never gets crushed
        // beside a cookie that grew with the widget height.
        val useStacked = size.height > size.width * 1.1f
        // On the big layouts the favorite always sits below the controls as a
        // wide button (it moves out of the header entirely).
        val showWideFavorite = data.showFavoriteButton
        // Vertical room the wide favorite needs under the controls: the button
        // itself is fixed 44dp plus the scaled gap before it.
        val favRoom = if (showWideFavorite) (10 * scale).dp + 44.dp else 0.dp

        // Cookie size: grows with the widget, but bounded so the rest of the stack fits.
        val artSize = if (useStacked) {
            val roomForText = (60 * scale).dp + favRoom
            val maxStackedArt = (size.height - controlsHeight - contentPadding * 2 - roomForText)
                .coerceAtLeast(minArtSize)
            minOf(size.height * 0.30f, size.width * 0.55f).coerceIn(minArtSize, maxStackedArt)
        } else {
            (size.height * artFraction).coerceIn(
                minArtSize,
                (size.height - controlsHeight - contentPadding * 2 - favRoom).coerceAtLeast(minArtSize)
            )
        }

        val subText = buildString {
            if (data.showArtist && data.artistName.isNotEmpty()) {
                append(data.artistName)
            }
            if (data.showAlbum && data.albumName.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append(data.albumName)
            }
        }

        Box(
            modifier = modifier
                .cornerRadius(data.cornerRadius.dp)
                .padding(contentPadding)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (useStacked) {
                    // Stacked header: cookie on top, song info centered below it
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AlbumArtImage(
                            preloadedBitmap = data.preloadedBitmap,
                            size = artSize
                        )
                        Spacer(GlanceModifier.height((14 * scale).dp))
                        Text(
                            text = data.songTitle,
                            style = TextStyle(
                                fontSize = titleFontSize,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            ),
                            maxLines = 2
                        )
                        if (subText.isNotEmpty()) {
                            Spacer(GlanceModifier.height((4 * scale).dp))
                            Text(
                                text = subText,
                                style = TextStyle(
                                    fontSize = subFontSize,
                                    color = subtextColor
                                ),
                                maxLines = 2
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        AlbumArtImage(
                            preloadedBitmap = data.preloadedBitmap,
                            size = artSize
                        )
                        Spacer(GlanceModifier.width((16 * scale).dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = data.songTitle,
                                style = TextStyle(
                                    fontSize = titleFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                ),
                                maxLines = 2
                            )
                            if (subText.isNotEmpty()) {
                                Spacer(GlanceModifier.height((6 * scale).dp))
                                Text(
                                    text = subText,
                                    style = TextStyle(
                                        fontSize = subFontSize,
                                        color = subtextColor
                                    ),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Spacer(GlanceModifier.defaultWeight())

                // Controls row with fixed height to prevent stretching
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(controlsHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviousButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        iconSize = sideIconSize,
                        cornerRadius = sideIconSize
                    )
                    Spacer(GlanceModifier.width((12 * scale).dp))
                    PlayPauseButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        isPlaying = data.isPlaying,
                        iconSize = playIconSize,
                        cornerRadius = playButtonCornerRadius
                    )
                    Spacer(GlanceModifier.width((12 * scale).dp))
                    NextButton(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        iconSize = sideIconSize,
                        cornerRadius = sideIconSize
                    )
                }

                if (showWideFavorite) {
                    Spacer(GlanceModifier.height((10 * scale).dp))
                    FavoriteWideButton(
                        isFavorite = data.isFavorite,
                        cardCornerRadius = data.cornerRadius
                    )
                }

                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
    // ==================== Shared UI Components ====================

    /**
     * Responsive scale factor constrained by both widget width and height so
     * fonts, artwork and spacing scale harmoniously without pushing controls
     * off-screen on wide/short widgets (such as 4x1 or 4x2 on foldables).
     */
    @Composable
    private fun responsiveScale(): Float {
        val size = LocalSize.current
        return computeResponsiveScale(size.width.value, size.height.value)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun AlbumArtImage(
        modifier: GlanceModifier = GlanceModifier,
        preloadedBitmap: Bitmap?,
        size: Dp? = null
    ) {
        val context = LocalContext.current
        val uiMode = context.resources.configuration.uiMode
        val sizingModifier = if (size != null) modifier.size(size) else modifier
        // The app logo mark has more whitespace than a plain note, so nudge it
        // up a touch to keep its visual weight inside the cookie placeholder.
        val placeholderSizeDp = if (size != null) {
            val raw = size.value * 0.6f
            raw.coerceIn(36f, 120f).dp
        } else {
            43.dp // 72 * 0.6 = 43.2
        }
        val artSizeDp = (size?.value ?: 96f).toInt().coerceAtLeast(40)

        // Album art (and its placeholder) clipped into the 12-sided cookie shape
        val cookieArt = remember(preloadedBitmap, uiMode, artSizeDp) {
            preloadedBitmap?.let {
                GlanceShapeBitmaps.create(context, artSizeDp, MaterialShapes.Cookie12Sided, sourceBitmap = it)
            }
        }
        val cookiePlaceholder = remember(uiMode, artSizeDp) {
            GlanceShapeBitmaps.create(context, artSizeDp, MaterialShapes.Cookie12Sided)
        }

        Box(modifier = sizingModifier, contentAlignment = Alignment.Center) {
            if (cookieArt != null) {
                Image(
                    provider = ImageProvider(cookieArt),
                    contentDescription = LocalContext.current.getString(R.string.settings_shapes_album_art),
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    provider = ImageProvider(cookiePlaceholder),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer)
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_notification),
                    contentDescription = LocalContext.current.getString(R.string.rhythmmusicwidget_album_art_placeholder),
                    modifier = GlanceModifier.size(placeholderSizeDp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
            }
        }
    }
    
    @Composable
    private fun PlayPauseButton(
        modifier: GlanceModifier = GlanceModifier,
        isPlaying: Boolean,
        iconColor: ColorProvider = GlanceTheme.colors.onPrimary,
        backgroundColor: ColorProvider = GlanceTheme.colors.primary,
        iconSize: Dp = 24.dp,
        cornerRadius: Dp = 24.dp
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<PlayPauseAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }
    
    @Composable
    private fun NextButton(
        modifier: GlanceModifier = GlanceModifier,
        iconColor: ColorProvider = GlanceTheme.colors.onTertiary,
        backgroundColor: ColorProvider = GlanceTheme.colors.tertiary,
        iconSize: Dp = 24.dp,
        cornerRadius: Dp = 24.dp
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<SkipNextAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_next),
                contentDescription = LocalContext.current.getString(R.string.onboarding_next),
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }
    
    @Composable
    private fun PreviousButton(
        modifier: GlanceModifier = GlanceModifier,
        iconColor: ColorProvider = GlanceTheme.colors.onTertiary,
        backgroundColor: ColorProvider = GlanceTheme.colors.tertiary,
        iconSize: Dp = 24.dp,
        cornerRadius: Dp = 24.dp
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<SkipPreviousAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_previous),
                contentDescription = LocalContext.current.getString(R.string.animatedplaybackcontrols_previous),
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }
    
    /**
     * Wide favorite button shown below the controls on the big widget layouts.
     * Uses a reddish translucent fill with a red heart so it reads as the
     * favorite control regardless of the widget theme.
     */
    @Composable
    private fun FavoriteWideButton(
        modifier: GlanceModifier = GlanceModifier,
        isFavorite: Boolean,
        cardCornerRadius: Int
    ) {
        val buttonCornerRadius = (cardCornerRadius * 18 / 28).dp
        // Reddish button + icon on every theme
        val bgColor = ColorProvider(Color(0x2EFF5252))
        val iconColor = if (isFavorite) {
            ColorProvider(Color(0xFFFF5252)) // vivid red when favorited
        } else {
            ColorProvider(Color(0xFFFFB4AB)) // soft coral outline when not
        }
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp)
                .cornerRadius(buttonCornerRadius)
                .background(bgColor)
                .clickable(actionRunCallback<ToggleFavoriteAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
                ),
                contentDescription = LocalContext.current.getString(R.string.player_chip_favorite),
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }
    
    @Composable
    private fun getBgColor(theme: Int): ColorProvider {
        return when (theme) {
            1 -> ColorProvider(Color(0xFF131215)) // Solid Dark
            2 -> ColorProvider(Color(0xD9131215)) // Translucent Dark
            3 -> ColorProvider(Color(0xFF2D235C)) // Solid Purple
            else -> GlanceTheme.colors.widgetBackground // Dynamic
        }
    }
    
    @Composable
    private fun getTextColor(theme: Int): ColorProvider {
        return when (theme) {
            3 -> ColorProvider(Color(0xFFFFFFFF)) // White on Purple
            1, 2 -> ColorProvider(Color(0xFFE6E1E5)) // Light on Dark
            else -> GlanceTheme.colors.onSurface
        }
    }
    
    @Composable
    private fun getSubtextColor(theme: Int): ColorProvider {
        return when (theme) {
            3 -> ColorProvider(Color(0xFFE8DEF8)) // Lavender on Purple
            1, 2 -> ColorProvider(Color(0xFFCAC4D0)) // Gray on Dark
            else -> GlanceTheme.colors.onSurfaceVariant
        }
    }
    
    private fun getWidgetData(prefs: Preferences, appSettings: chromahub.rhythm.app.shared.data.model.AppSettings?): WidgetData {
        return try {
            WidgetData(
                songTitle = prefs[stringPreferencesKey(KEY_SONG_TITLE)] ?: "Rhythm",
                artistName = prefs[stringPreferencesKey(KEY_ARTIST_NAME)] ?: "",
                albumName = prefs[stringPreferencesKey(KEY_ALBUM_NAME)] ?: "",
                isPlaying = prefs[booleanPreferencesKey(KEY_IS_PLAYING)] ?: false,
                artworkUri = prefs[stringPreferencesKey(KEY_ARTWORK_URI)]?.takeIf { it.isNotBlank() }?.let { 
                    try { 
                        (it).toUri() 
                    } catch (e: Exception) { 
                        null 
                    } 
                },
                hasPrevious = prefs[booleanPreferencesKey(KEY_HAS_PREVIOUS)] ?: false,
                hasNext = prefs[booleanPreferencesKey(KEY_HAS_NEXT)] ?: false,
                isFavorite = prefs[booleanPreferencesKey(KEY_IS_FAVORITE)] ?: false,
                showAlbumArt = appSettings?.widgetShowAlbumArt?.value ?: true,
                showArtist = appSettings?.widgetShowArtist?.value ?: true,
                showAlbum = appSettings?.widgetShowAlbum?.value ?: false,
                showFavoriteButton = appSettings?.widgetShowFavoriteButton?.value ?: true,
                cornerRadius = appSettings?.widgetCornerRadius?.value ?: 28,
                widgetTheme = appSettings?.widgetTheme?.value ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("RhythmMusicWidget", "Error getting widget data", e)
            // Return default data if anything fails
            WidgetData(
                songTitle = "Rhythm",
                artistName = "",
                albumName = "",
                isPlaying = false,
                artworkUri = null,
                hasPrevious = false,
                hasNext = false,
                isFavorite = false,
                widgetTheme = 0
            )
        }
    }
}

/**
 * Widget data class with M3 Expressive properties
 */
data class WidgetData(
    val songTitle: String,
    val artistName: String,
    val albumName: String,
    val isPlaying: Boolean,
    val artworkUri: android.net.Uri?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val isFavorite: Boolean = false,
    val showAlbumArt: Boolean = true,
    val showArtist: Boolean = true,
    val showAlbum: Boolean = false,
    val showFavoriteButton: Boolean = true,
    val cornerRadius: Int = 28,
    val widgetTheme: Int = 0,
    val preloadedBitmap: Bitmap? = null,
    val isShuffle: Boolean = false,
    val repeatMode: Int = 0,
    val cookieBottomLeft: Int = 0,
    val cookieBottomRight: Int = 0
)
