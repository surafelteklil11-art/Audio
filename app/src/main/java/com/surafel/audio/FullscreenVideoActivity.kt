package com.surafel.audio

import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

class FullscreenVideoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
    }

    private lateinit var video: VideoView
    private lateinit var videoHolder: FrameLayout
    private lateinit var pauseButton: TextView
    private lateinit var speedButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeLabel: TextView
    private var preparedPlayer: MediaPlayer? = null
    private var speed = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (::video.isInitialized && video.isPlaying || (::video.isInitialized && video.duration > 0)) {
                val duration = video.duration
                if (duration > 0) {
                    seekBar.max = duration
                    seekBar.progress = video.currentPosition.coerceIn(0, duration)
                    timeLabel.text = "${formatTime(video.currentPosition)} / ${formatTime(duration)}"
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // The holder fills the screen, while the actual VideoView is resized after
        // preparation to the source aspect ratio. This guarantees centered letterboxing
        // instead of a top-aligned/cropped video.
        videoHolder = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(videoHolder, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        video = VideoView(this).apply { setBackgroundColor(Color.BLACK) }
        videoHolder.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val top = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(145, 0, 0, 0))
            setPadding(12, 10, 12, 10)
        }
        val title = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        top.addView(title, FrameLayout.LayoutParams(0, 52, Gravity.START).apply { rightMargin = 64 })
        val close = TextView(this).apply {
            text = "✕"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "Close video"
            setOnClickListener { finish() }
        }
        top.addView(close, FrameLayout.LayoutParams(52, 52, Gravity.END))
        root.addView(top, FrameLayout.LayoutParams(-1, 72, Gravity.TOP))

        val bottom = FrameLayout(this).apply { setBackgroundColor(Color.argb(180, 0, 0, 0)) }
        seekBar = SeekBar(this).apply {
            max = 1
            progress = 0
            contentDescription = "Video progress"
            setPadding(8, 0, 8, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && ::video.isInitialized) video.seekTo(value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) { updatePause() }
            })
        }
        bottom.addView(seekBar, FrameLayout.LayoutParams(-1, 42, Gravity.TOP))

        timeLabel = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        bottom.addView(timeLabel, FrameLayout.LayoutParams(-1, 28, Gravity.TOP).apply { topMargin = 38 })

        val controls = FrameLayout(this)
        val back10 = control("↶ 10s") {
            if (::video.isInitialized) video.seekTo((video.currentPosition - 10_000).coerceAtLeast(0))
        }
        controls.addView(back10, FrameLayout.LayoutParams(82, 54, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = 4 })

        pauseButton = control("▶") {
            if (!::video.isInitialized) return@control
            if (video.isPlaying) video.pause() else video.start()
            updatePause()
        }
        controls.addView(pauseButton, FrameLayout.LayoutParams(64, 54, Gravity.CENTER))

        val forward10 = control("10s ↷") {
            if (::video.isInitialized) {
                val duration = video.duration.coerceAtLeast(0)
                video.seekTo((video.currentPosition + 10_000).coerceAtMost(duration))
            }
        }
        controls.addView(forward10, FrameLayout.LayoutParams(82, 54, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = 74 })

        speedButton = control("1.0×") { cycleSpeed() }
        controls.addView(speedButton, FrameLayout.LayoutParams(68, 54, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = 4 })
        bottom.addView(controls, FrameLayout.LayoutParams(-1, 58, Gravity.BOTTOM))
        root.addView(bottom, FrameLayout.LayoutParams(-1, 132, Gravity.BOTTOM))

        setContentView(root)

        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriString.isNullOrBlank()) {
            finish()
            return
        }

        video.setVideoURI(Uri.parse(uriString))
        video.setOnPreparedListener { player ->
            preparedPlayer = player
            player.isLooping = false
            applySpeed()
            resizeVideoToAspectRatio(player.videoWidth, player.videoHeight)
            seekBar.max = video.duration.coerceAtLeast(1)
            video.start()
            updatePause()
            handler.removeCallbacks(progressUpdater)
            handler.post(progressUpdater)
        }
        video.setOnCompletionListener {
            seekBar.progress = seekBar.max
            updatePause()
        }
        video.setOnErrorListener { _, _, _ ->
            // Do not leave a permanently black player if the source cannot be opened.
            timeLabel.text = "Unable to play this video"
            updatePause()
            true
        }
    }

    private fun resizeVideoToAspectRatio(sourceWidth: Int, sourceHeight: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        video.post {
            val availableWidth = videoHolder.width
            val availableHeight = videoHolder.height
            if (availableWidth <= 0 || availableHeight <= 0) return@post

            val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
            val availableRatio = availableWidth.toFloat() / availableHeight.toFloat()
            val width: Int
            val height: Int
            if (sourceRatio > availableRatio) {
                width = availableWidth
                height = (availableWidth / sourceRatio).roundToInt()
            } else {
                height = availableHeight
                width = (availableHeight * sourceRatio).roundToInt()
            }
            (video.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = width.coerceAtLeast(1)
                this.height = height.coerceAtLeast(1)
                gravity = Gravity.CENTER
                video.layoutParams = this
            }
        }
    }

    private fun control(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun updatePause() {
        if (::pauseButton.isInitialized) pauseButton.text = if (::video.isInitialized && video.isPlaying) "Ⅱ" else "▶"
    }

    private fun cycleSpeed() {
        speed = when (speed) { 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; else -> 1.0f }
        speedButton.text = "${speed}×"
        applySpeed()
    }

    private fun applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preparedPlayer?.let { player -> runCatching { player.playbackParams = player.playbackParams.apply { setSpeed(speed) } } }
        }
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return String.format("%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Deprecated in Android API 33; kept for older Android compatibility")
    override fun onBackPressed() { finish() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        preparedPlayer = null
        if (::video.isInitialized) video.stopPlayback()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
