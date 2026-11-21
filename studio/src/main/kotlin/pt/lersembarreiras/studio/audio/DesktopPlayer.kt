package pt.lersembarreiras.studio.audio

import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl

class DesktopPlayer {
    private var clip: Clip? = null
    private var loaded: File? = null
    private var speed: Float = 1f
    private var nativeRate: Float = 48_000f

    val isPlaying: Boolean get() = clip?.isRunning == true
    val positionMs: Long
        get() {
            val current = clip ?: return 0
            return (current.microsecondPosition / 1000L).coerceAtLeast(0)
        }
    val durationMs: Long
        get() {
            val current = clip ?: return 0
            return (current.microsecondLength / 1000L).coerceAtLeast(0)
        }

    fun load(file: File) {
        val position = positionMs
        stop()
        require(file.exists()) { "Ainda não existe uma gravação neste projecto." }
        loaded = file
        val stream = AudioSystem.getAudioInputStream(file)
        nativeRate = stream.format.sampleRate
        val newClip = AudioSystem.getClip()
        newClip.open(stream)
        clip = newClip
        applySpeed()
        if (position > 0) seekTo(position)
    }

    fun play() {
        val current = clip ?: return
        if (current.microsecondPosition >= current.microsecondLength) {
            current.microsecondPosition = 0
        }
        current.start()
    }

    fun pause() {
        clip?.stop()
    }

    fun stop() {
        clip?.stop()
        clip?.close()
        clip = null
    }

    fun seekTo(positionMs: Long) {
        val current = clip ?: return
        current.microsecondPosition = (positionMs.coerceAtLeast(0) * 1000L)
            .coerceAtMost(current.microsecondLength)
    }

    fun setSpeed(value: Float) {
        speed = when (value) {
            0.5f, 0.75f, 1f -> value
            else -> 1f
        }
        applySpeed()
    }

    private fun applySpeed() {
        val current = clip ?: return
        val control = runCatching { current.getControl(FloatControl.Type.SAMPLE_RATE) as FloatControl }.getOrNull()
        if (control != null) {
            val target = (nativeRate * speed).coerceIn(control.minimum, control.maximum)
            control.value = target
        }
    }

    fun setGainDb(db: Float) {
        val control = clip?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
        control.value = db.coerceIn(control.minimum, control.maximum)
    }
}
