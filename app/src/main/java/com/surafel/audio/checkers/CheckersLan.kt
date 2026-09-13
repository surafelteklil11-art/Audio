package com.surafel.audio.checkers

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Local IPv4 room codes never resolve hostnames or point at a public Internet server. */
data class LanAddress(val host: String, val port: Int, val token: String) {
    override fun toString() = "$host:$port/$token"
    companion object {
        fun localAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress || it.isLinkLocalAddress }.mapNotNull { it.hostAddress }.distinct()
        } catch (_: Exception) { emptyList() }
        fun parse(value: String): LanAddress {
            val m = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{1,5})/(\\d{6})$").matchEntire(value.trim())
                ?: throw IllegalArgumentException("Enter the full room code: IP:port/6 digits")
            val octets = m.groupValues[1].split('.').map { it.toInt() }; require(octets.all { it in 0..255 })
            val ip = InetAddress.getByAddress(octets.map { it.toByte() }.toByteArray())
            require(ip.isSiteLocalAddress || ip.isLinkLocalAddress) { "Use a local Wi-Fi or hotspot room code" }
            val port = m.groupValues[2].toInt(); require(port in 1024..65535)
            return LanAddress(ip.hostAddress!!, port, m.groupValues[3])
        }
    }
}

/** Bounded, versioned, heartbeat-protected TCP transport for one nearby peer. */
class CheckersLan(private val event: (String, JSONObject?) -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "checkers-lan-reader").apply { isDaemon = true } }
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
        { task -> Thread(task, "checkers-lan-writer").apply { isDaemon = true } })
    private val heartbeat = Executors.newSingleThreadScheduledExecutor { Thread(it, "checkers-heartbeat").apply { isDaemon = true } }
    @Volatile private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    val isConnected get() = connected.get() && !closed.get()

    fun host(address: String) {
        worker.execute {
            try {
                val secret = (SecureRandom().nextInt(900000) + 100000).toString()
                val listener = ServerSocket(); server = listener
                if (closed.get()) { listener.close(); return@execute }
                listener.reuseAddress = true; listener.bind(InetSocketAddress(InetAddress.getByName(address), 0))
                event("hosting", JSONObject().put("code", LanAddress(address, listener.localPort, secret).toString()))
                while (!closed.get()) {
                    val candidate = listener.accept(); socket = candidate
                    candidate.soTimeout = 5000; candidate.tcpNoDelay = true
                    try {
                        val input = DataInputStream(candidate.getInputStream())
                        val hello = readFrame(input)
                        if (hello.optString("type") != "hello" || hello.optInt("version") != VERSION ||
                            !MessageDigest.isEqual(hello.optString("token").toByteArray(), secret.toByteArray())) {
                            candidate.close(); socket = null; continue
                        }
                        listener.close(); server = null
                        attach(candidate)
                        event("connected", null)
                        readLoop(input)
                        break
                    } catch (e: Exception) {
                        candidate.close()
                        if (connected.get()) throw e
                        socket = null // A bad invitation does not consume the room.
                    }
                }
            } catch (_: Exception) { lost() }
        }
    }
    fun join(address: LanAddress) {
        worker.execute {
            try {
                val peer = Socket(); socket = peer
                if (closed.get()) { peer.close(); return@execute }
                peer.connect(InetSocketAddress(address.host, address.port), 8000)
                attach(peer)
                send(JSONObject().put("type", "hello").put("version", VERSION).put("token", address.token))
                event("connected", null)
                readLoop(DataInputStream(peer.getInputStream()))
            } catch (_: Exception) { lost() }
        }
    }
    private fun attach(peer: Socket) {
        if (closed.get()) { peer.close(); throw IOException("Closed") }
        peer.soTimeout = 15000; peer.tcpNoDelay = true
        output = DataOutputStream(peer.getOutputStream()); connected.set(true)
        heartbeat.scheduleAtFixedRate({ send(JSONObject().put("type", "ping")) }, 4, 4, TimeUnit.SECONDS)
    }
    private fun readLoop(input: DataInputStream) {
        while (!closed.get()) {
            val message = readFrame(input)
            if (message.optString("type") != "ping") event("message", message)
        }
    }
    fun send(message: JSONObject) {
        if (closed.get()) return
        val data = message.toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_FRAME) { lost(); return }
        try {
            writer.execute {
                try { val stream = output ?: throw IOException("Not connected"); stream.writeInt(data.size); stream.write(data); stream.flush() }
                catch (_: Exception) { lost() }
            }
        } catch (_: RuntimeException) { lost() }
    }
    private fun lost() {
        if (!closed.get()) { close(); event("disconnected", null) }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        connected.set(false)
        try { server?.close() } catch (_: IOException) { }
        try { socket?.close() } catch (_: IOException) { }
        server = null; socket = null; output = null
        worker.shutdownNow(); writer.shutdownNow(); heartbeat.shutdownNow()
    }
    companion object {
        const val VERSION = 1
        const val MAX_FRAME = 16384
        internal fun readFrame(input: DataInputStream): JSONObject {
            val size = input.readInt(); if (size !in 1..MAX_FRAME) throw IOException("Invalid frame length")
            val data = ByteArray(size); input.readFully(data)
            return JSONObject(String(data, Charsets.UTF_8))
        }
    }
}
