# ORDEN DE TRABAJO TÉCNICA — PARA EJECUCIÓN EN JULES (GITHUB)
**Auditoría y Planificación:** Antigravity (Modo Confianza Cero)
**Ejecutor de Código:** Jules (GitHub Agent)
**Fecha:** 2026-10-18
**Repositorio:** `eligiansupeer-hash/fenix-ia-movil-con-mcp-integrado`

---

## 1. OBJETIVO DEL DOCUMENTO
Este documento recopila la auditoría visual y funcional realizada en vivo sobre el dispositivo físico (Motorola Moto G77, Android 14/15) pantalla por pantalla. Su propósito es definir con precisión técnica y sin ambigüedades el estado actual y los requerimientos de refactorización visual y de backend que Jules implementará en el repositorio.

---

## 2. RELEVAMIENTO DE INTERFACES: PANTALLA POR PANTALLA

### PANTALLA 1: "FÉNIX IA" (F-CHAT PRINCIPAL)

#### A. Componentes Visuales Presentes y Estado Actual Observado (QA Humano)

1. **Banner Superior de Solicitud Externa (Mock / Hardcodeado en Compose):**
   - **Visualización:** Aparece un banner de notificación push flotante en la parte superior:
     - Texto: *"solicitud de conexion externa"*, *"claude desktop solicita vincularse al MCP local via tunel persistente"*.
     - Badges: En la esquina superior derecha figura un badge pequeño: `scope: Read-Only`.
     - Acciones: Dos botones: `Permitir conexion` y `Rechazar`.
   - **Diagnóstico Técnico:** Este banner es actualmente estático/simulado en la capa de UI (`ConsentBanner` o composable similar en `ChatScreen.kt`), no está enlazado dinámicamente a eventos reales del bus de autorización MCP ni se oculta tras ser interactuado de forma persistente.

2. **Cabecera de Marca y Estado de Conexión:**
   - **Visualización:**
     - Título: *"Fénix IA"* junto a un ícono naranja con forma de escudo de seguridad.
     - Subtítulo: En color naranja figura *"fenix free cloud"*.
   - **Diagnóstico Técnico:** Indica el proveedor de LLM actualmente seleccionado por defecto en el backend de inferencia (el bridge gratuito hacia GPT-4o-mini).

3. **Botón de Alerta / Estado de Taller ("Modo Taller"):**
   - **Visualización:** Al lado del nombre del proveedor hay un ícono de advertencia/peligro.
   - **Comportamiento al tocar:** Abre un diálogo/modal de advertencia con:
     - Título en letras grandes: *"Alerta de modo taller"*.
     - Mensaje: *"atencion: existen funciones de taller o depuracion activas en el telefono, para salir a la calle a trabajar de reparto, ejecute el script fenix-mode-calle.sh o desactive depuracion usb"*.
     - Datos técnicos expuestos: `adb enabled`, `port_8822`, etc.
     - Botón `Entendido`: Cierra el diálogo y vuelve a F-Chat.
   - **Diagnóstico Técnico:** Es el componente de Street Immunity / Detección de depuración (`StreetImmunityChecker` / `AccessibilityDetector`). Funciona a nivel informativo pero es solo modal de lectura.

4. **Bóveda Segura de Llaves IA (Ícono de Llave Roja):**
   - **Visualización:** Ícono de llave en color rojo en la barra superior.
   - **Comportamiento al tocar:** Abre el panel deslizable/modal *"Boveda segura de llaves IA"*.
     - Subtítulo: *"Cifrado con AndroidKeyStore (AES-256-GCM). Las claves nunca salen de su telefono"*.
     - Tarjetas de proveedores predeterminadas:
       1. Groq (Llama / Mixtral / Gemma)
       2. OpenRouter
       3. Google Gemini
       4. DeepSeek
       5. Cerebras
       6. SambaNova
       7. Mistral
     - Estado de cada tarjeta: Muestra en texto rosado *"no conectada"* si el campo está vacío.
     - Enlace externo: Cada tarjeta incluye un enlace directo a la consola del proveedor para obtener la API key.
     - Botón inferior: `Cerrar`.
   - **Fallas y Limitaciones Técnicas Detectadas:**
     - **Validación Falsa:** Si el usuario ingresa cualquier texto aleatorio (ej: `knofndfndslfdkl`) y guarda, la UI cambia el estado a *"conectada"* inmediatamente sin realizar un ping/health check real contra la API del proveedor.
     - **Rigidez Estructural:** No existe opción de "Agregar proveedor personalizado" (custom OpenAI-compatible endpoint como Ollama local, vLLM o proxy propio).

