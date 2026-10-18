# PLAN MAESTRO DE DIRECTRICES TÉCNICAS DE DESARROLLO — FÉNIX MOBILE MCP
**Documento Oficial de Directrices Técnicas de Implementación y Gobernanza**  
**Versión:** 1.0 — Depurada e Inmutable  
**Fecha de Actualización:** 18 de octubre de 2026  
**Regla de Oro:** Costo = $0 Absoluto | Cero Hardcodeo Académico/Técnico | Soberanía Absoluta del Usuario

---

## CONSTITUCIÓN TÉCNICA Y PRINCIPIOS INVIOLABLES
1. **Principio de Costo $0 en Desarrollo y Núcleo Base:** La aplicación, su compilación, infraestructura base y servidores predeterminados no demandan gasto alguno para su creación ni requieren pagos o suscripciones para ser 100% utilizables y funcionales.
2. **Soberanía del Usuario sobre Integraciones Externas:** La aplicación no restringe al usuario si este desea, bajo su propia voluntad y presupuesto, conectar servicios externos con cuota o API keys comerciales. El usuario gestiona libremente sus gastos; el software solo garantiza que ninguna función base dependa obligatoriamente de un pago.
3. **Protocolo de Modificación Gradual:** Cada fase analizada y consensuada se persiste físicamente en este documento antes de habilitar el análisis de la siguiente fase.

---

## FASE 0: GOBIERNO, BASELINE TÉCNICO Y RED PERSISTENTE

### 0.1. Núcleo Autosuficiente y Libertad de Integraciones
* **Directriz Técnica:**
  - El servidor local, las herramientas MCP y la experiencia base operan sin requerir tarjetas de crédito, saldo previo ni suscripciones.
  - Ningún servicio predeterminado vendrá configurado con peajes obligatorios.
  - El usuario dispone de paneles abiertos para configurar sus propios proveedores (locales gratuitos o APIs comerciales bajo su cuenta privada).

### 0.2. Persistencia de Identidad de Túnel por Dispositivo
* **Problema Resuelto:** Pérdida permanente del conector MCP en clientes con restricción de 1 solo conector personalizado (ej. planes Claude IA Free) ante desinstalaciones o actualizaciones de la APK.
* **Directriz Técnica:**
  - Al instalar la app por primera vez, generar un par de claves y token de identidad de túnel Cloudflare persistente en el almacenamiento privado del dispositivo: `/sdcard/Documents/fenix/tunnel_identity.json`.
  - El instalador/arranque de la app consultará este archivo antes de solicitar un túnel efímero. Si existe, restaura la misma URL fija persistente (`[id-dispositivo].trycloudflare.com/mcp`).
  - La clave no se destruye con la desinstalación normal de la APK.

### 0.3. Consentimiento Informado Pre-Activación
* **Directriz Técnica:**
  - El servidor MCP, el túnel remoto y los conectores inician **estrictamente apagados por defecto**.
  - Al presionar el botón *"Activar Servidor"*, la interfaz despliega de forma obligatoria un modal de consentimiento interactivo previo al binding de red.
  - El modal permite al usuario seleccionar entre:
    * `Modo Local Seguro`: Enlace exclusivo en loopback `127.0.0.1` (solo accesible por apps internas o ADB).
    * `Modo Red / Túnel Remoto`: Explicación explícita de riesgos de exposición, puertos y beneficios antes del encendido.

---

## FASE 1: ARQUITECTURA DE F-CHAT, UX Y EXTENSIBILIDAD DE AGENTES

### 1.1. Los 3 Modos de Agente por Defecto (Accesibilidad Universal)
* **Directriz Técnica:**
  - En la interfaz de F-Chat de producción se descarta la jerga técnica compleja para usuarios no programadores.
  - La instalación base proveerá únicamente tres modos fundamentales:
    1. `⚡ Modo General`: Consultas rápidas, resúmenes, síntesis y redacción.
    2. `📋 Modo Planificación`: Diseño estructurado, metodologías de estudio y descomposición paso a paso.
    3. `🔨 Modo Build`: Generación de código, artefactos, apuntes estructurados y ejecución técnica.
  - Todo agente avanzado o especializado (ej. Criticón AXIOM, Polyglot, Hardware Guard) se desacopla del núcleo y se distribuye como **Extensión de Agente modular instalable desde F-Market**.

