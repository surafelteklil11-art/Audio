package com.surafel.audio

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
    private val items = mutableListOf<MediaItem>()
    private val allSongs = mutableListOf<MediaItem>()
    private lateinit var adapter: SongAdapter
    private var currentTab = Tab.SONGS
    private var currentSection = Section.MUSIC
    private enum class Tab { SONGS, PLAYLISTS, FOLDERS, ARTISTS, ALBUMS }
    private enum class Section { HOME, MUSIC, VIDEO, MINE }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) renderSection()
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
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateNowPlaying()
                override fun onPlaybackStateChanged(playbackState: Int) = updateNowPlaying()
            })
            updateNowPlaying()
            requestAudioPermissionIfNeeded()
        }, mainExecutor)

        findViewById<ImageButton>(R.id.play).setOnClickListener {
            if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
            updateNowPlaying()
        }
        findViewById<TextView>(R.id.playAll).setOnClickListener { if (items.isNotEmpty()) playFrom(0) }
        findViewById<TextView>(R.id.shuffleAll).setOnClickListener { shuffleAndPlay() }
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
        findViewById<TextView>(R.id.simpleAction).setOnClickListener { selectSection(Section.MUSIC) }

        findViewById<TextView>(R.id.menuButton).setOnClickListener { showMenu() }
        findViewById<TextView>(R.id.searchButton).setOnClickListener { showSearch() }
        findViewById<TextView>(R.id.premiumButton).setOnClickListener { showPremiumInfo() }

        updateBottomNav()
        renderSection()
    }

    private fun selectSection(section: Section) {
        currentSection = section
        if (section == Section.MUSIC) currentTab = Tab.SONGS
        updateBottomNav()
        renderSection()
    }

    private fun renderSection() {
        val music = findViewById<View>(R.id.musicContent)
        val simple = findViewById<View>(R.id.simpleContent)
        val title = findViewById<TextView>(R.id.screenTitle)
        val action = findViewById<TextView>(R.id.simpleAction)
        val icon = findViewById<TextView>(R.id.simpleIcon)
        music.visibility = if (currentSection == Section.MUSIC) View.VISIBLE else View.GONE
        simple.visibility = if (currentSection == Section.MUSIC) View.GONE else View.VISIBLE

        when (currentSection) {
            Section.HOME -> {
                title.text = "Home"
                icon.text = "⌂"
                findViewById<TextView>(R.id.simpleTitle).text = "Welcome to Audio"
                findViewById<TextView>(R.id.simpleBody).text = "${allSongs.size} songs in your library\nTap below to open your music collection."
                action.text = "Open Music"
                action.setOnClickListener { selectSection(Section.MUSIC) }
                if (hasAudioPermission()) loadSongs() else updateHomeCount()
            }
            Section.MUSIC -> {
                title.text = "Music"
                updateTabStyle()
                if (hasAudioPermission()) loadTab()
            }
            Section.VIDEO -> {
                title.text = "Video"
                icon.text = "▶"
                findViewById<TextView>(R.id.simpleTitle).text = "Video Library"
                findViewById<TextView>(R.id.simpleBody).text = "Videos on your device can be opened from here."
                action.text = "Scan Videos"
                action.setOnClickListener { loadVideoSummary() }
                loadVideoSummary()
            }
            Section.MINE -> {
                title.text = "Mine"
                icon.text = "●"
                findViewById<TextView>(R.id.simpleTitle).text = "My Audio"
                findViewById<TextView>(R.id.simpleBody).text = "Songs: ${allSongs.size}\nBackground playback: ON\nNotification controls: ON\nRepeat: OFF"
                action.text = "Refresh Library"
                action.setOnClickListener { loadSongs() }
            }
        }
    }

    private fun selectTab(tab: Tab) {
        currentSection = Section.MUSIC
        currentTab = tab
        updateBottomNav()
        renderSection()
    }

    private fun updateBottomNav() {
        val ids = listOf(R.id.homeNav, R.id.musicNav, R.id.videoNav, R.id.mineNav)
        val selected = when (currentSection) { Section.HOME -> 0; Section.MUSIC -> 1; Section.VIDEO -> 2; Section.MINE -> 3 }
        ids.forEachIndexed { index, id ->
            val container = findViewById<ViewGroup>(id)
            val color = if (index == selected) Color.WHITE else Color.rgb(110, 120, 144)
            for (i in 0 until container.childCount) (container.getChildAt(i) as? TextView)?.setTextColor(color)
        }
    }

    private fun updateTabStyle() {
        val ids = listOf(R.id.songsTab, R.id.playlistsTab, R.id.foldersTab, R.id.artistsTab, R.id.albumsTab)
        val selected = when (currentTab) { Tab.SONGS -> 0; Tab.PLAYLISTS -> 1; Tab.FOLDERS -> 2; Tab.ARTISTS -> 3; Tab.ALBUMS -> 4 }
        ids.forEachIndexed { index, id ->
            findViewById<TextView>(id).apply {
                setTextColor(if (index == selected) Color.WHITE else Color.rgb(101, 113, 139))
                textSize = if (index == selected) 25f else 22f
                setTypeface(typeface, if (index == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        findViewById<View>(R.id.tabIndicator).translationX = when (selected) { 0 -> 0f; 1 -> 82f; 2 -> 190f; 3 -> 300f; else -> 405f }
    }

    private fun playFrom(position: Int) {
        if (position !in items.indices) return
        player.setMediaItems(items.toList(), position, 0L)
        player.prepare()
        player.play()
        updateNowPlaying()
    }

    private fun shuffleAndPlay() {
        if (items.isEmpty()) return
        player.setMediaItems(items.shuffled(), 0, 0L)
        player.prepare()
        player.play()
        updateNowPlaying()
    }

    private fun requestAudioPermissionIfNeeded() {
        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, audio) == PackageManager.PERMISSION_GRANTED) renderSection()
        else permissionLauncher.launch(arrayOf(audio))
    }

    private fun hasAudioPermission(): Boolean {
        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, audio) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadTab() {
        if (!hasAudioPermission()) return
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
        if (!hasAudioPermission()) return
        val found = mutableListOf<MediaItem>(); val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(base, baseProjection(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while(c.moveToNext()) found += mediaItem(android.content.ContentUris.withAppendedId(base,c.getLong(id)),c.getString(title),c.getString(artist))
        }
        allSongs.clear(); allSongs.addAll(found)
        if (currentSection == Section.MUSIC) replaceItems(found) else updateSimpleBody()
    }

    private fun updateHomeCount() {
        findViewById<TextView>(R.id.simpleBody).text = "${allSongs.size} songs in your library\nTap below to open your music collection."
    }
    private fun updateSimpleBody() {
        when (currentSection) {
            Section.HOME -> updateHomeCount()
            Section.MINE -> findViewById<TextView>(R.id.simpleBody).text = "Songs: ${allSongs.size}\nBackground playback: ON\nNotification controls: ON\nRepeat: OFF"
            else -> Unit
        }
    }

    private fun loadGroups(column: String, label: String) {
        val groups = linkedMapOf<String, Pair<MediaItem, Int>>(); val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(base, baseProjection(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "$column COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val group=c.getColumnIndexOrThrow(column)
            while(c.moveToNext()) { val name=c.getString(group)?.takeIf{it.isNotBlank()}?:"Unknown $label"; val item=mediaItem(android.content.ContentUris.withAppendedId(base,c.getLong(id)),name,if(label=="artist")"Artist" else "Album"); val old=groups[name]; groups[name]=if(old==null)item to 1 else old.first to old.second+1 }
        }
        replaceItems(groups.map { (name,pair)->pair.first.buildUpon().setMediaMetadata(pair.first.mediaMetadata.buildUpon().setTitle(name).setArtist("$label • ${pair.second} songs").build()).build() })
    }

    private fun loadFolders() {
        val groups=linkedMapOf<String,Pair<MediaItem,Int>>(); val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(base,baseProjection(),"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.DATA} COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val data=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while(c.moveToNext()) { val folder=File(c.getString(data)?:continue).parentFile?.name?:"Music"; val item=mediaItem(android.content.ContentUris.withAppendedId(base,c.getLong(id)),folder,"Folder"); val old=groups[folder]; groups[folder]=if(old==null)item to 1 else old.first to old.second+1 }
        }
        replaceItems(groups.map { (name,pair)->pair.first.buildUpon().setMediaMetadata(pair.first.mediaMetadata.buildUpon().setTitle(name).setArtist("Folder • ${pair.second} songs").build()).build() })
    }

    private fun loadPlaylists() {
        val found=mutableListOf<MediaItem>(); val playlists=MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        contentResolver.query(playlists,arrayOf(MediaStore.Audio.Playlists._ID,MediaStore.Audio.Playlists.NAME),null,null,"${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC")?.use { c ->
            val pid=c.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID); val name=c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while(c.moveToNext()) { val playlistId=c.getLong(pid); val members=MediaStore.Audio.Playlists.Members.getContentUri("external",playlistId); var firstUri=Uri.EMPTY; var count=0; contentResolver.query(members,arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),null,null,null)?.use { m -> val audioId=m.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID); if(m.moveToFirst()){firstUri=android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,m.getLong(audioId));count=m.count} }; if(firstUri != Uri.EMPTY) found += mediaItem(firstUri,c.getString(name),"Playlist • $count songs") }
        }
        replaceItems(found)
    }

    private fun loadVideoSummary() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) { permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO)); return }
        val base=MediaStore.Video.Media.EXTERNAL_CONTENT_URI; var count=0
        contentResolver.query(base,arrayOf(MediaStore.Video.Media._ID),null,null,null)?.use { count=it.count }
        findViewById<TextView>(R.id.simpleBody).text = "$count videos found on this device.\nVideo playback can be opened with your installed video player."
        findViewById<TextView>(R.id.simpleAction).text = "Open Video App"
        findViewById<TextView>(R.id.simpleAction).setOnClickListener { openFirstVideo() }
    }

    private fun openFirstVideo() {
        val base=MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(base,arrayOf(MediaStore.Video.Media._ID),null,null,"${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            if (c.moveToFirst()) { val id=c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)); val uri=android.content.ContentUris.withAppendedId(base,id); startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        }
    }

    private fun replaceItems(found:List<MediaItem>){items.clear();items.addAll(found);adapter.notifyDataSetChanged();findViewById<TextView>(R.id.playAll).text="▶  Play (${items.size})"}
    private fun mediaItem(uri:Uri,title:String?,artist:String?)=MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title?:"Unknown").setArtist(artist?:"Unknown artist").build()).build()
    private fun updateNowPlaying(){if(!::player.isInitialized)return;val item=player.currentMediaItem;findViewById<TextView>(R.id.title).text=item?.mediaMetadata?.title?:"Nothing playing";findViewById<TextView>(R.id.artist).text=item?.mediaMetadata?.artist?:"Choose a song";findViewById<ImageButton>(R.id.play).setImageResource(if(player.isPlaying)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)}

    private fun showQueue() { if (!::player.isInitialized) return; AlertDialog.Builder(this).setTitle("Queue (${player.mediaItemCount})").setMessage((0 until player.mediaItemCount).joinToString("\n") { i -> "${i + 1}. ${player.getMediaItemAt(i).mediaMetadata.title ?: "Unknown"}" }).setPositiveButton("Close", null).show() }
    private fun showSearch() { val input=EditText(this); input.hint="Search songs, artists, albums"; input.setSingleLine(true); AlertDialog.Builder(this).setTitle("Search").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Search"){_,_->filterSongs(input.text.toString())}.show(); input.requestFocus(); input.postDelayed({(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)},150) }
    private fun filterSongs(query:String){val q=query.trim();if(q.isEmpty()){replaceItems(allSongs);return};replaceItems(allSongs.filter{(it.mediaMetadata.title?.toString()?.contains(q,true)==true)||(it.mediaMetadata.artist?.toString()?.contains(q,true)==true)})}
    private fun showMenu(){AlertDialog.Builder(this).setTitle("Audio").setItems(arrayOf("Refresh library","Repeat off","About")){_,which->when(which){0->loadSongs();1->player.repeatMode=Player.REPEAT_MODE_OFF;2->showPremiumInfo()}}.show()}
    private fun showPremiumInfo(){AlertDialog.Builder(this).setTitle("Audio Player").setMessage("Modern local music player\nBackground playback enabled\nMedia notification enabled\nYour music stays on your device.").setPositiveButton("OK",null).show()}
    override fun onResume(){super.onResume();if(::player.isInitialized&&hasAudioPermission())renderSection()}
    override fun onDestroy(){if(::controllerFuture.isInitialized)MediaController.releaseFuture(controllerFuture);super.onDestroy()}
}

private class SongAdapter(private val items:List<MediaItem>,private val onClick:(Int)->Unit):RecyclerView.Adapter<SongAdapter.Holder>(){
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int)=Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_song,parent,false))
    override fun onBindViewHolder(holder:Holder,position:Int){val item=items[position];holder.title.text=item.mediaMetadata.title?:"Unknown";holder.artist.text=item.mediaMetadata.artist?:"Unknown artist";holder.itemView.setOnClickListener{onClick(position)}}
    override fun getItemCount()=items.size
    class Holder(view:View):RecyclerView.ViewHolder(view){val title:TextView=view.findViewById(R.id.songTitle);val artist:TextView=view.findViewById(R.id.songArtist)}
}
