package com.surafel.audio

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.surafel.audio.piano.*

class PianoTilesActivity : AppCompatActivity() {
    private lateinit var model: PianoModel
    private lateinit var audio: PianoAudio
    private lateinit var root: LinearLayout
    private var board: PianoBoardView? = null
    private var overlay: FrameLayout? = null
    private var audioStatus: TextView? = null
    private var displayedPhase: PianoPhase? = null
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var preview: String? = null
    private var receiverRegistered = false
    private val cyan = 0xff71edff.toInt()
    private val muted = 0xffbccbea.toInt()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseGame() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[PianoModel::class.java]
        volumeControlStream = AudioManager.STREAM_MUSIC
        window.statusBarColor = 0xff132859.toInt(); window.navigationBarColor = 0xff251749.toInt()
        root = column().apply {
            fitsSystemWindows = true
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff12617a.toInt(), 0xff142454.toInt(), 0xff46216e.toInt()))
        }
        setContentView(root)
        audio = PianoAudio(this, { updateAudioStatus() }, { pauseGame() })
        onBackPressedDispatcher.addCallback(this) { if (model.engine != null) leaveGame() else finish() }
        if (model.engine == null) renderHome() else buildGame()
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(noisy, filter)
        receiverRegistered = true
        if (model.engine == null) renderHome()
    }
    override fun onStop() {
        pauseGame()
        if (receiverRegistered) { unregisterReceiver(noisy); receiverRegistered = false }
        super.onStop()
    }
    override fun onDestroy() { dialog?.dismiss(); handler.removeCallbacksAndMessages(null); audio.close(); super.onDestroy() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String, size: Float = 16f, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(6), dp(5), dp(6), dp(5))
    }
    private fun panel(color: Int = 0xda173460.toInt()) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0x507cabdb) }
    private fun button(value: String, bright: Boolean = false, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 15f; gravity = Gravity.CENTER; minHeight = dp(50)
        setTextColor(if (bright) 0xff112843.toInt() else Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(10), dp(14), dp(10)); background = panel(if (bright) cyan else 0x663b518c)
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun renderHome() {
        board = null; overlay = null; displayedPhase = null; root.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = column().apply { setPadding(dp(18), dp(10), dp(18), dp(24)) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1)); scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        val nav = row()
        nav.addView(button("‹ Audio") { finish() }, LinearLayout.LayoutParams(-2, -2))
        nav.addView(label("OFFLINE STUDIO", 12f, cyan).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(nav)
        content.addView(label("PIANO\nTILES", 44f).apply { typeface = Typeface.create("sans-serif-black", Typeface.BOLD); setPadding(dp(4), dp(20), 0, dp(8)) })
        content.addView(label("ፒያኖ · Find your rhythm", 17f, cyan))
        content.addView(label("Six original arrangements. Four lanes.\nTap on the line. Hold the glowing notes.", 14f, muted))
        val options = row().apply { setPadding(0, dp(16), 0, dp(10)) }
        options.addView(button("${model.difficulty.label} ▾") { difficultyDialog() }, LinearLayout.LayoutParams(0, -2, 1f))
        options.addView(button(if (model.sound) "♫ Sound on" else "Sound off") {
            stopPreview(); model.setSound(!model.sound); renderHome()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        content.addView(options)
        audioStatus = label("", 12f, muted); content.addView(audioStatus); updateAudioStatus()
        content.addView(label("YOUR SONGS", 12f, cyan))
        for ((index, song) in PianoSongs.all.withIndex()) {
            val card = column().apply { background = panel(); setPadding(dp(12), dp(12), dp(12), dp(12)) }
            card.addView(label("0${index + 1}  ${song.title}", 22f))
            card.addView(label(song.subtitle, 13f, muted))
            card.addView(label("${(song.bpm * model.difficulty.speed).toInt()} BPM  ·  ${song.durationSeconds(model.difficulty)} sec  ·  Best ${model.best(song)}", 12f, muted))
            val actions = row()
            actions.addView(label("${"★".repeat(model.stars(song))}${"☆".repeat(3 - model.stars(song))}", 21f, 0xffffd27d.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button(if (preview == song.id) "■ Stop" else "♫ Listen") { previewSong(song) }, LinearLayout.LayoutParams(-2, -2))
            actions.addView(button("Play ›", true) { stopPreview(); model.select(song); buildGame() }.apply { tag = "piano-play-${song.id}" }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            card.addView(actions)
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        content.addView(button("How to play · አጨዋወት") { help() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }
    private fun difficultyDialog() {
        stopPreview()
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle("Choose your pace").setSingleChoiceItems(PianoDifficulty.entries.map { it.label }.toTypedArray(), model.difficulty.ordinal) { d, index ->
            model.setDifficulty(PianoDifficulty.entries[index]); d.dismiss(); renderHome()
        }.setNegativeButton("Close", null).show()
    }
    private fun help() {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle("Feel the rhythm · ፒያኖ").setMessage(
            "Tap each dark tile as its bottom edge reaches the cyan line. For a long tile, keep your finger in that lane until it clears. Use two fingers for notes side by side.\n\n" +
            "Perfect taps earn 100 points; Great taps earn 70. A completed hold adds 50. Every 10-note combo raises the multiplier, up to ×4. Three mistakes end the attempt.\n\n" +
            "Pause whenever you need. When resuming a hold, press its glowing lane during the one-second countdown. Changing apps or losing audio focus pauses the game.\n\n" +
            "Easy gives you slower notes and a wider timing window. Best scores and stars are saved separately for each song and difficulty. Wired headphones or the speaker work best; Bluetooth can delay sound.\n\n" +
            "All six arrangements and piano tones are included offline. Keyboard: 1–4 or D, F, J, K."
        ).setPositiveButton("Let's play", null).show()
    }
    private fun updateAudioStatus() {
        if (!::audio.isInitialized) return
        audioStatus?.text = when { !model.sound -> "Sound off · You can play silently"; audio.error -> "Piano sound unavailable. Turn sound off to play silently."; !audio.ready -> "Preparing piano sound…"; else -> "♫ Piano ready · No internet needed" }
    }
    private fun acquireAudio(): Boolean {
        if (!model.sound) return true
        if (audio.acquire()) return true
        Toast.makeText(this, when { audio.error -> "Sound unavailable. Choose Sound off to play."; !audio.ready -> "Piano sound is still preparing. Try again shortly."; else -> "Another app is using audio. Try again when it finishes." }, Toast.LENGTH_LONG).show()
        return false
    }
    private fun previewSong(song: PianoSong) {
        val same = preview == song.id; stopPreview()
        if (same) { renderHome(); return }
        if (!model.sound) { Toast.makeText(this, "Turn sound on to listen", Toast.LENGTH_SHORT).show(); return }
        if (!acquireAudio()) return
        preview = song.id; renderHome()
        song.chart(model.difficulty).filter { it.atMs < 4500 }.forEach { note ->
            handler.postDelayed({ if (preview == song.id) audio.play(note) }, note.atMs.toLong())
        }
        handler.postDelayed({ stopPreview(); if (model.engine == null && !isFinishing) renderHome() }, 5500)
    }
    private fun stopPreview() { preview = null; handler.removeCallbacksAndMessages(null); if (::audio.isInitialized) audio.releaseFocus() }
    private fun buildGame() {
        root.removeAllViews(); audioStatus = null; displayedPhase = null
        val e = model.engine ?: return
        val toolbar = row().apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
        toolbar.addView(button("‹ Songs") { leaveGame() })
        toolbar.addView(label(model.song!!.title, 16f).apply { gravity = Gravity.CENTER; maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("Ⅱ") { pauseGame() }.apply { contentDescription = "Pause game" })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        val stage = FrameLayout(this)
        board = PianoBoardView(this, e, { if (model.sound) audio.play(it) }, { audio.stop(it) }, { renderPhase() }).apply { tag = "piano-board" }
        stage.addView(board, FrameLayout.LayoutParams(-1, -1))
        overlay = FrameLayout(this); stage.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        renderPhase()
    }
    private fun renderPhase() {
        val e = model.engine ?: return
        if (displayedPhase == e.phase) return
        displayedPhase = e.phase
        val host = overlay ?: return
        host.removeAllViews(); host.visibility = if (e.phase == PianoPhase.RUNNING) View.GONE else View.VISIBLE
        if (e.phase == PianoPhase.RUNNING) return
        model.recordResult()
        val scroll = ScrollView(this).apply { isFillViewport = false; background = android.graphics.drawable.ColorDrawable(0xb009102b.toInt()) }
        val wrap = column().apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(20), dp(24), dp(20)) }
        val card = column().apply { background = panel(0xf51a2b58.toInt()); setPadding(dp(20), dp(22), dp(20), dp(22)) }
        val title = when(e.phase) { PianoPhase.READY -> "Ready to play?"; PianoPhase.PAUSED -> "Paused · ቆሟል"; PianoPhase.COMPLETE -> "Song complete!"; else -> "Try that rhythm again" }
        card.addView(label(title, 27f).apply { gravity = Gravity.CENTER })
        card.addView(label("${model.song!!.title} · ${model.difficulty.label}", 14f, cyan).apply { gravity = Gravity.CENTER })
        if (e.phase in listOf(PianoPhase.COMPLETE, PianoPhase.FAILED)) {
            card.addView(label("${"★".repeat(e.stars)}${"☆".repeat(3 - e.stars)}", 36f, 0xffffd27d.toInt()).apply { gravity = Gravity.CENTER })
            card.addView(label("${e.score} points\nBest ${model.best(model.song!!)}  ·  Combo ${e.bestCombo}", 20f).apply { gravity = Gravity.CENTER })
            card.addView(label("Perfect ${e.perfect}  ·  Great ${e.great}\nMistakes ${e.misses}", 14f, muted).apply { gravity = Gravity.CENTER })
            // Let the final tap ring before returning focus to the music player.
            handler.postDelayed({ if (model.engine === e && e.phase != PianoPhase.RUNNING) audio.releaseFocus() }, 800)
        } else {
            card.addView(label(if (e.phase == PianoPhase.READY) "Tap when a tile reaches the glowing line.\nKeep long tiles pressed. You have 3 lives." else "Your rhythm is saved here.\nFor held notes, touch the glowing tiles during the resume countdown.", 15f, muted).apply { gravity = Gravity.CENTER })
        }
        val startText = when(e.phase) { PianoPhase.READY -> "Start · ጀምር"; PianoPhase.PAUSED -> "Resume · ቀጥል"; else -> "Play again" }
        card.addView(button(startText, true) {
            handler.removeCallbacksAndMessages(null)
            if (e.phase == PianoPhase.COMPLETE || e.phase == PianoPhase.FAILED) { audio.releaseFocus(); model.retry(); buildGame(); return@button }
            if (!acquireAudio()) return@button
            if (e.phase == PianoPhase.READY) e.start() else e.resume()
            board?.wake(); renderPhase()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        card.addView(button(if (model.sound) "♫ Sound on" else "Sound off") {
            model.setSound(!model.sound); if (!model.sound) audio.releaseFocus(); displayedPhase = null; renderPhase()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        card.addView(button("Song library") { goHome() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        wrap.addView(card, LinearLayout.LayoutParams(-1, -2)); scroll.addView(wrap, FrameLayout.LayoutParams(-1, -2))
        host.addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    }
    private fun pauseGame() {
        board?.pause(); model.engine?.pause(); stopPreview()
        if (model.engine == null) renderHome() else renderPhase()
    }
    private fun leaveGame() {
        pauseGame()
        val phase = model.engine?.phase
        if (phase == PianoPhase.READY || phase == PianoPhase.COMPLETE || phase == PianoPhase.FAILED) { goHome(); return }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle("Leave this attempt?").setMessage("The current attempt will end. Your saved best scores stay available.")
            .setPositiveButton("Song library") { _, _ -> goHome() }.setNegativeButton("Keep playing", null).show()
    }
    private fun goHome() { board?.pause(); stopPreview(); model.home(); renderHome() }
}
