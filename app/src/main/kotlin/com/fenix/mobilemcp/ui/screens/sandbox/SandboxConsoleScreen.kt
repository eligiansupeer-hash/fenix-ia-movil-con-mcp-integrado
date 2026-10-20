@file:Suppress("FunctionNaming", "LongMethod")

package com.fenix.mobilemcp.ui.screens.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.sandbox.terminal.TerminalKey
import com.fenix.mobilemcp.ui.viewmodels.SandboxConsoleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxConsoleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SandboxConsoleViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Linux · ${mode.title}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::startShell) { Text("Shell") }
                    TextButton(onClick = viewModel::startOpenCode) { Text("OpenCode") }
                    if (running) {
                        TextButton(onClick = viewModel::stop) { Text("Detener") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color.Black)
                    .imePadding(),
        ) {
            if (!viewModel.installed) {
                Text(
                    text = "El sandbox todavía no está instalado. Volvé a Ajustes → Linux Sandbox.",
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(8.dp),
                )
            }
            TerminalGrid(
                snapshot = snapshot,
                onResize = viewModel::resize,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            KeyToolbar(
                onKey = viewModel::sendKey,
                onCtrlC = { viewModel.send("\u0003") },
                onCtrlD = { viewModel.send("\u0004") },
            )
            InputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    viewModel.send(input)
                    viewModel.sendKey(TerminalKey.Enter)
                    input = ""
                },
            )
        }
    }
}

@Composable
private fun KeyToolbar(
    onKey: (TerminalKey) -> Unit,
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap("Esc") { onKey(TerminalKey.Escape) }
        KeyCap("Tab") { onKey(TerminalKey.Tab) }
        KeyCap("^C") { onCtrlC() }
        KeyCap("^D") { onCtrlD() }
        KeyCap("←") { onKey(TerminalKey.Left) }
        KeyCap("↑") { onKey(TerminalKey.Up) }
        KeyCap("↓") { onKey(TerminalKey.Down) }
        KeyCap("→") { onKey(TerminalKey.Right) }
        KeyCap("⌫") { onKey(TerminalKey.Backspace) }
        KeyCap("⏎") { onKey(TerminalKey.Enter) }
    }
}

@Composable
private fun KeyCap(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFE5E5E5))
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            cursorBrush = SolidColor(Color(0xFF80CBC4)),
            keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
            keyboardActions = KeyboardActions(onSend = { onSend() }, onDone = { onSend() }),
            modifier =
                Modifier
                    .weight(1f)
                    .background(Color(0xFF1B1B1B), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "Escribí y tocá ⏎",
                        color = Color(0xFF6E6E6E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
                inner()
            },
        )
        IconButton(onClick = onSend) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Enviar",
                tint = Color(0xFF80CBC4),
            )
        }
    }
}
