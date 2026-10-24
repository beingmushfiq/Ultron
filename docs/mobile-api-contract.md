# Ultron Mobile Companion — API Contract (v1)

This document specifies the exact wire contracts, schemas, authorization models, and event protocols implemented in `dashboard/mobile_api.py` and supported by the Ultron Desktop service.

---

## 1. Authentication & Security Primitives

- **Base URL**: `http://<desktop-ip>:8000/api/v1/mobile` (or `https://<desktop-ip>:8000/api/v1/mobile` when TLS certificates are generated in `config/certs/`).
- **Authorization Header**: Requests to protected endpoints must supply:
  ```http
  Authorization: Bearer <session_token>
  ```
- **Error Format**: All rejected requests return standard JSON:
  ```json
  {
    "detail": "Error message describing the authorization, rate-limit, or validation failure"
  }
  ```

---

## 2. API Endpoints

### 2.1 Discovery & Health

#### `GET /api/v1/mobile/status`
Returns confirmed server availability, host telemetry, and active capabilities.
- **Auth**: None required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "server_version": "1.0.0",
  "api_version": "v1",
  "hostname": "ULTRON-DESKTOP",
  "os": "Windows",
  "os_release": "11",
  "desktop_available": true,
  "telemetry": {
    "cpu_percent": 14.2,
    "memory_percent": 68.5
  },
  "tls_enabled": false,
  "cert_fingerprint": "B4:6A:19:... (or NO-TLS-UNENCRYPTED-HTTP)",
  "capabilities": [
    "chat",
    "voice_push_to_talk",
    "voice_tts",
    "action_center",
    "action_approval_queue",
    "workspace_presets",
    "memory_management",
    "clipboard_sync",
    "system_telemetry"
  ]
}
```

#### `GET /api/v1/mobile/capabilities`
Returns the granular capability matrix and requirements.
- **Auth**: None required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "api_version": "v1",
  "capabilities": {
    "chat": { "supported": true, "streaming": true, "auth_required": true },
    "voice_input": { "supported": true, "type": "push_to_talk", "permission": "RECORD_AUDIO" },
    "voice_output": { "supported": true, "endpoint": "/api/tts", "format": "audio/mpeg" },
    "actions": { "supported": true, "approval_flow": true, "total_actions": 12 },
    "workspace_presets": { "supported": true, "endpoint": "/api/v1/mobile/presets" },
    "memory": { "supported": true, "endpoint": "/api/v1/mobile/memory" },
    "clipboard": { "supported": true, "endpoint": "/api/clipboard" },
    "file_upload": { "supported": true, "endpoint": "/api/upload" }
  }
}
```

---

### 2.2 Pairing & Session Management

#### `POST /api/v1/mobile/pair`
Consumes a short-lived 6-character PIN displayed on the desktop HUD. Rate-limited to 5 failed attempts per 5 minutes per IP.
- **Request Body**:
```json
{
  "pairing_code": "7K2M9P",
  "device_id": "c1f7b098-4b7f-44de-87d4-8d96b9f2910d",
  "device_name": "Pixel 8 Pro"
}
```
- **Response `200 OK`**:
```json
{
  "ok": true,
  "token": "4_B8W-0E4n4X-zY2... (32-byte URL-safe session token)",
  "device_token": "j9L_81m... (Persistent device token)",
  "server_name": "Ultron Desktop",
  "hostname": "ULTRON-PC",
  "cert_fingerprint": "B4:6A:19:...",
  "expires_in": 86400
}
```
- **Error `401 Unauthorized`**: Invalid or expired pairing code.
- **Error `429 Too Many Requests`**: Rate-limit exceeded.

#### `POST /api/v1/mobile/device-login`
Re-authenticates a paired companion using its persistent `device_token`.
- **Request Body**:
```json
{
  "device_token": "j9L_81m...",
  "device_id": "c1f7b098-4b7f-44de-87d4-8d96b9f2910d"
}
```
- **Response `200 OK`**:
```json
{
  "ok": true,
  "token": "fresh_session_token_...",
  "device_name": "Pixel 8 Pro",
  "hostname": "ULTRON-PC",
  "cert_fingerprint": "B4:6A:19:..."
}
```

#### `POST /api/v1/mobile/disconnect`
Revokes the caller's device token and session token.
- **Auth**: Bearer token required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "message": "Device disconnected and session revoked."
}
```

---

### 2.3 Assistant Chat Bridge

#### `POST /api/v1/mobile/chat`
Submits a user prompt to the Ultron engine and synchronizes with desktop HUD.
- **Auth**: Bearer token required.
- **Request Body**:
```json
{
  "prompt": "Ultron, lock workstation and engage focus mode.",
  "language": "en"
}
```
- **Response `200 OK`**:
```json
{
  "ok": true,
  "reply": "Workstation perimeter locked. Focus acoustic matrices engaged, Boss.",
  "timestamp": "14:22:05",
  "source": "desktop"
}
```

---

### 2.4 Action Catalogue & Approval Queue

#### `GET /api/v1/mobile/actions`
Returns the curated list of authorized workstation actions.
- **Auth**: Bearer token required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "actions": [
    {
      "id": "pc_lock",
      "name": "Lock Workstation",
      "category": "system_security",
      "risk_level": "high_impact",
      "description": "Immediately locks the desktop workstation screen session.",
      "requires_approval": true,
      "parameters": []
    },
    {
      "id": "media_play_pause",
      "name": "Play / Pause Media",
      "category": "media_playback",
      "risk_level": "reversible",
      "description": "Toggles active desktop media playback.",
      "requires_approval": false,
      "parameters": []
    }
  ]
}
```

