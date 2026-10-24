package com.ultron.companion.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.companion.core.model.ConnectionState
import com.ultron.companion.core.model.SystemStatus
import com.ultron.companion.core.model.WorkspacePreset
import com.ultron.companion.data.repository.UltronRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    val repository: UltronRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val systemStatus: StateFlow<SystemStatus?> = repository.systemStatus
    val presets: StateFlow<List<WorkspacePreset>> = repository.workspacePresets
    val pendingApprovalsCount = repository.pendingApprovals

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun applyPreset(preset: WorkspacePreset) {
        viewModelScope.launch {
            val res = repository.applyPreset(preset.name)
            if (res.isSuccess) {
                _uiMessage.value = "Engaged ${preset.title} on desktop."
            } else {
                _uiMessage.value = "Failed to engage preset: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshAll()
        }
    }
}
