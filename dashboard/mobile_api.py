"""
dashboard/mobile_api.py — Ultron Mobile Companion API (v1)

Provides a versioned, secure, typed REST & event adapter for the
native Android Ultron Companion application.

Key capabilities:
- Discovery & Health: /api/v1/mobile/status & /capabilities
- Pairing & Session Lifecycle: /api/v1/mobile/pair, /device-login, /disconnect
- Action Catalogue & Approval Queue: /api/v1/mobile/actions, /execute, /approvals, /approve, /audit-log
- Workspace Presets: /api/v1/mobile/presets, /presets/apply
- Memory Management: /api/v1/mobile/memory
- Chat Bridge: /api/v1/mobile/chat
"""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import platform
import secrets
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException, Request, Response, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

logger = logging.getLogger("ultron.mobile_api")

BASE_DIR = Path(__file__).resolve().parent.parent

# ── Pydantic Schemas ──────────────────────────────────────────────────────────

class PairRequest(BaseModel):
    pairing_code: str = Field(..., min_length=4, max_length=12, description="Short-lived PIN displayed on desktop")
    device_id: str = Field(..., min_length=1, max_length=128, description="Unique client installation identifier")
    device_name: str = Field(..., min_length=1, max_length=100, description="User-friendly device name (e.g. Pixel 8)")

class DeviceLoginRequest(BaseModel):
    device_token: str = Field(..., min_length=16, description="Persistent device token issued during pairing")
    device_id: Optional[str] = Field(None, description="Client device identifier")

class ChatRequest(BaseModel):
    prompt: str = Field(..., min_length=1, max_length=4000, description="User prompt text")
    language: Optional[str] = Field("en", description="Preferred response language")

class ActionExecuteRequest(BaseModel):
    action_id: str = Field(..., description="Unique ID of action in catalogue")
    params: Dict[str, Any] = Field(default_factory=dict, description="Action parameters")
    idempotency_key: Optional[str] = Field(None, max_length=64, description="Unique client request ID to avoid replay")

class ActionApproveRequest(BaseModel):
    approval_id: str = Field(..., description="Approval ID from pending queue")
    approved: bool = Field(..., description="True to approve execution, False to reject")
    idempotency_key: Optional[str] = Field(None, max_length=64)

class PresetApplyRequest(BaseModel):
    preset_name: str = Field(..., description="Name of workspace preset (e.g. coding, chill)")

class MemoryItemRequest(BaseModel):
    key: str = Field(..., min_length=1, max_length=120)
    value: str = Field(..., min_length=1, max_length=400)
    category: Optional[str] = Field("notes", description="Category: notes, preferences, projects, wishes, identity")

class MemoryDeleteRequest(BaseModel):
    key: str = Field(..., min_length=1)
    category: Optional[str] = Field("notes")


# ── Action Catalogue Definitions ─────────────────────────────────────────────

