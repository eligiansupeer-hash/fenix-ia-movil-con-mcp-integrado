package com.fenix.mobilemcp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TunnelIdentity(
    val tunnelId: String,
    val token: String,
    val credentialsFileContent: String
)
