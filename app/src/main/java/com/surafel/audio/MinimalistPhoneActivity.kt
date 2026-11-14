package com.surafel.audio

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MinimalistPhoneActivity : AppCompatActivity() {
    private data class AppEntry(
        val label: String,
        val packageName: String,
        val className: String,
        val icon: android.graphics.drawable.Drawable
    )

    private val favorites = linkedSetOf<String>()
    private val apps = mutableListOf<AppEntry>()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var showFavorites = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0
        favorites.addAll(getSharedPreferences("minimalist_phone", MODE_PRIVATE)
            .getStringSet("favorites", emptySet()).orEmpty())
        buildScreen()
        loadApps()
        renderApps()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(12))
            setBackgroundColor(Color.BLACK)
        }

        root.addView(TextView(this).apply {
            text = "Minimalist Phone"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Keep your phone simple."
            textSize = 13f
            setTextColor(Color.rgb(155, 155, 155))
            setPadding(0, dp(5), 0, dp(20))
        })

        search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(135, 135, 135))
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(Color.rgb(28, 28, 28), dp(14))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(12))
        }
        val allTab = makeTab("All apps")
        val favoritesTab = makeTab("Favorites")
        tabs.addView(allTab, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(6) })
        tabs.addView(favoritesTab, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(6) })
        root.addView(tabs)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps() }
            override fun afterTextChanged(s: Editable?) {}
        })
        allTab.setOnClickListener { showFavorites = false; updateTabs(allTab, favoritesTab); renderApps() }
        favoritesTab.setOnClickListener { showFavorites = true; updateTabs(allTab, favoritesTab); renderApps() }
        updateTabs(allTab, favoritesTab)
        setContentView(root)
    }

    private fun makeTab(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(34, 34, 34), dp(12))
    }

    private fun updateTabs(all: TextView, fav: TextView) {
        all.background = rounded(if (!showFavorites) Color.rgb(245, 245, 245) else Color.rgb(34, 34, 34), dp(12))
        all.setTextColor(if (!showFavorites) Color.BLACK else Color.WHITE)
        fav.background = rounded(if (showFavorites) Color.rgb(245, 245, 245) else Color.rgb(34, 34, 34), dp(12))
        fav.setTextColor(if (showFavorites) Color.BLACK else Color.WHITE)
    }

    private fun loadApps() {
        apps.clear()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = try {
            packageManager.queryIntentActivities(launcher, 0)
        } catch (_: Exception) {
            emptyList<ResolveInfo>()
        }
        entries.forEach { info ->
            try {
                val activity = info.activityInfo ?: return@forEach
                if (activity.packageName == packageName) return@forEach
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@forEach
                apps.add(AppEntry(label, activity.packageName, activity.name, info.loadIcon(packageManager)))
            } catch (_: Exception) {
                // Ignore an app that cannot be queried; keep the rest of the screen usable.
            }
        }
        apps.sortBy { it.label.lowercase() }
    }

    private fun renderApps() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = search.text?.toString()?.trim().orEmpty()
        val visible = apps.filter {
            (!showFavorites || favorites.contains(it.packageName)) &&
                (query.isEmpty() || it.label.contains(query, ignoreCase = true))
        }
        if (visible.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (showFavorites) "No favorites yet. Tap ☆ beside an app to add it here."
                    else "No apps found."
                textSize = 14f
                setTextColor(Color.rgb(155, 155, 155))
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
            return
        }
        visible.forEach { entry -> list.addView(makeAppRow(entry)) }
    }

    private fun makeAppRow(entry: AppEntry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.TRANSPARENT, dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { openApp(entry) }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(entry.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(14) })
        row.addView(TextView(this).apply {
            text = entry.label
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(TextView(this).apply {
            text = if (favorites.contains(entry.packageName)) "★" else "☆"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(if (favorites.contains(entry.packageName)) Color.rgb(255, 210, 90) else Color.rgb(150, 150, 150))
            contentDescription = "Toggle favorite"
            setOnClickListener {
                if (favorites.contains(entry.packageName)) favorites.remove(entry.packageName)
                else favorites.add(entry.packageName)
                getSharedPreferences("minimalist_phone", MODE_PRIVATE).edit()
                    .putStringSet("favorites", favorites.toSet()).apply()
                renderApps()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        return row
    }

    private fun openApp(entry: AppEntry) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(entry.packageName, entry.className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "This app could not be opened.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
