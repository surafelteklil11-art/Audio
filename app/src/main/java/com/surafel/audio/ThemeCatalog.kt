package com.surafel.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AtomicFile
import android.view.View
import java.io.File
import java.io.InputStream

/** Stable IDs and a single renderer for gallery, preview and the real player. */
object ThemeCatalog {
    const val CUSTOM_ID = -1
    private const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

    data class ThemeOption(
        val id: Int,
        val name: String,
        val description: String,
        val colors: IntArray,
        val pictureIndex: Int? = null,
        val tags: Set<String> = emptySet(),
        val motif: Int = -1
    )

    private fun colors(vararg values: String) = values.map(Color::parseColor).toIntArray()
    private val palettes = listOf(
        colors("#29114D", "#7946B8", "#20366E"),
        colors("#092B59", "#1879BD", "#14365D"),
        colors("#101B35", "#36496C", "#172238"),
        colors("#103F59", "#3698AD", "#19556B"),
        colors("#683B51", "#C8766F", "#794C70"),
        colors("#123E4C", "#298A85", "#185160"),
        colors("#4D205D", "#B54C94", "#4F336E"),
        colors("#222F3C", "#536578", "#253643"),
        colors("#63412B", "#C69857", "#745748"),
        colors("#173E3C", "#438D66", "#214C47"),
        colors("#39355E", "#817CB2", "#48416E")
    )
    private val gradients = listOf(
        "Amethyst" to "Violet light, indigo depth",
        "Cobalt" to "Clear blue, electric energy",
        "Midnight" to "Quiet slate, soft moonlight",
        "Glacier" to "Cool cyan, open horizons",
        "Rose Quartz" to "Warm peach, muted rose",
        "Lagoon" to "Ocean teal, a calmer rhythm",
        "Orchid" to "Rich berry, a violet glow",
        "Graphite" to "Refined charcoal, silver light"
    ).mapIndexed { id, (name, description) -> ThemeOption(id, name, description, palettes[id]) }

    private val artworkNames = listOf(
        "Aurora Fjord", "Rose Dunes", "Alpine Dawn", "Lunar Tide", "Coral Coast", "Violet Valley",
        "Cobalt Flow", "Lavender Hills", "Orbit", "Prism", "Alpine Blue", "Ember Horizon",
        "Lilac Drift", "Meteor", "Deep Cosmos", "Emerald Ridge", "Crimson Current", "Blue Planet",
        "Amber Waves", "Northern Lights", "Mint Terrace", "Tropical Tide", "Skyline", "Quiet Sunset",
        "Rose Arch", "Moonrise", "Desert Bloom", "Jade Flow", "Winter Peaks", "Coastal Dawn",
        "Indigo Rhythm", "Golden Hour", "Pink Horizon"
    )
    private val motifs = intArrayOf(0, 2, 0, 1, 2, 0, 3, 0, 1, 3, 0, 2, 3, 1, 1, 0, 3, 1, 2, 0, 3, 2, 3, 2, 3, 1, 2, 3, 0, 2, 3, 2, 3)
    private val paletteIds = intArrayOf(5, 4, 3, 1, 4, 0, 1, 10, 0, 6, 1, 8, 10, 6, 2, 9, 6, 1, 8, 9, 3, 5, 3, 4, 6, 2, 6, 9, 3, 1, 0, 8, 6)
    val all: List<ThemeOption> = gradients + artworkNames.mapIndexed { index, name ->
        val category = when (motifs[index]) { 0, 2 -> "Nature"; 1 -> "Space"; else -> "Abstract" }
        ThemeOption(8 + index, name, "$category · original illustration", palettes[paletteIds[index]], index, setOf(category), motifs[index])
    }

    fun option(id: Int): ThemeOption = all.firstOrNull { it.id == id } ?: all.first()
    fun selectedId(context: Context): Int = context.getSharedPreferences("audio_profile", 0).getInt("theme", 0)

    fun select(context: Context, id: Int) {
        require(id == CUSTOM_ID || all.any { it.id == id })
        // A prior Settings wallpaper must not silently override this choice.
        context.getSharedPreferences("audio_profile", 0).edit().putInt("theme", id)
            .putString("background_mode", "default").apply()
    }

    fun drawable(context: Context, id: Int): Drawable {
        if (id == CUSTOM_ID) customBitmap(context)?.let { return ThemeImageDrawable(it) }
        return ThemeArtworkDrawable(option(id))
    }

    fun apply(context: Context, root: View, id: Int) { root.background = drawable(context, id) }
    fun customFile(context: Context) = File(context.filesDir, "custom_theme.img")
    fun hasCustom(context: Context): Boolean = customFile(context).isFile && customFile(context).length() > 0
    fun customBitmap(context: Context): Bitmap? = decodeImage(customFile(context))

    fun decodeImage(file: File): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    /** Validate first; AtomicFile preserves the previous photo on every failed write. */
    @Synchronized
    fun importCustom(context: Context, input: InputStream) {
        val staging = File.createTempFile("theme-import-", ".tmp", context.cacheDir)
        try {
            staging.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_IMPORT_BYTES) { "Choose an image smaller than 20 MB" }
                    output.write(buffer, 0, count)
                }
            }
            val bitmap = decodeImage(staging) ?: throw IllegalArgumentException("This file is not a supported image")
            val target = AtomicFile(customFile(context))
            try {
                val output = target.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
                    target.finishWrite(output)
                } catch (error: Exception) {
                    target.failWrite(output)
                    throw error
                }
            } finally { bitmap.recycle() }
        } finally { staging.delete() }
    }
}
