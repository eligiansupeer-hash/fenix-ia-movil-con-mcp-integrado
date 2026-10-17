# Plan de Implementación: Suite de Gestión Avanzada de Archivos y Seguridad Perimetral Fénix MCP

**Fecha:** 2026-10-16  
**Objetivo:** Dotar a Fénix Mobile MCP de un gestor de archivos universal y de alto rendimiento que funcione en Android sin necesidad de Root ni Modo Desarrollador activo, con protecciones perimetrales contra ataques de terceros y resguardo estricto de apps bancarias y de delivery.

---

## 1. Principio Fundamental: Funcionamiento 100% Sin Root y Sin Depender de Modo Desarrollador

* **Permiso Oficial de Android:** `android.permission.MANAGE_EXTERNAL_STORAGE` (`All Files Access` / Acceso a todos los archivos).
* **Concesión amigable para el usuario:** Si no está concedido, la aplicación dispara la pantalla oficial de configuración del sistema (`Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`) con un solo toque desde la app.
* **Persistencia total:** Una vez otorgado, el permiso permanece activo indefinidamente en el sistema, funcionando aun con las Opciones de Desarrollador y la Depuración USB apagadas.
* **Integridad de Play Protect:** No vulnera Play Integrity ni SafetyNet. Aplicaciones como PeYa Rider, Soy Rappi, Uber Driver y banca móvil continúan operando con normalidad.

---

## 2. Arquitectura de Seguridad Perimetral (`FenixFileSecurityFilter`)

Antes de realizar cualquier lectura, escritura, búsqueda o borrado, la ruta debe pasar obligatoriamente por el filtro de seguridad perimetral:

1. **Rutas Prohibidas (Blacklist Estricta):**
   * Carpetas privadas de aplicaciones bancarias y financieras (`*mercadopago*`, `*banconacion*`, `*santander*`, `*brubank*`, `*naranja*`, `*tarjetanaranja*`, etc.).
   * Directorios de autenticadores y llaveros (`*authenticator*`, `*authy*`).
   * Almacén de certificados privados, tokens JWT y keystores de Fénix (`*.jks`, `*.keystore`, `*.id_rsa`).
   * Directorios de sistema protegidos fuera del almacenamiento compartido de usuario.
2. **Protección Anti-Path Traversal:**
   * Toda ruta relativa o absoluta se normaliza mediante `File.canonicalPath` para evitar secuencias `../../` que intenten escapar del almacenamiento permitido.
3. **Protección contra Borrado Destructivo:**
   * Prohibido eliminar la raíz de almacenamiento (`/sdcard` o `/storage/emulated/0`) o carpetas base del sistema.
4. **Trazabilidad en Caja Negra:**
   * Toda operación de archivo se registra automáticamente en `FenixBlackBoxRecorder`.

---

## 3. Catálogo de Herramientas MCP para Gestión de Archivos

### Grupo A: Diagnóstico de Almacenamiento y Clasificación
* **`android_file_classify`:**
  * Escanea y categoriza los archivos del teléfono por tipo: *Documentos (PDF, DOCX, TXT, MD), Código (PY, KT, JS, JSON, HTML), Imágenes (JPG, PNG, SVG), Audio (MP3, WAV), Video (MP4, MKV), Archivos Comprimidos (ZIP, TAR)*.
  * Detecta los archivos más pesados (> 50 MB / > 100 MB) que saturan la memoria del teléfono para facilitar la limpieza guiada por IA.
* **`android_file_search`:**
  * Búsqueda recursiva por nombre, extensión, rango de tamaño y fecha de modificación.

### Grupo B: Búsqueda de Contenido y Edición Fina
* **`android_file_grep`:**
  * Búsqueda de cadenas o expresiones regulares dentro del contenido de archivos de texto o código en un directorio, sin necesidad de saber el nombre exacto del archivo.
* **`android_file_insert`:**
  * Inserción de texto en una posición precisa (por número de línea, o después de un patrón coincidente) y reemplazo de bloques sin truncar el archivo.
* **`android_file_read_universal`:**
  * Lectura con paginación por líneas y soporte de límites de tamaño para no desbordar el contexto de la IA.
* **`android_file_write_universal`:**
  * Creación y sobrescritura controlada de archivos en cualquier carpeta accesible.

### Grupo C: Organización, Carpetas y Compartición
* **`android_directory_create`:**
  * Creación de nuevas carpetas o estructuras de carpetas (`mkdirs`).
* **`android_file_move`:**
  * Mover o renombrar archivos para organizar descargas y documentos dispersos.
* **`android_file_delete_safe`:**
  * Eliminación segura de archivos individuales verificando la lista de exclusiones.
* **`android_file_share`:**
  * Genera el intent `ACTION_SEND` de Android con FileProvider para enviar archivos directamente a WhatsApp, Telegram o correo.
* **`android_local_ocr`:**
  * Extracción de texto local en imágenes y documentos escaneados mediante el motor OCR offline del dispositivo.

---

## 4. Checklist de Modificación de Archivos

- [x] Declarar `MANAGE_EXTERNAL_STORAGE` en `AndroidManifest.xml`.
- [ ] Implementar `hasAllFilesAccess` y `openAllFilesAccessSettings` en `PermissionUtils.kt`.
- [ ] Agregar fila de "Acceso a todos los archivos" en `PermissionsSettingsScreen.kt`.
- [ ] Crear `FenixFileSecurityFilter.kt` (seguridad perimetral y lista negra bancaria).
- [ ] Crear `FenixFileManager.kt` (motor universal de búsqueda, clasificación, grep, inserción y operaciones de archivo).
- [ ] Crear y registrar las herramientas en `FenixTools.kt`.

