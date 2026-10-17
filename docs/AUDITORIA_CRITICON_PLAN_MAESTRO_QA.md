# AUDITORÍA QA ILIMITADA — AXIOM CRITICÓN vs PLAN MAESTRO + CÓDIGO REAL

**Fecha:** 2026-10-16
**Auditor:** AXIOM CRITICÓN (modo QA ilimitada, sin límite de 3 críticas)
**Documento auditado:** `docs/PLAN_MAESTRO_QA_FENIX_MOBILE.md` (253 líneas)
**Código contrastado:** `app/src/main/kotlin/com/fenix/mobilemcp/` (mcp/, connectors/, llm/, security/, services/, privacy/, memory/, ui/)
**Secrets (solo nombres):** `~/.fenix/secrets/api_keys.json` — claves: `cloudflare, openrouter, fenix_entregas, supabase, telegram, codex_proxy_pool, groq, cerebras, mistral, cohere, sambanova, gemini, github_token`
**Hardware HITL:** Motorola Moto G77 5G (`ZY22MK7B2P`, Android 16 / SDK 36, 8 GB RAM + 16 GB ZRAM), `com.fenix.mobilemcp.foss.debug`, Termux `llama-server` (`qwen2.5-0.5b-instruct-q4_k_m.gguf`) en `127.0.0.1:8081`

**Veredicto global:** el Plan Maestro es ambicioso pero **describe un sistema que no existe en el código actual**. Contraste directo revela stubs documentados como "conectores reales", herramientas contadas que no coinciden, pantallas nombradas que no existen, y pruebas que darán **falso PASS** (string simulado / catch silencioso / fallback `ok`) si no se endurecen.

---

## BLOQUE A — SUITE 0 / GAP-01/02/03: el Plan diagnostica mal su propio GAP

### CRÍTICA #1 — Severidad: **Bloqueante**
**a) Severidad:** Bloqueante
**b) Archivo/sección:** Plan §0 GAP-01 vs `mcp/registry/FenixDefaultToolInitializer.kt:20-44` + `services/mcp/McpServerService.kt:444-475` + `mcp/tools/LoggedToolRegistration.kt:78-98`
**c) Fallo / punto ciego:** El Plan dice "57 herramientas solo se registran en el SDK MCP cuando arranca Ktor, no en `FenixToolRegistry`". Falso por diseño actual: `LoggedToolRegistrar.addTool()` (líneas 86-97) registra **simultáneamente** en `FenixToolRegistry` Y en `server?` (nullable). `FenixDefaultToolInitializer.initialize()` pasa `server=null`, y `McpServerService.registerAllTools()` pasa el SDK real. El problema real no es "desconexión", es **doble instancia de dependencias**: `FenixDefaultToolInitializer` crea su propio `FenixMemoryDb(context)`, `ZoneMatcher`, `TurnScheduler` (líneas 21-26) y `McpServerService.registerFenixBundle()` (líneas 484-502) crea OTRO `FenixMemoryDb`, OTRO `ZoneMatcher`, OTRO `TurnScheduler`. Hay 2 DB helpers, 2 schedulers, 2 monitors vivos.
**d) Ajuste al Plan:** Agregar prueba **S0-01 Divergencia de instancias**: `memory_save` vía `FenixToolRegistry.executeInProcess` → leer vía ruta HTTP `/mcp` + `kvLeer` directo + inspección `dumpsys dbinfo`; assert mismo `valor`, mismo `actualizado`, sin `SQLiteDatabaseLocked`. Si falla, el fix no es "registrar 57 tools al inicio" sino unificar a singleton Hilt de `FenixMemoryDb`/`TurnScheduler`.

### CRÍTICA #2 — Severidad: **Bloqueante**
**a) Severidad:** Bloqueante
**b) Archivo/sección:** Plan §0 GAP-02 vs `connectors/*/*.kt` (los 9 archivos, 15-86 líneas c/u)
**c) Fallo:** El Plan asume que los 9 conectores "existen como clases Kotlin implementadas, pero requieren wrappers". Verificación real: **son stubs/objetos de constantes, no clientes invocables**:
- `backend/CloudflareClient.kt:11-17` — solo `object` con `routeOrder()`, sin `verify_token`, sin `kv_put/get`.
- `entregas/EntregasFirstParty.kt:11-21` — solo constantes de tablas, sin `registrar_evento` ni `consultar_zonas`.
- `github/GitHubConnector.kt:9-22` — solo `API_BASE` + listas de scopes + constante timeout, sin ningún `GET/POST`.
- `google/GoogleOAuth.kt:10-26` — solo `CALLBACK_URL` + scopes + allowlist, sin `build_url`, sin PKCE, sin token store.
- `social/SocialReadOnly.kt:10-27` — solo paquetes + constantes dwell, sin `read_feed`.
- `whatsapp/WhatsAppHybrid.kt:10-19` — solo `PACKAGE` + constantes pausa, sin `prepare_message`.
Solo `SupabaseLite.kt` (58 líneas, `select`+`heartbeat`), `GmailClient.kt` (64 líneas, `historyList`+`searchAll`), `DriveClient.kt` (86 líneas, `list`+`get`) tienen HTTP real. Por tanto S2-01, S2-03, S2-04, S2-07, S2-08, S2-09 **no pueden dar PASS real hoy**; cualquier prueba que "invoque `cloudflare_verify_token`" desde el chat fallará con `Herramienta no encontrada` (`FenixToolRegistry.executeInProcess:70-84`).
**d) Ajuste:** Pre-Suite 2 obligatoria **S2-00 Inventario de superficie real**: para cada `S2-xx`, assert `FenixToolRegistry.get(nombre)!=null` ANTES del E2E. Si null → marcar `FAIL_POR_AUSENCIA` (no "manejo limpio de error") y exigir especificar nombre exacto del tool, schema JSON, y qué clase lo respalda.

