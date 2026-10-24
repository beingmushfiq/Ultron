package com.ultron.companion.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.companion.core.model.ConnectionState
import com.ultron.companion.data.repository.UltronRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PairingViewModel(
    private val repository: UltronRepository
) : ViewModel() {

    val connectionState = repository.connectionState

    var hostInput = MutableStateFlow(repository.storage.host.ifBlank { "192.168.1." })
    var portInput = MutableStateFlow(repository.storage.port.toString())
    var useTls = MutableStateFlow(repository.storage.useTls)
    var pinInput = MutableStateFlow("")

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    fun startPairing(onSuccess: () -> Unit) {
        val host = hostInput.value.trim()
        val port = portInput.value.trim().toIntOrNull() ?: 8000
        val pin = pinInput.value.trim().uppercase()

        if (host.isBlank()) {
            _uiError.value = "Desktop IP address cannot be empty."
            return
        }
        if (pin.length < 4) {
            _uiError.value = "Enter the 6-character PIN shown on the Ultron desktop HUD."
            return
        }

        _uiError.value = null
        _isPairing.value = true

        viewModelScope.launch {
            val res = repository.pairWithPin(host, port, useTls.value, pin)
            _isPairing.value = false
            if (res.isSuccess) {
                onSuccess()
            } else {
                _uiError.value = res.exceptionOrNull()?.message ?: "Pairing failed. Check IP and PIN."
            }
        }
    }
}
