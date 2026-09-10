package com.surafel.audio.pdf

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

object PdfSharing {
    fun share(activity: Activity, file: File, name: String, mime: String = "application/pdf") {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.pdf-files", file)
        val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(name, uri) }
        activity.startActivity(Intent.createChooser(intent, "Share $name"))
    }
    fun print(activity: Activity, file: File, name: String) {
        val context = activity.applicationContext
        val adapter = object : PrintDocumentAdapter() {
            private val worker = Executors.newSingleThreadExecutor()
            private val main = Handler(Looper.getMainLooper())
            private var count = 0
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancellation: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                worker.execute {
                    try {
                        PdfTools(context).load(file).use { require(it.currentAccessPermission.canPrint()) { "This PDF does not allow printing" }; count = it.numberOfPages }
                        main.post { if (cancellation.isCanceled) callback.onLayoutCancelled() else callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(count).build(), true) }
                    } catch (e: Exception) { main.post { callback.onLayoutFailed(PdfLibraryModel.errorMessage(e)) } }
                }
            }
            override fun onWrite(ranges: Array<out PageRange>, destination: ParcelFileDescriptor, cancellation: CancellationSignal, callback: WriteResultCallback) {
                worker.execute {
                    var temp: File? = null
                    try {
                        if (cancellation.isCanceled) { main.post { callback.onWriteCancelled() }; return@execute }
                        val pages = (0 until count).filter { p -> ranges.any { p in it.start..it.end } }
                        require(pages.isNotEmpty()) { "No pages selected" }
                        val source = if (pages.size == count) file else PdfTools(context).let { tools -> tools.temp().also { temp = it; tools.pages(file, it, pages) } }
                        source.inputStream().use { input -> ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                            val buffer = ByteArray(65536)
                            while (!cancellation.isCanceled) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                        } }
                        main.post { if (cancellation.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(ranges.toList().toTypedArray()) }
                    } catch (e: Exception) { main.post { callback.onWriteFailed(PdfLibraryModel.errorMessage(e)) } }
                    finally { temp?.delete() }
                }
            }
            override fun onFinish() { worker.shutdown() }
        }
        (activity.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(name, adapter, null)
    }
}
