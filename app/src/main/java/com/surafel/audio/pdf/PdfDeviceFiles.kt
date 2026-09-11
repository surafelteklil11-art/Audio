package com.surafel.audio.pdf

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.util.ArrayDeque

/** Discovers references, never imports, moves or deletes the user's device documents. */
object PdfDeviceFiles {
    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun roots(context: Context): List<File> {
        val roots = mutableListOf(Environment.getExternalStorageDirectory())
        if (Build.VERSION.SDK_INT >= 30) {
            (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager).storageVolumes.mapNotNullTo(roots) { it.directory }
        } else context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            val marker = dir.absolutePath.indexOf("/Android/")
            if (marker > 0) roots.add(File(dir.absolutePath.substring(0, marker)))
        }
        return roots.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }.distinctBy { it.path }
    }
    fun isSharedPdf(context: Context, file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return file.extension.equals("pdf", true) && roots(context).any { root ->
            path.startsWith(root.path + "/") && path.removePrefix(root.path + "/").let { relative -> !relative.split('/').any { it.startsWith('.') } && (!relative.startsWith("Android/", true) || relative.startsWith("Android/media/", true)) }
        }
    }
    data class Scan(val files: List<File>, val limited: Boolean)
    fun scan(context: Context): Scan {
        if (!hasAccess(context)) return Scan(emptyList(), false)
        return scanRoots(roots(context))
    }
    internal fun scanRoots(roots: List<File>): Scan {
        val result = mutableListOf<File>(); val pending = ArrayDeque<Pair<File, Int>>()
        roots.forEach { pending.add(it to 0) }; val seen = hashSetOf<String>(); var visited = 0; var limited = false
        while (pending.isNotEmpty()) {
            check(!Thread.currentThread().isInterrupted) { "Scan cancelled" }
            val (directory, depth) = pending.removeFirst()
            val canonical = runCatching { directory.canonicalPath }.getOrNull() ?: continue
            if (!seen.add(canonical)) continue
            val children = directory.listFiles() ?: continue
            for (file in children) {
                if (++visited > 150000 || result.size >= 20000) { limited = true; break }
                if (file.name.startsWith('.')) continue
                if (file.name.equals("Android", true)) {
                    val media = File(file, "media")
                    if (media.isDirectory && media.canonicalPath == media.absolutePath) pending.add(media to depth + 1)
                    continue
                }
                // Do not follow links out of shared storage or around folder cycles.
                if (runCatching { file.canonicalPath != file.absolutePath }.getOrDefault(true)) continue
                if (file.isDirectory) { if (depth < 24) pending.add(file to depth + 1) else limited = true }
                else if (file.isFile && file.extension.equals("pdf", true) && file.canRead()) result.add(file)
            }
            if (visited > 150000 || result.size >= 20000) break
        }
        return Scan(result, limited)
    }
}
