package com.ultron.companion.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.companion.core.model.MemoryItem
import com.ultron.companion.data.repository.UltronRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MemoryViewModel(
    private val repository: UltronRepository
) : ViewModel() {

    val memories: StateFlow<List<MemoryItem>> = repository.memories
    val searchInput = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)

    private val _uiFeedback = MutableStateFlow<String?>(null)
    val uiFeedback: StateFlow<String?> = _uiFeedback.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshAll()
        }
    }

    fun addMemory(key: String, value: String, category: String) {
        viewModelScope.launch {
            val res = repository.rememberMemory(key, value, category)
            if (res.isSuccess) {
                _uiFeedback.value = "Stored in desktop memory: $key"
            } else {
                _uiFeedback.value = "Failed to remember: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun forgetMemory(item: MemoryItem) {
        viewModelScope.launch {
            val res = repository.forgetMemory(item.key, item.category)
            if (res.isSuccess) {
                _uiFeedback.value = "Forgotten from desktop: ${item.key}"
            } else {
                _uiFeedback.value = "Failed to delete: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearFeedback() {
        _uiFeedback.value = null
    }
}
