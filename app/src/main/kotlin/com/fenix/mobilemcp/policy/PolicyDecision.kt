package com.fenix.mobilemcp.policy

sealed class PolicyDecision {
    object Allow : PolicyDecision()
    data class Deny(val reasonCode: String) : PolicyDecision()
    object RequireConsent : PolicyDecision()
}

object PolicyReasonCodes {
    const val FINANCIAL_PACKAGE = "FINANCIAL_PACKAGE"
    const val SENSITIVE_WINDOW = "SENSITIVE_WINDOW"
    const val UNKNOWN_PACKAGE = "UNKNOWN_PACKAGE"
    const val STALE_NODE = "STALE_NODE"
    const val IDENTITY_MISMATCH = "IDENTITY_MISMATCH"
    const val AUTH_DISABLED = "AUTH_DISABLED"
    const val EMERGENCY_LATCH = "EMERGENCY_LATCH"
    const val PERMISSION_REVOKED = "PERMISSION_REVOKED"
}
