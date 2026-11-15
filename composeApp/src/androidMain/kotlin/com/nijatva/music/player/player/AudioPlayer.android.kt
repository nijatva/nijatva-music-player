package com.nijatva.music.player.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

private class AndroidAudioPlayer(context: Context) : AudioPlayer {

    private val exo: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        setHandleAudioBecomingNoisy(true)
    }

    private var equalizer: Equalizer? = null

    override fun open(uri: String) {
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
    }

    override fun play() = exo.play()
    override fun pause() = exo.pause()
    override fun seekTo(positionMs: Long) = exo.seekTo(positionMs.coerceAtLeast(0L))
    override fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        exo.release()
    }

    override fun positionMs(): Long = exo.currentPosition
    override fun durationMs(): Long = exo.duration.let { if (it > 0) it else 0L }
    override fun isPlaying(): Boolean = exo.isPlaying

    override fun setEqualizer(gainsMb: List<Int>?) {
        val eq = equalizer ?: runCatching { Equalizer(0, exo.audioSessionId) }.getOrNull()?.also { equalizer = it }
            ?: return
        runCatching {
            if (gainsMb == null) {
                eq.enabled = false
                return
            }
            val range = eq.bandLevelRange
            val bands = eq.numberOfBands.toInt()
            for (band in 0 until bands) {
                val wanted = gainsMb.getOrElse(band) { 0 }
                eq.setBandLevel(band.toShort(), wanted.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = true
        }
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current
    val player = remember { AndroidAudioPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
