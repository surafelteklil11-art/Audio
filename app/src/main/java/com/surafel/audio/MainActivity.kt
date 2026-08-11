package com.surafel.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private val songs = mutableListOf<MediaItem>()
    private lateinit var adapter: SongAdapter

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) scanDeviceAudio()
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (songs.none { it.localConfiguration?.uri == uri }) songs.add(MediaItem.fromUri(uri))
        }
        refreshPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        player = ExoPlayer.Builder(this).build()
        adapter = SongAdapter(songs) { position ->
            player.setMediaItems(songs, position, 0L)
            player.prepare()
            player.play()
        }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<ImageButton>(R.id.play).setOnClickListener {
            if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
            updateNowPlaying()
        }
        requestAudioPermissionIfNeeded()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateNowPlaying()
        })
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) scanDeviceAudio()
        else permissionLauncher.launch(arrayOf(permission))
    }

    private fun scanDeviceAudio() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val found = mutableListOf<MediaItem>()
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(id))
                found.add(MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(cursor.getString(title) ?: "Unknown").setArtist(cursor.getString(artist) ?: "Unknown artist").build()).build())
            }
        }
        songs.clear(); songs.addAll(found); refreshPlayer()
    }

    private fun refreshPlayer() {
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.playAll).text = "▶  Play  (${songs.size})"
    }

    private fun updateNowPlaying() {
        val item = player.currentMediaItem
        findViewById<TextView>(R.id.title).text = item?.mediaMetadata?.title ?: "Nothing playing"
        findViewById<TextView>(R.id.artist).text = item?.mediaMetadata?.artist ?: "Choose a song"
        findViewById<ImageButton>(R.id.play).setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    override fun onResume() { super.onResume(); if (::player.isInitialized && hasAudioPermission()) scanDeviceAudio() }
    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

private class SongAdapter(private val songs: List<MediaItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.Holder>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        view.setPadding(12, 16, 8, 16); return Holder(view)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = songs[position]
        holder.title.text = item.mediaMetadata.title ?: "Unknown"
        holder.artist.text = item.mediaMetadata.artist ?: "Unknown artist"
        holder.title.setTextColor(android.graphics.Color.WHITE); holder.artist.setTextColor(android.graphics.Color.rgb(145,157,183))
        holder.itemView.setOnClickListener { onClick(position) }
    }
    override fun getItemCount() = songs.size
    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) { val title: TextView = view.findViewById(android.R.id.text1); val artist: TextView = view.findViewById(android.R.id.text2) }
}