5. **Gestor de Sesiones e Historial de Chat:**
   - **Visualización:** Ícono de historial al lado de la bóveda de llaves.
   - **Comportamiento al tocar:** Abre panel con lista de sesiones previas, selector para cargar una sesión anterior, botón para eliminar sesiones y botón `+` para iniciar un nuevo chat.
   - **Diagnóstico Técnico:** Conectado a la base de datos local SQLite / DataStore de sesiones.

6. **Botón Limpiar Chat (Ícono de Bote de Basura):**
   - **Visualización:** Al lado del historial.
   - **Comportamiento:** Cierra la sesión activa actual y limpia la vista de conversación.

7. **Hero / Pantalla de Bienvenida Central (Estado Vacío):**
   - **Visualización:**
     - Ícono grande del escudo naranja de Fénix.
     - Título: *"Fénix IA Soberana"*.
     - Descripción: *"Habilidades a demanda con costo 0, ejecucion local y soberania de datos. Escribe tu instruccion para activar herramientas automaticamente"*.
     - Sección: *"Habilidades del Sistema (activacion a demanda):"*.
     - Tarjeta con tags: `📁 Archivos`, `🧠 Memoria`, `📱 Dispositivo`, `🛡️ Seguridad`, `🌐 Red Zero-Trust`, `⚡ Accesibilidad`.
   - **Diagnóstico Técnico:** Pantalla de placeholder inicial que desaparece una vez que se inicia la interacción.

8. **Área de Entrada de Mensajes (Input Bar):**
   - **Visualización:**
     - Campo de texto `EditText` con placeholder: *"Mensaje para Fenix..."*.
     - Botón de Audio (Micrófono) en color gris por defecto.
     - Botón de Enviar en color gris por defecto, que cambia a color naranja cuando el usuario escribe texto.
   - **Fallas y Limitaciones Técnicas Detectadas:**
     - **Ausencia de Controles de Agente:** No existe selector en pantalla para cambiar de modelo al vuelo, ni selector de modo de agente (ej: Chat rápido vs Deep Research vs Código), ni botón/icono para adjuntar archivos/imágenes.
     - **Falla Crítica del Micrófono (Audio):** Al presionar el botón de audio, se enciende en rojo por una fracción de segundo pero **no graba audio ni realiza transcripción (STT)**, incluso si se mantiene presionado. El intent de voz o el `AudioRecord`/`SpeechRecognizer` interno falla silenciosamente o carece de permisos en tiempo de ejecución.

