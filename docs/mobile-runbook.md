# Ultron Mobile Companion — Operator & Developer Runbook

This runbook details step-by-step procedures for building, testing, pairing, and operating the Ultron Companion Android application and its paired desktop service.

---

## 1. Prerequisites

### 1.1 Developer Environment
- **Operating System**: Windows 10/11, macOS, or Linux.
- **Java**: Java Development Kit (JDK) 17 (Microsoft Build of OpenJDK 17 or Eclipse Temurin 17).
  ```powershell
  # Verify Java 17
  java -version
  javac -version
  ```
- **Android SDK**: Android API Level 34 Platform and Build-Tools installed via Android Studio or command-line tools. Set `ANDROID_HOME` or define `sdk.dir` in `android/local.properties`.
- **Node.js & npm**: Node.js v18+ (tested on Node v25.1.0, npm 11.9.0) for REA.
- **Python**: Python 3.11 – 3.14 with Ultron dependencies installed (`pip install -r requirements.txt`).

---

## 2. REA (Reverse Engineer Anything) Setup

REA is registered as an MCP server and provides reverse-engineering inspection tools and skills:

```bash
# 1. Run REA setup for the active agent (e.g. Antigravity)
npx -y rea-agents setup --yes --client antigravity

# 2. Verify REA readiness
npx -y rea-agents doctor
```

The bundled reverse-engineering skill is mirrored in `.agents/skills/reverse-engineer-anything/SKILL.md`.

---

## 3. Starting the Ultron Desktop Service

Launch the Ultron desktop application with its HTTP and WebSocket dashboard:

```bash
# Start full Ultron Desktop AI assistant (PyQt6 HUD + Dashboard)
python main.py

# Or start standalone Dashboard / Mobile API service for testing
python -c "import asyncio; from dashboard.server import DashboardServer; asyncio.run(DashboardServer().serve())"
```

The service listens on port `8000`:
- **Status Endpoint**: `http://localhost:8000/api/v1/mobile/status`
- **Dashboard Web UI**: `http://localhost:8000/`

---

## 4. Pairing the Mobile Companion

1. **Obtain Pairing PIN**:
   - In the Ultron Desktop UI, click **Remote Control** (or call `DashboardServer.new_key()` in console).
   - A 6-character PIN (e.g. `7K2M9P`) is displayed with a 10-minute expiration.
2. **Open Mobile Companion**:
   - Enter your Desktop LAN IP (e.g. `192.168.1.150`).
   - Enter Port `8000`.
   - Toggle **TLS** if `config/certs/ultron.crt` is in use.
   - Enter the 6-character PIN.
   - Tap **CONNECT & PAIR**.
3. **Session Established**:
   - The app verifies the server identity, records the SHA-256 certificate fingerprint, and saves the persistent `device_token` securely in the Android Keystore.
   - The central Neural Core on the phone switches from Standby to Synchronized.

---

## 5. Building & Testing Android Companion

Execute from the `android/` directory (or use Windows batch runner `gradlew.bat`):

### 5.1 Run Unit Tests
```powershell
# Windows
cd android
.\gradlew.bat :app:testDebugUnitTest

# macOS / Linux
cd android
./gradlew :app:testDebugUnitTest
```

### 5.2 Build Debug APK
```powershell
# Windows
cd android
.\gradlew.bat :app:assembleDebug

# Output APK location:
# android/app/build/outputs/apk/debug/app-debug.apk
```

### 5.3 Install on Device / Emulator via ADB
```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. Running Server-Side Backend Tests

Execute the FastAPI Mobile Companion API integration test suite:

```bash
# From workspace root (d:\Ultron)
python -m unittest tests/test_mobile_api.py -v
```

Expected output:
```text
test_action_catalogue_and_safe_execution ... ok
test_capabilities_endpoint ... ok
test_high_impact_action_approval_queue ... ok
test_memory_management ... ok
test_pairing_lifecycle ... ok
test_status_endpoint ... ok
test_unauthorized_access_rejected ... ok
test_workspace_presets ... ok

Ran 8 tests in 0.27s - OK
```

---

## 7. Troubleshooting & Common Issues

| Issue | Cause | Resolution |
| :--- | :--- | :--- |
| **Connection Timed Out (`192.168.1.X:8000`)** | Windows Firewall blocking inbound port 8000 | Ensure Ultron was allowed when the UAC firewall prompt appeared, or run: `netsh advfirewall firewall add rule name="Ultron Dashboard" dir=in action=allow protocol=TCP localport=8000` |
| **Certificate Fingerprint Mismatch** | Desktop regenerated its self-signed certificate | In mobile app **Settings**, tap **REVOKE & DISCONNECT**, then pair again with a fresh PIN to pin the new certificate. |
| **Microphone Permission Denied** | User denied RECORD_AUDIO dialog | Push-to-talk voice will be disabled, but text chat remains 100% operational. Enable microphone permission in Android App Settings if voice input is desired. |
| **"Invalid or Expired PIN"** | PIN expired (> 10 mins) or already consumed | Generate a fresh PIN in Ultron Desktop. PINs are single-use. |