### 1.2. Selector Dinámico de Modelos y Calibración en Vivo
* **Directriz Técnica:**
  - El selector de modelos de F-Chat consulta dinámicamente al backend/proveedor las capacidades del modelo (longitud de ventana de contexto, soporte de visión, herramientas y streaming).
  - El control de `Reasoning Effort` (Bajo, Medio, Alto) se habilitará únicamente si los metadatos del modelo confirman soporte nativo de esfuerzo de razonamiento.

### 1.3. Autocompactación de Contexto Dinámica ($0 sin Hardcodeo)
* **Directriz Técnica:**
  - Prohibido hardcodear ventanas de tokens o límites de compactación en constantes fijas del código.
  - El motor de contexto consulta dinámicamente al proveedor la ventana máxima de contexto del modelo seleccionado para autocalibrar el umbral de advertencia y compactación.
  - **Control Soberano:**
    * Interruptor general ON/OFF para habilitar o desactivar el autocompactado.
    * Botón de purga explícito: El usuario puede eliminar y restaurar el historial no compactado en cualquier momento.

### 1.4. Grounding y Trazabilidad de Fuentes
* **Directriz Técnica:**
  - Cada fragmento de información emitido por el modelo debe enlazar citas inspeccionables directas a los archivos locales del usuario (`file:///sdcard/Documents/fenix/...`).
  - Si un fragmento carece de respaldo documental, se marca explícitamente como no verificado.

---

## FASE 2: POLICY ENFORCEMENT POINT (PEP) UNIFICADO Y CONTROL DE ACCESO

### 2.1. Arquitectura del Policy Enforcement Point (PolicyGate)
* **Directriz Técnica:**
  - Centralizar toda la lógica de autorización en un componente unificado `PolicyGate`, sin efectos secundarios.
  - El PEP es el árbitro obligatorio antes de cualquier operación de:
    * Lectura: `getRootNode()`, `parseTree()`, `ScreenCaptureProvider.captureScreen()`, `NotificationListener`.
    * Acción: Nodos (`click`, `longClick`, `setText`, `scroll`), Coordenadas (`tap`, `doubleTap`, `longPress`, `swipe`, `pinch`, `customGesture`), y Globales (`pressBack`, `pressHome`, `pressRecents`).
  - Respuestas tipadas con códigos de denegación auditables:
    * `FINANCIAL_PACKAGE`
    * `SENSITIVE_WINDOW` (campos password/PIN/OTP o flag secure activo)
    * `UNKNOWN_PACKAGE`
    * `STALE_NODE`
    * `IDENTITY_MISMATCH`
    * `AUTH_DISABLED`
    * `EMERGENCY_LATCH`
    * `PERMISSION_REVOKED`
  - Principio *Fail-Closed*: Incertidumbre, error en resolución o ambigüedad = `DENY`.

### 2.2. Eliminación de Stubs y Listas Hardcodeadas
* **Directriz Técnica:**
  - Eliminar el stub booleano constante `fun isAntiBanFirewallEnabled(): Boolean = true` en `ActionExecutorImpl.kt`. Su estado debe reflejar la evaluación real del `PolicyGate`.
  - Eliminar los paquetes fijos de delivery en `StreetImmunityChecker.kt`. La lista de paquetes protegidos pasa a ser una entidad dinámica local.

### 2.3. Gestión Dinámica de Aplicaciones (Ciclo de Vida)
* **Directriz Técnica:**
  - **Inicialización:** Al primer arranque de Fénix, escanear mediante `PackageManager` las apps del sistema y registrarlas en `/sdcard/Documents/fenix/installed_apps_policy.json`.
  - **Detección en Tiempo Real:** Registrar un `BroadcastReceiver` del sistema para los intents `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED` y `ACTION_PACKAGE_REPLACED`.
  - **Cuarentena por Defecto:** Toda nueva aplicación instalada se marca automáticamente como `UNKNOWN` con **Nivel 0 (Bloqueo Preventivo)** hasta que el usuario decida otorgarle acceso.

