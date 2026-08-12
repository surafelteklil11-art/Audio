package com.surafel.audio

import android.app.Activity
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
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.roundToInt

/** Keeps the existing MainActivity wiring and adds the hamburger side navigation. */
class AudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                BackgroundManager.apply(activity)
                if (activity is MainActivity) {
                    activity.findViewById<View>(R.id.searchButton)?.setOnClickListener {
                        activity.startActivity(Intent(activity, SearchActivity::class.java))
                    }
                    activity.findViewById<View>(R.id.premiumButton)?.visibility = View.GONE
                    activity.findViewById<View>(R.id.menuButton)?.setOnClickListener {
                        SideMenu.show(activity)
                    }
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

/** Uses the reference-style default plus 10 additional scenic backgrounds and a user-selected image. */
object BackgroundManager {
    private const val PREFS = "audio_profile"
    private const val MODE = "background_mode"
    private const val INDEX = "background_index"
    private const val CUSTOM = "background_custom_path"

    val drawableIds = intArrayOf(
        R.drawable.bg_reference_sunset,
        R.drawable.bg_wall_02, R.drawable.bg_wall_03, R.drawable.bg_wall_04, R.drawable.bg_wall_05,
        R.drawable.bg_wall_06, R.drawable.bg_wall_07, R.drawable.bg_wall_08, R.drawable.bg_wall_09,
        R.drawable.bg_wall_10, R.drawable.bg_wall_11
    )
    val names = arrayOf(
        "Reference Sunset Lake", "Tropical Beach", "Pine Forest", "Waterfall Valley", "Desert Dunes",
        "Snowy Peaks", "Autumn Lake", "Night City", "Bamboo River", "Aurora Night", "Purple Sunset Lake"
    )

    fun selectedIndex(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(INDEX, 0).coerceIn(0, drawableIds.lastIndex)

    fun isCustom(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(MODE, "builtin") == "custom"

    fun selectBuiltIn(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MODE, "builtin")
            .putInt(INDEX, index.coerceIn(0, drawableIds.lastIndex))
            .remove(CUSTOM)
            .apply()
    }

    fun setCustom(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MODE, "custom")
            .putString(CUSTOM, path)
            .apply()
    }

    fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val drawable = if (prefs.getString(MODE, "builtin") == "custom") {
            val path = prefs.getString(CUSTOM, null)
            val bitmap = path?.let { BitmapFactory.decodeFile(it) }
            if (bitmap != null) {
                LayerDrawable(arrayOf(
                    BitmapDrawable(activity.resources, bitmap).apply { gravity = Gravity.FILL },
                    ColorDrawable(0x52000000)
                ))
            } else {
                ContextCompat.getDrawable(activity, drawableIds[selectedIndex(activity)])
            }
        } else {
            ContextCompat.getDrawable(activity, drawableIds[selectedIndex(activity)])
        }
        if (drawable != null) root.background = drawable
    }
}

private object SideMenu {
    private var popup: PopupWindow? = null

    fun show(activity: Activity) {
        popup?.dismiss()
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).roundToInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(12), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(13, 18, 42))
                setCornerRadii(floatArrayOf(0f, 0f, dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f))
                setStroke(dp(1), Color.rgb(55, 64, 98))
            }
            elevation = dp(12).toFloat()
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = "♫"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(52, 25, 92))
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.rgb(120, 67, 205))
            }
        }, LinearLayout.LayoutParams(dp(50), dp(50)))
        header.addView(TextView(activity).apply {
            text = "Audio\nMusic & video"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setGravity(Gravity.CENTER_VERTICAL)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setOnClickListener { popup?.dismiss() }
        }, LinearLayout.LayoutParams(dp(40), dp(50)))
        root.addView(header)

        root.addView(View(activity).apply {
            setBackgroundColor(Color.rgb(43, 51, 82))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(16)
            bottomMargin = dp(8)
        })

        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        addItem(activity, list, "⌂", "Home") { popup?.dismiss(); activity.findViewById<View>(R.id.homeNav)?.performClick() }
        addItem(activity, list, "♫", "Music") { popup?.dismiss(); activity.findViewById<View>(R.id.musicNav)?.performClick() }
        addItem(activity, list, "▶", "Video") { popup?.dismiss(); activity.findViewById<View>(R.id.videoNav)?.performClick() }
        addItem(activity, list, "☺", "My Profile") { popup?.dismiss(); activity.findViewById<View>(R.id.mineNav)?.performClick() }
        addItem(activity, list, "☷", "Play Queue") { popup?.dismiss(); activity.findViewById<View>(R.id.queueButton)?.performClick() }

        addLabel(activity, list, "LIBRARY")
        addItem(activity, list, "↻", "Refresh Library") { popup?.dismiss(); activity.findViewById<View>(R.id.musicNav)?.performClick() }
        addItem(activity, list, "⌕", "Search") { popup?.dismiss(); activity.findViewById<View>(R.id.searchButton)?.performClick() }

        addLabel(activity, list, "APP")
        addItem(activity, list, "⚙", "Settings") {
            popup?.dismiss()
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }

        popup = PopupWindow(root, dp(315), ViewGroup.LayoutParams.MATCH_PARENT, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            elevation = dp(16).toFloat()
            setOnDismissListener { popup = null }
        }
        popup?.showAtLocation(activity.findViewById(android.R.id.content), Gravity.START or Gravity.TOP, 0, 0)
    }

    private fun addLabel(activity: Activity, list: LinearLayout, text: String) {
        val d = activity.resources.displayMetrics.density
        list.addView(TextView(activity).apply {
            this.text = text
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(105, 121, 155))
            setPadding((12 * d).roundToInt(), (18 * d).roundToInt(), 0, (6 * d).roundToInt())
        })
    }

    private fun addItem(activity: Activity, list: LinearLayout, icon: String, label: String, action: () -> Unit) {
        val d = activity.resources.displayMetrics.density
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).roundToInt(), 0, (8 * d).roundToInt(), 0)
            setOnClickListener { action() }
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 14 * d
            }
        }
        row.addView(TextView(activity).apply {
            this.text = icon
            textSize = 21f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.rgb(220, 210, 255))
        }, LinearLayout.LayoutParams((48 * d).roundToInt(), (52 * d).roundToInt()))
        row.addView(TextView(activity).apply {
            this.text = label
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(Color.rgb(232, 236, 247))
            setPadding((4 * d).roundToInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, (52 * d).roundToInt(), 1f))
        list.addView(row, LinearLayout.LayoutParams(-1, (54 * d).roundToInt()))
    }
}
