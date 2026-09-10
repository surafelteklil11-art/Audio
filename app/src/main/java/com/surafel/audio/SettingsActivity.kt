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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {
    private lateinit var libraryContainer: LinearLayout
    private lateinit var defaultCard: FrameLayout

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) saveCustomBackgrounds(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.settingsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.refreshLibrarySetting).setOnClickListener { finish() }
        findViewById<View>(R.id.aboutSetting).setOnClickListener {
            AlertDialog.Builder(this).setTitle("About Audio")
                .setMessage("Audio\nA private local music and video player.\nYour music and saved backgrounds stay on your device.")
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
            text = "Save 10+ of your own photos inside Audio. Choose any saved photo as the background, or use the original app default whenever you want. Imported photos are copied into private app storage."
            textSize = 12f
            setTextColor(Color.rgb(137, 151, 178))
            setPadding(dp(4), 0, 0, dp(12))
        })

        defaultCard = makeDefaultCard()
        section.addView(defaultCard, LinearLayout.LayoutParams(-1, dp(82)).apply {
            leftMargin = dp(4); rightMargin = dp(4); bottomMargin = dp(10)
        })

        section.addView(Button(this).apply {
            text = "＋  ADD PHOTOS FROM MY FILES"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.rgb(42, 28, 74))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(111, 69, 166))
            }
            setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(2) })

        section.addView(TextView(this).apply {
            text = "MY SAVED PHOTOS"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(125, 139, 168))
            setPadding(dp(4), dp(18), 0, dp(8))
        })

        libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        section.addView(libraryContainer)

        section.addView(TextView(this).apply {
            text = "Saved photos are private copies inside Audio. If you delete the original photo from Downloads, Gallery, Telegram, or another folder, the saved copy here remains. It is deleted only when you delete it from Audio or clear the app's data."
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(Color.rgb(125, 139, 168))
            setPadding(dp(8), dp(8), dp(8), dp(18))
        }, LinearLayout.LayoutParams(-1, dp(76)))

        container.addView(section, 0)
        refreshLibrary()
    }

    private fun makeDefaultCard(): FrameLayout {
        val card = FrameLayout(this)
        card.background = GradientDrawable().apply {
            setColor(Color.rgb(18, 25, 55))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(78, 92, 135))
        }
        card.addView(TextView(this).apply {
            text = "DEFAULT APP BACKGROUND\nUse the original Audio background"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(100), 0)
        }, FrameLayout.LayoutParams(-1, -1))
        card.addView(TextView(this).apply {
            text = "USE DEFAULT"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(53, 65, 105))
                cornerRadius = dp(15).toFloat()
            }
            setOnClickListener {
                BackgroundManager.clearCustom(this@SettingsActivity)
                BackgroundManager.apply(this@SettingsActivity)
                refreshLibrary()
            }
        }, FrameLayout.LayoutParams(dp(112), dp(44), Gravity.CENTER_VERTICAL or Gravity.END).apply { rightMargin = dp(10) })
        return card
    }

    private fun saveCustomBackgrounds(uris: List<Uri>) {
        val dir = File(filesDir, "saved_backgrounds")
        if (!dir.exists()) dir.mkdirs()
        var saved = 0
        uris.forEachIndexed { index, uri ->
            try {
                val extension = extensionFor(uri)
                val file = File(dir, "background_${System.currentTimeMillis()}_${index}.${extension}")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file, false).use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("No input stream")
                if (file.length() <= 0L) throw IllegalStateException("Empty image")
                saved++
            } catch (_: Exception) { }
        }
        refreshLibrary()
        AlertDialog.Builder(this)
            .setTitle(if (saved == 1) "Photo saved" else "Photos saved")
            .setMessage("$saved photo(s) are now saved inside Audio. You can add 10, 20, or more photos and choose any one later.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun extensionFor(uri: Uri): String {
        val mime = contentResolver.getType(uri)?.lowercase()
        return when {
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            mime == "image/gif" -> "gif"
            else -> "jpg"
        }
    }

    private fun refreshLibrary() {
        if (!::libraryContainer.isInitialized) return
        libraryContainer.removeAllViews()
        val files = BackgroundManagerLibrary.list(this)
        val active = BackgroundManager.customPath(this)
        val defaultSelected = !BackgroundManager.isCustom(this)

        defaultCard.background = GradientDrawable().apply {
            setColor(Color.rgb(18, 25, 55))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(if (defaultSelected) 2 else 1), if (defaultSelected) Color.rgb(255, 61, 170) else Color.rgb(78, 92, 135))
        }

        if (files.isEmpty()) {
            libraryContainer.addView(TextView(this).apply {
                text = "No saved photos yet. Tap ADD PHOTOS FROM MY FILES to save one or many."
                textSize = 12f
                setTextColor(Color.rgb(137, 151, 178))
                setPadding(dp(8), dp(8), dp(8), dp(14))
            })
            return
        }

        files.forEach { file ->
            libraryContainer.addView(makeSavedPhotoRow(file, active == file.absolutePath))
        }
    }

    private fun makeSavedPhotoRow(file: File, selected: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 25, 55))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(if (selected) 2 else 1), if (selected) Color.rgb(255, 61, 170) else Color.rgb(61, 73, 106))
            }
        }

        val image = ImageView(this).apply {
            setImageURI(Uri.fromFile(file))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        row.addView(image, LinearLayout.LayoutParams(dp(72), dp(62)))

        row.addView(TextView(this).apply {
            text = if (selected) "✓  ${file.name}" else file.name
            textSize = 11f
            setTextColor(Color.WHITE)
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }, LinearLayout.LayoutParams(0, dp(62), 1f))

        row.addView(TextView(this).apply {
            text = "USE"
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(53, 38, 91))
                cornerRadius = dp(13).toFloat()
            }
            setOnClickListener {
                BackgroundManager.setCustom(this@SettingsActivity, file.absolutePath)
                BackgroundManager.apply(this@SettingsActivity)
                refreshLibrary()
            }
        }, LinearLayout.LayoutParams(dp(54), dp(42)).apply { rightMargin = dp(5) })

        row.addView(TextView(this).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(225, 135, 155))
            setOnClickListener { confirmDelete(file) }
        }, LinearLayout.LayoutParams(dp(40), dp(62)))

        row.setOnClickListener {
            BackgroundManager.setCustom(this, file.absolutePath)
            BackgroundManager.apply(this)
            refreshLibrary()
        }
        return row
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete saved photo?")
            .setMessage("Only Audio's private saved copy will be deleted. Your original photo will not be touched.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val wasActive = BackgroundManager.customPath(this) == file.absolutePath
                file.delete()
                if (wasActive) BackgroundManager.clearCustom(this)
                BackgroundManager.apply(this)
                refreshLibrary()
            }
            .show()
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

/** Small adapter around the existing BackgroundManager storage contract. */
private object BackgroundManagerLibrary {
    fun list(context: android.content.Context): List<File> =
        File(context.filesDir, "saved_backgrounds").listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
