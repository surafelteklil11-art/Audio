package com.surafel.audio

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.VideoView
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
    private var currentTab = Tab.SONGS
    private var currentSection = Section.MUSIC
    private enum class Tab { SONGS, PLAYLISTS, FOLDERS, ARTISTS, ALBUMS }
    private enum class Section { HOME, MUSIC, VIDEO, MINE }
    private val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { renderSection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adapter = SongAdapter(items) { playFrom(it) }
        findViewById<RecyclerView>(R.id.list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = this@MainActivity.adapter }
        videoAdapter = VideoAdapter(videos) { playVideo(it) }
        findViewById<RecyclerView>(R.id.videoList).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = videoAdapter }

        findViewById<ImageButton>(R.id.play).setOnClickListener { if (::player.isInitialized) { if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play(); updateNowPlaying() } }
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
        findViewById<TextView>(R.id.videoScan).setOnClickListener { loadVideos() }
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
            updateNowPlaying(); requestAudioPermissionIfNeeded()
        }, mainExecutor)
        updateBottomNav(); renderSection()
    }

    private fun selectSection(section: Section) { currentSection = section; if (section == Section.MUSIC) currentTab = Tab.SONGS; updateBottomNav(); renderSection() }

    private fun renderSection() {
        findViewById<View>(R.id.musicContent).visibility = if (currentSection == Section.MUSIC) View.VISIBLE else View.GONE
        findViewById<View>(R.id.videoContent).visibility = if (currentSection == Section.VIDEO) View.VISIBLE else View.GONE
        findViewById<View>(R.id.simpleContent).visibility = if (currentSection == Section.MUSIC || currentSection == Section.VIDEO) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.screenTitle).text = when (currentSection) { Section.HOME -> "Home"; Section.MUSIC -> "Music"; Section.VIDEO -> "Video"; Section.MINE -> "Mine" }
        when (currentSection) {
            Section.HOME -> renderHome()
            Section.MUSIC -> { updateTabStyle(); if (hasAudioPermission()) loadTab() }
            Section.VIDEO -> { if (hasVideoPermission()) loadVideos() else requestVideoPermission() }
            Section.MINE -> renderMine()
        }
    }

    private fun renderHome() {
        findViewById<View>(R.id.mineProfile).visibility = View.GONE; findViewById<View>(R.id.weeklyReport).visibility = View.GONE
        findViewById<TextView>(R.id.simpleIcon).visibility = View.VISIBLE; findViewById<TextView>(R.id.simpleTitle).visibility = View.VISIBLE; findViewById<TextView>(R.id.simpleBody).visibility = View.VISIBLE; findViewById<TextView>(R.id.simpleAction).visibility = View.VISIBLE
        findViewById<TextView>(R.id.simpleIcon).text = "⌂"; findViewById<TextView>(R.id.simpleTitle).text = "Welcome to Audio"; findViewById<TextView>(R.id.simpleBody).text = "${allSongs.size} songs in your library\nYour private music, beautifully organized."; findViewById<TextView>(R.id.simpleAction).text = "OPEN MUSIC"; findViewById<TextView>(R.id.simpleAction).setOnClickListener { selectSection(Section.MUSIC) }
        if (hasAudioPermission()) loadSongs()
    }

    private fun renderMine() {
        findViewById<TextView>(R.id.simpleIcon).visibility = View.GONE; findViewById<TextView>(R.id.simpleTitle).visibility = View.GONE; findViewById<TextView>(R.id.simpleBody).visibility = View.GONE; findViewById<TextView>(R.id.simpleAction).visibility = View.GONE
        findViewById<View>(R.id.mineProfile).visibility = View.VISIBLE; findViewById<View>(R.id.weeklyReport).visibility = View.VISIBLE
        val name = prefs.getString("name", "Music Lover") ?: "Music Lover"; val sub = prefs.getString("subtitle", "Enjoy Listening") ?: "Enjoy Listening"
        findViewById<TextView>(R.id.profileName).text = name; findViewById<TextView>(R.id.profileSubtitle).text = sub; findViewById<TextView>(R.id.profileAvatar).text = name.trim().firstOrNull()?.uppercase() ?: "A"
        findViewById<TextView>(R.id.statPlayed).text = "♪\nMusic Played\n${prefs.getInt("played",0)} Times"; findViewById<TextView>(R.id.statSongs).text = "♫\nStorage\n${allSongs.size} Songs"; findViewById<TextView>(R.id.statToday).text = "◷\nToday Played\n${prefs.getInt("today",0)} Times"; findViewById<TextView>(R.id.statTime).text = "◴\nListening Time\n${prefs.getInt("minutes",0)} Mins"
    }

    private fun selectTab(tab: Tab) { currentSection = Section.MUSIC; currentTab = tab; updateBottomNav(); renderSection() }
    private fun updateBottomNav() { val ids=listOf(R.id.homeNav,R.id.musicNav,R.id.videoNav,R.id.mineNav); val selected=when(currentSection){Section.HOME->0;Section.MUSIC->1;Section.VIDEO->2;Section.MINE->3}; ids.forEachIndexed{index,id->val box=findViewById<ViewGroup>(id);val color=if(index==selected)Color.WHITE else Color.rgb(110,120,144);for(i in 0 until box.childCount)(box.getChildAt(i) as? TextView)?.setTextColor(color)} }
    private fun updateTabStyle(){val ids=listOf(R.id.songsTab,R.id.playlistsTab,R.id.foldersTab,R.id.artistsTab,R.id.albumsTab);val selected=when(currentTab){Tab.SONGS->0;Tab.PLAYLISTS->1;Tab.FOLDERS->2;Tab.ARTISTS->3;Tab.ALBUMS->4};ids.forEachIndexed{i,id->findViewById<TextView>(id).apply{setTextColor(if(i==selected)Color.WHITE else Color.rgb(101,113,139));textSize=if(i==selected)25f else 21f;setTypeface(typeface,if(i==selected)android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)}};findViewById<View>(R.id.tabIndicator).translationX=floatArrayOf(0f,82f,180f,286f,395f)[selected]}

    private fun playFrom(position:Int){if(position !in items.indices||!::player.isInitialized)return;player.setMediaItems(items.toList(),position,0L);player.prepare();player.play();prefs.edit().putInt("played",prefs.getInt("played",0)+1).putInt("today",prefs.getInt("today",0)+1).apply();updateNowPlaying()}
    private fun shuffleAndPlay(){if(items.isEmpty()||!::player.isInitialized)return;player.setMediaItems(items.shuffled(),0,0L);player.prepare();player.play();prefs.edit().putInt("played",prefs.getInt("played",0)+1).putInt("today",prefs.getInt("today",0)+1).apply();updateNowPlaying()}
    private fun requestAudioPermissionIfNeeded(){val p=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE;if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED)permissionLauncher.launch(arrayOf(p))}
    private fun hasAudioPermission()=ContextCompat.checkSelfPermission(this,if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
    private fun requestVideoPermission(){if(Build.VERSION.SDK_INT>=33)permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO))}
    private fun hasVideoPermission()=Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED

    private fun loadTab(){when(currentTab){Tab.SONGS->loadSongs();Tab.ARTISTS->loadGroups(MediaStore.Audio.Media.ARTIST,"artist");Tab.ALBUMS->loadGroups(MediaStore.Audio.Media.ALBUM,"album");Tab.FOLDERS->loadFolders();Tab.PLAYLISTS->loadPlaylists()}}
    private fun baseProjection()=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DATA)
    private fun loadSongs(){if(!hasAudioPermission())return;val found=mutableListOf<MediaItem>();val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;contentResolver.query(base,baseProjection(),"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);while(c.moveToNext())found+=mediaItem(android.content.ContentUris.withAppendedId(base,c.getLong(id)),c.getString(title),c.getString(artist))};allSongs.clear();allSongs.addAll(found);if(currentSection==Section.MUSIC)replaceItems(found)}
    private fun loadGroups(column:String,label:String){val groups=linkedMapOf<String,Pair<MediaItem,Int>>();val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;contentResolver.query(base,baseProjection(),"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"$column COLLATE NOCASE ASC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val group=c.getColumnIndexOrThrow(column);while(c.moveToNext()){val name=c.getString(group)?.takeIf{it.isNotBlank()}?:"Unknown $label";val item=mediaItem(android.content.ContentUris.withAppendedId(base,c.getLong(id)),name,label);val old=groups[name];groups[name]=if(old==null)item to 1 else old.first to old.second+1}};replaceItems(groups.map{(name,p)->p.first.buildUpon().setMediaMetadata(p.first.mediaMetadata.buildUpon().setTitle(name).setArtist("$label • ${p.second} songs").build()).build()})}
    private fun loadFolders(){val groups=linkedMapOf<String,Pair<MediaItem,Int>>();val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;contentResolver.query(base,baseProjection(),"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.DATA} ASC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val data=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);while(c.moveToNext()){val folder=File(c.getString(data)?:continue).parentFile?.name?:"Music";val item=mediaItem(android.content.ContentUris.withAppendedId(base,c.getLong(id)),folder,"Folder");val old=groups[folder];groups[folder]=if(old==null)item to 1 else old.first to old.second+1}};replaceItems(groups.map{(name,p)->p.first.buildUpon().setMediaMetadata(p.first.mediaMetadata.buildUpon().setTitle(name).setArtist("Folder • ${p.second} songs").build()).build()})}
    private fun loadPlaylists(){val found=mutableListOf<MediaItem>();val playlists=MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;contentResolver.query(playlists,arrayOf(MediaStore.Audio.Playlists._ID,MediaStore.Audio.Playlists.NAME),null,null,"${MediaStore.Audio.Playlists.NAME} ASC")?.use{c->val pid=c.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);val name=c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME);while(c.moveToNext()){val id=c.getLong(pid);val members=MediaStore.Audio.Playlists.Members.getContentUri("external",id);var uri=Uri.EMPTY;var count=0;contentResolver.query(members,arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),null,null,null)?.use{m->val aid=m.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);count=m.count;if(m.moveToFirst())uri=android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,m.getLong(aid))};if(uri!=Uri.EMPTY)found+=mediaItem(uri,c.getString(name),"Playlist • $count songs")}};replaceItems(found)}

    private fun loadVideos(){if(!hasVideoPermission())return;val base=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;videos.clear();contentResolver.query(base,arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.TITLE,MediaStore.Video.Media.SIZE,MediaStore.Video.Media.DURATION),null,null,"${MediaStore.Video.Media.DATE_ADDED} DESC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);val title=c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);val size=c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);val duration=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);while(c.moveToNext())videos+=VideoEntry(android.content.ContentUris.withAppendedId(base,c.getLong(id)),c.getString(title)?:"Video",c.getLong(size),c.getLong(duration))};videoAdapter.notifyDataSetChanged();findViewById<TextView>(R.id.videoCount).text="${videos.size} Videos"}
    private fun playVideo(entry:VideoEntry){val v=findViewById<VideoView>(R.id.videoPlayer);findViewById<TextView>(R.id.videoEmpty).visibility=View.GONE;v.setVideoURI(entry.uri);v.setOnPreparedListener{it.isLooping=false;v.start()};v.setOnCompletionListener{findViewById<TextView>(R.id.videoEmpty).visibility=View.VISIBLE}}
    private fun replaceItems(found:List<MediaItem>){items.clear();items.addAll(found);adapter.notifyDataSetChanged();findViewById<TextView>(R.id.playAll).text="▶  Play (${items.size})"}
    private fun mediaItem(uri:Uri,title:String?,artist:String?)=MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title?:"Unknown").setArtist(artist?:"Unknown artist").build()).build()
    private fun updateNowPlaying(){if(!::player.isInitialized)return;val item=player.currentMediaItem;findViewById<TextView>(R.id.title).text=item?.mediaMetadata?.title?:"Nothing playing";findViewById<TextView>(R.id.artist).text=item?.mediaMetadata?.artist?:"Choose a song";findViewById<ImageButton>(R.id.play).setImageResource(if(player.isPlaying)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)}

    private fun showProfileEditor(){val box=android.widget.LinearLayout(this);box.orientation=android.widget.LinearLayout.VERTICAL;box.setPadding(35,10,35,0);val name=EditText(this);name.hint="Your name";name.setSingleLine();name.setText(prefs.getString("name",""));val sub=EditText(this);sub.hint="Profile subtitle";sub.setSingleLine();sub.setText(prefs.getString("subtitle","Enjoy Listening"));box.addView(name);box.addView(sub);AlertDialog.Builder(this).setTitle("Create your profile").setMessage("Make your Mine page personal.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->prefs.edit().putString("name",name.text.toString().trim().ifEmpty{"Music Lover"}).putString("subtitle",sub.text.toString().trim().ifEmpty{"Enjoy Listening"}).apply();renderMine()}.show()}
    private fun showQueue(){if(!::player.isInitialized)return;AlertDialog.Builder(this).setTitle("Queue (${player.mediaItemCount})").setMessage((0 until player.mediaItemCount).joinToString("\n"){i->"${i+1}. ${player.getMediaItemAt(i).mediaMetadata.title?:"Unknown"}"}).setPositiveButton("Close",null).show()}
    private fun showSearch(){val input=EditText(this);input.hint="Search songs, artists, albums";input.setSingleLine(true);AlertDialog.Builder(this).setTitle("Search").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Search"){_,_->filterSongs(input.text.toString())}.show();input.requestFocus();input.postDelayed({(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)},150)}
    private fun filterSongs(q0:String){val q=q0.trim();replaceItems(if(q.isEmpty())allSongs else allSongs.filter{it.mediaMetadata.title?.toString()?.contains(q,true)==true||it.mediaMetadata.artist?.toString()?.contains(q,true)==true})}
    private fun showMenu(){AlertDialog.Builder(this).setTitle("Audio").setItems(arrayOf("Refresh library","Repeat off","About")){_,which->when(which){0->loadSongs();1->if(::player.isInitialized)player.repeatMode=Player.REPEAT_MODE_OFF;2->showPremiumInfo()}}.show()}
    private fun showPremiumInfo(){AlertDialog.Builder(this).setTitle("Audio Player").setMessage("Luxury local music and video experience.\nBackground audio playback enabled.\nYour library stays on your device.").setPositiveButton("OK",null).show()}
    override fun onResume(){super.onResume();if(::player.isInitialized)renderSection()}
    override fun onDestroy(){if(::controllerFuture.isInitialized)MediaController.releaseFuture(controllerFuture);super.onDestroy()}
}

data class VideoEntry(val uri:Uri,val title:String,val size:Long,val duration:Long)
private class SongAdapter(private val items:List<MediaItem>,private val onClick:(Int)->Unit):RecyclerView.Adapter<SongAdapter.Holder>(){override fun onCreateViewHolder(p:ViewGroup,t:Int)=Holder(LayoutInflater.from(p.context).inflate(R.layout.item_song,p,false));override fun onBindViewHolder(h:Holder,pos:Int){val x=items[pos];h.title.text=x.mediaMetadata.title?:"Unknown";h.artist.text=x.mediaMetadata.artist?:"Unknown artist";h.itemView.setOnClickListener{onClick(pos)}};override fun getItemCount()=items.size;class Holder(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.songTitle);val artist:TextView=v.findViewById(R.id.songArtist)}}
private class VideoAdapter(private val items:List<VideoEntry>,private val onClick:(VideoEntry)->Unit):RecyclerView.Adapter<VideoAdapter.Holder>(){override fun onCreateViewHolder(p:ViewGroup,t:Int)=Holder(LayoutInflater.from(p.context).inflate(R.layout.item_video,p,false));override fun onBindViewHolder(h:Holder,pos:Int){val x=items[pos];h.title.text=x.title;h.meta.text="${formatSize(x.size)} • ${formatDuration(x.duration)}";h.itemView.setOnClickListener{onClick(x)}};override fun getItemCount()=items.size;class Holder(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.videoTitle);val meta:TextView=v.findViewById(R.id.videoMeta)}}
private fun formatSize(bytes:Long):String{if(bytes<=0)return "Unknown size";val mb=bytes/1024.0/1024.0;return if(mb<1024)String.format("%.1f MB",mb) else String.format("%.1f GB",mb/1024)}
private fun formatDuration(ms:Long):String{val s=ms/1000;return String.format("%02d:%02d",s/60,s%60)}
