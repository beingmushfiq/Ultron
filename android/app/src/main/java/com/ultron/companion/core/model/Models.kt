package com.ultron.companion.core.model

import kotlinx.serialization.Serializable

/**
 * Connection states for UI indicators and diagnostic panels.
 */
sealed class ConnectionState {
    object NotConfigured : ConnectionState()
    object Pairing : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val host: String,
        val port: Int,
        val serverName: String,
        val fingerprint: String,
        val lastSyncTime: String = ""
    ) : ConnectionState()
    data class Disconnected(val reason: String, val canRetry: Boolean = true) : ConnectionState()
    object AuthExpired : ConnectionState()
}

/**
 * Chat conversation entry with source provenance and streaming status.
 */
data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestamp: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val source: MessageSource = MessageSource.DESKTOP
)

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageSource {
    DESKTOP,
    LOCAL_FALLBACK
}

/**
 * Remote action definition from the backend catalogue.
 */
@Serializable
data class UltronAction(
    val id: String,
    val name: String,
    val category: String,
    val risk_level: String, // "read_only" | "reversible" | "high_impact"
    val description: String,
    val requires_approval: Boolean = false,
    val parameters: List<ActionParameter> = emptyList()
) {
    val isHighImpact: Boolean get() = risk_level == "high_impact" || requires_approval
    val isReversible: Boolean get() = risk_level == "reversible"
    val isReadOnly: Boolean get() = risk_level == "read_only"
}

@Serializable
data class ActionParameter(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val description: String = ""
)

/**
 * High-impact action awaiting explicit confirmation.
 */
@Serializable
data class PendingApproval(
    val approval_id: String,
    val action_id: String,
    val title: String,
    val detail: String,
    val target: String,
    val risk_level: String,
    val time_remaining_sec: Int = 90
)

/**
 * Workstation environment preset.
 */
@Serializable
data class WorkspacePreset(
    val name: String,
    val title: String,
    val description: String = "",
    val apps: List<String> = emptyList(),
    val volume: Int? = null,
    val brightness: Int? = null,
    val dnd: Boolean = false
)

/**
 * Long-term memory entry.
 */
@Serializable
data class MemoryItem(
    val category: String,
    val key: String,
    val value: String,
    val updated: String = ""
)

/**
 * Desktop host system telemetry.
 */
@Serializable
data class SystemTelemetry(
    val cpu_percent: Float? = null,
    val memory_percent: Float? = null
)

@Serializable
data class SystemStatus(
    val ok: Boolean = true,
    val server_version: String = "1.0.0",
    val api_version: String = "v1",
    val hostname: String = "Ultron Host",
    val os: String = "Windows",
    val os_release: String = "",
    val desktop_available: Boolean = true,
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val tls_enabled: Boolean = false,
    val cert_fingerprint: String = "",
    val capabilities: List<String> = emptyList()
)
