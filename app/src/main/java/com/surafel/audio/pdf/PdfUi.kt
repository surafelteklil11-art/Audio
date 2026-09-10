package com.surafel.audio.pdf

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat

abstract class PdfUiActivity : AppCompatActivity() {
    protected val settings by lazy { getSharedPreferences("pdf_preferences", 0) }
    protected val dark get() = settings.getBoolean("dark", true)
    protected val ink get() = if (dark) Color.WHITE else Color.rgb(25, 31, 45)
    protected val muted get() = if (dark) 0xFF9299AB.toInt() else 0xFF58657A.toInt()
    protected val paper get() = if (dark) 0xFF1D1F25.toInt() else 0xFFF6F8FC.toInt()
    protected val card get() = if (dark) 0xFF292C35.toInt() else Color.WHITE
    protected val blue = 0xFF2582FF.toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = paper; window.navigationBarColor = paper
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        if (settings.getBoolean("keep_screen", false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    protected fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    protected fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    protected fun label(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    protected fun shape(color: Int, radius: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    protected fun action(value: String, description: String = value, task: () -> Unit) = label(value, 15f, ink, true).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); minWidth = dp(48); setPadding(dp(8), dp(4), dp(8), dp(4))
        contentDescription = description; isClickable = true; isFocusable = true; setOnClickListener { task() }
        val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)); foreground = attrs.getDrawable(0); attrs.recycle()
    }
    protected fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    protected fun message(title: String, text: String) { android.app.AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
    protected fun prompt(title: String, hint: String, initial: String = "", password: Boolean = false, multi: Boolean = false, submit: (String) -> Unit) {
        val input = EditText(this).apply {
            this.hint = hint; setText(initial); setTextColor(ink); setHintTextColor(muted); setPadding(dp(20), dp(12), dp(20), dp(12))
            inputType = if (password) 129 else if (multi) 131073 else 1
            if (multi) { minLines = 4; maxLines = 10; gravity = Gravity.TOP } else isSingleLine = true
            if (password) isSaveEnabled = false
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Continue", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val text = input.text.toString()
            if (text.isBlank()) input.error = "Enter a value" else { dialog.dismiss(); submit(text) }
        } }; dialog.show()
    }
    protected fun confirm(title: String, detail: String, task: () -> Unit) {
        android.app.AlertDialog.Builder(this).setTitle(title).setMessage(detail).setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> task() }.show()
    }
    protected fun choices(title: String, options: List<String>, task: (Int) -> Unit) {
        android.app.AlertDialog.Builder(this).setTitle(title).setItems(options.toTypedArray()) { _, i -> task(i) }.setNegativeButton("Cancel", null).show()
    }
}

class PdfFileIcon(context: android.content.Context) : View(context) {
    var folder = false
    var thumbnail: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(canvas: Canvas) {
        val save = canvas.save(); canvas.scale(width / 56f, height / 64f)
        thumbnail?.let { if (!it.isRecycled) { canvas.drawBitmap(it, null, RectF(6f, 2f, 50f, 62f), paint); canvas.restoreToCount(save); return } }
        if (folder) {
            paint.shader = LinearGradient(4f, 10f, 48f, 60f, intArrayOf(0xFF57CFFF.toInt(), 0xFF2470ED.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(1f, 8f, 25f, 28f), 4f, 4f, paint)
            canvas.drawRoundRect(RectF(1f, 16f, 54f, 55f), 5f, 5f, paint)
            paint.shader = LinearGradient(0f, 22f, 50f, 62f, intArrayOf(0xFF9DDEFF.toInt(), 0xFF438FFD.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(5f, 24f, 55f, 58f), 4f, 4f, paint); paint.shader = null
        } else {
            paint.color = 0xFFFF5465.toInt(); canvas.drawRoundRect(RectF(7f, 4f, 49f, 60f), 5f, 5f, paint)
            paint.color = 0xFFFFB6BE.toInt(); canvas.drawRect(36f, 4f, 49f, 17f, paint)
            paint.color = Color.WHITE; paint.textSize = 13f; paint.typeface = Typeface.DEFAULT_BOLD; canvas.drawText("PDF", 14f, 43f, paint)
        }
        canvas.restoreToCount(save)
    }
}
