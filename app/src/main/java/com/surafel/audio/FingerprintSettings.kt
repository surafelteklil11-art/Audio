package com.surafel.audio

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.provider.Settings

internal enum class FingerprintStatus { ENROLLED, NOT_ENROLLED, UNAVAILABLE, NOT_DETECTED, UNKNOWN }

/** Fingerprint-only queries: face enrollment must never be reported as an enrolled fingerprint. */
@Suppress("DEPRECATION")
internal object FingerprintSettings {
    fun status(context: Context): FingerprintStatus = try {
        val advertised = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val manager = context.getSystemService(FingerprintManager::class.java)
        when {
            manager == null || !manager.isHardwareDetected ->
                if (advertised) FingerprintStatus.UNAVAILABLE else FingerprintStatus.NOT_DETECTED
            manager.hasEnrolledFingerprints() -> FingerprintStatus.ENROLLED
            else -> FingerprintStatus.NOT_ENROLLED
        }
    } catch (_: RuntimeException) {
        // A failed vendor service or denied query is not evidence that hardware is absent.
        FingerprintStatus.UNKNOWN
    }

    fun enrollmentIntents(): List<Intent> = buildList {
        // Prefer the public fingerprint-specific route while OEMs still expose it.
        if (Build.VERSION.SDK_INT >= 28) add(Intent(Settings.ACTION_FINGERPRINT_ENROLL))
        if (Build.VERSION.SDK_INT >= 30) add(Intent(Settings.ACTION_BIOMETRIC_ENROLL))
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }

    /** Launch only exported system Settings handlers. Never guess hidden OEM component names. */
    fun open(context: Context, intents: List<Intent>, launch: (Intent) -> Unit = { context.startActivity(it) }): String? {
        for (intent in intents) {
            val handlers = try {
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: RuntimeException) { emptyList() }
            for (handler in handlers) {
                val info = handler.activityInfo ?: continue
                val app = info.applicationInfo ?: continue
                if (!info.enabled || !info.exported || !app.enabled ||
                    app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue
                try {
                    launch(Intent(intent).setComponent(ComponentName(info.packageName, info.name)))
                    return intent.action
                } catch (_: ActivityNotFoundException) {
                    // The Settings package may have changed since it was resolved.
                } catch (_: SecurityException) {
                    // A vendor or device policy may restrict this route; try the next public one.
                }
            }
        }
        return null
    }
}
