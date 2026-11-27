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

    @Test
    fun demoPlaylistPlays() {
        rule.waitUntilAtLeastOneExists(hasText("Try legal demo channels"), 20_000)
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
        rule.waitUntilAtLeastOneExists(hasTestTag("player_ready"), 60_000)
        Thread.sleep(3000)
        shot("05_player")

        // Back to the app, then check the Arabic (RTL) interface.
        val inst = InstrumentationRegistry.getInstrumentation()
        // BACK hides the overlay first (if shown), then closes the player: press until the main screen is back.
        repeat(3) {
            if (rule.onAllNodes(hasText("Live TV")).fetchSemanticsNodes().isNotEmpty()) return@repeat
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            Thread.sleep(1500)
        }
        rule.waitUntilAtLeastOneExists(hasText("Live TV"), 10_000)
        rule.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }
        rule.waitUntilAtLeastOneExists(hasText("المباريات"), 15_000)
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
        rule.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
    }
}
