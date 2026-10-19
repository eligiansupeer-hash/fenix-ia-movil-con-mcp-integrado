package com.fenix.mobilemcp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Centro de Ayuda y Documentación") }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "Manual de Usuario",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HelpSection(
                title = "Servidor Ktor y Red",
                content = """
                    Fénix Mobile MCP integra un servidor Ktor local. Puedes configurar puertos, autenticación mediante Bearer token y exponer tu asistente usando túneles seguros (Cloudflare, Ngrok). 
                    Cuenta con un 'kill switch' para apagar la red inmediatamente en caso de emergencia.
                    
                    Más información en: 
                    - https://ktor.io 
                    - https://developers.cloudflare.com
                """.trimIndent()
            )

            HelpSection(
                title = "Bóveda de Llaves IA y Conectores",
                content = """
                    Almacena tus credenciales de forma encriptada y segura. Compatible con proveedores como OpenAI, Claude, Gemini y DeepSeek.
                    Conecta fácilmente con servicios externos mediante conectores nativos para Supabase, GitHub, Telegram, WhatsApp, Google Drive y Gmail.
                """.trimIndent()
            )

            HelpSection(
                title = "Permisos de Accesibilidad, Micrófono y Almacenamiento SAF",
                content = """
                    La aplicación requiere permisos de accesibilidad para interactuar con la pantalla, micrófono para comandos de voz, y Storage Access Framework (SAF) para leer y escribir archivos de forma segura.
                    Tu privacidad está protegida y estos permisos se utilizan únicamente para la automatización local.
                    
                    Más detalles en: https://developer.android.com
                """.trimIndent()
            )

            HelpSection(
                title = "Proyectos y Memoria",
                content = """
                    Todos tus proyectos se almacenan en carpetas elegidas por el usuario bajo su control absoluto. La memoria del asistente y configuraciones se guardan en una base de datos SQLite local (Zero-Knowledge).
                """.trimIndent()
            )

            HelpSection(
                title = "F-Code IDE y Runtime Políglota",
                content = """
                    Entorno de desarrollo integrado que soporta ejecución de Python, C, JavaScript y Rust. Permite configurar la afinidad de núcleos de CPU para optimizar el rendimiento en tu dispositivo móvil.
                """.trimIndent()
            )

            HelpSection(
                title = "F-Docs",
                content = """
                    Módulo de gestión documental con soporte nativo para PDF, DOCX, XLSX, TXT y MD. Todas las conversiones e indexación de archivos ocurren localmente en el dispositivo.
                """.trimIndent()
            )

            HelpSection(
                title = "F-Market",
                content = """
                    Mercado de extensiones comunitarias y scripts de automatización. Soporte para Instant Apps ejecutadas en un entorno de pruebas (sandbox) seguro.
                """.trimIndent()
            )

            HelpSection(
                title = "Información del Sistema",
                content = """
                    Muestra en tiempo real el modelo del dispositivo, arquitectura de procesador y memoria detectada mediante el HardwareCapabilitiesDetector, permitiendo optimizar el rendimiento de la IA local.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Legales y Licencias",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("legales_anchor")
            )

            HelpSection(
                title = "Términos, Condiciones y Exención de Responsabilidad",
                content = """
                    Esta aplicación opera bajo el principio de soberanía del usuario (Zero-Knowledge, Costo 0 por uso de la plataforma base).
                    
                    En conformidad con el Código Civil y Comercial de la Nación de la República Argentina y la Ley 25.326 de Protección de Datos Personales, el usuario asume la total responsabilidad por el uso de la aplicación, el procesamiento de datos sensibles, y las interacciones con APIs de terceros.
                    
                    El desarrollador se exime expresamente de toda responsabilidad por pérdida de datos, bloqueos de cuentas en servicios de terceros (ej. baneos de WhatsApp, GitHub, etc.) o consecuencias directas o indirectas derivadas del uso de herramientas de automatización, accesibilidad o ejecución de código.
                """.trimIndent()
            )

            HelpSection(
                title = "Agradecimientos y Licencias de Código Abierto",
                content = """
                    Esta aplicación es posible gracias a las siguientes tecnologías y bibliotecas de código abierto:
                    
                    • Jetpack Compose (Apache 2.0)
                    • Kotlin (Apache 2.0)
                    • Ktor (Apache 2.0)
                    • OkHttp (Apache 2.0)
                    • DocumentFile (Apache 2.0)
                    
                    Agradecemos a las comunidades de desarrolladores por su invaluable contribución al ecosistema Open Source.
                """.trimIndent()
            )
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    content: String
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                )
            }
            if (isExpanded) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
