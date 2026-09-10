package com.surafel.audio

import android.graphics.Color
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class FullscreenVideoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
    }

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var pauseButton: TextView
    private lateinit var speedButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeLabel: TextView
    private var speed = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // PlayerView is full-screen, but RESIZE_MODE_FIT keeps the actual video
        // centered at its original aspect ratio with black space around it.
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            keepScreenOn = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

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
                    if (fromUser) player.seekTo(value.toLong())
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
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
            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
        }
        controls.addView(back10, FrameLayout.LayoutParams(82, 54, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = 4 })

        pauseButton = control("▶") {
            if (player.isPlaying) player.pause() else player.play()
            updatePause()
        }
        controls.addView(pauseButton, FrameLayout.LayoutParams(64, 54, Gravity.CENTER))

        val forward10 = control("10s ↷") {
            val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration))
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

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePause()
                updateProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePause()
                updateProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                timeLabel.text = "Unable to play this video"
                updatePause()
            }
        })

        player.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
        player.playWhenReady = true
        player.prepare()
        handler.post(progressUpdater)
    }

    private fun updateProgress() {
        if (!::player.isInitialized || !::seekBar.isInitialized) return
        val duration = player.duration
        if (duration > 0L) {
            seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            seekBar.progress = player.currentPosition.coerceIn(0L, duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            timeLabel.text = "${formatTime(player.currentPosition)} / ${formatTime(duration)}"
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
        if (::pauseButton.isInitialized && ::player.isInitialized) {
            pauseButton.text = if (player.isPlaying) "Ⅱ" else "▶"
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
        player.playbackParameters = PlaybackParameters(speed)
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L)
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
        if (::playerView.isInitialized) playerView.player = null
        if (::player.isInitialized) player.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
