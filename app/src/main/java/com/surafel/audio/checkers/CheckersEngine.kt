package com.surafel.audio.checkers

import kotlin.math.abs
import kotlin.random.Random

enum class Kings { FLYING, SHORT, FLYING_STOP }
enum class Capture { MAXIMUM, ANY, OPTIONAL }
enum class Crown { END, CONTINUE, STOP }

data class Rules(
    val name: String = "International", val size: Int = 10, val kings: Kings = Kings.FLYING,
    val capture: Capture = Capture.MAXIMUM, val backwards: Boolean = true,
    val orthogonal: Boolean = false, val crown: Crown = Crown.END,
    val kingPriority: Boolean = false, val first: Int = 1
) {
    init { require(size == 8 || size == 10); require(first == 1 || first == -1); require(!orthogonal || size == 8) }
    companion object {
        val presets = listOf(
            Rules(), Rules("Brazilian", 8),
            Rules("Spanish", 8, backwards = false, crown = Crown.STOP, kingPriority = true),
            Rules("Turkish", 8, backwards = false, orthogonal = true),
            Rules("Russian", 8, capture = Capture.ANY, crown = Crown.CONTINUE),
            Rules("American", 8, Kings.SHORT, Capture.ANY, false, crown = Crown.STOP, first = -1)
        )
    }
}

data class Position(val board: List<Int>, val turn: Int = 1, val quiet: Int = 0, val ply: Int = 0) {
    fun signature() = "$turn:" + board.joinToString(",")
}
data class Move(val path: List<Int>, val captures: List<Int> = emptyList())

object CheckersEngine {
    fun initial(r: Rules): Position {
        val n = r.size
        return Position(List(n * n) { i ->
            val row = i / n; val col = i % n
            if (r.orthogonal) when (row) { 1, 2 -> -1; n - 3, n - 2 -> 1; else -> 0 }
            else if ((row + col) % 2 == 0) 0 else when {
                row < n / 2 - 1 -> -1
                row >= n - (n / 2 - 1) -> 1
                else -> 0
            }
        }, r.first)
    }
    fun valid(p: Position, r: Rules): Boolean = p.board.size == r.size * r.size &&
        p.turn in listOf(-1, 1) && p.quiet in 0..100000 && p.ply in 0..100000 &&
        p.board.withIndex().all { (i, v) -> v in -2..2 && (r.orthogonal || v == 0 || (i / r.size + i % r.size) % 2 == 1) }

    private fun directions(piece: Int, r: Rules, capture: Boolean): List<Pair<Int, Int>> {
        val forward = if (piece > 0) -1 else 1
        return if (r.orthogonal) {
            if (abs(piece) == 2) listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            else listOf(forward to 0, 0 to -1, 0 to 1)
        } else if (abs(piece) == 2 || (capture && r.backwards)) listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        else listOf(forward to -1, forward to 1)
    }
    private fun square(row: Int, col: Int, n: Int) = if (row in 0 until n && col in 0 until n) row * n + col else -1
    private fun promotes(at: Int, piece: Int, n: Int) = at / n == if (piece > 0) 0 else n - 1