9. **Comportamiento del Motor de Chat y Ejecución de Herramientas:**
   - **Prueba 1 — Consulta básica de modelo:**
     - Envío: *"hola que modelo eres?"*.
     - UI: Muestra *"fenix procesando"*.
     - Respuesta: *"hola soy fenix ia basado en la arquitectura gpt-4 de openai, adaptado para operar en android con nuestras habilidades a demanda ¿en que puedo ayudarte hoy?"*.
     - Tiempo: Rápido.
   - **Prueba 2 — Consulta de herramientas:**
     - Envío: *"que herramientas tienes?"*.
     - Respuesta: Lista de herramientas.
     - **Falla de Renderizado:** El texto markdown no se procesa correctamente; los marcadores de negrita aparecen literales como asteriscos dobles (`**palabra**`) en lugar de texto en negrita.
   - **Prueba 3 — Ejecución de herramientas locales (File System):**
     - Envío: Se solicitó crear un documento con la lista de herramientas y guardarlo en el móvil.
     - **Falla 1 (Permisos de Archivos EPERM):** La IA llamó a `file_write_universal` (`sdcard/tools:list.txt`) y arrojó:
       `error: file write universal Error: /sdcard/tools:list.txt: open failed: EPERM (operation not permitted)`.
       *Causa técnica:* En Android 11+ (API 30+), el acceso directo por ruta a `/sdcard/` está bloqueado por Scoped Storage salvo que se use `MANAGE_EXTERNAL_STORAGE` o la carpeta propia `/sdcard/Documents/fenix/` o SAF.
     - **Falla 2 (Exposición de Errores Crudos HTTP 402 al Usuario):** La IA devolvió una segunda tarjeta con texto crudo de API:
       `error: error http 402: {status: 402 success false, error...} code free limit message... retry after 12s`.
       *Causa técnica:* El backend proxy de Fénix Free Cloud agotó la cuota por segundo/minuto y el frontend expuso el JSON crudo incomprensible directamente en la burbuja de chat en lugar de manejar el rate-limit de forma amigable (backoff automático o aviso claro).
     - **Prueba 4 — Reintento:**
       - Envío: *"reintentar"*.
       - Se ejecutó `directory_create` (`sdcard/fenixtools`).
       - En la tarjeta desplegable se expuso al usuario final el texto de seguridad interno del sistema ("CAUTION: The data below comes from an untrusted external source... prompt injection...").
       - Volvió a repetirse el error crudo: `HTTP 402 retry after 17s`.

10. **Barra de Navegación Inferior (Bottom Navigation Bar):**
    - **Visualización:** 5 pestañas:
      1. `Fénix IA` (activa)
      2. `F-Studio`
      3. `F-Market`
      4. `Servidor`
      5. `Ajustes`
    - No hay otros accesos disponibles en esta pantalla.

---

#### B. Especificación Técnica de Modificaciones para Jules (Pantalla 1: Fénix IA)

1. **Reemplazo de Identidad Visual y Marca (`P1-01`):**
   - **Logo en Cabecera:** Sustituir el vector circular naranja por un recurso gráfico realista de alta calidad del Fénix con animación de resplandor / partículas de fuego y luz en Jetpack Compose.
   - **Tipografía y Título:** Mantener *"Fénix IA"*.
   - **Slogan:** Reemplazar el texto *"fenix free cloud"* por el slogan:
     `"El poder MCP en tus manos"`.

2. **Eliminación del Ícono de Alerta y Creación del Panel de Red y Servidor (`P1-02`):**
   - **Eliminar:** Remover por completo el ícono de peligro/advertencia de taller de la barra superior.
   - **Nuevo Componente:** Añadir un ícono de **Red de Internet / Servidor**:
     - *Estado Inactivo:* Ícono en color **Rojo** (servidor Ktor/Netty apagado).
     - *Estado Activo:* Ícono en color **Verde** (servidor Ktor/Netty encendido).
   - **Panel Desplegable al Tocar el Ícono de Red:**
     - **Interruptores Rápidos:** Controles para `Encender Servidor` y `Apagar Servidor`.
     - **URL HTTPS Fija y Accesible:** Campo destacado que muestre la URL HTTPS fija del servidor con botón directo `Copiar URL` para pegarla en cualquier app de IA externa.
     - **Sección "Opciones Avanzadas de Conexión" (Consolidación Total de Servidor y Ajustes de Red):**
       - Token Bearer de identificación de seguridad para autorizar conexiones (con botón generar/copiar).
       - Puerto y Dirección de escucha (`127.0.0.1` vs `0.0.0.0`).
       - Apodo del equipo (`device_slug`).
       - Mostrar avisos de control.
       - Iniciar al prender el teléfono (Boot completed).
       - Ocultar de recientes.
       - Configuración de túneles (Cloudflare / Ngrok) y URL pública.
       - **Registro de MCPs Externos:** Permitir registrar servidores MCP externos mediante URL y cabeceras HTTPS.
       - **Pestillo de Emergencia (KillSwitch):** Corte inmediato de conexiones activas.
       - **Consola de Logs en Vivo:** Registro de eventos del servidor filtrable por tipo.
     - **Regla Obligatoria de Ayuda Contextual en Cada Configuración:**
       - En cada opción donde el usuario deba activar, desactivar o configurar un parámetro, debe incluirse una **breve explicación clara** junto con un botón `[Más información]` que redirija directamente al apartado correspondiente dentro de la sección **Ayuda**.
       - En todos los **permisos de accesibilidad**, deben mostrarse **dos botones**: `[Más información]` (redirige a la explicación técnica en Ayuda) y `[Legales]` (redirige directamente a la cláusula de exención de responsabilidad en Ayuda).

