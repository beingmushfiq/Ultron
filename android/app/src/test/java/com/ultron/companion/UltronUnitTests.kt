package com.ultron.companion

import com.ultron.companion.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class UltronUnitTests {

    @Test
    fun testActionRiskLevelClassification() {
        val lockAction = UltronAction(
            id = "pc_lock",
            name = "Lock Workstation",
            category = "pc_control",
            risk_level = "high_impact",
            description = "Lock screen",
            requires_approval = true
        )
        assertTrue(lockAction.isHighImpact)
        assertFalse(lockAction.isReversible)
        assertFalse(lockAction.isReadOnly)

        val mediaAction = UltronAction(
            id = "media_play_pause",
            name = "Play/Pause",
            category = "media",
            risk_level = "reversible",
            description = "Toggle media playback",
            requires_approval = false
        )
        assertFalse(mediaAction.isHighImpact)
        assertTrue(mediaAction.isReversible)
        assertFalse(mediaAction.isReadOnly)

        val readClipboardAction = UltronAction(
            id = "get_clipboard",
            name = "Read Clipboard",
            category = "clipboard",
            risk_level = "read_only",
            description = "Read text from clipboard",
            requires_approval = false
        )
        assertFalse(readClipboardAction.isHighImpact)
        assertFalse(readClipboardAction.isReversible)
        assertTrue(readClipboardAction.isReadOnly)
    }

    @Test
    fun testPendingApprovalModel() {
        val approval = PendingApproval(
            approval_id = "appr-001",
            action_id = "pc_lock",
            title = "Lock Workstation",
            detail = "Locks the screen",
            target = "Ultron-PC",
            risk_level = "high_impact",
            time_remaining_sec = 85
        )

        assertEquals("appr-001", approval.approval_id)
        assertEquals("pc_lock", approval.action_id)
        assertEquals(85, approval.time_remaining_sec)
    }

    @Test
    fun testSha256FingerprintFormat() {
        val sampleCertBytes = "TEST-CERTIFICATE-BYTES-FOR-ULTRON".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(sampleCertBytes)
        val fingerprint = digest.joinToString(":") { "%02X".format(it) }

        assertTrue(fingerprint.contains(":"))
        assertEquals(32, digest.size) // 32 bytes = 256 bits
        assertEquals(32 * 3 - 1, fingerprint.length) // XX:XX:XX... format length = 95
    }

    @Test
    fun testChatMessageProvenance() {
        val desktopMessage = ChatMessage(
            id = "msg-1",
            sender = MessageSender.ASSISTANT,
            text = "Command executed.",
            timestamp = "12:00:00",
            source = MessageSource.DESKTOP
        )
        assertEquals(MessageSource.DESKTOP, desktopMessage.source)
        assertFalse(desktopMessage.isError)

        val fallbackMessage = ChatMessage(
            id = "msg-2",
            sender = MessageSender.SYSTEM,
            text = "Desktop server unreachable.",
            timestamp = "12:00:05",
            isError = true,
            source = MessageSource.LOCAL_FALLBACK
        )
        assertEquals(MessageSource.LOCAL_FALLBACK, fallbackMessage.source)
        assertTrue(fallbackMessage.isError)
    }
}
