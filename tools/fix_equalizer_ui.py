from pathlib import Path
import re

PATH = Path("app/src/main/java/com/surafel/audio/EqualizerActivity.kt")
s = PATH.read_text(encoding="utf-8")

# Keep the existing UI fixes intact. This repair targets the crash that can
# happen when Android audio effects are created against session 0 before the
# Media3 player has exposed its real audio session.

if "private var effectsSessionId = 0" not in s:
    marker = "    private var reverbIndex = 0\n"
    s = s.replace(marker, marker + "    private var effectsSessionId = 0\n", 1)

if "private val effectRetry" not in s:
    marker = "    private val presetButtons = mutableMapOf<String, UiButton>()\n"
    s = s.replace(marker, marker + '''
    private val effectRetry = Runnable {
        if (!isFinishing && !isDestroyed) initializeEffects()
    }
''', 1)

s = s.replace(
'''    override fun onDestroy() {
        equalizer?.release()''',
'''    override fun onDestroy() {
        if (::root.isInitialized) root.removeCallbacks(effectRetry)
        equalizer?.release()''', 1)

pattern = re.compile(r"    private fun initializeEffects\(\) \{.*?\n    \}\n\n    private fun refreshContentAlpha\(\)", re.S)
replacement = '''    private fun initializeEffects() {
        val sessionId = VolumeBoosterController.getAudioSessionId()

        // Insert effects must be attached to the same audio session as the
        // Media3/ExoPlayer output. Session 0 is the global mix and is not a
        // safe target for Equalizer/BassBoost/Virtualizer on modern Android.
        if (sessionId <= 0) {
            status.text = "AUDIO ENGINE • WAITING FOR PLAYBACK SESSION"
            root.removeCallbacks(effectRetry)
            root.postDelayed(effectRetry, 500L)
            ensureCustomSnapshot()
            renderBands()
            refreshContentAlpha()
            return
        }

        if (effectsSessionId == sessionId && equalizer != null) {
            loadEffectValues()
            setEffectsEnabled(enabled)
            status.text = "AUDIO ENGINE • LIVE CONTROLS"
            ensureCustomSnapshot()
            renderBands()
            refreshContentAlpha()
            return
        }

        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()
        presetReverb?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        presetReverb = null
        effectsSessionId = sessionId

        // Vendor audio engines can expose only a subset of these effects.
        // Create each independently so one unsupported effect cannot crash
        // the whole Equalizer page.
        equalizer = try {
            Equalizer(0, sessionId).also {
                eqMin = it.bandLevelRange[0].toInt()
                eqMax = it.bandLevelRange[1].toInt()
            }
        } catch (_: Throwable) { null }
        bassBoost = try { BassBoost(0, sessionId) } catch (_: Throwable) { null }
        virtualizer = try { Virtualizer(0, sessionId) } catch (_: Throwable) { null }
        loudnessEnhancer = try { LoudnessEnhancer(sessionId) } catch (_: Throwable) { null }
        // PresetReverb is an auxiliary/output-mix effect and intentionally
        // remains on session 0; failure here must not affect insert effects.
        presetReverb = try { PresetReverb(0, 0) } catch (_: Throwable) { null }

        loadEffectValues()
        setEffectsEnabled(enabled)
        status.text = if (equalizer != null || bassBoost != null || virtualizer != null || loudnessEnhancer != null)
            "AUDIO ENGINE • LIVE CONTROLS"
        else
            "AUDIO ENGINE • UI CONTROLS ACTIVE"

        ensureCustomSnapshot()
        renderBands()
        refreshContentAlpha()
    }

    private fun refreshContentAlpha()'''
if not pattern.search(s):
    raise SystemExit("initializeEffects block not found")
s = pattern.sub(replacement, s, count=1)

PATH.write_text(s, encoding="utf-8")
print("Equalizer audio-session crash fix applied")
