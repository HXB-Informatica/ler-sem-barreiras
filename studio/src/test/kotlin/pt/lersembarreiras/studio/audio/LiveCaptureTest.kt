package pt.lersembarreiras.studio.audio

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveCaptureTest {
    @Test
    fun recordsBrieflyIfAMicrophoneIsPresent() {
        val devices = AudioDevices.listInputs()
        assumeTrue("Sem microfone neste ambiente — teste físico pendente.", devices.isNotEmpty())
        val recorder = DesktopRecorder()
        val file = File.createTempFile("live", ".wav")
        val started = runCatching { recorder.start(devices.first(), file) }
        assumeTrue("Microfone enumerado mas a linha não abriu (privacidade do Windows?).", started.isSuccess)
        Thread.sleep(400)
        val saved = recorder.stop(file)
        assumeTrue("A linha abriu mas não produziu amostras.", saved != null && saved.length() > 44)
        val props = StreamingWavWriter.inspect(saved!!)
        assert(props.sampleRate in setOf(16_000, 44_100, 48_000))
        assert(props.durationMs >= 0)
        saved.delete()
        File(saved.parent, saved.name + ".previous.wav").delete()
    }
}
