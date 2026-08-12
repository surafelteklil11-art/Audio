package com.surafel.audio

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Installs the hamburger side navigation without changing MainActivity's existing UI code. */
class SideMenuProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.findViewById<View>(R.id.menuButton)?.setOnClickListener {
                    AudioSideMenu.show(activity)
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                activity.findViewById<View>(R.id.menuButton)?.setOnClickListener {
                    AudioSideMenu.show(activity)
                }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}

private object AudioSideMenu {
    private var window: PopupWindow? = null

    fun show(activity: Activity) {
        window?.dismiss()

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(14), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(13, 18, 42))
                cornerRadii = floatArrayOf(0f, 0f, dp(26).toFloat(), dp(26).toFloat(), dp(26).toFloat(), dp(26).toFloat(), 0f, 0f)
                setStroke(dp(1), Color.rgb(48, 57, 91))
            }
            elevation = dp(14).toFloat()
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(activity).apply {
            text = "♫"
            gravity = Gravity.CENTER
            textSize = 23f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(50, 24, 91))
                setStroke(dp(1), Color.rgb(119, 64, 201))
                cornerRadius = dp(18).toFloat()
            }
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val titleBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        titleBox.addView(TextView(activity).apply {
            text = "Audio"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        titleBox.addView(TextView(activity).apply {
            text = "Music & video"
            textSize = 12f
            setTextColor(Color.rgb(132, 145, 174))
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(205, 213, 232))
            setOnClickListener { window?.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(52)))
        root.addView(header)

        val divider = View(activity).apply { setBackgroundColor(Color.rgb(40, 49, 80)) }
        root.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(18)
            bottomMargin = dp(10)
        })

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val menu = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(menu, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addItem(activity, menu, "⌂", "Home") {
            window?.dismiss()
            activity.findViewById<View>(R.id.homeNav)?.performClick()
        }
        addItem(activity, menu, "♫", "Music") {
            window?.dismiss()
            activity.findViewById<View>(R.id.musicNav)?.performClick()
        }
        addItem(activity, menu, "▶", "Video") {
            window?.dismiss()
            activity.findViewById<View>(R.id.videoNav)?.performClick()
        }
        addItem(activity, menu, "☺", "My profile") {
            window?.dismiss()
            activity.findViewById<View>(R.id.mineNav)?.performClick()
        }
        addItem(activity, menu, "☷", "Play queue") {
            window?.dismiss()
            activity.findViewById<View>(R.id.queueButton)?.performClick()
        }

        addSectionLabel(activity, menu, "LIBRARY")
        addItem(activity, menu, "↻", "Refresh library") {
            window?.dismiss()
            activity.findViewById<View>(R.id.musicNav)?.performClick()
        }
        addItem(activity, menu, "⌕", "Search") {
            window?.dismiss()
            activity.findViewById<View>(R.id.searchButton)?.performClick()
        }

        addSectionLabel(activity, menu, "APP")
        addItem(activity, menu, "⚙", "Settings") {
            window?.dismiss()
            showSettings(activity)
        }
        addItem(activity, menu, "♛", "Premium / About") {
            window?.dismiss()
            activity.findViewById<View>(R.id.premiumButton)?.performClick()
        }

        window = PopupWindow(root, dp(318), ViewGroup.LayoutParams.MATCH_PARENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            elevation = dp(18).toFloat()
            setOnDismissListener { window = null }
        }
        window?.showAtLocation(activity.findViewById(android.R.id.content), Gravity.START or Gravity.TOP, 0, 0)
    }

    private fun addSectionLabel(activity: Activity, parent: LinearLayout, text: String) {
        parent.addView(TextView(activity).apply {
            this.text = text
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(108, 123, 156))
            setPadding(dp(activity, 12), dp(activity, 18), 0, dp(activity, 7))
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun addItem(activity: Activity, parent: LinearLayout, icon: String, text: String, action: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * density).roundToInt(), 0, (10 * density).roundToInt(), 0)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = (16 * density)
            }
            setOnClickListener { action() }
            isClickable = true
        }
        row.addView(TextView(activity).apply {
            this.text = icon
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(223, 217, 255))
        }, LinearLayout.LayoutParams((48 * density).roundToInt(), (52 * density).roundToInt()))
        row.addView(TextView(activity).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.rgb(232, 236, 247))
            setPadding((4 * density).roundToInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, (52 * density).roundToInt(), 1f))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (54 * density).roundToInt()).apply {
            topMargin = (2 * density).roundToInt()
        })
    }

    private fun dp(activity: Activity, value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    private fun showSettings(activity: Activity) {
        val options = arrayOf("Refresh music library", "Create / edit profile", "About Audio")
        AlertDialog.Builder(activity)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> activity.findViewById<View>(R.id.musicNav)?.performClick()
                    1 -> activity.findViewById<View>(R.id.mineNav)?.performClick()
                    2 -> activity.findViewById<View>(R.id.premiumButton)?.performClick()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
