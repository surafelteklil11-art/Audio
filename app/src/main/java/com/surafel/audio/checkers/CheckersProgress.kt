package com.surafel.audio.checkers

import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Durable, once-per-match rewards and one local daily tournament entry per date. */
class CheckersProgress(private val prefs: SharedPreferences) {
    val stars get() = prefs.getInt("stars", 0)
    data class Daily(val day: String, val round: Int = 0, val status: String = "playing")
    fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun daily(): Daily? = runCatching { JSONObject(prefs.getString("daily", "")!!).let { Daily(it.getString("day"), it.getInt("round"), it.getString("status")) } }.getOrNull()
    fun startDaily(): Daily {
        daily()?.takeIf { it.day == today() }?.let { return it }
        val daily = Daily(today())
        prefs.edit().putString("daily", json(daily)).putInt("tournament_played", count("tournament_played") + 1).apply()
        return daily
    }
    fun count(key: String) = prefs.getInt(key, 0)
    fun reset(group: String) {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(if (group == "solo") "solo_" else if (group == "tournament") "tournament_" else "nearby_") }.forEach { edit.remove(it) }
        edit.apply()
    }
    fun record(matchId: String, key: String, difficulty: Difficulty, won: Boolean, dailyDay: String? = null) {
        val recorded = prefs.getStringSet("rewarded_matches", emptySet())!!.toMutableSet()
        if (!recorded.add(matchId)) return
        val edit = prefs.edit().putStringSet("rewarded_matches", recorded)
        if (dailyDay == null) edit.putInt(key, count(key) + 1)
        if (won) edit.putInt("stars", (stars.toLong() + difficulty.stars).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val daily = daily()
        if (dailyDay != null && daily?.day == dailyDay && daily.status == "playing") {
            val draw = key.endsWith("draw")
            val next = when { draw -> daily; won && daily.round == 4 -> daily.copy(status = "won"); won -> daily.copy(round = daily.round + 1); else -> daily.copy(status = "eliminated") }
            edit.putString("daily", json(next))
            if (next.status == "won") edit.putInt("tournament_win", count("tournament_win") + 1)
            else if (!won && !draw && daily.round == 4) edit.putInt("tournament_second", count("tournament_second") + 1)
        }
        edit.apply()
    }
    private fun json(d: Daily) = JSONObject().put("day", d.day).put("round", d.round).put("status", d.status).toString()
}
