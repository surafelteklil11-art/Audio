package com.surafel.audio.piano

import android.media.AudioManager
import android.media.SoundPool
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class PianoAudioTest {
    private fun loaded(audio: PianoAudio, success: Boolean = true): SoundPool {
        ReflectionHelpers.getField<ExecutorService>(audio, "executor").submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        val pool = ReflectionHelpers.getField<SoundPool>(audio, "pool")
        shadowOf(pool).notifyPathLoaded(PianoWave.cached(RuntimeEnvironment.getApplication()).absolutePath, success)
        return pool
    }
    @Test fun focusMustBeGrantedBeforeSoundAndLossStopsFurtherPlayback() {
        val app = RuntimeEnvironment.getApplication(); val manager = shadowOf(app.getSystemService(AudioManager::class.java))
        var lost = 0
        PianoAudio(app, {}, { lost++ }).use { audio ->
            val pool = loaded(audio); val shadow = shadowOf(pool); val path = PianoWave.cached(app).absolutePath
            manager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
            assertFalse(audio.acquire()); audio.play(PianoNote(0, 0, 72, 0.0)); assertFalse(shadow.wasPathPlayed(path))
            manager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            assertTrue(audio.acquire()); audio.play(PianoNote(0, 0, 60, 0.0)); audio.play(PianoNote(1, 1, 84, 0.0))
            assertEquals(listOf(.5f, 2f), shadow.getPathPlaybacks(path).map { it.rate })
            assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, manager.lastAudioFocusRequest.durationHint)
            manager.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertEquals(1, lost); assertFalse(audio.hasFocus)
            audio.play(PianoNote(2, 2, 72, 0.0)); assertEquals(2, shadow.getPathPlaybacks(path).size)
            audio.releaseFocus(); assertNotNull(manager.lastAbandonedAudioFocusListener)
        }
    }
    @Test fun failedSampleIsReportedAndClosedAudioCannotRestart() {
        val app = RuntimeEnvironment.getApplication(); var changed = 0
        val audio = PianoAudio(app, { changed++ }, {})
        loaded(audio, false); assertTrue(audio.error); assertFalse(audio.ready); assertTrue(changed > 0)
        assertFalse(audio.acquire()); audio.close(); audio.close(); assertFalse(audio.acquire())
    }
    @Test @Config(sdk = [24]) fun generatedWaveIsValidAudiblePcmWithNoClippingAndQuietEndpoints() {
        val bytes = PianoWave.bytes(); val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4)); assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(bytes.size - 8, buffer.getInt(4)); assertEquals(PianoWave.SAMPLE_RATE, buffer.getInt(24))
        assertEquals(bytes.size - 44, buffer.getInt(40)); assertEquals(16, buffer.getShort(34).toInt())
        val samples = (44 until bytes.size step 2).map { buffer.getShort(it).toInt() }
        assertTrue(samples.maxOf { kotlin.math.abs(it) } in 5000..32766)
        assertEquals(0, samples.first()); assertTrue(kotlin.math.abs(samples.last()) < 10)
    }
}
