package com.surafel.audio.video

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.surafel.audio.FullscreenVideoActivity
import com.surafel.audio.MainActivity

/** Each overlay owns one player surface. Other apps receive touches outside its bounds. */
class PopupVideoService : Service() {
    data class Popup(val panel: VideoPanel, val bounds: PopupBounds, val params: WindowManager.LayoutParams, val slot: Int)
    val windows = linkedMapOf<String, Popup>()
    private lateinit var manager: WindowManager
    private val claim: (String) -> Unit = { remove(it, release = false) }
    companion object {
        const val ADD = "com.surafel.audio.video.ADD"
        const val CLOSE_ALL = "com.surafel.audio.video.CLOSE_ALL"
        const val PAUSE_ALL = "com.surafel.audio.video.PAUSE_ALL"
        const val SESSION = "session_id"
        private const val CHANNEL = "popup_videos"
        private const val NOTIFICATION = 7306
        var instance: PopupVideoService? = null; private set
    }
    override fun onCreate() {
        super.onCreate(); instance = this; manager = getSystemService(WindowManager::class.java)
        VideoSessions.takeFromPopup = claim
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Popup videos", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ADD -> {
                // Called from the visible Activity, before handing over its surface.
                ServiceCompat.startForeground(this, NOTIFICATION, notification(),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
                intent.getStringExtra(SESSION)?.let { add(it) }; stopIfEmpty()
            }
            PAUSE_ALL -> VideoSessions.pauseAll()
            CLOSE_ALL -> { windows.keys.toList().forEach { remove(it, true) }; stopIfEmpty() }
            else -> stopIfEmpty()
        }
        return START_NOT_STICKY
    }
    private fun add(id: String) {
        val session = VideoSessions.get(id) ?: return
        if (id in windows) return
        if (!Settings.canDrawOverlays(this) || windows.size >= VideoSessions.MAX_VIDEOS) {
            VideoSessions.move(id, VideoOwner.FULLSCREEN)
            Toast.makeText(this, "Allow Display over other apps to open a popup", Toast.LENGTH_LONG).show(); return
        }
        val slot = (0 until VideoSessions.MAX_VIDEOS).first { candidate -> windows.values.none { it.slot == candidate } }
        val bounds = PopupBounds.initial(slot, usableArea(), dp(156))
        @Suppress("DEPRECATION") val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(bounds.width, bounds.height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = bounds.x; y = bounds.y }
        val panel = VideoPanel(this, session, true)
        val popup = Popup(panel, bounds, params, slot)
        panel.onClose = { remove(id, true) }
        panel.onFullscreen = {
            try { startActivity(Intent(this, FullscreenVideoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(FullscreenVideoActivity.EXTRA_SESSION_ID, id)) }
            catch (_: RuntimeException) { Toast.makeText(this, "Open Audio to return to fullscreen", Toast.LENGTH_SHORT).show() }
        }
        panel.onDrag = { dx, dy -> bounds.x += dx.toInt(); bounds.y += dy.toInt(); update(popup) }
        panel.onResize = { dx -> bounds.width += dx.toInt(); update(popup) }
        panel.onCycleSize = {
            val area = usableArea(); bounds.width = if (bounds.width < area.width() * .7) (area.width() * .85).toInt() else area.width() / 2 - dp(4)
            update(popup)
        }
        try {
            manager.addView(panel, params); windows[id] = popup
            VideoSessions.move(id, VideoOwner.POPUP); refreshNotification()
        } catch (_: RuntimeException) {
            panel.dispose(); VideoSessions.move(id, VideoOwner.FULLSCREEN)
            Toast.makeText(this, "Could not open popup. Check Display over other apps permission.", Toast.LENGTH_LONG).show()
        }
    }
    private fun update(popup: Popup) {
        popup.bounds.clamp(usableArea(), dp(156))
        popup.params.apply { x = popup.bounds.x; y = popup.bounds.y; width = popup.bounds.width; height = popup.bounds.height }
        try { manager.updateViewLayout(popup.panel, popup.params) }
        catch (_: RuntimeException) { remove(popup.panel.session.id, true) }
    }
    private fun remove(id: String, release: Boolean) {
        val popup = windows.remove(id) ?: return
        popup.panel.dispose(); runCatching { manager.removeViewImmediate(popup.panel) }
        if (release) VideoSessions.release(id)
        if (windows.isEmpty()) stopIfEmpty() else refreshNotification()
    }
    fun usableArea(): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(metrics.bounds).apply { left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom }
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(metrics)
        fun bar(name: String): Int { val id = resources.getIdentifier(name, "dimen", "android"); return if (id != 0) resources.getDimensionPixelSize(id) else 0 }
        return Rect(0, bar("status_bar_height"), metrics.widthPixels, metrics.heightPixels - bar("navigation_bar_height"))
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); windows.values.toList().forEach { update(it) } }
    private fun notification(): Notification {
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun action(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, javaClass).setAction(action), immutable)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Audio · ${windows.size} popup video${if (windows.size == 1) "" else "s"}")
            .setContentText("Open Audio to add videos · up to 6")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), immutable))
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause all", action(PAUSE_ALL, 1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close all", action(CLOSE_ALL, 2)).build()
    }
    private fun refreshNotification() { getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
    private fun stopIfEmpty() { if (windows.isEmpty()) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() } }
    override fun onDestroy() {
        windows.values.toList().forEach { popup ->
            popup.panel.dispose(); runCatching { manager.removeViewImmediate(popup.panel) }; VideoSessions.release(popup.panel.session.id)
        }
        windows.clear()
        if (VideoSessions.takeFromPopup === claim) VideoSessions.takeFromPopup = null
        if (instance === this) instance = null
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
