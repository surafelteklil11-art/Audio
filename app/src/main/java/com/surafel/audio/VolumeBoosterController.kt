package com.surafel.audio

import android.media.audiofx.LoudnessEnhancer

/** Process-wide booster bound to Media3's real playback audio session. */
object VolumeBoosterController {
    private var enhancer: LoudnessEnhancer? = null
    private var audioSessionId: Int = 0
    private var requestedGainMb: Int = 0

    @Synchronized
    fun setAudioSessionId(sessionId: Int) {
        if (sessionId <= 0 || sessionId == audioSessionId) return
        audioSessionId = sessionId
        rebuild()
    }

    @Synchronized
    fun setGain(gainMb: Int) {
        requestedGainMb = gainMb.coerceIn(0, 2000)
        rebuild()
    }

    @Synchronized
    fun release() {
        enhancer?.release()
        enhancer = null
        audioSessionId = 0
    }

    @Synchronized
    private fun rebuild() {
        enhancer?.release()
        enhancer = null
        if (requestedGainMb <= 0 || audioSessionId <= 0) return
        try {
            enhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(requestedGainMb)
                enabled = true
            }
        } catch (_: Throwable) {
            enhancer?.release()
            enhancer = null
        }
    }
}
