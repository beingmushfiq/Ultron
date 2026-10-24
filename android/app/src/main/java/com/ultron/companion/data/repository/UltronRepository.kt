package com.ultron.companion.data.repository

import com.ultron.companion.core.model.*
import com.ultron.companion.core.network.MobileApiClient
import com.ultron.companion.core.security.SecureStorageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class UltronRepository(
    val storage: SecureStorageManager,
    val api: MobileApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _systemStatus = MutableStateFlow<SystemStatus?>(null)
    val systemStatus: StateFlow<SystemStatus?> = _systemStatus.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<PendingApproval>>(emptyList())
    val pendingApprovals: StateFlow<List<PendingApproval>> = _pendingApprovals.asStateFlow()

    private val _workspacePresets = MutableStateFlow<List<WorkspacePreset>>(emptyList())
    val workspacePresets: StateFlow<List<WorkspacePreset>> = _workspacePresets.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var syncJob: Job? = null

    init {
        initSession()
    }

    fun initSession() {
        if (!storage.isConfigured) {
            _connectionState.value = ConnectionState.NotConfigured
            return
        }
        scope.launch {
            reconnect()
        }
    }

    suspend fun reconnect() {
        _connectionState.value = ConnectionState.Connecting

        // 1. Check server status
        val statusRes = api.getStatus()
        if (statusRes.isFailure) {
            _connectionState.value = ConnectionState.Disconnected(
                reason = "Desktop server unreachable at ${storage.host}:${storage.port}"
            )
            return
        }

        val status = statusRes.getOrNull()
        _systemStatus.value = status

        // 2. Validate/Refresh session
        val loginRes = api.deviceLogin()
        if (loginRes.isFailure && storage.sessionToken.isNullOrBlank()) {
            _connectionState.value = ConnectionState.AuthExpired
            return
        }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _connectionState.value = ConnectionState.Connected(
            host = storage.host,
            port = storage.port,
            serverName = status?.hostname ?: "Ultron Host",
            fingerprint = status?.cert_fingerprint ?: "",
            lastSyncTime = timeStr
        )

        // 3. Connect real-time WebSocket
        connectWebSocket()

        // 4. Load initial data
        refreshAll()
    }

    suspend fun pairWithPin(host: String, port: Int, useTls: Boolean, pin: String): Result<String> {
        storage.host = host
        storage.port = port
        storage.useTls = useTls
        _connectionState.value = ConnectionState.Pairing

        val res = api.pair(pin)
        return if (res.isSuccess) {
            val data = res.getOrThrow()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _connectionState.value = ConnectionState.Connected(
                host = storage.host,
                port = storage.port,
                serverName = data.server_name ?: "Ultron Desktop",
                fingerprint = data.cert_fingerprint ?: "",
                lastSyncTime = timeStr
            )
            connectWebSocket()
            refreshAll()
            Result.success("Paired successfully with ${data.server_name}")
        } else {
            _connectionState.value = ConnectionState.Disconnected(
                reason = res.exceptionOrNull()?.message ?: "Pairing failed"
            )
            Result.failure(res.exceptionOrNull() ?: Exception("Pairing failed"))
        }
    }

    suspend fun disconnect() {
        api.disconnect()
        activeWebSocket?.close(1000, "User disconnected")
        activeWebSocket = null
        storage.clearSession()
        _connectionState.value = ConnectionState.NotConfigured
        _chatMessages.value = emptyList()
        _pendingApprovals.value = emptyList()
    }

    private fun connectWebSocket() {
        activeWebSocket?.cancel()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // If closed unexpectedly, trigger reconnect
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Will retry on next heartbeat/user action
            }
        }
        activeWebSocket = api.openWebSocket(listener)
    }

    private fun handleWebSocketMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            when (type) {
                "chat_message" -> {
                    val senderStr = json.optString("sender", "ultron")
                    val sender = if (senderStr == "user") MessageSender.USER else MessageSender.ASSISTANT
                    val text = json.optString("text", "")
                    val time = json.optString("time", timeStr)
                    addMessage(ChatMessage(UUID.randomUUID().toString(), sender, text, time))
                }
                "approval_required" -> {
                    val approval = PendingApproval(
                        approval_id = json.optString("approval_id"),
                        action_id = json.optString("action_id"),
                        title = json.optString("title"),
                        detail = json.optString("detail"),
                        target = "desktop",
                        risk_level = "high_impact",
                        time_remaining_sec = json.optInt("expires_in", 90)
                    )
                    _pendingApprovals.value = _pendingApprovals.value.filter { it.approval_id != approval.approval_id } + approval
                }
                "approval_resolved" -> {
                    val id = json.optString("approval_id")
                    _pendingApprovals.value = _pendingApprovals.value.filter { it.approval_id != id }
                }
                "sys" -> {
                    val text = json.optString("text")
                    if (text.isNotBlank()) {
                        addMessage(ChatMessage(UUID.randomUUID().toString(), MessageSender.SYSTEM, text, timeStr))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore malformed payloads
        }
    }

    fun addMessage(msg: ChatMessage) {
        _chatMessages.value = _chatMessages.value + msg
    }

    suspend fun sendChatMessage(text: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val userMsg = ChatMessage(UUID.randomUUID().toString(), MessageSender.USER, text, timeStr)
        addMessage(userMsg)

        val res = api.sendChat(text)
        if (res.isSuccess) {
            val data = res.getOrThrow()
            val replyMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                text = data.reply,
                timestamp = data.timestamp.ifBlank { timeStr },
                source = MessageSource.DESKTOP
            )
            addMessage(replyMsg)
        } else {
            val err = res.exceptionOrNull()?.message ?: "Command failed"
            val errMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.SYSTEM,
                text = "Desktop communication error: $err",
                timestamp = timeStr,
                isError = true
            )
            addMessage(errMsg)
        }
    }

    suspend fun refreshAll() {
        scope.launch {
            val pRes = api.getPresets()
            if (pRes.isSuccess) _workspacePresets.value = pRes.getOrNull() ?: emptyList()

            val aRes = api.getPendingApprovals()
            if (aRes.isSuccess) _pendingApprovals.value = aRes.getOrNull() ?: emptyList()

            val mRes = api.getMemories()
            if (mRes.isSuccess) _memories.value = mRes.getOrNull() ?: emptyList()

            val sRes = api.getStatus()
            if (sRes.isSuccess) _systemStatus.value = sRes.getOrNull()
        }
    }

    suspend fun executeAction(actionId: String, params: Map<String, String> = emptyMap()): Result<MobileApiClient.ExecuteActionResponse> {
        val res = api.executeAction(actionId, params)
        if (res.isSuccess) {
            val data = res.getOrThrow()
            if (data.status == "pending_approval") {
                refreshAll()
            }
        }
        return res
    }

    suspend fun resolveApproval(approvalId: String, approved: Boolean): Result<String> {
        val res = api.resolveApproval(approvalId, approved)
        _pendingApprovals.value = _pendingApprovals.value.filter { it.approval_id != approvalId }
        return res
    }

    suspend fun applyPreset(presetName: String): Result<String> {
        return api.applyPreset(presetName)
    }

    suspend fun rememberMemory(key: String, value: String, category: String = "notes"): Result<String> {
        val res = api.remember(key, value, category)
        if (res.isSuccess) {
            val mRes = api.getMemories()
            if (mRes.isSuccess) _memories.value = mRes.getOrNull() ?: emptyList()
        }
        return res
    }

    suspend fun forgetMemory(key: String, category: String = "notes"): Result<String> {
        val res = api.forget(key, category)
        if (res.isSuccess) {
            _memories.value = _memories.value.filterNot { it.key == key && it.category == category }
        }
        return res
    }
}