### 2.4. Matriz de 4 Niveles de Acceso Soberano por Aplicación
* **Directriz Técnica:**
  - El usuario puede configurar individualmente para cada app instalada:
    * 🔴 `Nivel 0: Bloqueo Ciego Absoluto (DENY_ALL)`: Cero lectura, cero capturas, cero gestos.
    * 🟡 `Nivel 1: Solo Lectura / Asistente de Estudio (READ_ONLY)`: Lectura del árbol accesible para apuntes y citas, prohíbe clicks y teclado.
    * 🟠 `Nivel 2: Acción Asistida / Supervisada (HUMAN_IN_THE_LOOP)`: El agente propone la acción en un banner flotante y el usuario aprueba con 1 toque.
    * 🟢 `Nivel 3: Automatización Completa (FULL_AUTOMATION)`: Solo en apps de confianza expresamente autorizadas por el usuario.

### 2.5. Gestión de Entidades Conectadas (APIs, Apps, CLIs de Termux)
* **Directriz Técnica:**
  - Sección específica en Ajustes: *"Conexiones y Clientes Activos"*.
  - Las entidades conectadas heredan inicialmente los permisos de la matriz de aplicaciones, pero el usuario puede aplicar restricciones adicionales específicas por cliente.

### 2.6. Inviolabilidad de la Configuración de Fénix (Blindaje Anti-Prompt Injection)
* **Directriz Técnica:**
  - Invariante de seguridad: Ningún agente ni cliente MCP puede modificar la configuración interna de Fénix (`Settings`).
  - La API de MCP solo expone endpoints de solo lectura (`settings_status`).
  - La escritura de configuración solo es posible mediante eventos de interfaz de usuario generados físicamente por el usuario en Android Jetpack Compose.

### 2.7. Navegación Opaca Directa en F-Chat
* **Directriz Técnica:**
  - Cuando el usuario consulte al agente sobre cómo ajustar un permiso o parámetro, el agente emite un componente visual de píldora nativa con texto claro (ej. `[Ver Permisos de WhatsApp]`).
  - Prohibido exponer cadenas de URL o esquemas de deep link en texto plano (`fenix://...`). El botón despacha una acción interna opaca de Android mediante `ActionId.NAVIGATE_APP_PERMISSIONS` y argumento opaco.

---

## FASE 3: MEMORIA INTER-AGENTES, SOBERANÍA PERSISTENTE Y ESTACIÓN MULTIMODAL

### 3.1. Soberanía Persistente y Modificación Inmediata de Permisos
* **Directriz Técnica:**
  - **Soberanía en Billeteras y Pagos:** El usuario tiene control soberano para autorizar o bloquear el acceso a billeteras o apps financieras. No existen restricciones dogmáticas que bloqueen al usuario si este decide conceder acceso.
  - **Persistencia de Decisiones:** Los permisos configurados por el usuario para cualquier aplicación quedan guardados de forma permanente en `/sdcard/Documents/fenix/installed_apps_policy.json`. El usuario no debe reconfigurar una app cada vez que realiza la misma tarea.
  - **Modificación sin Salir del MCP:** El usuario puede modificar los permisos en cualquier momento desde los ajustes de Fénix o mediante los botones de acceso directo en F-Chat sin abandonar el entorno.

### 3.2. Memoria Persistente Compartida (Continuidad por Límite de Cuota)
* **Directriz Técnica:**
  - **Motor SQLite Local (`FenixMemoryDb`):** Utilizar el esquema SQLite local de Fénix en Android con tablas `sesiones`, `entradas`, `tareas_pasos`, `kv` y `misiones`.
  - **Herramientas de Memoria para MCP:** El servidor MCP expone a todos los agentes conectados: `sesion_iniciar`, `sesion_log`, `sesion_continuar`, `memoria_guardar`, `memoria_leer` y `bitacora_paso`.
  - **Relevo de Agentes ante Agotamiento de Cuota:** Si un agente (ej. Claude) agota su cuota de API o trunca la tarea, el usuario puede cambiar a otro modelo en F-Chat (ej. Gemini o DeepSeek). El nuevo agente consulta `sesion_continuar` y `bitacora_paso` para recuperar el contexto exacto y continuar la tarea sin pérdida de progreso.

