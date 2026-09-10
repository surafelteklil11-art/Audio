package com.surafel.audio.piano

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class PianoModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("piano_tiles", 0)
    var difficulty = runCatching { PianoDifficulty.valueOf(prefs.getString("difficulty", "NORMAL")!!) }.getOrDefault(PianoDifficulty.NORMAL)
        private set
    var sound = prefs.getBoolean("sound", true); private set
    var song: PianoSong? = null; private set
    var engine: PianoEngine? = null; private set
    private var recorded = false
    fun select(song: PianoSong) { this.song = song; engine = PianoEngine(song.chart(difficulty), difficulty); recorded = false }
    fun retry() { song?.let { select(it) } }
    fun home() { engine?.pause(); recordResult(); engine = null; song = null }
    fun setDifficulty(value: PianoDifficulty) { difficulty = value; prefs.edit().putString("difficulty", value.name).apply() }
    fun setSound(value: Boolean) { sound = value; prefs.edit().putBoolean("sound", value).apply() }
    fun best(song: PianoSong) = prefs.getInt("best_${song.id}_${difficulty.name}", 0)
    fun stars(song: PianoSong) = prefs.getInt("stars_${song.id}_${difficulty.name}", 0)
    fun recordResult() {
        val e = engine ?: return; val s = song ?: return
        if (recorded || e.phase !in listOf(PianoPhase.COMPLETE, PianoPhase.FAILED)) return
        prefs.edit().putInt("best_${s.id}_${difficulty.name}", maxOf(best(s), e.score))
            .putInt("stars_${s.id}_${difficulty.name}", maxOf(stars(s), e.stars)).apply()
        recorded = true
    }
}
