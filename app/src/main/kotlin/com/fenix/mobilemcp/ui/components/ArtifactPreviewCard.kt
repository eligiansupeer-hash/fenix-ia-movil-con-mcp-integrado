package com.fenix.mobilemcp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import android.net.Uri

@Composable
fun ArtifactPreviewCard(
    language: String,
    content: String,
    projectUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val title = when (language.lowercase()) {
        "python", "py" -> "script.py"
        "kotlin", "kt" -> "code.kt"
        "javascript", "js" -> "script.js"
        "html" -> "index.html"
        "json" -> "data.json"
        "markdown", "md" -> "documento.md"
        else -> "archivo.$language"
    }

    val previewContent = if (content.lines().size > 5) {
        content.lines().take(5).joinToString("\n") + "\n..."
    } else {
        content
    }

    val sizeInBytes = content.toByteArray().size
    val sizeText = if (sizeInBytes > 1024) "${sizeInBytes / 1024} KB" else "$sizeInBytes bytes"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$language • $sizeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row {
                    // Abrir / Ver
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Abriendo en editor/visor...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Ver en Vivo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Copiar
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(title, content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Contenido copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar Contenido",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Guardar
                    IconButton(
                        onClick = {
                            saveContentToProject(context, title, content, projectUri)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Guardar en Proyecto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Preview body
            Text(
                text = previewContent,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            )
        }
    }
}

private fun saveContentToProject(context: Context, filename: String, content: String, projectUri: Uri?) {
    try {
        if (projectUri != null) {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, projectUri)
            if (documentFile != null && documentFile.canWrite()) {
                val newFile = documentFile.createFile("text/plain", filename)
                if (newFile != null) {
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        output.write(content.toByteArray())
                    }
                    Toast.makeText(context, "Guardado en el proyecto", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            Toast.makeText(context, "No se pudo crear el archivo en el proyecto", Toast.LENGTH_SHORT).show()
        } else {
            // Fallback to internal storage / Documents
            val documentsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir != null) {
                val file = File(documentsDir, filename)
                FileOutputStream(file).use {
                    it.write(content.toByteArray())
                }
                Toast.makeText(context, "Guardado en Documentos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error al acceder a almacenamiento", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