### CRÍTICA #3 — Severidad: **Alta**
**a) Severidad:** Alta
**b) Archivo/sección:** Plan §0 GAP-03 vs `llm/DynamicModelDiscovery.kt:65-105` + `security/SecureKeyStore.kt:83-149` + `api_keys.json`
**c) Fallo:** GAP-03 pide agregar `cerebras/mistral/cohere/codex_proxy_pool` a discovery + keystore. Pero `SecureKeyStore` ya es genérico (`saveApiKey(provider, key)` con `provider.trim().lowercase()`, prefijo `api_key_`) — no hay nada que "incorporar" ahí. El gap real está en `DynamicModelDiscovery.standardProviders` (lista hardcodeada línea 65) que además **omite `deepseek` en el Plan pero sí existe en código**, y omite `codex_proxy_pool` que no es proveedor OpenAI-compat (`local_key`, `admin_jwt_secret`, `agent_token_*`). Inyectar `codex_proxy_pool.local_key` como Bearer contra `/v1/models` es un error de categoría. El Plan no distingue `api_keys.json` jerárquico (`fenix_entregas.supabase_url` vs `groq.api_key` vs `github_token: str` plano).
**d) Ajuste:** Dividir S1 en **S1-00 Mapeo de secrets**: tabla `ruta_en_json → providerId_normalizado → requiresKey/baseUrl/headers`. Probar que `saveApiKey("Cerebras ")` y `hasApiKey("cerebras")` coinciden (normalización), que `github_token` plano no se inyecta como proveedor LLM, y que `codex_proxy_pool` NO entra a `discoverAllAvailableModels()` sino a suite de túnel/proxy separada.

---

## BLOQUE B — SUITE 1: proveedores LLM (falsos PASS por headers, tool-calling y SLM local)

### CRÍTICA #4 — Severidad: **Bloqueante**
**b) Archivos:** Plan S1-02 + `llm/FenixLlmClient.kt:147-153` + `llm/DynamicModelDiscovery.kt:132-138`
**c) Fallo:** `FenixLlmClient` nunca envía los headers obligatorios de OpenRouter (`HTTP-Referer`, `X-Title`). `grep` en `llm/` confirma cero ocurrencias. En OpenRouter muchos modelos `:free` devuelven `401/403` o vacían `choices` sin esos headers. S1-02 dará FAIL intermitente y se culpará a la key, cuando es bug de cliente.
**d) Ajuste:** Agregar **S1-02b Headers OpenRouter**: interceptar con `adb logcat` + proxy/host check que el POST a `openrouter.ai` incluya `HTTP-Referer` y `X-Title`; assert `choices[0].message` no vacío con modelo `:free` explícito. Si no, fix en `FenixLlmClient` antes de continuar.

### CRÍTICA #5 — Severidad: **Bloqueante**
**b) Archivos:** Plan S1-03/S1-06/S1-07 + `llm/DynamicModelDiscovery.kt:86-90` + `llm/FenixLlmClient.kt:82,147-183`
**c) Fallo:** Gemini vía `.../v1beta/openai` y Cohere `.../compatibility/v1` tienen particularidades de `tool_calls`. `FenixLlmClient.parse` (líneas 172-183) asume `function.arguments` es string JSON; si devuelven objeto ya parseado → `jsonPrimitive.content` lanza → `mapNotNull` silencioso → **cero tool_calls → la prueba parece "PASS texto" aunque el tool_call se perdió**.
**d) Ajuste:** **S1-03b/06b/07b Tool-call fidelity por proveedor**: prompt forzado ("llama a `memory_read` con `clave=test`") y assert: (1) `tool_calls` no vacío, (2) `function.name` exacto registrado, (3) `arguments` parseable por `ChatViewModel.parseJsonArgs`. Loggear raw `bodyAsText` (truncado sin secrets) como evidencia.

### CRÍTICA #6 — Severidad: **Bloqueante**
**b) Archivos:** Plan S1-08 + `llm/DynamicModelDiscovery.kt:91-97` + `llm/FenixLlmClient.kt:134-143` + `ui/viewmodels/ChatViewModel.kt:106-109`
**c) Fallo:** El SLM local `qwen2.5-0.5b-instruct-q4_k_m.gguf` en `llama-server:8081` **no soporta function-calling OpenAI** de forma fiable; `FenixLlmClient` siempre envía `tools=... + tool_choice:auto` si `includeTools=true` (líneas 137-142). llama.cpp devolverá texto libre o `400 unknown field tools`. Además `ChatViewModel.refreshModels()` prefiere `isLocal` (línea 108), por lo que el "Modo Offline" será default y **todas las S4 parecerán FAIL de herramientas cuando es FAIL de modelo**. Medición `tokens/s` indefinida.
**d) Ajuste:** **S1-08b Capacidad real del SLM**: (1) probar `includeTools=false` vs `true` contra `127.0.0.1:8081` y registrar status/raw; (2) si `tools` causa 400, exigir fallback: chat local SIN tools + dispatch manual, o excluir SLM de S4; (3) definir medición `tokens/s` exacta (endpoint + fórmula `predicted_n / predicted_ms`) y temperatura vía `dumpsys battery`.

### CRÍTICA #7 — Severidad: **Alta**
**b) Archivos:** Plan S1-09 + `llm/DynamicModelDiscovery.kt:98-104`
**c) Fallo:** S1-09 asume "puente `fenix-usb-bridge.sh`" y túnel ADB reverso a `11434`. Ese script no está referenciado en el repo listado. Sin script versionado, la prueba depende de túnel manual (`adb reverse tcp:11434`) que muere al desconectar USB / reiniciar `adbd`. Contradicción con S8 (8081 marcado "riesgo").
**d) Ajuste:** **S1-09b Túnel determinista**: exigir `adb reverse --list` como evidencia pre-test, comando exacto de forward, y re-test tras `adb kill-server/start-server`. Registrar ubicación del script o eliminarlo de criterios.

### CRÍTICA #8 — Severidad: **Alta**
**b) Archivos:** Plan Suite 1 general + `llm/DynamicModelDiscovery.kt:54-63` + `connectors/*/client(): HttpClient(OkHttp)`
**c) Fallo:** Cada `discoverModelsForProvider`, `SupabaseLite.select`, `GmailClient.historyList/searchAll`, `DriveClient.list/get` construyen un **nuevo `HttpClient(OkHttp)` por llamada** y nunca lo cierran. En S1 (9 proveedores × discovery paralelo vía `async` línea 179-184) + S10-02 (50 ejecuciones) habrá **agotamiento de FD/sockets, `Too many open files`, y `ConnectionPool` huérfano**.
**d) Ajuste:** **S1-10 Fuga de clientes HTTP**: `adb shell ls /proc/<pid>/fd | wc -l` + `dumpsys meminfo` antes/después de `discoverAllAvailableModels()` ×5. Assert delta FD ≤20. Si crece sin cota → FAIL y fix a singleton `HttpClient`.

