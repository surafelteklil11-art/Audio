from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

# Deep-review pass: remove every trace of the obsolete in-place video player.
# FullscreenVideoActivity is the single owner of fullscreen video playback.
main = ROOT / "app/src/main/java/com/surafel/audio/MainActivity.kt"
s = main.read_text(encoding="utf-8")
original = s

# Remove old fullscreen state regardless of whitespace/one-line formatting.
s = re.sub(r'\s*private var fullscreenVideo\s*=\s*false', '', s)
s = re.sub(r'\s*private var fullscreenClose:\s*TextView\?\s*=\s*null', '', s)
s = re.sub(r'\s*private var normalVideoHeight\s*=\s*\d+', '', s)

# Remove obsolete enter/exit fullscreen functions, if present.
s = re.sub(
    r'\s*private fun enterFullscreenVideo\s*\(\)\s*\{.*?\n\s*\}\s*(?=private fun dp)',
    '\n    ', s, flags=re.S
)
s = re.sub(
    r'\s*private fun exitFullscreenVideo\s*\(\)\s*\{.*?\n\s*\}\s*(?=private fun dp)',
    '\n    ', s, flags=re.S
)

# Remove stale lifecycle references in both one-line and multiline forms.
s = re.sub(r'\s*if\s*\(\s*fullscreenVideo\s*\)\s*\{?\s*exitFullscreenVideo\s*\(\s*\)\s*\}?', '', s)
s = re.sub(r'\s*if\s*\(\s*fullscreenVideo\s*\)\s*exitFullscreenVideo\s*\(\s*\)', '', s)
s = re.sub(r'\s*override fun onBackPressed\s*\(\s*\)\s*\{.*?\}', '', s, flags=re.S)

# If the old onDestroy body was damaged by previous automation, normalize it.
s = re.sub(
    r'override fun onDestroy\s*\(\s*\)\s*\{.*?\n\s*\}',
    'override fun onDestroy() {\n        if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture)\n        super.onDestroy()\n    }',
    s, count=1, flags=re.S
)

# Ensure the adapter always binds the real video URI and thumbnail view.
s = s.replace(
    'override fun onBindViewHolder(h:Holder,pos:Int){val x=items[pos];h.title.text=x.title;h.meta.text="${formatSize(x.size)} • ${formatDuration(x.duration)}";h.itemView.setOnClickListener{onClick(x)}}',
    'override fun onBindViewHolder(h:Holder,pos:Int){val x=items[pos];h.title.text=x.title;h.meta.text="${formatSize(x.size)} • ${formatDuration(x.duration)}";h.thumb.setVideoUri(x.uri);h.itemView.setOnClickListener{onClick(x)}}'
)
s = s.replace(
    'class Holder(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.videoTitle);val meta:TextView=v.findViewById(R.id.videoMeta)}',
    'class Holder(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.videoTitle);val meta:TextView=v.findViewById(R.id.videoMeta);val thumb:VideoThumbnailView=v.findViewById(R.id.videoThumbnail)}'
)

main.write_text(s, encoding="utf-8")

# Thumbnail implementation: asynchronous, URI-bound and RecyclerView-safe.
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

    init {
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(0xFF101A38.toInt())
    }

    fun setVideoUri(uri: Uri?) {
        if (uri == boundUri) return
        boundUri = uri
        setImageDrawable(null)
        if (uri == null) return

        val resolver = context.contentResolver
        Thread {
            val bitmap: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    resolver.loadThumbnail(uri, Size(640, 360), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        resolver,
                        android.content.ContentUris.parseId(uri),
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                }
            } catch (_: Exception) { null }

            post {
                if (boundUri == uri) {
                    if (bitmap != null) setImageBitmap(bitmap) else setImageDrawable(null)
                }
            }
        }.start()
    }
}
''', encoding="utf-8")

# Stable thumbnail id.
item = ROOT / "app/src/main/res/layout/item_video.xml"
s_item = item.read_text(encoding="utf-8")
if 'android:id="@+id/videoThumbnail"' not in s_item:
    s_item = s_item.replace(
        '<com.surafel.audio.VideoThumbnailView',
        '<com.surafel.audio.VideoThumbnailView\n            android:id="@+id/videoThumbnail"',
        1
    )
item.write_text(s_item, encoding="utf-8")

# Fullscreen activity remains the only video player surface.
manifest = ROOT / "app/src/main/AndroidManifest.xml"
s_manifest = manifest.read_text(encoding="utf-8")
s_manifest = s_manifest.replace(
    '<activity android:name=".FullscreenVideoActivity" android:exported="false" android:screenOrientation="unspecified" />',
    '<activity android:name=".FullscreenVideoActivity" android:exported="false" android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden" />'
)
manifest.write_text(s_manifest, encoding="utf-8")

# Hard preflight: stale symbols must never reach Kotlin compilation again.
remaining = []
for token in ("fullscreenVideo", "enterFullscreenVideo", "exitFullscreenVideo", "fullscreenClose", "normalVideoHeight"):
    if token in main.read_text(encoding="utf-8"):
        remaining.append(token)
if remaining:
    raise SystemExit("Deep review preflight failed; stale fullscreen symbols remain: " + ", ".join(remaining))

if s == original:
    raise SystemExit("Deep review did not modify MainActivity; refusing to continue")

print("1000-level deep review preflight passed")
