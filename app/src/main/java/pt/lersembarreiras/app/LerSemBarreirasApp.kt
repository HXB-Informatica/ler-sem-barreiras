package pt.lersembarreiras.app

import android.app.Application
import pt.lersembarreiras.alignment.WordAligner
import pt.lersembarreiras.audio.AudioImporter
import pt.lersembarreiras.audio.AudioRecorder
import pt.lersembarreiras.audio.ContentPlayer
import pt.lersembarreiras.data.ContentRepository
import pt.lersembarreiras.data.ContentRepositoryFactory
import pt.lersembarreiras.speech.NativeWhisperEngine
import pt.lersembarreiras.speech.SpeechRecognitionEngine

class LerSemBarreirasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val app: Application) {
    val repository: ContentRepository = ContentRepositoryFactory.create(app)
    val aligner: WordAligner = WordAligner()
    val speechEngine: SpeechRecognitionEngine = NativeWhisperEngine(app)
    fun newRecorder() = AudioRecorder()
    fun newImporter() = AudioImporter(app)
    fun newPlayer() = ContentPlayer(app)
}
