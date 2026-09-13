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
    protected val paper get() = if (dark) 0xFF1E1F23.toInt() else 0xFFF6F8FC.toInt()
    protected val card get() = if (dark) 0xFF292C35.toInt() else Color.WHITE
    protected val blue = 0xFF087CFF.toInt()
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
    protected fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
    protected fun label(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    protected fun shape(color: Int, radius: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    protected fun action(value: String, description: String = value, task: () -> Unit) = label(value, 15f, ink, true).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); minWidth = dp(48); setPadding(dp(8), dp(4), dp(8), dp(4))
        contentDescription = description; isClickable = true; isFocusable = true; setOnClickListener { task() }
        val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)); foreground = attrs.getDrawable(0); attrs.recycle()
    }
    protected fun chromeIcon(name: String, color: Int = ink, size: Int = 24) = PdfChromeIcon(name, color).apply { setBounds(0, 0, dp(size), dp(size)) }
    protected fun iconAction(name: String, description: String, task: () -> Unit): TextView = action("", description, task).apply {
        setPadding(dp(12), dp(12), dp(12), dp(12)); setCompoundDrawables(chromeIcon(name), null, null, null)
    }
    protected fun tabAction(name: String, selected: Boolean = false, size: Float = 12f, task: () -> Unit): TextView = action(name, name, task).apply {
        val color = if (selected) blue else ink
        textSize = size; setTextColor(color); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(5), 0, dp(3)); compoundDrawablePadding = dp(5)
        setCompoundDrawables(null, chromeIcon(name, color), null, null)
    }
    protected fun sheet(title: String, body: View): com.google.android.material.bottomsheet.BottomSheetDialog {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_BottomSheetDialog)
        val panel = column().apply { setPadding(dp(20), dp(10), dp(20), dp(20)); background = shape(paper, 20) }
        panel.addView(View(this).apply { background = shape(muted, 3) }, LinearLayout.LayoutParams(dp(46), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18) })
        if (title.isNotEmpty()) panel.addView(label(title, 15f, muted).apply { setPadding(0, 0, 0, dp(16)) })
        panel.addView(body)
        dialog.setContentView(panel)
        dialog.setOnShowListener { (panel.parent as? View)?.setBackgroundColor(Color.TRANSPARENT); dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED }
        dialog.show(); return dialog
    }
    protected fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    protected fun message(title: String, text: String) { androidx.appcompat.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, if (dark) androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert else androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
    protected fun prompt(title: String, hint: String, initial: String = "", password: Boolean = false, multi: Boolean = false, submit: (String) -> Unit) {
        val input = EditText(this).apply {
            this.hint = hint; setText(initial); setTextColor(ink); setHintTextColor(muted); setPadding(dp(20), dp(12), dp(20), dp(12))
            inputType = if (password) 129 else if (multi) 131073 else 1
            if (multi) { minLines = 4; maxLines = 10; gravity = Gravity.TOP } else isSingleLine = true
            if (password) isSaveEnabled = false
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, if (dark) androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert else androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Continue", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val text = input.text.toString()
            if (text.isBlank()) input.error = "Enter a value" else { dialog.dismiss(); submit(text) }
        } }; dialog.show()
    }
    protected fun confirm(title: String, detail: String, task: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, if (dark) androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert else androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)).setTitle(title).setMessage(detail).setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> task() }.show()
    }
    protected fun choices(title: String, options: List<String>, task: (Int) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, if (dark) androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert else androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)).setTitle(title).setItems(options.toTypedArray()) { _, i -> task(i) }.setNegativeButton("Cancel", null).show()
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
            paint.shader = LinearGradient(4f, 12f, 46f, 62f, intArrayOf(0xFF4BD1FF.toInt(), 0xFF2776DB.toInt()), null, Shader.TileMode.CLAMP)
            val back = Path().apply { moveTo(2f, 16f); quadTo(2f, 12f, 6f, 12f); lineTo(19f, 12f); lineTo(26f, 17f); lineTo(47f, 17f); quadTo(51f, 17f, 51f, 21f); lineTo(51f, 57f); lineTo(2f, 57f); close() }
            canvas.drawPath(back, paint)
            paint.shader = null; paint.color = 0xFFC0E4F8.toInt(); canvas.drawRoundRect(10f, 23f, 51f, 53f, 2f, 2f, paint)
            paint.shader = LinearGradient(18f, 28f, 48f, 59f, intArrayOf(0xFF89D8FF.toInt(), 0xFF68A8E9.toInt()), null, Shader.TileMode.CLAMP)
            val front = Path().apply { moveTo(13f, 28f); lineTo(54f, 28f); quadTo(56f, 28f, 55f, 32f); lineTo(51f, 56f); quadTo(51f, 58f, 47f, 58f); lineTo(7f, 58f); close() }
            canvas.drawPath(front, paint); paint.shader = null
            paint.color = 0xFF76B9EB.toInt(); paint.strokeWidth = 1.2f
            canvas.drawLine(43f, 32f, 50f, 32f, paint); canvas.drawLine(42f, 35f, 49f, 35f, paint)
        } else {
            paint.color = 0xFFFF5465.toInt(); canvas.drawRoundRect(RectF(7f, 4f, 49f, 60f), 5f, 5f, paint)
            paint.color = 0xFFFFB6BE.toInt(); canvas.drawRect(36f, 4f, 49f, 17f, paint)
            paint.color = Color.WHITE; paint.textSize = 13f; paint.typeface = Typeface.DEFAULT_BOLD; canvas.drawText("PDF", 14f, 43f, paint)
        }
        canvas.restoreToCount(save)
    }
}