#### `POST /api/v1/mobile/actions/execute`
Executes an action, or enqueues into approval queue if `high_impact`. Supports idempotency keys.
- **Auth**: Bearer token required.
- **Request Body**:
```json
{
  "action_id": "pc_lock",
  "params": {},
  "idempotency_key": "uuid-v4-client-req-id"
}
```
- **Response for High-Impact (`200 OK`)**:
```json
{
  "ok": true,
  "status": "pending_approval",
  "approval_id": "5a9d28e1b04f",
  "title": "Lock Workstation",
  "message": "Action is high-impact and requires explicit confirmation in the approval queue."
}
```
- **Response for Safe Action (`200 OK`)**:
```json
{
  "ok": true,
  "status": "succeeded",
  "result": "Toggled media Play/Pause"
}
```

#### `GET /api/v1/mobile/actions/approvals`
Lists pending high-impact action approvals awaiting user confirmation.
- **Auth**: Bearer token required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "approvals": [
    {
      "approval_id": "5a9d28e1b04f",
      "action_id": "pc_lock",
      "title": "Lock Workstation",
      "detail": "Immediately locks the desktop workstation screen session.",
      "target": "ULTRON-PC",
      "risk_level": "high_impact",
      "time_remaining_sec": 84
    }
  ]
}
```

#### `POST /api/v1/mobile/actions/approve`
Explicitly confirms or cancels a pending approval.
- **Auth**: Bearer token required.
- **Request Body**:
```json
{
  "approval_id": "5a9d28e1b04f",
  "approved": true
}
```
- **Response `200 OK`**:
```json
{
  "ok": true,
  "status": "succeeded",
  "result": "Workstation locked successfully"
}
```

#### `GET /api/v1/mobile/actions/audit-log`
Returns recent action execution audit records.
- **Auth**: Bearer token required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "audit_trail": [
    {
      "timestamp": "2026-10-24T10:14:00Z",
      "action_id": "pc_lock",
      "status": "succeeded",
      "detail": "Workstation locked successfully",
      "initiated_by": "Pixel 8 Pro",
      "target": "ULTRON-PC"
    }
  ]
}
```

---

### 2.5 Workspace Presets

#### `GET /api/v1/mobile/presets`
Returns confirmed workspace presets from `memory/workspace_presets.json`.
- **Auth**: Bearer token required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "presets": [
    {
      "name": "coding",
      "title": "NEURAL CODING SANCTUARY",
      "description": "Full developer workspace with code editor, terminal, and focus stream.",
      "apps": ["vscode", "terminal"],
      "volume": 35,
      "brightness": 80,
      "dnd": true
    },
    {
      "name": "gaming",
      "title": "TACTICAL GAMING OVERDRIVE",
      "description": "High-performance gaming setup with Discord comms and Steam.",
      "apps": ["steam", "discord"],
      "volume": 70,
      "brightness": 95,
      "dnd": false
    }
  ]
}
```

#### `POST /api/v1/mobile/presets/apply`
- **Auth**: Bearer token required.
- **Request Body**:
```json
{
  "preset_name": "coding"
}
```
- **Response `200 OK`**:
```json
{
  "ok": true,
  "preset": "coding",
  "title": "NEURAL CODING SANCTUARY",
  "message": "Engaged NEURAL CODING SANCTUARY on workstation."
}
```

---

### 2.6 Neural Memory

#### `GET /api/v1/mobile/memory`
Queries desktop long-term memory entries.
- **Query Params**: `search` (optional substring), `category` (optional)
- **Auth**: Bearer token required.
- **Response `200 OK`**:
```json
{
  "ok": true,
  "total": 3,
  "entries": [
    { "category": "projects", "key": "Ultron", "value": "Desktop AI assistant", "updated": "2026-10-24" }
  ],
  "storage_location": "Desktop Long-Term Memory (memory/long_term.json)"
}
```

#### `POST /api/v1/mobile/memory`
- **Request Body**:
```json
{
  "key": "primary_editor",
  "value": "Android Studio and Neovim",
  "category": "preferences"
}
```

#### `DELETE /api/v1/mobile/memory?key=primary_editor&category=preferences`
Removes an entry from desktop memory.

---

### 2.7 WebSocket Real-Time Event Stream

- **URL**: `ws://<desktop-ip>:8000/ws?token=<session_token>` (or `wss://`)
- **Events Emitted by Server**:
  - `{"type": "chat_message", "sender": "ultron", "text": "...", "time": "12:00:00"}`
  - `{"type": "approval_required", "approval_id": "...", "title": "...", "expires_in": 90}`
  - `{"type": "approval_resolved", "approval_id": "...", "approved": true, "status": "succeeded"}`
  - `{"type": "sys", "text": "..."}`
  - `{"type": "breach_alert", "zone": "...", "timestamp": "..."}`
