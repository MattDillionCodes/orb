package com.orbdash.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore

/** Finding music on the TV (USB drives and its Music folder, via Android's media index). */
object MusicLibrary {
    /** The permission needed to list music: READ_MEDIA_AUDIO on Android 13+, storage before that. */
    val readPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canRead(context: Context) = context.checkSelfPermission(readPermission) == PackageManager.PERMISSION_GRANTED
    fun canAnalyse(context: Context) = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Music tracks on this TV, A to Z by title. Empty without permission. */
    fun scan(context: Context): List<Track> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<Track>()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += Track(
                        title = c.getString(1) ?: "Untitled",
                        artist = c.getString(2)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                        album = c.getString(3) ?: "",
                        durationMs = c.getLong(4),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    )
                }
            }
        }
        return out
    }
}
