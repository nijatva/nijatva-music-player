package com.nijatva.music.player.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nijatva.music.player.model.Track

/**
 * A track handed to the app from outside — "open with Music" on an audio file, or the
 * system default-player picker. The platform sets [request]; the UI plays it and clears it.
 */
object ExternalOpen {
    var request by mutableStateOf<Track?>(null)

    fun clear() {
        request = null
    }
}
