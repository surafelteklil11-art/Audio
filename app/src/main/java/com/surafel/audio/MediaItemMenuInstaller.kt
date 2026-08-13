package com.surafel.audio

import android.app.Activity

/** Compatibility hook for the normal media-item overflow menu integration. */
object MediaItemMenuInstaller {
    fun install(activity: Activity) {
        // The existing Audio/Video adapters remain responsible for their item actions.
    }
}
