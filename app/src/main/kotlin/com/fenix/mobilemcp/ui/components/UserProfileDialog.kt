package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    onDismiss: () -> Unit,
) {
    var isAuthenticated by remember { mutableStateOf(false) } // Placeholder for actual auth state

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                if (isAuthenticated) {
                    AuthenticatedProfileView(
                        onLogout = { isAuthenticated = false },
                        onDismiss = onDismiss
                    )
                } else {
                    UnauthenticatedLoginView(
                        onLoginSuccess = { isAuthenticated = true },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedProfileView(
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Avatar",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Usuario Fénix",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "usuario@fenixmcp.com",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Appearance
        var selectedTheme by remember { mutableStateOf("Sistema") }
        Text(
            text = "Apariencia",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Claro", "Oscuro", "Sistema").forEach { theme ->
                FilterChip(
                    selected = selectedTheme == theme,
                    onClick = { selectedTheme = theme },
                    label = { Text(theme) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Haptic Feedback
        var hapticEnabled by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Vibración háptica", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = hapticEnabled, onCheckedChange = { hapticEnabled = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backup & Restore
        Text(
            text = "Copia de Seguridad y Restauración",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { /* Backup logic */ }, modifier = Modifier.weight(1f)) {
                Text("Respaldar")
            }
            OutlinedButton(onClick = { /* Restore logic */ }, modifier = Modifier.weight(1f)) {
                Text("Restaurar")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // The ONLY Logout button
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Cerrar Sesión")
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Cerrar Panel")
        }
    }
}

@Composable
private fun UnauthenticatedLoginView(
    onLoginSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var isLoginTab by remember { mutableStateOf(true) }
    
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(selectedTabIndex = if (isLoginTab) 0 else 1) {
            Tab(
                selected = isLoginTab,
                onClick = { isLoginTab = true },
                text = { Text("Iniciar Sesión") }
            )
            Tab(
                selected = !isLoginTab,
                onClick = { isLoginTab = false },
                text = { Text("Registrarse") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isLoginTab) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nombre de usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it }
            )
            Text(
                text = "Acepto los Términos y Condiciones y la Exención de Responsabilidad Legal",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth(),
            enabled = termsAccepted
        ) {
            Text(if (isLoginTab) "Iniciar Sesión" else "Crear Cuenta")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { /* Google Login */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continuar con Google")
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onDismiss) {
            Text("Cancelar")
        }
    }
}