### 3.3. F-Chat Multimodal Universal
* **Directriz Técnica:**
  - Permitir adjuntar y procesar en F-Chat una suite universal de formatos:
    * **Texto y Documentos:** `.txt`, `.md`, `.pdf`, `.doc`, `.docx`.
    * **Hojas de Cálculo y Datos:** `.xls`, `.xlsx`, `.xlsm`, `.csv`.
    * **Código Fuente y Configuración:** `.kt`, `.py`, `.js`, `.ts`, `.html`, `.css`, `.json`, `.yaml`, etc.
    * **Multimedia:** Imágenes (JPG, PNG, WEBP), notas de audio (M4A, WAV, MP3) y clips de video.
  - El procesamiento y parsing de documentos se ejecuta localmente en el móvil para garantizar costo $0 y privacidad.

### 3.4. Renderizado y Edición en F-Studio
* **Directriz Técnica:**
  - `F-Studio` debe integrar visualizadores nativos para:
    * **Editor de Código:** Resaltado de sintaxis, numeración y edición en caliente.
    * **Visor de Documentos y Tablas:** Renderizado de Markdown, PDFs y vistas tabulares de hojas de cálculo (`.xlsx`, `.csv`).
    * **Visor Multimedia:** Previsualización fluida de imágenes y reproducción de notas de voz.

### 3.5. Cerrojo de Emergencia (Emergency Latch) y Trazabilidad
* **Directriz Técnica:**
  - El botón de parada de emergencia en la barra del sistema detiene de inmediato el servidor, invalida tareas en vuelo y persiste el estado `ServerStatus.EmergencyStopped`.
  - El bloqueo persiste en disco y no admite autoreinicios hasta que el usuario confirme el desbloqueo manual en la UI.
  - Toda acción física se reporta como `CANCELLED_BEFORE_DISPATCH`, `DISPATCHED_TO_SYSTEM` o `CONFIRMATION_UNCERTAIN`.

---

## FASE 4: AUTENTICACIÓN, GESTIÓN DE CUOTAS ORIENTADA A SOLUCIONES Y APROBACIÓN FLOTANTE

### 4.1. Prohibición de Servidor Remoto sin Autenticación
* **Directriz Técnica:**
  - Invariante de red: El servidor MCP en modo remoto (Túnel Cloudflare o bind externo) NO puede iniciarse si la autenticación (Bearer Token u OAuth) está desactivada.
  - Si el usuario intenta activar el túnel remoto sin credenciales configuradas, la interfaz bloquea el inicio y despliega la generación ágil de token.

### 4.2. Autorización Rápida Flotante (Aprobación sin Salir de la App Actual)
* **Problema Resuelto:** Fricción de tener que salir de la app de trabajo para abrir el MCP y buscar menús de aprobación.
* **Directriz Técnica:**
  - Cuando un cliente o app externa solicita conexión o token Bearer al MCP, el sistema despliega un componente interactivo flotante en primer plano (Banner Heads-Up interactivo / Overlay del sistema).
  - El usuario puede autorizar o rechazar la conexión con un solo toque (`[✓ Permitir Conexión] | [✕ Rechazar]`) directamente sobre la pantalla, sin necesidad de abrir la app del MCP ni cambiar de contexto.

### 4.3. Enforcement de Scopes por Capacidad en el Protocolo MCP
* **Directriz Técnica:**
  - Asociar a cada token de acceso una lista tipada de scopes:
    * `scope:mcp:read` (inspección de árbol de accesibilidad, notas, estado).
    * `scope:mcp:write` (escritura en archivos locales y workspace).
    * `scope:mcp:action` (interacción física con pantalla: clicks, gestos).
    * `scope:mcp:system` (acciones globales: volver, inicio, recientes).
  - La invocación de herramientas sin el scope correspondiente retorna `403 Forbidden`.

