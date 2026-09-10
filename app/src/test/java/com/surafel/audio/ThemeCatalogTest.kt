package com.surafel.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeCatalogTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        context.getSharedPreferences("audio_profile", 0).edit().clear().commit()
        ThemeCatalog.customFile(context).delete()
    }

    @Test fun everyThemeRendersVisibleDistinctArtwork() {
        assertEquals((0..40).toList(), ThemeCatalog.all.map { it.id })
        val fingerprints = mutableSetOf<Int>()
        for (theme in ThemeCatalog.all) {
            val bitmap = render(theme.id)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("${theme.name} must not be black", pixels.count { maxOf(Color.red(it), Color.green(it), Color.blue(it)) > 65 } > pixels.size / 3)
            assertTrue("${theme.name} needs its own artwork", fingerprints.add(pixels.contentHashCode()))
            assertTrue(pixels.all { Color.alpha(it) == 255 })
        }
    }

    @Test fun selectedThemeAndPreviewRenderIdentically() {
        val preferences = context.getSharedPreferences("audio_profile", 0)
        preferences.edit().putString("background_mode", "custom").apply()
        ThemeCatalog.select(context, 26)
        assertEquals(26, ThemeCatalog.selectedId(context))
        assertEquals("default", preferences.getString("background_mode", null))
        val view = View(context)
        ThemeCatalog.apply(context, view, ThemeCatalog.selectedId(context))
        val bitmap = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888)
        view.background.setBounds(0, 0, 160, 320)
        view.background.draw(Canvas(bitmap))
        assertTrue(bitmap.sameAs(render(26)))
    }

    @Test fun everyThemeKeepsItsColorsAcrossRepeatedRedraws() {
        for (theme in ThemeCatalog.all) {
            for (alpha in listOf(255, 127)) {
                val drawable = ThemeCatalog.drawable(context, theme.id)
                drawable.setBounds(0, 0, 160, 320)
                drawable.alpha = alpha
                fun frame(): Bitmap = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888).also {
                    drawable.draw(Canvas(it))
                }
                val first = frame()
                repeat(4) { redraw ->
                    val next = frame()
                    assertTrue("${theme.name}, alpha $alpha, redraw $redraw changed colors", first.sameAs(next))
                    next.recycle()
                }
                first.recycle()
            }
        }
    }

    @Test fun invalidStoredIdFallsBackToDefault() {
        assertTrue(render(500).sameAs(render(0)))
        assertTrue(render(ThemeCatalog.CUSTOM_ID).sameAs(render(0)))
    }

    @Test fun invalidImportKeepsPreviousPhotoAndSelection() {
        val bytes = photoBytes()
        ThemeCatalog.importCustom(context, ByteArrayInputStream(bytes))
        ThemeCatalog.select(context, ThemeCatalog.CUSTOM_ID)
        val before = ThemeCatalog.customFile(context).readBytes()
        try {
            ThemeCatalog.importCustom(context, ByteArrayInputStream("not an image".toByteArray()))
            fail("Invalid image must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, ThemeCatalog.customFile(context).readBytes())
        assertEquals(ThemeCatalog.CUSTOM_ID, ThemeCatalog.selectedId(context))
        assertNotNull(ThemeCatalog.customBitmap(context))
    }

    @Test fun validPhotoUsesCenterCropWithoutBlackLetterboxing() {
        ThemeCatalog.importCustom(context, ByteArrayInputStream(photoBytes()))
        val bitmap = render(ThemeCatalog.CUSTOM_ID)
        assertTrue(Color.red(bitmap.getPixel(80, 160)) > 120)
        assertEquals(255, Color.alpha(bitmap.getPixel(0, 160)))
    }

    @Test fun exportArtworkContactSheet() {
        val sheet = Bitmap.createBitmap(840, 7 * 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(16, 22, 37))
        for (theme in ThemeCatalog.all) {
            val x = theme.id % 6 * 140
            val y = theme.id / 6 * 260
            val drawable = ThemeCatalog.drawable(context, theme.id)
            drawable.setBounds(x + 4, y + 4, x + 136, y + 228)
            drawable.draw(canvas)
            paint.color = Color.WHITE; paint.textSize = 11f
            canvas.drawText(theme.name, x + 6f, y + 246f, paint)
        }
        val output = File("build/theme-previews/artwork-api-${android.os.Build.VERSION.SDK_INT}.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun photoBytes(): ByteArray {
        val photo = Bitmap.createBitmap(400, 120, Bitmap.Config.ARGB_8888)
        photo.eraseColor(Color.rgb(240, 120, 60))
        return ByteArrayOutputStream().apply { photo.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
    }
    private fun render(id: Int): Bitmap = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888).also {
        ThemeCatalog.drawable(context, id).apply { setBounds(0, 0, it.width, it.height); draw(Canvas(it)) }
    }
}
