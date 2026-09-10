package com.surafel.audio.piano

enum class PianoDifficulty(val label: String, val speed: Double, val windowMs: Double, val travelMs: Double) {
    EASY("Easy", .8, 230.0, 2600.0), NORMAL("Normal", 1.0, 180.0, 2200.0), HARD("Hard", 1.2, 135.0, 1850.0)
}
data class PianoNote(val id: Int, val lane: Int, val midi: Int, val atMs: Double, val holdMs: Double = 0.0) {
    val endMs get() = atMs + holdMs
}
data class PianoSong(val id: String, val title: String, val subtitle: String, val bpm: Int,
    val steps: Int, val melody: List<Int>, val shift: Int, val harmony: Boolean) {
    fun chart(difficulty: PianoDifficulty): List<PianoNote> {
        val beat = 60000.0 / (bpm * difficulty.speed)
        val notes = mutableListOf<PianoNote>(); var time = 0.0
        repeat(steps) { i ->
            val lane = (i + i / 8 + shift) % 4
            val hold = i % 8 == 7
            val midi = melody[i % melody.size]
            notes.add(PianoNote(notes.size, lane, midi, time, if (hold) beat * 1.5 else 0.0))
            if (harmony && difficulty != PianoDifficulty.EASY && i % 16 == 8) {
                notes.add(PianoNote(notes.size, (lane + 2) % 4, (midi + 7).coerceAtMost(84), time))
            }
            time += beat * if (hold) 2.0 else 1.0
        }
        return notes
    }
    fun durationSeconds(d: PianoDifficulty) = ((chart(d).last().endMs + 500) / 1000).toInt()
}

/** Original note arrangements synthesized locally; no remote song catalogue or recordings. */
object PianoSongs {
    val all = listOf(
        PianoSong("aurora", "Aurora Steps", "A gentle first melody", 100, 48, listOf(60,64,67,72,71,67,64,62), 0, false),
        PianoSong("blue", "Blue Cascade", "Flowing notes · two-finger chords", 112, 64, listOf(62,65,69,74,72,69,65,67,69,67,65,62), 1, true),
        PianoSong("moon", "Moonlit Keys", "Warm chords and long holds", 92, 56, listOf(64,67,71,76,74,71,67,64,62,67,71,74), 2, true),
        PianoSong("neon", "Neon Waltz", "Bright, quick arpeggios", 126, 72, listOf(60,67,72,76,72,67,62,69,74,77,74,69), 3, true),
        PianoSong("sunrise", "Sunrise Drive", "A lively rising melody", 138, 80, listOf(65,69,72,77,76,72,69,67,65,67,69,72), 1, true),
        PianoSong("midnight", "Midnight Sprint", "Fast lanes · expert timing", 158, 96, listOf(69,72,76,81,79,76,72,71,67,71,74,79,77,74,71,69), 2, true)
    )
}