---

## BLOQUE C — SUITE 2: conectores (trampa del "error estructurado = PASS")

### CRÍTICA #9 — Severidad: **Bloqueante**
**b) Archivos:** Plan S2-05/S2-06 vs `connectors/google/GmailClient.kt:36-63` + `DriveClient.kt:56-94`
**c) Fallo / trampa de falso PASS:** `GmailClient.historyList` hace `decodeFromString(HistoryDto, body)` sin chequear status; si token vencido/ausente, Google devuelve JSON de error → `decode` lanza → si el wrapper hace `catch → "OAuth Token Required"` el Plan lo contará como PASS. Eso **premia no tener token**. `DriveClient.list` no usa `isSuccess` (importado pero sin uso). "Sin crash = PASS" es insuficiente.
**d) Ajuste:** Endurecer S2-05/06 a **doble assert**: (1) token inválido dummy → `isError=true` + `401/OAuth` SIN NPE (`PASS_NEGATIVO`, no `PASS_E2E`); (2) con token real (hoy NO hay `google.oauth_token` en secrets) → listar ≥1 mensaje/archivo real con `id` en `ToolCallCard`. Prohibir 100% PASS solo con (1).

### CRÍTICA #10 — Severidad: **Alta**
**b) Archivos:** Plan S2-02/S2-03 vs `connectors/backend/SupabaseLite.kt:22-56` + `api_keys.json: fenix_entregas.* + supabase.*`
**c) Fallo:** Hay **dos raíces Supabase** con URLs/keys distintas. `SupabaseLite.select(baseUrl, anonKey, table...)` no valida `baseUrl` (SSRF si el LLM lo invoca) y `heartbeat()` hardcodea `notification_outbox`. S2-02 dice "invocar `supabase_select`" pero ese tool **no existe registrado** y no especifica tabla/columnas/límite. `limit.coerceIn(1,200)` permite `select=*&limit=200` y quemar egress (free 5GB, pausa 7d).
**d) Ajuste:** **S2-02b Seguridad + costo**: (1) allowlist de `baseUrl` (solo 2 URLs de secrets) + allowlist de tablas; (2) probar `table=inexistente` y `columns=*` → fail cerrado sin volcar `service_role_key`; (3) evidenciar `content-length` y que no se usó `service_role_key` en móvil (solo `anon_key`).

### CRÍTICA #11 — Severidad: **Alta**
**b) Archivos:** Plan S2-04 vs `connectors/github/GitHubConnector.kt` + `api_keys.json: github_token(str) + codex_proxy_pool.github_token`
**c) Fallo:** Hay **dos tokens GitHub**. El Plan dice "repo `fenix-ia-movil-con-mcp-integrado`" sin owner/rama/scopes. Sin definirlo, `github_list_repos` puede devolver 200 vacío (token sin scopes) y parecer PASS.
**d) Ajuste:** **S2-04b Scopes y repo canónico**: pre-check host `curl -H "Authorization: Bearer $TOKEN" .../rate_limit` + `GET /repos/{owner}/{repo}/commits?per_page=1`; assert `sha` real en `ToolCallCard`. Probar token sin scopes → 403 estructurado, no lista vacía.

### CRÍTICA #12 — Severidad: **Media**
**b) Archivos:** Plan S2-08 vs `connectors/social/SocialReadOnly.kt` + `api_keys.json: telegram.*`
**c) Fallo:** `SocialReadOnly` no tiene métodos de red; `telegram_send_message/get_updates` no existen. Si se implementa enviando mensaje real al `chat_id_default` en cada corrida, se spamea el chat productivo. Tampoco se prueba `MAX_PROFILE_VISITS_PER_HOUR=40` ni dwell anti-ban.
**d) Ajuste:** **S2-08b Telegram seguro**: usar chat de QA dedicado (no default productivo), limpiar mensajes de prueba, assert `message_id` + `GET /getUpdates` contiene el texto. Para IG/FB, exigir evidencia de NO llamar a Graph API. Si no hay tool, marcar `NO_IMPLEMENTADO`, no PASS.

### CRÍTICA #13 — Severidad: **Alta**
**b) Archivos:** Plan S2-09 vs `connectors/whatsapp/WhatsAppHybrid.kt` + `services/notifications/McpNotificationListenerService.kt:47-61`
**c) Fallo:** `whatsapp_prepare_message` no existe; el Plan lo "invoca" y verifica `Intent.ACTION_VIEW (https://wa.me/...)`. Pero `IntentTools` permite cualquier URI — riesgo de **inyección `wa.me` con texto arbitrario** sin límite. Leer respuesta vía `NotificationListener` depende del dedup hash que puede tragar la respuesta.
**d) Ajuste:** **S2-09b Límites WhatsApp**: assert intent `https://wa.me/<solo_dígitos>?text=<url_encoded>` con allowlist, NO `dispatchGesture` sobre `com.whatsapp`, y notificación entrante en `daniele_notification_list` <10s. Probar con `com.whatsapp.w4b` ausente → error estructurado, no crash.

---

## BLOQUE D — SUITE 3: QuickJS que no es QuickJS + skills no ejecutables

