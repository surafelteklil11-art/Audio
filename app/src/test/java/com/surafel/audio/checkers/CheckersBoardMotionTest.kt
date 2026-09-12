package com.surafel.audio.checkers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class CheckersBoardMotionTest {
    @Before fun useControlledFrames() {
        // PAUSED Looper alone still lets Robolectric auto-advance vsync to the end.
        // Drive a 16 ms display clock to inspect real intermediate animation frames.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }
    @Test fun partialCaptureMovesVisiblyLocksContinuationAndCommitsOnlyAtTheEnd() {
        val rules = Rules.presets[1]
        val before = Position(List(64) { when (it) { 42 -> 1; 35, 21 -> -1; else -> 0 } })
        val board = board(rules, before)
        var submitted: Move? = null
        board.onMove = { submitted = it; board.showPosition(rules, CheckersEngine.play(before, rules, it), it) }
        tap(board, 42); tap(board, 28)
        assertTrue(board.isAnimating)
        tap(board, 14) // An accidental extra tap during travel cannot skip the second jump.
        assertNull(submitted)
        assertEquals(listOf(42, 28), board.selected)
        assertEquals(before, board.position)
        advance(180)
        assertTrue(board.visualFrame!!.progress in .01f.. .99f)
        advance(800)
        assertFalse(board.isAnimating)
        tap(board, 42) // Already-started capture cannot be abandoned by selecting its origin.
        assertEquals(listOf(42, 28), board.selected)
        tap(board, 14)
        assertEquals(listOf(42, 28, 14), submitted!!.path)
        assertEquals(28, board.visualFrame!!.from) // The first hop is not replayed on commit.
        advance(1000)
        assertFalse(board.isAnimating)
        assertEquals(1, board.position.board[14])
        board.stopMotion()
    }

    @Test fun fastReplyWaitsForPreviousAnimationAndUndoCancelsBoth() {
        val rules = Rules.presets[1]
        val before = CheckersEngine.initial(rules)
        val first = CheckersEngine.legal(before, rules).first()
        val after = CheckersEngine.play(before, rules, first)
        val reply = CheckersEngine.legal(after, rules).first()
        val replied = CheckersEngine.play(after, rules, reply)
        val board = board(rules, before)
        board.showPosition(rules, after, first)
        board.showPosition(rules, replied, reply)
        assertEquals(first.path.first(), board.visualFrame!!.from)
        assertEquals(replied, board.position)
        advance(CheckersMotion(before, rules, first).steps.single().durationMillis + 45)
        assertEquals(reply.path.first(), board.visualFrame!!.from)
        board.showPosition(rules, before) // Undo/reset always restores the authoritative board.
        advance(2000)
        assertFalse(board.isAnimating)
        assertEquals(before, board.position)
        assertTrue(board.selected.isEmpty())
    }

    @Test @Config(sdk = [33, 35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderFlippedCaptureFrames() {
        val rules = Rules.presets[1]
        val before = Position(List(64) { when (it) { 42 -> 1; 35 -> -1; else -> 0 } })
        val move = CheckersEngine.legal(before, rules).first()
        val board = board(rules, before).apply { flipped = true }
        board.showPosition(rules, CheckersEngine.play(before, rules, move), move)
        repeat(20) { index ->
            snapshot(board, "capture-frame-%02d".format(index))
            advance(40)
        }
        assertFalse(board.isAnimating)
        board.stopMotion()
    }

    private fun board(rules: Rules, position: Position) = CheckersBoardView(RuntimeEnvironment.getApplication()).apply {
        showPosition(rules, position); legal = CheckersEngine.legal(position, rules)
        measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        layout(0, 0, 720, 720)
    }
    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            remaining -= step
        }
    }
    private fun tap(board: CheckersBoardView, square: Int) {
        val i = if (board.flipped) board.position.board.lastIndex - square else square
        val inset = board.width * .025f; val cell = (board.width - 2 * inset) / board.rules.size
        val x = inset + (i % board.rules.size + .5f) * cell
        val y = inset + (i / board.rules.size + .5f) * cell
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            board.dispatchTouchEvent(event); event.recycle()
        }
    }
    private fun snapshot(board: View, name: String) {
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        board.draw(Canvas(bitmap))
        val file = File("build/checkers-previews/$name-api-${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
