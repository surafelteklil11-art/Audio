package com.surafel.audio.checkers

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CheckersModelTest {
    @Test fun boardSizeIsIndependentAndRandomSideIsRetainedWhenResuming() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("checkers", 0).edit().clear().commit()
        val model = CheckersModel(app)
        try {
            model.boardSize = 6; model.playAs = 0; model.rules = Rules.presets.first(); model.saveOptions()
            model.start(PlayMode.TWO_PLAYERS)
            assertEquals(6, model.match!!.rules.size); assertEquals("International", model.match!!.rules.name)
            assertTrue(model.human in listOf(-1, 1)); val side = model.human
            assertTrue(model.hasUnfinishedSaved)
            model.home(); assertTrue(model.resumeSaved()); assertEquals(side, model.human); assertEquals(0, model.playAs)
            model.match!!.history.add(Position(List(36) { 0 }, turn = 1))
            model.home(); assertFalse(model.hasUnfinishedSaved)
        } finally { model.home(); model.detach() }
    }
    @Test fun dailyContextIsAvailableOnTheFirstGameRenderAndSurvivesResume() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("checkers", 0).edit().clear().commit()
        val model = CheckersModel(app)
        try {
            val days = mutableListOf<String?>()
            model.attach { if (model.match != null) days.add(model.tournamentDay) }
            assertTrue(model.startDaily()); assertTrue(days.isNotEmpty())
            assertTrue(days.all { it == model.progress.today() })
            model.home(); assertTrue(model.resumeSaved())
            assertEquals(model.progress.today(), model.tournamentDay)
            assertEquals("Your turn", model.turnLabel)
        } finally { model.home(); model.detach() }
    }
    @Test fun twoAppModelsExchangeTurnsAndFreezeAfterDisconnect() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("checkers", 0).edit().clear().commit()
        val host = CheckersModel(app); val guest = CheckersModel(app)
        try {
            host.attach {}; guest.attach {}
            val address = LanAddress.localAddresses().firstOrNull()
            assertNotNull("The test runner needs a private IPv4 interface for a real LAN test", address)
            host.host(address!!)
            until { host.roomCode.isNotEmpty() }
            assertTrue(guest.join(host.roomCode))
            until { host.connected && guest.connected }
            assertTrue(host.canMove); assertFalse(guest.canMove)
            assertEquals("Your turn", host.turnLabel); assertEquals("Alpha", guest.turnLabel)
            repeat(12) {
                val mover = if (host.match!!.position.turn == 1) host else guest
                val move = CheckersEngine.legal(mover.match!!.position, mover.match!!.rules).first()
                val nextPly = host.match!!.position.ply + 1
                mover.play(move)
                if (mover === guest) { assertTrue(guest.waiting); guest.play(move) }
                until { host.match!!.position.ply == nextPly && guest.match!!.position.ply == nextPly }
                assertEquals(host.match!!.position, guest.match!!.position)
                assertEquals(host.match!!.rules, guest.match!!.rules)
                assertEquals(if (host.match!!.position.turn == 1) "Your turn" else "Alpha", host.turnLabel)
                assertEquals(if (guest.match!!.position.turn == -1) "Your turn" else "Alpha", guest.turnLabel)
            }
            val before = guest.match!!.position
            guest.undo(); assertEquals(before, guest.match!!.position)
            host.home(); until { !guest.connected }
            assertFalse(guest.canMove); assertTrue(guest.notice.contains("Connection lost"))
        } finally { host.home(); guest.home(); host.detach(); guest.detach() }
    }
    private fun until(condition: () -> Boolean) {
        val end = System.nanoTime() + 8_000_000_000L
        while (!condition() && System.nanoTime() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        shadowOf(Looper.getMainLooper()).idle(); assertTrue("Timed out waiting for peer state", condition())
    }
}
