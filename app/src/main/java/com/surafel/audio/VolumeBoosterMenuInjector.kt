package com.surafel.audio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Adds the Volume Booster entry to the drawer owned by MainActivity.
 * The drawer itself remains owned by MainActivity; this helper only adds the
 * optional feature row after the drawer has actually been attached to the view tree.
 */
object VolumeBoosterMenuInjector {
    private const val DRAWER_TAG = "audio_side_drawer"
    private const val ENTRY_TAG = "volume_booster_entry"

    fun install(activity: Activity) {
        if (activity !is MainActivity) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val observer = root.viewTreeObserver
        observer.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val overlay = root.findViewWithTag<ViewGroup>(DRAWER_TAG) ?: return
                val menu = findMenu(overlay) ?: return
                if (menu.findViewWithTag<View>(ENTRY_TAG) != null) {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    return
                }
                addEntry(activity, menu, root, overlay)
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun findMenu(root: View): LinearLayout? {
        if (root is LinearLayout && root.findViewWithTag<View>(ENTRY_TAG) == null) {
            // The drawer's scroll content is the vertical menu. Prefer the
            // deepest LinearLayout that contains the PLAYER section.
            val texts = (0 until root.childCount).mapNotNull { index ->
                (root.getChildAt(index) as? TextView)?.text?.toString()
            }
            if (texts.contains("PLAYER") || texts.contains("Themes") || texts.contains("Widgets")) return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findMenu(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun addEntry(activity: Activity, menu: LinearLayout, root: ViewGroup, overlay: View) {
        val row = LinearLayout(activity).apply {
            tag = ENTRY_TAG
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(activity, 6), 0, dp(activity, 6), 0)
            setOnClickListener {
                root.removeView(overlay)
                activity.startActivity(Intent(activity, VolumeBoosterActivity::class.java))
            }
        }
        row.addView(TextView(activity).apply {
            text = "◉"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(60, 235, 221))
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(dp(activity, 54), dp(activity, 58)))
        row.addView(TextView(activity).apply {
            text = "Volume Booster"
            textSize = 17f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(228, 230, 243))
        }, LinearLayout.LayoutParams(0, dp(activity, 58), 1f))

        val playerHeader = (0 until menu.childCount).firstOrNull { index ->
            (menu.getChildAt(index) as? TextView)?.text?.toString() == "PLAYER"
        }
        val insertAt = (playerHeader?.plus(3) ?: menu.childCount).coerceIn(0, menu.childCount)
        menu.addView(
            row,
            insertAt,
            LinearLayout.LayoutParams(-1, dp(activity, 58)).apply {
                bottomMargin = dp(activity, 3)
            }
        )
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