3. **Reemplazo del Botón de Basura por Sistema de Login Propio y Perfil Único (`P1-03`):**
   - **Eliminar:** Quitar el ícono de bote de basura de la barra superior.
   - **Nuevo Botón (Esquina Superior Derecha):** Ícono de Perfil / Avatar de Usuario.
   - **Sistema de Autenticación Propio (Independiente de Verificaciones Pagas de Google):**
     - Registro e inicio de sesión mediante **Nombre de usuario, Correo electrónico y Contraseña** (conectado a Supabase Auth solo para gestión de cuentas de usuario y suscripciones futuras, más opción de Google OAuth estándar sin scopes restringidos).
     - Los permisos de conectores personales (Drive, Gmail) se configuran de forma independiente dentro de la Bóveda de Llaves cuando el usuario decida usarlos, asegurando que al servidor de registro solo lleguen los datos de cuenta encriptados.
   - **Aceptación Legal Obligatoria en Registro/Login:**
     - Casilla obligatoria antes de crear cuenta o iniciar sesión:
       `[X] Acepto los Términos y Condiciones y la Exención de Responsabilidad Legal` (con enlace directo al apartado Legales).
   - **Único Lugar para Cerrar Sesión y Ajustes de Cuenta:**
     - El botón `[Cerrar Sesión]` debe existir en **un único lugar en toda la aplicación**: dentro de este modal del Perfil de Usuario.
     - En este mismo modal de Perfil se ubican:
       - Selector de Apariencia (Tema Oscuro / Claro / Sistema) y Vibración háptica.
       - Copia de Seguridad y Restauración (Backup & Restore de configuración y sesiones locales).

4. **Refactorización de la Bóveda Segura de Llaves IA (`P1-04`):**
   - **Validación Real de API Keys (Micro-Llamada):** Al guardar una clave, realizar una micro-llamada de prueba real contra el proveedor. No marcar como *"Conectada"* si la clave es falsa o falla.
   - **Mensajes de Error Claros con Solución Directa:** Si la conexión falla, explicar en la misma tarjeta con lenguaje claro para no programadores cuál es la falla y dar el enlace de solución:
     *Ejemplo:* *"Se produjo un error 429 en su clave de su proveedor (límite de cuota agotado). Para cambiar su clave o conseguir una suscripción visite: [Link directo al proveedor]"*.
   - **Proveedores Personalizados:** Opción para agregar proveedores no registrados por defecto (Nombre, Base URL compatible con OpenAI y API Key).
   - **Gestor de Conectores Externos:** Botones y tarjetas para guardar las credenciales de los conectores por defecto: **Supabase, Cloudflare, GitHub, Bot de Telegram, WhatsApp, Google Drive y Gmail**.

5. **Evolución del Panel de Sesiones y Memoria hacia "Proyectos" (`P1-05`):**
   - **Nuevos Botones Claros:** En lugar de solo un ícono `+`, mostrar dos botones explícitos: `[Nueva Sesión]` y `[Nuevo Proyecto]`.
   - **Sesión Global vs Proyecto:**
     - *Nueva Sesión:* Inicia una conversación en un sandbox por defecto.
     - *Nuevo Proyecto:* Permite crear una carpeta dentro del teléfono en la ubicación que el usuario elija mediante el selector de archivos del sistema (SAF). Todos los chats, artefactos, documentos cargados, registro de sesiones, historial de contexto y base SQLite de ese proyecto viven dentro de esa carpeta.
   - **Confirmación de Permisos Fuera del Proyecto:** Si el usuario pide que la IA trabaje en una ruta fuera de la carpeta del proyecto, la IA debe avisar en el chat y mostrar una tarjeta interactiva con los botones `[Confirmar Permiso]` y `[Denegar Permiso]`.
   - **Limpieza de Memoria:** Incluir en este panel la opción de limpieza selectiva de sesiones e historial.