### 4.4. Manejo de Errores Empático y Elegante (Tarjetas de Asistencia con Botones de Solución)
* **Principio de UX:** Cero tecnicismos crípticos. La app no muestra códigos de error fríos ni volcados de pila (stack traces). Todo incidente se comunica con elegancia, tono constructivo y opciones de solución directa.
* **Directriz Técnica ante Límite de Cuota (Rate Limit / HTTP 429):**
  - Mostrar una tarjeta visual destacada con redacción amigable:  
    *"La cuota de procesamiento de este modelo ha alcanzado su límite temporal."*
  - Ofrecer botones de acción resolutiva inmediata:
    1. `[🔑 Obtener nueva API Key]`: Botón con enlace web directo a la consola del proveedor (ej. Google AI Studio, Anthropic, Mistral, OpenAI).
    2. `[🔄 Cambiar de Proveedor]`: Botón que enfoca el selector inferior de modelos en F-Chat.
    3. `[⏳ Esperar Restablecimiento]`: Indicador de tiempo estimado de reinicio de cuota si el proveedor lo suministra.
    4. `[➕ Agregar otro Proveedor]`: Botón que abre el modal directo para pegar una nueva clave privada.
* **Directriz Técnica ante Pérdida de Conexión:**
  - Explicar la situación con calma y ofrecer: `[Reintentar Envío]` o `[Continuar con Modelo Local Offline]`.

### 4.5. Monitor de Cuotas Local y Almacenamiento Seguro ($0 Asistido)
* **Directriz Técnica:**
  - Las API keys del usuario se almacenan cifradas mediante `EncryptedSharedPreferences` respaldado por Android Keystore.
  - Prohibido volcar credenciales completas en los logs del servidor.
  - Odómetro local en tiempo real de tokens consumidos y umbrales de alerta preventiva configurables por el usuario.

### 4.6. Módulos CLI y Google Jules en Termux
* **Directriz Técnica:**
  - Google Jules CLI y herramientas de terminal se administran como extensiones bajo demanda del usuario desde F-Market.
  - Cero precarga obligatoria en la APK. Su ejecución requiere confirmación de comandos supervisados.

---

## FASE 5: SOBERANÍA POR INTERRUPTORES, ACCESIBILIDAD MODULAR Y FIRMA MAESTRA FIRST-PARTY

### 5.1. Matriz Soberana de Permisos por Interruptores (Toggles ON/OFF)
* **Directriz Técnica:**
  - Prohibido imponer bloqueos o habilitaciones automáticas dogmáticas por el sistema.
  - El usuario dispone en los Ajustes de un panel de interruptores directos (Toggles) para controlar individualmente cada acceso:
    * **Micrófono:** Habilita o desactiva la captura de audio y dictado.
    * **Cámara:** Habilita o bloquea la captura de fotos y visión en vivo.
    * **Notificaciones:** Enciende o apaga la interceptación de alertas.
    * **Almacenamiento:** Controla el acceso a directorios locales.
    * **Ubicación:** Activa o revoca el acceso a coordenadas geográficas.
  - El estado del interruptor es vinculante: si está apagado, el sistema deniega el acceso de inmediato sin consultar al modelo.

### 5.2. Módulo de Accesibilidad Desacoplado (Extensión Descargable en F-Market)
* **Directriz Técnica:**
  - La aplicación base del MCP móvil se compila y distribuye sin requerir forzosamente el permiso de accesibilidad para su uso cotidiano (chat, gestión documental, APIs, conectores).
  - El servicio de accesibilidad y control táctil de pantalla se empaqueta como una **Extensión Oficial de Accesibilidad**, descargable e instalable desde F-Market bajo decisión explícita del usuario.
  - **Prohibición a Terceros:** Queda estrictamente restringido a terceros el desarrollo de extensiones que soliciten o utilicen el servicio de accesibilidad. El puente de accesibilidad es de uso exclusivo para componentes oficiales (*First-Party*).

### 5.3. Llave Criptográfica Maestra del Propietario (Filtro Invisible Anti-Manipulación)
* **Directriz Técnica:**
  - Implementar un mecanismo de validación criptográfica local donde toda extensión privilegiada de accesibilidad debe estar firmada o validada con una clave secreta maestra / código invisible custodiado exclusivamente por ti en tu dispositivo.
  - Ningún usuario común ni desarrollador externo puede generar extensiones con este privilegio: si el hash de firma de la extensión no coincide con la llave maestra registrada en el móvil, el sistema rechaza la instalación de inmediato.

