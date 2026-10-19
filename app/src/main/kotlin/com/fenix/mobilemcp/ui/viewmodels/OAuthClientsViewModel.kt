package com.fenix.mobilemcp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.mobilemcp.data.repository.OAuthClientRepository
import com.fenix.mobilemcp.di.IoDispatcher
import com.fenix.mobilemcp.mcp.oauth.OAuthClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Connected-clients screen: observes the registry and revokes clients. */
@HiltViewModel
class OAuthClientsViewModel
    @Inject
    constructor(
        private val repository: OAuthClientRepository,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val clients: StateFlow<List<OAuthClient>> =
            repository
                .observeClients()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_TIMEOUT_MS), emptyList())

        fun revoke(clientId: String) {
            viewModelScope.launch(ioDispatcher) { repository.revoke(clientId) }
        }

        fun updateClientPermissions(clientId: String, permissions: Set<String>) {
            viewModelScope.launch(ioDispatcher) { repository.updatePermissions(clientId, permissions) }
        }

        private companion object {
            const val FLOW_TIMEOUT_MS = 5_000L
        }
    }
