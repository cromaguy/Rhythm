/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile for Rhythm.
 *
 * Profiled user journeys are derived from the Perfetto v49.0 trace:
 *
 *  Journey 1 – Cold startup (bindApplication 442ms → activityIdle 4.58s)
 *    Covers: RhythmApplication.onCreate, AppSettings init, ActivityThread, Compose bootstrap
 *
 *  Journey 2 – HomeScreen first render + scroll
 *    The 330ms worst frame at +2.764s was the first Compose draw of MainActivity/HomeScreen.
 *    Scrolling warms up LazyColumn item composables and their drawing lambdas.
 *
 *  Journey 3 – Library screen navigation
 *    LibraryScreen.kt is 304 KB of Compose; navigating to it forces all its composables
 *    through JIT which the profile will pre-compile to native.
 *
 *  Journey 4 – Back to Home (nav back stack warm-up)
 *    Ensures the NavBackStack composables are also captured.
 *
 * PowerShell:
 *   .\gradlew.bat :macrobenchmark:connectedGithubBenchmarkAndroidTest
 *       "-Pandroid.testInstrumentationRunnerArguments.class=chromahub.rhythm.app.benchmark.BaselineProfileGenerator"
 *       --no-daemon
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = APP_PACKAGE,
        includeInStartupProfile = true,
    ) {
        grantRhythmRuntimePermissions(APP_PACKAGE)

        // ── Journey 1: Cold app startup ──────────────────────────────────────
        // Launches the app from a cold state and waits until the first frame is drawn.
        // This single call covers the entire bindApplication → activityIdle path:
        //   ActivityThreadMain, makeApplication, RhythmApplication.onCreate,
        //   clientTransactionExecuted, activityStart, activityResume, SplashScreen draw.
        startActivityAndWait()

        // Wait for the SplashScreen animation to complete and HomeScreen to settle.
        // Perfetto shows activityIdle at +4.58s; we give a generous margin.
        device.waitForIdle(6_000)

        // ── Journey 2: HomeScreen scroll ─────────────────────────────────────
        // The 330ms frame at +2.764s was the initial Compose traversal of HomeScreen
        // (257ms draw + 72ms animation). Scrolling warms up all LazyColumn items
        // and their measure/draw lambdas so ART can pre-compile them.
        scrollHomeScreen()

        // ── Journey 3: Navigate to Library ───────────────────────────────────
        // LibraryScreen is the app's largest screen (304 KB source).
        // Navigating to it triggers its full Compose composition + layout.
        navigateToLibrary()

        // ── Journey 4: Navigate back to Home ─────────────────────────────────
        // Ensures the back-stack composables (HomeScreen recomposition path) are profiled.
        navigateToHome()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun MacrobenchmarkScope.scrollHomeScreen() {
        repeat(2) {
            swipeVertically(up = true)
            device.waitForIdle(500)
        }
        swipeVertically(up = false)
        device.waitForIdle(500)
    }

    private fun MacrobenchmarkScope.navigateToLibrary() {
        // Try multiple selector strategies since content descriptions may be translated
        val libraryButton = device.findObject(By.desc("Library"))
            ?: device.findObject(By.text("Library"))
            ?: device.findObject(By.descContains("library"))
            ?: return

        libraryButton.click()

        // Wait for LibraryScreen to be fully composed (large screen, allow time)
        device.wait(Until.hasObject(By.scrollable(true)), 3_000)
        device.waitForIdle(2_000)

        // Scroll library content to warm up item composables
        val libraryScroll = device.findObject(By.scrollable(true))
        libraryScroll?.let {
            swipeVertically(up = true)
            device.waitForIdle(500)
        }

        openTabAndScroll("Settings")
        openTabAndScroll("Search")
        expandMiniPlayerIfPresent()
    }

    private fun MacrobenchmarkScope.openTabAndScroll(label: String) {
        val target = device.findObject(By.desc(label))
            ?: device.findObject(By.text(label))
            ?: device.findObject(By.descContains(label.lowercase()))
            ?: return
        target.click()
        device.waitForIdle(900)
        device.findObject(By.scrollable(true))?.let {
            swipeVertically(up = true)
            device.waitForIdle(400)
            swipeVertically(up = false)
            device.waitForIdle(400)
        }
    }

    private fun MacrobenchmarkScope.expandMiniPlayerIfPresent() {
        val miniBar = device.findObject(By.descContains("mini player"))
            ?: device.findObject(By.descContains("now playing"))
            ?: return
        miniBar.click()
        device.waitForIdle(900)
        device.pressBack()
        device.waitForIdle(600)
    }

    private fun MacrobenchmarkScope.swipeVertically(up: Boolean) {
        val x = device.displayWidth / 2
        val top = (device.displayHeight * 0.35f).toInt()
        val bottom = (device.displayHeight * 0.75f).toInt()
        val startY = if (up) bottom else top
        val endY = if (up) top else bottom
        device.swipe(x, startY, x, endY, 24)
    }

    private fun MacrobenchmarkScope.navigateToHome() {
        val homeButton = device.findObject(By.desc("Home"))
            ?: device.findObject(By.text("Home"))
            ?: device.findObject(By.descContains("home"))
            ?: return

        homeButton.click()
        device.waitForIdle(1_500)
    }

    private companion object {
        const val APP_PACKAGE = "chromahub.rhythm.app"
    }
}
