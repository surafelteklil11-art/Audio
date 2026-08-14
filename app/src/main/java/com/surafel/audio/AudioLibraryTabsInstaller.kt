package com.surafel.audio

import android.app.Activity
import android.view.View

/**
 * Final wiring for the Audio library tabs.
 *
 * IMPORTANT: MainActivity owns the original Songs screen.  The other library
 * tabs are full destinations, not AlertDialogs or transient popups.  This
 * installer runs from Application.onActivityResumed, after MainActivity has
 * finished its own onCreate listeners, so these handlers are the final ones.
 */
object AudioLibraryTabsInstaller {
    fun install(activity: Activity) {
        if (activity !is MainActivity) return

        bind(activity, R.id.songsTab) { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_SONGS) }
        bind(activity, R.id.playlistsTab) { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_PLAYLISTS) }
        bind(activity, R.id.foldersTab) { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_FOLDERS) }
        bind(activity, R.id.artistsTab) { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_ARTISTS) }
        bind(activity, R.id.albumsTab) { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_ALBUMS) }
    }

    private fun bind(activity: Activity, id: Int, action: () -> Unit) {
        activity.findViewById<View>(id)?.apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }
}
