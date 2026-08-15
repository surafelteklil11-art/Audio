package com.surafel.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View

object ThemeCatalog {
    data class ThemeOption(
        val id: Int,
        val name: String,
        val description: String,
        val colors: IntArray,
        val pictureIndex: Int? = null,
        val tags: Set<String> = emptySet()
    )

    private val gradients = listOf(
        ThemeOption(0, "Nebula Violet", "Deep violet space with electric blue accents", intArrayOf(Color.rgb(10, 9, 29), Color.rgb(31, 11, 58))),
        ThemeOption(1, "Cyber Blue", "Cold blue command-deck interface", intArrayOf(Color.rgb(5, 18, 40), Color.rgb(9, 42, 72))),
        ThemeOption(2, "Midnight Space", "Near-black space with subtle purple depth", intArrayOf(Color.rgb(6, 9, 20), Color.rgb(20, 12, 31))),
        ThemeOption(3, "Arctic Signal", "Clean cyan-white futuristic glass", intArrayOf(Color.rgb(7, 24, 48), Color.rgb(16, 77, 103))),
        ThemeOption(4, "Solar Mist", "Warm peach energy over a dark core", intArrayOf(Color.rgb(34, 14, 24), Color.rgb(91, 44, 35))),
        ThemeOption(5, "Ocean Circuit", "Deep teal with electric blue depth", intArrayOf(Color.rgb(3, 24, 37), Color.rgb(8, 63, 78))),
        ThemeOption(6, "Violet Pulse", "Purple neon command interface", intArrayOf(Color.rgb(20, 6, 42), Color.rgb(71, 16, 92))),
        ThemeOption(7, "Graphite", "Minimal black graphite control deck", intArrayOf(Color.rgb(8, 10, 15), Color.rgb(28, 30, 34)))
    )

    private val pictureNames = listOf(
        "Neon Muse", "Crimson King", "Starry Lake", "Moonlit", "Golden Retriever", "Violet Lake", "Future Drive", "Lavender Bloom",
        "Lunar Explorer", "Cyber Runner", "Mountain Night", "Sunset Forest", "Purple Mist", "Meteor Night", "Galaxy Drift", "Midnight Cat",
        "Red Velocity", "Blue Earth", "Orange GT", "Aurora", "Football Field", "Tropical Escape", "Skyward", "Sunset Silence",
        "Paris Signal", "Moon Horizon", "Sunset Yoga", "Goal Line", "Winter Lighthouse", "Coastal Beacon", "Court Pulse", "Golden Gate", "Street Skater"
    )

    private val pictureTags = listOf(
        "People", "Others", "Starry", "Starry", "Nature", "Nature", "Others", "Nature",
        "Others", "People", "Nature", "Starry", "Nature", "Starry", "Starry", "Nature",
        "Others", "Others", "Others", "Starry", "Others", "Nature", "People", "Nature",
        "Others", "Starry", "People", "Others", "Nature", "Nature", "Others", "Others", "People"
    )

    val all: List<ThemeOption> = gradients + pictureNames.mapIndexed { index, name ->
        val id = gradients.size + index
        ThemeOption(id, name, "Picture theme", intArrayOf(Color.rgb(5, 12, 28), Color.rgb(15, 25, 50)), index, setOf(pictureTags[index], "Others"))
    }

    @Volatile
    private var atlas: Bitmap? = null

    private fun atlas(context: Context): Bitmap? {
        atlas?.let { return it }
        synchronized(this) {
            atlas?.let { return it }
            val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.theme_atlas)
            atlas = decoded
            return decoded
        }
    }

    fun bitmap(context: Context, option: ThemeOption): Bitmap? {
        val index = option.pictureIndex ?: return null
        val source = atlas(context) ?: return null
        val columns = 3
        val rows = 11
        val cellWidth = source.width / columns
        val cellHeight = source.height / rows
        if (index !in 0 until (columns * rows)) return null
        val x = (index % columns) * cellWidth
        val y = (index / columns) * cellHeight
        return Bitmap.createBitmap(source, x, y, cellWidth, cellHeight)
    }

    fun apply(context: Context, root: View, id: Int) {
        val option = all.getOrNull(id) ?: gradients.first()
        val picture = bitmap(context, option)
        if (picture == null) {
            root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, option.colors)
            return
        }
        val image = BitmapDrawable(context.resources, picture).apply {
            gravity = Gravity.CENTER
            alpha = 72
        }
        val overlay = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(220, 2, 7, 22), Color.argb(195, 6, 12, 34), Color.argb(225, 11, 3, 30))
        )
        root.background = LayerDrawable(arrayOf(image, overlay))
    }
}
