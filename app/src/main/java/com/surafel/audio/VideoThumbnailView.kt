package com.surafel.audio

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Size
import androidx.appcompat.widget.AppCompatImageView
import java.util.concurrent.Executors

/** Loads a real frame for a video and safely ignores stale RecyclerView results. */
class VideoThumbnailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private var boundUri: Uri? = null
    private val executor = Executors.newCachedThreadPool()

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(0xFF101A38.toInt())
        contentDescription = "Video thumbnail"
    }

    fun setVideoUri(uri: Uri?) {
        boundUri = uri
        setImageDrawable(null)
        if (uri == null) return

        executor.execute {
            val bitmap = loadFrame(uri)
            post {
                if (boundUri == uri && !isDetached && bitmap != null) setImageBitmap(bitmap)
            }
        }
    }

    private fun loadFrame(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(640, 360), null)?.let { return it }
            } catch (_: Exception) { }
        }

        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT < 29) {
                try {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        android.content.ContentUris.parseId(uri),
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } catch (_: Exception) { null }
            } else null
        }
    }

    override fun onDetachedFromWindow() {
        boundUri = null
        super.onDetachedFromWindow()
    }
}
