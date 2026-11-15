package com.nijatva.music.player.library

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nijatva.music.player.hasAudioPermission
import com.nijatva.music.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the device's audio library through MediaStore. Nothing is copied or uploaded. */
private class AndroidMusicLibrary(private val context: Context) : MusicLibrary {

    override val sourceLabel: String = "MediaStore"

    @Suppress("DEPRECATION")
    override suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission(context)) return@withContext emptyList<Track>()

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.GENRE)
            add(MediaStore.Audio.Media.ALBUM_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val tracks = mutableListOf<Track>()
        val cursor = context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: return@withContext emptyList<Track>()

        cursor.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val genreCol = c.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val folderCol =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    c.getColumnIndex(MediaStore.Audio.Media.DATA)
                }

            while (c.moveToNext()) {
                val id = if (idCol >= 0) c.getLong(idCol) else 0L
                val albumId = if (albumIdCol >= 0) c.getLong(albumIdCol) else -1L
                val rawFolder = if (folderCol >= 0) c.getString(folderCol).orEmpty() else ""
                val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rawFolder.trimEnd('/')
                } else {
                    rawFolder.substringBeforeLast('/', missingDelimiterValue = "")
                }
                tracks += Track(
                    id = id.toString(),
                    title = if (titleCol >= 0) c.getString(titleCol).orEmpty() else "",
                    artist = if (artistCol >= 0) c.getString(artistCol).orEmpty() else "",
                    album = if (albumCol >= 0) c.getString(albumCol).orEmpty() else "",
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    durationMs = if (durationCol >= 0) c.getLong(durationCol) else 0L,
                    folder = folder,
                    genre = if (genreCol >= 0) c.getString(genreCol) else null,
                    trackNumber = if (trackCol >= 0) c.getInt(trackCol) % 1000 else 0,
                    year = if (yearCol >= 0) c.getInt(yearCol) else 0,
                    artUri = if (albumId > 0) "content://media/external/audio/albumart/$albumId" else null,
                )
            }
        }
        tracks
    }
}

@Composable
actual fun rememberMusicLibrary(): MusicLibrary {
    val context = LocalContext.current
    return remember(context) { AndroidMusicLibrary(context.applicationContext) }
}
