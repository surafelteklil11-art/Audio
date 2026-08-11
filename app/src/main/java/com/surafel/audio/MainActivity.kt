package com.surafel.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private val songs = mutableListOf<MediaItem>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) scanDeviceAudio()
    }

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (songs.none { it.localConfiguration?.uri == uri }) {
                songs.add(MediaItem.fromUri(uri))
            }
        }
        refreshPlayer()
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
                findViewById<android.widget.TextView>(R.id.artist).text =
                    item?.mediaMetadata?.artist ?: "Audio"
            }
        })
    }

    private fun requestAudioPermissionIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            scanDeviceAudio()
        }
    }

    private fun scanDeviceAudio() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val found = mutableListOf<MediaItem>()

        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown title"
                val artist = cursor.getString(artistColumn) ?: "Unknown artist"
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                found.add(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .build()
                        )
                        .build()
                )
            }
        }

        songs.clear()
        songs.addAll(found)
        refreshPlayer()
    }

    private fun refreshPlayer() {
        player.setMediaItems(songs)
        if (songs.isNotEmpty()) player.prepare()
        findViewById<android.widget.TextView>(R.id.statusText).text =
            if (songs.isEmpty()) "No music found on your device" else "${songs.size} songs ready"
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && hasAudioPermission()) scanDeviceAudio()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
