package com.surafel.audio

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.WeakHashMap

/**
 * Binds the normal Audio/Video row overflow buttons after RecyclerView has
 * actually created its children. The previous implementation only scanned
 * once during Activity resume, which happened before rows were inflated, so
 * the visible ⋮ buttons had no click listener.
 */
object MediaItemMenuInstaller {
    private val installed = WeakHashMap<View, Boolean>()
    private val observers = WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener>()

    fun install(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return

        bindAll(root, activity)

        if (observers[root] == null) {
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                if (!activity.isFinishing && !activity.isDestroyed) bindAll(root, activity)
            }
            observers[root] = listener
            root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }

        root.post { bindAll(root, activity) }
    }

    private fun bindAll(root: View, activity: Activity) {
        if (!root.isAttachedToWindow) return
        attach(root, activity)
    }

    private fun attach(view: View, activity: Activity) {
        if (view.id == R.id.songMore || view.id == R.id.videoMore) {
            if (installed[view] != true) {
                installed[view] = true
                view.isClickable = true
                view.isFocusable = true
                view.setOnClickListener { clicked ->
                    clicked.isPressed = true
                    showMenu(activity, clicked, clicked.id == R.id.videoMore)
                    clicked.postDelayed({ clicked.isPressed = false }, 120)
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) attach(view.getChildAt(i), activity)
        }
    }

    private fun showMenu(activity: Activity, more: View, video: Boolean) {
        val row = more.parent as? ViewGroup ?: return
        val titleId = if (video) R.id.videoTitle else R.id.songTitle
        val titleView = findView(row, titleId) as? TextView
        val title = titleView?.text?.toString()?.trim().orEmpty().ifEmpty { "Unknown" }
        val uri = findMediaUri(activity, title, video)

        if (uri == null) {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage("This media item is no longer available on the device.")
                .setPositiveButton("OK", null)
                .show()
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
                    which == 0 -> playNext(activity, uri, title)
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
            for (i in 0 until parent.childCount) {
                findView(parent.getChildAt(i), id)?.let { return it }
            }
        }
        return null
    }

    private fun findMediaUri(activity: Activity, title: String, video: Boolean): Uri? {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE
        )
        val selection = "${MediaStore.MediaColumns.TITLE} = ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        return activity.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf(title, title),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Uri.withAppendedPath(collection, cursor.getLong(0).toString())
            } else null
        }
    }

    private fun controller(activity: Activity, action: (MediaController) -> Unit) {
        val future: ListenableFuture<MediaController> = MediaController.Builder(
            activity,
            SessionToken(activity, ComponentName(activity, PlaybackService::class.java))
        ).buildAsync()
        future.addListener({
            runCatching {
                val controller = future.get()
                action(controller)
                controller.release()
            }
        }, activity.mainExecutor)
    }

    private fun mediaItem(uri: Uri, title: String) = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
        .build()

    private fun playNext(activity: Activity, uri: Uri, title: String) = controller(activity) { player ->
        val item = mediaItem(uri, title)
        val index = if (player.mediaItemCount == 0) 0 else
            (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
        player.addMediaItem(index, item)
        if (!player.isPlaying && player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    private fun addToQueue(activity: Activity, uri: Uri, title: String) =
        controller(activity) { it.addMediaItem(mediaItem(uri, title)) }

    private fun rename(activity: Activity, uri: Uri, current: String) {
        val dot = current.lastIndexOf('.')
        val base = if (dot > 0) current.substring(0, dot) else current
        val extension = if (dot > 0) current.substring(dot) else ""
        val input = EditText(activity).apply {
            setSingleLine(true)
            setText(base)
            selectAll()
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val requested = input.text.toString().trim()
                if (requested.isNotEmpty()) {
                    activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
                        putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_RENAME)
                        putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
                        putExtra(MediaActionActivity.EXTRA_NAME, requested + extension)
                    })
                }
            }
            .show()
    }

    private fun share(activity: Activity, uri: Uri, title: String, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Share $title"))
    }

    private fun details(activity: Activity, uri: Uri, title: String) {
        val message = activity.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                "Name: ${cursor.getString(0) ?: title}\n" +
                    "Size: ${formatSize(cursor.getLong(1))}\n" +
                    "Type: ${cursor.getString(2) ?: "Unknown"}"
            } else title
        } ?: title

        AlertDialog.Builder(activity)
            .setTitle("Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun delete(activity: Activity, uri: Uri, title: String) {
        AlertDialog.Builder(activity)
            .setTitle("Delete from device?")
            .setMessage("\"$title\" will be removed from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
                    putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_DELETE)
                    putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
                    putExtra(MediaActionActivity.EXTRA_NAME, title)
                })
            }
            .show()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1024) String.format("%.1f MB", mb)
        else String.format("%.1f GB", mb / 1024)
    }
}
