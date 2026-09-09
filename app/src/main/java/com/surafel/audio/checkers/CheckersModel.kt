package com.surafel.audio.checkers

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.Future

enum class PlayMode { SOLO, TWO_PLAYERS, HOST, GUEST }

class CheckersModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("checkers", 0)
    private val main = Handler(Looper.getMainLooper())
    private val thinker = Executors.newSingleThreadExecutor { Thread(it, "checkers-ai").apply { isDaemon = true } }
    private var task: Future<*>? = null
    private var generation = 0
    private var networkGeneration = 0
    private var lan: CheckersLan? = null
    var observer: (() -> Unit)? = null
    var match: CheckersMatch? = null; private set
    var mode = PlayMode.SOLO; private set
    var rules = runCatching { CheckersCodec.rules(JSONObject(prefs.getString("rules", "")!!)) }.getOrDefault(Rules.presets[1])
    var difficulty = runCatching { Difficulty.valueOf(prefs.getString("difficulty", "MEDIUM")!!) }.getOrDefault(Difficulty.MEDIUM)
    var human = prefs.getInt("human", 1).let { if (it == -1) -1 else 1 }
    var design = prefs.getInt("design", 0).coerceIn(0, 7)
    var tokenStyle = prefs.getInt("tokens", 0).coerceIn(0, 3)
    var hints = prefs.getBoolean("hints", true)
    var sound = prefs.getBoolean("sound", true)
    var thinking = false; private set
    var connected = false; private set
    var waiting = false; private set
    var roomCode = ""; private set
    var notice = ""; private set
    var hintPath: List<Int> = emptyList(); private set
    var lastMove: Move? = null; private set
    var revision = 0; private set
    private var recorded = false
    private var started = 0L
    private var elapsedBefore = 0L
    private var finishedAt: Long? = null
    private var pendingMove: Move? = null
    val elapsedSeconds get() = finishedAt ?: (elapsedBefore + if (match != null) (SystemClock.elapsedRealtime() - started) / 1000 else 0)
    val network get() = mode == PlayMode.HOST || mode == PlayMode.GUEST
    val mySide get() = if (mode == PlayMode.GUEST) -1 else if (mode == PlayMode.HOST) 1 else human
    val canMove get() = match?.let { it.result == null && !thinking && !waiting &&
        (!network || connected && it.position.turn == mySide) && (mode != PlayMode.SOLO || it.position.turn == human) } ?: false
    val hasSaved get() = prefs.contains("saved")

    fun saveOptions() {
        prefs.edit().putString("rules", CheckersCodec.rules(rules).toString()).putString("difficulty", difficulty.name)
            .putInt("human", human).putInt("design", design).putInt("tokens", tokenStyle).putBoolean("hints", hints).putBoolean("sound", sound).apply()
        changed()
    }
    fun start(newMode: PlayMode) {
        disconnect(); cancelThought(); mode = newMode
        match = CheckersMatch(rules); recorded = false; finishedAt = null; started = SystemClock.elapsedRealtime(); elapsedBefore = 0
        lastMove = null; hintPath = emptyList(); notice = ""; roomCode = ""; waiting = false
        revision++; persist(); changed(); requestAi()
    }
    fun home() {
        persist(); disconnect(); cancelThought(); match = null; roomCode = ""; notice = ""; revision++; changed()
    }
    fun resumeSaved(): Boolean = try {
        val j = JSONObject(prefs.getString("saved", "")!!)
        val r = CheckersCodec.rules(j.getJSONObject("rules")); val positions = j.getJSONArray("history")
        require(positions.length() in 1..256)
        val savedMode = PlayMode.valueOf(j.getString("mode")); require(savedMode == PlayMode.SOLO || savedMode == PlayMode.TWO_PLAYERS)
        val restored = CheckersMatch(r, MutableList(positions.length()) { CheckersCodec.position(positions.getJSONObject(it), r) })
        disconnect(); cancelThought(); mode = savedMode; match = restored; rules = r
        human = if (j.optInt("human", 1) == -1) -1 else 1
        difficulty = Difficulty.valueOf(j.getString("difficulty")); recorded = j.optBoolean("recorded")
        elapsedBefore = j.optLong("elapsed").coerceIn(0, 10000000); started = SystemClock.elapsedRealtime()
        finishedAt = if (restored.result != null) elapsedBefore else null
        notice = ""; lastMove = null; hintPath = emptyList(); revision++; changed(); requestAi(); true
    } catch (_: Exception) { prefs.edit().remove("saved").apply(); notice = "Saved game could not be restored. Start a new game."; changed(); false }

    fun play(move: Move) {
        val game = match ?: return
        if (!canMove) return
        hintPath = emptyList()
        val before = game.position
        if (mode == PlayMode.GUEST) {
            if (move !in CheckersEngine.legal(before, game.rules)) return
            waiting = true; pendingMove = move
            lan?.send(turnMessage("request", before, move)); changed(); return
        }
        if (!game.play(move)) return
        if (mode == PlayMode.HOST) lan?.send(turnMessage("applied", before, move).put("after", CheckersCodec.digest(game.position)))
        advanced(move)
    }
    private fun turnMessage(type: String, before: Position, move: Move) = JSONObject().put("type", type)
        .put("ply", before.ply).put("before", CheckersCodec.digest(before)).put("move", CheckersCodec.move(move))
    private fun advanced(move: Move) {
        lastMove = move; hintPath = emptyList(); waiting = false; pendingMove = null; revision++
        val game = match ?: return
        while (game.history.size > 256) game.history.removeAt(0)
        if (game.result != null && finishedAt == null) finishedAt = elapsedSeconds
        if (game.result != null && !recorded) {
            val outcome = game.result!!
            val key = when (mode) {
                PlayMode.SOLO -> "solo_${difficulty.name}_${if (outcome == 0) "draw" else if (outcome == human) "win" else "loss"}"
                PlayMode.TWO_PLAYERS -> "local_${if (outcome == 0) "draw" else if (outcome == 1) "white" else "black"}"
                else -> "nearby_${if (outcome == 0) "draw" else if (outcome == mySide) "win" else "loss"}"
            }
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply(); recorded = true
        }
        persist(); changed(); requestAi()
    }
    fun undo() {
        val game = match ?: return
        if (network || game.result != null) return
        cancelThought()
        val count = if (mode == PlayMode.SOLO && game.position.turn == human) 2 else 1
        if (game.undo(count)) { revision++; lastMove = null; hintPath = emptyList(); persist() }
        changed(); requestAi()
    }
    fun attach(callback: () -> Unit) { observer = callback; changed(); requestAi() }
    fun detach() { observer = null; if (!network) cancelThought(); persist() }
    fun requestHint() {
        if (!canMove || network) return
        think(true)
    }
    private fun requestAi() {
        if (observer == null || thinking || mode != PlayMode.SOLO) return
        val game = match ?: return
        if (game.result == null && game.position.turn != human) think(false)
    }
    private fun think(hint: Boolean) {
        val game = match ?: return
        val snapshot = game.position; val ticket = ++generation
        thinking = true; changed()
        task = thinker.submit {
            val move = runCatching { CheckersAi.choose(snapshot, game.rules, if (hint) Difficulty.HARD else difficulty) }.getOrNull()
            main.post {
                if (ticket != generation || match !== game || game.position != snapshot) return@post
                thinking = false
                if (hint) { hintPath = move?.path.orEmpty(); changed() }
                else if (move != null && game.play(move)) advanced(move) else changed()
            }
        }
    }
    private fun cancelThought() { generation++; task?.cancel(true); task = null; thinking = false }
    fun host(address: String) {
        start(PlayMode.HOST); notice = "Creating room…"; val ticket = ++networkGeneration
        lan = transport(ticket); lan!!.host(address); changed()
    }
    fun join(code: String): Boolean {
        val address = try { LanAddress.parse(code) } catch (_: Exception) { notice = "Enter the full local room code: IP:port/6 digits"; changed(); return false }
        start(PlayMode.GUEST); notice = "Connecting…"; waiting = true
        val ticket = ++networkGeneration; lan = transport(ticket); lan!!.join(address); changed(); return true
    }
    private fun transport(ticket: Int) = CheckersLan { event, data -> main.post {
        if (ticket != networkGeneration) return@post
        when (event) {
            "hosting" -> { roomCode = data!!.getString("code"); notice = "Waiting for your friend…" }
            "connected" -> if (mode == PlayMode.HOST) {
                connected = true; notice = "Connected · You are White"
                lan?.send(JSONObject().put("type", "welcome").put("version", CheckersLan.VERSION)
                    .put("rules", CheckersCodec.rules(match!!.rules)).put("position", CheckersCodec.position(match!!.position)))
            } else { notice = "Connected · Waiting for board…" }
            "disconnected" -> { connected = false; waiting = false; notice = "Connection lost. Keep both phones on the same hotspot/Wi-Fi. Return home to create or join a new room." }
            "message" -> receive(data!!)
        }
        changed()
    } }
    private fun receive(j: JSONObject) {
        try {
            when (j.getString("type")) {
                "welcome" -> {
                    require(mode == PlayMode.GUEST && !connected && j.getInt("version") == CheckersLan.VERSION)
                    val r = CheckersCodec.rules(j.getJSONObject("rules")); val p = CheckersCodec.position(j.getJSONObject("position"), r)
                    require(p == CheckersEngine.initial(r))
                    rules = r; match = CheckersMatch(r); connected = true; waiting = false; revision++
                    notice = "Connected · You are Black"
                }
                "request", "applied" -> {
                    val game = match ?: error("No match")
                    val request = j.getString("type") == "request"
                    require(connected && if (request) mode == PlayMode.HOST && game.position.turn == -1 else mode == PlayMode.GUEST)
                    val move = CheckersCodec.move(j.getJSONObject("move")); val before = game.position
                    require(j.getInt("ply") == before.ply && j.getString("before") == CheckersCodec.digest(before))
                    if (!request && before.turn == -1) require(waiting && pendingMove == move)
                    val next = CheckersEngine.play(before, game.rules, move)
                    if (!request) require(j.getString("after") == CheckersCodec.digest(next))
                    require(game.play(move))
                    if (request) lan?.send(turnMessage("applied", before, move).put("after", CheckersCodec.digest(next)))
                    advanced(move)
                }
                else -> error("Unexpected message")
            }
        } catch (_: Exception) {
            disconnect(); notice = "The boards could not be synchronized. Return home and create a new room."; changed()
        }
    }
    private fun disconnect() { networkGeneration++; lan?.close(); lan = null; connected = false; waiting = false; pendingMove = null }
    private fun persist() {
        val game = match ?: return
        if (network) return
        val j = JSONObject().put("rules", CheckersCodec.rules(game.rules)).put("mode", mode.name).put("human", human)
            .put("difficulty", difficulty.name).put("recorded", recorded).put("elapsed", elapsedSeconds)
            .put("history", JSONArray(game.history.takeLast(256).map { CheckersCodec.position(it) }))
        prefs.edit().putString("saved", j.toString()).apply()
    }
    fun stats(): String = buildString {
        append("SINGLE PLAYER    Win / Loss / Draw\n")
        Difficulty.entries.forEach { d -> append("${d.label}:    ${prefs.getInt("solo_${d.name}_win", 0)} / ${prefs.getInt("solo_${d.name}_loss", 0)} / ${prefs.getInt("solo_${d.name}_draw", 0)}\n") }
        append("\nTWO PLAYERS    White / Black / Draw\n${prefs.getInt("local_white", 0)} / ${prefs.getInt("local_black", 0)} / ${prefs.getInt("local_draw", 0)}\n")
        append("\nNEARBY    Win / Loss / Draw\n${prefs.getInt("nearby_win", 0)} / ${prefs.getInt("nearby_loss", 0)} / ${prefs.getInt("nearby_draw", 0)}")
    }
    private fun changed() { observer?.invoke() }
    override fun onCleared() { observer = null; disconnect(); cancelThought(); thinker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
