package com.surafel.audio

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
                executeAction(controller, intent.action)
                AudioWidgetRenderer.updateAll(appContext, controller)

                // If the service was just started, its MediaStore queue can finish loading
                // shortly after the controller connects. Retry navigation once so the first
                // widget press is not lost while the queue is being built.
                if (intent.action == ACTION_NEXT || intent.action == ACTION_PREVIOUS) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            if (controller.mediaItemCount > 1) {
                                executeAction(controller, intent.action)
                                AudioWidgetRenderer.updateAll(appContext, controller)
                            }
                        } finally {
                            controller.release()
                            pendingResult.finish()
                        }
                    }, 350L)
                } else {
                    controller.release()
                    pendingResult.finish()
                }
            } catch (_: Exception) {
                // Widget taps must never crash the launcher.
                pendingResult.finish()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun executeAction(controller: MediaController, action: String?) {
        when (action) {
            ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
            ACTION_PREVIOUS -> {
                if (controller.mediaItemCount > 1 && controller.currentMediaItemIndex == 0) {
                    controller.seekToDefaultPosition(controller.mediaItemCount - 1)
                } else {
                    controller.seekToPreviousMediaItem()
                }
            }
            ACTION_NEXT -> {
                if (controller.mediaItemCount > 1 && controller.currentMediaItemIndex >= controller.mediaItemCount - 1) {
                    controller.seekToDefaultPosition(0)
                } else {
                    controller.seekToNextMediaItem()
                }
            }
            ACTION_SHUFFLE -> controller.setShuffleModeEnabled(!controller.shuffleModeEnabled)
        }
    }
}
