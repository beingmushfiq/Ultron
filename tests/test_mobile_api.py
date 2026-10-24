"""
tests/test_mobile_api.py — Unit & Integration tests for Ultron Mobile Companion API (v1)
"""

import unittest
import time
from fastapi.testclient import TestClient
from dashboard.server import DashboardServer


class TestMobileApi(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = DashboardServer()
        cls.client = TestClient(cls.server.app)

    def setUp(self):
        # Reset tokens and pending keys
        self.server._tokens.clear()
        self.server._token_keys.clear()
        self.server._pending_keys.clear()
        self.server._device_sessions.clear()

    def test_status_endpoint(self):
        res = self.client.get("/api/v1/mobile/status")
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertTrue(data.get("ok"))
        self.assertEqual(data.get("api_version"), "v1")
        self.assertTrue(data.get("desktop_available"))
        self.assertIn("capabilities", data)
        self.assertIn("chat", data["capabilities"])
        self.assertIn("action_center", data["capabilities"])
        self.assertIn("telemetry", data)

    def test_capabilities_endpoint(self):
        res = self.client.get("/api/v1/mobile/capabilities")
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertTrue(data.get("ok"))
        self.assertIn("chat", data["capabilities"])
        self.assertIn("actions", data["capabilities"])
        self.assertTrue(data["capabilities"]["actions"]["approval_flow"])

    def test_pairing_lifecycle(self):
        pin = self.server.new_key(expiry_secs=60)
        self.assertEqual(len(pin), 6)

        # 1. Pair with valid PIN
        pair_res = self.client.post("/api/v1/mobile/pair", json={
            "pairing_code": pin,
            "device_id": "test-device-uuid-123",
            "device_name": "Test Pixel Phone",
        })
        self.assertEqual(pair_res.status_code, 200)
        pair_data = pair_res.json()
        self.assertTrue(pair_data.get("ok"))
        token = pair_data.get("token")
        device_token = pair_data.get("device_token")
        self.assertTrue(token)
        self.assertTrue(device_token)

        # 2. Reusing PIN should fail (one-time use)
        reuse_res = self.client.post("/api/v1/mobile/pair", json={
            "pairing_code": pin,
            "device_id": "test-device-uuid-123",
            "device_name": "Test Pixel Phone",
        })
        self.assertEqual(reuse_res.status_code, 401)

        # 3. Authenticated request using session token
        headers = {"Authorization": f"Bearer {token}"}
        act_res = self.client.get("/api/v1/mobile/actions", headers=headers)
        self.assertEqual(act_res.status_code, 200)
        self.assertTrue(act_res.json().get("ok"))

        # 4. Device Login with device_token
        dev_res = self.client.post("/api/v1/mobile/device-login", json={
            "device_token": device_token,
            "device_id": "test-device-uuid-123",
        })
        self.assertEqual(dev_res.status_code, 200)
        fresh_token = dev_res.json().get("token")
        self.assertTrue(fresh_token)

        # 5. Disconnect device
        disc_res = self.client.post("/api/v1/mobile/disconnect", headers={"Authorization": f"Bearer {fresh_token}"})
        self.assertEqual(disc_res.status_code, 200)

        # 6. Request after disconnect should fail
        fail_res = self.client.get("/api/v1/mobile/actions", headers={"Authorization": f"Bearer {fresh_token}"})
        self.assertEqual(fail_res.status_code, 401)

    def test_unauthorized_access_rejected(self):
        res = self.client.get("/api/v1/mobile/actions")
        self.assertEqual(res.status_code, 401)

        res_bad_tok = self.client.get("/api/v1/mobile/actions", headers={"Authorization": "Bearer bad-token"})
        self.assertEqual(res_bad_tok.status_code, 401)

    def test_action_catalogue_and_safe_execution(self):
        # Setup session
        pin = self.server.new_key()
        pair_res = self.client.post("/api/v1/mobile/pair", json={
            "pairing_code": pin,
            "device_id": "dev-01",
            "device_name": "Dev",
        })
        token = pair_res.json()["token"]
        headers = {"Authorization": f"Bearer {token}"}

        # Catalogue
        cat_res = self.client.get("/api/v1/mobile/actions", headers=headers)
        self.assertEqual(cat_res.status_code, 200)
        actions = cat_res.json()["actions"]
        self.assertGreater(len(actions), 5)

        # Safe action execution (media_play_pause)
        exec_res = self.client.post("/api/v1/mobile/actions/execute", headers=headers, json={
            "action_id": "media_play_pause",
            "params": {},
        })
        self.assertEqual(exec_res.status_code, 200)
        self.assertEqual(exec_res.json()["status"], "succeeded")

    def test_high_impact_action_approval_queue(self):
        pin = self.server.new_key()
        pair_res = self.client.post("/api/v1/mobile/pair", json={
            "pairing_code": pin,
            "device_id": "dev-02",
            "device_name": "Dev",
        })
        token = pair_res.json()["token"]
        headers = {"Authorization": f"Bearer {token}"}

        # Attempt to run pc_lock (high impact)
        exec_res = self.client.post("/api/v1/mobile/actions/execute", headers=headers, json={
            "action_id": "pc_lock",
            "params": {},
        })
        self.assertEqual(exec_res.status_code, 200)
        data = exec_res.json()
        self.assertEqual(data["status"], "pending_approval")
        approval_id = data.get("approval_id")
        self.assertTrue(approval_id)

        # Check pending approvals queue
        appr_res = self.client.get("/api/v1/mobile/actions/approvals", headers=headers)
        self.assertEqual(appr_res.status_code, 200)
        pending_list = appr_res.json()["approvals"]
        self.assertTrue(any(a["approval_id"] == approval_id for a in pending_list))

        # Reject approval
        reject_res = self.client.post("/api/v1/mobile/actions/approve", headers=headers, json={
            "approval_id": approval_id,
            "approved": False,
        })
        self.assertEqual(reject_res.status_code, 200)
        self.assertEqual(reject_res.json()["status"], "rejected")

        # Check audit log
        audit_res = self.client.get("/api/v1/mobile/actions/audit-log", headers=headers)
        self.assertEqual(audit_res.status_code, 200)
        trail = audit_res.json()["audit_trail"]
        self.assertTrue(any("rejected" in a.get("status", "") for a in trail))

    def test_workspace_presets(self):
        pin = self.server.new_key()
        pair_res = self.client.post("/api/v1/mobile/pair", json={
            "pairing_code": pin,
            "device_id": "dev-03",
            "device_name": "Dev",
        })
        token = pair_res.json()["token"]
        headers = {"Authorization": f"Bearer {token}"}

        # Get presets
        presets_res = self.client.get("/api/v1/mobile/presets", headers=headers)
        self.assertEqual(presets_res.status_code, 200)
        presets = presets_res.json()["presets"]
        names = [p["name"] for p in presets]
        self.assertIn("coding", names)
        self.assertIn("gaming", names)

        # Apply preset
        apply_res = self.client.post("/api/v1/mobile/presets/apply", headers=headers, json={
            "preset_name": "coding",
        })
        self.assertEqual(apply_res.status_code, 200)
        self.assertTrue(apply_res.json()["ok"])

    def test_memory_management(self):
        pin = self.server.new_key()
        pair_res = self.client.post("/api/v1/mobile/pair", json={
            "pairing_code": pin,
            "device_id": "dev-04",
            "device_name": "Dev",
        })
        token = pair_res.json()["token"]
        headers = {"Authorization": f"Bearer {token}"}

        # Add memory
        add_res = self.client.post("/api/v1/mobile/memory", headers=headers, json={
            "key": "test_mobile_key",
            "value": "Ultron Companion Unit Test",
            "category": "notes",
        })
        self.assertEqual(add_res.status_code, 200)

        # Query memory
        mem_res = self.client.get("/api/v1/mobile/memory?search=test_mobile_key", headers=headers)
        self.assertEqual(mem_res.status_code, 200)
        entries = mem_res.json()["entries"]
        self.assertTrue(any(e["key"] == "test_mobile_key" for e in entries))

        # Delete memory
        del_res = self.client.delete("/api/v1/mobile/memory?key=test_mobile_key&category=notes", headers=headers)
        self.assertEqual(del_res.status_code, 200)


if __name__ == "__main__":
    unittest.main()
