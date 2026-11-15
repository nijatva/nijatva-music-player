package com.nijatva.music.player.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nijatva.music.player.model.Track
import kotlin.random.Random

/**
 * Queue and transport for the desktop build: the queue lives here and libVLC plays one
 * URI at a time. Android uses a MediaSession instead — see [AndroidPlayback].
 */
class DesktopPlayback(private val player: AudioPlayer) : Playback {

    override var queue by mutableStateOf<List<Track>>(emptyList())
        private set

    override var index by mutableStateOf(-1)
        private set

    override var isPlaying by mutableStateOf(false)
        private set

    override var positionMs by mutableStateOf(0L)
        private set

    override var durationMs by mutableStateOf(0L)
        private set

    override var shuffle by mutableStateOf(false)

    override var repeat by mutableStateOf(RepeatMode.Off)

    private val sleep = SleepTimer()
    override val sleepMinutes: Int get() = sleep.minutes

    override val current: Track? get() = queue.getOrNull(index)

    private var wasPlaying = false

    override fun playNow(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.lastIndex)
        load()
    }

    override fun toggle() {
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

    override fun next() {
        if (queue.isEmpty()) return
        val target = when {
            shuffle && queue.size > 1 -> {
                var candidate = Random.nextInt(queue.size)
                if (candidate == index) candidate = (candidate + 1) % queue.size
                candidate
            }
            index < queue.lastIndex -> index + 1
            repeat == RepeatMode.All -> 0
            else -> {
                player.pause()
                player.seekTo(0)
                isPlaying = false
                positionMs = 0
                wasPlaying = false
                return
            }
        }
        index = target
        load()
    }

    override fun previous() {
        if (queue.isEmpty()) return
        if (positionMs > 3_000L) {
            seekTo(0)
            return
        }
        index = if (index > 0) index - 1 else if (repeat == RepeatMode.All) queue.lastIndex else 0
        load()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        this.positionMs = positionMs.coerceAtLeast(0)
    }

    override fun startSleepTimer(minutes: Int) {
        sleep.start(minutes)
    }

    override fun setEqualizer(gainsMb: List<Int>?) {
        player.setEqualizer(gainsMb)
    }

    override fun tick() {
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
        if (sleep.tick() && isPlaying) {
            player.pause()
            isPlaying = false
            wasPlaying = false
        }
    }

    override fun release() {
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
