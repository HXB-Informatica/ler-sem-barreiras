package pt.lersembarreiras.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.lersembarreiras.data.ContentRepository

class TeacherListViewModel(
    private val repository: ContentRepository,
) : ViewModel() {
    val contents = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
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
