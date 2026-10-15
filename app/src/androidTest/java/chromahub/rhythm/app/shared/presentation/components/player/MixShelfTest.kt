// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.RecommendationShelf
import chromahub.rhythm.app.features.local.presentation.metadata.PersonalCollectionsContent
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MixShelfTest {
    @get:Rule val compose = createComposeRule()
    private val context by lazy {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        base.createConfigurationContext(android.content.res.Configuration(base.resources.configuration).apply { setLocale(java.util.Locale.KOREAN) })
    }
    @Test fun refreshingClearsTheNewGenerationVoteAndBothListsRemainInTheDrawer() {
        val art = File(context.cacheDir, "mix-art-${System.nanoTime()}.png")
        Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(0xff72566e.toInt()); art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val nonce = System.nanoTime()
        val songs = (1..32).map { Song("mix-$nonce-$it", "나의 음악 $it", "아티스트 ${it % 4}", "앨범 ${it % 6}",
            duration = 180_000, uri = Uri.parse("file:///test/$nonce/$it.mp3"), artworkUri = Uri.fromFile(art), genre = if (it % 2 == 0) "Pop" else "R&B") }
        try {
            compose.setContent { androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides context,
                androidx.compose.ui.platform.LocalConfiguration provides context.resources.configuration) { MaterialTheme { Column(Modifier.fillMaxWidth().padding(24.dp)) {
                PersonalCollectionsContent(songs, emptyList(), null, onPlayQueue = { _, _ -> }, onCreatePlaylist = { _, _ -> })
            } } } }
            compose.waitUntil(20000) { RecommendationShelf.current(context)?.songKeys?.firstOrNull()?.contains(nonce.toString()) == true }
            val first = RecommendationShelf.current(context)!!
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText(context.getString(R.string.personal_like)).assertIsEnabled(); true }.getOrDefault(false) }
            compose.onNodeWithText(context.getString(R.string.personal_like)).performClick()
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText(context.getString(R.string.personal_like)).assertIsSelected(); true }.getOrDefault(false) }
            screenshot("durable-mix-card.png")
            compose.onNodeWithContentDescription(context.getString(R.string.personal_refresh_mix)).performClick()
            compose.waitUntil(20000) { RecommendationShelf.current(context)?.id != first.id }
            compose.waitForIdle()
            compose.waitUntil(15000) { runCatching {
                compose.onNodeWithText(context.getString(R.string.personal_like)).assertIsEnabled().assertIsNotSelected(); true
            }.getOrDefault(false) }
            assertTrue(RecommendationShelf.recent(context).any { it.id == first.id })
            compose.onNodeWithContentDescription(context.getString(R.string.personal_mix_shelf)).performClick()
            compose.waitUntil(15000) { runCatching {
                compose.onNodeWithText(context.getString(R.string.personal_mix_shelf_hint)).assertIsDisplayed(); true
            }.getOrDefault(false) }
            screenshot("durable-mix-shelf.png")
        } finally { art.delete() }
    }
    private fun screenshot(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
