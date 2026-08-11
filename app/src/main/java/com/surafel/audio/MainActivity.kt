package com.surafel.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private val songs = mutableListOf<MediaItem>()
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            songs.add(MediaItem.fromUri(uri))
        }
        player.setMediaItems(songs)
        if (songs.isNotEmpty()) player.prepare()
        findViewById<TextView>(R.id.statusText).text = "${songs.size} song${if (songs.size == 1) "" else "s"} ready"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        player = ExoPlayer.Builder(this).build()
        requestAudioPermission()
        findViewById<Button>(R.id.addButton).setOnClickListener { picker.launch(arrayOf("audio/*")) }
        findViewById<ImageButton>(R.id.play).setOnClickListener { if (player.isPlaying) player.pause() else player.play() }
        findViewById<ImageButton>(R.id.next).setOnClickListener { player.seekToNextMediaItem() }
        findViewById<ImageButton>(R.id.prev).setOnClickListener { player.seekToPreviousMediaItem() }
        findViewById<SeekBar>(R.id.seek).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) player.seekTo(p.toLong()) }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                findViewById<ImageButton>(R.id.play).setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                findViewById<TextView>(R.id.title).text = item?.mediaMetadata?.title ?: "Playing"
                findViewById<SeekBar>(R.id.seek).max = player.duration.coerceAtLeast(0).toInt()
            }
        })
    }

    private fun requestAudioPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 42)
        }
    }

    override fun onDestroy() { player.release(); super.onDestroy() }
}
