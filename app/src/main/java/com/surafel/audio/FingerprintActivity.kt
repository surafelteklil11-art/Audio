package com.surafel.audio

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** A shortcut to Android-owned enrollment, not a replacement fingerprint enrollment service. */
class FingerprintActivity : AudioToolPageActivity() {
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var navigationResult: TextView

    override fun pageTitle() = "Fingerprint · አሻራ"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageRoot.fitsSystemWindows = true
        navigationResult.text = savedInstanceState?.getString("navigation-result").orEmpty()
        navigationResult.visibility = if (navigationResult.text.isEmpty()) View.GONE else View.VISIBLE
    }
    override fun onResume() { super.onResume(); refreshStatus() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("navigation-result", navigationResult.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun buildContent(): View = contentColumn().apply {
        addView(sectionTitle("Fingerprint settings", "የስልክህን የአሻራ ምዝገባ ክፈት"))
        addView(panel().apply {
            statusTitle = label("", 20f, true).apply { tag = "fingerprint-status" }
            statusDetail = label("", 14f).apply { tag = "fingerprint-detail" }
            addView(statusTitle); addView(statusDetail)
        }, space())
        addView(actionButton("የአሻራ ምዝገባ ክፈት") {
            openSettings(FingerprintSettings.enrollmentIntents())
        }.apply { tag = "fingerprint-enroll"; minHeight = dp(56); setPadding(dp(12), dp(12), dp(12), dp(12)) }, space())
        addView(actionButton("Password & Security ክፈት") {
            openSettings(listOf(Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
        }.apply { tag = "fingerprint-security"; minHeight = dp(56); setPadding(dp(12), dp(12), dp(12), dp(12)) }, space())
        addView(actionButton("ሁኔታውን እንደገና ፈትሽ") { refreshStatus() }.apply {
            tag = "fingerprint-refresh"; minHeight = dp(52); setPadding(dp(12), dp(12), dp(12), dp(12))
        }, space())
        navigationResult = label("", 14f).apply {
            tag = "fingerprint-navigation"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            // Keep feedback readable with every player theme.
            setBackgroundColor(Color.rgb(5, 15, 36)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        addView(navigationResult, space())
        addView(panel().apply {
            addView(label("እንዴት እጠቀም?", 17f, true))
            addView(label("1. የአሻራ ምዝገባውን ክፈት።\n2. Android Settings ሲጠይቅ PIN፣ Pattern ወይም Password አስገባ።\n3. Fingerprint ምረጥና የስልኩን መመሪያ ተከተል።\n4. ወደ Audio ስትመለስ ሁኔታው እንደገና ይፈተሻል።", 14f))
            addView(label("የአሻራ አማራጩ አሁንም ከጠፋ፣ ስልኩን restart አድርግ። ችግሩ ከቀጠለ የስልኩን አምራች ድጋፍ አግኝ። Audio የጠፋ sensor ወይም የስርዓት ችግር መጠገን አይችልም።", 14f))
            addView(label("አሻራና PIN በስልኩ Settings ውስጥ ብቻ ይመዘገባሉ፤ Audio አያነባቸውም ወይም አያስቀምጣቸውም።", 13f))
        }, space())
    }

    private fun refreshStatus() {
        val state = FingerprintSettings.status(this)
        statusTitle.text = when (state) {
            FingerprintStatus.ENROLLED -> "አሻራ ተመዝግቧል · Enrolled"
            FingerprintStatus.NOT_ENROLLED -> "አሻራ ገና አልተመዘገበም"
            FingerprintStatus.UNAVAILABLE -> "Fingerprint sensor አሁን አይገኝም"
            FingerprintStatus.NOT_DETECTED -> "Android የአሻራ sensor አላገኘም"
            FingerprintStatus.UNKNOWN -> "ሁኔታውን ማንበብ አልተቻለም"
        }
        statusTitle.setTextColor(if (state == FingerprintStatus.ENROLLED) Color.rgb(105, 231, 181) else Color.rgb(255, 216, 143))
        statusDetail.text = when (state) {
            FingerprintStatus.ENROLLED -> "በዚህ የስልክ ተጠቃሚ ላይ አሻራ አለ። ለመጨመር ወይም ለማስተዳደር Settings ክፈት።"
            FingerprintStatus.NOT_ENROLLED -> "Sensorው ተገኝቷል። አሻራ ለመጨመር ከታች ያለውን የምዝገባ አዝራር ንካ።"
            FingerprintStatus.UNAVAILABLE -> "ስልኩ Fingerprint እንደሚደግፍ ያሳያል፣ ግን sensorው በአሁኑ ጊዜ አልተገኘም። Settings መክፈት መሞከር ትችላለህ።"
            FingerprintStatus.NOT_DETECTED -> "ይህ የአሁኑ የAndroid ሪፖርት ነው። ቀድሞ ይሠራ ከነበረ፣ የስርዓት ወይም የsensor ችግር ሊኖር ይችላል።"
            FingerprintStatus.UNKNOWN -> "የስርዓቱ መልስ አልተገኘም። እንደገና ፈትሽ ወይም Settings ክፈት።"
        }
    }

    private fun openSettings(intents: List<Intent>) {
        navigationResult.visibility = View.VISIBLE
        navigationResult.text = when (FingerprintSettings.open(this, intents)) {
            Settings.ACTION_FINGERPRINT_ENROLL -> "የስልኩ የአሻራ ምዝገባ ተከፍቷል።"
            Settings.ACTION_BIOMETRIC_ENROLL -> "Biometrics Settings ተከፍቷል። Fingerprint ካለ ምረጥ፤ ስልኩ Face Unlock ሊያሳይም ይችላል።"
            Settings.ACTION_SECURITY_SETTINGS -> "Password & Security ተከፍቷል። በዚያ Fingerprint ፈልግ።"
            Settings.ACTION_SETTINGS -> "ዋናው Settings ተከፍቷል። Password & Security → Fingerprint ፈልግ።"
            else -> "Settings ከAudio መክፈት አልተቻለም። የስልኩን Settings በቀጥታ ክፈት።"
        }
    }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(222, 232, 247))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6)); setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun space() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
}