6. **Rediseño Completo de la Caja de Chat (Input Bar) (`P1-06`):**
   - **Botón `+` con Menú de Opciones:**
     1. **Subir Archivo:** Permite adjuntar todo tipo de archivos (`txt`, `md`, `doc`, `docx`, `pdf`, `xlsm`, `mp3`, `mp4`, `jpg`, `png`, archivos de código, etc.).
     2. **Elegir Modelo:** Lista todos los modelos conectados (APIs de la bóveda, LLMs locales, CLIs de IA desde Termux) e incluye el acceso a la configuración de **Inferencia Local** (ruta de modelos y límites de CPU/RAM).
     3. **Elegir Tipo de Agente:** Agentes por defecto (*Modo Build*, *Modo Plan*, *Modo General*) más los **agentes personalizados** que el usuario cree o descargue desde `F-Market` (deben aparecer automáticamente en la lista sin configuraciones técnicas manuales).
     4. **Skills:** Lista de skills ordenadas por categoría para conectar o desconectar en el chat. En sesiones globales el estado se comparte entre sesiones globales; dentro de un proyecto, las skills conservan los permisos dados en ese proyecto.
   - **Botón de Grabar Audio (Voz a Texto Continuo):**
     - Al presionarlo una vez, queda encendido grabando y transcribiendo automáticamente la voz a texto dentro de la caja de chat en vivo.
     - Al volver a presionarlo, se detiene la grabación **sin enviar el mensaje automáticamente**, para que el usuario pueda revisarlo antes de tocar enviar.
   - **Botón de Enviar Integrado:** Ubicar el botón de enviar **dentro** de la caja de texto.

7. **Pantalla de Inicio en Sesión Limpia (Hero Animado) (`P1-07`):**
   - **Logo Fénix en Llamas:** Mismo tamaño que el actual, con efecto de fuego y partículas de llama que suben por la pantalla.
   - **Título Impactante:** Debajo del logo, mostrar *"Fénix IA MCP"* con el mismo tamaño de letra actual, animando las letras desde el fondo de la pantalla con un efecto explosivo al chocar contra la pantalla.
   - **Efecto de Escritura (Typewriter):** Debajo del título, el texto se va tecleando solo:
     > *"¡Bienvenido! Aquí estoy para ayudarte a convertir tus ideas en realidad, tienes múltiples herramientas así como la capacidad de conectarte a otras aplicaciones de inteligencia artificial, hacer trabajos directamente en tu celular, controlar la pantalla, abrir aplicaciones, gestionar tus cuentas, etc. Puedes personalizarme y hacer un sinfín de cosas. ¿Por dónde empezamos?"*

8. **Corrección de Errores del Backend y Renderizado en el Chat (`P1-08`):**
   - **Renderizado Markdown Real:** Eliminar los `**` crudos; renderizar negritas, bloques de código, listas y tablas correctamente.
   - **Errores Comprensibles en el Chat:** Prohibido mostrar JSON crudo como `HTTP 402 {status: 402...}`. Mostrar mensajes en lenguaje humano claro explicando el motivo y dando el enlace o botón de solución.
   - **Corrección de `EPERM` en Escritura de Archivos:** Las herramientas de archivos (`file_write_universal`, `directory_create`) deben operar sobre rutas con permiso válido en Android (carpeta del proyecto o almacenamiento autorizado por SAF/App-specific storage).
   - **Ocultar Mensajes Internos de Seguridad:** No mostrar al usuario el texto interno *"CAUTION: The data below comes from an untrusted external source..."*.

