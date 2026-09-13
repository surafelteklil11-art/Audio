package com.surafel.audio.checkers

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** A visual replay only. Rules, saves and network messages still use complete legal moves. */
internal class CheckersMotion(before: Position, rules: Rules, move: Move) {
    data class Frame(
        val board: List<Int>, val from: Int, val to: Int, val piece: Int,
        val progress: Float, val lift: Float, val captured: Int?, val capturedPiece: Int,
        val capturedAlpha: Int
    )

    class Step(val before: List<Int>, val after: List<Int>, val from: Int, val to: Int,
               val captured: Int?, size: Int) {
        private val travelMillis = (260L + 35L * max(abs(to / size - from / size), abs(to % size - from % size))).coerceAtMost(540L)
        val durationMillis = travelMillis + if (captured != null) 220L else 90L

        fun frame(fraction: Float): Frame {
            val time = fraction.coerceIn(0f, 1f) * durationMillis
            val t = (time / travelMillis).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val pieces = before.toMutableList().apply { this[from] = 0; captured?.let { this[it] = 0 } }
            val fade = if (captured == null) 0f else ((time - travelMillis) / 140f).coerceIn(0f, 1f)
            return Frame(pieces, from, to, if (t == 1f) after[to] else before[from], eased,
                if (captured == null) 0f else sin(Math.PI * eased).toFloat() * .16f,
                captured, captured?.let { before[it] } ?: 0, ((1f - fade) * 255).toInt())
        }
    }

    val steps: List<Step>
    init {
        val board = before.board.toMutableList()
        steps = move.path.zipWithNext().mapIndexed { index, (from, to) ->
            val start = board.toList()
            var piece = board[from]
            board[from] = 0
            val captured = move.captures.getOrNull(index)
            captured?.let { board[it] = 0 }
            val crownRow = if (piece > 0) 0 else rules.size - 1
            if (abs(piece) == 1 && to / rules.size == crownRow &&
                (rules.crown == Crown.CONTINUE || index == move.path.size - 2)) piece *= 2
            board[to] = piece
            Step(start, board.toList(), from, to, captured, rules.size)
        }
    }
}
