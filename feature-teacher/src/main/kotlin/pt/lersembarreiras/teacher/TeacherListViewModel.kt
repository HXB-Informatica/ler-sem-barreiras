package pt.lersembarreiras.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.lersembarreiras.data.ContentRepository
import pt.lersembarreiras.lsb.LsbException

class TeacherListViewModel(
    private val repository: ContentRepository,
) : ViewModel() {
    val contents = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun importLsb(open: () -> InputStream?) {
        viewModelScope.launch {
            try {
                val stream = open() ?: throw LsbException("Não foi possível abrir o ficheiro .lsb.")
                val result = stream.use { repository.importLsb(it) }
                _message.value = when {
                    result.duplicate -> "Este exercício já estava neste tablet. Nada foi substituído."
                    result.idRemapped -> "Exercício importado como «${result.content.title}», com um identificador novo para não substituir outro conteúdo."
                    else -> "Exercício importado: ${result.content.title}. As ${result.content.words.size} palavras e os tempos foram preservados."
                }
            } catch (error: Exception) {
                _message.value = error.message ?: "O pacote é inválido e não foi importado."
            }
        }
    }

    companion object {
        fun factory(repository: ContentRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TeacherListViewModel(repository) as T
            }
        }
    }
}
