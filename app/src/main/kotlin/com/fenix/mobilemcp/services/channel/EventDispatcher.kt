package com.fenix.mobilemcp.services.channel

import com.fenix.mobilemcp.data.model.ChannelConnectionStatus
import com.fenix.mobilemcp.data.model.ChannelEvent
import kotlinx.coroutines.flow.StateFlow

interface EventDispatcher {
    val connectionStatus: StateFlow<ChannelConnectionStatus>

    suspend fun dispatch(event: ChannelEvent): Result<Unit>

    suspend fun healthCheck(): Result<Unit>

    fun start(
        endpointUrl: String,
        authToken: String,
    )

    fun stop()
}
