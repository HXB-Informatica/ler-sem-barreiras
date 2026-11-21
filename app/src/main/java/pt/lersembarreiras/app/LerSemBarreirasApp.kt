package pt.lersembarreiras.app

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import pt.lersembarreiras.alignment.DefaultAlignmentEngine
import pt.lersembarreiras.audio.AudioImporter
import pt.lersembarreiras.audio.AudioRecorder
import pt.lersembarreiras.audio.ContentPlayer
import pt.lersembarreiras.data.ContentRepository
import pt.lersembarreiras.data.ContentRepositoryFactory
import pt.lersembarreiras.shared.AlignmentEngine
import pt.lersembarreiras.speech.SpeechRecognitionEngine
import pt.lersembarreiras.speech.UnavailableSpeechEngine

class LerSemBarreirasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val app: Application) {
    val pendingLsbUri = MutableStateFlow<Uri?>(null)
    val repository: ContentRepository = ContentRepositoryFactory.create(app)
    val aligner: AlignmentEngine = DefaultAlignmentEngine()
    val speechEngine: SpeechRecognitionEngine = UnavailableSpeechEngine()
    fun newRecorder() = AudioRecorder()
    fun newImporter() = AudioImporter(app)
    fun newPlayer() = ContentPlayer(app)
}
