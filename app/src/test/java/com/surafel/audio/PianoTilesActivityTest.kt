package com.surafel.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.surafel.audio.piano.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35], qualifiers = "w360dp-h800dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class PianoTilesActivityTest {
    @Before fun clean() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        RuntimeEnvironment.getApplication().getSharedPreferences("piano_tiles", 0).edit().clear().commit()
    }
    @Test fun rotationAndBackgroundPreserveAttemptAndRequireExplicitResume() {
        Robolectric.buildActivity(PianoTilesActivity::class.java).use { c ->
            var a = c.setup().visible().get(); var root = a.findViewById<ViewGroup>(android.R.id.content)
            silence(root); root.findViewWithTag<View>("piano-play-aurora").performClick(); click(root, "Start · ጀምር"); layout(root)
            val e = ViewModelProvider(a)[PianoModel::class.java].engine!!
            e.advance(e.difficulty.travelMs); val b = root.findViewWithTag<PianoBoardView>("piano-board")
            tap(b, 0, 0); assertEquals(100, e.score)
            e.advance(600.0)
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_2))
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_2))
            assertEquals(200, e.score)
            c.recreate(); a = c.get(); root = a.findViewById(android.R.id.content); layout(root)
            assertSame(e, ViewModelProvider(a)[PianoModel::class.java].engine)
            assertEquals(PianoPhase.PAUSED, e.phase); assertEquals(200, e.score)
            click(root, "Resume · ቀጥል"); assertEquals(PianoPhase.RUNNING, e.phase)
            c.pause().stop(); assertEquals(PianoPhase.PAUSED, e.phase)
            c.start().resume().visible(); assertEquals(PianoPhase.PAUSED, e.phase)
        }
    }
    @Test fun soundPlaybackFocusInterruptionAndHeadphoneUnplugPauseTheActualActivity() {
        Robolectric.buildActivity(PianoTilesActivity::class.java).use { c ->
            val a = c.setup().visible().get(); val root = a.findViewById<ViewGroup>(android.R.id.content)
            val pool = loadAudio(a)
            if (descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Sound off" }) click(root, "Sound off")
            val manager = shadowOf(a.getSystemService(android.media.AudioManager::class.java))
            manager.setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            root.findViewWithTag<View>("piano-play-aurora").performClick(); click(root, "Start · ጀምር"); layout(root)
            val e = ViewModelProvider(a)[PianoModel::class.java].engine!!
            assertEquals(PianoPhase.RUNNING, e.phase)
            e.advance(e.difficulty.travelMs); tap(root.findViewWithTag("piano-board"), 0, 0)
            assertEquals(100, e.score)
            assertTrue(shadowOf(pool).wasPathPlayed(PianoWave.cached(a).absolutePath))
            manager.lastAudioFocusRequest.listener.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertEquals(PianoPhase.PAUSED, e.phase)
            click(root, "Resume · ቀጥል"); assertEquals(PianoPhase.RUNNING, e.phase)
            val time = e.elapsedMs
            a.sendBroadcast(android.content.Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(PianoPhase.PAUSED, e.phase); assertEquals(time, e.elapsedMs, .001)
            assertEquals(3, e.lives); assertEquals(100, e.score)
        }
    }
    @Test fun twoFingersHoldSeparateLanesAndCancellationPausesWithoutPenalty() {
        val app = RuntimeEnvironment.getApplication()
        val e = PianoEngine(listOf(PianoNote(0, 0, 72, 0.0, 900.0), PianoNote(1, 3, 79, 0.0, 900.0)), PianoDifficulty.NORMAL)
        val played = mutableListOf<Int>(); val stopped = mutableListOf<Int>()
        val b = PianoBoardView(app, e, { played.add(it.id) }, { stopped.add(it) }, {})
        layout(b); e.start(); e.advance(e.difficulty.travelMs)
        touches(b, MotionEvent.ACTION_DOWN, intArrayOf(17), intArrayOf(0))
        touches(b, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), intArrayOf(17, 4), intArrayOf(0, 3))
        assertEquals(listOf(0, 1), played); assertEquals(listOf(17, 4), e.progress.map { it.pointer })
        e.advance(400.0)
        touches(b, MotionEvent.ACTION_CANCEL, intArrayOf(17, 4), intArrayOf(0, 3))
        assertEquals(PianoPhase.PAUSED, e.phase); assertEquals(0, e.misses); assertEquals(setOf(0, 1), stopped.toSet())
        e.resume()
        touches(b, MotionEvent.ACTION_DOWN, intArrayOf(6), intArrayOf(0))
        touches(b, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), intArrayOf(6, 22), intArrayOf(0, 3))
        e.advance(1500.0)
        touches(b, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), intArrayOf(6, 22), intArrayOf(0, 3))
        assertEquals(PianoPhase.COMPLETE, e.phase); assertEquals(300, e.score)
    }
    @Test fun missedTileGeometryCannotScoreAndFrameStallPauses() {
        val e = PianoEngine(listOf(PianoNote(0, 0, 72, 0.0)), PianoDifficulty.NORMAL)
        val b = PianoBoardView(RuntimeEnvironment.getApplication(), e, {}, {}, {})
        layout(b); e.start(); e.advance(e.difficulty.travelMs)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, b.width / 8f, b.height - 2f, 0)
        b.dispatchTouchEvent(event); event.recycle(); assertEquals(0, e.score); assertEquals(2, e.lives)
        b.doFrame(1_000_000_000); b.doFrame(2_000_000_000)
        assertEquals(PianoPhase.PAUSED, e.phase); assertEquals(2, e.lives)
    }
    @Test fun completedSongRecordsHighScoreAndStarsOnceAndByDifficulty() {
        val app = RuntimeEnvironment.getApplication(); val model = PianoModel(app)
        val song = PianoSongs.all.first(); model.setSound(false); assertFalse(PianoModel(app).sound); model.select(song)
        val e = model.engine!!; e.start()
        for (n in e.notes) {
            e.advance(n.atMs - e.elapsedMs); e.down(n.lane, n.id)
            if (n.holdMs > 0) e.advance(n.holdMs)
            e.up(n.id)
        }
        model.recordResult(); model.recordResult(); val score = e.score
        assertEquals(3, model.stars(song)); assertEquals(score, PianoModel(app).best(song))
        model.retry(); model.engine!!.start(); model.engine!!.advance(100000.0); model.recordResult()
        assertEquals(score, model.best(song)); assertEquals(3, model.stars(song))
        model.home(); model.setDifficulty(PianoDifficulty.HARD)
        assertEquals(0, model.best(song)); assertEquals(0, model.stars(song))
    }
    @Test @Config(sdk = [33, 35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderSongLibraryReadyAndPlayingAtPhoneSize() {
        Robolectric.buildActivity(PianoTilesActivity::class.java).use { c ->
            val a = c.setup().visible().get(); val root = a.findViewById<ViewGroup>(android.R.id.content)
            loadAudio(a); layout(root); snapshot(root, "home")
            silence(root); root.findViewWithTag<View>("piano-play-blue").performClick(); layout(root); snapshot(root, "ready")
            click(root, "Start · ጀምር"); layout(root)
            val e = ViewModelProvider(a)[PianoModel::class.java].engine!!
            assertEquals(PianoPhase.RUNNING, e.phase)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            assertTrue(e.elapsedMs > -e.difficulty.travelMs)
            assertTrue(e.elapsedMs <= -e.difficulty.travelMs + 50)
            e.advance(-120.0 - e.elapsedMs)
            val b = root.findViewWithTag<PianoBoardView>("piano-board")
            assertTrue(b.targetY > b.boardTop + 300); assertTrue(b.height > 900)
            snapshot(root, "playing")
            b.pause(); layout(root)
        }
    }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-xhdpi")
    fun compactLandscapeStillHasPlayableLanesAndReachableStart() {
        Robolectric.buildActivity(PianoTilesActivity::class.java).use { c ->
            val root = c.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
            silence(root); root.findViewWithTag<View>("piano-play-aurora").performClick(); layout(root, 960, 640)
            click(root, "Start · ጀምር"); layout(root, 960, 640)
            val b = root.findViewWithTag<PianoBoardView>("piano-board")
            assertTrue(b.targetY > b.boardTop); assertEquals(PianoPhase.RUNNING, b.engine.phase)
        }
    }
    private fun tap(b: PianoBoardView, lane: Int, id: Int) {
        touches(b, MotionEvent.ACTION_DOWN, intArrayOf(id), intArrayOf(lane))
        touches(b, MotionEvent.ACTION_UP, intArrayOf(id), intArrayOf(lane))
    }
    private fun touches(b: PianoBoardView, action: Int, ids: IntArray, lanes: IntArray) {
        val properties = ids.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords = lanes.map { lane -> MotionEvent.PointerCoords().apply { x = (lane + .5f) * b.width / 4; y = b.targetY - 10; pressure = 1f; size = 1f } }.toTypedArray()
        val event = MotionEvent.obtain(0, 0, action, ids.size, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        b.dispatchTouchEvent(event); event.recycle()
    }
    private fun loadAudio(a: PianoTilesActivity): android.media.SoundPool {
        val audio = org.robolectric.util.ReflectionHelpers.getField<PianoAudio>(a, "audio")
        org.robolectric.util.ReflectionHelpers.getField<java.util.concurrent.ExecutorService>(audio, "executor").submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        val pool = org.robolectric.util.ReflectionHelpers.getField<android.media.SoundPool>(audio, "pool")
        shadowOf(pool).notifyPathLoaded(PianoWave.cached(a).absolutePath, true)
        assertTrue(audio.ready); return pool
    }
    private fun silence(root: View) {
        if (descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "♫ Sound on" }) click(root, "♫ Sound on")
        assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Sound off" })
    }
    private fun click(root: View, title: String) { descendants(root).filterIsInstance<TextView>().first { it.text.toString() == title }.performClick() }
    private fun layout(root: View, width: Int = 720, height: Int = 1600) { repeat(2) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height); shadowOf(Looper.getMainLooper()).idle()
    } }
    private fun snapshot(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
        val file = File("build/piano-previews/$name-api-${android.os.Build.VERSION.SDK_INT}.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun descendants(v: View): Sequence<View> = sequence { yield(v); if (v is ViewGroup) for (i in 0 until v.childCount) yieldAll(descendants(v.getChildAt(i))) }
}
