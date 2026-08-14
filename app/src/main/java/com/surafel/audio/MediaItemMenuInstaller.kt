package com.surafel.audio

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Installs the overflow menu on every normal Audio/Video row without changing
 * the existing adapters or the Hidden/Vault implementation.
 */
object MediaItemMenuInstaller {
    private val installed = WeakHashMap<View, Boolean>()

    fun install(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        attach(root, activity)
    }

    private fun attach(view: View, activity: Activity) {
        if (view.id == R.id.songMore || view.id == R.id.videoMore) {
            if (installed[view] != true) {
                installed[view] = true
                view.setOnClickListener { showMenu(activity, view, view.id == R.id.videoMore) }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) attach(view.getChildAt(i), activity)
        }
    }

    private fun showMenu(activity: Activity, more: View, video: Boolean) {
        val row = more.parent as? ViewGroup ?: return
        val titleId = if (video) R.id.videoTitle else R.id.songTitle
        val titleView = findView(row, titleId) as? TextView ?: return
        val title = titleView.text?.toString()?.trim().orEmpty().ifEmpty { "Unknown" }
        val uri = findMediaUri(activity, title, video) ?: run {
            AlertDialog.Builder(activity).setTitle(title).setMessage("This media item is no longer available on the device.").setPositiveButton("OK", null).show()
            return
        }

        val actions = if (video) {
            arrayOf("Play next", "Rename", "Share", "Details", "Delete from device")
        } else {
            arrayOf("Play next", "Add to queue", "Rename", "Share", "Details", "Delete from device")
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(actions) { _, which ->
                when {
                    video && which == 0 -> playNext(activity, uri, title)
                    !video && which == 0 -> playNext(activity, uri, title)
                    !video && which == 1 -> addToQueue(activity, uri, title)
                    (video && which == 1) || (!video && which == 2) -> rename(activity, uri, title)
                    (video && which == 2) || (!video && which == 3) -> share(activity, uri, title, if (video) "video/*" else "audio/*")
                    (video && which == 3) || (!video && which == 4) -> details(activity, uri, title)
                    (video && which == 4) || (!video && which == 5) -> delete(activity, uri, title)
                }
            }
            .show()
    }

    private fun findView(parent: View, id: Int): View? {
        if (parent.id == id) return parent
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) findView(parent.getChildAt(i), id)?.let { return it }
        }
        return null
    }

    private fun findMediaUri(activity: Activity, title: String, video: Boolean): Uri? {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE)
        val selection = if (video) "${MediaStore.MediaColumns.DISPLAY_NAME} = ? OR ${MediaStore.MediaColumns.TITLE} = ?" else "${MediaStore.MediaColumns.TITLE} = ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        return activity.contentResolver.query(collection, projection, selection, arrayOf(title, title), null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
    }

    private fun controller(activity: Activity, action: (MediaController) -> Unit) {
        val token = SessionToken(activity, ComponentName(activity, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(activity, token).buildAsync()
        future.addListener({
            runCatching { future.get().also(action).release() }
        }, activity.mainExecutor)
    }

    private fun playNext(activity: Activity, uri: Uri, title: String) = controller(activity) { player ->
        val item = MediaItem.Builder().setUri(uri).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build()).build()
        val index = if (player.mediaItemCount == 0) 0 else (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
        player.addMediaItem(index, item)
        if (!player.isPlaying && player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    private fun addToQueue(activity: Activity, uri: Uri, title: String) = controller(activity) { player ->
        val item = MediaItem.Builder().setUri(uri).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build()).build()
        player.addMediaItem(item)
    }

    private fun rename(activity: Activity, uri: Uri, current: String) {
        val input = EditText(activity).apply {
            setSingleLine(true)
            setText(current.substringBeforeLast('.', current))
            selectAll()
        }
        AlertDialog.Builder(activity).setTitle("Rename").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val requested = input.text.toString().trim()
                if (requested.isEmpty()) return@setPositiveButton
                val extension = current.substringAfterLast('.', "").let { if (it.isNotEmpty() && !current.endsWith(".$it")) ".$it" else "" }
                val name = if (extension.isEmpty()) requested else "$requested$extension"
                activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
                    putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_RENAME)
                    putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
                    putExtra(MediaActionActivity.EXTRA_NAME, name)
                })
            }.show()
    }

    private fun share(activity: Activity, uri: Uri, title: String, mime: String) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share $title"))
    }

    private fun details(activity: Activity, uri: Uri, title: String) {
        val message = activity.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE), null, null, null)?.use { c ->
            if (c.moveToFirst()) "Name: ${c.getString(0) ?: title}\nSize: ${formatSize(c.getLong(1))}\nType: ${c.getString(2) ?: "Unknown"}" else title
        } ?: title
        AlertDialog.Builder(activity).setTitle("Details").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun delete(activity: Activity, uri: Uri, title: String) {
        AlertDialog.Builder(activity).setTitle("Delete from device?")
            .setMessage("\"$title\" will be removed from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
                    putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_DELETE)
                    putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
                    putExtra(MediaActionActivity.EXTRA_NAME, title)
                })
            }.show()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1024) String.format("%.1f MB", mb) else String.format("%.1f GB", mb / 1024)
    }

    private class WeakHashMap<K : Any, V> {
        private val map = java.util.WeakHashMap<K, V>()
        operator fun get(key: K): V? = map[key]
        operator fun set(key: K, value: V) { map[key] = value }
    }
}
