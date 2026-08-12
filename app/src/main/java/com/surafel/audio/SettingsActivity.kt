package com.surafel.audio

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) saveCustomBackground(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.settingsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.refreshLibrarySetting).setOnClickListener { finish() }
        findViewById<View>(R.id.aboutSetting).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About Audio")
                .setMessage("Audio\nA private local music and video player.\nYour library stays on your device.")
                .setPositiveButton("OK", null)
                .show()
        }
        addBackgroundSettings()
    }

    private fun addBackgroundSettings() {
        val scroll = findScrollView(window.decorView) ?: return
        val container = scroll.getChildAt(0) as? LinearLayout ?: return
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, 0)
        }
        section.addView(TextView(this).apply {
            text = "PLAYER BACKGROUND"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(125, 139, 168))
            setPadding(dp(4), 0, 0, dp(8))
        })
        section.addView(TextView(this).apply {
            text = "Choose one of 10 scenic themes, or use a photo from your device."
            textSize = 12f
            setTextColor(Color.rgb(137, 151, 178))
            setPadding(dp(4), 0, 0, dp(12))
        })

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 5
            useDefaultMargins = false
        }
        BackgroundManager.drawableIds.forEachIndexed { index, resId ->
            grid.addView(makeBackgroundCard(index, resId), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(118)
                columnSpec = GridLayout.spec(index % 2, 1, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        section.addView(grid, LinearLayout.LayoutParams(-1, dp(610)))

        section.addView(Button(this).apply {
            text = "＋  USE PHOTO FROM MY FILES"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.rgb(42, 28, 74))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(111, 69, 166))
            }
            setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        section.addView(TextView(this).apply {
            text = "Reset to default background"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.rgb(151, 163, 187))
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener {
                BackgroundManager.selectBuiltIn(this@SettingsActivity, 0)
                BackgroundManager.apply(this@SettingsActivity)
                refreshCards(grid)
            }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        container.addView(section, 0)
    }

    private fun makeBackgroundCard(index: Int, resId: Int): View {
        val frame = FrameLayout(this).apply {
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setOnClickListener {
                BackgroundManager.selectBuiltIn(this@SettingsActivity, index)
                BackgroundManager.apply(this@SettingsActivity)
                refreshCards(parent as? GridLayout)
            }
        }
        frame.addView(ImageView(this).apply {
            setImageResource(resId)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(TextView(this).apply {
            text = BackgroundManager.names[index]
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            gravity = Gravity.BOTTOM
            setPadding(dp(9), dp(6), dp(6), dp(8))
        }, FrameLayout.LayoutParams(-1, -1))
        updateCardBorder(frame, index)
        return frame
    }

    private fun refreshCards(grid: GridLayout?) {
        grid ?: return
        for (i in 0 until grid.childCount) updateCardBorder(grid.getChildAt(i), i)
    }

    private fun updateCardBorder(view: View, index: Int) {
        val selected = !BackgroundManager.isCustom(this) && BackgroundManager.selectedIndex(this) == index
        view.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(13).toFloat()
            setStroke(dp(if (selected) 3 else 1), if (selected) Color.rgb(255, 61, 170) else Color.rgb(61, 73, 106))
        }
    }

    private fun saveCustomBackground(uri: Uri) {
        try {
            val file = File(filesDir, "custom_background.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("No input stream")
            BackgroundManager.setCustom(this, file.absolutePath)
            BackgroundManager.apply(this)
            AlertDialog.Builder(this)
                .setTitle("Background updated")
                .setMessage("Your photo is now the Audio background.")
                .setPositiveButton("OK", null)
                .show()
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Could not use photo")
                .setMessage("Please choose another image file.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun findScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findScrollView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
