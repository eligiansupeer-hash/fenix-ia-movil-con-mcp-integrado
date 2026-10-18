package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModesBar(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val modes = listOf("⚡ Modo General", "📋 Modo Planificación", "🔨 Modo Build")
        modes.forEach { mode ->
            FilterChip(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode) }
            )
        }
    }
}
