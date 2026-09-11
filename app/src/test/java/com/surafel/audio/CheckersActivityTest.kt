package com.surafel.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.surafel.audio.checkers.CheckersBoardView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35], qualifiers = "w360dp-h800dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class CheckersActivityTest {
    @Before fun clean() { RuntimeEnvironment.getApplication().getSharedPreferences("checkers", 0).edit().clear().commit() }
    @Test fun twoPlayersMoveByTouchRotateAndUndoWithoutLosingState() {
        Robolectric.buildActivity(CheckersActivity::class.java).use { c ->
            val a = c.setup().visible().get(); var root = a.findViewById<ViewGroup>(android.R.id.content)
            root.findViewWithTag<View>("checkers-local").performClick(); layout(root)
            val board = root.findViewWithTag<CheckersBoardView>("checkers-board")
            val move = board.legal.first(); move.path.forEach { tap(board, it) }
            assertEquals(1, board.position.ply)
            c.recreate(); root = c.get().findViewById(android.R.id.content); layout(root)
            assertEquals(1, root.findViewWithTag<CheckersBoardView>("checkers-board").position.ply)
            root.findViewWithTag<View>("checkers-undo").performClick()
            assertEquals(0, root.findViewWithTag<CheckersBoardView>("checkers-board").position.ply)
        }
    }
    @Test fun savedSoloGameAndDifficultySurviveClosingActivity() {
        Robolectric.buildActivity(CheckersActivity::class.java).use { c ->
            val root = c.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
            root.findViewWithTag<View>("checkers-difficulty").performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            descendants(dialog.findViewById(android.R.id.content)).filterIsInstance<TextView>().first { it.text.toString() == "Expert" }.performClick()
            root.findViewWithTag<View>("checkers-solo").performClick()
            assertTrue(root.findViewWithTag<TextView>("checkers-game-info").text.contains("Expert"))
        }
        Robolectric.buildActivity(CheckersActivity::class.java).use { c ->
            val root = c.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
            root.findViewWithTag<View>("checkers-continue").performClick()
            assertTrue(root.findViewWithTag<TextView>("checkers-game-info").text.contains("Expert"))
            assertEquals(0, root.findViewWithTag<CheckersBoardView>("checkers-board").position.ply)
        }
    }
    @Test fun nearbyExplainsOfflineSetupAndRejectsBadRoomCode() {
        Robolectric.buildActivity(CheckersActivity::class.java).use { c ->
            val root = c.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
            root.findViewWithTag<View>("checkers-nearby").performClick()
            var dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>("checkers-host"))
            dialog.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>("checkers-join").performClick()
            dialog = ShadowAlertDialog.getLatestAlertDialog()
            val body = dialog.findViewById<ViewGroup>(android.R.id.content)
            val input = body.findViewWithTag<EditText>("checkers-join-code"); input.setText("8.8.8.8:45000/123456")
            descendants(body).filterIsInstance<TextView>().first { it.text.toString() == "Connect" }.performClick()
            assertNotNull(input.error)
            assertNull(root.findViewWithTag<View>("checkers-board"))
        }
    }
    @Test @Config(sdk = [33, 35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderHomeAndPlayableBoard() {
        Robolectric.buildActivity(CheckersActivity::class.java).use { c ->
            val root = c.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
            assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "‹  Audio" })
            layout(root); snapshot(root, "home")
            root.findViewWithTag<View>("checkers-local").performClick(); layout(root)
            val board = root.findViewWithTag<CheckersBoardView>("checkers-board")
            assertEquals(board.width, board.height); assertTrue(board.width in 600..720)
            val rect = android.graphics.Rect(); assertTrue(board.getGlobalVisibleRect(rect))
            assertEquals(board.height, rect.height()); snapshot(root, "board")
        }
    }
    private fun tap(board: CheckersBoardView, square: Int) {
        val inset = board.width * .025f; val cell = (board.width - 2 * inset) / board.rules.size
        val x = inset + (square % board.rules.size + .5f) * cell
        val y = inset + (square / board.rules.size + .5f) * cell
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(0, 0, action, x, y, 0); board.dispatchTouchEvent(e); e.recycle()
        }
    }
    private fun layout(root: View) { repeat(3) {
        root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 720, 1600); shadowOf(Looper.getMainLooper()).idle()
    } }
    private fun snapshot(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
        val file = File("build/checkers-previews/$name-api-${android.os.Build.VERSION.SDK_INT}.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun descendants(v: View): Sequence<View> = sequence { yield(v); if (v is ViewGroup) for (i in 0 until v.childCount) yieldAll(descendants(v.getChildAt(i))) }
}
