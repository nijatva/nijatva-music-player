package com.nijatva.music.player.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.io.File

/**
 * Desktop playback through libVLC (vlcj).
 *
 * VLC must be installed on the machine — the app still opens without it, but
 * playback is a no-op rather than a crash.
 */
private class DesktopAudioPlayer : AudioPlayer {

    private val factory: MediaPlayerFactory? = runCatching { MediaPlayerFactory() }.getOrNull()

    private val player: MediaPlayer? = factory?.let {
        runCatching { it.mediaPlayers().newEmbeddedMediaPlayer() }.getOrNull()
    }

    override fun open(uri: String) {
        val mrl = toMrl(uri)
        player?.media()?.prepare(mrl)
    }

    override fun play() {
        player?.controls()?.play()
    }

    override fun pause() {
        player?.controls()?.pause()
    }

    override fun seekTo(positionMs: Long) {
        player?.controls()?.setTime(positionMs.coerceAtLeast(0L))
    }

    override fun release() {
        runCatching { player?.release() }
        runCatching { factory?.release() }
    }

    override fun positionMs(): Long = player?.status()?.time() ?: 0L

    override fun durationMs(): Long = player?.status()?.length() ?: 0L

    override fun isPlaying(): Boolean = player?.status()?.isPlaying == true

    private fun toMrl(uri: String): String = when {
        uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("file:") -> uri
        else -> File(uri).toURI().toString()
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