### CRÍTICA #14 — Severidad: **Bloqueante**
**b) Archivos:** Plan S3-03/S3-05/S3-07 vs `mcp/skills/QuickJsSkillEngine.kt:37-94`
**c) Fallo / trampa mayor de falso PASS:** Se llama `QuickJsSkillEngine` y el Plan cita `cash.breezy.quickjs`, pero la implementación es **`AndroidScriptEvaluator` sobre `WebView.evaluateJavascript` + `new Function('args', code)`** (líneas 49-60), con `Fallback` (líneas 87-93) que **siempre devuelve `{"status":"ok","output":"Ejecutado con éxito"}` sin ejecutar nada** cuando `context==null || Looper.myLooper()==null` o hay excepción. S3-05 dará PASS con fallback aunque el JS sea inválido. `withContext(Dispatchers.Main)` (línea 39) desde Ktor `/mcp` (hilo Netty IO, sin Looper) → cae al fallback falso. S3-07 inútil: `withTimeoutOrNull(5000)` (línea 84) envuelve `await()` pero el `WebView` con `while(true)` nunca resuelve y **nunca se destruye** (destroy solo en callback) → fuga WebView + ANR real.
**d) Ajuste:** Reescribir S3 como **pruebas anti-fallback**:
- S3-05b: conector `suma(a,b)→a+b` con `{a:2,b:3}` assert output contiene `5`, NO solo `"Ejecutado con éxito"`. Repetir desde HTTP `/mcp` y desde chat; si difieren → FAIL.
- S3-07b: `while(true){}` medido con `dumpsys` + ANR + WebView count; assert UI responde (`daniele_wait` <500ms) y no crece instances. Exigir `destroy()` en `finally`/timeout y prohibir `new Function` sin sanitización.
- S3-03b: `schema_json` inválido y `nombre` con `../` o SQL → debe rechazar, no registrar.

### CRÍTICA #15 — Severidad: **Bloqueante**
**b) Archivos:** Plan S3-01/S3-02 vs `skills/SkillCatalog.kt:17-29` + `mcp/tools/FenixTools.kt:156-274`
**c) Fallo:** `SkillCatalog.BUILT_IN` solo tiene 2 `FenixSkill(TEXT)` (`turno-radar`, `zona-sj`) — **descripciones para prompt, no herramientas ejecutables**. No hay binding `skill→tool`. `zone_check` recibe `{direccion: string}` textual, NO coordenadas GPS, contradiciendo S3-02 ("con `-31.5375,-68.5364` + `geoapify_api_key`"). Pasar lat/lng dará `peligrosa:false` siempre → falso PASS "zona segura". `turn_watch` ignora `desde_hora/hasta_hora` y solo hace `scheduler.start()`.
**d) Ajuste:** S3-01b/02b: (1) inputs canónicos: `zone_check{direccion:"..."}` con 3 direcciones (1 peligrosa, 1 segura, 1 ambigua) + assert `hits[].zone_id`; prohibir lat/lng a ese tool; (2) GPS real vía `daniele_get_location` + `ZoneMatcher` por separado; (3) `turn_watch` assert `TurnScheduler.getMode()` + fila en `TurnMetricsStore`, no solo `vigilando:true`.

### CRÍTICA #16 — Severidad: **Alta**
**b) Archivos:** Plan S3-06 vs `mcp/skills/QuickJsSkillEngine.kt:288-293` + `memory/FenixMemoryDb.kt:279-322`
**c) Fallo:** `restoreStoredConnectors()` re-llama a `registerDynamicConnector()` que hace `guardarConector()` (UPDATE con `creado=now`) en cada cold start — **reescribe `creado` en cada reinicio**, destruyendo la evidencia de persistencia que S3-06 quiere verificar. Doble inicialización posible (`FenixDefaultToolInitializer` + `McpServerService.registerFenixBundle`) → doble registro. Race `initialize()` vs primer `listar_conectores`.
**d) Ajuste:** S3-06b: guardar `creado` original antes del force-stop, tras reopen assert mismo `creado` (no `now`) + `count` idéntico + mismo output. Si `creado` cambió → FAIL.

---

## BLOQUE E — SUITE 4: 61 herramientas (conteo, permisos Android 16 y trampas)

### CRÍTICA #17 — Severidad: **Alta**
**b) Archivos:** Plan Suite 4 "61 herramientas" vs `mcp/tools/*.kt` + `McpServerService.kt:459-475`
**c) Fallo:** La taxonomía no cuadra con el registro real y el Plan asume "1 ejecución = cobertura", sin probar **gate de permisos** (`OptionalToolPermissions.effectivePermissions` línea 309-310): si `CAMERA` denegado, ¿registra o falla en runtime? Si registra pero falla, S4-54..56 darán `isError` contado como FAIL cuando es correcto. Si no registra, `executeInProcess` devuelve "no encontrada" que parece FAIL de GAP-01.
**d) Ajuste:** Matriz **S4-00 Permiso × Registro × Ejecución**: para cada tool, documentar `isToolEnabled` + `grantedOptionalPermissions` + `get(name)!=null` antes de ejecutar. Probar cada tool con permiso concedido/denegado y definir `PASS_EJECUTA` vs `PASS_BLOQUEO_CORRECTO`.

### CRÍTICA #18 — Severidad: **Bloqueante**
**b) Archivos:** Plan S4-22/S4-23/S4-53 + `mcp/tools/SystemActionTools.kt` + `AndroidManifest.xml:6-43` + `build.gradle.kts:223-224 (minSdk 33, targetSdk 34, compileSdk 37)` vs SDK 36
**c) Fallo:** Tres permisos imposibles que el Plan no prevé:
- `daniele_get_device_Logs` (logcat): requiere `READ_LOGS` (signature) — third-party solo lee **sus propios logs**.
- `daniele_get_location`: manifest solo `ACCESS_FINE/COARSE` + `FOREGROUND_SERVICE_LOCATION`; **falta `ACCESS_BACKGROUND_LOCATION`** (solo flavor `gms`). En `fossDebug` el geofence background fallará.
- `targetSdk 34` vs SDK 36: cambios `FOREGROUND_SERVICE_SPECIAL_USE`, `QUERY_ALL_PACKAGES`, `READ_MEDIA_VISUAL_USER_SELECTED` (línea 43) → `list_files` parcial.
**d) Ajuste:** **S4-22b/53b Permisos SDK 36**: (1) `adb shell dumpsys package ...foss.debug | grep permission` como evidencia; (2) `get_device_logs` assert SOLO logs propios (`logcat --pid=<pid>`); (3) `get_location` con GPS off / aproximada / denegada → 3 asserts; (4) verificar `shouldShowRequestPermissionRationale` flow.

