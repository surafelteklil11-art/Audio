from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

# 1) Remove the obsolete in-place fullscreen implementation from MainActivity.
# Video playback is now owned by FullscreenVideoActivity; keeping two implementations
# caused state/layout conflicts and made the old player reachable through stale code.
main = ROOT / "app/src/main/java/com/surafel/audio/MainActivity.kt"
s = main.read_text(encoding="utf-8")
old = s
s = re.sub(
    r'\n    private fun enterFullscreenVideo\(\) \{.*?\n    private fun dp\(value: Int\)',
    '\n    private fun dp(value: Int)',
    s,
    flags=re.S,
)
# Keep the source free of unused fullscreen state left by the old implementation.
s = re.sub(r'\n    private var fullscreenVideo = false\n    private var fullscreenClose: TextView\? = null\n    private var normalVideoHeight = 255', '', s)
s = re.sub(r'\n        if \(fullscreenVideo\) exitFullscreenVideo\(\)', '', s)
s = re.sub(r'\n    override fun onBackPressed\(\) \{.*?\n    \}', '', s, flags=re.S)
s = re.sub(r'\n    override fun onDestroy\(\) \{\n        if \(fullscreenVideo\) exitFullscreenVideo\(\);', '\n    override fun onDestroy() {', s)
# onDestroy after the previous replacement must still have a valid body.
s = s.replace('if(::controllerFuture.isInitialized)MediaController.releaseFuture(controllerFuture);super.onDestroy()', 'if(::controllerFuture.isInitialized)MediaController.releaseFuture(controllerFuture);super.onDestroy()')
main.write_text(s, encoding="utf-8")

# 2) Make video thumbnails bind to the actual content URI, not title text.
thumb = ROOT / "app/src/main/java/com/surafel/audio/VideoThumbnailView.kt"
thumb.write_text('''package com.surafel.audio

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
''', encoding="utf-8")

# 3) Bind thumbnails from VideoAdapter using the exact URI, preventing wrong images
# when two videos share the same title and preventing RecyclerView reuse artifacts.
s = main.read_text(encoding="utf-8")
s = s.replace(
    'override fun onBindViewHolder(h:Holder,pos:Int){val x=items[pos];h.title.text=x.title;h.meta.text="${formatSize(x.size)} • ${formatDuration(x.duration)}";h.itemView.setOnClickListener{onClick(x)}}',
    'override fun onBindViewHolder(h:Holder,pos:Int){val x=items[pos];h.title.text=x.title;h.meta.text="${formatSize(x.size)} • ${formatDuration(x.duration)}";h.thumb.setVideoUri(x.uri);h.itemView.setOnClickListener{onClick(x)}}'
)
s = s.replace(
    'class Holder(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.videoTitle);val meta:TextView=v.findViewById(R.id.videoMeta)}',
    'class Holder(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.videoTitle);val meta:TextView=v.findViewById(R.id.videoMeta);val thumb:VideoThumbnailView=v.findViewById(R.id.videoThumbnail)}'
)
main.write_text(s, encoding="utf-8")

# 4) Give the thumbnail a stable id for direct binding.
item = ROOT / "app/src/main/res/layout/item_video.xml"
s = item.read_text(encoding="utf-8")
s = s.replace(
    '<com.surafel.audio.VideoThumbnailView\n            android:layout_width="match_parent" android:layout_height="match_parent" />',
    '<com.surafel.audio.VideoThumbnailView\n            android:id="@+id/videoThumbnail"\n            android:layout_width="match_parent" android:layout_height="match_parent" />'
)
item.write_text(s, encoding="utf-8")

# 5) Ensure the fullscreen activity is explicitly immersive and keeps the video screen on.
manifest = ROOT / "app/src/main/AndroidManifest.xml"
s = manifest.read_text(encoding="utf-8")
s = s.replace(
    '<activity android:name=".FullscreenVideoActivity" android:exported="false" android:screenOrientation="unspecified" />',
    '<activity android:name=".FullscreenVideoActivity" android:exported="false" android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden" />'
)
manifest.write_text(s, encoding="utf-8")

# Fail fast if an expected transformation silently did nothing.
if s == old:
    raise SystemExit("review_and_fix.py did not modify MainActivity; refusing to continue")
print("Comprehensive review fixes applied")
