package com.surafel.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView

/**
 * Self-contained video player container. It does not depend on IDs from another layout,
 * so it can safely be reused from XML or created programmatically.
 */
class VideoPlayerContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val video: VideoView = VideoView(context)
    private var preparedPlayer: MediaPlayer? = null
    private var speed = 1.0f

    init {
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val controller = MediaController(context)
        controller.setAnchorView(video)
        video.setMediaController(controller)

        video.setOnPreparedListener { player ->
            preparedPlayer = player
            player.isLooping = false
            applySpeed()
            controller.show(2500)
        }
        video.setOnCompletionListener { preparedPlayer = null }
    }

    fun setVideoUri(uri: Uri?) {
        if (uri == null) {
            video.stopPlayback()
            preparedPlayer = null
            return
        }
        video.setVideoURI(uri)
    }

    fun play() = video.start()

    fun pause() = video.pause()

    fun seekBy(milliseconds: Int) {
        val target = (video.currentPosition + milliseconds).coerceIn(0, video.duration.coerceAtLeast(0))
        video.seekTo(target)
    }

    fun setPlaybackSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 2.0f)
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

    override fun onDetachedFromWindow() {
        video.stopPlayback()
        preparedPlayer = null
        super.onDetachedFromWindow()
    }
}
