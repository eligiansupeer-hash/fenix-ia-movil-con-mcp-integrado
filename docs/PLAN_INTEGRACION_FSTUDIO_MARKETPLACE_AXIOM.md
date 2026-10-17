# Plan Maestro de Integración: F-Studio (IDE en Vivo), Marketplace de Extensiones, Flota AXIOM, Motores Políglotas y Orquestador CPU/RAM

**Fecha:** 2026-10-17
**Rama:** `feature/ui-ai-connection-docs`
**Estado:** En ejecución bajo Protocolo GUARDIAN (Costo = $0, Cero Hardcodeo Académico, Independencia de Termux).

---

## 1. Objetivo General

Transformar a **Fénix Mobile MCP** en una estación de desarrollo, ejecución e inteligencia artificial móvil autónoma (estilo VS Code / Antigravity en Android), eliminando la dependencia obligatoria de Termux (que pasa a ser una extensión opcional descargable dentro del Marketplace) e incorporando:

1. **Orquestador Inteligente de Hardware (`HardwareCapabilitiesDetector` + `CpuCoreAffinityManager`):**
   - Detección en tiempo real de RAM total, RAM libre, umbrales de seguridad anti-LMKd (*Low Memory Killer*) y topología heterogénea de núcleos ARM (`big.LITTLE` / `DynamIQ`) leyendo frecuencias máximas en `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`.
   - Asignación inteligente de cargas: tareas de red/servidor MCP a núcleos de eficiencia y tareas de compilación/inferencia a núcleos de alto rendimiento.
   - Recomendador dinámico de modelos cuantizados (`.gguf` / `.task` MediaPipe) según la capacidad real del móvil portador.

2. **F-Studio (IDE Móvil + Espacio de Trabajo en Vivo):**
   - Editor de código integrado con numeración de líneas, resaltado de sintaxis multi-lenguaje (Python, Kotlin, JS/TS, C/C++, Rust, Go, HTML, Markdown, JSON, SQL, Bash), barra de símbolos rápidos para programadores y buscador.
   - **Sincronización en Vivo con IA (`LiveWorkspaceBus`):** Cuando un agente (F-Chat local, Gemini, Claude o Manus vía MCP) crea, lee o edita un documento mediante `FenixFileManager`, el documento se refleja en vivo en F-Studio para lectura, edición o previsualización inmediata (Markdown, HTML, JSON, Código).
   - **Consola Políglota Integrada:** Ejecución y compilación con 1 toque tanto en motor local como en servidores virtuales gratuitos sin login.

3. **Marketplace de Extensiones (`F-Market` estilo VS Code):**
   - Cada extensión posee ficha técnica completa estilo VS Code: Nombre, Identificador, Autor, Versión, Categoría, Consumo de RAM, Descripción Corta, **README Detallado** (qué es, para qué sirve, qué herramientas habilita, qué ocurre al activarla/desactivarla) y enlace a documentación oficial.
   - **Categorías del Marketplace:**
     1. **Agentes Personalizados & Flota AXIOM:** `AXIOM Criticon`, `AXIOM Deep Researcher`, `AXIOM Polyglot Architect`, `AXIOM Hardware & Memory Guard`, `AXIOM Street Co-Pilot`, `AXIOM Universal Doc Synthesizer`.
     2. **Modelos IA Cuantizados (On-Device):** Catálogo adaptativo con semáforo de compatibilidad según la RAM del dispositivo (`Qwen 2.5 Coder 1.5B`, `Gemma 2 2B MediaPipe`, `Llama 3.2 3B`, `DeepSeek R1 Distill`, `Hermes 3 Tool-Use`).
     3. **Runtimes, Compiladores y Termux Opcional:** `QuickJS Sandbox`, `Python Mobile Bridge (Chaquopy/Hybrid)`, `Clang/LLVM & TinyCC Native Kit`, `Rosemoe CodeEditor Pack`, y **`Termux Linux Bridge (Opcional)`** con botón de descarga directa oficial.
     4. **Servidores Virtuales (VMs) y Nube Efímera Sin Login:** `Piston API`, `Wandbox (Clang/GCC/Python/Rust/Go)`, `H2.nexus WebVM`, `Godbolt Compiler Explorer`, `file.io`, `storage.to` / `tmpfiles.org`, más opción para que el usuario registre cualquier servidor virtual futuro.
     5. **Mis Extensiones & Vibe Coding:** Creación, edición, activación y exportación de extensiones codificadas manualmente en F-Studio o generadas conversando con la IA (*Vibe Coding*).

---

## 2. División en Microfases (Protocolo GUARDIAN)

