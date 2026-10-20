package com.fenix.mobilemcp.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.mobilemcp.sandbox.SandboxManager
import com.fenix.mobilemcp.sandbox.pty.PtySession
import com.fenix.mobilemcp.sandbox.terminal.TerminalKey
import com.fenix.mobilemcp.sandbox.terminal.TerminalKeyEncoder
import com.fenix.mobilemcp.sandbox.terminal.TerminalModifiers
import com.fenix.mobilemcp.sandbox.terminal.TerminalSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SandboxConsoleViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ViewModel() {
        enum class Mode(
            val title: String,
        ) {
            SHELL("Shell"),
            OPENCODE("OpenCode"),
        }

        private val manager = SandboxManager(context)

        val installed: Boolean = manager.status().installed

        private val _mode = MutableStateFlow(Mode.SHELL)
        val mode: StateFlow<Mode> = _mode.asStateFlow()

        private val _session = MutableStateFlow<PtySession?>(null)
        val session: StateFlow<PtySession?> = _session.asStateFlow()

        private val _ctrlLatch = MutableStateFlow(false)
        val ctrlLatch: StateFlow<Boolean> = _ctrlLatch.asStateFlow()

        private var columns = 80
        private var rows = 24

        @OptIn(ExperimentalCoroutinesApi::class)
        val snapshot: StateFlow<TerminalSnapshot> =
            _session
                .flatMapLatest { active -> active?.snapshot ?: flowOf(TerminalSnapshot.blank(columns, rows)) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    TerminalSnapshot.blank(columns, rows),
                )

        @OptIn(ExperimentalCoroutinesApi::class)
        val running: StateFlow<Boolean> =
            _session
                .flatMapLatest { active -> active?.state ?: flowOf(PtySession.State.IDLE) }
                .map { it == PtySession.State.RUNNING }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    false,
                )

        init {
            start(Mode.SHELL)
        }

        fun startShell() {
            start(Mode.SHELL)
        }

        fun startOpenCode() {
            start(Mode.OPENCODE)
        }

        fun start(next: Mode) {
            _mode.value = next
            _session.value?.stop()
            val created = PtySession(manager, next.title, columns, rows)
            _session.value = created
            created.start(
                when (next) {
                    Mode.SHELL -> manager.shellCommand()
                    Mode.OPENCODE -> manager.openCodeCommand()
                },
            )
        }

        fun send(text: String) {
            if (text.isEmpty()) return
            val modifiers = if (_ctrlLatch.value) TerminalModifiers(ctrl = true) else TerminalModifiers.None
            _ctrlLatch.value = false
            _session.value?.write(TerminalKeyEncoder.encodeText(text, modifiers))
        }

        fun sendKey(
            key: TerminalKey,
            modifiers: TerminalModifiers = TerminalModifiers.None,
        ) {
            val effective = if (_ctrlLatch.value) modifiers + TerminalModifiers(ctrl = true) else modifiers
            _ctrlLatch.value = false
            val applicationCursorKeys = snapshot.value.applicationCursorKeys
            _session.value?.write(TerminalKeyEncoder.encode(key, effective, applicationCursorKeys))
        }

        fun toggleCtrl() {
            _ctrlLatch.value = !_ctrlLatch.value
        }

        fun resize(
            columns: Int,
            rows: Int,
        ) {
            if (columns < 1 || rows < 1) return
            this.columns = columns
            this.rows = rows
            _session.value?.resize(columns, rows)
        }

        fun stop() {
            _session.value?.stop()
        }

        override fun onCleared() {
            _session.value?.stop()
            _session.value = null
            super.onCleared()
        }
    }
