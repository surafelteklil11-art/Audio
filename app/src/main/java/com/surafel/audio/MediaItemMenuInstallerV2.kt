package com.surafel.audio

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

object MediaItemMenuInstallerV2 {
    private const val INSTALLED_TAG = "normal_media_overflow_installed"

    fun install(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() { bindAll(root, activity) }
        })
        bindAll(root, activity)
    }

    private fun bindAll(root: View, activity: Activity) {
        if (root !is ViewGroup) return
        findViews(root, R.id.songMore).forEach { v ->
            if (v.getTag(R.id.songMore) != INSTALLED_TAG) {
                v.setTag(R.id.songMore, INSTALLED_TAG)
                v.setOnClickListener { audioMenu(activity, v) }
            }
        }
        findViews(root, R.id.videoMore).forEach { v ->
            if (v.getTag(R.id.videoMore) != INSTALLED_TAG) {
                v.setTag(R.id.videoMore, INSTALLED_TAG)
                v.setOnClickListener { videoMenu(activity, v) }
            }
        }
    }

    private fun findViews(parent: ViewGroup, id: Int): List<View> {
        val out = ArrayList<View>()
        for (i in 0 until parent.childCount) {
            val c = parent.getChildAt(i)
            if (c.id == id) out += c
            if (c is ViewGroup) out += findViews(c, id)
        }
        return out
    }

    private fun textInRow(more: View, id: Int): String? {
        var p: View? = more
        while (p != null) {
            if (p is ViewGroup) findText(p, id)?.let { if (it.isNotBlank()) return it }
            p = p.parent as? View
        }
        return null
    }

    private fun findText(parent: ViewGroup, id: Int): String? {
        for (i in 0 until parent.childCount) {
            val c = parent.getChildAt(i)
            if (c.id == id && c is TextView) return c.text?.toString()
            if (c is ViewGroup) findText(c, id)?.let { if (it.isNotBlank()) return it }
        }
        return null
    }

    private fun audioMenu(activity: Activity, more: View) {
        val title = textInRow(more, R.id.songTitle) ?: return
        val artist = textInRow(more, R.id.songArtist) ?: "Unknown artist"
        val uri = findAudio(activity, title, artist) ?: run { Toast.makeText(activity, "Audio file not found", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(activity).setTitle(title).setItems(arrayOf("Play next", "Add to queue", "Rename", "Share", "Details")) { _, which ->
            val item = MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()
            when (which) {
                0 -> playNext(activity, item)
                1 -> addToQueue(activity, item)
                2 -> rename(activity, uri, title)
                3 -> share(activity, uri, title, "audio/*")
                4 -> details(activity, uri, title)
            }
        }.show()
    }

    private fun videoMenu(activity: Activity, more: View) {
        val title = textInRow(more, R.id.videoTitle) ?: return
        val uri = findVideo(activity, title) ?: run { Toast.makeText(activity, "Video file not found", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(activity).setTitle(title).setItems(arrayOf("Play next", "Rename", "Share", "Details")) { _, which ->
            val item = MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()
            when (which) {
                0 -> playNext(activity, item)
                1 -> rename(activity, uri, title)
                2 -> share(activity, uri, title, "video/*")
                3 -> details(activity, uri, title)
            }
        }.show()
    }

    private fun findAudio(activity: Activity, title: String, artist: String): Uri? {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        activity.contentResolver.query(base, arrayOf(MediaStore.Audio.Media._ID), "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?", arrayOf(title, artist), null)?.use { if (it.moveToFirst()) return ContentUris.withAppendedId(base, it.getLong(0)) }
        return null
    }

    private fun findVideo(activity: Activity, title: String): Uri? {
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        activity.contentResolver.query(base, arrayOf(MediaStore.Video.Media._ID), "${MediaStore.Video.Media.TITLE} = ?", arrayOf(title), null)?.use { if (it.moveToFirst()) return ContentUris.withAppendedId(base, it.getLong(0)) }
        return null
    }

    private fun controller(activity: Activity, action: (MediaController) -> Unit) {
        val future = MediaController.Builder(activity, SessionToken(activity, ComponentName(activity, PlaybackService::class.java))).buildAsync()
        future.addListener({ runCatching { future.get().also(action).release() } }, activity.mainExecutor)
    }

    private fun playNext(activity: Activity, item: MediaItem) = controller(activity) { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(item); c.prepare() }
        else c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtLeast(0).coerceAtMost(c.mediaItemCount), item)
    }

    private fun addToQueue(activity: Activity, item: MediaItem) = controller(activity) { it.addMediaItem(item) }

    private fun rename(activity: Activity, uri: Uri, current: String) {
        val input = EditText(activity).apply { setSingleLine(true); setText(current.substringBeforeLast('.', current)); selectAll() }
        AlertDialog.Builder(activity).setTitle("Rename").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Rename") { _, _ ->
            val base = input.text.toString().trim(); if (base.isEmpty()) return@setPositiveButton
            val ext = current.substringAfterLast('.', "")
            activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
                putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_RENAME)
                putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
                putExtra(MediaActionActivity.EXTRA_NAME, if (ext.isEmpty()) base else "$base.$ext")
            })
        }.show()
    }

    private fun share(activity: Activity, uri: Uri, title: String, mime: String) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TITLE, title); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share $title"))
    }

    private fun details(activity: Activity, uri: Uri, title: String) {
        val message = activity.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE), null, null, null)?.use { if (it.moveToFirst()) "Name: ${it.getString(0) ?: title}\nSize: ${formatSize(it.getLong(1))}\nType: ${it.getString(2) ?: "Unknown"}" else title } ?: title
        AlertDialog.Builder(activity).setTitle("Details").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1024) String.format("%.1f MB", mb) else String.format("%.1f GB", mb / 1024)
    }
}