    fun legal(p: Position, r: Rules): List<Move> {
        require(valid(p, r))
        val captures = mutableListOf<Move>()
        val n = r.size
        fun jump(board: MutableList<Int>, at: Int, piece: Int, path: List<Int>, taken: List<Int>, last: Pair<Int, Int>?) {
            var extended = false
            for (dir in directions(piece, r, true)) {
                if (r.orthogonal && last != null && dir.first == -last.first && dir.second == -last.second) continue
                val flying = abs(piece) == 2 && r.kings != Kings.SHORT
                var row = at / n + dir.first; var col = at % n + dir.second
                var target = square(row, col, n)
                if (flying) while (target >= 0 && board[target] == 0) {
                    row += dir.first; col += dir.second; target = square(row, col, n)
                }
                if (target < 0 || board[target] * piece >= 0 || target in taken) continue
                row += dir.first; col += dir.second
                var landing = square(row, col, n)
                while (landing >= 0 && board[landing] == 0) {
                    extended = true
                    val nextBoard = board.toMutableList(); nextBoard[at] = 0
                    if (r.orthogonal) nextBoard[target] = 0 // Turkish captures disappear immediately.
                    val reached = abs(piece) == 1 && promotes(landing, piece, n)
                    val nextPiece = if (reached && r.crown == Crown.CONTINUE) piece * 2 else piece
                    nextBoard[landing] = nextPiece
                    val nextPath = path + landing; val nextTaken = taken + target
                    if (reached && r.crown == Crown.STOP) captures.add(Move(nextPath, nextTaken))
                    else jump(nextBoard, landing, nextPiece, nextPath, nextTaken, dir)
                    if (!flying || r.kings == Kings.FLYING_STOP) break
                    row += dir.first; col += dir.second; landing = square(row, col, n)
                }
            }
            if (!extended && taken.isNotEmpty()) captures.add(Move(path, taken))
        }
        for (i in p.board.indices) if (p.board[i] * p.turn > 0) jump(p.board.toMutableList(), i, p.board[i], listOf(i), emptyList(), null)
        if (captures.isNotEmpty() && r.capture != Capture.OPTIONAL) {
            if (r.capture == Capture.ANY) return captures.distinct()
            val longest = captures.maxOf { it.captures.size }
            val best = captures.filter { it.captures.size == longest }
            val kings = best.maxOf { move -> move.captures.count { abs(p.board[it]) == 2 } }
            return (if (r.kingPriority) best.filter { move -> move.captures.count { abs(p.board[it]) == 2 } == kings } else best).distinct()
        }
        val moves = captures.toMutableList()
        for (i in p.board.indices) {
            val piece = p.board[i]
            if (piece * p.turn <= 0) continue
            for ((dr, dc) in directions(piece, r, false)) {
                var row = i / n + dr; var col = i % n + dc
                var to = square(row, col, n)
                while (to >= 0 && p.board[to] == 0) {
                    moves.add(Move(listOf(i, to)))
                    if (abs(piece) == 1 || r.kings == Kings.SHORT) break
                    row += dr; col += dc; to = square(row, col, n)
                }
            }
        }
        return moves.distinct()
    }
    fun play(p: Position, r: Rules, move: Move): Position {
        require(move in legal(p, r)) { "Illegal move" }
        return apply(p, r, move)
    }
    internal fun apply(p: Position, r: Rules, move: Move): Position {
        val board = p.board.toMutableList(); var piece = board[move.path.first()]
        board[move.path.first()] = 0
        move.captures.forEach { board[it] = 0 }
        if (abs(piece) == 1 && (promotes(move.path.last(), piece, r.size) ||
                r.crown == Crown.CONTINUE && move.path.drop(1).any { promotes(it, piece, r.size) })) piece *= 2
        board[move.path.last()] = piece
        return Position(board, -p.turn, if (move.captures.isNotEmpty() || abs(p.board[move.path.first()]) == 1) 0 else p.quiet + 1, p.ply + 1)
    }
    /** Casual-match draw policy shared by all presets; competition-specific draw adjudication is excluded. */
    fun outcome(p: Position, r: Rules, repetitions: Int = 1): Int? = when {
        legal(p, r).isEmpty() -> -p.turn
        p.quiet >= 80 || repetitions >= 3 -> 0
        else -> null
    }
}

enum class Difficulty(val label: String, val depth: Int, val budgetMillis: Long) {
    BEGINNER("Beginner", 1, 20), EASY("Easy", 2, 70), MEDIUM("Medium", 4, 200), HARD("Hard", 6, 550), EXPERT("Expert", 9, 1200)
}

object CheckersAi {
    private class TimeUp : RuntimeException()
    fun choose(p: Position, r: Rules, difficulty: Difficulty, random: Random = Random.Default): Move? {
        val root = CheckersEngine.legal(p, r)
        if (root.isEmpty()) return null
        if (difficulty == Difficulty.BEGINNER) return root.random(random)
        val deadline = System.nanoTime() + difficulty.budgetMillis * 1_000_000
        fun checkTime() { if (Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) throw TimeUp() }
        fun evaluate(p: Position): Int = p.board.withIndex().sumOf { (i, piece) ->
            if (piece == 0) 0 else {
                val advancement = if (piece > 0) r.size - 1 - i / r.size else i / r.size
                val center = r.size - abs(2 * (i % r.size) - (r.size - 1))
                (if (piece > 0) 1 else -1) * (if (abs(piece) == 2) 310 + center else 100 + advancement * 4 + center)
            }
        } * p.turn
        fun search(p: Position, depth: Int, alphaStart: Int, beta: Int): Int {
            checkTime()
            val moves = CheckersEngine.legal(p, r)
            if (moves.isEmpty()) return -100000 - depth
            if (p.quiet >= 80) return 0
            if (depth == 0) return evaluate(p)
            var alpha = alphaStart
            for (m in moves.sortedByDescending { it.captures.size }) {
                val score = -search(CheckersEngine.apply(p, r, m), depth - 1, -beta, -alpha)
                if (score >= beta) return score
                alpha = maxOf(alpha, score)
            }
            return alpha
        }
        var best = root.first()
        for (depth in 1..difficulty.depth) {
            try {
                var candidate = best; var score = Int.MIN_VALUE
                for (m in root.sortedByDescending { if (it == best) 100 else it.captures.size }) {
                    checkTime()
                    val value = -search(CheckersEngine.apply(p, r, m), depth - 1, -200000, 200000)
                    if (value > score) { score = value; candidate = m }
                }
                best = candidate
            } catch (_: TimeUp) { break }
        }
        return best
    }
}
