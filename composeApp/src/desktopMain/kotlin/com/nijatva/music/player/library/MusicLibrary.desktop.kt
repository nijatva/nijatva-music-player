package com.nijatva.music.player.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nijatva.music.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop library: every audio file under ~/Music (and ~/Downloads if it holds music).
 *
 * The desktop build reads no tags — title comes from the file name and album from the
 * folder name. Android gets real tags from MediaStore.
 */
private class DesktopMusicLibrary : MusicLibrary {

    override val sourceLabel: String = "~/Music"

    private val extensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "mp4")

    override suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val home = System.getProperty("user.home") ?: return@withContext emptyList<Track>()
        val roots = listOf(File(home, "Music"), File(home, "Downloads")).filter { it.isDirectory }

        val tracks = mutableListOf<Track>()
        for (root in roots) {
            root.walkTopDown()
                .onEnter { it.name != "." && !it.name.startsWith(".") }
                .filter { it.isFile && it.extension.lowercase() in extensions }
                .forEach { file ->
                    val parent = file.parentFile
                    tracks += Track(
                        id = file.absolutePath,
                        title = file.nameWithoutExtension,
                        artist = "",
                        album = parent?.name ?: "",
                        uri = file.absolutePath,
                        durationMs = 0L,
                        folder = parent?.absolutePath ?: "",
                    )
                }
        }
        tracks.sortedBy { it.displayTitle.lowercase() }
    }
}

@Composable
actual fun rememberMusicLibrary(): MusicLibrary = remember { DesktopMusicLibrary() }