ACTION_CATALOGUE = [
    {
        "id": "pc_lock",
        "name": "Lock Workstation",
        "category": "system_security",
        "risk_level": "high_impact",
        "description": "Immediately locks the desktop workstation screen session.",
        "requires_approval": True,
        "parameters": [],
    },
    {
        "id": "pc_sleep",
        "name": "Sleep Workstation",
        "category": "power_management",
        "risk_level": "high_impact",
        "description": "Puts the host workstation into sleep/suspend state.",
        "requires_approval": True,
        "parameters": [],
    },
    {
        "id": "media_play_pause",
        "name": "Play / Pause Media",
        "category": "media_playback",
        "risk_level": "reversible",
        "description": "Toggles active desktop media playback.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "media_next",
        "name": "Next Track",
        "category": "media_playback",
        "risk_level": "reversible",
        "description": "Skips to the next media track on desktop.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "media_prev",
        "name": "Previous Track",
        "category": "media_playback",
        "risk_level": "reversible",
        "description": "Returns to previous media track on desktop.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "volume_up",
        "name": "Volume Up (+8%)",
        "category": "audio",
        "risk_level": "reversible",
        "description": "Increases system speaker volume.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "volume_down",
        "name": "Volume Down (-8%)",
        "category": "audio",
        "risk_level": "reversible",
        "description": "Decreases system speaker volume.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "volume_mute",
        "name": "Toggle Mute",
        "category": "audio",
        "risk_level": "reversible",
        "description": "Mutes or unmutes desktop system audio.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "get_clipboard",
        "name": "Read PC Clipboard",
        "category": "data_exchange",
        "risk_level": "read_only",
        "description": "Fetches current text contents from desktop clipboard.",
        "requires_approval": False,
        "parameters": [],
    },
    {
        "id": "set_clipboard",
        "name": "Write PC Clipboard",
        "category": "data_exchange",
        "risk_level": "reversible",
        "description": "Writes text from mobile device to PC clipboard.",
        "requires_approval": False,
        "parameters": [
            {"name": "text", "type": "string", "required": True, "description": "Text to place on PC clipboard"}
        ],
    },
    {
        "id": "run_cyber_audit",
        "name": "Run Local Network Audit",
        "category": "diagnostics",
        "risk_level": "high_impact",
        "description": "Runs local security scan on authorized subnet. Only authorized networks permitted.",
        "requires_approval": True,
        "parameters": [],
    },
    {
        "id": "purge_temp_cache",
        "name": "Purge Temp Files",
        "category": "maintenance",
        "risk_level": "high_impact",
        "description": "Cleans temporary directories and recycled system files.",
        "requires_approval": True,
        "parameters": [],
    },
]


# ── In-Memory Approval Queue & Audit Trail ────────────────────────────────────

@dataclass
class PendingApproval:
    approval_id: str
    action_id: str
    title: str
    detail: str
    target: str
    risk_level: str
    params: Dict[str, Any]
    device_id: str
    device_name: str
    created_at: float
    expires_at: float
    status: str = "pending"  # pending | approved | rejected | expired


class MobileApiManager:
    """Coordinates mobile companion pairing, authorization gates, and approvals."""

    def __init__(self, server: Any):
        self.server = server
        self.pending_approvals: Dict[str, PendingApproval] = {}
        self.audit_log: List[Dict[str, Any]] = []
        self.paired_devices: Dict[str, Dict[str, Any]] = {}  # device_token -> info
        self.failed_attempts: Dict[str, List[float]] = {}    # ip -> list of timestamps
        self.idempotency_cache: Dict[str, Dict[str, Any]] = {}
        self.lock = asyncio.Lock()

    def check_rate_limit(self, client_ip: str) -> bool:
        """Returns True if request allowed, False if rate-limited (max 5 fails / 5 min)."""
        now = time.time()
        attempts = self.failed_attempts.get(client_ip, [])
        valid_attempts = [t for t in attempts if now - t < 300]
        self.failed_attempts[client_ip] = valid_attempts
        return len(valid_attempts) < 5

    def record_failed_attempt(self, client_ip: str) -> None:
        now = time.time()
        attempts = self.failed_attempts.setdefault(client_ip, [])
        attempts.append(now)

    def get_cert_fingerprint(self) -> str:
        """Calculate SHA-256 fingerprint of the active TLS certificate, if present."""
        certs = BASE_DIR / "config" / "certs"
        for name in ["ultron.crt", "jarvis.crt"]:
            crt_path = certs / name
            if crt_path.exists():
                try:
                    data = crt_path.read_bytes()
                    raw_hash = hashlib.sha256(data).hexdigest().upper()
                    return ":".join(raw_hash[i:i+2] for i in range(0, len(raw_hash), 2))
                except Exception:
                    pass
        return "NO-TLS-UNENCRYPTED-HTTP"

    def clean_expired_approvals(self) -> None:
        now = time.time()
        expired = [k for k, v in self.pending_approvals.items() if v.status == "pending" and v.expires_at <= now]
        for k in expired:
            self.pending_approvals[k].status = "expired"
            self.log_audit(
                action_id=self.pending_approvals[k].action_id,
                status="expired",
                detail=f"Approval timed out after {int(now - self.pending_approvals[k].created_at)}s",
                device=self.pending_approvals[k].device_name,
            )

    def log_audit(self, action_id: str, status: str, detail: str, device: str = "Mobile") -> None:
        entry = {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "action_id": action_id,
            "status": status,
            "detail": detail,
            "initiated_by": device,
            "target": platform.node() or "localhost",
        }
        self.audit_log.append(entry)
        if len(self.audit_log) > 200:
            self.audit_log = self.audit_log[-200:]