### CRÍTICA #19 — Severidad: **Alta**
**b) Archivos:** Plan S4-32/S4-33 + `mcp/tools/UtilityTools.kt` (862 líneas) + `mcp/registry/FenixToolRegistry.kt:96-123`
**c) Fallo:** `daniele_wait` siempre retorna éxito tras `delay`, `daniele_retry` puede tragar error original. `isSuccess = result.isError!=true` — si `retry` retorna éxito con `outputText="reintentado 3 veces"` el Plan lo cuenta PASS sin verificar efecto. No se prueba cancelación (`loggedToolHandler` líneas 53-61 no loguea cancelados).
**d) Ajuste:** **S4-32b/33b Semántica**: `wait(2000)` assert `durationMs ∈ [1900,2500]` (no 0ms = mock); `retry` con tool que siempre falla assert `isError=true` + conteo de intentos; probar `job.cancel()` mid-`wait` → sin log fantasma ni `ReferenceCountedToolCallIndicator` colgado.

---

## BLOQUE F — SUITES 5/6: multi-app, APK, OpenAPI y web IA

### CRÍTICA #20 — Severidad: **Bloqueante**
**b) Archivos:** Plan S5-05 vs `AndroidManifest.xml` (sin `REQUEST_INSTALL_PACKAGES`) + `mcp/tools/AppManagementTools.kt` (277 líneas)
**c) Fallo:** S5-05 exige "descargar/instalar app nueva + verificar en `list_installed_apps` + `open_app`". Sin `REQUEST_INSTALL_PACKAGES` (ausente líneas 6-43), `PackageInstaller` lanzará `SecurityException` en Android 16. `QUERY_ALL_PACKAGES` en SDK 36 puede devolver lista filtrada. La prueba pasará si la app ya estaba instalada (caché) y fallará si es nueva, sin distinguir.
**d) Ajuste:** **S5-05b Instalación honesta**: pre-evidencia `pm list packages | grep <paquete>` ausente ANTES, luego install vía `SessionParams` + `ACTION_VIEW` con `FileProvider`, assert `get_app_info` con versión exacta. Si falta permiso, agregarlo o cambiar S5-05 a "solo descubrir e interactuar, sin instalar".

### CRÍTICA #21 — Severidad: **Bloqueante**
**b) Archivos:** Plan S6-01/S6-02 vs `mcp/McpServer.kt:186-196,170` + `mcp/openapi/FenixOpenApiGenerator.kt:15-17`
**c) Fallo doble:** (1) El Plan exige "OpenAPI 3.1.0 con 100% tools en `/tools/{name}`". El código genera **`openapi: 3.0.3`** (línea 17), no 3.1.0, y documenta `/tools/{name}` (líneas 65-77) **sin que exista ninguna ruta Ktor `/tools/{name}`** (solo `/health`, `/openapi.json`, `/s/{token}`, `/mcp`, OAuth). S6-02 dará 404 siempre. (2) `/openapi.json` está en `excludedPaths` (línea 170) → **expuesto sin auth**, filtrando capacidades del dispositivo en red local.
**d) Ajuste:** **S6-01b/02b Contrato real**: (1) validar con `openapi-validator` que el JSON es 3.0.3 (actualizar Plan o código); (2) assert `GET /tools/memory_read → 404` (gap, no PASS); herramientas solo vía `POST /mcp` con Bearer; (3) prueba de seguridad: `GET /openapi.json` sin token = `LEAK` si contiene descripciones sensibles → exigir auth o redacción.

### CRÍTICA #22 — Severidad: **Alta**
**b) Archivos:** Plan S5-01..S5-04/S6-03 vs `services/accessibility/*` (`AccessibilityTreeParser`, `WebViewNodeMerger`, `ScreenStateSnapshotCache`, `CacheInvalidationDebouncer 250ms`) + `mcp/tools/ScreenIntrospectionTools.kt` (418 líneas)
**c) Fallo:** El Plan asume `get_screen_state(compact=true)` + `wait_for_element` + `click_node` iguales en Ajustes, Termux, Navegador, Calculadora. Pero: Termux es `TerminalView` (sin nodos útiles), Navegador requiere `WebViewNodeMerger` + `clearFrameworkNodeCache()` (`McpAccessibilityService.kt:449-451`) o lee DOM stale; `wait_for_element` con debounce 250ms puede invalidar caché durante scroll y devolver `node_id` caducado → tap en coordenadas equivocadas. S6-03 chocará con bot-check/CAPTCHA + IME en `WebView` multilínea.
**d) Ajuste:** **S5/S6 Matriz por app**: evidenciar `get_screen_state` raw vs `compact` (tokens, `CompactUiPruner` 40 max), `wait_for_element` con timeout explícito (10s) + screenshot pre/post tap, y re-resolución de `node_id` tras cada scroll. Termux: assert que NO promete leer sesión `llama-server`, solo `daniele_get_current_app`.

---

## BLOQUE G — SUITE 7: pantallas que no existen

### CRÍTICA #23 — Severidad: **Bloqueante**
**b) Archivos:** Plan Suite 7 (S7-01..S7-10) vs `ui/screens/` reales + `ui/navigation/Routes.kt` (51 líneas)
**c) Fallo:** El Plan nombra pantallas inexistentes y omite las reales:
- Plan: `HomeScreen.kt`, `FenixHubScreen.kt`, `ConnectionScreen.kt`, `ToolPermissionsScreen.kt`, `LocationScreen.kt`, `FileAccessScreen.kt`, `CameraCaptureActivity.kt`, `McpNavHost.kt` — **ninguno existe** (`ls` real: `MainScreen, ServerScreen, ServerTabScreen, ChatScreen, SettingsScreen, LogsScreen, AboutScreen, ApprovalScreen` + `settings/*`).
- Reales no cubiertos: `ServerScreen/ServerTabScreen`, `LogsScreen`, `ApprovalScreen` (OAuth consent), `ShareReceiverActivity`, `settings/*` (11 sub-pantallas).
- `McpNavHost.kt` no existe; navegación es `MainScreen` bottom bar (Chat/Server/Settings/About, `MainScreen.kt:66-90`) + `SettingsRoute`/`ServerRoute`.
- S7-08 "CameraCaptureActivity transparent" es ficción: `find` vacío; `CameraTools.kt:256` llama `cameraProvider.takePhoto()` directo (background camera en Android 16 = `SecurityException`).
**d) Ajuste:** Reescribir Suite 7 como **S7-R Mapa real**: 1 test por archivo real (`ChatScreen`+`ToolCallCard`, `ServerScreen`+`ConnectionInfoCard/ServerStatusCard/ServerLogsSection`, `LogsScreen`, `SettingsIndex`+11 settings, `AboutScreen`, `ApprovalScreen` approve/deny, `ShareReceiverActivity` SEND/SEND_MULTIPLE). Eliminar `CameraCaptureActivity` o crearla. Medir fugas con `dumpsys meminfo` + profiler, no "ausencia visual".

