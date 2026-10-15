// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.video.MusicVideo
import chromahub.rhythm.app.features.local.data.video.MusicVideoRepository
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.screens.player.ExpressivePlayerScreen
import chromahub.rhythm.app.shared.presentation.screens.player.artworkValidationCache
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlayerRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = AppSettings.getInstance(context)
    private val oldBackdrop = settings.playerAmbientBackdropEnabled.value
    private val oldAccent = settings.playerAccentBackgroundEnabled.value
    private val oldFetch = settings.autoFetchArtwork.value
    private val oldMerge = settings.playerMergeControlsToBottom.value
    private val oldOrder = settings.expressiveBottomButtonsMerge.value
    private val oldHidden = settings.expressiveHiddenBottomButtonsMerge.value
    private val files = mutableListOf<File>()

    @After fun restore() {
        settings.setPlayerAmbientBackdropEnabled(oldBackdrop)
        settings.setPlayerAccentBackgroundEnabled(oldAccent)
        settings.setAutoFetchArtwork(oldFetch)
        settings.setPlayerMergeControlsToBottom(oldMerge)
        settings.setExpressiveBottomButtonsMerge(oldOrder)
        settings.setExpressiveHiddenBottomButtonsMerge(oldHidden)
        files.forEach { it.delete() }
    }

    private fun song(): Song {
        val art = File(context.cacheDir, "regression-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff556688.toInt())
        art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        files.add(art)
        val uri = Uri.fromFile(art); artworkValidationCache[uri] = true
        return Song("regression-${System.nanoTime()}", "Video regression", "Test", "Cover album", duration = 24000L,
            uri = Uri.parse("content://t2rhythm-test/${System.nanoTime()}"), artworkUri = uri)
    }

    @Test fun managerStillOpensWhenNoArtworkIsComposed() {
        val song = song(); val controls = MusicVideoControls()
        val showArtwork = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                MusicVideoSession(song, controls)
                if (showArtwork.value) MusicVideoArtwork(song, false, { 0L }, controls = controls)
                Button(onClick = { controls.managing.value = true }, modifier = Modifier.testTag("manage")) { Text("Manage") }
            }
        }
        compose.runOnIdle { showArtwork.value = false }
        compose.onNodeWithTag("manage").performClick()
        compose.onNodeWithText(context.getString(R.string.mv_watch)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mv_link)).assertExists()
    }

    @Test fun videoAppearsWithValidCoverAndAmbientBackdrop() {
        val song = song(); val controls = MusicVideoControls()
        val root = File(context.filesDir, "music_metadata/videos").apply { mkdirs() }
        val video = File(root, "regression-${System.nanoTime()}.webm"); files.add(video)
        InstrumentationRegistry.getInstrumentation().context.assets.open("video-test.webm").use { input -> video.outputStream().use(input::copyTo) }
        MusicVideoRepository(context).save(MusicVideo(song.uri.toString(), "local", fileName = video.name, title = "Regression"))
        files.add(File(root, "records/${kr.co.metadata.crawler.CatalogFileStore.hash(song.uri.toString())}.json"))
        settings.setAutoFetchArtwork(false); settings.setPlayerAmbientBackdropEnabled(true)
        render(song, controls)
        compose.waitUntil(120000) { controls.hasVideo }
        val selectorBefore = compose.onNodeWithText(context.getString(R.string.personal_video)).getUnclippedBoundsInRoot()
        screenshot("full-cover-audio.png")
        compose.onNodeWithText(context.getString(R.string.personal_video)).performClick()
        // Wait for composition before examining loading; immediately after the click it can
        // still hold the previous audio-only value while the video is being mounted.
        compose.onNodeWithTag("music_video_surface").assertExists()
        compose.waitUntil(120000) { !controls.loading.value }
        compose.waitForIdle()
        assertNull(controls.error.value)
        compose.onAllNodesWithTag("music_video_surface").assertCountEquals(1)
        compose.onNodeWithTag("music_video_surface").assertIsDisplayed()
        val backdrop = compose.onNodeWithTag("player_video_backdrop").getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals(screen.left.value, backdrop.left.value, 1f)
        assertEquals(screen.top.value, backdrop.top.value, 1f)
        assertEquals(screen.right.value, backdrop.right.value, 1f)
        assertTrue("Video must cover the whole artwork backdrop", (backdrop.bottom.value - backdrop.top.value) >= (screen.bottom.value - screen.top.value) * .6f)
        val selectorAfter = compose.onNodeWithText(context.getString(R.string.personal_video)).getUnclippedBoundsInRoot()
        assertEquals("Selecting video must not add a square or move controls", selectorBefore.top.value, selectorAfter.top.value, 2f)
        compose.runOnIdle {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            fun textures(view: android.view.View): List<android.view.TextureView> = when (view) {
                is android.view.TextureView -> listOf(view)
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { textures(view.getChildAt(it)) }
                else -> emptyList()
            }
            val texture = textures(activity.window.decorView).single()
            val clipped = texture.parent.parent as android.widget.FrameLayout
            assertTrue(clipped.clipChildren)
            assertTrue(texture.width >= clipped.width && texture.height >= clipped.height)
            assertTrue("Crop must fill without black bars", texture.width > clipped.width || texture.height > clipped.height)
        }
        screenshot("full-cover-video.png")
        compose.onNodeWithText(context.getString(R.string.personal_audio)).performClick()
        compose.onNodeWithTag("music_video_surface").assertDoesNotExist()
    }

    @Test fun videoFailureOffersRetryAndWorkingManagementOutsideTheBackdrop() {
        val song = song(); val controls = MusicVideoControls()
        val local = File(context.filesDir, "music_metadata/videos/status-${System.nanoTime()}.webm")
        local.parentFile!!.mkdirs(); files.add(local)
        InstrumentationRegistry.getInstrumentation().context.assets.open("video-test.webm").use { input -> local.outputStream().use(input::copyTo) }
        val video = MusicVideo(song.uri.toString(), "local", fileName = local.name, title = song.title)
        MusicVideoRepository(context).save(video); controls.video.value = video
        files.add(File(context.filesDir, "music_metadata/videos/records/${kr.co.metadata.crawler.CatalogFileStore.hash(song.uri.toString())}.json"))
        controls.active.value = true
        controls.error.value = context.getString(R.string.mv_embed_disabled)
        compose.setContent { MaterialTheme { Column {
            MusicVideoSession(song, controls)
            MusicVideoStatus(controls)
        } } }
        compose.onNodeWithTag("music_video_error").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mv_retry)).performClick()
        assertEquals(1, controls.retry.intValue)
        compose.onNodeWithText(context.getString(R.string.mv_manage)).performClick()
        compose.onNodeWithText(context.getString(R.string.mv_watch)).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }

    @Test fun fiveSelectedActionsLeaveOneFixedOverflowAtTopRight() {
        val song = song(); val controls = MusicVideoControls()
        settings.setAutoFetchArtwork(false)
        settings.setPlayerMergeControlsToBottom(true)
        settings.setExpressiveBottomButtonsMerge(listOf("LYRICS", "FAVORITE", "DEVICE", "QUEUE", "SHUFFLE", "MORE"))
        settings.setExpressiveHiddenBottomButtonsMerge(emptySet())
        assertEquals(5, settings.expressiveBottomButtonsMerge.value.size)
        assertFalse(settings.expressiveBottomButtonsMerge.value.contains("MORE"))
        render(song, controls)
        compose.onAllNodesWithTag("player_overflow").assertCountEquals(1)
        val bounds = compose.onNodeWithTag("player_overflow").getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(bounds.top.value < 96f)
        assertTrue(bounds.left.value > (screen.right.value - screen.left.value) * .6f)
        compose.onNodeWithTag("player_overflow").performClick()
        compose.onNodeWithText(context.getString(R.string.mv_watch)).assertIsDisplayed()
    }

    @Test fun coverDoubleTapLikesIdempotentlyAndTheNormalButtonCanStillUnlike() {
        val song = song(); val controls = MusicVideoControls(); val liked = mutableStateOf(false)
        var seek = .5f
        settings.setAutoFetchArtwork(false); settings.setPlayerAmbientBackdropEnabled(true)
        settings.setPlayerMergeControlsToBottom(true)
        settings.setExpressiveBottomButtonsMerge(listOf("FAVORITE", "LYRICS", "SHUFFLE", "REPEAT"))
        settings.setExpressiveHiddenBottomButtonsMerge(emptySet())
        render(song, controls, liked) { seek = it }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("cover_gesture_surface").performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitUntil(5000) { liked.value }
        compose.onNodeWithContentDescription(context.getString(R.string.cover_liked)).assertIsDisplayed()
        screenshot("durable-cover-like.png")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("cover_gesture_surface").performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertTrue(liked.value) }
        compose.onNodeWithContentDescription(context.getString(R.string.expressiveplayerscreen_favorite)).performClick()
        compose.runOnIdle { assertFalse(liked.value) }
        compose.onNodeWithTag("cover_gesture_surface").performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(width * .06f, height * .25f)) }
        compose.runOnIdle { assertTrue(seek < .5f) }
    }

    private fun render(song: Song, controls: MusicVideoControls, liked: MutableState<Boolean> = mutableStateOf(false), onSeek: (Float) -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    MusicVideoSession(song, controls)
                    ExpressivePlayerScreen(song = song, videoControls = controls, isPlaying = false, isFavorite = liked.value,
                        progress = { .5f }, currentTimeStr = "0:00", totalTimeStr = "0:24", queuePosition = 1, queueTotal = 1,
                        isShuffleEnabled = false, repeatMode = 0, showLyricsView = false, showLyrics = true, lyrics = null,
                        isLoadingLyrics = false, onlineOnlyLyrics = false, onLyricsSeek = null, onRetryLyrics = {},
                        onShowLyricsEditor = {}, onPickLyricsFile = {}, isMediaLoading = false, isSeeking = false,
                        onPlayPause = {}, onSeek = onSeek, onSkipPrevious = {}, onSkipNext = {}, onToggleFavorite = { liked.value = !liked.value }, onLikeSong = { liked.value = true },
                        onToggleShuffle = {}, onToggleRepeat = {}, onToggleLyrics = {}, onSongInfoClick = {},
                        onShowAlbumBottomSheet = {}, onShowArtist = {}, onMoreClick = { controls.managing.value = true },
                        onDeviceClick = {}, onQueueClick = {}, onBack = {}, location = null, appSettings = settings)
                }
            }
        }
    }
}
