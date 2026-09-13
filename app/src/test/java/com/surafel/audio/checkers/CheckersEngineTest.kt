package com.surafel.audio.checkers

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CheckersEngineTest {
    private val brazil = Rules.presets[1]
    private fun position(vararg pieces: Pair<Int, Int>, turn: Int = 1, size: Int = 8, quiet: Int = 0): Position {
        val b = MutableList(size * size) { 0 }; pieces.forEach { b[it.first] = it.second }; return Position(b, turn, quiet)
    }
    @Test fun presetsHaveCorrectPieceCountsAndFirstPlayer() {
        Rules.presets.forEach { r ->
            val p = CheckersEngine.initial(r)
            val expected = if (r.orthogonal) 16 else if (r.size == 10) 20 else 12
            assertEquals(expected, p.board.count { it == 1 }); assertEquals(expected, p.board.count { it == -1 })
            assertEquals(r.first, p.turn); assertTrue(CheckersEngine.legal(p, r).isNotEmpty())
            assertTrue(CheckersEngine.valid(p, r))
        }
        assertEquals(-1, CheckersEngine.initial(Rules.presets.last()).turn)
    }
    @Test fun maximumCaptureRequiresCompleteLongestSequence() {
        val p = position(40 to 1, 33 to -1, 19 to -1, 44 to 1, 37 to -1)
        val moves = CheckersEngine.legal(p, brazil)
        assertEquals(listOf(Move(listOf(40, 26, 12), listOf(33, 19))), moves)
        assertFalse(Move(listOf(40, 26), listOf(33)) in moves)
        val q = CheckersEngine.play(p, brazil, moves.single())
        assertEquals(-1, q.turn); assertEquals(0, q.board[33]); assertEquals(0, q.board[19]); assertEquals(1, q.board[12])
        assertEquals(-1, p.board[33])
    }
    @Test fun anyCaptureAllowsShorterCompleteSequenceAndOptionalAllowsQuietMoves() {
        val p = position(40 to 1, 33 to -1, 19 to -1, 44 to 1, 37 to -1)
        val any = CheckersEngine.legal(p, brazil.copy(capture = Capture.ANY))
        assertTrue(any.any { it.captures.size == 1 }); assertTrue(any.any { it.captures.size == 2 })
        val optional = CheckersEngine.legal(p, brazil.copy(capture = Capture.OPTIONAL))
        assertTrue(optional.any { it.captures.isEmpty() }); assertTrue(optional.any { it.captures.isNotEmpty() })
    }
    @Test fun menCaptureBackwardsOnlyWhenAllowed() {
        val p = position(26 to 1, 35 to -1)
        assertTrue(CheckersEngine.legal(p, brazil).any { it.path == listOf(26, 44) })
        assertFalse(CheckersEngine.legal(p, Rules.presets.last()).any { it.captures.isNotEmpty() })
    }
    @Test fun russianPromotionContinuesAsFlyingKingWhileBrazilianWaits() {
        val p = position(17 to 1, 10 to -1, 21 to -1)
        val russian = Rules.presets[4]
        val m = CheckersEngine.legal(p, russian).first { it.path == listOf(17, 3, 30) }
        assertEquals(2, CheckersEngine.play(p, russian, m).board[30])
        assertEquals(listOf(17, 3), CheckersEngine.legal(p, brazil).single().path)
        assertEquals(2, CheckersEngine.play(p, brazil, CheckersEngine.legal(p, brazil).single()).board[3])
    }
    @Test fun americanCrowningEndsCaptureTurn() {
        val p = position(17 to 1, 10 to -1, 12 to -1)
        val r = Rules.presets.last()
        assertEquals(listOf(Move(listOf(17, 3), listOf(10))), CheckersEngine.legal(p, r))
    }
    @Test fun capturedPiecesBlockFlyingKingsUntilEndOfTurn() {
        val p = position(42 to 2, 35 to -1, 49 to -1)
        val moves = CheckersEngine.legal(p, brazil)
        assertTrue(moves.isNotEmpty()); assertTrue(moves.all { it.captures.size == 1 })
    }
    @Test fun spanishTiesPreferCapturedKings() {
        val p = position(42 to 2, 35 to -2, 33 to -1)
        assertTrue(CheckersEngine.legal(p, Rules.presets[2]).all { it.captures == listOf(35) })
    }
    @Test fun turkishMovesOrthogonallyAndCannotReverseACapture() {
        val r = Rules.presets[3]; val p = position(36 to 2, 35 to -1, 37 to -1)
        val moves = CheckersEngine.legal(p, r)
        assertTrue(moves.all { it.captures.size == 1 })
        val quiet = CheckersEngine.legal(position(36 to 1), r)
        assertEquals(setOf(28, 35, 37), quiet.map { it.path.last() }.toSet())
    }
    @Test fun flyingStopLandsDirectlyBeyondVictim() {
        val p = position(42 to 2, 28 to -1)
        val normal = CheckersEngine.legal(p, brazil)
        val stop = CheckersEngine.legal(p, brazil.copy(kings = Kings.FLYING_STOP))
        assertTrue(normal.size > stop.size); assertEquals(21, stop.single().path.last())
    }
    @Test fun blockedPlayerLosesAndCasualDrawsAreDetected() {
        assertEquals(-1, CheckersEngine.outcome(position(1 to -1), brazil))
        val p = position(42 to 2, 7 to -2, quiet = 80)
        assertEquals(0, CheckersEngine.outcome(p, brazil))
        assertEquals(0, CheckersEngine.outcome(p.copy(quiet = 0), brazil, 3))
    }
    @Test fun illegalMovesAndBadBoardsAreRejected() {
        val p = CheckersEngine.initial(brazil)
        assertThrows(IllegalArgumentException::class.java) { CheckersEngine.play(p, brazil, Move(listOf(40, 1))) }
        assertFalse(CheckersEngine.valid(p.copy(board = listOf(0)), brazil))
        assertFalse(CheckersEngine.valid(p.copy(turn = 0), brazil))
    }
    @Test fun eachAiLevelReturnsLegalTurnWithoutChangingBoard() {
        val p = position(40 to 1, 33 to -1, 19 to -1, 44 to 1, 37 to -1)
        val original = p.board.toList()
        Difficulty.entries.forEach { d -> assertTrue(CheckersAi.choose(p, brazil, d, Random(3)) in CheckersEngine.legal(p, brazil)) }
        assertEquals(original, p.board)
    }
    @Test fun seededGamesPreserveBoardAndPieceInvariantsForEveryPreset() {
        val random = Random(42)
        Rules.presets.forEach { r ->
            var p = CheckersEngine.initial(r)
            for (turn in 0 until 100) {
                val moves = CheckersEngine.legal(p, r); if (moves.isEmpty()) break
                val move = moves.random(random); val count = p.board.count { it != 0 }
                val q = CheckersEngine.play(p, r, move)
                assertTrue(CheckersEngine.valid(q, r)); assertEquals(-p.turn, q.turn)
                assertEquals(count - move.captures.size, q.board.count { it != 0 })
                assertTrue(q.board.all { abs(it) <= 2 }); p = q
            }
        }
    }
}
