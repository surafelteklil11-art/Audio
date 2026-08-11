package com.surafel.audio

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Size
import androidx.appcompat.widget.AppCompatImageView

class VideoThumbnailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
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
        var node: android.view.View? = this
        var titleView: android.widget.TextView? = null
        repeat(5) {
            val parent = node?.parent as? android.view.ViewGroup ?: return@repeat
            titleView = parent.findViewWithTag("video_title") as? android.widget.TextView
            if (titleView != null) return@repeat
            node = parent
        }

        val title = titleView?.text?.toString()?.trim().orEmpty()
        if (title.isEmpty() || title == loadedTitle || loading) return

        loadedTitle = title
        loading = true
        val resolver = context.contentResolver

        Thread {
            var bitmap: Bitmap? = null
            try {
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.TITLE} = ?",
                    arrayOf(title),
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        )
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        bitmap = if (android.os.Build.VERSION.SDK_INT >= 29) {
                            resolver.loadThumbnail(uri, Size(640, 360), null)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Video.Thumbnails.getThumbnail(
                                resolver,
                                id,
                                MediaStore.Video.Thumbnails.MINI_KIND,
                                null
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                bitmap = null
            }

            post {
                loading = false
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    setImageDrawable(null)
                }
            }
        }.start()
    }
}
