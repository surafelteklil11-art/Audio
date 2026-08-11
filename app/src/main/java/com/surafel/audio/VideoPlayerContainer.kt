package com.surafel.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView

class VideoPlayerContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private lateinit var video: VideoView
    private var preparedPlayer: MediaPlayer? = null
    private var speed = 1.0f

    override fun onFinishInflate() {
        super.onFinishInflate()
        video = findViewById(R.id.videoPlayer)
        val controller = MediaController(context)
        controller.setAnchorView(video)
        video.setMediaController(controller)

        findViewById<View>(R.id.videoBack10).setOnClickListener {
            video.seekTo((video.currentPosition - 10_000).coerceAtLeast(0))
        }
        findViewById<View>(R.id.videoForward30).setOnClickListener {
            val duration = video.duration.coerceAtLeast(0)
            video.seekTo((video.currentPosition + 30_000).coerceAtMost(duration))
        }
        findViewById<View>(R.id.videoSpeed).setOnClickListener { cycleSpeed() }
        findViewById<View>(R.id.videoPause).setOnClickListener {
            if (video.isPlaying) video.pause() else video.start()
            updatePauseText()
        }

        video.setOnPreparedListener { player ->
            preparedPlayer = player
            player.isLooping = false
            applySpeed()
            updatePauseText()
            controller.show(2500)
        }
        video.setOnCompletionListener {
            updatePauseText()
            preparedPlayer = null
        }
    }

    private fun updatePauseText() {
        findViewById<TextView>(R.id.videoPause).text = if (video.isPlaying) "Ⅱ" else "▶"
    }

    private fun cycleSpeed() {
        speed = when (speed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        applySpeed()
        findViewById<TextView>(R.id.videoSpeed).text = "${speed}×"
        Toast.makeText(context, "Playback ${speed}×", Toast.LENGTH_SHORT).show()
    }

    private fun applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                preparedPlayer?.playbackParams = preparedPlayer?.playbackParams?.apply {
                    setSpeed(speed)
                } ?: return
            }
        }
    }
}
