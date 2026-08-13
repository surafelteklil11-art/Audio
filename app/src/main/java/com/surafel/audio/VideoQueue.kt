package com.surafel.audio

import android.content.Context

object VideoQueue {
    private const val PREFS = "video_queue"
    private const val URI = "next_uri"
    private const val TITLE = "next_title"

    fun setNext(context: Context, entry: VideoEntry) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(URI, entry.uri.toString())
            .putString(TITLE, entry.title)
            .apply()
    }

    fun consumeNext(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uri = prefs.getString(URI, null) ?: return null
        val title = prefs.getString(TITLE, "Video") ?: "Video"
        prefs.edit().remove(URI).remove(TITLE).apply()
        return uri to title
    }
}
