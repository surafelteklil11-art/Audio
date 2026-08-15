package com.surafel.audio

import android.media.audiofx.LoudnessEnhancer

/** Single process-wide audio booster used by the futuristic booster screen. */
object VolumeBoosterController {
    private var enhancer: LoudnessEnhancer? = null

    @Synchronized
    fun setGain(gainMb: Int) {
        try {
            enhancer?.release()
            enhancer = null
            if (gainMb <= 0) return
            enhancer = LoudnessEnhancer(0).apply {
                setTargetGain(gainMb.coerceIn(0, 2000))
                enabled = true
            }
        } catch (_: Throwable) {
            enhancer?.release()
            enhancer = null
        }
    }

    @Synchronized
    fun release() {
        enhancer?.release()
        enhancer = null
    }
}
