package com.fenix.mobilemcp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FDocsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDocumentFile by remember { mutableStateOf<DocumentFile?>(null) }
    var folderFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var isFolderMode by remember { mutableStateOf(false) }

    var textContent by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    var showAiBottomSheet by remember { mutableStateOf(false) }

    val supportedMimeTypes = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel.sheet.macroEnabled.12",
        "text/plain",
        "text/markdown",
        "*/*"
    )

    fun loadTextContent(uri: Uri) {
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).readText()
                    } ?: ""
                } catch (e: Exception) {
                    "Error leyendo archivo: ${e.message}"
                }
            }
            textContent = text
        }
    }

    fun saveTextContent(uri: Uri, content: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(content)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isEditing = false
        }
    }

    fun selectFile(uri: Uri, isTree: Boolean = false) {
        selectedUri = uri
        if (isTree) {
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            selectedDocumentFile = documentFile
        } else {
            selectedDocumentFile = DocumentFile.fromSingleUri(context, uri)
        }
        val name = selectedDocumentFile?.name ?: ""
        if (name.endsWith(".txt") || name.endsWith(".md") || selectedDocumentFile?.type?.startsWith("text/") == true) {
            loadTextContent(uri)
        } else {
            textContent = ""
        }
        isEditing = false
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isFolderMode = false
            selectFile(it)
        }
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            isFolderMode = true
            val treeUri = DocumentFile.fromTreeUri(context, it)
            if (treeUri != null && treeUri.isDirectory) {
                folderFiles = treeUri.listFiles().filter { file ->
                    file.isFile
                }
            }
        }
    }
    
    var conversionTargetContent by remember { mutableStateOf("") }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                            OutputStreamWriter(outputStream).use { writer ->
                                writer.write(conversionTargetContent)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("F-Docs") },
                actions = {
                    if (selectedDocumentFile != null) {
                        IconButton(onClick = { showAiBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Fénix IA Context",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { openDocumentLauncher.launch(supportedMimeTypes) }) {
                    Text("Abrir Archivo")
                }
                Button(onClick = { openDocumentTreeLauncher.launch(null) }) {
                    Text("Abrir Carpeta")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isFolderMode) {
                Text("Documentos en la carpeta:", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folderFiles) { file ->
                        ListItem(
                            headlineContent = { Text(file.name ?: "Desconocido") },
                            supportingContent = { Text("Tamaño: ${file.length()} bytes") },
                            modifier = Modifier.clickable {
                                selectFile(file.uri, true)
                                isFolderMode = false
                            }
                        )
                    }
                }
            } else if (selectedDocumentFile != null) {
                val file = selectedDocumentFile!!
                val name = file.name ?: ""
                val isTextFile = name.endsWith(".txt") || name.endsWith(".md") || file.type?.startsWith("text/") == true

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nombre: $name", style = MaterialTheme.typography.titleMedium)
                        Text("Tamaño: ${file.length()} bytes", style = MaterialTheme.typography.bodyMedium)
                        Text("Tipo MIME: ${file.type ?: "Desconocido"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isTextFile) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val wordCount = textContent.split("\\s+".toRegex()).count { it.isNotEmpty() }
                        Text("Caracteres: ${textContent.length} | Palabras: $wordCount", style = MaterialTheme.typography.bodySmall)
                        
                        Row {
                            if (isEditing) {
                                IconButton(onClick = { 
                                    selectedUri?.let { saveTextContent(it, textContent) } 
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Guardar")
                                }
                            } else {
                                IconButton(onClick = { isEditing = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                            }
                        }
                    }

                    if (isEditing) {
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (name.endsWith(".md") || name.endsWith(".txt")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = {
                                conversionTargetContent = textContent
                                createDocumentLauncher.launch(name.substringBeforeLast(".") + ".txt")
                            }) {
                                Text("Convertir a TXT")
                            }
                            Button(onClick = {
                                conversionTargetContent = "<html><body><pre>$textContent</pre></body></html>"
                                createDocumentLauncher.launch(name.substringBeforeLast(".") + ".html")
                            }) {
                                Text("Convertir a HTML")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Vista previa no disponible para este tipo de archivo.",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Selecciona un documento o carpeta para comenzar.",
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showAiBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Asistente Fénix IA",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Contexto: ${selectedDocumentFile?.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                Button(
                    onClick = { showAiBottomSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text("Resumir puntos clave")
                }
                
                Button(
                    onClick = { showAiBottomSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text("Explicar documento")
                }
                
                Button(
                    onClick = { showAiBottomSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Traducir")
                }
            }
        }
    }
}
