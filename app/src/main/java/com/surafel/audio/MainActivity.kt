package com.surafel.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private val songs = mutableListOf<MediaItem>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) scanAndPrepare()
    }

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            songs.add(MediaItem.fromUri(uri))
        }
        player.setMediaItems(songs)
        if (songs.isNotEmpty()) player.prepare()
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        player = ExoPlayer.Builder(this).build()

        requestAudioPermissionIfNeeded()

        findViewById<android.widget.Button>(R.id.addButton).setOnClickListener {
            picker.launch(arrayOf("audio/*"))
        }
        findViewById<android.widget.ImageButton>(R.id.play).setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        findViewById<android.widget.ImageButton>(R.id.next).setOnClickListener {
            player.seekToNextMediaItem()
        }
        findViewById<android.widget.ImageButton>(R.id.prev).setOnClickListener {
            player.seekToPreviousMediaItem()
        }

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                findViewById<android.widget.ImageButton>(R.id.play).setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                findViewById<android.widget.TextView>(R.id.title).text =
                    item?.mediaMetadata?.title ?: "Playing"
            }
        })
    }

    private fun requestAudioPermissionIfNeeded() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            scanAndPrepare()
        }
    }

    private fun scanAndPrepare() {
        // The permission is requested only when missing. The system remembers the grant.
        // User-selected files remain available through persistable URI permissions.
        updateStatus()
    }

    private fun updateStatus() {
        findViewById<android.widget.TextView>(R.id.statusText).text =
            if (songs.isEmpty()) "Your music, beautifully simple" else "${songs.size} song${if (songs.size == 1) "" else "s"} ready"
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
