package com.surafel.audio.piano

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

/** One monotonic clock for drawing and scoring, with independent Android pointer IDs. */
class PianoBoardView(context: Context, val engine: PianoEngine,
    private val sound: (PianoNote) -> Unit, private val stopSound: (Int) -> Unit,
    private val changed: () -> Unit) : View(context), Choreographer.FrameCallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.rgb(99, 240, 255)
    private val purple = Color.rgb(179, 129, 255)
    private var lastNanos = 0L
    private var scheduled = false
    private val fingers = mutableMapOf<Int, Int>()
    private val soundingHolds = mutableSetOf<Int>()
    private val clock = Choreographer.getInstance()
    val boardTop get() = dp(112f)
    val targetY get() = boardTop + (height - boardTop) * .78f
    private val pixelsPerMs get() = (targetY - boardTop) / engine.difficulty.travelMs
    init { isFocusable = true; contentDescription = "Four piano lanes. Tap tiles at the glowing line. Keep long tiles pressed. Keys 1 to 4 also play."; setLayerType(LAYER_TYPE_SOFTWARE, null) }
    private fun dp(n: Float) = n * resources.displayMetrics.density
    fun tileRect(note: PianoNote): RectF {
        val laneWidth = width / 4f
        val head = targetY - ((note.atMs - engine.elapsedMs) * pixelsPerMs).toFloat()
        val tileHeight = max(dp(90f), (targetY - boardTop) * .21f)
        val top = head - if (note.holdMs > 0) (note.holdMs * pixelsPerMs).toFloat() else tileHeight
        return RectF(note.lane * laneWidth + dp(5f), top, (note.lane + 1) * laneWidth - dp(5f),
            if (engine.progress[note.id].state == NoteState.HOLDING) targetY + dp(18f) else head)
    }
    fun wake() { lastNanos = 0L; invalidate(); schedule() }
    private fun schedule() {
        if (!scheduled && isAttachedToWindow && engine.phase == PianoPhase.RUNNING) { scheduled = true; clock.postFrameCallback(this) }
    }
    fun pause() {
        engine.pause(); clock.removeFrameCallback(this); scheduled = false; lastNanos = 0L
        fingers.clear(); soundingHolds.forEach(stopSound); soundingHolds.clear(); invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (engine.phase != PianoPhase.RUNNING) return
        if (lastNanos != 0L) {
            val delta = (frameTimeNanos - lastNanos) / 1_000_000.0
            if (delta > 250) { pause(); changed(); return }
            engine.advance(delta.coerceAtLeast(0.0))
        }
        lastNanos = frameTimeNanos
        afterInput(); schedule()
    }
    private fun afterInput() {
        soundingHolds.filter { engine.progress[it].state != NoteState.HOLDING }.forEach { stopSound(it); soundingHolds.remove(it) }
        invalidate(); changed()
    }
    private fun press(lane: Int, pointer: Int, onTile: Boolean) {
        fingers[pointer] = lane
        engine.down(lane, pointer, onTile)?.let { note -> sound(note); if (note.holdMs > 0) soundingHolds.add(note.id) }
        afterInput()
    }
    private fun release(pointer: Int) { engine.up(pointer); fingers.remove(pointer); afterInput() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (engine.phase != PianoPhase.RUNNING) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex; val x = event.getX(index); val y = event.getY(index)
                val lane = (x / (width / 4f)).toInt().coerceIn(0, 3)
                val candidate = engine.candidate(lane)
                val hit = candidate != null && y >= boardTop && tileRect(candidate).contains(x, y)
                press(lane, event.getPointerId(index), hit)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { release(event.getPointerId(event.actionIndex)); if (event.actionMasked == MotionEvent.ACTION_UP) performClick() }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index); val lane = fingers[id] ?: continue
                    val x = event.getX(index)
                    if (x < lane * width / 4f || x >= (lane + 1) * width / 4f) release(id)
                }
            }
            MotionEvent.ACTION_CANCEL -> { pause(); changed() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun keyLane(code: Int) = when(code) { KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_D -> 0; KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_F -> 1; KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_J -> 2; KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_K -> 3; else -> -1 }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val lane = keyLane(keyCode)
        if (lane < 0 || engine.phase != PianoPhase.RUNNING) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) press(lane, 1000 + keyCode, true)
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyLane(keyCode) < 0) return super.onKeyUp(keyCode, event)
        release(1000 + keyCode); return true
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), intArrayOf(0xff102b61.toInt(), 0xff171440.toInt(), 0xff35216d.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
        text(canvas, "${engine.score}", width / 2f, dp(42f), 34f, Color.WHITE, true)
        text(canvas, "COMBO ${engine.combo}  ·  ×${engine.multiplier}", width / 2f, dp(66f), 13f, cyan)
        text(canvas, "${"♥".repeat(engine.lives)}${"♡".repeat(3 - engine.lives)}", width / 2f, dp(90f), 19f, 0xffffaad0.toInt())
        paint.color = 0xff424768.toInt(); canvas.drawRect(0f, boardTop - dp(5f), width.toFloat(), boardTop, paint)
        paint.color = cyan; canvas.drawRect(0f, boardTop - dp(5f), width * engine.completion.toFloat(), boardTop, paint)
        canvas.save(); canvas.clipRect(0f, boardTop, width.toFloat(), height.toFloat())
        for (lane in 0..3) {
            paint.color = if (lane % 2 == 0) 0x154a94ef else 0x107166df
            canvas.drawRect(lane * width / 4f, boardTop, (lane + 1) * width / 4f, height.toFloat(), paint)
            paint.color = 0x405bd9ec; paint.strokeWidth = dp(1f)
            canvas.drawLine(lane * width / 4f, boardTop, lane * width / 4f, height.toFloat(), paint)
        }
        paint.color = 0x2263f0ff; canvas.drawRect(0f, targetY - dp(22f), width.toFloat(), targetY + dp(22f), paint)
        for (note in engine.notes.asReversed()) {
            val state = engine.progress[note.id].state
            if (state == NoteState.HIT || state == NoteState.MISSED) continue
            val rect = tileRect(note)
            if (rect.bottom < boardTop || rect.top > height || rect.top >= rect.bottom) continue
            val holding = state == NoteState.HOLDING
            paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, if (holding) 0xff2cbfff.toInt() else 0xff152144.toInt(), if (holding) 0xff9067f4.toInt() else 0xff080b19.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), paint); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (holding) 2.5f else 1.2f); paint.color = if (holding) cyan else 0xff49608d.toInt()
            canvas.drawRoundRect(rect, dp(8f), dp(8f), paint); paint.style = Paint.Style.FILL
            if (note.holdMs > 0) {
                paint.color = if (holding) Color.WHITE else purple; paint.strokeWidth = dp(4f)
                canvas.drawLine(rect.centerX(), max(rect.top + dp(18f), boardTop), rect.centerX(), rect.bottom - dp(30f), paint)
            }
            paint.color = if (holding) Color.WHITE else cyan
            canvas.drawCircle(rect.centerX(), rect.bottom - dp(18f), dp(if (note.holdMs > 0) 7f else 4f), paint)
        }
        paint.color = cyan; paint.strokeWidth = dp(2f); canvas.drawLine(0f, targetY, width.toFloat(), targetY, paint)
        for (lane in 0..3) text(canvas, "${lane + 1}", (lane + .5f) * width / 4f, height - dp(18f), 13f, 0xff8b9fd0.toInt())
        if (engine.phase == PianoPhase.RUNNING) {
            when {
                engine.resumeDelayMs > 0 -> text(canvas, "HOLD TO RESUME", width / 2f, boardTop + dp(45f), 19f, cyan, true)
                engine.elapsedMs < -engine.difficulty.windowMs -> text(canvas, "${ceil(-engine.elapsedMs / 1000).toInt()}", width / 2f, boardTop + dp(64f), 48f, Color.WHITE, true)
                engine.elapsedMs - engine.feedbackAtMs < 550 -> text(canvas, engine.feedback, width / 2f, boardTop + dp(48f), 25f, if (engine.feedback == "PERFECT") cyan else 0xffffcdec.toInt(), true)
            }
        }
        canvas.restore()
    }
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        paint.color = color; paint.textSize = dp(size); paint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER; canvas.drawText(value, x, y, paint)
    }
}
