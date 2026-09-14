package com.surafel.audio.video

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import kotlin.math.abs

/** The same texture and controls work in a fullscreen activity and an overlay window. */
class VideoPanel(context: Context, val session: VideoSession, private val compact: Boolean) : FrameLayout(context) {
    var onClose: (() -> Unit)? = null
    var onPopup: (() -> Unit)? = null
    var onFullscreen: (() -> Unit)? = null
    var onRotate: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onResize: ((Float) -> Unit)? = null
    var onCycleSize: (() -> Unit)? = null
    private val player get() = session.player
    private val handler = Handler(Looper.getMainLooper())
    private val video = AspectRatioFrameLayout(context).apply { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT }
    private val texture = TextureView(context)
    private val chrome = FrameLayout(context)
    private val top = row()
    private val bottom = column()
    private val middle = row()
    private val feedback = text("", 15f)
    private val title = text(session.title, if (compact) 10f else 16f)
    private val pause = button("Ⅱ", "Pause video") { if (player.playWhenReady) VideoSessions.pause(session) else VideoSessions.play(session) }
    private val speed = button("1×", "Playback speed") { cycleSpeed() }
    private val mute = button("♪", "Mute video") { VideoSessions.mute(session); update() }
    private val timeline = SeekBar(context)
    private val elapsed = text("00:00", 11f)
    private val duration = text("00:00", 11f)
    private val unlock = button("Unlock", "Unlock video controls") { locked = false; showControls() }
    private var locked = false
    private var disposed = false
    private var scrubbing = false
    private var downX = 0f; private var downY = 0f
    private var previousX = 0f; private var previousY = 0f
    private var startPosition = 0L; private var previewPosition: Long? = null
    private var startVolume = 0; private var startBrightness = .5f
    private var gesture = 0
    private val hide = Runnable { if (!locked && player.isPlaying && !scrubbing) chrome.visibility = GONE }
    private val tick = object : Runnable { override fun run() { if (!disposed) { update(); handler.postDelayed(this, 300) } } }
    private val changed: () -> Unit = { update() }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { update() }
        override fun onVideoSizeChanged(size: VideoSize) { updateAspect(size) }
        override fun onRenderedFirstFrame() { session.renderedFrames++ }
        override fun onPlayerError(error: PlaybackException) {
            showControls(); feedback.text = "Cannot play this video. Close another window or tap Play to retry."; feedback.visibility = VISIBLE
        }
    }
    init {
        setBackgroundColor(Color.BLACK); clipChildren = true
        video.addView(texture, LayoutParams(-1, -1)); addView(video, LayoutParams(-1, -1, Gravity.CENTER))
        val touch = View(context).apply {
            contentDescription = if (compact) "Drag popup video; tap for controls" else "Video gestures: swipe horizontally to seek, left vertically for brightness, right for volume"
            setOnTouchListener { _, event -> handleTouch(event) }
        }
        addView(touch, LayoutParams(-1, -1)); addView(chrome, LayoutParams(-1, -1))
        val controlHeight = if (compact) 28 else 48
        top.setBackgroundColor(0x66000000); top.setPadding(dp(3), 0, dp(3), 0)
        if (!compact) top.addView(button("‹", "Close video") { onClose?.invoke() }, linear(44, 48))
        top.addView(title, LinearLayout.LayoutParams(0, dp(controlHeight), 1f))
        top.addView(speed, linear(if (compact) 32 else 48, controlHeight))
        if (compact) {
            top.addView(mute, linear(28, controlHeight)); top.addView(button("×", "Close popup") { onClose?.invoke() }, linear(28, controlHeight))
        } else {
            top.addView(button("Popup", "Open popup video") { onPopup?.invoke() }, linear(64, 48))
        }
        chrome.addView(top, LayoutParams(-1, dp(controlHeight), Gravity.TOP))
        if (!compact) middle.addView(button("↶10", "Back ten seconds") { seekBy(-10000) }, linear(60, 52))
        middle.addView(pause, linear(if (compact) 36 else 64, if (compact) 36 else 52))
        if (!compact) middle.addView(button("10↷", "Forward ten seconds") { seekBy(10000) }, linear(60, 52))
        middle.addView(button("▶|", "Play next queued video") {
            if (!VideoSessions.next(context, session)) Toast.makeText(context, "Long-press a video in Audio and choose Play next", Toast.LENGTH_SHORT).show()
        }, linear(if (compact) 36 else 52, if (compact) 36 else 52))
        middle.gravity = Gravity.CENTER; chrome.addView(middle, LayoutParams(-2, -2, Gravity.CENTER))
        bottom.setBackgroundColor(0x88000000.toInt())
        val progressRow = row()
        timeline.max = 1000; timeline.contentDescription = "Video progress"; timeline.setPadding(dp(2), 0, dp(2), 0)
        timeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) { scrubbing = true; handler.removeCallbacks(hide) }
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser && player.duration > 0) player.seekTo(player.duration * value / 1000) }
            override fun onStopTrackingTouch(bar: SeekBar?) { scrubbing = false; showControls() }
        })
        if (compact) {
            val resize = button("↗", "Resize popup") { onCycleSize?.invoke() }
            var x = 0f; var moved = false
            resize.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { x = e.rawX; moved = false }
                    MotionEvent.ACTION_MOVE -> { val dx = e.rawX - x; if (abs(dx) > dp(2)) { moved = true; onResize?.invoke(dx); x = e.rawX } }
                    MotionEvent.ACTION_UP -> if (!moved) resize.performClick()
                }; true
            }
            progressRow.addView(resize, linear(28, 28))
        } else progressRow.addView(elapsed, linear(58, 36))
        progressRow.addView(timeline, LinearLayout.LayoutParams(0, dp(if (compact) 28 else 36), 1f))
        if (compact) progressRow.addView(button("⛶", "Return to fullscreen") { onFullscreen?.invoke() }, linear(28, 28))
        else progressRow.addView(duration, linear(58, 36))
        bottom.addView(progressRow)
        if (!compact) {
            val extras = row()
            extras.addView(mute, LinearLayout.LayoutParams(0, dp(44), 1f))
            extras.addView(button("Fit", "Video aspect ratio") {
                video.resizeMode = when (video.resizeMode) { AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                showFeedback(when (video.resizeMode) { AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop to fill"; else -> "Stretch" })
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            extras.addView(button("Rotate", "Rotate video screen") { onRotate?.invoke() }, LinearLayout.LayoutParams(0, dp(44), 1f))
            extras.addView(button("Lock", "Lock video controls") { locked = true; showControls() }, LinearLayout.LayoutParams(0, dp(44), 1f))
            bottom.addView(extras)
        }
        chrome.addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM))
        chrome.addView(unlock, LayoutParams(dp(80), dp(48), Gravity.END or Gravity.CENTER_VERTICAL)); unlock.visibility = GONE
        feedback.maxLines = 3; feedback.textSize = if (compact) 11f else 15f
        feedback.setBackgroundColor(0xAA000000.toInt()); feedback.setPadding(dp(8), dp(4), dp(8), dp(4)); feedback.visibility = GONE
        addView(feedback, LayoutParams(-2, -2, Gravity.CENTER))
        player.addListener(listener); VideoSessions.observers.add(changed); player.setVideoTextureView(texture)
        updateAspect(player.videoSize); handler.post(tick); showControls()
    }
    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { if (!locked) { if (chrome.visibility == VISIBLE) chrome.visibility = GONE else showControls() }; return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { if (!locked) { seekBy(if (e.x < width / 2) -10000 else 10000); showFeedback(if (e.x < width / 2) "−10 seconds" else "+10 seconds") }; return true }
    })
    private fun handleTouch(event: MotionEvent): Boolean {
        if (locked) return true
        taps.onTouchEvent(event)
        val audio = context.getSystemService(AudioManager::class.java)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; previousX = event.rawX; previousY = event.rawY; gesture = 0
                startPosition = player.currentPosition; previewPosition = null
                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                startBrightness = (context as? Activity)?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: .5f
                handler.removeCallbacks(hide)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX; val dy = event.y - downY
                if (compact) { if (abs(event.rawX - previousX) + abs(event.rawY - previousY) > dp(2)) onDrag?.invoke(event.rawX - previousX, event.rawY - previousY) }
                else {
                    if (gesture == 0 && abs(dx) + abs(dy) > dp(12)) gesture = if (abs(dx) > abs(dy)) 1 else if (downX < width / 2) 2 else 3
                    when (gesture) {
                        1 -> if (player.duration > 0) { previewPosition = (startPosition + dx / width * 120000).toLong().coerceIn(0, player.duration); showFeedback(formatTime(previewPosition!!)) }
                        2 -> (context as? Activity)?.window?.let { w -> val value = (startBrightness - dy / height).coerceIn(.02f, 1f); w.attributes = w.attributes.apply { screenBrightness = value }; showFeedback("Brightness ${(value * 100).toInt()}%") }
                        3 -> { val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val value = (startVolume - dy / height * max).toInt().coerceIn(0, max); audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); showFeedback("Volume ${(value * 100 / max.coerceAtLeast(1))}%") }
                    }
                }
                previousX = event.rawX; previousY = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) previewPosition?.let { player.seekTo(it) }
                previewPosition = null; handler.postDelayed(hide, 2600)
            }
        }
        return true
    }
    fun showControls() {
        chrome.visibility = VISIBLE; top.visibility = if (locked) GONE else VISIBLE; bottom.visibility = if (locked) GONE else VISIBLE
        middle.visibility = if (locked) GONE else VISIBLE; unlock.visibility = if (locked) VISIBLE else GONE
        handler.removeCallbacks(hide); if (!locked) handler.postDelayed(hide, 2600)
    }
    fun attachVideo() { if (!disposed) player.setVideoTextureView(texture) }
    fun setSafePadding(left: Int, top: Int, right: Int, bottom: Int) { chrome.setPadding(left, top, right, bottom) }
    private fun updateAspect(size: VideoSize) { video.setAspectRatio(if (size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 16f / 9) }
    private fun update() {
        if (disposed) return
        pause.text = if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) "Ⅱ" else "▶"
        pause.contentDescription = if (player.playWhenReady) "Pause video" else "Play video"
        if (player.playerError != null) pause.setOnClickListener { player.prepare(); VideoSessions.play(session); feedback.visibility = GONE }
        else pause.setOnClickListener { if (player.playWhenReady) VideoSessions.pause(session) else VideoSessions.play(session) }
        speed.text = "${player.playbackParameters.speed}×"; mute.text = if (session.muted) "×♪" else "♪"
        mute.contentDescription = if (session.muted) "Unmute video" else "Mute video"
        title.text = if (compact) player.videoSize.height.takeIf { it > 0 }?.let { "${it}p" } ?: "Video" else session.title
        val total = player.duration.coerceAtLeast(0); elapsed.text = formatTime(player.currentPosition); duration.text = formatTime(total)
        if (!scrubbing) timeline.progress = if (total > 0) (player.currentPosition * 1000 / total).toInt() else 0
        timeline.isEnabled = total > 0 && player.isCurrentMediaItemSeekable
    }
    private fun cycleSpeed() { val choices = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f); val index = choices.indexOf(player.playbackParameters.speed); player.setPlaybackSpeed(choices[(index + 1) % choices.size]); showControls() }
    private fun seekBy(delta: Long) { if (player.isCurrentMediaItemSeekable) player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0))); showControls() }
    private fun showFeedback(message: String) { feedback.text = message; feedback.visibility = VISIBLE; handler.postDelayed({ if (player.playerError == null) feedback.visibility = GONE }, 900) }
    fun dispose() { if (disposed) return; disposed = true; handler.removeCallbacksAndMessages(null); VideoSessions.observers.remove(changed); player.removeListener(listener); player.clearVideoTextureView(texture) }
    private fun text(value: String, size: Float) = TextView(context).apply { text = value; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private fun button(value: String, description: String, action: () -> Unit) = text(value, if (compact) 12f else 16f).apply { contentDescription = description; isClickable = true; isFocusable = true; setOnClickListener { action(); showControls() } }
    private fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun linear(w: Int, h: Int) = LinearLayout.LayoutParams(dp(w), dp(h))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { fun formatTime(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60) } }
}
