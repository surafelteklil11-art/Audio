package com.surafel.audio

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
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

class SearchActivity : AppCompatActivity() {
    private lateinit var input: TextView
    private lateinit var results: RecyclerView
    private lateinit var empty: View
    private lateinit var adapter: SearchAdapter
    private val data = mutableListOf<SearchResult>()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        input = findViewById(R.id.searchInput)
        results = findViewById(R.id.searchResults)
        empty = findViewById(R.id.emptyState)
        adapter = SearchAdapter(data) { openResult(it) }
        results.layoutManager = LinearLayoutManager(this)
        results.adapter = adapter

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        input.setOnEditorActionListener { _, _, _ -> search(input.text.toString()); true }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { search(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        input.requestFocus()
        input.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 180)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({ player = controllerFuture?.get() }, mainExecutor)
    }

    private fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            data.clear()
            adapter.notifyDataSetChanged()
            results.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }

        data.clear()
        if (hasAudioPermission()) {
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
            contentResolver.query(base, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (c.moveToNext()) {
                    val t = c.getString(title) ?: "Unknown"
                    val a = c.getString(artist) ?: "Unknown artist"
                    if (t.contains(q, true) || a.contains(q, true)) data += SearchResult(ContentUris.withAppendedId(base, c.getLong(id)), t, a, false)
                }
            }
        }
        if (hasVideoPermission()) {
            val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION)
            contentResolver.query(base, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val duration = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (c.moveToNext()) {
                    val t = c.getString(title) ?: "Video"
                    if (t.contains(q, true)) data += SearchResult(ContentUris.withAppendedId(base, c.getLong(id)), t, formatDuration(c.getLong(duration)), true)
                }
            }
        }
        adapter.notifyDataSetChanged()
        empty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        results.visibility = if (data.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openResult(item: SearchResult) {
        if (item.video) {
            startActivity(Intent(this, FullscreenVideoActivity::class.java).apply {
                putExtra(FullscreenVideoActivity.EXTRA_VIDEO_URI, item.uri.toString())
                putExtra(FullscreenVideoActivity.EXTRA_VIDEO_TITLE, item.title)
            })
        } else {
            player?.let {
                val media = MediaItem.Builder().setUri(item.uri).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(item.title).setArtist(item.subtitle).build()
                ).build()
                it.setMediaItem(media)
                it.prepare()
                it.play()
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasVideoPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    private fun formatDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format("%02d:%02d", s / 60, s % 60)
    }
}

data class SearchResult(val uri: Uri, val title: String, val subtitle: String, val video: Boolean)

private class SearchAdapter(private val items: List<SearchResult>, private val onClick: (SearchResult) -> Unit) : RecyclerView.Adapter<SearchAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.icon.text = if (item.video) "▶" else "♪"
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
        holder.itemView.setOnClickListener { onClick(item) }
    }
    override fun getItemCount() = items.size
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.searchIcon)
        val title: TextView = view.findViewById(R.id.searchTitle)
        val subtitle: TextView = view.findViewById(R.id.searchSubtitle)
    }
}
