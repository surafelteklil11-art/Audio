package com.surafel.audio

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var player: MediaController
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val items = mutableListOf<MediaItem>()
    private lateinit var adapter: SongAdapter
    private var currentTab = Tab.SONGS

    private enum class Tab { SONGS, PLAYLISTS, FOLDERS, ARTISTS, ALBUMS }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadTab()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adapter = SongAdapter(items) { position -> playFrom(position) }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            player = controllerFuture.get()
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateNowPlaying()
            })
            updateNowPlaying()
            requestAudioPermissionIfNeeded()
        }, mainExecutor)

        findViewById<ImageButton>(R.id.play).setOnClickListener {
            if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
        }
        findViewById<TextView>(R.id.playAll).setOnClickListener { if (items.isNotEmpty()) playFrom(0) }
        findViewById<TextView>(R.id.shuffleAll).setOnClickListener {
            if (items.isNotEmpty()) {
                player.setMediaItems(items.shuffled(), 0, 0L)
                player.prepare(); player.play()
            }
        }

        findViewById<TextView>(R.id.songsTab).setOnClickListener { selectTab(Tab.SONGS) }
        findViewById<TextView>(R.id.playlistsTab).setOnClickListener { selectTab(Tab.PLAYLISTS) }
        findViewById<TextView>(R.id.foldersTab).setOnClickListener { selectTab(Tab.FOLDERS) }
        findViewById<TextView>(R.id.artistsTab).setOnClickListener { selectTab(Tab.ARTISTS) }
        findViewById<TextView>(R.id.albumsTab).setOnClickListener { selectTab(Tab.ALBUMS) }
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        updateTabStyle()
        if (::player.isInitialized && hasAudioPermission()) loadTab()
    }

    private fun updateTabStyle() {
        val ids = listOf(R.id.songsTab, R.id.playlistsTab, R.id.foldersTab, R.id.artistsTab, R.id.albumsTab)
        val selected = when (currentTab) {
            Tab.SONGS -> 0; Tab.PLAYLISTS -> 1; Tab.FOLDERS -> 2; Tab.ARTISTS -> 3; Tab.ALBUMS -> 4
        }
        ids.forEachIndexed { index, id ->
            findViewById<TextView>(id).apply {
                setTextColor(if (index == selected) Color.WHITE else Color.rgb(101,113,139))
                textSize = if (index == selected) 25f else 22f
                setTypeface(typeface, if (index == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun playFrom(position: Int) {
        if (position !in items.indices) return
        val item = items[position]
        player.setMediaItem(item, 0L)
        player.prepare(); player.play()
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadTab()
        else permissionLauncher.launch(permission)
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadTab() {
        when (currentTab) {
            Tab.SONGS -> loadSongs()
            Tab.ARTISTS -> loadGroups(MediaStore.Audio.Media.ARTIST, "artist")
            Tab.ALBUMS -> loadGroups(MediaStore.Audio.Media.ALBUM, "album")
            Tab.FOLDERS -> loadFolders()
            Tab.PLAYLISTS -> loadPlaylists()
        }
    }

    private fun baseProjection() = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATA)

    private fun loadSongs() {
        val found = mutableListOf<MediaItem>()
        val uriBase = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(uriBase, baseProjection(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while(c.moveToNext()) found += mediaItem(android.content.ContentUris.withAppendedId(uriBase,c.getLong(id)), c.getString(title), c.getString(artist))
        }
        replaceItems(found)
    }

    private fun loadGroups(column: String, label: String) {
        val groups = linkedMapOf<String, Pair<MediaItem, Int>>()
        val uriBase = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(uriBase, baseProjection(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "$column COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val group=c.getColumnIndexOrThrow(column)
            while(c.moveToNext()) {
                val name=c.getString(group)?.takeIf{it.isNotBlank()} ?: "Unknown $label"
                val item=mediaItem(android.content.ContentUris.withAppendedId(uriBase,c.getLong(id)), name, if(label=="artist") "Artist" else "Album")
                val old=groups[name]; groups[name]=if(old==null) item to 1 else old.first to (old.second+1)
            }
        }
        replaceItems(groups.map { (name,pair) -> pair.first.buildUpon().setMediaMetadata(pair.first.mediaMetadata.buildUpon().setTitle(name).setArtist("$label • ${pair.second} songs").build()).build() })
    }

    private fun loadFolders() {
        val groups = linkedMapOf<String, Pair<MediaItem, Int>>()
        val uriBase=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(uriBase, baseProjection(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATA} COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val data=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while(c.moveToNext()) {
                val path=c.getString(data) ?: continue; val folder=File(path).parentFile?.name ?: "Music"
                val item=mediaItem(android.content.ContentUris.withAppendedId(uriBase,c.getLong(id)), folder, "Folder")
                val old=groups[folder]; groups[folder]=if(old==null)item to 1 else old.first to old.second+1
            }
        }
        replaceItems(groups.map { (name,pair)->pair.first.buildUpon().setMediaMetadata(pair.first.mediaMetadata.buildUpon().setTitle(name).setArtist("Folder • ${pair.second} songs").build()).build() })
    }

    private fun loadPlaylists() {
        val found=mutableListOf<MediaItem>(); val playlists=MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        contentResolver.query(playlists,arrayOf(MediaStore.Audio.Playlists._ID,MediaStore.Audio.Playlists.NAME),null,null,"${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID); val name=c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while(c.moveToNext()) found += mediaItem(android.net.Uri.EMPTY,c.getString(name),"Playlist")
        }
        replaceItems(found)
    }

    private fun mediaItem(uri: android.net.Uri, title: String?, artist: String?) = MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title ?: "Unknown").setArtist(artist ?: "Unknown artist").build()).build()

    private fun replaceItems(found: List<MediaItem>) {
        items.clear(); items.addAll(found); adapter.notifyDataSetChanged(); findViewById<TextView>(R.id.playAll).text="▶  Play (${items.size})"
    }

    private fun updateNowPlaying() {
        if (!::player.isInitialized) return
        val item=player.currentMediaItem
        findViewById<TextView>(R.id.title).text=item?.mediaMetadata?.title ?: "Nothing playing"
        findViewById<TextView>(R.id.artist).text=item?.mediaMetadata?.artist ?: "Choose a song"
        findViewById<ImageButton>(R.id.play).setImageResource(if(player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    override fun onResume() { super.onResume(); if(::player.isInitialized && hasAudioPermission()) loadTab() }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}

private class SongAdapter(private val items: List<MediaItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)=Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_song,parent,false))
    override fun onBindViewHolder(holder: Holder, position: Int) { val item=items[position]; holder.title.text=item.mediaMetadata.title?:"Unknown"; holder.artist.text=item.mediaMetadata.artist?:"Unknown artist"; holder.itemView.setOnClickListener{onClick(position)} }
    override fun getItemCount()=items.size
    class Holder(view: View):RecyclerView.ViewHolder(view){ val title:TextView=view.findViewById(R.id.songTitle); val artist:TextView=view.findViewById(R.id.songArtist) }
}
