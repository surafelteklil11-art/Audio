package com.surafel.audio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setPauseAtEndOfMediaItems(false)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
            }

        mediaSession = MediaSession.Builder(this, player)
            .setId("AudioPlayerSession")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Keep the MediaSessionService alive while playback is active.
        // Android may still stop a foreground service under aggressive OEM
        // battery management, but removing the app task must not intentionally
        // destroy an active player.
        val player = mediaSession?.player
        if (player != null && player.isPlaying) {
            return
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
