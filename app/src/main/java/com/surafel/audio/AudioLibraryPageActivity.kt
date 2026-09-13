package com.surafel.audio

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File

/** Full-page browser for Audio library tabs. Navigation never uses a popup. */
class AudioLibraryPageActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private val nameComparator = Comparator<String> { a, b -> a.compareTo(b, ignoreCase = true) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        buildShell(intent.getStringExtra(EXTRA_SECTION) ?: SECTION_SONGS)
    }

    private fun buildShell(section: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(20)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply { text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setOnClickListener { finish() } }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(52)))
        title = TextView(this).apply { textSize = 24f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(header)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        when (section) {
            SECTION_PLAYLISTS -> { title.text = "Playlists"; playlists() }
            SECTION_FOLDERS -> { title.text = "Folders"; folders() }
            SECTION_ARTISTS -> { title.text = "Artists"; artists() }
            SECTION_ALBUMS -> { title.text = "Albums"; albums() }
            else -> { title.text = "Songs"; songs(querySongs(null, null)) }
        }
    }

    private fun playlists() {
        val rows = mutableListOf<Pair<Long,String>>()
        contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME), null, null, "${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID); val name = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while (c.moveToNext()) rows += c.getLong(id) to (c.getString(name) ?: "Unnamed playlist")
        }
        rows.forEach { (id, name) -> row(name) { songs(queryPlaylist(id)) } }
        if (rows.isEmpty()) empty()
    }

    private fun folders() {
        val folders = linkedSetOf<String>()
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.DATA), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATA} COLLATE NOCASE ASC")?.use { c ->
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) { val p = c.getString(data) ?: continue; folders += File(p).parent ?: "/" }
        }
        folders.sortedWith(nameComparator).forEach { folder -> row(folder.substringAfterLast('/').ifBlank { folder }) { songs(queryFolder(folder)) } }
        if (folders.isEmpty()) empty()
    }

    private fun artists() {
        val values = linkedSetOf<String>()
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.ARTIST), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC")?.use { c -> while (c.moveToNext()) values += c.getString(0) ?: "Unknown artist" }
        values.filter { it.isNotBlank() }.sortedWith(nameComparator).forEach { value -> row(value) { songs(querySongs(MediaStore.Audio.Media.ARTIST, value)) } }
        if (values.isEmpty()) empty()
    }

    private fun albums() {
        val values = linkedSetOf<String>()
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.ALBUM), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC")?.use { c -> while (c.moveToNext()) values += c.getString(0) ?: "Unknown album" }
        values.filter { it.isNotBlank() }.sortedWith(nameComparator).forEach { value -> row(value) { songs(querySongs(MediaStore.Audio.Media.ALBUM, value)) } }
        if (values.isEmpty()) empty()
    }

    private fun songs(items: List<MediaItem>) {
        list.removeAllViews()
        if (items.isEmpty()) { empty(); return }
        items.forEachIndexed { index, item ->
            val name = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown" }
            val artist = item.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" }
            row("$name\n$artist") { play(items, index) }
        }
    }

    private fun row(text: String, action: () -> Unit) {
        val v = TextView(this).apply { this.text = text; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)); minHeight = dp(64); setOnClickListener { action() } }
        list.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }

    private fun empty() { list.removeAllViews() }

    private fun querySongs(column: String?, value: String?): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val p = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val selection = if (column == null) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND $column = ?"
        val args = if (value == null) null else arrayOf(value)
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, p, selection, args, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val t = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val a = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) out += item(c.getLong(id), c.getString(t), c.getString(a))
        }
        return out
    }

    private fun queryFolder(folder: String): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val p = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA)
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, p, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val t = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val a = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val d = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) { val path = c.getString(d) ?: continue; if (File(path).parent == folder) out += item(c.getLong(id), c.getString(t), c.getString(a)) }
        }
        return out
    }

    private fun queryPlaylist(id: Long): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id)
        contentResolver.query(uri, arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST), null, null, "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID); val t = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val a = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) out += item(c.getLong(idCol), c.getString(t), c.getString(a))
        }
        return out
    }

    private fun item(id: Long, title: String?, artist: String?) = MediaItem.Builder().setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title ?: "Unknown").setArtist(artist ?: "Unknown artist").build()).build()

    private fun play(items: List<MediaItem>, index: Int) {
        val f = MediaController.Builder(this, SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))).buildAsync()
        f.addListener({ runCatching { f.get().apply { setMediaItems(items, index, 0L); prepare(); play() } } }, mainExecutor)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_SONGS = "songs"
        const val SECTION_PLAYLISTS = "playlists"
        const val SECTION_FOLDERS = "folders"
        const val SECTION_ARTISTS = "artists"
        const val SECTION_ALBUMS = "albums"
        fun open(context: Context, section: String) = context.startActivity(Intent(context, AudioLibraryPageActivity::class.java).putExtra(EXTRA_SECTION, section))
    }
}
