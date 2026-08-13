package com.surafel.audio

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture

object MediaItemMenuInstaller {
    private const val TAG = "media_item_menu_installed"

    fun install(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() = attachAll(root, activity)
        })
        attachAll(root, activity)
    }

    private fun attachAll(view: View, activity: Activity) {
        if ((view.id == R.id.songMore || view.id == R.id.videoMore) && view.getTag(R.id.media_menu_installed_tag) != true) {
            view.setTag(R.id.media_menu_installed_tag, true)
            view.setOnClickListener { showMenu(activity, view, view.id == R.id.videoMore) }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) attachAll(view.getChildAt(i), activity)
    }

    private fun showMenu(activity: Activity, moreView: View, isVideo: Boolean) {
        val item = resolveItem(moreView) ?: return
        val title = if (item is MediaItem) item.mediaMetadata.title?.toString() ?: "Unknown" else (item as VideoEntry).title
        val uri = if (item is MediaItem) item.localConfiguration?.uri else (item as VideoEntry).uri
        val actions = if (isVideo) arrayOf("Play next", "Rename", "Share", "Details", "Delete from device") else arrayOf("Play next", "Add to queue", "Rename", "Share", "Details", "Delete from device")
        android.app.AlertDialog.Builder(activity).setTitle(title).setItems(actions) { _, which ->
            if (uri == null) return@setItems
            when {
                !isVideo && which == 0 -> playNext(activity, item as MediaItem)
                !isVideo && which == 1 -> addToQueue(activity, item as MediaItem)
                (!isVideo && which == 2) || (isVideo && which == 1) -> rename(activity, uri, title)
                (!isVideo && which == 3) || (isVideo && which == 2) -> share(activity, uri, title, if (isVideo) "video/*" else "audio/*")
                (!isVideo && which == 4) || (isVideo && which == 3) -> details(activity, uri, title)
                (!isVideo && which == 5) || (isVideo && which == 4) -> deleteFromDevice(activity, uri, title)
            }
        }.show()
    }

    private fun playNext(activity: Activity, item: MediaItem) = withController(activity) { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(item); c.prepare(); c.play() }
        else c.addMediaItem((c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount), item)
    }

    private fun addToQueue(activity: Activity, item: MediaItem) = withController(activity) { it.addMediaItem(item) }

    private fun withController(activity: Activity, action: (MediaController) -> Unit) {
        val token = SessionToken(activity, ComponentName(activity, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(activity, token).buildAsync()
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
        val message = activity.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.MIME_TYPE), null, null, null)?.use { c ->
            if (c.moveToFirst()) "Name: ${c.getString(0) ?: title}\nSize: ${formatSize(c.getLong(1))}\nType: ${c.getString(2) ?: "Unknown"}" else title
        } ?: title
        android.app.AlertDialog.Builder(activity).setTitle("Details").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun deleteFromDevice(activity: Activity, uri: Uri, title: String) {
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

    private fun resolveItem(moreView: View): Any? {
        var item: View? = moreView
        while (item != null && item.parent !is RecyclerView) item = item.parent as? View
        val recycler = item?.parent as? RecyclerView ?: return null
        val holder = recycler.getChildViewHolder(item) ?: return null
        val pos = holder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return null
        return runCatching {
            val field = holder.bindingAdapter?.javaClass?.getDeclaredField("items") ?: return null
            field.isAccessible = true
            (field.get(holder.bindingAdapter) as? List<*>)?.getOrNull(pos)
        }.getOrNull()
    }
}
