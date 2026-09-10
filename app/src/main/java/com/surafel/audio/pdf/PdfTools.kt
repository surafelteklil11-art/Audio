package com.surafel.audio.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.*

/** Called only by the PDF worker. Inputs are immutable; every operation writes a new file. */
class PdfTools(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }
    private fun memory() = MemoryUsageSetting.setupMixed(8L * 1024 * 1024).setTempDir(context.cacheDir)
    fun load(file: File, password: String = ""): PDDocument = PDDocument.load(file, password, memory())
    fun temp(suffix: String = ".pdf") = File.createTempFile("pdf-", suffix, File(context.cacheDir, "pdf_work").apply { mkdirs() })
    fun pageCount(file: File, password: String = ""): Int = load(file, password).use { it.numberOfPages }
    fun extract(file: File, password: String = "", query: String? = null): String = load(file, password).use { doc ->
        require(doc.currentAccessPermission.canExtractContent()) { "This PDF does not allow text extraction" }
        val stripper = PDFTextStripper()
        if (query == null) {
            require(doc.numberOfPages <= 300) { "Extract text from a smaller PDF (up to 300 pages)" }
            stripper.getText(doc).also { require(it.length <= 300000) { "Too much text; split the PDF first" } }
        } else buildString {
            for (page in 1..doc.numberOfPages) {
                check(!Thread.currentThread().isInterrupted)
                stripper.startPage = page; stripper.endPage = page
                val content = stripper.getText(doc)
                if (content.contains(query, true)) append("Page $page\n" + content.lineSequence().filter { it.contains(query, true) }.take(3).joinToString("\n") + "\n\n")
                if (length > 30000) break
            }
        }
    }
    fun pages(file: File, output: File, order: List<Int>, rotate: Boolean = false, password: String = "") {
        load(file, password).use { source ->
            require(source.currentAccessPermission.canAssembleDocument()) { "This PDF does not allow page changes" }
            require(order.isNotEmpty() && order.size <= 2000 && order.all { it in 0 until source.numberOfPages })
            PDDocument(memory()).use { target ->
                order.forEach { index -> target.importPage(source.getPage(index)).also { page ->
                    page.resources = source.getPage(index).resources
                    if (rotate) page.rotation = (page.rotation + 90) % 360
                } }
                target.save(output)
            }
        }
    }
    fun merge(files: List<File>, output: File) {
        require(files.size in 2..30) { "Select 2–30 PDFs" }
        // Keep sources open until save: imported resources can reference their scratch storage.
        val sources = mutableListOf<PDDocument>()
        try {
            PDDocument(memory()).use { target ->
                files.forEach { file ->
                    val source = load(file); sources.add(source)
                    require(source.currentAccessPermission.canAssembleDocument()) { "A PDF does not allow merging" }
                    require(target.numberOfPages + source.numberOfPages <= 2000) { "Merge up to 2,000 pages at a time" }
                    for (page in source.pages) target.importPage(page).resources = page.resources
                }
                target.save(output)
            }
        } finally { sources.forEach { it.close() } }
    }
    fun lock(file: File, output: File, password: String) {
        require(password.length in 6..64) { "Use a password of 6–64 characters" }
        load(file).use { doc ->
            require(doc.currentAccessPermission.isOwnerPermission) { "Owner permission is required" }
            val protection = StandardProtectionPolicy(password, password, AccessPermission())
            protection.encryptionKeyLength = 256; protection.isPreferAES = true
            doc.protect(protection); doc.save(output)
        }
    }
    fun unlock(file: File, output: File, password: String) {
        load(file, password).use { doc ->
            require(doc.currentAccessPermission.isOwnerPermission) { "Use the owner's password to remove protection" }
            doc.isAllSecurityToBeRemoved = true; doc.save(output)
        }
    }
    fun textPdf(text: String, output: File) {
        require(text.isNotBlank() && text.length <= 100000) { "Enter text (up to 100,000 characters)" }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 499).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        val doc = PdfDocument()
        try {
            var start = 0; var number = 1
            while (start < layout.lineCount) {
                val top = layout.getLineTop(start); var end = start + 1
                while (end < layout.lineCount && layout.getLineBottom(end) - top <= 746) end++
                val bottom = layout.getLineTop(end)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, number++).create())
                page.canvas.save(); page.canvas.translate(48f, 48f); page.canvas.clipRect(0, 0, 499, bottom - top)
                page.canvas.translate(0f, -top.toFloat()); layout.draw(page.canvas); page.canvas.restore()
                doc.finishPage(page); start = end
            }
            output.outputStream().use(doc::writeTo)
        } finally { doc.close() }
    }
    fun images(files: List<File>, output: File) {
        require(files.size in 1..100) { "Choose 1–100 images" }
        PDDocument(memory()).use { doc ->
            files.forEach { file ->
                val bitmap = image(file, 2200)
                try {
                    val scale = min(595f / bitmap.width, 842f / bitmap.height)
                    val w = bitmap.width * scale; val h = bitmap.height * scale
                    val page = PDPage(PDRectangle(w, h)); doc.addPage(page)
                    PDPageContentStream(doc, page).use { it.drawImage(JPEGFactory.createFromImage(doc, bitmap, .9f), 0f, 0f, w, h) }
                } finally { bitmap.recycle() }
            }
            doc.save(output)
        }
    }
    fun overlay(file: File, output: File, index: Int, bitmap: Bitmap, password: String = "") {
        load(file, password).use { doc ->
            require(doc.currentAccessPermission.canModify()) { "This PDF does not allow editing" }
            val page = doc.getPage(index); val box = page.cropBox
            // Canvas marks use the rotated display coordinates; transform to PDF user space.
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                val matrix = when ((page.rotation % 360 + 360) % 360) {
                    90 -> com.tom_roush.pdfbox.util.Matrix(0f, 1f, -1f, 0f, box.upperRightX, box.lowerLeftY)
                    180 -> com.tom_roush.pdfbox.util.Matrix(-1f, 0f, 0f, -1f, box.upperRightX, box.upperRightY)
                    270 -> com.tom_roush.pdfbox.util.Matrix(0f, -1f, 1f, 0f, box.lowerLeftX, box.upperRightY)
                    else -> com.tom_roush.pdfbox.util.Matrix(1f, 0f, 0f, 1f, box.lowerLeftX, box.lowerLeftY)
                }
                stream.transform(matrix)
                val sideways = page.rotation % 180 != 0
                stream.drawImage(LosslessFactory.createFromImage(doc, bitmap), 0f, 0f, if (sideways) box.height else box.width, if (sideways) box.width else box.height)
            }
            doc.save(output)
        }
    }
    fun compress(file: File, output: File) {
        load(file).use { require(it.currentAccessPermission.canExtractContent()) { "This PDF does not allow image conversion" } }
        NativePdf(file).use { source ->
            PDDocument(memory()).use { target ->
                for (i in 0 until source.count) {
                    val bitmap = source.render(i, 1000)
                    try {
                        val page = PDPage(PDRectangle(595f, 595f * bitmap.height / bitmap.width)); target.addPage(page)
                        PDPageContentStream(target, page).use { it.drawImage(JPEGFactory.createFromImage(target, bitmap, .55f), 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                    } finally { bitmap.recycle() }
                }
                target.save(output)
            }
        }
    }
    fun toImages(file: File, output: File, longImage: Boolean) {
        load(file).use { require(it.currentAccessPermission.canExtractContent()) { "This PDF does not allow image conversion" } }
        NativePdf(file).use { source ->
            if (!longImage) {
                ZipOutputStream(output.outputStream()).use { zip ->
                    for (i in 0 until source.count) {
                        val bitmap = source.render(i, 1400)
                        try { zip.putNextEntry(ZipEntry("page-${i + 1}.png")); bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry() }
                        finally { bitmap.recycle() }
                    }
                }
            } else {
                require(source.count <= 20) { "Long images support up to 20 pages; split this PDF first" }
                val ratios = (0 until source.count).map { source.size(it).let { p -> p.second.toDouble() / p.first } }
                val width = min(1000, min(sqrt(12_000_000.0 / ratios.sum()).toInt(), (30000 / ratios.sum()).toInt())).coerceAtLeast(1)
                val heights = ratios.map { (it * width).roundToInt().coerceAtLeast(1) }
                val bitmap = Bitmap.createBitmap(width, heights.sum(), Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE); var y = 0
                    for (i in 0 until source.count) {
                        val page = source.render(i, width)
                        try { canvas.drawBitmap(page, null, Rect(0, y, width, y + heights[i]), Paint(Paint.FILTER_BITMAP_FLAG)) }
                        finally { page.recycle() }; y += heights[i]
                    }
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        }
    }
    companion object {
        fun parsePages(value: String, count: Int): List<Int> {
            require(count > 0)
            val result = mutableListOf<Int>()
            value.split(',').forEach { token ->
                val parts = token.trim().split('-'); require(parts.size in 1..2) { "Use page numbers such as 1,3-5" }
                val first = parts[0].trim().toIntOrNull() ?: error("Enter a page number")
                val last = if (parts.size == 2) parts[1].trim().toIntOrNull() ?: error("Enter a page number") else first
                require(first in 1..count && last in first..count) { "Page numbers must be between 1 and $count" }
                require(result.size + last - first + 1 <= 2000) { "Choose up to 2,000 pages" }
                result.addAll((first..last).map { it - 1 })
            }
            require(result.isNotEmpty()); return result
        }
        fun image(file: File, maxSide: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
            var sample = 1; while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Could not read image")
            val orientation = runCatching { android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix()
            when (orientation) {
                2 -> matrix.setScale(-1f, 1f)
                3 -> matrix.setRotate(180f)
                4 -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
                5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                6 -> matrix.setRotate(90f)
                7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                8 -> matrix.setRotate(-90f)
            }
            if (matrix.isIdentity) return decoded
            return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        }
    }
}

/** One renderer per worker. At most one native page is open, with bounded bitmap allocation. */
class NativePdf(file: File) : java.io.Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (e: Throwable) { descriptor.close(); throw e }
    val count: Int get() = renderer.pageCount
    fun size(index: Int): Pair<Int, Int> = renderer.openPage(index).use { it.width to it.height }
    fun render(index: Int, requestedWidth: Int): Bitmap = renderer.openPage(index).use { page ->
        val ratio = page.height.toDouble() / page.width
        val width = min(requestedWidth.coerceIn(100, 2400), min(sqrt(4_000_000.0 / ratio).toInt(), (12000 / ratio).toInt())).coerceAtLeast(1)
        val height = (width * ratio).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bitmap }
        catch (e: Throwable) { bitmap.recycle(); throw e }
    }
    override fun close() { renderer.close() }
}
