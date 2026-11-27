package com.ultimateiptv.sports

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.view.KeyEvent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ultimateiptv.sports.player.PlayerActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end check on a real device/emulator:
 * add the legal demo playlist → open Live TV → play a public HLS test stream → verify playback.
 * Screenshots are written to the app's external files dir (pulled by CI).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.waitForIdleSync()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        val dir = File(inst.targetContext.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        // Small preview copy (printed into the CI log for review).
        val w = 400
        val small = Bitmap.createScaledBitmap(bmp, w, bmp.height * w / bmp.width, true)
        File(dir, "$name.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 60, it) }
    }

    /** Like waitUntilAtLeastOneExists, but tolerant to the moment without UI while an activity rotates/recreates. */
    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher, timeout: Long) {
        rule.waitUntil(timeout) { runCatching { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }

    @Test
    fun demoPlaylistPlays() {
        // The app starts in Arabic by default; run the first part in English, Arabic is checked below.
        rule.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        waitFor(hasText("Try legal demo channels"), 20_000)
        shot("01_home_empty")
        rule.onAllNodes(hasText("Try legal demo channels")).onFirst().performClick()

        // Demo import + synthetic EPG
        rule.waitUntilAtLeastOneExists(hasText("Sports channels"), 30_000)
        shot("02_home_demo")

        rule.onAllNodes(hasText("Matches")).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Today"), 10_000)
        Thread.sleep(1500)
        shot("03_matches")

        rule.onAllNodes(hasText("Live TV")).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Demo Adaptive HLS (Mux)"), 15_000)
        Thread.sleep(1000)
        shot("04_live_tv")

        rule.onAllNodes(hasText("Demo Adaptive HLS (Mux)")).onFirst().performClick()
        // The player marks itself "player_ready" once frames are playing.
        waitFor(hasTestTag("player_ready"), 60_000)
        Thread.sleep(3000)
        shot("05_player")

        // Back to the app, then check the Arabic (RTL) interface.
        val inst = InstrumentationRegistry.getInstrumentation()
        // BACK hides the overlay first (if shown), then closes the player: press until the main screen is back.
        fun playerOnTop(): Boolean {
            var top = false
            inst.runOnMainSync {
                top = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is PlayerActivity }
            }
            return top
        }
        var tries = 0
        while (playerOnTop() && tries++ < 4) {
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            Thread.sleep(1500)
        }
        try {
            waitFor(hasText("Live TV"), 15_000)
        } catch (e: Throwable) {
            shot("99_after_back_failure")
            throw e
        }
        rule.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }
        waitFor(hasText("المباريات"), 15_000)
        rule.onAllNodes(hasText("الرئيسية")).onFirst().performClick()
        Thread.sleep(1500)
        shot("06_home_ar")
        rule.onAllNodes(hasText("المباريات")).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("اليوم"), 10_000)
        Thread.sleep(1000)
        shot("07_matches_ar")
        rule.onAllNodes(hasTestTag("match_card")).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("أين تشاهد (من قنواتك)"), 10_000)
        Thread.sleep(1000)
        shot("08_match_detail_ar")
        // Back to the match list, scrolled further, to review more club crests.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        Thread.sleep(1500)
        shot("09_matches_ar_list")

    }
}
