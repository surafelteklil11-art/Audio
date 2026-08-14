package com.surafel.audio

import kotlin.math.roundToInt

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private var homeView: View? = null
    private val prefs by lazy { getSharedPreferences("audio_profile", MODE_PRIVATE) }
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    private enum class Tab { SONGS, PLAYLISTS, FOLDERS, ARTISTS, ALBUMS }
    private enum class Section { HOME, MUSIC, VIDEO, MINE }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { renderSection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupFuturisticShell()
        applyDriveMode()
        adapter = SongAdapter(items) { playFrom(it) }
        findViewById<RecyclerView>(R.id.list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = this@MainActivity.adapter }
        videoAdapter = VideoAdapter(videos) { playVideo(it) }
        findViewById<RecyclerView>(R.id.videoList).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = videoAdapter }
        findViewById<ImageButton>(R.id.play).setOnClickListener { if (!::player.isInitialized) return@setOnClickListener; if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play(); updateNowPlaying() }
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
        findViewById<View>(R.id.weeklyReport).setOnClickListener { showWeeklyReport() }
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

    private fun setupFuturisticShell() {
        val labels = listOf("Home", "Audio", "Vedio", "Mine")
        listOf(R.id.homeNav, R.id.musicNav, R.id.videoNav, R.id.mineNav).forEachIndexed { index, id ->
            val box = findViewById<ViewGroup>(id)
            if (box.childCount > 1) (box.getChildAt(1) as? TextView)?.text = labels[index]
            box.setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        findViewById<View>(R.id.bottomNav).setPadding(dp(10), dp(4), dp(10), dp(5))
        findViewById<View>(R.id.miniPlayer).background = roundedGradient(intArrayOf(Color.rgb(13, 17, 36), Color.rgb(20, 12, 42)), Color.rgb(91, 52, 190), dp(1), dp(18))
    }

    private fun selectSection(section: Section) { currentSection = section; if (section == Section.MUSIC) currentTab = Tab.SONGS; updateBottomNav(); renderSection() }
    private fun selectTab(tab: Tab) { currentSection = Section.MUSIC; currentTab = tab; updateBottomNav(); renderSection() }

    private fun renderSection() {
        findViewById<View>(R.id.musicContent).visibility = if (currentSection == Section.MUSIC) View.VISIBLE else View.GONE
        findViewById<View>(R.id.videoContent).visibility = if (currentSection == Section.VIDEO) View.VISIBLE else View.GONE
        findViewById<View>(R.id.simpleContent).visibility = if (currentSection == Section.MUSIC || currentSection == Section.VIDEO) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.screenTitle).text = when (currentSection) { Section.HOME -> "Home"; Section.MUSIC -> "Music"; Section.VIDEO -> "Video"; Section.MINE -> "Mine" }
        when (currentSection) {
            Section.HOME -> renderHome()
            Section.MUSIC -> { updateTabStyle(); if (hasAudioPermission()) loadSongs() else requestAudioPermissionIfNeeded() }
            Section.VIDEO -> if (hasVideoPermission()) loadVideos() else requestVideoPermission()
            Section.MINE -> renderMine()
        }
    }

    private fun renderHome(): Unit {
        val container = findViewById<LinearLayout>(R.id.simpleContainer)
        findViewById<TextView>(R.id.simpleIcon).visibility = View.GONE
        findViewById<TextView>(R.id.simpleTitle).visibility = View.GONE
        findViewById<TextView>(R.id.simpleBody).visibility = View.GONE
        findViewById<TextView>(R.id.simpleAction).visibility = View.GONE
        findViewById<View>(R.id.mineProfile).visibility = View.GONE
        findViewById<View>(R.id.weeklyReport).visibility = View.GONE
        if (homeView == null) homeView = buildHomeView()
        val view = homeView ?: return
        if (view.parent == null) container.addView(view, 0)
        view.visibility = View.VISIBLE
    }

    private fun renderMine() {
        ensureWeeklyWindow()
        homeView?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
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
        findViewById<View>(R.id.weeklyReport).layoutParams = findViewById<View>(R.id.weeklyReport).layoutParams.apply { height = (84 * resources.displayMetrics.density).roundToInt() }
        findViewById<View>(R.id.weeklyReportDot).visibility = View.GONE
    }

    private fun buildHomeView(): View {
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2); isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(24)) }
        scroll.addView(root)
        val greeting = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(8)) }
        greeting.addView(label("GOOD EVENING  •  AUDIO CORE", 11, Color.rgb(170, 106, 255), Typeface.BOLD))
        greeting.addView(label("Welcome Back", 29, Color.WHITE, Typeface.BOLD).apply { setPadding(0, dp(3), 0, 0) })
        greeting.addView(label("Your sound universe is ready.", 13, Color.rgb(132, 145, 177), Typeface.NORMAL).apply { setPadding(0, dp(3), 0, 0) })
        root.addView(greeting)
        val search = card(intArrayOf(Color.rgb(15, 20, 43), Color.rgb(11, 15, 31)), Color.rgb(51, 113, 255), dp(1), dp(16)).apply { isClickable = true; isFocusable = true; setPadding(dp(14), dp(11), dp(14), dp(11)); setOnClickListener { showSearch() } }
        val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        searchRow.addView(label("⌕", 27, Color.rgb(122, 179, 255), Typeface.NORMAL))
        searchRow.addView(label("Search songs, artists, albums…", 14, Color.rgb(136, 149, 180), Typeface.NORMAL).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(label("◈", 19, Color.rgb(188, 117, 255), Typeface.NORMAL))
        search.addView(searchRow)
        root.addView(search, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, 0, 0, dp(14)) })
        val hero = card(intArrayOf(Color.rgb(20, 9, 50), Color.rgb(7, 25, 58)), Color.rgb(151, 66, 255), dp(1), dp(22)).apply { setPadding(dp(18), dp(17), dp(14), dp(17)) }
        val heroRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val heroCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroCopy.addView(label("✦  FEATURED SIGNAL", 10, Color.rgb(190, 126, 255), Typeface.BOLD))
        heroCopy.addView(label("Feel The\nFuture of Sound", 25, Color.WHITE, Typeface.BOLD).apply { setPadding(0, dp(7), 0, dp(7)) })
        heroCopy.addView(label("Explore a new way to listen\nand experience your library.", 12, Color.rgb(178, 190, 218), Typeface.NORMAL))
        val listen = label("  ▶  LISTEN NOW  ", 12, Color.WHITE, Typeface.BOLD).apply { gravity = Gravity.CENTER; background = roundedGradient(intArrayOf(Color.rgb(137, 63, 255), Color.rgb(42, 154, 255)), Color.rgb(204, 136, 255), dp(1), dp(16)); setPadding(dp(4), dp(10), dp(4), dp(10)); isClickable = true; setOnClickListener { if (allSongs.isNotEmpty()) playFrom(0) else selectSection(Section.MUSIC) } }
        heroCopy.addView(listen, LinearLayout.LayoutParams(dp(128), dp(42)).apply { topMargin = dp(13) })
        heroRow.addView(heroCopy, LinearLayout.LayoutParams(0, -2, 1f))
        val orb = label("◉\n∿∿∿", 25, Color.rgb(120, 204, 255), Typeface.BOLD).apply { gravity = Gravity.CENTER; background = roundedGradient(intArrayOf(Color.rgb(40, 21, 94), Color.rgb(7, 54, 94)), Color.rgb(83, 185, 255), dp(1), dp(48)); setShadowLayer(dp(14).toFloat(), 0f, 0f, Color.rgb(139, 67, 255)) }
        heroRow.addView(orb, LinearLayout.LayoutParams(dp(96), dp(96)).apply { leftMargin = dp(8) })
        hero.addView(heroRow)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        root.addView(label("QUICK ACCESS", 12, Color.rgb(139, 151, 184), Typeface.BOLD).apply { setPadding(dp(2), dp(2), 0, dp(8)) })
        val quickRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val quickItems = listOf(Triple("♬", "Trending", Section.MUSIC), Triple("♡", "Favorites", Section.MINE), Triple("⇩", "Downloads", Section.MUSIC), Triple("◷", "History", Section.MINE))
        quickItems.forEachIndexed { index, item ->
            val q = card(intArrayOf(Color.rgb(13, 19, 39), Color.rgb(18, 13, 42)), if (index % 2 == 0) Color.rgb(92, 99, 255) else Color.rgb(207, 67, 201), dp(1), dp(16)).apply { isClickable = true; isFocusable = true; setPadding(dp(5), dp(8), dp(5), dp(7)); setOnClickListener { selectSection(item.third) } }
            val qbox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            qbox.addView(label(item.first, 23, if (index % 2 == 0) Color.rgb(101, 193, 255) else Color.rgb(242, 116, 232), Typeface.NORMAL))
            qbox.addView(label(item.second, 10, Color.rgb(206, 214, 233), Typeface.BOLD).apply { setPadding(0, dp(5), 0, 0) })
            q.addView(qbox)
            quickRow.addView(q, LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = if (index == 0) 0 else dp(6) })
        }
        root.addView(quickRow, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(17) })
        val songsTitle = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        songsTitle.addView(label("Your Soundstream", 20, Color.WHITE, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        songsTitle.addView(label("${allSongs.size} TRACKS  ›", 10, Color.rgb(166, 104, 255), Typeface.BOLD).apply { isClickable = true; setOnClickListener { selectSection(Section.MUSIC) } })
        root.addView(songsTitle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        if (allSongs.isEmpty()) {
            val empty = card(intArrayOf(Color.rgb(12, 18, 36), Color.rgb(17, 12, 35)), Color.rgb(54, 68, 112), dp(1), dp(18)).apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
            empty.addView(label("No local tracks detected yet.", 15, Color.WHITE, Typeface.BOLD))
            empty.addView(label("Grant audio access, then your library will appear here.", 12, Color.rgb(132, 146, 177), Typeface.NORMAL).apply { setPadding(0, dp(6), 0, 0) })
            root.addView(empty)
        } else {
            allSongs.take(4).forEachIndexed { index, song ->
                val track = card(intArrayOf(Color.rgb(10, 15, 31), Color.rgb(17, 13, 35)), Color.rgb(39, 55, 92), dp(1), dp(15)).apply { isClickable = true; setPadding(dp(11), dp(9), dp(10), dp(9)); setOnClickListener { playFrom(index) } }
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                val art = label("${index + 1}", 15, Color.WHITE, Typeface.BOLD).apply { gravity = Gravity.CENTER; background = roundedGradient(intArrayOf(Color.rgb(46, 22, 82), Color.rgb(11, 51, 75)), Color.rgb(107, 80, 220), dp(1), dp(12)) }
                row.addView(art, LinearLayout.LayoutParams(dp(48), dp(48)))
                val meta = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
                meta.addView(label(song.mediaMetadata.title?.toString() ?: "Unknown track", 14, Color.WHITE, Typeface.BOLD).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                meta.addView(label(song.mediaMetadata.artist?.toString() ?: "Unknown artist", 11, Color.rgb(129, 143, 174), Typeface.NORMAL).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) })
                row.addView(meta, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label("▶", 17, Color.rgb(141, 91, 255), Typeface.BOLD))
                track.addView(row)
                root.addView(track, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(7) })
            }
        }
        return scroll
    }

    private fun label(text: String, sizeSp: Int, color: Int, style: Int): TextView = TextView(this).apply { this.text = text; textSize = sizeSp.toFloat(); setTextColor(color); typeface = Typeface.create(Typeface.DEFAULT, style); includeFontPadding = false }
    private fun card(colors: IntArray, strokeColor: Int, strokeWidth: Int, radius: Int): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedGradient(colors, strokeColor, strokeWidth, radius); elevation = dp(2).toFloat() }
    private fun roundedGradient(colors: IntArray, strokeColor: Int, strokeWidth: Int, radius: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius.toFloat(); setStroke(strokeWidth, strokeColor) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun updateBottomNav() {
        val ids = listOf(R.id.homeNav, R.id.musicNav, R.id.videoNav, R.id.mineNav)
        val selected = when (currentSection) { Section.HOME -> 0; Section.MUSIC -> 1; Section.VIDEO -> 2; Section.MINE -> 3 }
        ids.forEachIndexed { index, id ->
            val box = findViewById<ViewGroup>(id)
            val selectedNow = index == selected
            val color = if (selectedNow) Color.WHITE else Color.rgb(110, 120, 144)
            box.background = roundedGradient(if (selectedNow) intArrayOf(Color.rgb(51, 19, 91), Color.rgb(18, 33, 70)) else intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT), if (selectedNow) Color.rgb(139, 75, 255) else Color.rgb(31, 42, 67), if (selectedNow) dp(1) else 0, dp(17))
            for (i in 0 until box.childCount) (box.getChildAt(i) as? TextView)?.apply { setTextColor(color); if (i == 0) setTypeface(typeface, if (selectedNow) Typeface.BOLD else Typeface.NORMAL) }
        }
    }

    private fun updateTabStyle() {
        val ids = listOf(R.id.songsTab, R.id.playlistsTab, R.id.foldersTab, R.id.artistsTab, R.id.albumsTab)
        val selected = when (currentTab) { Tab.SONGS -> 0; Tab.PLAYLISTS -> 1; Tab.FOLDERS -> 2; Tab.ARTISTS -> 3; Tab.ALBUMS -> 4 }
        ids.forEachIndexed { i, id -> findViewById<TextView>(id).apply { setTextColor(if (i == selected) Color.WHITE else Color.rgb(101, 113, 139)); textSize = if (i == selected) 25f else 21f; setTypeface(typeface, if (i == selected) Typeface.BOLD else Typeface.NORMAL) } }
        findViewById<View>(R.id.tabIndicator).translationX = floatArrayOf(0f, 82f, 180f, 286f, 395f)[selected]
    }

    private fun ensureWeeklyWindow() {
        val now = System.currentTimeMillis()
        val start = prefs.getLong("week_start", 0L)
        if (start == 0L || now - start >= 7L * 24L * 60L * 60L * 1000L) prefs.edit().putLong("week_start", now).putInt("week_plays", 0).apply()
    }

    private fun playFrom(position: Int) {
        if (!::player.isInitialized || position !in items.indices) return
        player.setMediaItems(items.toList(), position, 0L); player.prepare(); player.play(); ensureWeeklyWindow()
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).putInt("today", prefs.getInt("today", 0) + 1).putInt("week_plays", prefs.getInt("week_plays", 0) + 1).apply(); updateNowPlaying()
    }

    private fun shuffleAndPlay() {
        if (!::player.isInitialized || items.isEmpty()) return
        player.setMediaItems(items.shuffled(), 0, 0L); player.prepare(); player.play(); ensureWeeklyWindow()
        prefs.edit().putInt("played", prefs.getInt("played", 0) + 1).putInt("today", prefs.getInt("today", 0) + 1).putInt("week_plays", prefs.getInt("week_plays", 0) + 1).apply(); updateNowPlaying()
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(arrayOf(permission))
    }
    private fun hasAudioPermission(): Boolean { val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE; return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED }
    private fun requestVideoPermission() { if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO)) }
    private fun hasVideoPermission(): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

    private fun loadSongs() {
        if (!hasAudioPermission()) return
        val found = mutableListOf<MediaItem>(); val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val sortOrder = when (audioSortMode) { 1 -> "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"; 2 -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"; else -> "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC" }
        contentResolver.query(base, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, sortOrder)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) { val uri = android.content.ContentUris.withAppendedId(base, cursor.getLong(id)); found += mediaItem(uri, cursor.getString(title), cursor.getString(artist)) }
        }
        allSongs.clear(); allSongs.addAll(found); replaceItems(found)
    }

    private fun loadVideos() {
        if (!hasVideoPermission()) return
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI; videos.clear()
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION)
        val sortOrder = when (videoSortMode) { 1 -> "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"; 2 -> "${MediaStore.Video.Media.SIZE} DESC"; 3 -> "${MediaStore.Video.Media.DURATION} DESC"; else -> "${MediaStore.Video.Media.DATE_ADDED} DESC" }
        contentResolver.query(base, projection, null, null, sortOrder)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID); val title = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE); val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE); val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) { val uri = android.content.ContentUris.withAppendedId(base, cursor.getLong(id)); videos += VideoEntry(uri, cursor.getString(title) ?: "Video", cursor.getLong(size), cursor.getLong(duration)) }
        }
        videoAdapter.notifyDataSetChanged(); findViewById<TextView>(R.id.videoCount).text = "${videos.size} Videos"
    }

    private fun showAudioSortDialog() {
        val options = arrayOf("Title A–Z", "Artist A–Z", "Recently added")
        AlertDialog.Builder(this).setTitle("Sort Audio by").setSingleChoiceItems(options, audioSortMode) { dialog, which -> audioSortMode = which; dialog.dismiss(); loadSongs() }.setNegativeButton("Cancel", null).show()
    }
    private fun showVideoSortDialog() {
        val options = arrayOf("Recently added", "Title A–Z", "Largest first", "Longest first")
        AlertDialog.Builder(this).setTitle("Sort Video by").setSingleChoiceItems(options, videoSortMode) { dialog, which -> videoSortMode = which; dialog.dismiss(); loadVideos() }.setNegativeButton("Cancel", null).show()
    }
    private fun playVideo(entry: VideoEntry) { if (!hasVideoPermission()) return; startActivity(Intent(this, FullscreenVideoActivity::class.java).apply { putExtra(FullscreenVideoActivity.EXTRA_VIDEO_URI, entry.uri.toString()); putExtra(FullscreenVideoActivity.EXTRA_VIDEO_TITLE, entry.title) }) }
    private fun replaceItems(found: List<MediaItem>) { items.clear(); items.addAll(found); adapter.notifyDataSetChanged(); findViewById<TextView>(R.id.playAll).text = "▶  Play (${items.size})" }
    private fun mediaItem(uri: Uri, title: String?, artist: String?) = MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title ?: "Unknown").setArtist(artist ?: "Unknown artist").build()).build()
    private fun updateNowPlaying() { if (!::player.isInitialized) return; val item = player.currentMediaItem; findViewById<TextView>(R.id.title).text = item?.mediaMetadata?.title ?: "Nothing playing"; findViewById<TextView>(R.id.artist).text = item?.mediaMetadata?.artist ?: "Choose a song"; findViewById<ImageButton>(R.id.play).setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play) }

    private fun showWeeklyReport() {
        ensureWeeklyWindow(); val weekPlays = prefs.getInt("week_plays", 0); val today = prefs.getInt("today", 0); val total = prefs.getInt("played", 0); val songs = allSongs.size; val minutes = prefs.getInt("minutes", 0)
        AlertDialog.Builder(this).setTitle("Weekly Music Report").setMessage("LAST 7 DAYS\n\n♫  Plays this week     $weekPlays\n◷  Played today        $today times\n♪  Total plays         $total\n▣  Songs in library    $songs\n◴  Listening time      $minutes mins").setPositiveButton("DONE", null).show()
    }

    private fun showProfileEditor() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 10, 35, 0) }
        val name = EditText(this).apply { hint = "Your name"; setSingleLine(); setText(prefs.getString("name", "")) }
        val subtitle = EditText(this).apply { hint = "Profile subtitle"; setSingleLine(); setText(prefs.getString("subtitle", "Enjoy Listening")) }
        box.addView(name); box.addView(subtitle)
        AlertDialog.Builder(this).setTitle("Create your profile").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> prefs.edit().putString("name", name.text.toString().trim().ifEmpty { "Music Lover" }).putString("subtitle", subtitle.text.toString().trim().ifEmpty { "Enjoy Listening" }).apply(); renderMine() }.show()
    }

    private fun showQueue() {
        if (!::player.isInitialized) return
        val message = (0 until player.mediaItemCount).joinToString("\n") { i -> "${i + 1}. ${player.getMediaItemAt(i).mediaMetadata.title ?: "Unknown"}" }
        AlertDialog.Builder(this).setTitle("Queue (${player.mediaItemCount})").setMessage(message.ifEmpty { "Queue is empty" }).setPositiveButton("Close", null).show()
    }

    private fun showSearch() {
        val input = EditText(this).apply { hint = "Search songs, artists"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Search").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Search") { _, _ -> val q = input.text.toString().trim(); replaceItems(if (q.isEmpty()) allSongs else allSongs.filter { it.mediaMetadata.title?.toString()?.contains(q, true) == true || it.mediaMetadata.artist?.toString()?.contains(q, true) == true }) }.show()
        input.requestFocus(); input.postDelayed({ (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 150)
    }

    private fun showMenu() {
        lateinit var dialog: AlertDialog
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(14))
            background = roundedGradient(intArrayOf(Color.rgb(9, 14, 33), Color.rgb(25, 10, 49)), Color.rgb(126, 67, 255), dp(1), dp(22))
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), 0, dp(8)) }
        val icon = TextView(this).apply { text = "♫"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = roundedGradient(intArrayOf(Color.rgb(55, 22, 104), Color.rgb(31, 20, 72)), Color.rgb(137, 66, 255), dp(1), dp(18)) }
        header.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titleBox.addView(label("Audio", 22, Color.WHITE, Typeface.BOLD))
        titleBox.addView(label("Music & video", 13, Color.rgb(184, 190, 217), Typeface.BOLD).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this).apply { text = "×"; textSize = 31f; gravity = Gravity.CENTER; setTextColor(Color.rgb(218, 221, 235)); isClickable = true }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(58)))
        panel.addView(header)
        panel.addView(View(this).apply { setBackgroundColor(Color.rgb(48, 56, 84)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false; setPadding(0, dp(2), 0, dp(10)) }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(menu, ViewGroup.LayoutParams(-1, -1))

        fun addMenuItem(iconText: String, title: String, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(6), 0, dp(6), 0)
                setOnClickListener { onClick() }
            }
            row.addView(label(iconText, 22, Color.rgb(211, 205, 244), Typeface.NORMAL).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(54), dp(58)))
            row.addView(label(title, 17, Color.rgb(228, 230, 243), Typeface.NORMAL).apply { gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(58), 1f))
            menu.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(3) })
        }

        fun addSection(title: String) {
            menu.addView(label(title, 11, Color.rgb(117, 134, 170), Typeface.BOLD).apply { setPadding(dp(6), dp(17), 0, dp(7)) }, LinearLayout.LayoutParams(-1, dp(38)))
        }

        addMenuItem("☷", "Themes") { dialog.dismiss(); showThemes() }
        addMenuItem("▦", "Widgets") { dialog.dismiss(); showWidgets() }
        addSection("PLAYER")
        addMenuItem("≋", "Equalizer") { dialog.dismiss(); showEqualizer() }
        addMenuItem("◷", "Sleep Timer") { dialog.dismiss(); showSleepTimer() }
        addMenuItem("🚗", "Drive Mode") { toggleDriveMode() }
        addSection("APP")
        addMenuItem("⚙", "Settings") { dialog.dismiss(); startActivity(Intent(this, SettingsActivity::class.java)) }

        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        dialog = AlertDialog.Builder(this, R.style.Theme_Audio_SideDrawer).setView(panel).create()
        close.setOnClickListener { dialog.dismiss() }
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setDimAmount(0.62f)
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.window?.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)
            dialog.window?.setGravity(Gravity.START or Gravity.TOP)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.62f)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setLayout(dp(326), WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setGravity(Gravity.START or Gravity.TOP)
    }

    private fun showEqualizer() {
        val equalizerIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
        }
        try {
            startActivity(equalizerIntent)
        } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Equalizer").setMessage("Your device does not provide a system equalizer panel for Audio.").setPositiveButton("OK", null).show()
        }
    }

    private fun showSleepTimer() {
        val options = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes", "120 minutes")
        val values = intArrayOf(0, 15, 30, 45, 60, 90, 120)
        val currentEnd = prefs.getLong("sleep_timer_end", 0L)
        val currentRemaining = if (currentEnd > System.currentTimeMillis()) ((currentEnd - System.currentTimeMillis()) / 60000L).toInt() else 0
        val current = if (currentRemaining > 0) values.indices.minByOrNull { kotlin.math.abs(values[it] - currentRemaining) } ?: 0 else 0
        AlertDialog.Builder(this).setTitle("Sleep Timer").setSingleChoiceItems(options, current) { dialog, which ->
            if (which == 0) cancelSleepTimer() else scheduleSleepTimer(values[which].toLong())
            dialog.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun scheduleSleepTimer(minutes: Long) {
        sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks)
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        prefs.edit().putLong("sleep_timer_end", endAt).apply()
        sleepTimerRunnable = Runnable {
            if (::player.isInitialized) player.pause()
            prefs.edit().remove("sleep_timer_end").apply()
            sleepTimerRunnable = null
        }
        sleepTimerHandler.postDelayed(sleepTimerRunnable!!, minutes * 60_000L)
    }

    private fun cancelSleepTimer() {
        sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks)
        sleepTimerRunnable = null
        prefs.edit().remove("sleep_timer_end").apply()
    }

    private fun restoreSleepTimer() {
        val endAt = prefs.getLong("sleep_timer_end", 0L)
        if (endAt <= 0L) return
        val remaining = endAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            if (::player.isInitialized) player.pause()
            prefs.edit().remove("sleep_timer_end").apply()
            return
        }
        sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks)
        sleepTimerRunnable = Runnable {
            if (::player.isInitialized) player.pause()
            prefs.edit().remove("sleep_timer_end").apply()
            sleepTimerRunnable = null
        }
        sleepTimerHandler.postDelayed(sleepTimerRunnable!!, remaining)
    }

    private fun toggleDriveMode() {
        val enabled = !prefs.getBoolean("drive_mode", false)
        prefs.edit().putBoolean("drive_mode", enabled).apply()
        applyDriveMode()
    }

    private fun applyDriveMode() {
        if (prefs.getBoolean("drive_mode", false)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showThemes() {
        val themes = arrayOf("Nebula Violet", "Cyber Blue", "Midnight Space")
        val current = prefs.getInt("theme", 0)
        AlertDialog.Builder(this).setTitle("Themes").setSingleChoiceItems(themes, current) { dialog, which -> prefs.edit().putInt("theme", which).apply(); dialog.dismiss(); applyTheme(which) }.setNegativeButton("Close", null).show()
    }

    private fun applyTheme(theme: Int) {
        val root = findViewById<View>(android.R.id.content)
        val colors = when (theme) { 1 -> intArrayOf(Color.rgb(5, 18, 40), Color.rgb(9, 42, 72)); 2 -> intArrayOf(Color.rgb(6, 9, 20), Color.rgb(20, 12, 31)); else -> intArrayOf(Color.rgb(10, 9, 29), Color.rgb(31, 11, 58)) }
        root.background = roundedGradient(colors, Color.TRANSPARENT, 0, 0)
    }

    private fun showWidgets() {
        val widgets = arrayOf("Now Playing", "Quick Access", "Soundstream", "Weekly Report")
        AlertDialog.Builder(this).setTitle("Home Widgets").setMultiChoiceItems(widgets, null) { _, _, _ -> }.setPositiveButton("APPLY") { _, _ -> }.setNegativeButton("CLOSE", null).show()
    }

    private fun showPremiumInfo() { AlertDialog.Builder(this).setTitle("Audio Player").setMessage("Luxury local music and video experience.\nBackground audio playback enabled.\nYour library stays on your device.").setPositiveButton("OK", null).show() }
    override fun onResume() { super.onResume(); applyDriveMode(); restoreSleepTimer(); if (::player.isInitialized) renderSection() }
    override fun onDestroy() { sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks); if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture); super.onDestroy() }
}