### 5.4. Arquitectura de Autenticación de Usuarios (Gmail y Autenticación Propia)
* **Directriz Técnica:**
  - Prever e implementar una capa desacoplada de identidad de usuario:
    * **Login con Google / Gmail:** Integración nativa con Android Credential Manager / Google Sign-In para registro y autenticación ágil.
    * **Autenticación Propia:** Mecanismo alternativo de usuario/contraseña o Passkeys para usuarios que prefieran no depender de Google.
  - **Independencia Operativa:** La app permite el uso anónimo/local por defecto a costo $0, pero si el usuario inicia sesión (con Gmail o cuenta propia), sus preferencias, perfiles de extensiones y configuraciones se sincronizan bajo su identidad.

### 5.5. Experiencia F-Chat y Gestión de Conectores
* **Directriz Técnica:**
  - **Voz Supervisada:** Transcripción de dictado visible y editable en pantalla. Envío manual exclusivo mediante el botón de enviar.
  - **Salud de Conectores:** Estados visibles (`Registrado`, `Autenticado`, `Autorizado`, `Probado`, `Activo`) con botón interactivo `[Probar Conector]`.
  - **Aislamiento de Espacios:** Cada conversación o proyecto mantiene su memoria y contexto estrictamente aislados para evitar fuga de información.

---

## FASE 6: BROKER FIRST-PARTY DE ACCESIBILIDAD Y QA ADVERSARIAL AUTOMATIZADO

### 6.1. Arquitectura del Broker First-Party (Observe -> Propose -> Commit)
* **Directriz Técnica:**
  - El módulo oficial de accesibilidad implementa la separación estricta del ciclo de acción:
    * `Observe`: Lectura del árbol visual y extracción de nodos accesibles permitidos.
    * `Propose`: El modelo de IA o cliente formula la intención de interacción de forma estructurada.
    * `Commit`: Verificación de identidad del paquete, foco actual de ventana y despacho físico.
  - **Leases de Autorización Efímeros:** Toda autorización táctil cuenta con un Time-To-Live corto; si la acción no se ejecuta dentro de su ventana de vigencia, la autorización expira automáticamente.

### 6.2. Prevención Activa de Carreras de Ventana (Anti-TOCTOU)
* **Directriz Técnica:**
  - Inmediatamente antes de invocar `dispatchGesture` o `performAction`:
    * Re-consultar la ventana en primer plano y verificar que coincide con el paquete previamente autorizado.
    * Invocar `node.refresh()` para comprobar que las coordenadas, visibilidad y hash del nodo no mutaron.
    * Si la ventana o el nodo cambiaron durante el procesamiento, la acción se cancela inmediatamente con código tipado `STALE_NODE` o `WINDOW_CHANGED`.

### 6.3. Suite de Pruebas Adversariales Automatizadas (QA Local $0)
* **Directriz Técnica:**
  - Implementar una batería de pruebas unitarias e instrumentadas locales con Test Doubles (Mocks que comprueban que el adaptador de Android real recibe CERO llamadas no autorizadas):
    * `TEST_FIN_BLOCK`: Simulación de app bancaria/billetera -> Verificar que lectura y gestos arrojan `DENY` sin invocar a Android.
    * `TEST_TOCTOU_RACE`: Simular cambio repentino de app entre la propuesta y el despacho -> Verificar cancelación segura.
    * `TEST_KEY_TAMPER`: Simular carga de extensión de accesibilidad sin la clave o firma maestra del propietario -> Verificar rechazo inmediato.
    * `TEST_AUTH_SCOPES`: Llamada a herramientas de escritura o gestos con token de solo lectura -> Verificar `403 Forbidden`.
    * `TEST_EMERGENCY_STOP`: Pulsación de parada de emergencia durante ejecución concurrente -> Verificar cancelación inmediata de cola y persistencia del cerrojo.

---

## FASE 7: MODELOS LOCALES EN MÓVIL, ARQUITECTURA DE PERMISOS EN 2 CAPAS Y CATÁLOGO CLI (ANTIGRAVITY, OPENCODE, JULES)

