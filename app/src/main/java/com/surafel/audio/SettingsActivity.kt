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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {
    private var preview: ImageView? = null
    private var emptyLabel: TextView? = null
    private var deleteButton: Button? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) saveCustomBackground(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.settingsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.refreshLibrarySetting).setOnClickListener { finish() }
        findViewById<View>(R.id.aboutSetting).setOnClickListener {
            AlertDialog.Builder(this).setTitle("About Audio")
                .setMessage("Audio\nA private local music and video player.\nYour library stays on your device.")
                .setPositiveButton("OK", null).show()
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
            text = "Choose your own photo. Audio saves a private copy inside the app, so the original file can be deleted or moved without removing your background."
            textSize = 12f
            setTextColor(Color.rgb(137, 151, 178))
            setPadding(dp(4), 0, 0, dp(12))
        })

        val card = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 25, 55))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.rgb(78, 92, 135))
            }
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        card.addView(preview, FrameLayout.LayoutParams(-1, dp(205)))
        emptyLabel = TextView(this).apply {
            text = "No personal photo selected\n\nTap the button below to choose one"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 180, 204))
        }
        card.addView(emptyLabel, FrameLayout.LayoutParams(-1, dp(205)))
        section.addView(card, LinearLayout.LayoutParams(-1, dp(205)).apply { leftMargin = dp(4); rightMargin = dp(4) })

        section.addView(Button(this).apply {
            text = "＋  CHOOSE PHOTO FROM MY FILES"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.rgb(42, 28, 74))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(111, 69, 166))
            }
            setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })

        deleteButton = Button(this).apply {
            text = "Delete saved background"
            setTextColor(Color.rgb(220, 130, 150))
            textSize = 12f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(92, 65, 82))
            }
            setOnClickListener { confirmDeleteBackground() }
        }
        section.addView(deleteButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })

        section.addView(TextView(this).apply {
            text = "The saved copy stays here until you choose another photo or delete it yourself."
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(Color.rgb(125, 139, 168))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }, LinearLayout.LayoutParams(-1, dp(42)))

        container.addView(section, 0)
        refreshBackgroundPreview()
    }

    private fun saveCustomBackground(uri: Uri) {
        try {
            val target = BackgroundManager.savedFile(this)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target, false).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("No input stream")

            BackgroundManager.setCustom(this, target.absolutePath)
            BackgroundManager.apply(this)
            refreshBackgroundPreview()

            AlertDialog.Builder(this)
                .setTitle("Background saved")
                .setMessage("A private copy was saved inside Audio. You can delete the original photo from your phone and this background will remain.")
                .setPositiveButton("OK", null)
                .show()
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Could not save photo")
                .setMessage("Please choose another image file.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun confirmDeleteBackground() {
        if (!BackgroundManager.isCustom(this)) return
        AlertDialog.Builder(this)
            .setTitle("Delete saved background?")
            .setMessage("This removes the copy saved inside Audio. Your original photo in Gallery/Files will not be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                BackgroundManager.clearCustom(this)
                BackgroundManager.apply(this)
                refreshBackgroundPreview()
            }
            .show()
    }

    private fun refreshBackgroundPreview() {
        val path = BackgroundManager.customPath(this)
        val file = path?.let { File(it) }
        val exists = file?.exists() == true
        if (exists) {
            preview?.setImageURI(Uri.fromFile(file))
            preview?.visibility = View.VISIBLE
            emptyLabel?.visibility = View.GONE
            deleteButton?.visibility = View.VISIBLE
        } else {
            preview?.setImageDrawable(null)
            preview?.visibility = View.GONE
            emptyLabel?.visibility = View.VISIBLE
            deleteButton?.visibility = View.GONE
        }
    }

    private fun findScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findScrollView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
