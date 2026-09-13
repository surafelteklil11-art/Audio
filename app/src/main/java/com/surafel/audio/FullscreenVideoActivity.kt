package com.surafel.audio

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.surafel.audio.video.*

class FullscreenVideoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_SESSION_ID = "video_session_id"
        const val EXTRA_START_POPUP = "start_popup"
    }
    private var session: VideoSession? = null
    private var panel: VideoPanel? = null
    private var resumeOnStart = false
    private val changed: () -> Unit = {
        if (session?.owner == VideoOwner.POPUP) { panel?.dispose(); finish() }
        else if (session?.owner == VideoOwner.FULLSCREEN) panel?.attachVideo()
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) openPopup()
        else Toast.makeText(this, "Popup needs Display over other apps permission", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false); hideSystemBars()
        val id = savedInstanceState?.getString(EXTRA_SESSION_ID) ?: intent.getStringExtra(EXTRA_SESSION_ID)
        session = VideoSessions.fullscreen(id ?: "")
        if (session == null) {
            val uri = savedInstanceState?.getString(EXTRA_VIDEO_URI) ?: intent.getStringExtra(EXTRA_VIDEO_URI)
            if (uri.isNullOrBlank()) { finish(); return }
            session = VideoSessions.create(this, Uri.parse(uri), savedInstanceState?.getString(EXTRA_VIDEO_TITLE)
                ?: intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video", savedInstanceState?.getLong("position") ?: 0,
                savedInstanceState?.getFloat("speed") ?: 1f, savedInstanceState?.getBoolean("playing", true) ?: true)
        }
        val current = session ?: run { Toast.makeText(this, "6 videos are already open. Close one to add another.", Toast.LENGTH_LONG).show(); finish(); return }
        resumeOnStart = savedInstanceState?.getBoolean("playing", false) == true
        panel = VideoPanel(this, current, false).apply {
            onClose = { finish() }; onPopup = { requestPopup() }
            onRotate = { requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        }
        setContentView(panel!!)
        ViewCompat.setOnApplyWindowInsetsListener(panel!!) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            panel?.setSafePadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        VideoSessions.observers.add(changed)
        if (intent.getBooleanExtra(EXTRA_START_POPUP, false) && savedInstanceState == null) panel?.post { requestPopup() }
    }
    private fun requestPopup() {
        if (Settings.canDrawOverlays(this)) openPopup()
        else AlertDialog.Builder(this).setTitle("Play over other apps")
            .setMessage("Allow Audio to display over other apps. You can then move, resize and play up to 6 popup videos.")
            .setNegativeButton("Cancel", null).setPositiveButton("Open settings") { _, _ ->
                runCatching { overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                    .onFailure { Toast.makeText(this, "Open Settings → Apps → Audio → Display over other apps", Toast.LENGTH_LONG).show() }
            }.show()
    }
    private fun openPopup() {
        val current = session ?: return
        if (current.owner != VideoOwner.FULLSCREEN || isFinishing) return
        if (resumeOnStart) { VideoSessions.play(current); resumeOnStart = false }
        VideoSessions.move(current.id, VideoOwner.TRANSFERRING)
        try { ContextCompat.startForegroundService(this, Intent(this, PopupVideoService::class.java).setAction(PopupVideoService.ADD).putExtra(PopupVideoService.SESSION, current.id)) }
        catch (_: RuntimeException) { VideoSessions.move(current.id, VideoOwner.FULLSCREEN); Toast.makeText(this, "Could not start popup. Please try again.", Toast.LENGTH_LONG).show() }
    }
    override fun onStart() { super.onStart(); session?.let { if (resumeOnStart && it.owner == VideoOwner.FULLSCREEN) VideoSessions.play(it) }; resumeOnStart = false }
    override fun onStop() {
        session?.takeIf { it.owner == VideoOwner.FULLSCREEN }?.let { resumeOnStart = it.player.playWhenReady; VideoSessions.pause(it) }
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        session?.let {
            outState.putString(EXTRA_SESSION_ID, it.id); outState.putString(EXTRA_VIDEO_URI, it.uri.toString()); outState.putString(EXTRA_VIDEO_TITLE, it.title)
            outState.putLong("position", it.player.currentPosition); outState.putFloat("speed", it.player.playbackParameters.speed)
            outState.putBoolean("playing", it.player.playWhenReady || resumeOnStart)
        }; super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        panel?.dispose(); panel = null; VideoSessions.observers.remove(changed)
        session?.takeIf { it.owner == VideoOwner.FULLSCREEN && !isChangingConfigurations }?.let { VideoSessions.release(it.id) }
        super.onDestroy()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }
    private fun hideSystemBars() { WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } }
}
