package com.ultron.companion.feature.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.companion.core.model.PendingApproval
import com.ultron.companion.core.model.UltronAction
import com.ultron.companion.data.repository.UltronRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActionsViewModel(
    private val repository: UltronRepository
) : ViewModel() {

    val pendingApprovals: StateFlow<List<PendingApproval>> = repository.pendingApprovals

    private val _actions = MutableStateFlow<List<UltronAction>>(emptyList())
    val actions: StateFlow<List<UltronAction>> = _actions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiFeedback = MutableStateFlow<String?>(null)
    val uiFeedback: StateFlow<String?> = _uiFeedback.asStateFlow()

    init {
        loadActions()
    }

    fun loadActions() {
        _isLoading.value = true
        viewModelScope.launch {
            val res = repository.api.getActions()
            _isLoading.value = false
            if (res.isSuccess) {
                _actions.value = res.getOrNull() ?: emptyList()
            } else {
                _uiFeedback.value = "Failed to load catalogue: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun executeAction(action: UltronAction) {
        viewModelScope.launch {
            val res = repository.executeAction(action.id)
            if (res.isSuccess) {
                val data = res.getOrThrow()
                if (data.status == "pending_approval") {
                    _uiFeedback.value = "Action queued for confirmation: ${action.name}"
                } else {
                    _uiFeedback.value = data.result ?: "Executed: ${action.name}"
                }
            } else {
                _uiFeedback.value = "Execution failed: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun resolveApproval(approval: PendingApproval, approved: Boolean) {
        viewModelScope.launch {
            val res = repository.resolveApproval(approval.approval_id, approved)
            if (res.isSuccess) {
                _uiFeedback.value = if (approved) "Approved: ${approval.title}" else "Rejected: ${approval.title}"
            } else {
                _uiFeedback.value = "Approval failed: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearFeedback() {
        _uiFeedback.value = null
    }
}
