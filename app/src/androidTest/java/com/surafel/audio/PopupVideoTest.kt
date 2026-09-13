package com.surafel.audio

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surafel.audio.video.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PopupVideoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var video: File
    @Before fun setUp() {
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        video = File(app.cacheDir, "popup-test.mp4").apply {
            writeBytes(Base64.decode(instrumentation.context.assets.open("popup-test.mp4.b64").bufferedReader().use { it.readText() }, Base64.DEFAULT))
        }
    }
    @After fun cleanUp() {
        main { app.stopService(Intent(app, PopupVideoService::class.java)) }
        waitUntil { PopupVideoService.instance == null }
        main { VideoSessions.sessions.forEach { VideoSessions.release(it.id) } }
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW default")
        video.delete()
    }
    private fun intent(title: String = "Test video") = Intent(app, FullscreenVideoActivity::class.java)
        .putExtra(FullscreenVideoActivity.EXTRA_VIDEO_URI, Uri.fromFile(video).toString())
        .putExtra(FullscreenVideoActivity.EXTRA_VIDEO_TITLE, title)
    @Test fun sixVideosRenderTogetherAndKeepIndependentControlsAcrossSurfaceTransfers() {
        val ids = mutableListOf<String>()
        repeat(6) { index ->
            ActivityScenario.launch<FullscreenVideoActivity>(intent("Video ${index + 1}")).use { scenario ->
                lateinit var session: VideoSession
                scenario.onActivity { activity ->
                    session = VideoSessions.sessions.last(); ids.add(session.id)
                    session.player.repeatMode = Player.REPEAT_MODE_ALL
                    session.player.seekTo(1200)
                    assertTrue(descendants(activity.window.decorView).any { it.contentDescription == "Open popup video" })
                }
                waitUntil { session.renderedFrames > 0 && session.player.isPlaying }
                scenario.onActivity { click(it.window.decorView, "Open popup video") }
                waitUntil { session.owner == VideoOwner.POPUP && PopupVideoService.instance?.windows?.size == index + 1 }
            }
        }
        waitUntil { VideoSessions.sessions.size == 6 && VideoSessions.sessions.all { it.player.isPlaying && it.renderedFrames >= 2 } }
        main {
            assertEquals(6, VideoSessions.sessions.map { it.player }.toSet().size)
            assertEquals(1, VideoSessions.sessions.count { !it.muted })
            assertNull(VideoSessions.create(app, Uri.fromFile(video), "Seventh"))
            PopupVideoService.instance!!.windows.values.forEach { it.panel.showControls() }
        }
        screenshot("video-six-popups")
        val first = mainValue { VideoSessions.get(ids.first())!! }
        val second = mainValue { VideoSessions.get(ids[1])!! }
        val panel = mainValue { PopupVideoService.instance!!.windows[first.id]!!.panel }
        main { click(panel, "Pause video"); click(panel, "Playback speed"); click(panel, "Mute video") }
        val position = mainValue { first.player.currentPosition }
        val otherPosition = mainValue { second.player.currentPosition }
        waitUntil { second.player.currentPosition != otherPosition }
        main { assertFalse(first.player.playWhenReady); assertEquals(position, first.player.currentPosition); assertTrue(first.muted); assertEquals(1.25f, first.player.playbackParameters.speed) }
        main {
            val service = PopupVideoService.instance!!
            val popup = service.windows[first.id]!!
            val oldWidth = popup.bounds.width
            click(panel, "Resize popup"); assertTrue(popup.bounds.width > oldWidth)
            panel.onDrag?.invoke(100000f, 100000f)
            assertTrue(service.usableArea().contains(popup.bounds.x, popup.bounds.y))
            assertTrue(popup.bounds.x + popup.bounds.width <= service.usableArea().right)
            assertTrue(popup.bounds.y + popup.bounds.height <= service.usableArea().bottom)
        }
        val monitor = instrumentation.addMonitor(FullscreenVideoActivity::class.java.name, null, false)
        main { click(panel, "Return to fullscreen") }
        val fullscreen = monitor.waitForActivityWithTimeout(10000) as? FullscreenVideoActivity
            ?: throw AssertionError("Popup fullscreen control did not open the Activity")
        instrumentation.removeMonitor(monitor)
        waitUntil { first.owner == VideoOwner.FULLSCREEN && PopupVideoService.instance?.windows?.size == 5 }
        main {
            assertSame(first, VideoSessions.get(first.id)); assertEquals(position, first.player.currentPosition)
            click(fullscreen.window.decorView, "Play video")
            click(fullscreen.window.decorView, "Open popup video")
        }
        waitUntil { first.owner == VideoOwner.POPUP && PopupVideoService.instance?.windows?.size == 6 && first.player.isPlaying }
        main { click(PopupVideoService.instance!!.windows[first.id]!!.panel, "Close popup") }
        waitUntil { VideoSessions.get(first.id) == null && PopupVideoService.instance?.windows?.size == 5 }
        ActivityScenario.launch<FullscreenVideoActivity>(intent("Replacement")).use { scenario ->
            scenario.onActivity { click(it.window.decorView, "Open popup video") }
            waitUntil { PopupVideoService.instance?.windows?.size == 6 }
        }
        main { app.startService(Intent(app, PopupVideoService::class.java).setAction(PopupVideoService.PAUSE_ALL)) }
        waitUntil { VideoSessions.sessions.all { !it.player.playWhenReady } }
        main { app.startService(Intent(app, PopupVideoService::class.java).setAction(PopupVideoService.CLOSE_ALL)) }
        waitUntil { PopupVideoService.instance == null && VideoSessions.sessions.isEmpty() }
    }
    @Test fun fullscreenPausesInBackgroundAndRetainsPositionSpeedOnRecreation() {
        ActivityScenario.launch<FullscreenVideoActivity>(intent()).use { scenario ->
            lateinit var session: VideoSession
            scenario.onActivity { session = VideoSessions.sessions.single(); session.player.repeatMode = Player.REPEAT_MODE_ALL }
            waitUntil { session.player.isPlaying && session.renderedFrames > 0 }
            scenario.onActivity { session.player.setPlaybackSpeed(1.5f); session.player.seekTo(2000) }
            scenario.moveToState(Lifecycle.State.CREATED)
            main { assertFalse(session.player.playWhenReady) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil { session.player.isPlaying }
            scenario.recreate()
            waitUntil { session.player.isPlaying }
            scenario.onActivity { assertSame(session, VideoSessions.sessions.single()); assertEquals(1.5f, session.player.playbackParameters.speed) }
            screenshot("video-fullscreen")
        }
        waitUntil { VideoSessions.sessions.isEmpty() }
    }
    @Test fun denyingOverlayPermissionKeepsFullscreenPlaybackAvailable() {
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
        assertFalse(Settings.canDrawOverlays(app))
        ActivityScenario.launch<FullscreenVideoActivity>(intent()).use { scenario ->
            scenario.onActivity { click(it.window.decorView, "Open popup video") }
            main { assertNull(PopupVideoService.instance); assertEquals(VideoOwner.FULLSCREEN, VideoSessions.sessions.single().owner) }
        }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun click(view: View, description: String) { val target = descendants(view).firstOrNull { it.contentDescription == description } ?: throw AssertionError("Missing control: $description"); assertTrue(target.performClick()) }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun <T> mainValue(block: () -> T): T { var result: T? = null; main { result = block() }; @Suppress("UNCHECKED_CAST") return result as T }
    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) { if (mainValue(predicate)) return; SystemClock.sleep(100) }
        throw AssertionError("Timed out waiting for native video state")
    }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("No display screenshot")
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Download/AudioPdfPreviews"); put(MediaStore.Images.Media.IS_PENDING, 1) }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        app.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null); bitmap.recycle()
    }
}