### CRÍTICA #24 — Severidad: **Alta**
**b) Archivos:** Plan S7-10 vs `ui/viewmodels/ChatViewModel.kt:49-60` + `ui/screens/ChatScreen.kt:301-420`
**c) Fallo:** Rotación con `ToolCallCard` expandida + chat activo: `ChatViewModel` guarda `_messages`, `_selectedModel`, `_isGenerating` en `StateFlow` (sobrevive rotación) pero **no usa `SavedStateHandle`** → muerte de proceso (`Don't keep activities`, `force-stop`, `onTaskRemoved` línea 243-246) pierde historial y `isGenerating=true` queda colgado sin timeout/restore. El Plan no prueba process-death, solo rotación.
**d) Ajuste:** **S7-10b Recreación total**: (1) rotación con `isGenerating=true` mid-tool_call → assert no duplica ni deja spinner eterno; (2) `adb shell am kill ...` + restore → assert historial persistido o "sesión perdida" explícita; (3) `ToolCallCard` conserva colapso vía `rememberSaveable`.

---

## BLOQUE H — SUITE 8: botón volumen, modo calle y anti-ban (auto-contradicción HITL)

### CRÍTICA #25 — Severidad: **Alta**
**b) Archivos:** Plan S8-01 vs `services/accessibility/McpAccessibilityService.kt:351-371`
**c) Fallo:** Lógica depende de `ACTION_DOWN` repetidos con `eventTime-downTime>=550` (línea 358). En Motorola el volumen envía un solo DOWN + repeats que pueden no llegar a tiempo con `notificationTimeout=100ms`. `return true` en UP tras hold (línea 366) → el sistema nunca ve el UP → slider "pegado". El Plan no instrumenta cómo medir 550ms.
**d) Ajuste:** **S8-01b Instrumentado**: loggear `eventTime-downTime` vía `adb logcat -s MCP:AccessibilityService`, 10 cortas (<300ms) assert volumen ±1 sin trigger, 10 largas (>700ms) assert trigger + volumen intacto. Probar con pantalla apagada y con música.

### CRÍTICA #26 — Severidad: **Bloqueante**
**b) Archivos:** Plan S8-02 vs `security/StreetImmunityChecker.kt:18-21,49-69` + S1-08/S6
**c) Fallo / auto-contradicción:** `WORKSHOP_PORTS = [8022, 8080, 8081, 9222]` (línea 19). Pero el QA exige `McpServer` en `8080` (S6-01) + `llama-server` en `8081` (S1-08) → `auditStreetImmunity().isFullyImmune=false` **siempre durante el QA**. S8-02 nunca dará PASS si S1/S6 corren. Además `isFirewallActive=true` hardcodeado (línea 67) → falso PASS; `Settings.Global.ADB_ENABLED` deprecado en API 33+ (siempre 0 en Android 16) → falso PASS de seguridad.
**d) Ajuste:** **S8-02b Coherencia calle vs taller**: definir `MODO_TALLER_QA` (8080/8081 abiertos) vs `MODO_CALLE` (todo cerrado + ADB off + dev off). Probar transición: `stop McpServer + kill llama-server` → `isFullyImmune==true`. Exigir verificación real del firewall (bloqueo `dispatchGesture`/`click` si `currentPackage in DELIVERY_PACKAGES`), no booleano fijo. Evidencia `dumpsys deviceidle whitelist` + `appops get ... RUN_ANY_IN_BACKGROUND`.

### CRÍTICA #27 — Severidad: **Alta**
**b) Archivos:** Plan S8-03 vs `services/accessibility/ActionExecutor*`, `mcp/tools/*`, `delivery/monitor/DeliveryMonitor`
**c) Fallo:** Sin `AccessibilityFirewall` que filtre `packageName` antes de `dispatchGesture/click`. `DeliveryMonitor.onContentChanged(pkg, root)` (línea 130) recibe árbol completo de cualquier app — si PedidosYa está en foreground, se parsea. ¿Es "lectura intrusiva"? El Plan dice "solo notificaciones" pero el servicio escucha `TYPE_WINDOW_CONTENT_CHANGED` de PedidosYa. Sin denylist explícita, S8-03 no es automatizable.
**d) Ajuste:** **S8-03b Firewall verificable**: instrumentar `ActionExecutor` para loggear `packageName` destino; test negativo: `daniele_tap` con PedidosYa en foreground → **bloqueado `isError=true` + `ANTI_BAN_BLOCKED`**. Revisar APK con `apktool`/`aapt dump badging`. `NotificationListener` solo lee `StatusBarNotification` para delivery apps.

---

## BLOQUE I — SUITE 9: privacidad (sanitizador no conectado + regex incompletos)

### CRÍTICA #28 — Severidad: **Bloqueante**
**b) Archivos:** Plan S9-01/S9-02 vs `privacy/LocalNerSanitizer.kt:15-21` + `llm/FenixLlmClient.kt` (0 llamadas) + `ui/viewmodels/ChatViewModel.kt` (0 llamadas)
**c) Fallo / falso PASS garantizado:** `grep sanitize` en `llm/` + `ChatViewModel` = **0 resultados**. `LocalNerSanitizer.sanitize()` existe pero **nadie lo llama** en el path chat→LLM. S9 fallará siempre (el secret viaja en claro) salvo confusión con `PrivacyPipeline/PrivacyToolGate` (solo para tools). Marcadores no coinciden: código `[REDACTED_API_KEY]/[REDACTED_IMEI]/...`, Plan espera `[API_KEY_REDACTED]/[JWT_REDACTED]` → assert fallará aunque funcione.
**d) Ajuste:** **S9-00 Cableado**: antes de S9, assert `grep -rn LocalNerSanitizer llm/ ui/viewmodels/` no vacío + test de red (mock `HttpClient`) que el `POST /chat/completions` NO contiene `sk-.../gsk_...`. Actualizar marcadores del Plan o del código. Definir dónde se restauran placeholders (`PlaceholderSubstitutor` solo en tools vía `McpServerService:177`).