- **Fase 1:** Núcleo de Hardware (`HardwareCapabilitiesDetector.kt`, `CpuCoreAffinityManager.kt`), Conectores Cloud Zero-Auth (`ZeroAuthCloudSandboxClient.kt`, `EphemeralCloudStorageClient.kt`) y Registro del Marketplace (`FenixExtensionRegistry.kt`).
- **Fase 2:** Bus de Documentos en Vivo (`LiveWorkspaceBus.kt`), Motor de Ejecución Políglota (`PolyglotExecutionEngine.kt`) y nuevas herramientas MCP en `FenixTools.kt` y `FenixDefaultToolInitializer.kt`.
- **Fase 3:** Pantallas de Interfaz Visual: **F-Studio IDE (`StudioIdeScreen.kt`)** y **Marketplace de Extensiones (`MarketplaceScreen.kt`)**.
- **Fase 4:** Integración en barra de navegación (`Routes.kt`, `MainScreen.kt`), recursos bilingües Español/Inglés (`strings.xml`) y compilación + verificación final.


---

## 3. Control de Pantalla Dividida Bajo Demanda (`SplitScreenController`)

- **`AndroidManifest.xml`:** Habilitado `android:resizeableActivity="true"` y manejo fluido de `configChanges="screenSize|smallestScreenSize|screenLayout|orientation"` en `MainActivity`.
- **`SplitScreenController.kt`:**
  - Conmutación inmediata de pantalla dividida mediante `AccessibilityService.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)`.
  - Lanzamiento de aplicaciones en la mitad adyacente mediante `Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK`.
  - Activación automática de `force_resizable_activities` (cuando el permiso `WRITE_SECURE_SETTINGS` está disponible) para obligar a aplicaciones rígidas a aceptar pantalla dividida.
  - Protección estricta del Firewall Bancario Anti-Ban.
- **Herramienta MCP (`split_screen_control`) y Extensión en Marketplace (`split-screen-multitask-engine`):** Disponible tanto para agentes remotos/locales como mediante botón directo **`[ ◫ Pantalla Dividida ]`** en la barra de F-Studio.


---

## 4. Interruptor Físico de Emergencia (Hardware Kill-Switch / Anti-Bucle)

- **`EmergencyKillSwitchManager.kt` + `McpAccessibilityService.onKeyEvent` + `LoggedToolRegistration.kt`:**
  - **Disparo por Botones Físicos del Teléfono (sin necesidad de tocar la pantalla):**
    1. **Triple pulsación rápida de `[Volumen Abajo]`** (3 clics en menos de 1.25 segundos), o
    2. **Pulsación simultánea de `[Volumen Arriba + Volumen Abajo]`**.
  - **Acción Inmediata de Corte:**
    - Cancela todos los `Job` de herramientas MCP en curso (`activeToolJobs.clear()`).
    - Activa bloqueo inmediato de ráfagas entrantes (`CancellationException`).
    - Detiene el servicio `McpServerService` y el túnel activo.
    - Oculta los overlays y limpia la caché de nodos de accesibilidad.
    - Emite doble vibración háptica de confirmación y aviso en pantalla.

## FASE 7: TIENDA DE INSTANT APPS COMUNITARIAS (GITHUB COSTO $0), AXIOM CRITICÓN POR MÉTRICAS OBJETIVAS (OQSI) Y CONSENTIMIENTO INFORMADO
- **`AxiomCriticonAuditor.kt`**:
  - Auditoría automática, determinista y **Zero-Knowledge (0 bytes de datos personales expuestos)**.
  - Evalúa cada creación mediante el **OQSI (`Objective Quality & Safety Index`, 0 a 100)**:
    1. **Seguridad Estática SAST (`0-100`, bloqueante < 85)**: Bloquea accesos a `/data/data/`, `su -c`, `rm -rf`, `SharedPreferences`, paquetes bancarios protegidos y **protege al propio autor bloqueando fugas accidentales de API Keys** (`AIzaSy...`, `sk-...`, `ghp_...`, `gsk_...`).
    2. **Calidad Descriptiva (`0-100`, bloqueante < 70)**: Exige resumen mínimo de 40 caracteres, documentación detallada mínima de 120 caracteres y declaración explícita de capacidades.
    3. **Eficiencia de RAM/CPU y Anti-Bucle (`0-100`, bloqueante < 60)**: Detecta bucles infinitos sin corte (`while(true)`, `for(;;)`) y evalúa huella de memoria.
    4. **Certificado SHA-256 de Integridad**: Firma criptográfica corta adjunta a cada creación.
  - **Descubrimiento y Publicación en GitHub a Costo $0**:
    - Sincroniza creaciones comunitarias públicas bajo los topics `fenix-instant-app` y `fenix-mcp-extension` y las audita antes de mostrarlas.
    - Exige **Consentimiento Informado Explícito (3 casillas obligatorias + alias de autor)** antes de permitir compartir una creación en la tienda comunitaria.
