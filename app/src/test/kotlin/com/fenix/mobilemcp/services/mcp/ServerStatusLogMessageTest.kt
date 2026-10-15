package com.fenix.mobilemcp.services.mcp

import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.data.model.ServerStatus
import com.fenix.mobilemcp.data.model.TunnelEndpoint
import com.fenix.mobilemcp.data.model.TunnelProviderType
import com.fenix.mobilemcp.data.model.TunnelStatus
import com.fenix.mobilemcp.privacy.PrivacyModeStatus
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("serverStatusLogMessageRes / privacyStatusLogMessageRes / tunnelStatusLogMessage")
class ServerStatusLogMessageTest {
    @Test
    fun `resources for all five server statuses`() {
        assertEquals(R.string.log_server_starting, serverStatusLogMessageRes(ServerStatus.Starting).first)
        val running = serverStatusLogMessageRes(ServerStatus.Running(port = 8080, bindingAddress = "127.0.0.1"))
        assertEquals(R.string.log_server_started, running.first)
        assertArrayEquals(arrayOf<Any>("127.0.0.1", 8080), running.second)
        assertEquals(R.string.log_server_stopping, serverStatusLogMessageRes(ServerStatus.Stopping).first)
        assertEquals(R.string.log_server_stopped, serverStatusLogMessageRes(ServerStatus.Stopped).first)
        // Excepción técnica: el texto del error viaja como argumento, sin traducir.
        val error = serverStatusLogMessageRes(ServerStatus.Error("boom"))
        assertEquals(R.string.log_server_error, error.first)
        assertArrayEquals(arrayOf("boom"), error.second)
    }

    @Test
    fun `resources for privacy self-check results`() {
        assertEquals(R.string.log_privacy_ready_model, privacyStatusLogMessageRes(PrivacyModeStatus.Ready).first)
        assertEquals(
            R.string.log_privacy_ready_deterministic,
            privacyStatusLogMessageRes(PrivacyModeStatus.ReadyDeterministicOnly).first,
        )
        // Excepción técnica: el motivo del fallo del modelo viaja como argumento, sin traducir.
        val unavailable = privacyStatusLogMessageRes(PrivacyModeStatus.Unavailable("model missing"))
        assertEquals(R.string.log_privacy_unavailable, unavailable.first)
        assertArrayEquals(arrayOf("model missing"), unavailable.second)
        assertEquals(R.string.log_privacy_disabled, privacyStatusLogMessageRes(PrivacyModeStatus.Disabled).first)
    }

    @Test
    fun `tunnel messages for connecting connected error`() {
        assertEquals("Tunnel connecting…", tunnelStatusLogMessage(TunnelStatus.Connecting))
        assertEquals(
            "Tunnel connected: https://a.example.com, https://b.example.com",
            tunnelStatusLogMessage(
                TunnelStatus.Connected(
                    endpoints =
                        listOf(
                            TunnelEndpoint(url = "https://a.example.com", valid = true),
                            TunnelEndpoint(url = "https://b.example.com", valid = true),
                        ),
                    providerType = TunnelProviderType.CLOUDFLARE,
                ),
            ),
        )
        assertEquals("Tunnel error: nope", tunnelStatusLogMessage(TunnelStatus.Error("nope")))
    }

    @Test
    fun `tunnel disconnected produces no observer message`() {
        assertNull(tunnelStatusLogMessage(TunnelStatus.Disconnected))
    }
}
