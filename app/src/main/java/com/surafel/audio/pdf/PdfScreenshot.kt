package com.surafel.audio.pdf

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Captures this reader window only, then publishes a PNG in the user's Gallery. */
object PdfScreenshot {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun capture(activity: Activity, complete: (Result<Uri>) -> Unit) {
        val view = activity.window.decorView
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
            complete(Result.failure(IllegalStateException("Wait for the PDF to appear"))); return
        }
        val bitmap = try { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888) }
            catch (_: OutOfMemoryError) { complete(Result.failure(IllegalStateException("Not enough memory for a screenshot"))); return }
        val app = activity.applicationContext
        fun captured() {
            worker.execute {
                val result = runCatching {
                    val name = "AudioPDF_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.png"
                    if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Audio PDF")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = app.contentResolver
                        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: error("Cannot create screenshot")
                        try {
                            (resolver.openOutputStream(uri, "w") ?: error("Cannot save screenshot")).use {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot encode screenshot" }
                            }
                            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            check(resolver.update(uri, values, null, null) == 1) { "Cannot publish screenshot" }
                            uri
                        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
                    } else {
                        @Suppress("DEPRECATION")
                        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Audio PDF")
                        check(folder.isDirectory || folder.mkdirs()) { "Cannot create screenshots folder" }
                        val file = File(folder, name)
                        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot encode screenshot" } } }
                        catch (e: Exception) { file.delete(); throw e }
                        MediaScannerConnection.scanFile(app, arrayOf(file.path), arrayOf("image/png"), null)
                        Uri.fromFile(file)
                    }
                }
                bitmap.recycle(); main.post { complete(result) }
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                PixelCopy.request(activity.window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) captured()
                    else { bitmap.recycle(); complete(Result.failure(IllegalStateException("Screenshot could not be captured. Try again."))) }
                }, main)
            } catch (e: Exception) { bitmap.recycle(); complete(Result.failure(e)) }
        } else {
            try { view.draw(Canvas(bitmap)); captured() }
            catch (e: Exception) { bitmap.recycle(); complete(Result.failure(e)) }
        }
    }
}
