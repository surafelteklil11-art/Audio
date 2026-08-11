package com.surafel.audio

import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class FullscreenVideoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
    }

    private lateinit var video: VideoView
    private lateinit var pauseButton: TextView
    private lateinit var speedButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeLabel: TextView
    private var preparedPlayer: MediaPlayer? = null
    private var speed = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (::video.isInitialized && video.duration > 0) {
                seekBar.max = video.duration
                seekBar.progress = video.currentPosition.coerceIn(0, video.duration)
                timeLabel.text = "${formatTime(video.currentPosition)} / ${formatTime(video.duration)}"
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // The whole screen is deliberately black. VideoView is centered and allowed to
        // keep the source aspect ratio, producing black letterbox space above/below when
        // the video is wider than the available portrait area instead of pinning it to top.
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        video = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        root.addView(
            video,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

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

        val bottom = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(180, 0, 0, 0))
        }

        seekBar = SeekBar(this).apply {
            max = 1
            progress = 0
            contentDescription = "Video progress"
            setPadding(8, 0, 8, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) video.seekTo(value)
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
        bottom.addView(
            timeLabel,
            FrameLayout.LayoutParams(-1, 28, Gravity.TOP).apply { topMargin = 38 }
        )

        val controls = FrameLayout(this)

        // Dedicated 10-second rewind button.
        val back10 = control("↶ 10s") {
            if (::video.isInitialized) {
                video.seekTo((video.currentPosition - 10_000).coerceAtLeast(0))
                updatePause()
            }
        }
        controls.addView(
            back10,
            FrameLayout.LayoutParams(82, 54, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = 4
            }
        )

        pauseButton = control("▶") {
            if (video.isPlaying) video.pause() else video.start()
            updatePause()
        }
        controls.addView(pauseButton, FrameLayout.LayoutParams(64, 54, Gravity.CENTER))

        // Dedicated 10-second forward button (not 30 seconds).
        val forward10 = control("10s ↷") {
            if (::video.isInitialized) {
                val duration = video.duration.coerceAtLeast(0)
                video.seekTo((video.currentPosition + 10_000).coerceAtMost(duration))
                updatePause()
            }
        }
        controls.addView(
            forward10,
            FrameLayout.LayoutParams(82, 54, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = 74
            }
        )

        speedButton = control("1.0×") { cycleSpeed() }
        controls.addView(
            speedButton,
            FrameLayout.LayoutParams(68, 54, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = 4
            }
        )
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
            video.start()
            updatePause()
            seekBar.max = video.duration.coerceAtLeast(1)
            handler.removeCallbacks(progressUpdater)
            handler.post(progressUpdater)
        }
        video.setOnCompletionListener {
            seekBar.progress = seekBar.max
            updatePause()
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
        if (::pauseButton.isInitialized) {
            pauseButton.text = if (video.isPlaying) "Ⅱ" else "▶"
        }
    }

    private fun cycleSpeed() {
        speed = when (speed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        speedButton.text = "${speed}×"
        applySpeed()
    }

    private fun applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preparedPlayer?.let { player ->
                runCatching {
                    player.playbackParams = player.playbackParams.apply { setSpeed(speed) }
                }
            }
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
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        preparedPlayer = null
        if (::video.isInitialized) video.stopPlayback()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