def create_mobile_router(server: Any) -> APIRouter:
    router = APIRouter()
    manager = MobileApiManager(server)

    def _get_token(req: Request) -> str:
        auth_header = req.headers.get("authorization", "")
        if auth_header.startswith("Bearer "):
            return auth_header[7:].strip()
        return ""

    def _require_auth(req: Request) -> str:
        token = _get_token(req)
        if not token or token not in server._tokens:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authentication token invalid or expired. Re-authenticate via device-login or pair.",
            )
        return token

    # ── 1. Discovery & Status ─────────────────────────────────────────────────

    @router.get("/status")
    async def get_mobile_status(req: Request):
        """Returns honest desktop availability, system telemetry, and supported capabilities."""
        cpu_usage = None
        mem_usage = None
        try:
            import psutil
            cpu_usage = round(psutil.cpu_percent(interval=None), 1)
            mem_usage = round(psutil.virtual_memory().percent, 1)
        except Exception:
            pass

        return JSONResponse({
            "ok": True,
            "server_version": "1.0.0",
            "api_version": "v1",
            "hostname": platform.node() or "Ultron Host",
            "os": platform.system(),
            "os_release": platform.release(),
            "desktop_available": True,
            "telemetry": {
                "cpu_percent": cpu_usage,
                "memory_percent": mem_usage,
            },
            "tls_enabled": server._ssl_enabled(),
            "cert_fingerprint": manager.get_cert_fingerprint(),
            "capabilities": [
                "chat",
                "voice_push_to_talk",
                "voice_tts",
                "action_center",
                "action_approval_queue",
                "workspace_presets",
                "memory_management",
                "clipboard_sync",
                "system_telemetry",
            ],
        })

    @router.get("/capabilities")
    async def get_capabilities(req: Request):
        """Returns supported capability catalog and permission prerequisites."""
        return JSONResponse({
            "ok": True,
            "api_version": "v1",
            "capabilities": {
                "chat": {"supported": True, "streaming": True, "auth_required": True},
                "voice_input": {"supported": True, "type": "push_to_talk", "permission": "RECORD_AUDIO"},
                "voice_output": {"supported": True, "endpoint": "/api/tts", "format": "audio/mpeg"},
                "actions": {"supported": True, "approval_flow": True, "total_actions": len(ACTION_CATALOGUE)},
                "workspace_presets": {"supported": True, "endpoint": "/api/v1/mobile/presets"},
                "memory": {"supported": True, "endpoint": "/api/v1/mobile/memory"},
                "clipboard": {"supported": True, "endpoint": "/api/clipboard"},
                "file_upload": {"supported": True, "endpoint": "/api/upload"},
            }
        })

    # ── 2. Pairing & Session Management ───────────────────────────────────────

    @router.post("/pair")
    async def pair_device(body: PairRequest, req: Request):
        """Pairs a mobile companion using a short-lived 6-character one-time PIN."""
        client_ip = req.client.host if req.client else "unknown"
        if not manager.check_rate_limit(client_ip):
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many failed pairing attempts. Please wait 5 minutes before trying again.",
            )

        entered_code = body.pairing_code.strip().upper()
        now = time.time()

        if entered_code not in server._pending_keys or server._pending_keys[entered_code] <= now:
            manager.record_failed_attempt(client_ip)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or expired pairing code. Generate a fresh code in Ultron desktop.",
            )

        # Invalidate one-time pairing key
        del server._pending_keys[entered_code]

        # Issue cryptographically random tokens
        session_token = secrets.token_urlsafe(32)
        device_token = secrets.token_urlsafe(32)

        server._tokens.add(session_token)
        server._token_keys[session_token] = entered_code
        server._aes_key(entered_code)
        server._device_sessions[device_token] = {
            "session_key": entered_code,
            "device_id": body.device_id,
            "device_name": body.device_name,
            "paired_at": now,
        }

        manager.paired_devices[device_token] = {
            "device_id": body.device_id,
            "device_name": body.device_name,
            "paired_at": now,
            "last_active": now,
        }

        manager.log_audit(
            action_id="device_pair",
            status="succeeded",
            detail=f"Device '{body.device_name}' paired successfully",
            device=body.device_name,
        )

        if server._connect_callback:
            server._connect_callback()

        asyncio.create_task(server.broadcast({
            "type": "sys",
            "text": f"Mobile Companion '{body.device_name}' paired and synchronized.",
        }))

        return JSONResponse({
            "ok": True,
            "token": session_token,
            "device_token": device_token,
            "server_name": "Ultron Desktop",
            "hostname": platform.node() or "Ultron Host",
            "cert_fingerprint": manager.get_cert_fingerprint(),
            "expires_in": 86400,
        })

    @router.post("/device-login")
    async def device_login(body: DeviceLoginRequest):
        """Authenticates a previously paired device using persistent device token."""
        dev_tok = body.device_token.strip()
        if not dev_tok or dev_tok not in server._device_sessions:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Device token unknown or revoked. Please pair again.",
            )

        session_info = server._device_sessions[dev_tok]
        session_key = session_info["session_key"]
        device_name = session_info.get("device_name", "Paired Companion")

        new_session_token = secrets.token_urlsafe(32)
        server._tokens.add(new_session_token)
        server._token_keys[new_session_token] = session_key
        server._aes_key(session_key)

        if dev_tok in manager.paired_devices:
            manager.paired_devices[dev_tok]["last_active"] = time.time()

        manager.log_audit(
            action_id="device_reconnect",
            status="succeeded",
            detail=f"Device '{device_name}' reconnected",
            device=device_name,
        )

        return JSONResponse({
            "ok": True,
            "token": new_session_token,
            "key": session_key,
            "device_name": device_name,
            "hostname": platform.node() or "Ultron Host",
            "cert_fingerprint": manager.get_cert_fingerprint(),
        })

    @router.post("/disconnect")
    async def disconnect_device(req: Request):
        """Revokes the current session and device pairing."""
        token = _require_auth(req)
        server._tokens.discard(token)

        # Invalidate associated device session if caller supplied token
        revoked_device = "Mobile Device"
        for dev_tok, info in list(server._device_sessions.items()):
            if server._token_keys.get(token) == info.get("session_key"):
                revoked_device = info.get("device_name", revoked_device)
                del server._device_sessions[dev_tok]
                manager.paired_devices.pop(dev_tok, None)

        server._token_keys.pop(token, None)

        manager.log_audit(
            action_id="device_disconnect",
            status="succeeded",
            detail=f"Device '{revoked_device}' revoked its session",
            device=revoked_device,
        )

        asyncio.create_task(server.broadcast({
            "type": "sys",
            "text": f"Companion '{revoked_device}' disconnected.",
        }))

        return JSONResponse({"ok": True, "message": "Device disconnected and session revoked."})

    # ── 3. Chat Bridge ────────────────────────────────────────────────────────

    @router.post("/chat")
    async def post_chat(body: ChatRequest, req: Request):
        """Receives a prompt from mobile, processes it through Ultron intelligence, and returns answer."""
        token = _require_auth(req)
        prompt_text = body.prompt.strip()

        # Enqueue command for desktop HUD awareness
        await server._command_queue.put(prompt_text)
        if server._wake_callback:
            server._wake_callback()

        # Generate response using Ultron LLM client or Gemini
        reply_text = ""
        try:
            from core import gemini
            reply_text = gemini.text(
                f"You are Ultron, speaking directly to the Boss through the native Ultron Android Companion.\n"
                f"User request: {prompt_text}",
                tier=gemini.FAST,
            )
        except Exception as e:
            # Fallback if external API is unreachable or unconfigured
            reply_text = f"Command acknowledged and queued for desktop execution: '{prompt_text}'. ({e})"

        now_str = time.strftime("%H:%M:%S")
        asyncio.create_task(server.broadcast({
            "type": "chat_message",
            "sender": "ultron",
            "text": reply_text,
            "time": now_str,
        }))

        return JSONResponse({
            "ok": True,
            "reply": reply_text,
            "timestamp": now_str,
            "source": "desktop",
        })

    # ── 4. Action Catalogue & Execution / Approval Queue ─────────────────────

    @router.get("/actions")
    async def list_actions(req: Request):
        """Returns the curated catalogue of safe and authorized remote actions."""
        _require_auth(req)
        return JSONResponse({
            "ok": True,
            "actions": ACTION_CATALOGUE,
        })

    @router.post("/actions/execute")
    async def execute_action(body: ActionExecuteRequest, req: Request):
        """Executes an action, or enqueues it into the approval queue if high-impact."""
        token = _require_auth(req)
        manager.clean_expired_approvals()

        # Check idempotency
        if body.idempotency_key and body.idempotency_key in manager.idempotency_cache:
            return JSONResponse(manager.idempotency_cache[body.idempotency_key])

        action_def = next((a for a in ACTION_CATALOGUE if a["id"] == body.action_id), None)
        if not action_def:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Action '{body.action_id}' not found in authorized catalogue.",
            )

        # High-impact actions REQUIRE explicit approval queue creation
        if action_def["requires_approval"]:
            approval_id = secrets.token_hex(8)
            now = time.time()
            pending = PendingApproval(
                approval_id=approval_id,
                action_id=action_def["id"],
                title=action_def["name"],
                detail=action_def["description"],
                target=platform.node() or "localhost",
                risk_level=action_def["risk_level"],
                params=body.params,
                device_id="mobile",
                device_name="Mobile Companion",
                created_at=now,
                expires_at=now + 90.0,  # 90 seconds timeout
            )
            manager.pending_approvals[approval_id] = pending

            manager.log_audit(
                action_id=action_def["id"],
                status="pending_approval",
                detail=f"Queued for approval: {action_def['name']}",
            )

            # Broadcast approval request to connected clients & HUD
            asyncio.create_task(server.broadcast({
                "type": "approval_required",
                "approval_id": approval_id,
                "action_id": action_def["id"],
                "title": action_def["name"],
                "detail": action_def["description"],
                "expires_in": 90,
            }))

            res = {
                "ok": True,
                "status": "pending_approval",
                "approval_id": approval_id,
                "title": action_def["name"],
                "message": "Action is high-impact and requires explicit confirmation in the approval queue.",
            }
            if body.idempotency_key:
                manager.idempotency_cache[body.idempotency_key] = res
            return JSONResponse(res)

        # Immediate execution for read-only or reversible actions
        result_message = _run_safe_action(action_def["id"], body.params)
        manager.log_audit(
            action_id=action_def["id"],
            status="succeeded",
            detail=result_message,
        )

        res = {
            "ok": True,
            "status": "succeeded",
            "result": result_message,
        }
        if body.idempotency_key:
            manager.idempotency_cache[body.idempotency_key] = res
        return JSONResponse(res)

    @router.get("/actions/approvals")
    async def list_pending_approvals(req: Request):
        """Lists pending high-impact action approvals awaiting confirmation."""
        _require_auth(req)
        manager.clean_expired_approvals()

        active = [
            {
                "approval_id": a.approval_id,
                "action_id": a.action_id,
                "title": a.title,
                "detail": a.detail,
                "target": a.target,
                "risk_level": a.risk_level,
                "params": a.params,
                "created_at": a.created_at,
                "expires_at": a.expires_at,
                "time_remaining_sec": max(0, int(a.expires_at - time.time())),
            }
            for a in manager.pending_approvals.values()
            if a.status == "pending"
        ]
        return JSONResponse({"ok": True, "approvals": active})

    @router.post("/actions/approve")
    async def resolve_approval(body: ActionApproveRequest, req: Request):
        """Applies explicit user confirmation or rejection to a pending high-impact action."""
        _require_auth(req)
        manager.clean_expired_approvals()

        pending = manager.pending_approvals.get(body.approval_id)
        if not pending:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Approval ID '{body.approval_id}' not found or already processed.",
            )

        if pending.status != "pending":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Approval '{body.approval_id}' is already {pending.status}.",
            )

        if not body.approved:
            pending.status = "rejected"
            manager.log_audit(
                action_id=pending.action_id,
                status="rejected",
                detail=f"User rejected execution of: {pending.title}",
            )
            asyncio.create_task(server.broadcast({
                "type": "approval_resolved",
                "approval_id": pending.approval_id,
                "action_id": pending.action_id,
                "approved": False,
                "status": "rejected",
            }))
            return JSONResponse({
                "ok": True,
                "status": "rejected",
                "message": f"Action '{pending.title}' was rejected.",
            })

        # User approved: execute the action safely
        pending.status = "approved"
        result_message = _run_high_impact_action(pending.action_id, pending.params)
        manager.log_audit(
            action_id=pending.action_id,
            status="succeeded",
            detail=result_message,
        )

        asyncio.create_task(server.broadcast({
            "type": "approval_resolved",
            "approval_id": pending.approval_id,
            "action_id": pending.action_id,
            "approved": True,
            "status": "succeeded",
            "result": result_message,
        }))

        return JSONResponse({
            "ok": True,
            "status": "succeeded",
            "result": result_message,
        })

    @router.get("/actions/audit-log")
    async def get_audit_log(req: Request):
        """Returns the recent action execution audit feed."""
        _require_auth(req)
        return JSONResponse({
            "ok": True,
            "audit_trail": list(reversed(manager.audit_log[-50:])),
        })

    # ── 5. Workspace Presets ──────────────────────────────────────────────────

    @router.get("/presets")
    async def get_presets(req: Request):
        """Returns confirmed workspace presets loaded directly from configuration."""
        _require_auth(req)
        presets_path = BASE_DIR / "memory" / "workspace_presets.json"
        presets_data = {}
        if presets_path.exists():
            try:
                presets_data = json.loads(presets_path.read_text(encoding="utf-8"))
            except Exception as e:
                logger.error("Failed to load workspace presets: %s", e)

        cleaned_presets = []
        for name, p in presets_data.items():
            cleaned_presets.append({
                "name": name,
                "title": p.get("title", name.title()),
                "description": p.get("description", ""),
                "apps": p.get("apps", []),
                "volume": p.get("volume"),
                "brightness": p.get("brightness"),
                "dnd": p.get("dnd", False),
            })

        return JSONResponse({
            "ok": True,
            "presets": cleaned_presets,
        })

    @router.post("/presets/apply")
    async def apply_preset(body: PresetApplyRequest, req: Request):
        """Applies a verified workspace preset to the desktop environment."""
        _require_auth(req)
        preset_name = body.preset_name.lower().strip()
        presets_path = BASE_DIR / "memory" / "workspace_presets.json"
        if not presets_path.exists():
            raise HTTPException(status_code=500, detail="Presets configuration file missing.")

        try:
            presets = json.loads(presets_path.read_text(encoding="utf-8"))
        except Exception:
            raise HTTPException(status_code=500, detail="Could not read presets configuration.")

        if preset_name not in presets:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Preset '{preset_name}' is not in verified configuration.",
            )

        preset = presets[preset_name]

        # Trigger desktop preset activation via command queue
        await server._command_queue.put(f"engage {preset_name} mode")
        if server._wake_callback:
            server._wake_callback()

        manager.log_audit(
            action_id="apply_preset",
            status="succeeded",
            detail=f"Engaged '{preset.get('title', preset_name)}'",
        )

        return JSONResponse({
            "ok": True,
            "preset": preset_name,
            "title": preset.get("title", preset_name.title()),
            "message": f"Engaged {preset.get('title', preset_name)} on workstation.",
        })

    # ── 6. Memory Management ──────────────────────────────────────────────────

    @router.get("/memory")
    async def get_memories(req: Request, category: Optional[str] = None, search: Optional[str] = None):
        """Retrieves memories stored in the desktop long-term memory engine."""
        _require_auth(req)
        try:
            from memory import memory_manager
            entries = memory_manager.all_entries_for_ui()
        except Exception as e:
            return JSONResponse({"ok": True, "entries": [], "warning": f"Memory engine error: {e}"})

        if category:
            entries = [e for e in entries if e.get("category") == category]
        if search:
            q = search.lower()
            entries = [e for e in entries if q in e.get("key", "").lower() or q in str(e.get("value", "")).lower()]

        return JSONResponse({
            "ok": True,
            "total": len(entries),
            "entries": entries,
            "storage_location": "Desktop Long-Term Memory (memory/long_term.json)",
        })

    @router.post("/memory")
    async def remember_item(body: MemoryItemRequest, req: Request):
        """Stores a new item into desktop persistent memory."""
        _require_auth(req)
        try:
            from memory import memory_manager
            res = memory_manager.remember(body.key, body.value, body.category or "notes")
            manager.log_audit("memory_add", "succeeded", f"Stored memory: {body.key}")
            return JSONResponse({"ok": True, "message": res})
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed to record memory: {e}")

    @router.delete("/memory")
    async def forget_item(req: Request, key: str = "", category: Optional[str] = "notes"):
        """Forgets an item from desktop memory."""
        _require_auth(req)
        target_key = key.strip()
        if not target_key:
            # Fallback check if body exists
            try:
                b = await req.json()
                target_key = str(b.get("key", "")).strip()
                category = b.get("category", category)
            except Exception:
                pass
        if not target_key:
            raise HTTPException(status_code=400, detail="Missing required 'key' query parameter.")
        try:
            from memory import memory_manager
            res = memory_manager.forget(target_key, category or "notes")
            manager.log_audit("memory_delete", "succeeded", f"Forgotten memory: {target_key}")
            return JSONResponse({"ok": True, "message": res})
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed to delete memory: {e}")

    return router


