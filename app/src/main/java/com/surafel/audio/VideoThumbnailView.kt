package com.surafel.audio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Size
import androidx.appcompat.widget.AppCompatImageView

class VideoThumbnailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private var boundUri: Uri? = null
    private var loadingUri: Uri? = null

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(0xFF101A38.toInt())
    }

    fun setVideoUri(uri: Uri?) {
        if (uri == boundUri && loadingUri == null) return
        boundUri = uri
        loadingUri = uri
        setImageDrawable(null)
        if (uri == null) {
            loadingUri = null
            return
        }
        val resolver = context.contentResolver
        Thread {
            var bitmap: Bitmap? = null
            try {
                bitmap = if (Build.VERSION.SDK_INT >= 29) {
                    resolver.loadThumbnail(uri, Size(640, 360), null)
                } else {
                    @Suppress("DEPRECATION")
                    val id = android.content.ContentUris.parseId(uri)
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        resolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                    )
                }
            } catch (_: Exception) {
                bitmap = null
            }
            post {
                if (boundUri == uri) {
                    if (bitmap != null) setImageBitmap(bitmap) else setImageDrawable(null)
                    loadingUri = null
                }
            }
        }.start()
    }
}
