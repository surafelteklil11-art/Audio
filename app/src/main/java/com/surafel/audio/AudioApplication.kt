package com.surafel.audio

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import java.io.File

/** Application-level background and media-menu wiring. MainActivity owns its own UI controls. */
class AudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                BackgroundManager.apply(activity)
                MediaItemMenuInstaller.install(activity)
                // Do not override MainActivity's search/menu listeners here.
                // MainActivity owns the futuristic Themes + Widgets menu and its search action.
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

/**
 * Stores only the user's chosen background image.
 * The selected image is copied into app-private storage, so deleting or moving
 * the original image from Downloads/Gallery does not remove the saved background.
 */
object BackgroundManager {
    private const val PREFS = "audio_profile"
    private const val MODE = "background_mode"
    private const val CUSTOM = "background_custom_path"
    private const val CUSTOM_FILE = "saved_player_background"

    fun isCustom(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(MODE, "default") == "custom"

    fun setCustom(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MODE, "custom")
            .putString(CUSTOM, path)
            .apply()
    }

    fun customPath(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(CUSTOM, null)

    fun savedFile(context: Context): File = File(context.filesDir, CUSTOM_FILE)

    fun clearCustom(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(MODE)
            .remove(CUSTOM)
            .apply()
        savedFile(context).delete()
    }

    fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val custom = prefs.getString(MODE, "default") == "custom"
        val path = prefs.getString(CUSTOM, null)
        val bitmap = if (custom && path != null) BitmapFactory.decodeFile(path) else null

        if (bitmap != null) {
            root.background = LayerDrawable(arrayOf(
                BitmapDrawable(activity.resources, bitmap).apply { gravity = Gravity.FILL },
                ColorDrawable(0x52000000)
            ))
        } else {
            root.background = ContextCompat.getDrawable(activity, R.drawable.bg_art)
        }
    }
}
