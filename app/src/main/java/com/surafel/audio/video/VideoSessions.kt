package com.surafel.audio.video

import android.content.*
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import java.util.UUID

enum class VideoOwner { FULLSCREEN, TRANSFERRING, POPUP }
class VideoSession(val id: String, var uri: Uri, var title: String, val player: ExoPlayer) {
    var owner = VideoOwner.FULLSCREEN; internal set
    var muted = false; internal set
    var renderedFrames = 0; internal set
}

/** One owner per player. Transferring a surface never recreates its decoder or timeline. */
object VideoSessions {
    const val MAX_VIDEOS = 6
    private val entries = linkedMapOf<String, VideoSession>()
    val sessions get() = entries.values.toList()
    val observers = linkedSetOf<() -> Unit>()
    var takeFromPopup: ((String) -> Unit)? = null
    private lateinit var app: Context
    private lateinit var audio: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var duck = 1f
    private val resumeAfterFocus = mutableSetOf<String>()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseAll() }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true; duck = 1f
                val resume = resumeAfterFocus.toSet(); resumeAfterFocus.clear()
                sessions.forEach { it.player.volume = if (it.muted) 0f else duck; if (it.id in resume) it.player.play() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duck = .2f; sessions.forEach { it.player.volume = if (it.muted) 0f else duck } }
            else -> {
                hasFocus = false
                resumeAfterFocus.clear()
                if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) resumeAfterFocus.addAll(sessions.filter { it.player.playWhenReady }.map { it.id })
                sessions.forEach { it.player.pause() }
            }
        }
    }
    fun get(id: String?) = entries[id]
    fun create(context: Context, uri: Uri, title: String, position: Long = 0, speed: Float = 1f, playing: Boolean = true): VideoSession? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (entries.size >= MAX_VIDEOS) return null
        if (entries.isEmpty()) {
            app = context.applicationContext; audio = app.getSystemService(AudioManager::class.java)
            ContextCompat.registerReceiver(app, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        val player = ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2000, 10000, 750, 1500)
                .setTargetBufferBytes(4 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).build())
            .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
            .build()
        val session = VideoSession(UUID.randomUUID().toString(), uri, title, player)
        session.muted = entries.values.any { !it.muted }
        player.volume = if (session.muted) 0f else duck
        entries[session.id] = session
        player.setMediaItem(MediaItem.fromUri(uri)); player.seekTo(position.coerceAtLeast(0)); player.setPlaybackSpeed(speed.coerceIn(.25f, 3f)); player.prepare()
        if (playing) play(session)
        changed(); return session
    }
    fun play(session: VideoSession) {
        if (!hasFocus) {
            val result = if (Build.VERSION.SDK_INT >= 26) {
                val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper())).build().also { focusRequest = it }
                audio.requestAudioFocus(request)
            } else { @Suppress("DEPRECATION") audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) }
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (hasFocus) { if (session.player.playbackState == androidx.media3.common.Player.STATE_ENDED) session.player.seekTo(0); session.player.play() }
    }
    fun pause(session: VideoSession) { resumeAfterFocus.remove(session.id); session.player.pause() }
    fun pauseAll() { resumeAfterFocus.clear(); sessions.forEach { it.player.pause() } }
    fun mute(session: VideoSession) { session.muted = !session.muted; session.player.volume = if (session.muted) 0f else duck; changed() }
    fun next(context: Context, session: VideoSession): Boolean {
        val next = com.surafel.audio.VideoQueue.consumeNext(context) ?: return false
        session.uri = Uri.parse(next.first); session.title = next.second
        session.player.setMediaItem(MediaItem.fromUri(session.uri)); session.player.prepare(); play(session); changed(); return true
    }
    fun move(id: String, owner: VideoOwner) { entries[id]?.owner = owner; changed() }
    fun fullscreen(id: String): VideoSession? {
        val session = entries[id] ?: return null
        session.owner = VideoOwner.FULLSCREEN; takeFromPopup?.invoke(id); changed(); return session
    }
    fun release(id: String) {
        val session = entries.remove(id) ?: return
        resumeAfterFocus.remove(id); session.player.release()
        if (entries.isEmpty()) {
            runCatching { app.unregisterReceiver(noisy) }
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            else { @Suppress("DEPRECATION") audio.abandonAudioFocus(focusListener) }
            focusRequest = null; hasFocus = false; duck = 1f
        }
        changed()
    }
    fun changed() { observers.toList().forEach { it() } }
}
