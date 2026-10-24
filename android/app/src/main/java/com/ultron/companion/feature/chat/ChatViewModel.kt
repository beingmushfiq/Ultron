package com.ultron.companion.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.companion.core.model.ChatMessage
import com.ultron.companion.data.repository.UltronRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: UltronRepository
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = repository.chatMessages
    val inputPrompt = MutableStateFlow("")

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun sendMessage() {
        val text = inputPrompt.value.trim()
        if (text.isBlank() || _isSending.value) return

        inputPrompt.value = ""
        _isSending.value = true

        viewModelScope.launch {
            repository.sendChatMessage(text)
            _isSending.value = false
        }
    }

    fun retryMessage(failedMessage: ChatMessage) {
        viewModelScope.launch {
            repository.sendChatMessage(failedMessage.text)
        }
    }
}
