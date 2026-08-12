package com.surafel.audio

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast

/** Installs the ten-tap gate without changing MainActivity's existing click wiring. */
class VaultHookProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) installGate(activity)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    private fun installGate(activity: MainActivity) {
        val dot = activity.findViewById<View>(R.id.weeklyReportDot) ?: return
        if (dot.getTag(TAG) != null) return
        dot.setTag(TAG, GateState())
        dot.setOnClickListener { view ->
            val state = view.getTag(TAG) as GateState
            state.count++
            if (state.count >= 10) {
                state.count = 0
                activity.startActivity(Intent(activity, VaultActivity::class.java))
            } else if (state.count >= 7) {
                Toast.makeText(activity, "${10 - state.count} more taps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class GateState(var count: Int = 0)
    companion object { private const val TAG = 0x61756469 }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