9. **Sistema Dinámico de Autorización de Clientes MCP Externos (`P1-09`):**
   - **Sin Apps Simuladas:** Eliminar el banner falso de *"claude desktop solicita vincularse"*.
   - **Tarjeta de Solicitud Real en el Chat:** Toda conexión externa real (por HTTP, HTTPS, puerto, túnel o USB) que use el Bearer token genera una tarjeta en el chat para aceptar o rechazar.
   - **Concesión Inmediata de Conexión:** Apenas el usuario toca `[Permitir Conexión]`, el servidor concede el acceso al instante sin esperar a que termine de configurar los permisos granulares.
   - **Lista de Permisos Granulares y Guardado:** Acto seguido, la misma tarjeta muestra los switches de permisos (accesibilidad —con sus botones `Más información` y `Legales`—, micrófono, pantalla, almacenamiento, etc.) y el botón `[Guardar]` que cierra la tarjeta.
   - **Reconfiguración en el Panel de Red:** Todas las apps conectadas quedan registradas en el Panel de Red; al tocar la tarjeta de cualquiera de ellas, el usuario puede cambiar sus permisos en cualquier momento.

10. **Tarjetas de Previsualización de Artefactos (`P1-10`):**
    - Todo artefacto creado en un chat debe generar automáticamente una **Tarjeta de Previsualización** interactiva con botones para ver en vivo, copiar, guardar o abrir en `F-Code` / `F-Docs`.

---

### PANTALLA 2: "F-CODE" (ANTES F-STUDIO — IDE DE CÓDIGO)

#### A. Estado Actual y Regla de Preservación
- El IDE actual funciona bien (muestra la RAM, archivos demo y consola). **No se debe romper ni eliminar ninguna de sus funciones actuales.**

#### B. Especificación Técnica de Modificaciones para Jules (Pantalla 2: F-Code)

1. **Cambio de Nombre (`P2-01`):**
   - Renombrar la sección y su cabecera de *"F-Studio"* a **`"F-Code"`**.

2. **Juego Snake Táctil en `demo_agente.py` (`P2-02`):**
   - Colocar dentro de `demo_agente.py` el código en Python del clásico juego **Snake**, completamente jugable de manera táctil en la pantalla del móvil cuando el usuario presione `[Ejecutar]`.

3. **Gestor de Archivos Nativo sin Rutas Hardcodeadas (`P2-03`):**
   - Al presionar el ícono de carpeta para agregar, abrir el gestor de archivos nativo del móvil (SAF) para elegir desde cualquier ruta del dispositivo.
   - Permitir tanto **agregar carpetas como archivos**, y al crear nuevo permitir elegir entre **`[Nueva Carpeta]`** y **`[Nuevo Archivo]`**.

4. **Botón de Asistente IA Contextual en Cabecera (`P2-04`):**
   - Agregar arriba un ícono claramente distinguible de Fénix IA para abrir un panel de consulta rápida que tome automáticamente como contexto los archivos abiertos en pantalla.

---

### PANTALLA 3: "F-DOCS" (NUEVA SECCIÓN INDEPENDIENTE DE DOCUMENTOS)

#### Especificación Técnica para Jules (Pantalla 3: F-Docs)

1. **Sección Propia en la Barra Inferior (`P3-01`):**
   - Crear **`"F-Docs"`** como sección independiente en el menú principal inferior para no sobrecargar ni romper `F-Code`.

2. **Apertura, Edición y Conversión de Documentos (`P3-02`):**
   - Permitir abrir desde el gestor de archivos nativo (sin rutas hardcodeadas, soportando archivos y carpetas) todo tipo de documentos: `PDF`, `DOC`, `DOCX`, `XLS`, `XLSX`, `XLSM`, `TXT`, `MD`.
   - Incluir herramientas de edición de documentos y conversión local de formato (ej: convertir entre DOCX, Markdown, TXT y PDF).

3. **Botón de Asistente IA Contextual en Cabecera (`P3-03`):**
   - Incluir en la parte superior el ícono de Fénix IA para revisar, resumir y hacer preguntas teniendo como contexto directo el documento abierto en pantalla.

---

### PANTALLA 4: "F-MARKET" (MICRO-APP STORE DE EXTENSIONES E INSTANT APPS)

#### Especificación Técnica de Rediseño para Jules (Pantalla 4: F-Market)

