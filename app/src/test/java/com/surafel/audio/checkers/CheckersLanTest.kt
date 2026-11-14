package com.surafel.audio.checkers

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CheckersLanTest {
    @Test fun roomCodesRejectPublicHostsAndMalformedEndpoints() {
        assertEquals("192.168.43.1:45000/123456", LanAddress.parse("192.168.43.1:45000/123456").toString())
        for (bad in listOf("8.8.8.8:45000/123456", "example.com:45000/123456", "127.0.0.1:45000/123456", "192.168.1.999:45000/123456", "192.168.1.2:80/123456", "192.168.1.2:45000/1")) {
            assertThrows(IllegalArgumentException::class.java) { LanAddress.parse(bad) }
        }
    }
    @Test fun oversizedFramesAndInvalidPositionsAreRejected() {
        for (length in listOf(-1, 0, CheckersLan.MAX_FRAME + 1)) {
            assertThrows(IOException::class.java) { CheckersLan.readFrame(DataInputStream(ByteArrayInputStream(ByteBuffer.allocate(4).putInt(length).array()))) }
        }
        val r = Rules.presets[1]; val j = CheckersCodec.position(CheckersEngine.initial(r)).put("turn", 5)
        assertThrows(IllegalArgumentException::class.java) { CheckersCodec.position(j, r) }
    }
    @Test fun matchRejectsReplayOutOfOrderAndIllegalMovesWithoutChangingState() {
        val host = CheckersMatch(Rules.presets[1]); val guest = CheckersMatch(host.rules)
        repeat(15) {
            val before = host.position; val move = CheckersEngine.legal(before, host.rules).first()
            val seq = before.ply; val hash = CheckersCodec.digest(before)
            assertTrue(host.play(move, seq, hash)); assertTrue(guest.play(move, seq, hash))
            assertEquals(host.position, guest.position)
            val after = guest.position
            assertFalse(guest.play(move, seq, hash)); assertEquals(after, guest.position)
            assertFalse(guest.play(Move(listOf(-1, 999)))); assertEquals(after, guest.position)
        }
    }
    @Test fun twoRealSocketsAuthenticateExchangeMovesAndReportDisconnect() {
        val ready = CountDownLatch(1); val connected = CountDownLatch(1); val received = CountDownLatch(1); val dropped = CountDownLatch(1)
        val code = AtomicReference<String>(); val packet = AtomicReference<JSONObject>()
        val host = CheckersLan { kind, data ->
            when (kind) { "hosting" -> { code.set(data!!.getString("code")); ready.countDown() }; "connected" -> connected.countDown(); "message" -> { packet.set(data); received.countDown() }; "disconnected" -> dropped.countDown() }
        }
        val guest = CheckersLan { _, _ -> }
        try {
            host.host("127.0.0.1"); assertTrue(ready.await(5, TimeUnit.SECONDS))
            val parts = code.get().split(':', '/')
            guest.join(LanAddress(parts[0], parts[1].toInt(), parts[2])); assertTrue(connected.await(5, TimeUnit.SECONDS))
            val game = CheckersMatch(Rules.presets[1]); val move = CheckersEngine.legal(game.position, game.rules).first()
            guest.send(JSONObject().put("type", "request").put("move", CheckersCodec.move(move)))
            assertTrue(received.await(5, TimeUnit.SECONDS)); assertEquals(move, CheckersCodec.move(packet.get().getJSONObject("move")))
            guest.close(); assertTrue(dropped.await(5, TimeUnit.SECONDS))
        } finally { guest.close(); host.close() }
    }
    @Test fun wrongRoomSecretDoesNotConsumeHostRoom() {
        val ready = CountDownLatch(1); val badDropped = CountDownLatch(1); val accepted = CountDownLatch(1)
        val code = AtomicReference<String>()
        val host = CheckersLan { kind, data -> if (kind == "hosting") { code.set(data!!.getString("code")); ready.countDown() } else if (kind == "connected") accepted.countDown() }
        val bad = CheckersLan { kind, _ -> if (kind == "disconnected") badDropped.countDown() }
        val good = CheckersLan { _, _ -> }
        try {
            host.host("127.0.0.1"); assertTrue(ready.await(5, TimeUnit.SECONDS)); val parts = code.get().split(':', '/')
            bad.join(LanAddress(parts[0], parts[1].toInt(), "000000")); assertTrue(badDropped.await(5, TimeUnit.SECONDS))
            assertEquals(1L, accepted.count)
            good.join(LanAddress(parts[0], parts[1].toInt(), parts[2])); assertTrue(accepted.await(5, TimeUnit.SECONDS))
        } finally { bad.close(); good.close(); host.close() }
    }
}
