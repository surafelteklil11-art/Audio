package com.surafel.audio

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File

/** Application-level background and media-menu wiring. */
class AudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                BackgroundManager.apply(activity)
                MediaItemMenuInstaller.install(activity)
                SideMenuInstaller.install(activity)
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
 * Owns the app side menu. Only the four requested navigation entries are removed:
 * Home, Audio/Music, Vedio/Video and My Profile. Other utility actions remain.
 */
object SideMenuInstaller {
    private var installedFor: Activity? = null

    fun install(activity: Activity) {
        val button = activity.findViewById<TextView>(R.id.menuButton) ?: return
        if (installedFor === activity && button.getTag(R.id.menuButton) == true) return
        installedFor = activity
        button.setTag(R.id.menuButton, true)
        button.setOnClickListener { showMenu(activity) }
    }

    private fun showMenu(activity: Activity) {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 18))
            background = rounded(intArrayOf(Color.rgb(9, 14, 34), Color.rgb(24, 11, 48)), Color.rgb(126, 67, 255), dp(activity, 1), dp(activity, 24))
        }

        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = TextView(activity).apply {
            text = "♫"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(intArrayOf(Color.rgb(63, 25, 111), Color.rgb(30, 17, 70)), Color.rgb(137, 65, 255), dp(activity, 1), dp(activity, 18))
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(activity, 62), dp(activity, 62)))
        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 14), 0, 0, 0) }
        titleBox.addView(text(activity, "Audio", 22, Color.WHITE, Typeface.BOLD))
        titleBox.addView(text(activity, "Music & video", 16, Color.rgb(205, 210, 232), Typeface.BOLD).apply { setPadding(0, dp(activity, 2), 0, 0) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        val close = text(activity, "×", 30, Color.rgb(218, 219, 235), Typeface.NORMAL).apply { gravity = Gravity.CENTER; isClickable = true; setOnClickListener { (tag as? AlertDialog)?.dismiss() } }
        header.addView(close, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 62)))
        panel.addView(header)
        panel.addView(View(activity).apply { setBackgroundColor(Color.rgb(45, 49, 75)) }, LinearLayout.LayoutParams(-1, dp(activity, 1)).apply { topMargin = dp(activity, 14); bottomMargin = dp(activity, 12) })

        // Intentionally omitted: Home, Audio/Music, Vedio/Video, My Profile.
        addMenuItem(activity, panel, "☷", "Themes", true) { showThemes(activity) }
        addMenuItem(activity, panel, "▦", "Widgets", true) { showWidgets(activity) }
        addMenuItem(activity, panel, "☷", "Play Queue", true) {
            activity.findViewById<TextView>(R.id.queueButton)?.performClick()
        }

        panel.addView(text(activity, "LIBRARY", 12, Color.rgb(112, 129, 165), Typeface.BOLD).apply { setPadding(dp(activity, 2), dp(activity, 14), 0, dp(activity, 4)) })
        addMenuItem(activity, panel, "↻", "Refresh Library", true) { activity.recreate() }
        addMenuItem(activity, panel, "⌕", "Search", true) {
            activity.findViewById<TextView>(R.id.searchButton)?.performClick()
        }

        panel.addView(text(activity, "APP", 12, Color.rgb(112, 129, 165), Typeface.BOLD).apply { setPadding(dp(activity, 2), dp(activity, 14), 0, dp(activity, 4)) })
        addMenuItem(activity, panel, "⚙", "Settings", true) {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }

        val dialog = AlertDialog.Builder(activity).setView(panel).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        close.setTag(dialog)
        dialog.setOnShowListener {
            dialog.window?.setLayout(dp(activity, 310), -1)
            dialog.window?.setGravity(Gravity.START or Gravity.TOP)
            dialog.window?.attributes?.y = dp(activity, 48)
        }
        dialog.show()
        dialog.window?.setLayout(dp(activity, 310), -1)
        dialog.window?.setGravity(Gravity.START or Gravity.TOP)
        dialog.window?.attributes?.y = dp(activity, 48)
    }

    private fun addMenuItem(activity: Activity, panel: LinearLayout, glyph: String, label: String, enabled: Boolean, action: () -> Unit) {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = enabled
            isFocusable = enabled
            if (enabled) setOnClickListener { action() }
            setPadding(dp(activity, 18), 0, dp(activity, 8), 0)
        }
        val icon = text(activity, glyph, 25, Color.rgb(205, 202, 238), Typeface.NORMAL).apply { gravity = Gravity.CENTER }
        row.addView(icon, LinearLayout.LayoutParams(dp(activity, 54), dp(activity, 62)))
        row.addView(text(activity, label, 18, Color.rgb(220, 222, 240), Typeface.NORMAL), LinearLayout.LayoutParams(0, dp(activity, 62), 1f))
        panel.addView(row)
    }

    private fun showThemes(activity: Activity) {
        val prefs = activity.getSharedPreferences("audio_profile", Context.MODE_PRIVATE)
        val themes = arrayOf("Nebula Violet", "Cyber Blue", "Midnight Space")
        val current = prefs.getInt("theme", 0)
        AlertDialog.Builder(activity)
            .setTitle("Themes")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                prefs.edit().putInt("theme", which).apply()
                applyTheme(activity, which)
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun applyTheme(activity: Activity, theme: Int) {
        val root = activity.findViewById<View>(android.R.id.content)
        val colors = when (theme) {
            1 -> intArrayOf(Color.rgb(5, 18, 40), Color.rgb(9, 42, 72))
            2 -> intArrayOf(Color.rgb(6, 9, 20), Color.rgb(20, 12, 31))
            else -> intArrayOf(Color.rgb(10, 9, 29), Color.rgb(31, 11, 58))
        }
        root.background = rounded(colors, Color.TRANSPARENT, 0, 0)
    }

    private fun showWidgets(activity: Activity) {
        val widgets = arrayOf("Now Playing", "Quick Access", "Soundstream", "Weekly Report")
        val checked = booleanArrayOf(true, true, true, true)
        AlertDialog.Builder(activity)
            .setTitle("Home Widgets")
            .setMultiChoiceItems(widgets, checked) { _, _, _ -> }
            .setPositiveButton("APPLY", null)
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun text(activity: Activity, value: String, size: Int, color: Int, style: Int): TextView = TextView(activity).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, style)
        includeFontPadding = false
    }

    private fun rounded(colors: IntArray, stroke: Int, width: Int, radius: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        cornerRadius = radius.toFloat()
        if (width > 0) setStroke(width, stroke)
    }

    private fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
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