1. **Diseño de Tienda de Aplicaciones Completa (`P4-01`):**
   - Eliminar el diseño actual con título cortado y la tarjeta intrusiva de *"Hardware & Guardian Axiom"*.
   - Construir una verdadera **Micro-App de Tienda** dentro de Fénix Mobile con buscador, categorías en chips (`Todas`, `Productividad`, `Seguridad`, `Documentos`, `Automatización`, `Agentes`, `Instant Apps`), destacados y grillas de catálogo.

2. **Ficha Completa de Extensión (Estándar VS Code Marketplace / Raycast) (`P4-02`):**
   - Cada extensión o Instant App debe mostrar todos los metadatos de creador:
     - Icono, nombre visible (`displayName`), identificador único (`publisher.name`), **nombre del creador**, **fecha de creación y actualización**, versión, categoría, palabras clave, capturas de pantalla, **descripción total y completa**, permisos requeridos, historial de cambios (changelog), licencia y repositorio.
   - Al instalar un agente o extensión desde `F-Market`, debe quedar disponible automáticamente en el selector de `Fénix IA` sin configuraciones técnicas manuales.

3. **Independencia del Catálogo frente a Repositorio Privado y Preparación Comercial (`P4-03`):**
   - **Requisito Crítico de Arquitectura:** El repositorio del código fuente principal de la aplicación pasará a ser **privado** en el futuro cercano. Por lo tanto, `F-Market` debe leer y publicar el catálogo comunitario desde un índice/repositorio público separado o endpoint configurable desacoplado del repositorio privado del código fuente.
   - **Preparación para Monetización:** Estructurar las fichas de la tienda como un escaparate comercial listo para soportar tanto ítems gratuitos de la comunidad como extensiones e Instant Apps de pago/suscripción futuras.

---

### PANTALLA 5: "AYUDA" (MANUAL DEL USUARIO, LEGALES Y AGRADECIMIENTOS)

#### Especificación Técnica para Jules (Pantalla 5: Ayuda)

1. **Eliminación de las Pestañas "Servidor" y "Ajustes" y Barra Inferior Definitiva (`P5-01`):**
   - Las antiguas secciones de *"Servidor"* y *"Ajustes"* desaparecen de la barra inferior (sus funciones quedan distribuidas en el Panel de Red, Bóveda de Llaves, Perfil de Usuario, Selector de Modelos y Ayuda).
   - La barra de navegación inferior tiene exactamente estas **5 secciones**:
     1. **`Fénix IA`**
     2. **`F-Code`**
     3. **`F-Docs`**
     4. **`F-Market`**
     5. **`Ayuda`**

2. **Manual del Usuario Amplísimo por Secciones (`P5-02`):**
   - Documentación exhaustiva, redactada sin escatimar explicaciones en lenguaje claro para cualquier usuario, cubriendo qué es la aplicación y qué hace y para qué sirve cada una de sus configuraciones.
   - **Ninguna configuración de la app puede quedar sin su apartado en Ayuda.**
   - Todos los botones `[Más información]` distribuidos en los interruptores y ajustes de la app redirigen exactamente a su apartado correspondiente aquí.
   - Cada apartado debe incluir **URLs clicables a los sitios oficiales de documentación técnica** (Protocolo MCP, Android Developers, Cloudflare, Ktor, etc.).
   - Incluir al final del manual el bloque de **Información del Sistema** (versión, arquitectura, estado de hardware).

3. **Sección "Legales" (Términos, Condiciones y Exención de Responsabilidad) (`P5-03`):**
   - Ubicada debajo del manual de usuario, con su propia ancla directa para que los botones `[Legales]` de los permisos de accesibilidad y de la pantalla de login abran este texto de inmediato.
   - Redactada en lenguaje jurídico argentino (Código Civil y Comercial de la Nación, Ley 25.326) e internacional, estableciendo el uso bajo exclusivo riesgo del usuario, la soberanía local de los datos y la exención de responsabilidad para el creador de la aplicación ante daños, pérdidas de información, bloqueos de cuentas de terceros o mal uso de herramientas de accesibilidad y automatización.

4. **Sección "Agradecimientos" (Licencias Open Source) (`P5-04`):**
   - Ubicada al final de la pantalla de Ayuda, listando todos los agradecimientos y textos de licencias exigidos por las bibliotecas de código abierto utilizadas en el proyecto.







