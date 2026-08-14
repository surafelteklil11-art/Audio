package com.surafel.audio

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** Bridges launcher widget buttons to the single Media3 session used by PlaybackService. */
class WidgetActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.surafel.audio.widget.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.surafel.audio.widget.PREVIOUS"
        const val ACTION_NEXT = "com.surafel.audio.widget.NEXT"
        const val ACTION_SHUFFLE = "com.surafel.audio.widget.SHUFFLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (intent.action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_PREVIOUS -> controller.seekToPreviousMediaItem()
                    ACTION_NEXT -> controller.seekToNextMediaItem()
                    ACTION_SHUFFLE -> controller.setShuffleModeEnabled(!controller.shuffleModeEnabled)
                }
                controller.release()
            } catch (_: Exception) {
                // The widget may be pressed before the media session is ready; never crash the launcher.
            } finally {
                pendingResult.finish()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }
}
