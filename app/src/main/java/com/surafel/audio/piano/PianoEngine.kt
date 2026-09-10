package com.surafel.audio.piano

import kotlin.math.abs

enum class PianoPhase { READY, RUNNING, PAUSED, FAILED, COMPLETE }
enum class NoteState { PENDING, HOLDING, HIT, MISSED }
enum class Judgment { PERFECT, GREAT }
data class NoteProgress(var state: NoteState = NoteState.PENDING, var pointer: Int? = null, var judgment: Judgment = Judgment.GREAT)

/** Deterministic song-time engine. Rendering speed and wall-clock changes never drive scoring. */
class PianoEngine(val notes: List<PianoNote>, val difficulty: PianoDifficulty) {
    init {
        require(notes.isNotEmpty() && notes.size <= 512)
        require(notes.withIndex().all { (i, n) -> n.id == i && n.lane in 0..3 && n.midi in 60..84 && n.atMs.isFinite() && n.holdMs.isFinite() && n.atMs >= 0 && n.holdMs >= 0 })
        require(notes.zipWithNext().all { (a, b) -> b.atMs >= a.atMs })
        for (lane in 0..3) require(notes.filter { it.lane == lane }.zipWithNext().all { (a, b) -> b.atMs > a.endMs })
    }
    val progress = List(notes.size) { NoteProgress() }
    var phase = PianoPhase.READY; private set
    var elapsedMs = -difficulty.travelMs; private set
    var resumeDelayMs = 0.0; private set
    var score = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var lives = 3; private set
    var perfect = 0; private set
    var great = 0; private set
    var misses = 0; private set
    var feedback = ""; private set
    var feedbackAtMs = -100000.0; private set
    private val downPointers = mutableSetOf<Int>()
    val endMs get() = notes.maxOf { it.endMs } + difficulty.windowMs
    val completion get() = (elapsedMs / endMs).coerceIn(0.0, 1.0)
    val multiplier get() = (1 + combo / 10).coerceAtMost(4)
    val stars get() = if (phase != PianoPhase.COMPLETE) 0 else when {
        misses == 0 && perfect >= notes.size * .9 -> 3
        perfect + great >= notes.size * .8 -> 2
        else -> 1
    }
    fun start() { if (phase == PianoPhase.READY) phase = PianoPhase.RUNNING }
    fun pause() {
        if (phase != PianoPhase.RUNNING) return
        phase = PianoPhase.PAUSED; downPointers.clear()
        progress.forEach { it.pointer = null }
    }
    fun resume() {
        if (phase != PianoPhase.PAUSED) return
        phase = PianoPhase.RUNNING; resumeDelayMs = 1000.0
    }
    fun advance(deltaMs: Double) {
        require(deltaMs.isFinite() && deltaMs >= 0)
        if (phase != PianoPhase.RUNNING) return
        var delta = deltaMs
        if (resumeDelayMs > 0) {
            val frozen = minOf(delta, resumeDelayMs); resumeDelayMs -= frozen; delta -= frozen
            if (resumeDelayMs > 0) return
            progress.withIndex().filter { it.value.state == NoteState.HOLDING && it.value.pointer == null }.forEach { miss(it.index) }
        }
        if (phase != PianoPhase.RUNNING) return
        elapsedMs += delta
        for (note in notes) {
            if (phase != PianoPhase.RUNNING) break
            val p = progress[note.id]
            if (p.state == NoteState.PENDING && elapsedMs > note.atMs + difficulty.windowMs) miss(note.id)
            else if (p.state == NoteState.HOLDING && elapsedMs >= note.endMs) finish(note.id)
        }
        checkComplete()
    }
    fun candidate(lane: Int): PianoNote? {
        if (lane !in 0..3) return null
        return notes.firstOrNull { it.lane == lane && progress[it.id].state == NoteState.HOLDING && progress[it.id].pointer == null }
            ?: notes.filter { it.lane == lane && progress[it.id].state == NoteState.PENDING }
                .minByOrNull { abs(it.atMs - elapsedMs) }
    }
    /** Returns the note to sound. A pointer must lift before it can score another tile. */
    fun down(lane: Int, pointer: Int, onTile: Boolean = true): PianoNote? {
        if (phase != PianoPhase.RUNNING || pointer in downPointers) return null
        downPointers.add(pointer)
        val note = candidate(lane)
        if (note != null && progress[note.id].state == NoteState.HOLDING && onTile) {
            progress[note.id].pointer = pointer; return note
        }
        if (resumeDelayMs > 0 || elapsedMs < -difficulty.windowMs) return null
        if (!onTile || note == null || abs(elapsedMs - note.atMs) > difficulty.windowMs) {
            mistake("WRONG TILE"); return null
        }
        val p = progress[note.id]
        p.judgment = if (abs(elapsedMs - note.atMs) <= difficulty.windowMs * .38) Judgment.PERFECT else Judgment.GREAT
        if (note.holdMs > 0) { p.state = NoteState.HOLDING; p.pointer = pointer; feedback("HOLD") }
        else finish(note.id)
        return note
    }
    fun up(pointer: Int) {
        if (!downPointers.remove(pointer) || phase != PianoPhase.RUNNING) return
        val id = progress.indexOfFirst { it.state == NoteState.HOLDING && it.pointer == pointer }
        if (id < 0) return
        progress[id].pointer = null
        if (resumeDelayMs > 0) return
        if (elapsedMs >= notes[id].endMs - 65) finish(id) else miss(id, "HOLD RELEASED")
        checkComplete()
    }
    private fun finish(id: Int) {
        val p = progress[id]
        if (p.state == NoteState.HIT || p.state == NoteState.MISSED) return
        p.state = NoteState.HIT; p.pointer = null
        val points = if (p.judgment == Judgment.PERFECT) 100 else 70
        score += (points + if (notes[id].holdMs > 0) 50 else 0) * multiplier
        combo++; bestCombo = maxOf(bestCombo, combo)
        if (p.judgment == Judgment.PERFECT) perfect++ else great++
        feedback(p.judgment.name); checkComplete()
    }
    private fun miss(id: Int, message: String = "MISSED") {
        if (phase != PianoPhase.RUNNING || progress[id].state in listOf(NoteState.HIT, NoteState.MISSED)) return
        progress[id].state = NoteState.MISSED; progress[id].pointer = null; mistake(message)
    }
    private fun mistake(message: String) {
        if (phase != PianoPhase.RUNNING) return
        misses++; lives--; combo = 0; feedback(message)
        if (lives <= 0) { phase = PianoPhase.FAILED; downPointers.clear(); progress.forEach { it.pointer = null } }
    }
    private fun feedback(message: String) { feedback = message; feedbackAtMs = elapsedMs }
    private fun checkComplete() {
        if (phase == PianoPhase.RUNNING && progress.all { it.state == NoteState.HIT || it.state == NoteState.MISSED }) phase = PianoPhase.COMPLETE
    }
}
