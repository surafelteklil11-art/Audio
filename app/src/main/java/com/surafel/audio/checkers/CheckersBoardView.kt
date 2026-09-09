package com.surafel.audio.checkers

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.abs

class CheckersBoardView(context: Context) : View(context) {
    var rules = Rules.presets[1]
    var position = CheckersEngine.initial(rules)
    var flipped = false
    var design = 0
    var tokenStyle = 0
    var showHints = true
    var inputEnabled = true
    var onMove: ((Move) -> Unit)? = null
    var onStep: (() -> Unit)? = null
    var legal: List<Move> = emptyList()
    var hint: List<Int> = emptyList()
    var last: List<Int> = emptyList()
    var selected: List<Int> = emptyList(); private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cursor = 0
    private var touchSquare = -1
    private val inset get() = width * .025f
    private val cell get() = (width - 2 * inset) / rules.size
    private val accessibility = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int = squareAt(x, y).let { if (it < 0) INVALID_ID else it }
        override fun getVisibleVirtualViews(ids: MutableList<Int>) { ids.addAll(position.board.indices) }
        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            node.contentDescription = description(id)
            node.setBoundsInParent(bounds(id).let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) })
            node.isFocusable = true; node.isClickable = inputEnabled
            node.isSelected = id in selected
            if (inputEnabled) node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }
        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !inputEnabled) return false
            pick(id); return true
        }
    }
    init { isFocusable = true; isClickable = true; ViewCompat.setAccessibilityDelegate(this, accessibility) }
    fun resetSelection() { selected = emptyList(); refresh() }
    fun refresh() { invalidate(); accessibility.invalidateRoot() }
    private fun description(i: Int): String {
        val piece = position.board[i]
        return "${('a'.code + i % rules.size).toChar()}${rules.size - i / rules.size}, " +
            (if (piece == 0) "empty" else (if (piece > 0) "White" else "Black") + if (abs(piece) == 2) " king" else " piece") +
            if (i in nextSquares()) ", available move" else ""
    }
    private fun nextSquares() = if (selected.isEmpty()) legal.map { it.path.first() }.toSet()
        else legal.filter { it.path.take(selected.size) == selected }.mapNotNull { it.path.getOrNull(selected.size) }.toSet()
    private fun pick(i: Int) {
        if (!inputEnabled || i !in position.board.indices) return
        cursor = i
        val next = selected + i
        val candidates = legal.filter { it.path.take(next.size) == next }
        if (selected.isNotEmpty() && candidates.isNotEmpty()) {
            selected = next
            val complete = candidates.firstOrNull { it.path.size == next.size }
            if (complete != null) { selected = emptyList(); onMove?.invoke(complete) } else onStep?.invoke()
        } else selected = if (legal.any { it.path.first() == i }) listOf(i) else emptyList()
        refresh()
    }
    override fun onMeasure(w: Int, h: Int) {
        val side = resolveSize((320 * resources.displayMetrics.density).toInt(), w)
        setMeasuredDimension(side, side)
    }
    private fun displayIndex(i: Int) = if (flipped) rules.size * rules.size - 1 - i else i
    private fun bounds(i: Int): RectF {
        val d = displayIndex(i)
        return RectF(inset + d % rules.size * cell, inset + d / rules.size * cell,
            inset + (d % rules.size + 1) * cell, inset + (d / rules.size + 1) * cell)
    }
    private fun squareAt(x: Float, y: Float): Int {
        if (cell <= 0 || x < inset || y < inset || x >= width - inset || y >= width - inset) return -1
        return displayIndex(((y - inset) / cell).toInt() * rules.size + ((x - inset) / cell).toInt())
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { touchSquare = squareAt(event.x, event.y); touchSquare >= 0 }
        MotionEvent.ACTION_UP -> { val at = squareAt(event.x, event.y); if (at == touchSquare && at >= 0) { pick(at); performClick() }; touchSquare = -1; true }
        MotionEvent.ACTION_CANCEL -> { touchSquare = -1; true }
        else -> true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun dispatchHoverEvent(event: MotionEvent) = accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val visual = displayIndex(cursor)
        val next = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (visual % rules.size > 0) visual - 1 else visual
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (visual % rules.size < rules.size - 1) visual + 1 else visual
            KeyEvent.KEYCODE_DPAD_UP -> (visual - rules.size).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> (visual + rules.size).coerceAtMost(position.board.lastIndex)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { pick(cursor); return true }
            else -> return super.onKeyDown(keyCode, event)
        }
        cursor = displayIndex(next); invalidate(); return true
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val colors = palettes[design.coerceIn(palettes.indices)]
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 0xFFE8C99B.toInt(), 0xFF58301D.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), inset * .7f, inset * .7f, paint); paint.shader = null
        val next = if (inputEnabled && showHints) nextSquares() else emptySet()
        for (i in position.board.indices) {
            val b = bounds(i)
            val dark = (i / rules.size + i % rules.size) % 2 == 1
            paint.color = colors[if (dark) 1 else 0]; c.drawRect(b, paint)
            paint.color = 0x08000000
            for (k in 1..4) c.drawLine(b.left + cell * k / 5, b.top, b.left + cell * k / 5 + 2, b.bottom, paint)
            if (i in last) { paint.color = 0x33FFCF57; c.drawRect(b, paint) }
            if (i in selected || i in hint) { paint.color = 0x885DBD92.toInt(); c.drawRect(b, paint) }
            if (i in next) {
                paint.style = Paint.Style.STROKE; paint.color = 0xFF91E6A7.toInt(); paint.strokeWidth = cell * .05f
                c.drawRect(b.left + 2, b.top + 2, b.right - 2, b.bottom - 2, paint); paint.style = Paint.Style.FILL
            }
            val piece = position.board[i]
            if (piece != 0) drawPiece(c, b.centerX(), b.centerY(), cell * .39f, piece)
            else if (i in next && selected.isNotEmpty()) { paint.color = 0xCC1F6D43.toInt(); c.drawCircle(b.centerX(), b.centerY(), cell * .12f, paint) }
            if (isFocused && i == cursor) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = Color.WHITE
                c.drawRect(b.left + 1, b.top + 1, b.right - 1, b.bottom - 1, paint); paint.style = Paint.Style.FILL
            }
        }
        if (selected.size > 1) {
            paint.color = 0xFFF9DF72.toInt(); paint.strokeWidth = cell * .045f
            selected.zipWithNext().forEach { (a, b) -> val from = bounds(a); val to = bounds(b); c.drawLine(from.centerX(), from.centerY(), to.centerX(), to.centerY(), paint) }
        }
    }
    private fun drawPiece(c: Canvas, x: Float, y: Float, radius: Float, piece: Int) {
        val white = piece > 0
        paint.color = 0x55000000; c.drawOval(x - radius, y - radius + radius * .18f, x + radius, y + radius * 1.18f, paint)
        paint.color = if (white) 0xFFA56328.toInt() else 0xFF131514.toInt(); c.drawCircle(x, y + radius * .11f, radius, paint)
        paint.shader = LinearGradient(x - radius, y - radius, x + radius, y + radius,
            if (white) 0xFFFFE8AC.toInt() else 0xFF77776B.toInt(), if (white) 0xFFD4AB60.toInt() else 0xFF272A29.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(x, y - radius * .06f, radius, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .04f
        paint.color = if (white) 0xFF996027.toInt() else 0xFF121613.toInt()
        val rings = when (tokenStyle) { 1 -> 4; 2 -> 1; 3 -> 0; else -> 2 }
        repeat(rings) { c.drawCircle(x, y - radius * .06f, radius * (.82f - it * .13f), paint) }
        if (tokenStyle == 3) {
            val p = Path(); for (k in 0..8) {
                val angle = Math.PI * k / 4; val px = x + kotlin.math.cos(angle).toFloat() * radius * .75f
                val py = y + kotlin.math.sin(angle).toFloat() * radius * .75f - radius * .06f
                if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }; c.drawPath(p, paint)
        }
        paint.style = Paint.Style.FILL
        if (abs(piece) == 2) {
            paint.color = if (white) 0xFF6E3C17.toInt() else 0xFFFFDB82.toInt()
            paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = radius * 1.1f; paint.textAlign = Paint.Align.CENTER
            c.drawText("K", x, y + radius * .31f, paint)
        }
    }
    companion object {
        val names = listOf("Walnut", "Classic", "Crimson", "Ocean", "Forest", "Sunset", "Sapphire", "Slate")
        val palettes = listOf(
            intArrayOf(0xFFF1DDB4.toInt(), 0xFF8D4D29.toInt()), intArrayOf(0xFFEAE5DC.toInt(), 0xFF383D3D.toInt()),
            intArrayOf(0xFFFFEADA.toInt(), 0xFFA93E38.toInt()), intArrayOf(0xFFD9EAF2.toInt(), 0xFF346876.toInt()),
            intArrayOf(0xFFE2E9CC.toInt(), 0xFF376951.toInt()), intArrayOf(0xFFF5C59E.toInt(), 0xFF775268.toInt()),
            intArrayOf(0xFFE5EAF5.toInt(), 0xFF365CA0.toInt()), intArrayOf(0xFFBAC4CE.toInt(), 0xFF37434E.toInt())
        )
    }
}
