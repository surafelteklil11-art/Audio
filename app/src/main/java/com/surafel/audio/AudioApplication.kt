package com.surafel.audio

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import java.io.File

/** Application-level background and media-menu wiring. MainActivity owns the side drawer UI. */
class AudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                BackgroundManager.apply(activity)
                MediaItemMenuInstaller.install(activity)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

object BackgroundManager {
    private const val PREFS = "audio_profile"
    private const val MODE = "background_mode"
    private const val CUSTOM = "background_custom_path"
    private const val CUSTOM_FILE = "saved_player_background"

    fun isCustom(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODE, "default") == "custom"
    fun setCustom(context: Context, path: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODE, "custom").putString(CUSTOM, path).apply() }
    fun customPath(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CUSTOM, null)
    fun savedFile(context: Context): File = File(context.filesDir, CUSTOM_FILE)
    fun clearCustom(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(MODE).remove(CUSTOM).apply(); savedFile(context).delete() }

    fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        if (activity is EthiopianCalendarActivity) return
        // A neutral gallery surface lets artwork and selection states stay legible.
        if (activity is ThemesActivity) {
            root.setBackgroundColor(android.graphics.Color.rgb(16, 25, 40))
            return
        }
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val custom = prefs.getString(MODE, "default") == "custom"
        val path = prefs.getString(CUSTOM, null)
        val bitmap = if (custom && path != null) ThemeCatalog.decodeImage(File(path)) else null
        if (bitmap != null) {
            root.background = ThemeImageDrawable(bitmap)
        } else {
            ThemeCatalog.apply(activity, root, prefs.getInt("theme", 0))
        }
    }
}
