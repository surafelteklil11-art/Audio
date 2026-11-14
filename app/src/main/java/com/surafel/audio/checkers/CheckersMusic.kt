package com.surafel.audio.checkers

import android.content.Context
import android.media.*
import android.os.*
import java.util.concurrent.Executors
import kotlin.math.*

/** Original, quiet eight-second instrumental loop, confined to the Checkers screen. */
class CheckersMusic(context: Context) : java.io.Closeable {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var track: AudioTrack? = null
    private var enabled = false
    private var closed = false
    private var generation = 0
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        track?.let { player -> runCatching { when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { player.setVolume(.32f); if (enabled) player.play() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.setVolume(.08f)
            else -> player.pause()
        } } }
    }
    private val request: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes).setOnAudioFocusChangeListener(focus, main).build() else null
    fun setEnabled(value: Boolean) {
        if (closed || value == enabled) return
        enabled = value; val token = ++generation
        if (!value) { release(); return }
        worker.execute {
            val samples = melody()
            main.post {
                if (closed || !enabled || token != generation) return@post
                try {
                    @Suppress("DEPRECATION") val granted = if (Build.VERSION.SDK_INT >= 26) audio.requestAudioFocus(request!!) else audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@post
                    val player = AudioTrack.Builder().setAudioAttributes(attributes)
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(samples.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                    track = player
                    check(player.state != AudioTrack.STATE_UNINITIALIZED)
                    check(player.write(samples, 0, samples.size) == samples.size)
                    check(player.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
                    player.setVolume(.32f); player.play()
                } catch (_: Exception) { release() }
            }
        }
    }
    private fun release() {
        track?.let { runCatching { it.stop() }; it.release() }; track = null
        if (Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(request!!) else { @Suppress("DEPRECATION") audio.abandonAudioFocus(focus) }
    }
    override fun close() { if (closed) return; setEnabled(false); closed = true; generation++; worker.shutdown(); main.removeCallbacksAndMessages(null) }
    companion object {
        private const val RATE = 22050
        private fun melody(): ShortArray {
            val chords = arrayOf(doubleArrayOf(261.63, 329.63, 392.0), doubleArrayOf(220.0, 261.63, 329.63), doubleArrayOf(174.61, 220.0, 261.63), doubleArrayOf(196.0, 246.94, 293.66))
            return ShortArray(RATE * 8) { i ->
                val t = i.toDouble() / RATE; val local = t % 2; val envelope = sin(Math.PI * local / 2).pow(2)
                (chords[(t / 2).toInt()].sumOf { frequency -> sin(2 * Math.PI * frequency * t) + .15 * sin(4 * Math.PI * frequency * t) } * envelope * 2600).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
    }
}
