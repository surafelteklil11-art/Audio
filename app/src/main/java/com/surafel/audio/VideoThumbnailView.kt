package com.surafel.audio

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Size

class VideoThumbnailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {
    private var loadedTitle = ""
    private var loading = false

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(0xFF101A38.toInt())
        post { refreshThumbnail() }
    }

    override fun onDraw(canvas: Canvas) {
        refreshThumbnail()
        super.onDraw(canvas)
    }

    private fun refreshThumbnail() {
        val parentGroup = parent as? android.view.ViewGroup ?: return
        val titleView = parentGroup.findViewWithTag<android.widget.TextView>("video_title") ?: return
        val title = titleView.text?.toString()?.trim().orEmpty()
        if (title.isEmpty() || title == loadedTitle || loading) return
        loadedTitle = title
        loading = true
        Thread {
            var bitmap: Bitmap? = null
            runCatching {
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.TITLE} = ?",
                    arrayOf(title),
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        bitmap = if (android.os.Build.VERSION.SDK_INT >= 29) {
                            contentResolver.loadThumbnail(uri, Size(640, 360), null)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Video.Thumbnails.getThumbnail(contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                        }
                    }
                }
            }
            post {
                loading = false
                if (bitmap != null) setImageBitmap(bitmap) else setImageDrawable(null)
            }
        }.start()
    }
}