### CRÍTICA #29 — Severidad: **Alta**
**b) Archivos:** Plan S9-01/S9-02 vs `privacy/LocalNerSanitizer.kt:15-21,73-87`
**c) Fallo:** Regex incompletos para Argentina + Plan:
- `API_KEY_REGEX` cubre `sk-`, `gsk_`, `AIza`, `Bearer` pero **NO `ghp_`, NO `eyJ`, NO `sambanova_*`, NO `cerebras_*`, NO `mistral_*`, NO `cohere_*`**. S9-01 exige `ghp_...` y `eyJ...` → fuga.
- `HARDWARE_SERIAL_REGEX` solo conoce 2 seriales + heurístico `[A-Z0-9]{12,18}` que fuga o sobrerredacta.
- Sin regex DNI argentino, CBU 22 dígitos, teléfono `+54`, CUIT/CUIL. `CREDIT_CARD_REGEX` + Luhn rechazará tarjetas de prueba inválidas → falso "no redacta".
**d) Ajuste:** **S9-02b Corpus argentino**: DNI 7/8 dígitos, CBU 22 dígitos válido, alias CBU, `+54 9 264 ...`, `CUIT 20-...`, email, tarjeta Luhn-válida `4242...`, `ghp_...`, `eyJ...`, `gsk_...`. Assert por patrón individual. Medir falsos positivos.

---

## BLOQUE J — SUITE 10: caos, concurrencia y térmicas (umbrales inventados)

### CRÍTICA #30 — Severidad: **Alta**
**b) Archivos:** Plan S10-01 vs `services/mcp/McpServerService.kt:243-246`, `services/accessibility/*`, Android 16 límites FGS
**c) Fallo:** `monkey -p ... 2500 --pct-appswitch 5` **sacará la app de foreground** y puede matar el foreground service o desactivar accesibilidad vía Ajustes. Sin **restauración de estado + re-verificación `isReady()`** tras monkey, S10-02/03 parten de estado roto. `--pct-touch 45` sobre `ChatScreen` enviará basura al LLM (quema cuota/rate-limit → envenena S1).
**d) Ajuste:** **S10-01b Monkey confinado**: primero `monkey --pct-appswitch 0` (solo in-app) + segunda pasada con appswitch pero con snapshot/restore de `enabled_accessibility_services` y `notification_listeners`. Filtrar `logcat -b crash` + `dumpsys activity services` post-monkey. Desconectar LLM (avión + solo local) durante monkey.

### CRÍTICA #31 — Severidad: **Bloqueante**
**b) Archivos:** Plan S10-02 ("50 invocaciones, `<1ms`, `Locked=0`") vs `memory/FenixMemoryDb.kt:109-134`, `mcp/registry/FenixToolRegistry.kt:42-61`
**c) Fallo:** `<1ms` es para dispatch vacío, no para `memory_save` (3 I/O: `update`+`insert`+`historial`, `Instant.now()`, WAL `synchronous=NORMAL`). Medir 50× `memory_save` exigiendo `<1ms` **fallará siempre** en Moto G77. `kvGuardar` sin `beginTransaction` → `SQLiteDatabaseLockedException` intermitente bajo 50 concurrentes. `clear()` sin lock puede borrar mid-ejecución (race con `executeInProcess`).
**d) Ajuste:** **S10-02b Concurrencia honesta**: (1) `executeInProcess(listar_conectores vacía)` 50× → p50 `<5ms`; (2) `memory_save` 50× claves únicas → 50 éxitos + 0 `Locked` + `kvLeer` verifica 50 filas; (3) `clear()` durante ejecución → no crash (o prohibir en prod). Usar `runBlocking + async(50)` + `measureTimeMillis`.

### CRÍTICA #32 — Severidad: **Media**
**b) Archivos:** Plan S10-03 (`<250MB PSS`, `<38.0°C`) vs Netty + WebView + `llama-server` en Termux
**c) Fallo:** `<250MB PSS` con Ktor-Netty + Hilt + Compose + WebView + WAL es optimista; con `llama-server` en paralelo (misma RAM 8GB+ZRAM) la presión es conjunta. `<38.0°C` depende de ambiente/carga/5G; en San Juan a 35°C ambiente + 5G + cámara, se supera sin bug. Sin condiciones fijas (brillo, red, carga, `thermalservice`).
**d) Ajuste:** **S10-03b Condiciones controladas**: `brillo 50%, WiFi, sin carga, temp ambiente`, `dumpsys meminfo --local` PSS + `dumpsys battery` + `/sys/class/thermal/thermal_zone*/temp` antes/después, con y sin `llama-server`. PASS como `delta PSS <50MB` (no absoluto) y `temp <42°C` o trend estable.

---

## BLOQUE K — SUITE 11/12 + transversales faltantes

### CRÍTICA #33 — Severidad: **Alta**
**b) Archivos:** Plan S11-01/S11-02 vs `build.gradle.kts:352-363`, `llm/DynamicModelDiscovery.kt:65-105`
**c) Fallo:** "Costo $0 / 100% libre" ignora: (1) `gmsImplementation(play-services-location)` — en `foss` se excluye pero el Plan no verifica que el APK no contenga `com.google.android.gms` (`apkanalyzer dex`); (2) `TunnelManager` + `CloudflaredBinaryResolver` descargan binario `cloudflared` (egress + nativo no auditado); (3) "Cero hardcodeo" falso: `standardProviders` hardcodea 7 `baseUrl`, `heartbeat` hardcodea `notification_outbox`, `CALLBACK_URL` hardcodea `127.0.0.1:3000`, `WORKSHOP_PORTS` hardcodea puertos, 41 zonas SJ hardcodeadas.
**d) Ajuste:** **S11-01b/02b Auditoría binaria**: `apkanalyzer` + `grep gms/`, `strings` buscando URLs, tabla `hardcodeado por diseño vs dinámico`. Criterio: "cero hardcodeo de *modelos/datos de usuario*", no absoluto.

