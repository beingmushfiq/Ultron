# Ultron Mobile Companion — Architectural Specification

## 1. System Overview

**Ultron Companion** (`com.ultron.companion`) is a native Android application designed as an authenticated, tactile mobile control surface and voice intelligence terminal for the Ultron Desktop AI assistant.

Rather than mechanically porting desktop-only Python modules (PyQt6 HUD, local OCR, device automation, LLM orchestration) into an Android runtime, the companion adopts a **split-responsibility architecture**:
- **Desktop (Host / Authority)**: Hosts LLM provider integrations (Gemini Live, OpenAI, Ollama), 51 workstation tools, local SQLite neural memory (`memory/long_term.json`), audio DSP engines, and action approval gates.
- **Android Companion (Client / Surface)**: Acts as an encrypted remote client providing native Jetpack Compose Material 3 UI, push-to-talk voice capture, real-time WebSocket telemetry synchronization, quick workspace presets, and an explicit action approval queue.

```
┌────────────────────────────────────────────────────────┐
│             Android Companion (Mobile Client)          │
│   ┌────────────────────────────────────────────────┐   │
│   │  Jetpack Compose M3 UI (Obsidian / Crimson)   │   │
│   └───────────────────────┬────────────────────────┘   │
│                           │ Coroutines / StateFlow     │
│   ┌───────────────────────▼────────────────────────┐   │
│   │       ViewModels & UI State Coordinators       │   │
│   └───────────────────────┬────────────────────────┘   │
│                           │                            │
│   ┌───────────────────────▼────────────────────────┐   │
│   │              UltronRepository                  │   │
│   └───────────┬──────────────────────┬─────────────┘   │
│               │                      │                 │
│   ┌───────────▼──────────┐ ┌─────────▼─────────────┐   │
│   │ SecureStorageManager │ │     MobileApiClient   │   │
│   │  (Keystore / AES)    │ │   (OkHttp / WSS / TLS)│   │
│   └──────────────────────┘ └─────────┬─────────────┘   │
└──────────────────────────────────────┼─────────────────┘
                                       │ HTTPS / WSS / LAN
┌──────────────────────────────────────▼─────────────────┐
│              Ultron Desktop Host (FastAPI)             │
│   ┌────────────────────────────────────────────────┐   │
│   │        /api/v1/mobile/* (MobileApiManager)     │   │
│   │  Pairing | Status | Actions | Presets | Memory │   │
│   └───────────────────────┬────────────────────────┘   │
│                           │ Internal Event Bus         │
│   ┌───────────────────────▼────────────────────────┐   │
│   │   Approval Queue (core/confirm) & Audit Log    │   │
│   └───────────────────────┬────────────────────────┘   │
│                           │                            │
│   ┌───────────────────────▼────────────────────────┐   │
│   │    Workstation Tools | Cerebro Memory | DSP    │   │
│   └────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

---

## 2. Component Boundaries & Responsibilities

### 2.1 Android Client Layering
The Android client follows official Android Architecture Guidelines with strict unidirectional data flow (UDF):

1. **UI Layer (`feature/*`)**:
   - `feature/home`: Real desktop availability, honest CPU/RAM telemetry, animated neural core, quick preset chips.
   - `feature/chat`: Full conversational interface with Markdown/code rendering, copy response, streaming support, and retry states.
   - `feature/voice`: Push-to-talk modal sheet, visible recording feedback, speech recognition, and TTS playback.
   - `feature/actions`: Curated action catalogue categorized by risk (`read_only`, `reversible`, `high_impact`), high-priority approval queue with countdown timers, and activity audit feed.
   - `feature/memory`: Desktop long-term fact search, categorization, fact retention, and deletion with clear provenance disclosure.
   - `feature/settings`: Connection configuration, certificate SHA-256 fingerprint verification, device rename, and session revocation.
   - `feature/onboarding`: First-run pairing setup with IP, port, TLS toggle, and 6-character PIN verification.

2. **Data Layer (`data/*` & `core/*`)**:
   - `UltronRepository`: Single source of truth for UI state. Manages WebSocket heartbeat, local caching, real-time message handling, and background polling.
   - `MobileApiClient`: OkHttp 4 HTTP and WebSocket client. Implements trust-on-first-use (TOFU) certificate validation and custom SSLSocketFactory for self-signed certificates.
   - `SecureStorageManager`: Android Keystore-backed `EncryptedSharedPreferences`. Secures device tokens, session tokens, and pinned fingerprints.

### 2.2 Desktop Integration Boundary (`dashboard/mobile_api.py`)
To isolate mobile interactions from legacy web dashboard routes, a versioned router is mounted under `/api/v1/mobile`:
- **Stateless Authentication**: Uses random 32-byte URL-safe bearer tokens issued upon PIN verification or device token exchange.
- **Strict Allowlisting**: Only actions explicitly defined in `ACTION_CATALOGUE` can be triggered remotely. Raw terminal execution is strictly blocked.
- **Two-Tier Execution**:
  - `read_only` & `reversible` actions (e.g. Volume, Media, Clipboard) execute immediately.
  - `high_impact` actions (e.g. Lock, Suspend, Network Audit, Purge) create a `PendingApproval` entry with a 90-second expiration and require an explicit confirmation handshake.

---

## 3. Key Design Decisions & Trade-Offs

| Decision | Alternative Considered | Rationale |
| :--- | :--- | :--- |
| **Native Kotlin + Jetpack Compose** | WebView wrapper around `dashboard/app.html` | A mobile companion requires native touch responsiveness, proper lifecycle handling, background resilience, TalkBack accessibility, and secure Keystore integration. |
| **Push-to-Talk Default** | Always-on background microphone wake-word | Background microphone listening drains mobile battery and requires complex foreground-service notifications. Push-to-talk respects user privacy and complies with Android Play policies. |
| **Local-First LAN & WireGuard / VPN** | Public cloud relay / port forwarding | Ultron controls sensitive desktop capabilities. Keeping the communication surface within local LAN or private overlay networks (Tailscale/WireGuard) prevents public internet exposure of port 8000. |
| **Server-Side Action Confirmation** | Client-only UI confirmation dialog | A malicious or compromised client could bypass a UI confirmation dialog. All high-impact actions are held in an in-memory queue on the desktop until an explicit signed approval request arrives. |
| **Split Memory Model** | Full bi-directional database sync | Syncing phone contacts or device files to the desktop memory graph by default is a privacy risk. Mobile explicitly manages facts stored in desktop memory without uploading device telemetry. |
