package com.surafel.audio.piano

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** A compact original piano-like sample. SoundPool transposes it from C4 through C6. */
object PianoWave {
    const val SAMPLE_RATE = 44100
    fun bytes(): ByteArray {
        val count = SAMPLE_RATE * 2
        val out = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()); out.putInt(36 + count * 2); out.put("WAVEfmt ".toByteArray())
        out.putInt(16); out.putShort(1); out.putShort(1); out.putInt(SAMPLE_RATE)
        out.putInt(SAMPLE_RATE * 2); out.putShort(2); out.putShort(16); out.put("data".toByteArray()); out.putInt(count * 2)
        val harmonics = listOf(1 to .60, 2 to .23, 3 to .10, 4 to .045, 6 to .025)
        repeat(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / .006).coerceAtMost(1.0)
            val tail = ((2.0 - t) / .08).coerceIn(0.0, 1.0)
            var value = 0.0
            for ((harmonic, weight) in harmonics) {
                value += weight * sin(2 * PI * 523.2511306 * harmonic * t) * exp(-t * (2.6 + harmonic * .5))
            }
            out.putShort((value * attack * tail * 25000).toInt().coerceIn(-32767, 32767).toShort())
        }
        return out.array()
    }
    @Synchronized fun cached(context: Context): File {
        val file = File(context.cacheDir, "piano-c5-v1.wav")
        if (file.length() != 44L + SAMPLE_RATE * 4) {
            val temp = File.createTempFile("piano-", ".wav", context.cacheDir)
            try { temp.writeBytes(bytes()); if (!temp.renameTo(file)) { file.writeBytes(temp.readBytes()) } }
            finally { temp.delete() }
        }
        return file
    }
}

class PianoAudio(context: Context, private val changed: () -> Unit, private val focusLost: () -> Unit) : AutoCloseable {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "piano-sample").apply { isDaemon = true } }
    private val manager = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
    private val streams = linkedMapOf<Int, Int>()
    @Volatile private var closed = false
    private var sample = 0
    var ready = false; private set
    var error = false; private set
    var hasFocus = false; private set
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0 && !closed) { hasFocus = false; silence(); focusLost() }
    }
    private val request = if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(listener, handler).build() else null
    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            if (!closed) { ready = status == 0; error = !ready; changed() }
        }
        executor.execute {
            try {
                val file = PianoWave.cached(app)
                handler.post {
                    if (!closed) {
                        try { sample = pool.load(file.absolutePath, 1); if (sample == 0) { error = true; changed() } }
                        catch (_: RuntimeException) { error = true; changed() }
                    }
                }
            } catch (_: Exception) { handler.post { if (!closed) { error = true; changed() } } }
        }
    }
    @Suppress("DEPRECATION") fun acquire(): Boolean {
        if (!ready || closed) return false
        if (hasFocus) return true
        hasFocus = try {
            val result = if (Build.VERSION.SDK_INT >= 26) manager.requestAudioFocus(request!!) else
                manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: RuntimeException) { false }
        return hasFocus
    }
    fun play(note: PianoNote) {
        if (!hasFocus || !ready || closed) return
        stop(note.id)
        val rate = 2.0.pow((note.midi - 72) / 12.0).toFloat().coerceIn(.5f, 2f)
        val stream = pool.play(sample, .75f, .75f, 1, 0, rate)
        if (stream != 0) streams[note.id] = stream
        while (streams.size > 24) { val first = streams.keys.first(); stop(first) }
    }
    fun stop(id: Int) { streams.remove(id)?.let { if (!closed) pool.stop(it) } }
    fun silence() { if (!closed) streams.values.forEach { pool.stop(it) }; streams.clear() }
    @Suppress("DEPRECATION") fun releaseFocus() {
        silence()
        try { if (Build.VERSION.SDK_INT >= 26) manager.abandonAudioFocusRequest(request!!) else manager.abandonAudioFocus(listener) }
        catch (_: RuntimeException) { }
        hasFocus = false
    }
    override fun close() {
        if (closed) return
        releaseFocus(); closed = true; ready = false
        executor.shutdownNow(); handler.removeCallbacksAndMessages(null); pool.release()
    }
}
