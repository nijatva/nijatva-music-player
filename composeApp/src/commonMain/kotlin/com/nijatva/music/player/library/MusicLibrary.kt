package com.nijatva.music.player.library

import androidx.compose.runtime.Composable
import com.nijatva.music.player.model.Track

/** A platform's on-device music collection. */
interface MusicLibrary {
    /** Where the tracks come from, shown in Settings: "MediaStore", "~/Music", … */
    val sourceLabel: String

    /** Reads every playable audio file the platform exposes. Called off the UI thread. */
    suspend fun scan(): List<Track>
}

@Composable
expect fun rememberMusicLibrary(): MusicLibrary
