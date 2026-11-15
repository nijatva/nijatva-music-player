package com.nijatva.music.player.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nijatva.music.player.model.Track
import kotlin.random.Random

/**
 * The only thing the shared UI needs from a platform audio engine.
 *
 * The shared code owns the queue, shuffle, repeat and sleep timer; a platform only
 * has to play one URI at a time and report where it is.
 */
interface AudioPlayer {
    fun open(uri: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
    fun positionMs(): Long
    fun durationMs(): Long
    fun isPlaying(): Boolean

    /** Five band gains in millibels (60..-60 typical); null resets to flat. No-op where unsupported. */
    fun setEqualizer(gainsMb: List<Int>?) {}
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer

enum class RepeatMode { Off, All, One }

/** Queue + transport state shared by every platform. */
class PlaybackController(private val player: AudioPlayer) {

    var queue by mutableStateOf<List<Track>>(emptyList())
        private set

    var index by mutableStateOf(-1)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var positionMs by mutableStateOf(0L)
        private set

    var durationMs by mutableStateOf(0L)
        private set

    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(RepeatMode.Off)
    var sleepMinutes by mutableStateOf(0)
        private set

    private var wasPlaying = false

    val current: Track? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = queue.isNotEmpty() && (index < queue.lastIndex || repeat == RepeatMode.All || shuffle)
    val hasPrevious: Boolean get() = queue.isNotEmpty() && (index > 0 || positionMs > 3_000L)

    /** Replaces the queue and starts playing at [startIndex]. */
    fun playNow(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.lastIndex)
        load()
    }

    /** Plays [track], using [context] as the queue when that list contains it. */
    fun playTrack(track: Track, context: List<Track>) {
        val start = context.indexOfFirst { it.id == track.id }
        if (start >= 0) playNow(context, start) else playNow(listOf(track), 0)
    }

    fun toggle() {
        val track = current ?: return
        if (isPlaying) {
            player.pause()
            isPlaying = false
            wasPlaying = false
        } else {
            if (positionMs == 0L) player.open(track.uri)
            player.play()
            isPlaying = true
            wasPlaying = true
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        index = when {
            shuffle && queue.size > 1 -> {
                var candidate = Random.nextInt(queue.size)
                if (candidate == index) candidate = (candidate + 1) % queue.size
                candidate
            }
            index < queue.lastIndex -> index + 1
            repeat == RepeatMode.All -> 0
            else -> {
                // End of queue with repeat off: stop cleanly.
                player.pause()
                player.seekTo(0)
                isPlaying = false
                positionMs = 0
                wasPlaying = false
                return
            }
        }
        load()
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (positionMs > 3_000L) {
            seekTo(0)
            return
        }
        index = if (index > 0) index - 1 else if (repeat == RepeatMode.All) queue.lastIndex else 0
        load()
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms.coerceAtLeast(0)
    }

    fun startSleepTimer(minutes: Int) {
        sleepMinutes = minutes.coerceAtLeast(0)
    }

    fun tickSleep() {
        if (sleepMinutes <= 0) return
        sleepMinutes -= 1
        if (sleepMinutes == 0 && isPlaying) {
            player.pause()
            isPlaying = false
            wasPlaying = false
        }
    }

    /** Called on a timer by the UI: refreshes position and advances at end of track. */
    fun tick() {
        val track = current ?: return
        positionMs = player.positionMs()
        val reported = player.durationMs()
        if (reported > 0) durationMs = reported else if (durationMs == 0L) durationMs = track.durationMs
        val playing = player.isPlaying()
        if (wasPlaying && !playing && durationMs > 0 && positionMs >= durationMs - 1_500L) {
            when (repeat) {
                RepeatMode.One -> {
                    player.seekTo(0)
                    player.play()
                    isPlaying = true
                    positionMs = 0
                    wasPlaying = true
                }
                else -> next()
            }
            return
        }
        wasPlaying = playing
        isPlaying = playing
    }

    fun release() {
        player.release()
    }

    private fun load() {
        val track = current ?: return
        durationMs = track.durationMs
        positionMs = 0
        player.open(track.uri)
        player.play()
        isPlaying = true
        wasPlaying = true
    }
}