### CRÍTICA #34 — Severidad: **Bloqueante**
**b) Archivos:** Plan Suite 12 + `ui/viewmodels/ChatViewModel.kt:169-274` + `mcp/registry/FenixToolRegistry.kt:96-123`
**c) Fallo / trampa "100% PASS":** `executeConversationTurn` solo hace **un ciclo tool→followUp** (`includeTools=false` línea 228). Si el LLM pide 2 tools secuenciales (`list_files` → `read_file`), el segundo nunca se ejecuta; parecerá PASS parcial. `executeInProcess` devuelve `isError=true` con texto (líneas 108-123) que el LLM resume como "éxito parcial" y `ToolCallCard` (`ChatScreen.kt:420`) puede mostrar verde. Sin loop multi-turn + assert `isError`, el "100% PASS" es cosmético.
**d) Ajuste:** **S12-01 Loop multi-tool**: prompt de 2-3 hops (`list_storage → list_files → read_file`) assert N `ToolCallUi` con `isSuccess=true` c/u + cita del archivo. `PASS` = `isError==false`, no "texto plausible". Agregar columna `isError` a Matriz.

### CRÍTICA #35 — Severidad: **Alta**
**b) Archivos:** Plan (ausente) vs `mcp/tools/FileTools.kt`, `mcp/tools/IntentTools.kt` (331 líneas), `services/storage/*`, `McpServer.kt:204-212 (/s/{token})`
**c) Fallo:** Cero pruebas negativas/destructivas:
- **Path traversal**: `write/read/copy` con `../../`, symlink, `/data/data/...`, `content://` — ¿`StorageLocationProvider`+`PermissionChecker` bloquean?
- **SSRF/intent**: `open_uri` con `file://`, `intent://`, `http://169.254.169.254`, `tel:` USSD (`*#06#`), `geo:` falsas — ¿allowlist? (`GoogleOAuth.ALLOWLIST` no aplica a tools).
- **Inputs corruptos**: `schema_json` no-JSON, `codigo_js` con `fetch()`, `arguments=null` (línea 192 pasa `null` si parse falla).
- **Red a mitad de tool_call**: matar WiFi durante `supabase_select` (20s timeout) → ¿cuelga UI? (`_isGenerating` true 45s sin cancel).
- **Token vencido / 429 mid-chat**: `ChatViewModel` rota a "primer otro proveedor" (línea 249) sin respetar cooldown → ping-pong entre 2 en 429.
**d) Ajuste:** Nueva **SUITE 13 Destructiva** (mínimo 10 casos): traversal, SSRF, intent maligno, JS exfiltrante, JSON roto, red cortada a los 2s (`svc wifi disable` mid-call), 401, 429 simultáneo, `fileSizeLimitMb` excedido en `/s/{token}`, token expirado → 404. Cada uno `isError=true` + 0 crash + 0 secret en `serverLog`.

### CRÍTICA #36 — Severidad: **Media**
**b) Archivos:** Plan Suite 12 ("recompilar `:app:assembleFossDebug`, reinstalar, re-test") vs `BootCompletedReceiver`, `PackageReplacedReceiver`, `McpServerService.onDestroy`, `FenixMemoryDb.DB_VERSION=2`
**c) Fallo:** `adb install -r` + `force-stop` invalida S3-06, S10-03 y cooldowns, y puede bump `DB_VERSION` sin migración (`onUpgrade` llama `onCreate` con `IF NOT EXISTS` — no migra). Cada re-test parte de estado distinto; el "100% PASS final" no es comparable.
**d) Ajuste:** **S12-02 Trazabilidad de re-test**: antes de cada reinstall, backup `fenix_memory.db` + `fenix_secure_vault` + `dumpsys` snapshot; tras reinstall, assert `kvLeer` + `listarConectores` + `listConfiguredProviders` idénticos (o wipe documentado). Versionar cada APK (`versionName` + `git sha`) en la Matriz.

---

## RESUMEN EJECUTIVO PARA REAJUSTE DEL PLAN

1. **Reescribir Suite 2 y 3**: 6/9 conectores y 2/2 skills son no-ejecutables; usar `PASS_NEGATIVO` vs `NO_IMPLEMENTADO` vs `PASS_E2E`, prohibir contar `isError` como éxito.
2. **Prohibir fallbacks silenciosos**: `evaluateFallback`, `getCachedModels` en 429, `parseModelsResponse catch→emptyList`, `executeInProcess catch→isError` deben etiquetarse `FALLBACK/MOCK`.
3. **Fijar contrato HTTP real**: OpenAPI 3.0.3 (no 3.1.0), sin `/tools/{name}`, `/openapi.json` sin auth = leak a decidir.
4. **Corregir pantallas**: agregar las 11 de `settings/` + `Logs/Approval/ShareReceiver`; eliminar `CameraCaptureActivity` o crearla (bloquea cámara background en SDK 36).
5. **Desdoblar S8**: modo taller (8080/8081 abiertos) vs modo calle (todo cerrado); arreglar `StreetImmunityChecker` (puertos + `ADB_ENABLED` + firewall hardcodeado).
6. **Conectar `LocalNerSanitizer` al path LLM** antes de S9 y ampliar regex (ghp, eyJ, DNI, CBU 22, +54); alinear marcadores.
7. **Endurecer S10**: monkey confinado, concurrencia SQLite con transacciones, umbrales relativos.
8. **Agregar Suite 13 destructiva/negativa** (traversal, SSRF, intents, JS maligno, red cortada, 401/429, cuota, capability-links).
9. **Definir PASS formal**: `isError==false` + evidencia (`ToolCallCard` + `serverLog TOOL_CALL` + `logcat` + screenshot + `dumpsys`) por cada ID S1-01..S11-02, sin "texto plausible".
