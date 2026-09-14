package com.surafel.audio

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckersReferenceTest {
    @Test fun boardAndVisualCatalogAndStatisticsRenderOnDevice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("checkers", 0).edit().clear().putInt("stars", 21).commit()
        val instrument = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(CheckersActivity::class.java).use { scenario ->
            fun click(name: String) { scenario.onActivity { activity -> descendants(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == name }.performClick() }; instrument.waitForIdleSync() }
            fun panelClick(name: String) {
                scenario.onActivity { activity ->
                    val field = CheckersActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }
                    val dialog = field.get(activity) as androidx.appcompat.app.AlertDialog
                    descendants(dialog.window!!.decorView).first { it.contentDescription == name || it is android.widget.RadioButton && it.text.toString() == name }.performClick()
                }; instrument.waitForIdleSync()
            }
            PdfTestScreenshots.capture("checkers-home", scenario)
            click("Settings")
            PdfTestScreenshots.captureDisplay("checkers-settings")
            panelClick("Board Size")
            PdfTestScreenshots.captureDisplay("checkers-board-size")
            panelClick("6x6")
            assertEquals(6, app.getSharedPreferences("checkers", 0).getInt("board_size", 0))
            panelClick("Play as")
            PdfTestScreenshots.captureDisplay("checkers-play-as")
            panelClick("White")
            instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            click("Stats")
            PdfTestScreenshots.captureDisplay("checkers-stats")
            instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            click("Design")
            PdfTestScreenshots.captureDisplay("checkers-design")
            instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<View>("checkers-local").performClick() }
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val board = activity.window.decorView.findViewWithTag<View>("checkers-board")
                assertEquals(board.width, board.height); assertTrue(board.width > 100)
                assertEquals(36, (board as com.surafel.audio.checkers.CheckersBoardView).position.board.size)
                assertEquals("Your turn", activity.window.decorView.findViewWithTag<TextView>("checkers-game-info").text.toString())
            }
            PdfTestScreenshots.capture("checkers-board", scenario)
        }
    }
    private fun descendants(view: View): Sequence<View> = sequence { yield(view); if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i))) }
}
