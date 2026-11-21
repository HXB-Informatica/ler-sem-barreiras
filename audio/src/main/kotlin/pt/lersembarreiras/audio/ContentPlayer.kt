package pt.lersembarreiras.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import pt.lersembarreiras.core.model.PlaybackSnapshot
import pt.lersembarreiras.shared.PlaybackController

class ContentPlayer(context: Context) : PlaybackController {
    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val durationMs: Long get() = player.duration.coerceAtLeast(0)
    override val isPlaying: Boolean get() = player.isPlaying

    override fun load(path: String) {
        load(File(path))
    }

    fun load(file: File) {
        require(file.exists()) { "O ficheiro de áudio ainda não existe neste tablet." }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = false
    }

    override fun play() {
        player.playWhenReady = true
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun restart() {
        player.seekTo(0)
        play()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 1.5f))
    }

    override fun release() {
        player.release()
    }

    override fun playbackState(): Flow<PlaybackSnapshot> = callbackFlow {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(snapshot())
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                trySend(snapshot())
            }

            override fun onPlayerError(error: PlaybackException) {
                trySend(snapshot())
            }
        }
        player.addListener(listener)
        trySend(snapshot())
        while (isActive) {
            trySend(snapshot())
            delay(32)
        }
        awaitClose { player.removeListener(listener) }
    }.distinctUntilChanged()

    private fun snapshot() = PlaybackSnapshot(
        positionMs = player.currentPosition.coerceAtLeast(0),
        durationMs = if (player.duration > 0) player.duration else 0,
        playing = player.isPlaying,
        ended = player.playbackState == Player.STATE_ENDED,
    )
}
