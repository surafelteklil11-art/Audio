package com.surafel.audio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
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

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 33, 35], qualifiers = "w360dp-h800dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class FingerprintActivityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun distinguishesEnrollmentUnavailableAndUnreportedHardware() {
        val pm = shadowOf(context.packageManager)
        val sensor = shadowOf(context.getSystemService(FingerprintManager::class.java))
        pm.setSystemFeature(PackageManager.FEATURE_FINGERPRINT, false)
        sensor.setIsHardwareDetected(false)
        assertEquals(FingerprintStatus.NOT_DETECTED, FingerprintSettings.status(context))
        pm.setSystemFeature(PackageManager.FEATURE_FINGERPRINT, true)
        assertEquals(FingerprintStatus.UNAVAILABLE, FingerprintSettings.status(context))
        sensor.setIsHardwareDetected(true)
        assertEquals(FingerprintStatus.NOT_ENROLLED, FingerprintSettings.status(context))
        sensor.setDefaultFingerprints(1)
        assertEquals(FingerprintStatus.ENROLLED, FingerprintSettings.status(context))
        sensor.setDefaultFingerprints(0)
        assertEquals(FingerprintStatus.NOT_ENROLLED, FingerprintSettings.status(context))
    }

    @Test fun failedVendorServiceIsUnknownInsteadOfMissingHardware() {
        val broken = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.FINGERPRINT_SERVICE) throw SecurityException("Restricted by device")
                return super.getSystemService(name)
            }
        }
        assertEquals(FingerprintStatus.UNKNOWN, FingerprintSettings.status(broken))
    }

    @Test fun fallbackRejectsNonSystemHandlersAndSurvivesDeniedOrMissingActivities() {
        val attempts = mutableListOf<Intent>()
        addHandler(Settings.ACTION_SECURITY_SETTINGS, "other.app", false)
        addHandler(Settings.ACTION_SECURITY_SETTINGS, "system.denied", true)
        addHandler(Settings.ACTION_SECURITY_SETTINGS, "system.removed", true)
        addHandler(Settings.ACTION_SETTINGS, "system.settings", true)
        val result = FingerprintSettings.open(context, listOf(Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
            attempts.add(it)
            when (it.component!!.packageName) {
                "system.denied" -> throw SecurityException()
                "system.removed" -> throw ActivityNotFoundException()
            }
        }
        assertEquals(Settings.ACTION_SETTINGS, result)
        assertEquals(listOf("system.denied", "system.removed", "system.settings"), attempts.map { it.component!!.packageName })
        assertNull(FingerprintSettings.open(context, listOf(Intent("missing.settings.action"))) { fail("No handler should launch") })
    }

    @Test fun enrollmentUsesPublicRoutesAvailableOnThisAndroidVersion() {
        val actions = FingerprintSettings.enrollmentIntents().map { it.action }
        assertEquals(Build.VERSION.SDK_INT >= 28, Settings.ACTION_FINGERPRINT_ENROLL in actions)
        assertEquals(Build.VERSION.SDK_INT >= 30, Settings.ACTION_BIOMETRIC_ENROLL in actions)
        val expected = when {
            Build.VERSION.SDK_INT >= 30 -> Settings.ACTION_BIOMETRIC_ENROLL
            Build.VERSION.SDK_INT >= 28 -> Settings.ACTION_FINGERPRINT_ENROLL
            else -> Settings.ACTION_SECURITY_SETTINGS
        }
        addHandler(expected, "system.enrollment", true)
        assertEquals(expected, FingerprintSettings.open(context, FingerprintSettings.enrollmentIntents()) {
            assertEquals(expected, it.action)
            assertEquals("system.enrollment", it.component!!.packageName)
        })
    }

    @Test fun activityRefreshesAfterSettingsAndHandlesNoSettingsWithoutClaimingEnrollment() {
        val sensor = shadowOf(context.getSystemService(FingerprintManager::class.java))
        sensor.setIsHardwareDetected(true)
        Robolectric.buildActivity(FingerprintActivity::class.java).use { controller ->
            val activity = controller.setup().visible().get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val status = root.findViewWithTag<TextView>("fingerprint-status")
            assertFalse(status.text.contains("Enrolled"))
            root.findViewWithTag<View>("fingerprint-enroll").performClick()
            assertTrue(root.findViewWithTag<TextView>("fingerprint-navigation").text.contains("አልተቻለም"))
            assertFalse(status.text.contains("Enrolled"))
            controller.pause().stop()
            sensor.setDefaultFingerprints(1)
            controller.start().resume().visible()
            assertTrue(status.text.contains("Enrolled"))
            sensor.setDefaultFingerprints(0)
            root.findViewWithTag<View>("fingerprint-refresh").performClick()
            assertFalse(status.text.contains("Enrolled"))
            controller.recreate()
            assertFalse(controller.get().findViewById<ViewGroup>(android.R.id.content).findViewWithTag<TextView>("fingerprint-status").text.contains("Enrolled"))
        }
    }

    @Test @Config(sdk = [33, 35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun screenshotAndPrimaryActionsFitCompactScreen() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_FINGERPRINT, true)
        Robolectric.buildActivity(FingerprintActivity::class.java).use { controller ->
            val root = controller.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
            repeat(3) {
                root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 720, 1600); shadowOf(Looper.getMainLooper()).idle()
            }
            for (tag in listOf("fingerprint-enroll", "fingerprint-security", "fingerprint-refresh")) {
                val rect = android.graphics.Rect()
                assertTrue(root.findViewWithTag<View>(tag).getGlobalVisibleRect(rect))
                assertTrue(rect.height() >= 96)
                assertTrue(rect.left >= 0 && rect.right <= 720)
            }
            val image = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val file = File("build/fingerprint-previews/fingerprint-api-${Build.VERSION.SDK_INT}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun addHandler(action: String, pkg: String, system: Boolean) {
        val app = ApplicationInfo().apply { packageName = pkg; enabled = true; flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0 }
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Settings"; applicationInfo = app; enabled = true; exported = true }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), info)
    }
}
