package com.surafel.audio.checkers

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object CheckersCodec {
    fun rules(r: Rules) = JSONObject().put("name", r.name).put("size", r.size).put("kings", r.kings.name)
        .put("capture", r.capture.name).put("backwards", r.backwards).put("orthogonal", r.orthogonal)
        .put("crown", r.crown.name).put("kingPriority", r.kingPriority).put("first", r.first)
    fun rules(j: JSONObject) = Rules(j.getString("name").take(40), j.getInt("size"), Kings.valueOf(j.getString("kings")),
        Capture.valueOf(j.getString("capture")), j.getBoolean("backwards"), j.getBoolean("orthogonal"),
        Crown.valueOf(j.getString("crown")), j.getBoolean("kingPriority"), j.getInt("first"))
    fun position(p: Position) = JSONObject().put("board", JSONArray(p.board)).put("turn", p.turn).put("quiet", p.quiet).put("ply", p.ply)
    fun position(j: JSONObject, r: Rules): Position {
        val array = j.getJSONArray("board"); require(array.length() == r.size * r.size)
        return Position(List(array.length()) { array.getInt(it) }, j.getInt("turn"), j.getInt("quiet"), j.getInt("ply")).also { require(CheckersEngine.valid(it, r)) }
    }
    fun move(m: Move) = JSONObject().put("path", JSONArray(m.path)).put("captures", JSONArray(m.captures))
    fun move(j: JSONObject): Move {
        val path = j.getJSONArray("path"); val captures = j.getJSONArray("captures")
        require(path.length() in 2..41 && captures.length() <= 40)
        return Move(List(path.length()) { path.getInt(it) }, List(captures.length()) { captures.getInt(it) })
    }
    fun digest(p: Position): String = MessageDigest.getInstance("SHA-256")
        .digest("${p.signature()}:${p.ply}:${p.quiet}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Both peers independently validate the same complete turn before advancing their board. */
class CheckersMatch(val rules: Rules, val history: MutableList<Position> = mutableListOf(CheckersEngine.initial(rules))) {
    init { require(history.isNotEmpty()); require(history.all { CheckersEngine.valid(it, rules) }) }
    val position get() = history.last()
    val result get() = CheckersEngine.outcome(position, rules, history.count { it.signature() == position.signature() })
    fun play(move: Move, expectedPly: Int = position.ply, expectedHash: String = CheckersCodec.digest(position)): Boolean {
        if (result != null || expectedPly != position.ply || expectedHash != CheckersCodec.digest(position)) return false
        if (move !in CheckersEngine.legal(position, rules)) return false
        history.add(CheckersEngine.play(position, rules, move))
        return true
    }
    fun undo(count: Int): Boolean {
        if (history.size <= count) return false
        repeat(count) { history.removeAt(history.lastIndex) }
        return true
    }
}
