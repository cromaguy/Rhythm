// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        compose.onNodeWithText(context.getString(R.string.personal_video)).performClick()
        compose.onNodeWithTag("music_video_surface").assertIsDisplayed()
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

    private fun render(song: Song, controls: MusicVideoControls) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    MusicVideoSession(song, controls)
                    ExpressivePlayerScreen(song = song, videoControls = controls, isPlaying = false, isFavorite = false,
                        progress = { 0f }, currentTimeStr = "0:00", totalTimeStr = "0:24", queuePosition = 1, queueTotal = 1,
                        isShuffleEnabled = false, repeatMode = 0, showLyricsView = false, showLyrics = true, lyrics = null,
                        isLoadingLyrics = false, onlineOnlyLyrics = false, onLyricsSeek = null, onRetryLyrics = {},
                        onShowLyricsEditor = {}, onPickLyricsFile = {}, isMediaLoading = false, isSeeking = false,
                        onPlayPause = {}, onSeek = {}, onSkipPrevious = {}, onSkipNext = {}, onToggleFavorite = {},
                        onToggleShuffle = {}, onToggleRepeat = {}, onToggleLyrics = {}, onSongInfoClick = {},
                        onShowAlbumBottomSheet = {}, onShowArtist = {}, onMoreClick = { controls.managing.value = true },
                        onDeviceClick = {}, onQueueClick = {}, onBack = {}, location = null, appSettings = settings)
                }
            }
        }
    }
}
