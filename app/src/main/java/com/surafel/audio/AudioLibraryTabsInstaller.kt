package com.surafel.audio

import android.app.Activity
import android.view.View

/** Routes Audio library tabs to real full pages instead of popup dialogs. */
object AudioLibraryTabsInstaller {
    fun install(activity: Activity) {
        if (activity !is MainActivity) return
        activity.findViewById<View>(R.id.songsTab)?.setOnClickListener { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_SONGS) }
        activity.findViewById<View>(R.id.playlistsTab)?.setOnClickListener { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_PLAYLISTS) }
        activity.findViewById<View>(R.id.foldersTab)?.setOnClickListener { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_FOLDERS) }
        activity.findViewById<View>(R.id.artistsTab)?.setOnClickListener { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_ARTISTS) }
        activity.findViewById<View>(R.id.albumsTab)?.setOnClickListener { AudioLibraryPageActivity.open(activity, AudioLibraryPageActivity.SECTION_ALBUMS) }
    }
}
