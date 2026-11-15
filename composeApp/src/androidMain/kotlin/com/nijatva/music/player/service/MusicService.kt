package com.nijatva.music.player.service

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nijatva.music.player.MainActivity

/**
 * Holds the one ExoPlayer and the MediaSession for the whole app.
 *
 * Living in a foreground service is what keeps music playing when the app is swiped
 * away, and it is what puts the track on the lock screen, in Bluetooth controls and in
 * Assistant. The UI never touches this player directly: it drives a [MediaController]
 * and reads its state back.
 */
class MusicService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    override fun onCreate() {
        super.onCreate()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val exo = player
        if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        releaseEffects()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_SET_EQUALIZER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SET_EQUALIZER) {
                val gains = args.getIntArray(ARG_GAINS)?.toList()
                val reset = args.getBoolean(ARG_RESET, gains == null)
                applyEqualizer(if (reset) null else gains)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    /**
     * Effects are attached to the ExoPlayer audio session, not to a player instance, so
     * they survive track changes and engine swaps. Same approach as PixelPlayer's
     * EqualizerManager, plus bass boost and virtualiser.
     */
    private fun applyEqualizer(gainsMb: List<Int>?) {
        val exo = player ?: return
        if (gainsMb == null) {
            releaseEffects()
            return
        }
        runCatching {
            val eq = equalizer ?: Equalizer(0, exo.audioSessionId).also { equalizer = it }
            val range = eq.bandLevelRange
            val bands = eq.numberOfBands.toInt()
            for (band in 0 until bands) {
                val wanted = gainsMb.getOrElse(band) { 0 }
                eq.setBandLevel(
                    band.toShort(),
                    wanted.coerceIn(range[0].toInt(), range[1].toInt()).toShort(),
                )
            }
            eq.enabled = true

            val bass = bassBoost ?: BassBoost(0, exo.audioSessionId).also { bassBoost = it }
            bass.setStrength((gainsMb.firstOrNull() ?: 0).coerceIn(0, 1000).toShort())
            bass.enabled = (gainsMb.firstOrNull() ?: 0) > 0

            val virt = virtualizer ?: Virtualizer(0, exo.audioSessionId).also { virtualizer = it }
            virt.setStrength(400)
            virt.enabled = true
        }
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    companion object {
        const val ACTION_SET_EQUALIZER = "com.nijatva.music.player.SET_EQUALIZER"
        const val ARG_GAINS = "gains"
        const val ARG_RESET = "reset"
    }
}
