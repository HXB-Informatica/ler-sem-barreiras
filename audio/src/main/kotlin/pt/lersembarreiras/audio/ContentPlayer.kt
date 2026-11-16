package pt.lersembarreiras.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

class ContentPlayer(context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    val durationMs: Long get() = player.duration.coerceAtLeast(0)
    val isPlaying: Boolean get() = player.isPlaying

    fun load(file: File) {
        require(file.exists()) { "O ficheiro de áudio ainda não existe neste tablet." }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = false
    }

    fun play() {
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun restart() {
        player.seekTo(0)
        play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    fun release() {
        player.release()
    }

    fun playbackState(): Flow<PlaybackSnapshot> = callbackFlow {
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

data class PlaybackSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val ended: Boolean,
)
