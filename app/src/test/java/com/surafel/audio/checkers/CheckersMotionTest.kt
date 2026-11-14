package com.surafel.audio.checkers

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CheckersMotionTest {
    @Test fun victimStaysVisibleDuringJumpAndFadesAfterLanding() {
        val rules = Rules.presets[1]
        val before = Position(List(64) { when (it) { 42 -> 1; 35, 21 -> -1; else -> 0 } })
        val move = CheckersEngine.legal(before, rules).first { it.path == listOf(42, 28, 14) }
        val sequence = CheckersMotion(before, rules, move)
        val first = sequence.steps.first()
        val jumping = first.frame(.3f)
        assertTrue(jumping.progress > 0f && jumping.progress < 1f)
        assertTrue(jumping.lift > 0f)
        assertEquals(255, jumping.capturedAlpha)
        assertEquals(0, jumping.board[42])
        assertEquals(0, jumping.board[35]) // Victim is drawn separately with its own opacity.
        assertEquals(-1, jumping.capturedPiece)
        val landed = first.frame(.75f)
        assertEquals(1f, landed.progress, 0f)
        assertTrue(landed.capturedAlpha in 1..254)
        assertEquals(0, first.frame(1f).capturedAlpha)
        assertEquals(1, first.after[28])
        assertEquals(-1, first.after[21]) // Next victim stays until the next jump.
        assertEquals(first.after, sequence.steps[1].before)
        assertEquals(CheckersEngine.play(before, rules, move).board, sequence.steps.last().after)
        assertEquals(1, before.board[42])
    }

    @Test fun everyReplayEndsAtTheEnginePositionAcrossAllPresets() {
        val random = Random(418)
        Rules.presets.forEach { rules ->
            var position = CheckersEngine.initial(rules)
            repeat(100) {
                val moves = CheckersEngine.legal(position, rules)
                if (moves.isNotEmpty()) {
                    val move = moves.random(random)
                    val after = CheckersEngine.play(position, rules, move)
                    val steps = CheckersMotion(position, rules, move).steps
                    assertEquals("${rules.name}: $move", after.board, steps.last().after)
                    steps.zipWithNext().forEach { (a, b) -> assertEquals(a.after, b.before) }
                    position = after
                }
            }
        }
    }
}
