package com.lightningstudio.watchrss.ui.screen.cloud

import org.junit.Assert.*
import org.junit.Test

class CloudApprovalTest {
    @Test fun codesKeepLeadingZerosAndRequireEightAsciiDigits() {
        assertTrue(validApprovalCode("00123456"))
        listOf("1234567", "123456789", "abcdefgh", "１２３４５６７８").forEach { assertFalse(validApprovalCode(it)) }
    }
    @Test fun errorsAreActionableWithoutRawBackendResponses() {
        val invalid = friendlyApprovalError(Exception("HTTP 400: {error:approval_code_invalid}"))
        assertFalse(invalid.second)
        assertTrue(invalid.first.contains("不正确"))
        assertTrue(friendlyApprovalError(Exception("HTTP404 approval_expired")).second)
        assertTrue(friendlyApprovalError(Exception("approval_waiting")).first.contains("先在"))
        assertFalse(friendlyApprovalError(Exception("secret raw backend payload")).first.contains("payload"))
    }
}
