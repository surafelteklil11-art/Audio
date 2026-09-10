package com.surafel.audio.piano

import org.junit.Assert.*
import org.junit.Test

class PianoEngineTest {
    private val normal = PianoDifficulty.NORMAL
    private fun game(vararg notes: PianoNote) = PianoEngine(notes.toList(), normal).apply { start(); advance(normal.travelMs) }
    private fun tap(id: Int, lane: Int, time: Double = 0.0) = PianoNote(id, lane, 72, time)
    @Test fun perfectAndGreatUseSongTimeAndScoreOnlyOncePerPointer() {
        val e = game(tap(0, 0), tap(1, 1, 500.0), tap(2, 2, 1000.0))
        assertNotNull(e.down(0, 8)); assertEquals(100, e.score)
        e.advance(620.0); assertNull(e.down(1, 8)); assertEquals(100, e.score)
        e.up(8); assertNotNull(e.down(1, 8)); assertEquals(170, e.score)
        assertEquals(1, e.perfect); assertEquals(1, e.great)
    }
    @Test fun simultaneousNotesAcceptIndependentNonSequentialPointerIds() {
        val e = game(tap(0, 0), tap(1, 3))
        e.down(0, 17); e.down(3, 4)
        assertEquals(PianoPhase.COMPLETE, e.phase); assertEquals(200, e.score); assertEquals(3, e.stars)
    }
    @Test fun holdingNeedsTheSameFingerUntilItsTailAndScoresOnce() {
        val e = game(PianoNote(0, 1, 72, 0.0, 700.0), tap(1, 3, 1000.0))
        e.down(1, 14); e.advance(600.0); e.up(9)
        assertEquals(NoteState.HOLDING, e.progress[0].state); assertEquals(0, e.score)
        e.advance(100.0); e.up(14)
        assertEquals(150, e.score); assertEquals(3, e.lives)
    }
    @Test fun releasingLongTileEarlyCostsOneLife() {
        val e = game(PianoNote(0, 1, 72, 0.0, 700.0), tap(1, 3, 1000.0))
        e.down(1, 14); e.advance(300.0); e.up(14); e.up(14); e.advance(400.0)
        assertEquals(2, e.lives); assertEquals(1, e.misses); assertEquals(0, e.score)
    }
    @Test fun finalHoldReleaseWithinToleranceCompletesTheSong() {
        val e = game(PianoNote(0, 1, 72, 0.0, 700.0))
        e.down(1, 14); e.advance(640.0); e.up(14)
        assertEquals(PianoPhase.COMPLETE, e.phase); assertEquals(150, e.score)
    }
    @Test fun wrongSpaceAndMissedNotesFailAfterExactlyThreeMistakes() {
        val e = game(tap(0, 0), tap(1, 1, 500.0), tap(2, 2, 1000.0), tap(3, 3, 1500.0))
        e.down(0, 2, false); e.up(2); e.advance(700.0)
        assertEquals(PianoPhase.FAILED, e.phase); assertEquals(0, e.lives); assertEquals(3, e.misses)
        e.advance(10000.0); e.down(3, 7); assertEquals(3, e.misses)
    }
    @Test fun countdownIgnoresTapsAndPauseFreezesTheSong() {
        val e = PianoEngine(listOf(tap(0, 0)), normal)
        e.start(); e.down(3, 2); e.up(2); assertEquals(3, e.lives)
        e.advance(1000.0); val at = e.elapsedMs; e.pause(); e.advance(300000.0)
        assertEquals(at, e.elapsedMs, 0.0); e.resume(); e.advance(900.0)
        assertEquals(at, e.elapsedMs, 0.0); e.advance(200.0); assertEquals(at + 100, e.elapsedMs, .001)
    }
    @Test fun pausedHoldCanBeRegrippedByNewPointerWithoutFreePoints() {
        val e = game(PianoNote(0, 2, 72, 0.0, 900.0))
        e.down(2, 2); e.advance(250.0); e.pause(); e.resume()
        assertNotNull(e.down(2, 19)); e.advance(1000.0); assertEquals(0, e.score)
        e.advance(650.0); assertEquals(PianoPhase.COMPLETE, e.phase); assertEquals(150, e.score)
    }
    @Test fun notRegrippingPausedHoldIsOneMissAndCompletesFinalNote() {
        val e = game(PianoNote(0, 2, 72, 0.0, 900.0))
        e.down(2, 2); e.advance(250.0); e.pause(); e.resume(); e.advance(1000.0)
        assertEquals(1, e.misses); assertEquals(PianoPhase.COMPLETE, e.phase)
    }
    @Test fun comboMultiplierHasABoundedRewardAndWrongTapResetsIt() {
        val e = PianoEngine((0..40).map { tap(it, it % 4, it * 500.0) }, normal).apply { start(); advance(normal.travelMs) }
        repeat(40) { i -> if (i > 0) e.advance(500.0); e.down(i % 4, 3); e.up(3) }
        assertEquals(4, e.multiplier); assertEquals(10000, e.score)
        e.down(3, 9, false); assertEquals(1, e.multiplier); assertEquals(0, e.combo); assertEquals(40, e.bestCombo)
    }
    @Test fun everyArrangementAndDifficultyCanBePerfectlyCompletedIncludingChordsAndHolds() {
        for (song in PianoSongs.all) for (d in PianoDifficulty.entries) {
            val notes = song.chart(d); val e = PianoEngine(notes, d).apply { start() }
            val times = notes.flatMap { listOf(it.atMs, it.endMs) }.distinct().sorted()
            for (time in times) {
                e.advance(time - e.elapsedMs)
                notes.filter { it.atMs == time }.forEach { e.down(it.lane, it.id); if (it.holdMs == 0.0) e.up(it.id) }
                notes.filter { it.holdMs > 0 && it.endMs == time }.forEach { e.up(it.id) }
            }
            assertEquals("${song.id} $d", PianoPhase.COMPLETE, e.phase)
            assertEquals(notes.size, e.perfect); assertEquals(0, e.misses); assertEquals(3, e.stars)
        }
    }
    @Test fun framePartitionDoesNotChangeHoldResultOrSongClock() {
        val notes = arrayOf(PianoNote(0, 0, 72, 0.0, 1000.0), tap(1, 2, 2000.0))
        val a = game(*notes); val b = game(*notes); a.down(0, 1); b.down(0, 1)
        repeat(100) { a.advance(10.0) }; b.advance(1000.0)
        assertEquals(a.score, b.score); assertEquals(a.elapsedMs, b.elapsedMs, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun overlappingLaneNotesAreRejected() {
        PianoEngine(listOf(PianoNote(0, 0, 72, 0.0, 1000.0), tap(1, 0, 500.0)), normal)
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteClockIsRejected() { game(tap(0, 0)).advance(Double.NaN) }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteChartIsRejected() { game(tap(0, 0, Double.POSITIVE_INFINITY)) }
}
