package com.ultron.companion.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.companion.data.repository.UltronRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    val repository: UltronRepository
) : ViewModel() {

    val storage = repository.storage
    val isTtsEnabled = MutableStateFlow(storage.isTtsEnabled)
    val deviceName = MutableStateFlow(storage.deviceName)

    private val _uiFeedback = MutableStateFlow<String?>(null)
    val uiFeedback: StateFlow<String?> = _uiFeedback.asStateFlow()

    fun updateDeviceName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            storage.deviceName = trimmed
            deviceName.value = trimmed
            _uiFeedback.value = "Device name updated."
        }
    }

    fun toggleTts(enabled: Boolean) {
        storage.isTtsEnabled = enabled
        isTtsEnabled.value = enabled
    }

    fun disconnectSession(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.disconnect()
            _uiFeedback.value = "Session revoked and disconnected."
            onComplete()
        }
    }

    fun clearLocalCache() {
        storage.clearSession()
        _uiFeedback.value = "Local connection cache cleared."
    }

    fun getRedactedDiagnostics(): String {
        return """
            === ULTRON COMPANION DIAGNOSTIC REPORT ===
            App Version: 1.0.0
            API Target: v1
            Host Address: ${storage.host}:${storage.port}
            Protocol: ${if (storage.useTls) "TLS/HTTPS" else "Plain HTTP (LAN)"}
            Certificate SHA-256: ${storage.pinnedFingerprint ?: "None"}
            Device ID: ${storage.deviceId}
            Device Name: ${storage.deviceName}
            Session Active: ${!storage.sessionToken.isNullOrBlank()}
            Tokens Redacted: [REDACTED_SECURE_TOKEN]
            Microphone Permission: Push-to-Talk (Runtime Only)
            Local Storage: Keystore EncryptedSharedPreferences
        """.trimIndent()
    }

    fun clearFeedback() {
        _uiFeedback.value = null
    }
}
