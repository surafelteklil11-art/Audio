package com.surafel.audio.checkers

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CheckersProgressTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("progress-test", 0)
    private val progress get() = CheckersProgress(prefs)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun winsAwardOneThroughFiveStarsExactlyOnceAndSurviveStatsReset() {
        Difficulty.entries.forEachIndexed { i, level ->
            repeat(2) { progress.record("match-$i", "solo_${level.name}_win", level, true) }
            assertEquals(1, progress.count("solo_${level.name}_win"))
            assertEquals((i + 1) * (i + 2) / 2, progress.stars)
        }
        progress.record("loss", "solo_EXPERT_loss", Difficulty.EXPERT, false)
        progress.record("draw", "solo_EXPERT_draw", Difficulty.EXPERT, false)
        assertEquals(15, progress.stars)
        progress.reset("solo")
        assertEquals(0, progress.count("solo_EXPERT_loss")); assertEquals(15, progress.stars)
        progress.record("match-4", "solo_EXPERT_win", Difficulty.EXPERT, true)
        assertEquals(15, progress.stars)
    }
    @Test fun dailyEntryIsOncePerDayDrawReplaysAndFiveWinsCompleteTournament() {
        val day = progress.startDaily().day
        progress.startDaily(); assertEquals(1, progress.count("tournament_played"))
        progress.record("draw", "solo_BEGINNER_draw", Difficulty.BEGINNER, false, day)
        assertEquals(0, progress.daily()!!.round)
        Difficulty.entries.forEachIndexed { i, level ->
            progress.record("round-$i", "solo_${level.name}_win", level, true, day)
            progress.record("round-$i", "solo_${level.name}_win", level, true, day)
        }
        assertEquals("won", progress.daily()!!.status); assertEquals(15, progress.stars)
        assertEquals(1, progress.count("tournament_win")); assertEquals(1, progress.count("tournament_played"))
        assertEquals("won", progress.startDaily().status)
        progress.reset("tournament"); assertEquals(0, progress.count("tournament_win"))
        assertEquals("won", progress.startDaily().status); assertEquals(15, progress.stars)
    }
    @Test fun FinalLossEarnsSecondPlaceAndTomorrowHasNewEntry() {
        val day = progress.startDaily().day
        Difficulty.entries.take(4).forEachIndexed { i, level -> progress.record("r$i", "solo_${level.name}_win", level, true, day) }
        progress.record("final", "solo_EXPERT_loss", Difficulty.EXPERT, false, day)
        assertEquals("eliminated", progress.daily()!!.status); assertEquals(1, progress.count("tournament_second"))
        assertEquals(10, progress.stars)
        prefs.edit().putString("daily", "{\"day\":\"2000-01-01\",\"round\":4,\"status\":\"eliminated\"}").commit()
        assertEquals(0, progress.startDaily().round); assertEquals(2, progress.count("tournament_played"))
    }
}
