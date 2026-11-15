package com.nijatva.music.player.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.nijatva.music.player.model.Track
import com.nijatva.music.player.service.MusicService

/**
 * Android playback: a thin proxy over the MediaController bound to [MusicService].
 *
 * The queue is handed to the session as MediaItems, so ExoPlayer itself owns next,
 * previous, shuffle and repeat — which is why the lock screen, Bluetooth buttons and
 * Assistant stay in sync with what the app shows.
 */
class AndroidPlayback(context: Context) : Playback {

    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private val sleep = SleepTimer()

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

    private var shuffleState by mutableStateOf(false)
    private var repeatState by mutableStateOf(RepeatMode.Off)

    override var shuffle: Boolean
        get() = shuffleState
        set(value) {
            shuffleState = value
            controller?.shuffleModeEnabled = value
        }

    override var repeat: RepeatMode
        get() = repeatState
        set(value) {
            repeatState = value
            controller?.repeatMode = when (value) {
                RepeatMode.Off -> Player.REPEAT_MODE_OFF
                RepeatMode.All -> Player.REPEAT_MODE_ALL
                RepeatMode.One -> Player.REPEAT_MODE_ONE
            }
        }

    override val sleepMinutes: Int get() = sleep.minutes

    override val current: Track? get() = queue.getOrNull(index)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            val position = queue.indexOfFirst { it.id == id }
            if (position >= 0) {
                index = position
                durationMs = queue[position].durationMs
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromController()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeatState = repeatMode.toRepeatMode()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleState = shuffleModeEnabled
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val built = runCatching { future.get() }.getOrNull()
                if (built != null) {
                    controller = built
                    built.addListener(listener)
                    syncFromController()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun syncFromController() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        positionMs = c.currentPosition.coerceAtLeast(0L)
        val reported = c.duration
        if (reported > 0) durationMs = reported
        repeatState = c.repeatMode.toRepeatMode()
        shuffleState = c.shuffleModeEnabled
        val id = c.currentMediaItem?.mediaId
        val position = queue.indexOfFirst { it.id == id }
        if (position >= 0) index = position
    }

    override fun playNow(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val c = controller ?: return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.lastIndex)
        durationMs = tracks[index].durationMs
        positionMs = 0
        c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    override fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    override fun next() {
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000L) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        this.positionMs = positionMs.coerceAtLeast(0L)
    }

    override fun startSleepTimer(minutes: Int) {
        sleep.start(minutes)
    }

    override fun setEqualizer(gainsMb: List<Int>?) {
        val c = controller ?: return
        val args = Bundle().apply {
            putBoolean(MusicService.ARG_RESET, gainsMb == null)
            if (gainsMb != null) putIntArray(MusicService.ARG_GAINS, gainsMb.toIntArray())
        }
        c.sendCustomCommand(SessionCommand(MusicService.ACTION_SET_EQUALIZER, Bundle.EMPTY), args)
    }

    override fun tick() {
        syncFromController()
        if (sleep.tick() && isPlaying) controller?.pause()
    }

    override fun release() {
        controller?.release()
        controller = null
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(displayTitle)
                .setArtist(displayArtist)
                .setAlbumTitle(displayAlbum)
                .apply { artUri?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.All
        Player.REPEAT_MODE_ONE -> RepeatMode.One
        else -> RepeatMode.Off
    }
}

@Composable
actual fun rememberPlayback(): Playback {
    val context = LocalContext.current
    val playback = remember(context) { AndroidPlayback(context) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    return playback
}