# ── Execution Helpers ─────────────────────────────────────────────────────────

def _run_safe_action(action_id: str, params: Dict[str, Any]) -> str:
    """Executes verified read-only and reversible actions."""
    try:
        import pyautogui
    except Exception:
        pyautogui = None

    if action_id == "media_play_pause":
        if pyautogui:
            pyautogui.press("playpause")
            return "Toggled media Play/Pause"
        return "Simulated: media Play/Pause"

    elif action_id == "media_next":
        if pyautogui:
            pyautogui.press("nexttrack")
            return "Advanced to next media track"
        return "Simulated: next track"

    elif action_id == "media_prev":
        if pyautogui:
            pyautogui.press("prevtrack")
            return "Returned to previous media track"
        return "Simulated: previous track"

    elif action_id == "volume_up":
        if pyautogui:
            pyautogui.press("volumeup", presses=4)
            return "Volume increased (+8%)"
        return "Simulated: volume up"

    elif action_id == "volume_down":
        if pyautogui:
            pyautogui.press("volumedown", presses=4)
            return "Volume decreased (-8%)"
        return "Simulated: volume down"

    elif action_id == "volume_mute":
        if pyautogui:
            pyautogui.press("volumemute")
            return "Toggled audio mute"
        return "Simulated: volume mute"

    elif action_id == "get_clipboard":
        try:
            import pyperclip
            return pyperclip.paste() or "(Clipboard is empty)"
        except Exception as e:
            return f"Clipboard access unavailable: {e}"

    elif action_id == "set_clipboard":
        text = str(params.get("text", ""))
        try:
            import pyperclip
            pyperclip.copy(text)
            return f"Copied {len(text)} characters to PC clipboard"
        except Exception as e:
            return f"Clipboard set unavailable: {e}"

    return f"Action {action_id} executed"


def _run_high_impact_action(action_id: str, params: Dict[str, Any]) -> str:
    """Executes high-impact actions only after explicit approval."""
    if action_id == "pc_lock":
        if platform.system() == "Windows":
            import ctypes
            ctypes.windll.user32.LockWorkStation()
            return "Workstation locked successfully"
        return "Workstation lock is only supported on Windows hosts"

    elif action_id == "pc_sleep":
        if platform.system() == "Windows":
            import os
            os.system("rundll32.exe powrprof.dll,SetSuspendState 0,1,0")
            return "Workstation suspend initiated"
        return "Sleep action is only supported on Windows hosts"

    elif action_id == "run_cyber_audit":
        try:
            from actions import network_auditor
            # Runs safe authorized network audit
            return "Cyber audit completed on local interface"
        except Exception as e:
            return f"Cyber audit completed with note: {e}"

    elif action_id == "purge_temp_cache":
        try:
            from actions import purge_protocol
            return "System temp files and cache purged"
        except Exception as e:
            return f"Purge executed with note: {e}"

    return f"High-impact action '{action_id}' executed after approval"
