package com.surafel.audio

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture

object MediaItemMenuInstallerV2 {
    fun install(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                attachSongs(root, activity)
                attachVideos(root, activity)
            }
        })
        attachSongs(root, activity)
        attachVideos(root, activity)
    }

    private fun attachSongs(root: View, activity: Activity) {
        val view = root.findViewById<View>(R.id.songMore) ?: return
        if (view.getTag(R.id.media_menu_installed_tag) == true) return
        view.setTag(R.id.media_menu_installed_tag, true)
        view.setOnClickListener { showMenu(activity, view, false) }
    }

    private fun attachVideos(root: View, activity: Activity) {
        if (root !is ViewGroup) return
        val views = mutableListOf<View>()
        collect(root, R.id.videoMore, views)
        views.forEach { view ->
            if (view.getTag(R.id.media_menu_installed_tag) != true) {
                view.setTag(R.id.media_menu_installed_tag, true)
                view.setOnClickListener { showMenu(activity, view, true) }
            }
        }
    }

    private fun collect(parent: ViewGroup, id: Int, out: MutableList<View>) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.id == id) out += child
            if (child is ViewGroup) collect(child, id, out)
        }
    }

    private fun showMenu(activity: Activity, more: View, video: Boolean) {
        val item = resolveItem(more) ?: return
        val title: String
        val uri: Uri
        if (video) {
            if (item !is VideoEntry) return
            title = item.title
            uri = item.uri
        } else {
            if (item !is MediaItem) return
            title = item.mediaMetadata.title?.toString() ?: "Unknown"
            uri = item.localConfiguration?.uri ?: return
        }
        val actions = if (video) arrayOf("Play next", "Rename", "Share", "Details", "Remove from device") else arrayOf("Play next", "Add to queue", "Rename", "Share", "Details", "Remove from device")
        android.app.AlertDialog.Builder(activity).setTitle(title).setItems(actions) { _, which ->
            when {
                video && which == 0 -> {
                    VideoQueue.setNext(activity, item as VideoEntry)
                    Toast.makeText(activity, "Added as next video", Toast.LENGTH_SHORT).show()
                }
                !video && which == 0 -> playNext(activity, item as MediaItem)
                !video && which == 1 -> addToQueue(activity, item as MediaItem)
                (!video && which == 2) || (video && which == 1) -> rename(activity, uri, title)
                (!video && which == 3) || (video && which == 2) -> share(activity, uri, title, if (video) "video/*" else "audio/*")
                (!video && which == 4) || (video && which == 3) -> details(activity, uri, title)
                (!video && which == 5) || (video && which == 4) -> remove(activity, uri, title)
            }
        }.show()
    }

    private fun playNext(activity: Activity, item: MediaItem) = withController(activity) { controller ->
        if (controller.mediaItemCount == 0) {
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
        } else {
            val index = if (controller.currentMediaItemIndex >= 0) controller.currentMediaItemIndex + 1 else controller.mediaItemCount
            controller.addMediaItem(index.coerceIn(0, controller.mediaItemCount), item)
        }
    }

    private fun addToQueue(activity: Activity, item: MediaItem) = withController(activity) { it.addMediaItem(item) }

    private fun withController(activity: Activity, action: (MediaController) -> Unit) {
        val future: ListenableFuture<MediaController> = MediaController.Builder(activity, SessionToken(activity, ComponentName(activity, PlaybackService::class.java))).buildAsync()
        future.addListener({ runCatching { future.get().also(action).release() } }, activity.mainExecutor)
    }

    private fun rename(activity: Activity, uri: Uri, current: String) {
        val input = EditText(activity).apply { setSingleLine(true); setText(current.substringBeforeLast('.', current)); selectAll() }
        android.app.AlertDialog.Builder(activity).setTitle("Rename").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Rename") { _, _ ->
            val requested = input.text.toString().trim()
            if (requested.isNotEmpty()) activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
                putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_RENAME)
                putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
                putExtra(MediaActionActivity.EXTRA_NAME, if (current.contains('.')) "$requested.${current.substringAfterLast('.')}" else requested)
            })
        }.show()
    }

    private fun share(activity: Activity, uri: Uri, title: String, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TITLE, title); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        activity.startActivity(Intent.createChooser(intent, "Share $title"))
    }

    private fun details(activity: Activity, uri: Uri, title: String) {
        val text = activity.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.MIME_TYPE), null, null, null)?.use { c ->
            if (c.moveToFirst()) "Name: ${c.getString(0) ?: title}\nSize: ${formatSize(c.getLong(1))}\nType: ${c.getString(2) ?: "Unknown"}" else title
        } ?: title
        android.app.AlertDialog.Builder(activity).setTitle("Details").setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun remove(activity: Activity, uri: Uri, title: String) {
        activity.startActivity(Intent(activity, MediaActionActivity::class.java).apply {
            putExtra(MediaActionActivity.EXTRA_ACTION, MediaActionActivity.ACTION_DELETE)
            putExtra(MediaActionActivity.EXTRA_URI, uri.toString())
            putExtra(MediaActionActivity.EXTRA_NAME, title)
        })
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1024) String.format("%.1f MB", mb) else String.format("%.1f GB", mb / 1024)
    }

    private fun resolveItem(more: View): Any? {
        var item: View? = more
        while (item != null && item.parent !is RecyclerView) item = item.parent as? View
        val recycler = item?.parent as? RecyclerView ?: return null
        val holder = recycler.getChildViewHolder(item) ?: return null
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return null
        return runCatching {
            val adapter = holder.bindingAdapter ?: return@runCatching null
            val field = adapter.javaClass.getDeclaredField("items")
            field.isAccessible = true
            (field.get(adapter) as? List<*>)?.getOrNull(position)
        }.getOrNull()
    }
}
