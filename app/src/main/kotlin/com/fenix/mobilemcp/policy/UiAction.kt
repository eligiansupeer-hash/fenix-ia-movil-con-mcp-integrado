package com.fenix.mobilemcp.policy

data class UiAction(
    val pkg: String?,
    val actionType: String,
    val isFinancial: Boolean = false,
    val isSensitive: Boolean = false,
    val isEmergency: Boolean = false
)
