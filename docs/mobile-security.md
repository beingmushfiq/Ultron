# Ultron Mobile Companion — Security & Privacy Architecture

## 1. Threat Model & Trust Boundaries

Ultron Desktop is an autonomous workstation orchestrator with local file management, terminal automation, power settings, and cyber analysis capabilities. The Android Companion acts as a privileged remote-control surface. Security is designed defensively from the assumption that the local network could have untrusted actors.

### 1.1 Assets & Boundaries
- **Workstation System State**: Screen locks, system sleep, power control, process state.
- **Desktop Long-Term Memory**: Stored user preferences, project notes, relationship context (`memory/long_term.json`).
- **Cryptographic Tokens**: Session bearer tokens, persistent device tokens, AES session keys.
- **Microphone Audio**: Real-time voice data captured on mobile device.
- **Boundary**: All communications traverse the boundary between the Android device and the desktop host (`d:\Ultron`).

### 1.2 Threat Actors & Abuse Cases
| Threat Actor | Vector | Abuse Case | Mitigation |
| :--- | :--- | :--- | :--- |
| **LAN Attacker** | Man-in-the-middle / sniffing | Intercept plaintext commands or credentials | Optional self-signed TLS/WSS; trust-on-first-use (TOFU) SHA-256 fingerprint pinning; session bearer tokens. |
| **Rogue Client / Brute-Force** | Pairing endpoint abuse | Guess 6-character PIN to gain workstation access | Strict rate-limiting (5 failed attempts per 5 minutes per IP); 600s PIN expiration; one-time consumption upon pair. |
| **Compromised Mobile UI** | Script injection / direct API calls | Bypass UI confirmation dialogs to execute high-impact actions (e.g. `pc_lock`, `pc_sleep`) | **Server-side approval gate**: Desktop holds high-impact actions in pending queue; execution requires explicit secondary approval endpoint call. |
| **Stolen Mobile Device** | Persistent device token misuse | Unauthorized workstation control | Immediate session and device revocation via `/api/v1/mobile/disconnect` or desktop `/api/revoke-devices`. |
| **Replay Attack** | Replaying intercepted requests | Repeating high-impact actions | Unique `idempotency_key` per execution; timestamp freshness validation. |

---

## 2. Authentication & Credential Lifecycle

1. **Short-Lived Pairing Code**:
   - The desktop operator clicks "Remote Control" in the Ultron UI or executes `server.new_key()`.
   - Generates an unguessable 6-character alphanumeric key (excluding ambiguous characters `O, 0, I, 1, L`).
   - Hard TTL: 600 seconds (10 minutes).
   - Rate limiting: Maximum 5 failed attempts per 5-minute rolling window per client IP.
   - Destroyed immediately upon first successful use.

2. **Dual-Token Architecture**:
   - **Session Token**: Random 32-byte URL-safe string. Held in volatile memory or Keystore-encrypted storage; used in the `Authorization: Bearer <token>` header.
   - **Device Token**: Random 32-byte URL-safe string. Stored exclusively in Android Keystore-backed `EncryptedSharedPreferences`. Allows seamless reconnection via `/api/v1/mobile/device-login` without exposing the master password or requiring repeated PIN entry.

3. **Revocation & Disconnect**:
   - Calling `POST /api/v1/mobile/disconnect` revokes both the session token and the device token on the desktop immediately.
   - Desktop operator can call `POST /api/revoke-devices` to instantly revoke all paired companions.

---

## 3. Action Risk Levels & Server-Side Gates

Ultron classifies every remote action into three strictly enforced categories:

```
┌────────────────────────────────────────────────────────┐
│                   Action Categorization                │
├───────────────────┬────────────────────────────────────┤
│ Read-Only         │ Read clipboard, query memory,      │
│                   │ fetch status (Zero side effects)   │
├───────────────────┼────────────────────────────────────┤
│ Reversible        │ Play/Pause media, volume up/down,  │
│                   │ copy to clipboard, change preset   │
├───────────────────┼────────────────────────────────────┤
│ High-Impact       │ Lock workstation, suspend/sleep,   │
│ (Requires Gate)   │ purge temp files, run cyber audit  │
└───────────────────┴────────────────────────────────────┘
```

### High-Impact Approval Workflow
1. Client requests `POST /api/v1/mobile/actions/execute` with `pc_lock`.
2. Desktop server recognizes `pc_lock` as `high_impact` (`requires_approval = true`).
3. Desktop creates a `PendingApproval` record in `MobileApiManager` with a **90-second timeout** and emits `{"type": "approval_required", ...}` to the desktop HUD and mobile clients.
4. Action execution is paused; the API returns `status: "pending_approval"`.
5. User must review the target and description on screen and tap **CONFIRM** (`POST /api/v1/mobile/actions/approve` with `approved: true`).
6. Only upon receipt of the explicit approval does the desktop execute the action.
7. If rejected or timed out, the action is discarded, and an audit log event is recorded.

---

## 4. Android Device Permissions & Privacy

- **Microphone (`RECORD_AUDIO`)**:
  - Push-to-talk only while the app is in the foreground.
  - No background recording, no stealth audio capture, and no hidden foreground services.
  - Permission is requested at runtime with a plain explanation only when the user taps the microphone button.
  - Text input always remains 100% operational even if microphone permission is denied.
- **Local Keystore Encryption**:
  - `SecureStorageManager` encrypts all stored settings and tokens using `MasterKey` (AES256-GCM / AES256-SIV).
- **Diagnostics Redaction**:
  - Diagnostic export (`SettingsViewModel.getRedactedDiagnostics`) redacts all session tokens, private keys, transcripts, and personal information.

---

## 5. Licensing, Branding & Intellectual Property Review

Before distributing or publishing the Ultron Companion application:

1. **Repository License Discrepancy**:
   - The desktop Ultron `README.md` claims an **MIT License**, but no top-level `LICENSE` file exists in the source repository. An official `LICENSE` file should be committed to clarify rights.
2. **Third-Party Trademarks & Persona**:
   - The desktop project uses "Ultron", "MCU Ultron (James Spader)", and "Jarvis" references.
   - For public store distribution (e.g. Google Play), avoid claiming official Marvel / Disney affiliation. The companion app uses original abstract metallic styling, original UI palettes, and system TTS rather than unauthorized voice clones or copyrighted movie assets.
