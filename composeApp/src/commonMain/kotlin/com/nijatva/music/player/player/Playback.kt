package com.nijatva.music.player.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nijatva.music.player.model.Track

enum class RepeatMode { Off, All, One }

/**
 * Everything the UI needs from playback.
 *
 * Android backs this with a MediaSession that lives in [com.nijatva.music.player.service.MusicService],
 * so audio survives the app being swiped away and the lock screen, Bluetooth buttons and
 * Assistant all work. Desktop backs it with libVLC in-process.
 */
interface Playback {
    val queue: List<Track>
    val index: Int
    val current: Track?
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    val shuffle: Boolean
    val repeat: RepeatMode
    val sleepMinutes: Int

    fun playNow(tracks: List<Track>, startIndex: Int = 0)
    fun toggle()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun startSleepTimer(minutes: Int)
    fun setEqualizer(gainsMb: List<Int>?)

    /** Called by the UI on a timer: refreshes position and drives the sleep timer. */
    fun tick()

    fun release()
}

/** Counts the sleep timer down; the same implementation on every platform. */
class SleepTimer {
    var minutes by mutableStateOf(0)
        private set

    fun start(minutes: Int) {
        this.minutes = minutes.coerceAtLeast(0)
    }

    /** Advances one second. Returns true on the tick where playback should stop. */
    fun tick(): Boolean {
        if (minutes <= 0) return false
        minutes -= 1
        return minutes == 0
    }
}

@Composable
expect fun rememberPlayback(): Playback
