@file:Suppress("TooManyFunctions")

package com.fenix.mobilemcp.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.mobilemcp.sandbox.SandboxInstaller
import com.fenix.mobilemcp.sandbox.SandboxManager
import com.fenix.mobilemcp.sandbox.SandboxProcessRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SandboxSettingsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val manager = SandboxManager(context)

        private val _status = MutableStateFlow(manager.status())
        val status: StateFlow<SandboxManager.Status> = _status.asStateFlow()

        private val _progress = MutableStateFlow<SandboxInstaller.Progress?>(null)
        val progress: StateFlow<SandboxInstaller.Progress?> = _progress.asStateFlow()

        private val _busy = MutableStateFlow(false)
        val busy: StateFlow<Boolean> = _busy.asStateFlow()

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        private val _output = MutableStateFlow("")
        val output: StateFlow<String> = _output.asStateFlow()

        private var installJob: Job? = null

        fun refresh() {
            _status.value = manager.status()
        }

        fun install() {
            if (_busy.value) return
            _busy.value = true
            _message.value = null
            _output.value = ""
            installJob =
                viewModelScope.launch {
                    try {
                        manager.install { progress -> _progress.value = progress }
                        _message.value = "Sandbox Linux instalado"
                    } catch (cancelled: CancellationException) {
                        _message.value = "Instalación cancelada"
                        throw cancelled
                    } catch (error: Exception) {
                        _message.value = error.message ?: "Error al instalar"
                    } finally {
                        _progress.value = null
                        _busy.value = false
                        refresh()
                    }
                }
        }

        fun cancelInstall() {
            installJob?.cancel()
            installJob = null
            _busy.value = false
            _progress.value = null
        }

        fun smokeTest() {
            runGuestAction("Sin novedades") { manager.smokeTest() }
        }

        fun installOpenCode() {
            runGuestAction("OpenCode ya estaba instalado") { manager.installOpenCode() }
        }

        fun uninstall() {
            if (_busy.value) return
            manager.uninstall()
            _output.value = ""
            _message.value = "Sandbox eliminado"
            refresh()
        }

        private fun runGuestAction(
            emptyMessage: String,
            action: suspend () -> SandboxProcessRunner.Result,
        ) {
            if (_busy.value) return
            _busy.value = true
            _message.value = null
            viewModelScope.launch {
                try {
                    val result = action()
                    _output.value = buildString {
                        if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
                        if (result.stderr.isNotBlank()) {
                            if (isNotEmpty()) appendLine()
                            append(result.stderr.trimEnd())
                        }
                    }.ifBlank { emptyMessage }
                    _message.value = if (result.ok) "Listo" else "Terminó con código ${result.exitCode}"
                } catch (error: Exception) {
                    _message.value = error.message ?: "Error"
                } finally {
                    _busy.value = false
                    refresh()
                }
            }
        }
    }
