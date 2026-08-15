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

/** Injects Volume Booster into the existing side drawer after MainActivity builds it. */
object VolumeBoosterMenuInjector {
    fun install(activity: Activity) {
        if (activity !is MainActivity) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val overlay = root.findViewWithTag<ViewGroup>("audio_side_drawer") ?: return
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val panel = overlay.getChildAt(1) as? ViewGroup ?: return
                val scroll = panel.getChildAt(panel.childCount - 1) as? ScrollView ?: return
                val menu = scroll.getChildAt(0) as? LinearLayout ?: return
                if (menu.findViewWithTag<View>("volume_booster_entry") != null) return

                val row = LinearLayout(activity).apply {
                    tag = "volume_booster_entry"
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

                val playerHeader = (0 until menu.childCount).firstOrNull { i ->
                    (menu.getChildAt(i) as? TextView)?.text?.toString() == "PLAYER"
                }
                val insertAt = (playerHeader?.plus(3) ?: menu.childCount).coerceAtMost(menu.childCount)
                menu.addView(row, insertAt, LinearLayout.LayoutParams(-1, dp(activity, 58)).apply { bottomMargin = dp(activity, 3) })
            }
        })
    }

    private fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
