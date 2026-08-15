package com.surafel.audio

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.concurrent.Executors

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private var libraryLoaded = false

    private val libraryLoader = object : Runnable {
        override fun run() {
            if (libraryLoaded || mediaSession == null) return
            if (loadLibraryWhenAllowed()) {
                libraryLoaded = true
                refreshWidgets()
                return
            }
            handler.postDelayed(this, 1000L)
        }
    }

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

        VolumeBoosterController.setAudioSessionId(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshWidgets()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshWidgets()
            override fun onPlaybackStateChanged(playbackState: Int) = refreshWidgets()
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refreshWidgets()
        })

        mediaSession = MediaSession.Builder(this, player)
            .setId("AudioPlayerSession")
            .build()

        handler.post(libraryLoader)
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** Load the complete local music library, not only the first item, so widget next/previous have a real queue. */
    private fun loadLibraryWhenAllowed(): Boolean {
        if (!hasAudioPermission()) return false
        val player = mediaSession?.player ?: return false
        if (player.mediaItemCount > 0) return true

        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val loaded = mutableListOf<MediaItem>()

        return try {
            contentResolver.query(
                base,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(base, cursor.getLong(id))
                    loaded += MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(cursor.getString(title) ?: "Unknown")
                                .setArtist(cursor.getString(artist) ?: "Unknown artist")
                                .build()
                        )
                        .build()
                }
            }
            if (loaded.isEmpty()) return false
            player.setMediaItems(loaded, 0, 0L)
            player.prepare()
            player.playWhenReady = false
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Push the current Media3 state to every installed widget immediately. */
    private fun refreshWidgets() {
        val player = mediaSession?.player ?: return
        AudioWidgetRenderer.updateAll(this, player)

        val uri = player.currentMediaItem?.localConfiguration?.uri ?: return
        artworkExecutor.execute {
            val bitmap = loadEmbeddedArtwork(uri)
            handler.post {
                if (mediaSession?.player === player) AudioWidgetRenderer.updateAll(this, player, bitmap)
            }
        }
    }

    private fun loadEmbeddedArtwork(uri: Uri): android.graphics.Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player != null && player.isPlaying) return
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(libraryLoader)
        artworkExecutor.shutdownNow()
        VolumeBoosterController.release()
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