data class VideoEntry(val uri: Uri, val title: String, val size: Long, val duration: Long)

private class SongAdapter(private val items: List<MediaItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) { val item = items[position]; holder.title.text = item.mediaMetadata.title ?: "Unknown"; holder.artist.text = item.mediaMetadata.artist ?: "Unknown artist"; holder.itemView.setOnClickListener { onClick(position) } }
    override fun getItemCount() = items.size
    class Holder(view: View) : RecyclerView.ViewHolder(view) { val title: TextView = view.findViewById(R.id.songTitle); val artist: TextView = view.findViewById(R.id.songArtist) }
}

private class VideoAdapter(private val items: List<VideoEntry>, private val onClick: (VideoEntry) -> Unit) : RecyclerView.Adapter<VideoAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) { val item = items[position]; holder.title.text = item.title; holder.meta.text = "${formatSize(item.size)} • ${formatDuration(item.duration)}"; holder.thumb.setVideoUri(item.uri); holder.itemView.setOnClickListener { onClick(item) } }
    override fun getItemCount() = items.size
    class Holder(view: View) : RecyclerView.ViewHolder(view) { val title: TextView = view.findViewById(R.id.videoTitle); val meta: TextView = view.findViewById(R.id.videoMeta); val thumb: VideoThumbnailView = view.findViewById(R.id.videoThumbnail) }
}

private fun formatSize(bytes: Long): String { if (bytes <= 0) return "Unknown size"; val mb = bytes / 1024.0 / 1024.0; return if (mb < 1024) String.format("%.1f MB", mb) else String.format("%.1f GB", mb / 1024) }
private fun formatDuration(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return String.format("%02d:%02d", seconds / 60, seconds % 60) }
