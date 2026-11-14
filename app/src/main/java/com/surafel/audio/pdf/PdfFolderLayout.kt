package com.surafel.audio.pdf

import java.io.File

/** Presentation of cached folders; never moves files or scans storage. */
object PdfFolderLayout {
    data class Summary(val children: Int, val bytes: Long)

    fun summaries(entries: List<PdfLibrary.Entry>): Map<String, Summary> {
        val active = entries.filterNot { it.trashed || it.sourceMissing }
        val children = active.groupBy { it.folder }
        val totals = mutableMapOf<String, Summary>()
        val visiting = hashSetOf<String>()
        fun total(id: String): Summary {
            totals[id]?.let { return it }
            if (!visiting.add(id)) return Summary(0, 0)
            val items = children[id].orEmpty()
            val result = Summary(items.size, items.sumOf { if (it.isFolder) total(it.id).bytes else it.bytes })
            visiting.remove(id); totals[id] = result
            return result
        }
        active.filter { it.isFolder }.forEach { total(it.id) }
        return totals
    }

    // The reference app stores its organized collection in .../pdfreader/folder.
    // Use that existing collection as Home rather than displaying storage wrappers.
    fun suggestedHome(entries: List<PdfLibrary.Entry>): String? {
        val candidates = entries.filter { entry ->
            val directory = File(entry.sourcePath)
            entry.isFolder && !entry.trashed && !entry.sourceMissing &&
                directory.name.equals("folder", true) && directory.parentFile?.name.equals("pdfreader", true)
        }
        // Multiple collections need an explicit user choice; never hide one arbitrarily.
        return candidates.singleOrNull()?.id
    }

    fun contains(folder: String, root: String, entries: List<PdfLibrary.Entry>): Boolean {
        if (root.isEmpty()) return true
        val byId = entries.associateBy { it.id }; val seen = hashSetOf<String>()
        var current = folder
        while (current.isNotEmpty() && seen.add(current)) {
            if (current == root) return true
            current = byId[current]?.folder ?: return false
        }
        return false
    }

    fun trail(folder: String, home: String, entries: List<PdfLibrary.Entry>): List<PdfLibrary.Entry> {
        val root = if (contains(folder, home, entries)) home else ""
        val byId = entries.associateBy { it.id }; val seen = hashSetOf<String>()
        val path = mutableListOf<PdfLibrary.Entry>(); var current = folder
        while (current.isNotEmpty() && current != root && seen.add(current)) {
            val entry = byId[current] ?: break
            path.add(entry); current = entry.folder
        }
        return path.asReversed()
    }

    /** Grade 9 precedes Grade 10; ordinary names retain case-insensitive ordering. */
    private val nameParts = Regex("[0-9]+|[^0-9]+")
    val names: Comparator<String> = Comparator { first, second ->
        val a = nameParts.findAll(first).map { it.value }.toList()
        val b = nameParts.findAll(second).map { it.value }.toList()
        var result = 0
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]; val y = b[i]
            result = if (x[0] in '0'..'9' && y[0] in '0'..'9') {
                val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
            } else x.compareTo(y, ignoreCase = true)
            if (result != 0) break
        }
        if (result != 0) result else a.size.compareTo(b.size).takeIf { it != 0 } ?: first.compareTo(second, ignoreCase = true)
    }
}