### 7.1. Inferencia Local Probada Exclusivamente en el Celular ($0 y Offline)
* **Directriz Técnica:**
  - Prohibido validar modelos locales en la computadora para inferir su viabilidad. Toda prueba de LLM local debe realizarse **estrictamente sobre el hardware real del teléfono móvil**.
  - Medición in situ de RAM consumida (RSS pico), temperatura de batería y estabilidad de proceso antes de certificar compatibilidad.

### 7.2. Doble Modalidad de LLMs Nativos (Nativo Directo vs. Termux) sin Duplicar Almacenamiento
* **Directriz Técnica:**
  - El F-Market ofrecerá extensiones para ambas modalidades de ejecución según la preferencia del usuario:
    1. **Modalidad Nativa Directa (Sin Termux):** Ejecución de Ollama o llama.cpp embebido directamente en la app para usuarios que buscan simplicidad con un toque.
    2. **Modalidad Termux:** Ejecución de Ollama / llama-cli dentro del entorno de terminal de Termux para usuarios avanzados y desarrolladores.
  - **Deduplicación de Modelos Compartidos:**
    * Los pesos y archivos de modelos se almacenan en un directorio unificado y persistente: `/sdcard/Documents/fenix/models/`.
    * Si el usuario descarga un modelo en una modalidad y luego cambia a la otra, la app reutiliza los mismos pesos sin volver a descargarlos ni duplicar espacio de almacenamiento.
    * Selector visual en la configuración de la extensión para elegir la vía de conexión activa (Nativa o Termux).

### 7.3. Arquitectura Soberana de Permisos en 2 Capas (Global e Individual)
* **Directriz Técnica:**
  - **Capa 1: Política Global Predeterminada:** El usuario define la postura de seguridad base para todo el dispositivo.
  - **Capa 2: Gestión Individual por Conector / CLI / API / Web Chat:**
    * Cada entidad conectada (Antigravity CLI, OpenCode, Jules, Claude Desktop, una API o un chat web) tiene su propia sección de permisos independiente.
    * **Herencia Inteligente:** Al registrarse una nueva entidad, adopta por defecto la política global configurada por el usuario.
    * **Sintonía Fina Soberana:** El usuario puede encender o apagar cualquier permiso para esa entidad específica (red, almacenamiento, ejecución de comandos, o accesibilidad si así lo decide).
  - **Resguardo Predeterminado de Billeteras:** Las aplicaciones de pago permanecen aisladas por defecto para toda nueva entidad hasta que el usuario decida manualmente encender el permiso para ese cliente en particular.

### 7.4. Catálogo Modular de Extensiones en F-Market (Runtimes y CLIs)
* **Directriz Técnica:**
  - Instalación bajo demanda: Runtimes (Python, Node, compiladores) y herramientas CLI se distribuyen fuera del APK base como extensiones opt-in.
  - Disponibilidad oficial en F-Market de:
    1. **Google Jules CLI:** Automatización de tareas de desarrollo y repositorios.
    2. **Antigravity CLI:** Orquestación y flujos agentic avanzados.
    3. **OpenCode CLI:** Asistencia de código y desarrollo abierto.
    4. **llama-cli / AIChat:** Clientes de terminal ligeros para inferencia local.
  - **Orquestación en F-Chat:** Las extensiones instaladas se integran automáticamente en la barra de herramientas de F-Chat, permitiendo al usuario interactuar con estos CLIs mediante comandos naturales o botones rápidos.

---

## FASE 8: EMPAQUE, VERIFICACIÓN INTEGRADA Y ACTUALIZACIÓN SIN PÉRDIDAS

### 8.1. Estrategia de Compilación en Dos Flavors
* **Directriz Técnica:**
  - Mantener la separación de variantes de compilación:
    * `Flavor FOSS`: 100% libre de dependencias propietarias de Google, ideal para distribución abierta y sideloading a costo $0.
    * `Flavor GMS`: Integración nativa de Android Credential Manager para inicio de sesión opcional con Google / Gmail.
  - Ambas variantes comparten idéntico motor de MCP, F-Chat, F-Studio y F-Market.

