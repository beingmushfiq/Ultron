package com.ultron.companion.core.network

import com.ultron.companion.core.model.*
import com.ultron.companion.core.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class MobileApiClient(
    private val storage: SecureStorageManager,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Custom TrustManager for self-signed LAN certificates
        try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain == null || chain.isEmpty()) return
                    val cert = chain[0]
                    val md = MessageDigest.getInstance("SHA-256")
                    val fingerprintBytes = md.digest(cert.encoded)
                    val fingerprint = fingerprintBytes.joinToString(":") { "%02X".format(it) }

                    val pinned = storage.pinnedFingerprint
                    if (pinned != null && pinned.isNotBlank()) {
                        if (!fingerprint.equals(pinned, ignoreCase = true)) {
                            throw SSLPeerUnverifiedException(
                                "Server certificate fingerprint mismatch! Expected $pinned but received $fingerprint"
                            )
                        }
                    } else {
                        // Trust-on-first-use: record fingerprint
                        storage.pinnedFingerprint = fingerprint
                    }
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), java.security.SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            // Standard default if SSL init fails
        }

        builder.build()
    }

    val baseUrl: String
        get() {
            val scheme = if (storage.useTls) "https" else "http"
            val host = if (storage.host.isBlank()) "127.0.0.1" else storage.host
            return "$scheme://$host:${storage.port}"
        }

    val wsUrl: String
        get() {
            val scheme = if (storage.useTls) "wss" else "ws"
            val host = if (storage.host.isBlank()) "127.0.0.1" else storage.host
            val token = storage.sessionToken ?: ""
            return "$scheme://$host:${storage.port}/ws?token=$token"
        }

    private fun addAuth(builder: Request.Builder): Request.Builder {
        val token = storage.sessionToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        return builder
    }

    // ── 1. Status & Discovery ─────────────────────────────────────────────────

    suspend fun getStatus(): Result<SystemStatus> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/v1/mobile/status")
                .get()
                .build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val status = json.decodeFromString<SystemStatus>(body)
                Result.success(status)
            } else {
                Result.failure(IOException("Server returned status ${resp.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 2. Pairing & Auth ─────────────────────────────────────────────────────

    @Serializable
    data class PairResponse(
        val ok: Boolean,
        val token: String? = null,
        val device_token: String? = null,
        val server_name: String? = null,
        val hostname: String? = null,
        val cert_fingerprint: String? = null,
        val error: String? = null
    )

    suspend fun pair(pin: String): Result<PairResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = """
                {
                    "pairing_code": "${pin.trim().uppercase()}",
                    "device_id": "${storage.deviceId}",
                    "device_name": "${storage.deviceName}"
                }
            """.trimIndent()

            val req = Request.Builder()
                .url("$baseUrl/api/v1/mobile/pair")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val pairResp = json.decodeFromString<PairResponse>(body)
                if (pairResp.token != null) {
                    storage.sessionToken = pairResp.token
                }
                if (pairResp.device_token != null) {
                    storage.deviceToken = pairResp.device_token
                }
                if (pairResp.cert_fingerprint != null) {
                    storage.pinnedFingerprint = pairResp.cert_fingerprint
                }
                Result.success(pairResp)
            } else {
                Result.failure(IOException("Pairing failed (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    data class DeviceLoginResponse(
        val ok: Boolean,
        val token: String? = null,
        val device_name: String? = null,
        val error: String? = null
    )

    suspend fun deviceLogin(): Result<DeviceLoginResponse> = withContext(Dispatchers.IO) {
        try {
            val devToken = storage.deviceToken
            if (devToken.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("No device token available for reconnect."))
            }

            val payload = """
                {
                    "device_token": "$devToken",
                    "device_id": "${storage.deviceId}"
                }
            """.trimIndent()

            val req = Request.Builder()
                .url("$baseUrl/api/v1/mobile/device-login")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val loginResp = json.decodeFromString<DeviceLoginResponse>(body)
                if (loginResp.token != null) {
                    storage.sessionToken = loginResp.token
                }
                Result.success(loginResp)
            } else {
                Result.failure(IOException("Reconnect failed (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/disconnect")
                    .post("{}".toRequestBody(jsonMediaType))
            ).build()

            okHttpClient.newCall(req).execute()
            storage.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            storage.clearSession()
            Result.success(Unit)
        }
    }

    // ── 3. Chat Interaction ───────────────────────────────────────────────────

    @Serializable
    data class ChatResponse(
        val ok: Boolean,
        val reply: String = "",
        val timestamp: String = "",
        val source: String = "desktop"
    )

    suspend fun sendChat(prompt: String): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
            val payload = """{"prompt": "$cleanPrompt"}"""

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/chat")
                    .post(payload.toRequestBody(jsonMediaType))
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val chatResp = json.decodeFromString<ChatResponse>(body)
                Result.success(chatResp)
            } else {
                Result.failure(IOException("Chat error (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 4. Actions & Approvals ────────────────────────────────────────────────

    @Serializable
    private data class ActionCatalogueResponse(val ok: Boolean, val actions: List<UltronAction> = emptyList())

    suspend fun getActions(): Result<List<UltronAction>> = withContext(Dispatchers.IO) {
        try {
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/actions")
                    .get()
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val data = json.decodeFromString<ActionCatalogueResponse>(body)
                Result.success(data.actions)
            } else {
                Result.failure(IOException("Failed to fetch actions (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    data class ExecuteActionResponse(
        val ok: Boolean,
        val status: String, // "succeeded" | "pending_approval"
        val approval_id: String? = null,
        val result: String? = null,
        val message: String? = null
    )

    suspend fun executeAction(actionId: String, params: Map<String, String> = emptyMap()): Result<ExecuteActionResponse> = withContext(Dispatchers.IO) {
        try {
            val paramsJson = params.entries.joinToString(",") { "\"${it.key}\": \"${it.value}\"" }
            val payload = """
                {
                    "action_id": "$actionId",
                    "params": {$paramsJson},
                    "idempotency_key": "${java.util.UUID.randomUUID()}"
                }
            """.trimIndent()

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/actions/execute")
                    .post(payload.toRequestBody(jsonMediaType))
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val data = json.decodeFromString<ExecuteActionResponse>(body)
                Result.success(data)
            } else {
                Result.failure(IOException("Execution error (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class ApprovalsResponse(val ok: Boolean, val approvals: List<PendingApproval> = emptyList())

    suspend fun getPendingApprovals(): Result<List<PendingApproval>> = withContext(Dispatchers.IO) {
        try {
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/actions/approvals")
                    .get()
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val data = json.decodeFromString<ApprovalsResponse>(body)
                Result.success(data.approvals)
            } else {
                Result.failure(IOException("Approvals error (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveApproval(approvalId: String, approved: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = """
                {
                    "approval_id": "$approvalId",
                    "approved": $approved
                }
            """.trimIndent()

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/actions/approve")
                    .post(payload.toRequestBody(jsonMediaType))
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                Result.success(body)
            } else {
                Result.failure(IOException("Approval resolution failed (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 5. Presets ────────────────────────────────────────────────────────────

    @Serializable
    private data class PresetsResponse(val ok: Boolean, val presets: List<WorkspacePreset> = emptyList())

    suspend fun getPresets(): Result<List<WorkspacePreset>> = withContext(Dispatchers.IO) {
        try {
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/presets")
                    .get()
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val data = json.decodeFromString<PresetsResponse>(body)
                Result.success(data.presets)
            } else {
                Result.failure(IOException("Presets error (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyPreset(name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = """{"preset_name": "$name"}"""
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/presets/apply")
                    .post(payload.toRequestBody(jsonMediaType))
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                Result.success("Preset $name applied.")
            } else {
                Result.failure(IOException("Apply failed (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 6. Memory Management ──────────────────────────────────────────────────

    @Serializable
    private data class MemoryResponse(val ok: Boolean, val entries: List<MemoryItem> = emptyList())

    suspend fun getMemories(search: String? = null): Result<List<MemoryItem>> = withContext(Dispatchers.IO) {
        try {
            val q = if (!search.isNullOrBlank()) "?search=${search.trim()}" else ""
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/memory$q")
                    .get()
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val data = json.decodeFromString<MemoryResponse>(body)
                Result.success(data.entries)
            } else {
                Result.failure(IOException("Memory query error (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun remember(key: String, value: String, category: String = "notes"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = """
                {
                    "key": "$key",
                    "value": "$value",
                    "category": "$category"
                }
            """.trimIndent()

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/memory")
                    .post(payload.toRequestBody(jsonMediaType))
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                Result.success("Memory remembered.")
            } else {
                Result.failure(IOException("Remember error (${resp.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forget(key: String, category: String = "notes"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/v1/mobile/memory?key=$key&category=$category")
                    .delete()
            ).build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                Result.success("Memory forgotten.")
            } else {
                Result.failure(IOException("Forget error (${resp.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 7. TTS & Audio ────────────────────────────────────────────────────────

    fun getTtsStreamUrl(text: String): String {
        val encoded = java.net.URLEncoder.encode(text, "UTF-8")
        val token = storage.sessionToken ?: ""
        return "$baseUrl/api/tts?text=$encoded&token=$token"
    }

    // ── 8. WebSocket ──────────────────────────────────────────────────────────

    fun openWebSocket(listener: WebSocketListener): WebSocket {
        val req = Request.Builder()
            .url(wsUrl)
            .build()
        return okHttpClient.newWebSocket(req, listener)
    }
}
