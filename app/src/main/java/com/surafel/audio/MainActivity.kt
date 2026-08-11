package com.surafel.audio

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
    private lateinit var adapter: SongAdapter
    private lateinit var videoAdapter: VideoAdapter
    private val items = mutableListOf<MediaItem>()
    private val allSongs = mutableListOf<MediaItem>()
    private val videos = mutableListOf<VideoEntry>()
    private var currentSection = Section.MUSIC
    private var currentTab = Tab.SONGS
    private var audioSortMode = 0
    private var videoSortMode = 0
    private val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }

    private enum class Tab { SONGS, PLAYLISTS, FOLDERS, ARTISTS, ALBUMS }
    private enum class Section { HOME, MUSIC, VIDEO, MINE }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { renderSection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = SongAdapter(items) { playFrom(it) }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        videoAdapter = VideoAdapter(videos) { playVideo(it) }
        findViewById<RecyclerView>(R.id.videoList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = videoAdapter
        }

        findViewById<ImageButton>(R.id.play).setOnClickListener {
            if (!::player.isInitialized) return@setOnClickListener
            if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
            updateNowPlaying()
        }
        findViewById<TextView>(R.id.playAll).setOnClickListener { if (items.isNotEmpty()) playFrom(0) }
        findViewById<TextView>(R.id.shuffleAll).setOnClickListener { shuffleAndPlay() }
        findViewById<TextView>(R.id.sortSongs).setOnClickListener { showAudioSortDialog() }
        findViewById<TextView>(R.id.queueButton).setOnClickListener { showQueue() }
        findViewById<TextView>(R.id.songsTab).setOnClickListener { selectTab(Tab.SONGS) }
        findViewById<TextView>(R.id.playlistsTab).setOnClickListener { selectTab(Tab.PLAYLISTS) }
        findViewById<TextView>(R.id.foldersTab).setOnClickListener { selectTab(Tab.FOLDERS) }
        findViewById<TextView>(R.id.artistsTab).setOnClickListener { selectTab(Tab.ARTISTS) }
        findViewById<TextView>(R.id.albumsTab).setOnClickListener { selectTab(Tab.ALBUMS) }
        findViewById<View>(R.id.homeNav).setOnClickListener { selectSection(Section.HOME) }
        findViewById<View>(R.id.musicNav).setOnClickListener { selectSection(Section.MUSIC) }
        findViewById<View>(R.id.videoNav).setOnClickListener { selectSection(Section.VIDEO) }
        findViewById<View>(R.id.mineNav).setOnClickListener { selectSection(Section.MINE) }
        findViewById<TextView>(R.id.videoScan).setOnClickListener { loadVideos() }
        findViewById<TextView>(R.id.sortVideos).setOnClickListener { showVideoSortDialog() }
        findViewById<TextView>(R.id.registerProfile).setOnClickListener { showProfileEditor() }
        findViewById<TextView>(R.id.profileEdit).setOnClickListener { showProfileEditor() }
        findViewById<TextView>(R.id.simpleAction).setOnClickListener { selectSection(Section.MUSIC) }
        findViewById<TextView>(R.id.menuButton).setOnClickListener { showMenu() }
        findViewById<TextView>(R.id.searchButton).setOnClickListener { showSearch() }
        findViewById<TextView>(R.id.premiumButton).setOnClickListener { showPremiumInfo() }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            player = controllerFuture.get()
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateNowPlaying()
                override fun onPlaybackStateChanged(playbackState: Int) = updateNowPlaying()
            })
            updateNowPlaying()
            requestAudioPermissionIfNeeded()
        }, mainExecutor)

        updateBottomNav()
        renderSection()
    }

    private fun selectSection(section: Section) {
        currentSection = section
        if (section == Section.MUSIC) currentTab = Tab.SONGS
        updateBottomNav()
        renderSection()
    }

    private fun selectTab(tab: Tab) {
        currentSection = Section.MUSIC
        currentTab = tab
        updateBottomNav()
        renderSection()
    }

    private fun renderSection() {
        findViewById<View>(R.id.musicContent).visibility = if (currentSection == Section.MUSIC) View.VISIBLE else View.GONE
        findViewById<View>(R.id.videoContent).visibility = if (currentSection == Section.VIDEO) View.VISIBLE else View.GONE
        findViewById<View>(R.id.simpleContent).visibility = if (currentSection == Section.MUSIC || currentSection == Section.VIDEO) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.screenTitle).text = when (currentSection) {
            Section.HOME -> "Home"
            Section.MUSIC -> "Music"
            Section.VIDEO -> "Video"
            Section.MINE -> "Mine"
        }
        when (currentSection) {
            Section.HOME -> renderHome()
            Section.MUSIC -> { updateTabStyle(); if (hasAudioPermission()) loadSongs() else requestAudioPermissionIfNeeded() }
            Section.VIDEO -> if (hasVideoPermission()) loadVideos() else requestVideoPermission()
            Section.MINE -> renderMine()
        }
    }

    private fun renderHome() {
        findViewById<View>(R.id.mineProfile).visibility = View.GONE
        findViewById<View>(R.id.weeklyReport).visibility = View.GONE
        findViewById<TextView>(R.id.simpleIcon).visibility = View.VISIBLE
        findViewById<TextView>(R.id.simpleTitle).visibility = View.VISIBLE
        findViewById<TextView>(R.id.simpleBody).visibility = View.VISIBLE
        findViewById<TextView>(R.id.simpleAction).visibility = View.VISIBLE
        findViewById<TextView>(R.id.simpleIcon).text = "⌂"
        findViewById<TextView>(R.id.simpleTitle).text = "Welcome to Audio"
        findViewById<TextView>(R.id.simpleBody).text = "${allSongs.size} songs in your library\nYour private music, beautifully organized."
        findViewById<TextView>(R.id.simpleAction).text = "OPEN MUSIC"
    }

    private fun renderMine() {
        findViewById<TextView>(R.id.simpleIcon).visibility = View.GONE
        findViewById<TextView>(R.id.simpleTitle).visibility = View.GONE
        findViewById<TextView>(R.id.simpleBody).visibility = View.GONE
        findViewById<TextView>(R.id.simpleAction).visibility = View.GONE
        findViewById<View>(R.id.mineProfile).visibility = View.VISIBLE
        findViewById<View>(R.id.weeklyReport).visibility = View.VISIBLE
        val name = prefs.getString("name", "Music Lover") ?: "Music Lover"
        val subtitle = prefs.getString("subtitle", "Enjoy Listening") ?: "Enjoy Listening"
        findViewById<TextView>(R.id.profileName).text = name
        findViewById<TextView>(R.id.profileSubtitle).text = subtitle
        findViewById<TextView>(R.id.profileAvatar).text = name.trim().firstOrNull()?.uppercase() ?: "A"
        findViewById<TextView>(R.id.statPlayed).text = "♪\nMusic Played\n${prefs.getInt("played", 0)} Times"
        findViewById<TextView>(R.id.statSongs).text = "♫\nStorage\n${allSongs.size} Songs"
        findViewById<TextView>(R.id.statToday).text = "◷\nToday Played\n${prefs.getInt("today", 0)} Times"
        findViewById<TextView>(R.id.statTime).text = "◴\nListening Time\n${prefs.getInt("minutes", 0)} Mins"
    }

    private fun updateBottomNav() {
        val ids = listOf(R.id.homeNav, R.id.musicNav, R.id.videoNav, R.id.mineNav)
        val selected = when (currentSection) { Section.HOME -> 0; Section.MUSIC -> 1; Section.VIDEO -> 2; Section.MINE -> 3 }
        ids.forEachIndexed { index, id ->
            val box = findViewById<ViewGroup>(id)
            val color = if (index == selected) Color.WHITE else Color.rgb(110, 120, 144)
            for (i in 0 until box.childCount) (box.getChildAt(i) as? TextView)?.setTextColor(color)
        }
    }

    private fun updateTabStyle() {
        val ids = listOf(R.id.songsTab, R.id.playlistsTab, R.id.foldersTab, R.id.artistsTab, R.id.albumsTab)
        val selected = when (currentTab) { Tab.SONGS -> 0; Tab.PLAYLISTS -> 1; Tab.FOLDERS -> 2; Tab.ARTISTS -> 3; Tab.ALBUMS -> 4 }
        ids.forEachIndexed { i, id ->
            findViewById<TextView>(id).apply {
                setTextColor(if (i == selected) Color.WHITE else Color.rgb(101, 113, 139))
                textSize = if (i == selected) 25f else 21f
                setTypeface(typeface, if (i == selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        findViewById<View>(R.id.tabIndicator).translationX = floatArrayOf(0f, 82f, 180f, 286f, 395f)[selected]
    }

    private fun playFrom(position: Int) {
        if (!::player.isInitialized || position !in items.indices) return
        player.setMediaItems(items.toList(), position, 0L)
        player.prepare()
        player.play()
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).putInt("today", prefs.getInt("today", 0) + 1).apply()
        updateNowPlaying()
    }

    private fun shuffleAndPlay() {
        if (!::player.isInitialized || items.isEmpty()) return
        player.setMediaItems(items.shuffled(), 0, 0L)
        player.prepare()
        player.play()
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).putInt("today", prefs.getInt("today", 0) + 1).apply()
        updateNowPlaying()
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(arrayOf(permission))
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestVideoPermission() {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO))
    }

    private fun hasVideoPermission(): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

    private fun loadSongs() {
        if (!hasAudioPermission()) return
        val found = mutableListOf<MediaItem>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val sortOrder = when (audioSortMode) {
            1 -> "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            2 -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            else -> "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        }
        contentResolver.query(base, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, sortOrder)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val uri = android.content.ContentUris.withAppendedId(base, cursor.getLong(id))
                found += mediaItem(uri, cursor.getString(title), cursor.getString(artist))
            }
        }
        allSongs.clear()
        allSongs.addAll(found)
        replaceItems(found)
    }

    private fun loadVideos() {
        if (!hasVideoPermission()) return
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        videos.clear()
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION)
        val sortOrder = when (videoSortMode) {
            1 -> "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"
            2 -> "${MediaStore.Video.Media.SIZE} DESC"
            3 -> "${MediaStore.Video.Media.DURATION} DESC"
            else -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
        }
        contentResolver.query(base, projection, null, null, sortOrder)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val uri = android.content.ContentUris.withAppendedId(base, cursor.getLong(id))
                videos += VideoEntry(uri, cursor.getString(title) ?: "Video", cursor.getLong(size), cursor.getLong(duration))
            }
        }
        videoAdapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.videoCount).text = "${videos.size} Videos"
    }

    private fun showAudioSortDialog() {
        val options = arrayOf("Title A–Z", "Artist A–Z", "Recently added")
        AlertDialog.Builder(this)
            .setTitle("Sort Audio by")
            .setSingleChoiceItems(options, audioSortMode) { dialog, which ->
                audioSortMode = which
                dialog.dismiss()
                loadSongs()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVideoSortDialog() {
        val options = arrayOf("Recently added", "Title A–Z", "Largest first", "Longest first")
        AlertDialog.Builder(this)
            .setTitle("Sort Video by")
            .setSingleChoiceItems(options, videoSortMode) { dialog, which ->
                videoSortMode = which
                dialog.dismiss()
                loadVideos()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playVideo(entry: VideoEntry) {
        if (!hasVideoPermission()) return
        startActivity(Intent(this, FullscreenVideoActivity::class.java).apply {
            putExtra(FullscreenVideoActivity.EXTRA_VIDEO_URI, entry.uri.toString())
            putExtra(FullscreenVideoActivity.EXTRA_VIDEO_TITLE, entry.title)
        })
    }

    private fun replaceItems(found: List<MediaItem>) {
        items.clear()
        items.addAll(found)
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.playAll).text = "▶  Play (${items.size})"
    }

    private fun mediaItem(uri: Uri, title: String?, artist: String?) = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title ?: "Unknown").setArtist(artist ?: "Unknown artist").build())
        .build()

    private fun updateNowPlaying() {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem
        findViewById<TextView>(R.id.title).text = item?.mediaMetadata?.title ?: "Nothing playing"
        findViewById<TextView>(R.id.artist).text = item?.mediaMetadata?.artist ?: "Choose a song"
        findViewById<ImageButton>(R.id.play).setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun showProfileEditor() {
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(35, 10, 35, 0)
        }
        val name = EditText(this).apply { hint = "Your name"; setSingleLine(); setText(prefs.getString("name", "")) }
        val subtitle = EditText(this).apply { hint = "Profile subtitle"; setSingleLine(); setText(prefs.getString("subtitle", "Enjoy Listening")) }
        box.addView(name)
        box.addView(subtitle)
        AlertDialog.Builder(this).setTitle("Create your profile").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
            prefs.edit().putString("name", name.text.toString().trim().ifEmpty { "Music Lover" }).putString("subtitle", subtitle.text.toString().trim().ifEmpty { "Enjoy Listening" }).apply()
            renderMine()
        }.show()
    }

    private fun showQueue() {
        if (!::player.isInitialized) return
        val message = (0 until player.mediaItemCount).joinToString("\n") { i -> "${i + 1}. ${player.getMediaItemAt(i).mediaMetadata.title ?: "Unknown"}" }
        AlertDialog.Builder(this).setTitle("Queue (${player.mediaItemCount})").setMessage(message.ifEmpty { "Queue is empty" }).setPositiveButton("Close", null).show()
    }

    private fun showSearch() {
        val input = EditText(this).apply { hint = "Search songs, artists"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Search").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Search") { _, _ ->
            val q = input.text.toString().trim()
            replaceItems(if (q.isEmpty()) allSongs else allSongs.filter { it.mediaMetadata.title?.toString()?.contains(q, true) == true || it.mediaMetadata.artist?.toString()?.contains(q, true) == true })
        }.show()
        input.requestFocus()
        input.postDelayed({ (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 150)
    }

    private fun showMenu() {
        AlertDialog.Builder(this).setTitle("Audio").setItems(arrayOf("Refresh library", "Repeat off", "About")) { _, which ->
            when (which) {
                0 -> loadSongs()
                1 -> if (::player.isInitialized) player.repeatMode = Player.REPEAT_MODE_OFF
                2 -> showPremiumInfo()
            }
        }.show()
    }

    private fun showPremiumInfo() {
        AlertDialog.Builder(this).setTitle("Audio Player").setMessage("Luxury local music and video experience.\nBackground audio playback enabled.\nYour library stays on your device.").setPositiveButton("OK", null).show()
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized) renderSection()
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}

data class VideoEntry(val uri: Uri, val title: String, val size: Long, val duration: Long)

private class SongAdapter(private val items: List<MediaItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.mediaMetadata.title ?: "Unknown"
        holder.artist.text = item.mediaMetadata.artist ?: "Unknown artist"
        holder.itemView.setOnClickListener { onClick(position) }
    }
    override fun getItemCount() = items.size
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.songTitle)
        val artist: TextView = view.findViewById(R.id.songArtist)
    }
}

private class VideoAdapter(private val items: List<VideoEntry>, private val onClick: (VideoEntry) -> Unit) : RecyclerView.Adapter<VideoAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.meta.text = "${formatSize(item.size)} • ${formatDuration(item.duration)}"
        holder.thumb.setVideoUri(item.uri)
        holder.itemView.setOnClickListener { onClick(item) }
    }
    override fun getItemCount() = items.size
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.videoTitle)
        val meta: TextView = view.findViewById(R.id.videoMeta)
        val thumb: VideoThumbnailView = view.findViewById(R.id.videoThumbnail)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb < 1024) String.format("%.1f MB", mb) else String.format("%.1f GB", mb / 1024)
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
}