### 8.2. Preservación Inmutable de Datos ante Actualizaciones (Upgrades)
* **Directriz Técnica:**
  - Al instalar una nueva versión del APK (actualización sobre la existente):
    * La identidad del túnel (`tunnel_identity.json`) se mantiene intacta en `/sdcard/Documents/fenix/` preservando la URL fija.
    * La carpeta de modelos compartidos (`/sdcard/Documents/fenix/models/`) se respeta íntegramente sin re-descargas ni pérdida de gigabytes.
    * Las políticas de aplicaciones (`installed_apps_policy.json`) y configuraciones de usuario se conservan sin reinicio.
  - Migraciones SQLite en `FenixMemoryDb`: El método `onUpgrade` aplicará scripts de migración no destructivos (`ALTER TABLE`), preservando el historial de sesiones y bitácoras previas.

### 8.3. Lista de Comprobación GO / NO-GO Previa a Publicación
* **Directriz Técnica:**
  - Análisis estático de secretos: Verificación automatizada de que el APK final no contiene API keys privadas ni tokens hardcodeados.
  - Verificación de arranque en frío: Comprobar que en una instalación limpia el servidor inicia apagado por defecto.
  - Prueba de deduplicación de modelos: Verificar que ambas extensiones de inferencia (Nativa y Termux) reconocen el mismo directorio compartido de modelos.
  - Prueba de navegación opaca: Validar que todos los botones de asistencia en F-Chat conducen a las pantallas de ajustes correspondientes.

---

## FASE 9: OPERACIÓN CONTINUA, MANTENIMIENTO MODULAR Y PORTABILIDAD TOTAL

### 9.1. Portabilidad Soberana de Datos (Backup en 1 Toque)
* **Directriz Técnica:**
  - Implementar la función de exportación completa desde los Ajustes de almacenamiento:
    * Genera un archivo comprimido (`/sdcard/Download/fenix_backup_[fecha].zip`) conteniendo: conversaciones de F-Chat, base de datos SQLite de memoria (`FenixMemoryDb`), notas y artefactos de F-Studio.
    * Cero cautiverio de datos: El usuario es dueño absoluto de su información y puede exportarla en cualquier momento en formatos abiertos estándar (.json, .md, .sqlite).

### 9.2. Borrado Seguro y Reseteo Soberano (Factory Reset Local)
* **Directriz Técnica:**
  - Botón explícito en Ajustes: *"Limpieza Total y Retirada"*:
    * Purga y destruye las claves criptográficas almacenadas en Android Keystore (`EncryptedSharedPreferences`).
    * Revoca y elimina tokens de túnel y credenciales OAuth registradas.
    * Vacía las tablas de base de datos local y cachés de sesión.
    * Solicita confirmación explícita con aviso de irreversibilidad antes de ejecutar.

### 9.3. Mantenimiento Modular de Extensiones y CLIs
* **Directriz Técnica:**
  - Las extensiones de F-Market (Antigravity CLI, OpenCode, Google Jules, runtimes de Termux) cuentan con ciclo de vida independiente del APK base.
  - El usuario puede actualizar, pausar o desinstalar cualquier CLI o conector desde su ficha en F-Market sin necesidad de reinstalar la aplicación completa ni interrumpir el servidor principal.

### 9.4. Diagnóstico del Sistema y Telemetría 100% Local
* **Directriz Técnica:**
  - Panel de salud operativa en Ajustes: Estado de sockets locales, memoria RAM consumida, temperatura del dispositivo, latencia de proveedores y cuota acumulada.
  - Telemetría estrictamente local: Prohibido transmitir estadísticas de uso, textos de usuario o transcripciones a servidores externos; todo diagnóstico se visualiza y almacena localmente en el teléfono.

---
## CONCLUSIÓN Y ESTADO DEL PLAN MAESTRO
**Plan de Directrices Técnicas de Desarrollo Oficial: 100% Revisado, Depurado y Aprobado.**  
Comprende la totalidad del ciclo de vida del proyecto desde la **Fase 0 hasta la Fase 9**, listo para ser ejecutado directamente en el código fuente bajo el protocolo GUARDIAN.
*(Fin del Documento Oficial).*

