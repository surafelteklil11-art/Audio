package com.surafel.audio

import android.app.Activity
import android.app.AlertDialog
import android.provider.MediaStore
import android.view.View
import androidx.media3.common.MediaItem
import java.io.File

/** Makes the Audio library tabs functional while preserving MainActivity's existing player flow. */
object AudioLibraryTabsInstaller {
    fun install(activity: Activity) {
        if (activity !is MainActivity) return
        activity.findViewById<View>(R.id.songsTab)?.setOnClickListener { showSongs(activity) }
        activity.findViewById<View>(R.id.playlistsTab)?.setOnClickListener { showPlaylists(activity) }
        activity.findViewById<View>(R.id.foldersTab)?.setOnClickListener { showFolders(activity) }
        activity.findViewById<View>(R.id.artistsTab)?.setOnClickListener { showArtists(activity) }
        activity.findViewById<View>(R.id.albumsTab)?.setOnClickListener { showAlbums(activity) }
    }

    private fun showSongs(activity: MainActivity) {
        activity.findViewById<View>(R.id.musicNav)?.performClick()
    }

    private fun showPlaylists(activity: MainActivity) {
        val rows = mutableListOf<Pair<Long, String>>()
        val uri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        activity.contentResolver.query(uri, arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME), null, null, "${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while (c.moveToNext()) rows += c.getLong(id) to (c.getString(name) ?: "Unnamed playlist")
        }
        if (rows.isEmpty()) {
            info(activity, "Playlists", "No playlists found on this device.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Playlists").setItems(rows.map { it.second }.toTypedArray()) { _, which -> playPlaylist(activity, rows[which].first) }.setNegativeButton("Close", null).show()
    }

    private fun playPlaylist(activity: MainActivity, playlistId: Long) {
        val members = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
        val items = mutableListOf<MediaItem>()
        activity.contentResolver.query(members, arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST), null, null, "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) items += buildItem(c.getLong(id), c.getString(title), c.getString(artist))
        }
        showPlayableList(activity, "Playlist", items)
    }

    private fun showFolders(activity: MainActivity) {
        val folders = linkedSetOf<String>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        activity.contentResolver.query(base, arrayOf(MediaStore.Audio.Media.DATA), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATA} COLLATE NOCASE ASC")?.use { c ->
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: continue
                folders += File(path).parent ?: "/"
            }
        }
        val values = folders.toList().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (values.isEmpty()) {
            info(activity, "Folders", "No audio folders found.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Audio Folders").setItems(values.toTypedArray()) { _, which -> showFolderSongs(activity, values[which]) }.setNegativeButton("Close", null).show()
    }

    private fun showFolderSongs(activity: MainActivity, folder: String) {
        val songs = mutableListOf<MediaItem>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA)
        activity.contentResolver.query(base, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: continue
                if (File(path).parent == folder) songs += buildItem(c.getLong(id), c.getString(title), c.getString(artist))
            }
        }
        showPlayableList(activity, folder.substringAfterLast('/').ifBlank { folder }, songs)
    }

    private fun showArtists(activity: MainActivity) {
        val artists = linkedSetOf<String>()
        activity.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.ARTIST), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC")?.use { c ->
            while (c.moveToNext()) artists += c.getString(0) ?: "Unknown artist"
        }
        val values = artists.filter { it.isNotBlank() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (values.isEmpty()) {
            info(activity, "Artists", "No artists found.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Artists").setItems(values.toTypedArray()) { _, which -> showFilteredSongs(activity, "Artist • ${values[which]}", MediaStore.Audio.Media.ARTIST, values[which]) }.setNegativeButton("Close", null).show()
    }

    private fun showAlbums(activity: MainActivity) {
        val albums = linkedSetOf<String>()
        activity.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.ALBUM), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC")?.use { c ->
            while (c.moveToNext()) albums += c.getString(0) ?: "Unknown album"
        }
        val values = albums.filter { it.isNotBlank() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (values.isEmpty()) {
            info(activity, "Albums", "No albums found.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Albums").setItems(values.toTypedArray()) { _, which -> showFilteredSongs(activity, "Album • ${values[which]}", MediaStore.Audio.Media.ALBUM, values[which]) }.setNegativeButton("Close", null).show()
    }

    private fun showFilteredSongs(activity: MainActivity, title: String, column: String, value: String) {
        val songs = mutableListOf<MediaItem>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        activity.contentResolver.query(base, projection, "$column = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0", arrayOf(value), "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val t = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val a = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) songs += buildItem(c.getLong(id), c.getString(t), c.getString(a))
        }
        showPlayableList(activity, title, songs)
    }

    private fun showPlayableList(activity: MainActivity, title: String, songs: List<MediaItem>) {
        if (songs.isEmpty()) {
            info(activity, title, "No audio items found.")
            return
        }
        val names = songs.map { it.mediaMetadata.title?.toString() ?: "Unknown" }.toTypedArray()
        AlertDialog.Builder(activity).setTitle("$title • ${songs.size}").setItems(names) { _, which ->
            val future = androidx.media3.session.MediaController.Builder(activity, androidx.media3.session.SessionToken(activity, android.content.ComponentName(activity, PlaybackService::class.java))).buildAsync()
            future.addListener({ runCatching { future.get().apply { setMediaItems(songs, which, 0L); prepare(); play(); release() } } }, activity.mainExecutor)
        }.setNegativeButton("Close", null).show()
    }

    private fun buildItem(id: Long, title: String?, artist: String?): MediaItem = MediaItem.Builder().setUri(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title ?: "Unknown").setArtist(artist ?: "Unknown artist").build()).build()

    private fun info(activity: Activity, title: String, message: String) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
