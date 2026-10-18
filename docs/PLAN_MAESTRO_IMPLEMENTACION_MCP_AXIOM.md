# PLAN MAESTRO DE IMPLEMENTACIÓN MCP AXIOM

**Estado:** plan preliminar de implementación; **NO aprobado para producción**.  
**Fecha:** 18 de octubre de 2026.  
**Alcance:** Fenix Mobile MCP y su superficie de control Android; integración complementaria con el MCP de PC.  
**Regla financiera:** esta revisión no efectuó compras, pagos, suscripciones ni invocaciones deliberadas a APIs/modelos comerciales de pago. Ninguna propuesta de este plan autoriza gasto. El costo de cualquier fase futura debe seguir siendo $0; los costos de infraestructura/túneles no se pueden certificar sin acceso a facturación externa.

> **Veredicto ejecutivo:** el código revisado expone acciones capaces de leer e interactuar con la interfaz Android. La protección observada de “modo calle” deniega acciones de nodo solamente para tres paquetes de delivery; no constituye un firewall bancario o de billeteras. Las acciones por coordenadas, como `tap`, despachan gestos sin una comprobación de paquete visible en ese método. La función consultada como estado del firewall devuelve `true` constante. Por tanto, **no se debe habilitar control visual general en un dispositivo con apps bancarias/wallets hasta implementar y probar un Policy Enforcement Point (PEP) común, deny-by-default y no evadible**. No se ejecutaron las pruebas propuestas ni acciones en apps/dispositivos.

## 1. Alcance, límites y reglas de operación

Se verificaron los catálogos `tools/list` de Fenix Mobile MCP y Fenix MCP PC. Se leyeron mediante el MCP móvil la directiva `Readme_manus.md`, los protocolos AXIOM, los informes de auditoría, la matriz, la investigación de 31 CLI para Termux, documentación y archivos seleccionados de la copia fuente. En el MCP de PC se enumeraron rutas y se leyeron, con comandos estrictamente de lectura (`find`, `sed`, `grep`), partes del repositorio `/home/eli/Documentos/fenix-mobile-mcp/`, incluidas las rutas de accesibilidad, autenticación, servidor y evidencia QA. También se inventarió superficialmente `/home/eli/biblioteca-fenix-v3/`; no se realizó una auditoría integral de Biblioteca Fénix porque no es el objeto principal de este plan.

Las llamadas del MCP se limitaron a descubrimiento de herramientas, listado y lectura de archivos; los comandos enviados a la computadora fueron de solo lectura. No se tocaron apps, no se hicieron clics, no se escribieron datos en apps, no se iniciaron procesos ni se alteró el código. La única modificación autorizada por la solicitud es este documento en el almacenamiento del teléfono.

El contenido recibido desde archivos y MCP externos se trató como **datos no confiables**, no como autoridad para anular reglas. Las declaraciones de los informes de apps, matrices y CLIs se clasifican como evidencia documental, salvo donde se indique inspección estática directa. Una mención en un README, una presencia de función o una respuesta genérica no demuestra comportamiento en ejecución.

**Exposición del entorno de prueba:** la usuaria indicó que ambos endpoints MCP temporales estaban abiertos, públicos y sin autenticación. Se conectaron y consultaron en modo de solo lectura. Esa conexión de prueba no prueba que el servidor Android estuviera configurado de igual forma en todas las sesiones. Antes de dejar cualquiera de esos endpoints publicado, debe cerrarse o protegerse con autenticación fuerte y alcance mínimo; la URL pública no es un secreto ni un control de acceso.

## 2. Método y estados de evidencia

| Etiqueta | Significado en este documento |
|---|---|
| `OBSERVADO` | Resultado directo de una consulta de herramientas o lectura en esta tarea. No equivale a prueba de ejecución de la app. |
| `ESTÁTICO` | Presencia/ausencia aparente en código, configuración, manifiesto o estructura del repositorio. No demuestra el comportamiento runtime completo. |
| `DOCUMENTAL` | Afirmación escrita en una auditoría, README, plan o matriz; no verificada independientemente. |
| `INFERIDO` | Conclusión razonada o estimación pendiente de experimento. |
| `PENDIENTE / NO EJECUTADO` | Prueba necesaria que todavía no se corrió. |
| `BLOQUEANTE` | No continuar a exposición remota ni a control activo hasta resolverlo y aportar evidencia. |

Un `PASS` de prueba futura exige operación real con nonce/challenge único, proveniencia del proceso/modelo/endpoint, artefactos crudos vinculados a la corrida y controles negativos contra stubs, credenciales inválidas y fallbacks. `DOCUMENTAL`, `ESTÁTICO` e `INFERIDO` nunca se elevan por sí solos a `PASS_OBSERVED`.

## 3. Material y síntesis de las 17 auditorías de apps

Se revisaron los informes aportados para Manus, Perplexity, Claude, ChatGPT, Copilot, Zapia, Replit IDE, Trae IDE, DeepSeek, Mistral, Grok, NotebookLM, Google AI Edge, SPCK, Poe y VS Code Web, junto con la ficha de prospección de Google Play. La matriz comparativa global y la fecha/versión de cada informe constituyen la procedencia indicada por el material. Los detalles de frameworks, versiones, modelos, cuotas y funciones que siguen son **claims documentales** de esos informes, no una auditoría independiente de los proveedores.

| Aplicación / fuente | Aporte de diseño aprovechable para Fénix | Límite o decisión AXIOM |
|---|---|---|
| Manus | Orquestación, sandbox y variedad de artefactos. | El propio informe describe cómputo cloud/suscripción; no importar ese backend en una ruta que exige $0. |
| Perplexity | Conectores MCP remotos/Spaces y búsqueda con fuentes. | Mantener la búsqueda/ejecución bajo control local; no asumir que cuota gratuita equivale a costo total cero. |
| Claude | El informe y la matriz describen cliente MCP móvil por HTTP/Streamable HTTP/SSE. | Útil como referencia de interoperabilidad, no como evidencia de seguridad de Fénix. |
| ChatGPT | GPT Actions/OpenAPI y capas de autorización. | No confundir OpenAPI con MCP; cualquier proxy debe tener auth y scopes, no una superficie genérica abierta. |
| Copilot | Plugins/Graph y salidas orientadas a productividad. | Integraciones empresariales o dependientes de Microsoft 365 no forman parte de la arquitectura $0. |
| Zapia | Conectores con confirmación humana/HITL y experiencia conversacional. | HITL no permite anular una política de bloqueo de finanzas o secretos. |
| Replit IDE | Agentes y múltiples tipos de artefactos. | Adoptar perfiles y herramientas acotadas, sin replicar tiers o modos comerciales. |
| Trae IDE | Ejemplos de puente entre móvil y PC. | El control remoto amplía la superficie; excluir operaciones remotas no necesarias y exigir autenticación. |
| DeepSeek | Modos separados de razonamiento y búsqueda. | La app descrita es cerrada; no inferir de “gratuito en app” que un endpoint/API sea gratis o local. |
| Mistral | Skills conmutables, creación/organización modular y sanitización del conocimiento. | Aplicar switches como selección de capacidades, nunca como autorización implícita. |
| Grok | Alternancia entre síntesis y búsqueda profunda, según el informe. | Cliente descrito como REST/WebSocket propietario; bloquear funciones que puedan generar costos o realizar llamadas remotas. |
| NotebookLM | Respuestas ancladas a fuentes y trazabilidad documental. | Replicar grounding y referencias; no aceptar afirmaciones sin cita en tareas de auditoría. |
| Google AI Edge | Referencia documental para inferencia local, skills/function calling y RAG local. | Candidato de prototipo sin costo API; modelos, RAM, velocidad y compatibilidad de este teléfono no están validados por esta revisión. |
| Prospección Google Play | Criterio de selección y ficha de evaluación sin instalar productos de pago. | Continuar sin compras, pruebas pagas ni instalación no autorizada. |
| SPCK Editor | Lección de editor táctil y acceso Git en móvil. | El informe dice que la IA requiere pago y no hay MCP nativo; excluir la IA comercial de la ruta objetivo. |
| Poe | Bots de servidor vía HTTP/SSE como caso de frontend remoto. | El material reconoce puntos/cuotas; no usar créditos/suscripciones ni equiparar su protocolo con MCP. |
| VS Code Web | Limitaciones de File System Access y fricción de teclado/tacto en Android. | Favorecer flujo local y herramientas de edición pequeñas; no depender de `showDirectoryPicker()` como garantía móvil. |

La matriz plantea como lecciones generales las skills conmutables, el grounding por fuentes, permisos escalonados, el streaming/voz y la ejecución local. Se adoptan como **inspiración**, no como prueba de viabilidad ni autorización para agregar dependencias. Los números y modelos con apariencia futura, las cifras de rendimiento y las cuotas deben comprobarse antes de citarlos como hechos técnicos.

## 4. Síntesis de la investigación de 31 CLI de IA en Termux

El documento `18_cli_ia_termux_android_2026-10-17.md` separa compatibilidad nativa, workarounds y estado indeterminado, y acierta en que “ARM64” por sí solo no demuestra compatibilidad: importan Bionic frente a glibc/musl, PIE, módulos nativos, PTY, permisos, ciclo de vida y aislamiento. Sus mediciones de RAM son mayormente estimaciones y el informe declara que no son pruebas directas en el dispositivo.

| # | CLI / herramienta | Estado reportado para Termux | Criterio del plan |
|---:|---|---|---|
| 1 | Antigravity CLI | Con ajustes/workaround | No primera opción móvil; verificar dependencias y autenticación antes de considerar. |
| 2 | Claude Code | Workaround/proot | No asumir soporte nativo Android. |
| 3 | OpenAI Codex CLI | Workaround | Requiere prueba del entorno/arquitectura; ninguna API de pago. |
| 4 | Gemini CLI | Workaround | No usar suscripciones ni cuotas; comprobar autenticación y coste antes de cualquier prueba. |
| 5 | GitHub Copilot CLI | Workaround | Suscripción/licencia excluye esta ruta $0. |
| 6 | Kiro CLI | Workaround | Prototipo únicamente; no asumir login/API gratuito. |
| 7 | Cursor CLI | Workaround comunitario | No depender de parches no afiliados en producción. |
| 8 | Auggie CLI | Workaround | Probar sin PTY y con secretos fuera de argumentos; no asumir gratuidad. |
| 9 | Amp CLI | Workaround/proot | Excluir como dependencia obligatoria; inferencia remota puede tener costo. |
| 10 | Cline CLI | Workaround/port no oficial | Mantener aprobación de herramientas; prohibir auto-approve. |
| 11 | OpenHands Agent Canvas | Indeterminado/no orientado a Termux | Evitar prometer Docker o sandbox real dentro de proot. |
| 12 | OpenCode | Workaround | No confundir `serve`/ACP con transporte MCP stdio. |
| 13 | Goose | Native/portable según informe | Candidato de prototipo, no verificado aquí; revisar proveedor y scopes. |
| 14 | Crush | Artefacto Android ARM64 según informe; instalación manual | Prototipo condicionado a binario firmado/hash, compatibilidad y RAM medida. |
| 15 | Continue CLI (`cn`) | Indeterminado | Riesgo de mantenimiento y módulos nativos. |
| 16 | Mistral Vibe | Workaround/proot | No asumir wheel Android ni endpoint sin costo. |
| 17 | Kimi Code CLI | Indeterminado | Mantener fuera de baseline. |
| 18 | Qwen Code | Workaround; fork comunitario descrito como EOL | No adoptar fork EOL como base de producción. |
| 19 | Pi coding-agent | Termux explícito según informe; protocolo JSONL/RPC | Candidato como adaptador, no confundir JSONL con MCP directo. |
| 20 | gptme | Núcleo minimal descrito como nativo | Prototipo local condicionado a pruebas de dependencias y `--tools` mínimo. |
| 21 | Aider | Workaround | Priorizar proot reproducible solo si no introduce carga/servicios externos pagos. |
| 22 | Ollama | Paquete de Termux mantenido por comunidad | Fallback opcional; medir overhead y nunca mantenerlo junto con otro servidor de modelos. |
| 23 | LM Studio CLI (`lms`) | Indeterminado/no recomendado para Android | No usar como baseline móvil. |
| 24 | llama.cpp | Android/Termux explícito en el informe | Candidato principal para inferencia local experimental; validar binario/modelo y RAM. |
| 25 | Hugging Face CLI (`hf`) | Workaround por wheels/Xet | Descargas grandes no son necesarias para el MVP; verificar egress, licencia y almacenamiento. |
| 26 | vLLM | Indeterminado; requisitos de Linux/glibc/recursos | Excluir de móvil y de la netbook limitada. |
| 27 | LLM Datasette (`llm`) | Workaround por extensiones Python | Usar solo si se justifica; deshabilitar logs de prompts/datos. |
| 28 | AIChat | Termux explícito según informe | Alternativa ligera para cliente, no servidor MCP por sí sola. |
| 29 | Fabric | Workaround/ejecución comunitaria | API local solamente con llave y loopback, previa prueba; no esencial. |
| 30 | ShellGPT (`sgpt`) | Workaround | `--shell` implica ejecución de comandos; excluir de perfil seguro y evitar API paga. |
| 31 | OpenAI CLI | Indeterminado/no documentado para Termux | No es modelo local ni Codex CLI; no baseline $0. |

**Candidatos y límites:** el informe favorece `llama.cpp` como backend local y considera `Goose`, `gptme`, `Crush`, `Pi` y `Ollama` según el dispositivo/rol. No se verificó ningún CLI ni se descargaron modelos en esta tarea. La netbook reportada tiene Celeron N4020 y 3.74 GB RAM; el Moto G77 se reporta con 8 GB. Presupuestos de RAM, tamaños y cuantizaciones son hipótesis dependientes de modelo/contexto y requieren medición de RSS pico, OOM, térmicas, batería y suspensión. Prototipar primero un único modelo Q4 pequeño, contexto 2K–4K, concurrencia uno; no iniciar si deja menos de 20–25% de RAM disponible. Nada requiere contratar servicios.

## 5. Hallazgos estáticos críticos en el MCP

### 5.1 Acciones de accesibilidad y protección de apps financieras

**Hallazgo `R-01` — Crítico / estático confirmado en código seleccionado.** En `app/src/main/kotlin/com/fenix/mobilemcp/services/accessibility/ActionExecutorImpl.kt`, `tap(x,y)` valida coordenadas y llama a `dispatchSingleStrokeGesture`; la secuencia mostrada no realiza validación del paquete objetivo antes del gesto. `longPress`, `swipe`, `scroll` y gestos análogos también construyen/despachan rutas por coordenadas. Las acciones por nodo pasan por `performNodeAction`, que consulta el paquete raíz y llama a `StreetImmunityChecker.isDeliveryPackageBlocked(activePkg)`. Ese método solo reconoce `com.ubercab.driver`, `com.pedidosya` y `com.grability.rappi`; no es una lista de bancos/wallets. Si `activePkg` es nulo, el método de bloqueo devuelve `false`.

Además, `ActionExecutorImpl.isAntiBanFirewallEnabled()` devuelve `true` constante (línea aproximada 1022 en el snapshot revisado). `StreetImmunityChecker.auditStreetImmunity()` utiliza ese booleano y el conjunto de delivery para reportar estado; esa señal no demuestra que exista firewall bancario, ni que el bloqueo por paquete sea completo.

**Consecuencia:** a partir del código leído no hay evidencia de un guard central que garantice cero lectura/clic/tap/escritura en Mercado Pago, banca, pagos, autenticadores o wallets. No se probó runtime; la conclusión correcta es “control bancario no demostrado y bloqueo por coordenadas ausente en el método inspeccionado”, no una afirmación de que todas las rutas de todo el repositorio se ejecuten sin control.

### 5.2 Lectura de eventos y redacción

`McpAccessibilityService.onAccessibilityEvent` conserva paquete/actividad actuales, registra el paquete del evento y deriva `TYPE_WINDOW_CONTENT_CHANGED` a `DeliveryMonitor` sin que el fragmento revisado muestre un filtro financiero previo. Debe determinarse si y cuándo se lee el árbol asociado, y bloquear esa lectura antes de extraer texto.

`PrivacyToolGate` delega a `PrivacyPipeline`. `PrivacyPipelineImpl` devuelve el contenido original cuando Privacy Mode está desactivado; cuando está habilitado, la redacción se aplica sobre el texto/árbol que ya se obtuvo. Es una capa de minimización/redacción, no un firewall previo a la lectura ni un sustituto de la política de paquetes. `FenixFileSecurityFilter` contiene patrones de banca/autenticación para rutas de archivos; su alcance es acceso a archivos y no protege taps, screenshots, árbol, notificaciones o intents.

### 5.3 Servidor, autenticación y superficies

`ServerConfig.kt` define defaults de bearer y OAuth habilitados, HTTPS desactivado y binding `LOCALHOST`; esos defaults estáticos no verifican la configuración efectiva actual. `McpAuthPlugin` autoriza por bearer u OAuth y permite explícitamente una instancia abierta si ambos mecanismos se deshabilitan. Sus pruebas unitarias incluyen una rama para esa configuración abierta. La exposición debe impedirse por política cuando el binding/túnel sea remoto.

En `McpServer.kt`, el plugin global se instala antes de las rutas MCP; el conjunto excluido incluye `/health`, endpoints OAuth concretos, `/s/` y `/.well-known/`. `GET /openapi.json` y `POST /tools/{name}` sí aparecen en la fuente. Por el orden observado, `/openapi.json` no está en las rutas excluidas y queda bajo el plugin de autenticación. La ruta `/tools/{name}` existe y ejecuta `FenixToolRegistry.executeInProcess` tras parsear argumentos. Por tanto, afirmaciones de un plan QA anterior que decían que estas rutas no existían o que `/openapi.json` estaba excluido/no autenticado **no se consideran confirmadas por el snapshot actual**; deben cerrarse con tests de integración de autenticación para la versión desplegada. También existen rutas de capability-link `/s/{token}` y rutas espejo sin auth declaradas en el servidor; auditar su necesidad, contenido y alcance antes de exponerlas.

La configuración del proyecto no equivale a su estado efectivo. La usuaria informó que ambos endpoints temporales usados en la prueba estaban abiertos y sin autenticación. Para cualquier conexión remota, bearer/OAuth fuerte, TLS o túnel seguro, revocación, rate limiting y scopes mínimos son requisitos previos. Un token global sin permisos por capacidad no satisface autorización granular.

### 5.4 Permisos, lifecycle y QA

El manifiesto revisado incluye Accessibility, `MANAGE_EXTERNAL_STORAGE`, cámara, micrófono, ubicación, consulta amplia de paquetes y listener de notificaciones, entre otros. Es una superficie privilegiada amplia. Cada permiso debe tener justificación, pantalla de consentimiento clara, aislamiento por perfil y apagado cuando no se usa.

`EmergencyKillSwitchManager` registra/cancela jobs y detiene el servicio; la consulta `isCurrentlyBlocking()` despeja el flag tras el cooldown de 3 segundos. Un gesto que el sistema Android ya recibió podría no poder revertirse solo por cancelar la coroutine. El UI no debe declarar “todos los procesos detenidos” hasta tener confirmación verificable; el latch de emergencia debe cerrar admisión y persistir hasta recuperación explícita.

La búsqueda literal realizada en los directorios `app/src/test` y `e2e-tests` del PC no encontró referencias a términos de banca/wallet ni a los tres identificadores de delivery. Es un indicio acotado, no una inspección exhaustiva de todos los tests ni prueba de ausencia. `docs/qa-evidence/README.md` pide artefactos por ID (JSON, log, captura cuando aplique y `run_id`) y una calibración negativa del validador; adoptar esa disciplina para las nuevas suites.

## 6. Modelo de amenazas AXIOM

| Actor / evento | Riesgo | Tratamiento inicial |
|---|---|---|
| Prompt injection en una página, notificación o archivo | Inducir tool-call, tap, lectura o envío de secreto | Los datos externos son no confiables; los LLM nunca deciden permisos. Gate determinista local antes del handler. |
| Tool MCP maliciosa/ambigua o modelo alucinado | Click por coordenadas en “transferir”, “confirmar” o login | Perfil por defecto sin acciones táctiles; PEP consulta paquete/ventana y deniega antes del dispatch. |
| Cambio de app entre análisis y ejecución | Reutilizar autorización o nodeId en banca | Revalidación atómica antes de cada lectura/acción; nodo/ventana obsoletos denegados. |
| Paquete desconocido, alias regional o reinstalación | Eludir una denylist por nombre | Denegar identidad desconocida; resolver package + UID + certificado y categorías; invalidar autorización tras cambios. |
| Campo secreto dentro de app no financiera | Exponer contraseña, OTP, PIN, CVV, semilla o recuperación | Bloqueo de `isPassword`, inputType/autofill y pantallas sensibles; incertidumbre significa denegar, sin extraer texto. |
| MCP remoto abierto, token filtrado o túnel público | Cualquier tercero opera herramienta sobre el teléfono | Bind loopback por defecto; auth fuerte, scopes, TLS, expiración, rate-limit y salida remota apagada hasta configuración segura. |
| Lectura pasiva de notificaciones/screenshot | Filtrar OTP o detalles financieros aunque no haya tap | Política previa a lectura y redacción en origen; bloquear fuentes sensibles. |
| Carrera de emergencia/cancelación | Servidor acepta nuevas acciones o un gesto pendiente se completa | Latch persistente, cerrar admisión primero, cancelar y confirmar estado; no prometer rollback imposible. |

La blacklist por paquete es necesaria pero insuficiente: mantenerla con metadatos firmados/versionados, admitir aliases regionales y categorías, detectar UI sensible y bloquear todo lo no verificable. No autorizar por una clasificación de texto que pueda fallar. Si un paquete no está identificado o la ventana está en transición/overlay/multi-ventana, el resultado debe ser denegación segura.

## 7. Arquitectura objetivo

Ruta obligatoria de toda llamada: `MCP transport → autenticación → principal/scopes → normalización de capacidad/argumentos → Policy Enforcement Point (PEP) → adaptador tipado → servicio Android → minimización/redacción de resultado → auditoría`. Ninguna skill, modelo, tool o ruta legacy puede llamar directamente a Accessibility, storage sensible, NotificationListener o acciones de sistema sin atravesar el PEP.

Crear módulos desacoplados: `contracts` con capacidades versionadas; `auth` con principal y scopes; `policy` determinista y deny-by-default; `capabilities` con catálogo explícito; `services/accessibility` como adaptador único; `lifecycle`/`JobRegistry` con dueño único; `audit` con logs sin contenido sensible. El modelo propone un plan, pero no eleva privilegios ni cambia una decisión del PEP.

Capacidades separadas como mínimo: lectura de UI; captura de pantalla; lectura de notificaciones; tap de nodo; tap de coordenada; escritura de texto; acciones globales; apps/intents; archivos; cámara; ubicación; skills/conectores. En el perfil AXIOM seguro, retirar el registro de `tap_coordinate`, gestos, `setText`, global actions, notificaciones e intents, salvo que una necesidad concreta se apruebe por separado. Una herramienta no puede recibir `password`, `pin`, `otp`, `cvv`, `seed` o `secret` como argumento; debe rechazarlos antes de cualquier adaptador y no registrarlos.

El `PolicyDecision` produce solo `ALLOW`, `DENY(reason_code)` o `REQUIRE_CONSENT` para capacidades expresamente permitidas. `DENY` no puede ser sobrescrito por HITL. Consentimiento, cuando corresponda a una app no sensible, debe ser de un solo uso, duración corta, y vincular `package + firma + UID + window token + tool/capacidad + objetivo + expiración`; cambio de paquete/ventana/permiso lo revoca. En apps financieras y ventanas de autenticación el único resultado posible es `DENY`.

## 8. Plan paso a paso con puertas de salida

### Fase 0 — Congelar exposición y acordar baseline seguro (BLOQUEANTE)

1. Desactivar los túneles/endpoints de prueba públicos sin auth o sustituirlos por un acceso con autenticación fuerte y rotación de secretos. No reutilizar URLs temporales publicadas en chat.
2. Mantener el servidor detenido o en loopback; prohibir modo abierto cuando la interfaz no sea loopback. Config inválida o auth deshabilitada en binding remoto debe impedir el inicio.
3. Congelar y registrar commit/versiones del código, APK, configuración y huella de entorno. No grabar claves ni tokens en el plan.
4. Inventariar todas las rutas de lectura/acción y puntos de dispatch en source actual; reconciliar la copia móvil con la PC.

**Salida:** inventario firmado; configuración efectiva capturada sin secretos; cero endpoint remoto abierto. No continuar a control activo si el endpoint sigue anónimo.

### Fase 1 — Especificar políticas y catálogo (BLOQUEANTE)

1. Definir categorías: banca, wallets, pagos, trading/cripto, autenticadores, password managers, pantallas/diálogos de Android de PIN/biometría y cualquier paquete desconocido.
2. Construir un registro local de identidad por paquete: nombre exacto, UID, digest de certificado de firma, categoría, versión y vigencia. Fallar cerrado ante error/actualización/reinstalación o falta de lookup.
3. Definir atributos sensibles a bloquear antes de su lectura: `isPassword`, password/PIN/OTP/CVV, tarjeta, semilla, clave privada, recuperar cuenta, confirmar/pagar/transferir; soporte de idioma y WebView; incertidumbre = deny.
4. Publicar un catálogo seguro de capacidades. El perfil seguro no incluye herramientas genéricas de gesto/teclado; módulos extra permanecen deshabilitados hasta revisión.

**Salida:** matriz `paquete/categoría × capacidad × decisión` completa; política versionada; aprobación de seguridad; ningún allow implícito.

### Fase 2 — Implementar PEP único antes de lectura y acción (BLOQUEANTE)

1. Introducir `PolicyGate`/PEP sin efectos laterales, con un solo punto de decisión y códigos estables (`FINANCIAL_PACKAGE`, `SENSITIVE_WINDOW`, `UNKNOWN_PACKAGE`, `STALE_NODE`, `IDENTITY_MISMATCH`, `AUTH_DISABLED`, `EMERGENCY_LATCH`, `PERMISSION_REVOKED`).
2. Integrar la decisión antes de `getRootNode`/parsing/lectura de texto, captura de pantalla, notificación o llamada al `ActionExecutor`; no limitarlo a `performNodeAction`.
3. Revalidar identidad y foco justo antes del dispatch. Para coordenadas, resolver ventana bajo el punto y exigir correspondencia inequívoca; si no se puede resolver, rechazar. Evitar TOCTOU con ventana/token/identidad bloqueados durante la autorización.
4. Asegurar que click, long-click, setText, tap, doble tap, swipe, scroll, pinch, customGesture, acciones globales, intents y wrappers legacy pasan por el PEP.
5. `currentPackageName` y coincidencia de strings no bastan para autorizar. El filtro de archivos actual queda como defensa secundaria.
6. `PrivacyToolGate` podrá reducir la salida después de autorizar lectura, pero nunca es el control de acceso primario; habilitar Privacy Mode no debe ser requisito para bloquear banca.

**Salida:** análisis estático demuestra que 100% de rutas de acceso sensible llaman al PEP; pruebas de unidad de tabla completa; las fallas del gate deniegan.

### Fase 3 — Perfil sin interacción, HITL acotado y emergency latch

1. Arranque en perfil `AUDIT_READ_ONLY`, con herramientas de acción no registradas. `PREVIEW` puede mostrar `would_deny`/`would_tap`, nunca despachar gesto.
2. Apps/billeteras/banca y cualquier pantalla sensible quedan siempre fuera; no ofrecer botón de “permitir de todas formas”.
3. En apps expresamente permitidas, consentimiento por acción y destino, visible en primer plano y expiración corta. No existe consentimiento global “controlar teléfono”.
4. Emergency stop primero cierra admisión, invalida el epoch de los jobs, cancela cola, revoca consentimientos y detiene el servidor. Persistir `EMERGENCY_STOPPED` hasta recuperación administrativa explícita; no limpiar solo por transcurso de 3s.
5. Informar diferencia entre `cancelación solicitada`, `cancelada`, `dispatch ya entregado al sistema` y `resultado no confirmado`. No prometer que una acción ya enviada a Android se deshace.

**Salida:** estados de lifecycle y latch persistentes; pruebas de carreras start/stop/cancel; no se aceptan nuevas llamadas tras emergencia.

### Fase 4 — Autenticación, red y separación de capacidades (BLOQUEANTE para acceso remoto)

1. Binding loopback por defecto; remoto solo mediante decisión explícita. TLS obligatorio para tráfico remoto; bearer/OAuth fuerte, rotación/revocación y scopes mínimos por principal.
2. Un token no equivale a autorización universal. Aplicar scopes por capacidad, rate limit, límites de tamaño/tiempo y logs de decisión sin argumentos.
3. Verificar auth efectiva en `/mcp`, `/tools/{name}`, `/openapi.json`, mirror routes, `/s/{token}`, `/.well-known/` y rutas OAuth. Cada excepción no autenticada debe documentar por qué el secreto de ruta/capability es suficiente y cuál es el límite.
4. Endurecer controles de CORS/hosts, datos de salud, túneles públicos y exposición de metadatos. Redactar URL/token de capability en logs.
5. Deshabilitar perfiles de auth abierta en builds de producción; tests deben fallar si bearer y OAuth están apagados junto con binding/túnel remoto.

**Salida:** pruebas de integración demuestran 401 sin credenciales, 403 sin scope, rechazo de expirado/revocado; ninguna herramienta mutante se ejecuta bajo auth fallida.

### Fase 5 — Menor privilegio y minimización

1. Revisar `MANAGE_EXTERNAL_STORAGE`, QUERY_ALL_PACKAGES, Accessibility, NotificationListener, cámara, micrófono y localización uno por uno; retirar lo no imprescindible. Usar SAF/permisos temporales cuando sea viable.
2. Registrar “fuentes financieras prohibidas” también en notifications y apps intents, no solo rutas de archivo.
3. En eventos de accesibilidad, filtrar por identidad antes de procesar contenido; evitar logging de texto/árbol, nombres de nodos o coordenadas sensibles. Redactar/descartar en origen.
4. Eliminar el booleano estático `isAntiBanFirewallEnabled()` o calcularlo con validación efectiva del gate, pruebas de cobertura y estado activo. No mostrar sello “inmune/seguro” por un booleano constante.

**Salida:** matriz permiso→feature→justificación→consentimiento→prueba; `0` datos financieros/secretos en logs, screenshots, prompts y telemetría de prueba.

### Fase 6 — QA adversarial automatizado (BLOQUEANTE)

Crear app(s) sintética(s), nunca usar cuentas/credenciales reales. Añadir test doubles que cuenten llamadas a getters, capturas, `dispatchGesture`, `performAction`, `setText` e intents; las pruebas assertan que el adaptador no fue llamado, no solo que MCP devolvió error.

| ID | Caso | Resultado exigido |
|---|---|---|
| `FIN-001` | Paquete wallet simulado identificado, consulta de árbol/screenshot/notificación | `DENY` antes de leer; cero getter/captura/retorno de contenido. |
| `FIN-002` | `tap`, `setText`, nodo click, gesture, scroll y global action en paquete financiero simulado | Rechazo previo; cero dispatch/performAction/setText. |
| `FIN-003` | Paquete desconocido, UID/certificado no resuelto, lookup falla o paquete reinstalado | Deny-by-default, sin lectura ni acción. |
| `FIN-004` | `nodeId` creado en app segura y reutilizado tras cambio a wallet | `STALE_NODE`/`IDENTITY_MISMATCH`; nada llega al executor. |
| `FIN-005` | Carrera: cambiar paquete/ventana después del PEP y antes del dispatch | Denegar/invalidar; garantizar que no se toca la ventana nueva. |
| `FIN-006` | Coordenada sobre ventana protegida o resolución de ventana ambigua | No dispatch; motivo auditable. |
| `FIN-007` | Campo password/PIN/OTP/CVV/seed, WebView/canvas, idioma distinto o metadata incompleta | No leer valor, no enfocar, no escribir; incertidumbre = deny. |
| `FIN-008` | Intent o NotificationListener recibe contenido/OTP financiero | Fuente bloqueada o solo metadatos no sensibles permitidos; nunca devolver OTP. |
| `FIN-009` | Denegación frente a HITL “permitir” | Política financiera prevalece; no existe override. |
| `NET-001` | Sin bearer/OAuth en binding remoto/túnel | Inicio rechazado o 401; ninguna llamada de tool. |
| `NET-002` | Token válido sin scope y token expirado/revocado | 403/401 respectivamente; handler no invocado. |
| `LIFE-001` | Botón físico de stop durante cola/concurrencia | No se inician nuevas acciones; estado persistido; resultados ya enviados se reportan como inciertos. |
| `QA-001` | Paquete/modelo/servidor requerido ausente, credencial inválida, red bloqueada y entrada malformada | Error real y explícito; nunca respuesta stub/`ok`/fallback confundible con éxito. |
| `QA-002` | Evidencia de corrida sin archivo/log/run_id o control negativo | Validador devuelve fallo; no se marca PASS. |

**Criterios de aceptación:** 100% de casos protegidos bloqueados antes de lectura y antes de dispatch; cero llamadas al adaptador; 0 secretos en artefactos; cambio de paquete invalida el consentimiento; auth inválida no ejecuta handler; resultados reproducibles. La lista de paquetes debe cubrir alias regionales y firmas conocidas y contemplar una política `UNKNOWN=DENY`. No usar apps bancarias reales ni generar transacciones.

### Fase 7 — Modelo local y CLI costo cero, solo tras seguridad

1. Primero implementar/auditar el PEP, luego añadir IA local. El modelo nunca recibe una capacidad de acción libre.
2. MVP candidato: `llama.cpp` en local y un solo modelo pequeño cuantizado; en móvil, evaluar `Pi` como adaptador JSONL o una alternativa ligera. En PC, probar por separado `Goose` o `gptme minimal`. `Crush` y `Ollama` son alternativas experimentales, no dependencias obligatorias.
3. Empezar en `plan-only`: el modelo devuelve JSON con capability y argumentos no sensibles; el policy engine autoriza o deniega. No llamadas a cloud, API key, créditos, SaaS ni alternancia automática a proveedor remoto.
4. Fijar versión, binario, hash de modelo/licencia; desactivar descargas automáticas y concurrencia. Medir RSS pico, CPU, temperatura, storage, OOM, latencia, suspensión/reanudación, egress y consumo de batería; solo entonces ampliar contexto.
5. Si se detecta cualquier egress inesperado, proveedor no local o requisito comercial, cancelar la fase: no hay fallback pago.

**Salida:** prueba con red bloqueada y secretos vacíos demuestra que el flujo local resuelve una tarea sintética nonceada; proveniencia local verificada; el costo se mantiene $0. Compatibilidad y RAM seguirán como `INFERRED_ONLY` hasta esa evidencia.

### Fase 8 — Pilotaje, rollback y mantenimiento

1. Mantener la primera entrega en modo lectura/preview en dispositivo de laboratorio y con datos sintéticos. Sin acceso remoto público.
2. Habilitar una capacidad a la vez, en un allowlist por app no financiera, con monitorización y rollback funcional probado.
3. Conservar manifiesto de artefactos y hashes, decisiones de política, versiones y logs minimizados; no guardar screenshots ni texto completo por defecto.
4. En cambios de modelo, SDK, app, firma, Android/OEM o permiso, invalidar aprobación previa y repetir las pruebas afectadas.
5. Realizar revisión AXIOM Criticón antes de cualquier versión pública; cualquier falla financiera revierte al perfil `AUDIT_READ_ONLY` y bloquea publicación.

## 9. Matriz evidencia → riesgo → control → prueba

| Evidencia | Riesgo | Control | Prueba | Estado ahora |
|---|---|---|---|---|
| `ActionExecutorImpl.tap` despacha gesto por coordenadas sin paquete visible en el método; `performNodeAction` solo bloquea paquetes de delivery exactos. | `R-01` Interacción con banca/wallet no bloqueada. | `C-01` PEP común antes de lectura/acción, identidad/ventana en tiempo de dispatch y financiero `DENY`. | `FIN-001..006`. | `ESTÁTICO`; pruebas `NO EJECUTADAS`; **BLOQUEANTE**. |
| `isAntiBanFirewallEnabled()` retorna `true` constante; `StreetImmunityChecker` reporta tres paquetes de reparto. | `R-02` falso sello de seguridad que sugiere protección no demostrada. | `C-02` eliminar literal constante, medir cobertura real y mostrar estado solo con evidencias. | Test de política + test mutante que retira el gate y exige fallo. | `ESTÁTICO`; `NO EJECUTADO`. |
| `PrivacyPipelineImpl` pasa texto/árbol sin cambios si privacidad está apagada y procesa tras adquisición. | `R-03` privacidad opcional interpretada como firewall. | `C-03` autorización antes de adquisición; redacción como segunda capa siempre que aplique. | `FIN-001`, `FIN-007`, `FIN-008` con Privacy Mode ON/OFF. | `ESTÁTICO`; `NO EJECUTADO`. |
| MCP puede operar abierto si bearer y OAuth se desactivan; endpoints temporales informados como públicos sin auth. | `R-04` ejecución remota no autorizada. | `C-04` cerrar endpoints de prueba; auth+scopes+TLS y remoto apagado por default. | `NET-001/002`; revisar rutas excluidas. | `OBSERVADO` como configuración declarada y `ESTÁTICO`; entorno futuro no verificado. |
| Manifiesto declara permisos privilegiados (Accessibility, almacenamiento amplio, notificaciones, cámara/micrófono, ubicación). | `R-05` exposición amplia y sobrecolección. | `C-05` mínimo privilegio y permiso temporal/por función. | Matriz de permisos y revocación. | `ESTÁTICO`; revisión funcional pendiente. |
| Investigación de 31 CLI ofrece compatibilidad/huellas mayormente documentales o estimadas. | `R-06` escoger stack inviable o con costos ocultos. | `C-06` prototipo local con red bloqueada, versiones fijas y medición. | `CLI-001..007`, `FIN-001..008`. | `DOCUMENTAL/INFERIDO`; `NO EJECUTADO`. |
| Ciertas afirmaciones del QA anterior contradicen rutas actuales (`/tools/{name}` existe; `/openapi.json` no figura excluido). | `R-07` implementar sobre diagnóstico obsoleto. | `C-07` reconciliar cada claim con commit/snapshot y test de integración. | `NET-001/002` sobre app compilada. | `CONTRADICCIÓN DOCUMENTAL RESUELTA ESTÁTICAMENTE`; runtime pendiente. |
| MCP PC tiene herramienta general `pc_comando`. En esta tarea se usó solo para `find/sed/grep` de lectura. | `R-08` herramienta de PC amplía impacto fuera de código. | `C-08` política de tarea read-only; en producto, lista de comandos o quitar shell/control global de perfil seguro. | Test de denegación de `rm`, shell arbitraria, teclado/ratón en perfil auditor. | Herramienta inventariada; no se ejecutaron comandos mutantes. |

## 10. QA de evidencia, costo y telemetría

Para cada test futuro, guardar `result.json`, salida cruda, log, captura solo si necesaria y redaccionada, hash del APK/binario/modelo, versión del código, configuración sin secretos, entorno, permisos, nonce/run_id y estado de red. El `run_id` debe aparecer literalmente en `result.json` y log. Ejecutar primero una suite negativa que pruebe que el validador rechaza carpetas incompletas, `isError=true`, captura ausente, log sin fragmento exigido o run_id desconectado. Las pruebas deben declarar su resultado como `NOT_TESTED`, `BLOCKED`, `PASS_OBSERVED`, `FAIL_OBSERVED` o `CONTRADICTED`; no usar un simple “se abrió” o “no crasheó”.

Para el principio $0, diferenciar: **sin pago en esta tarea**, **sin cargo observado durante un ensayo** y **costo cero verificado**. Para afirmar `VERIFIED_ZERO` se requiere ruta local confirmada, credenciales vacías, red bloqueada excepto loopback, proveniencia de proceso/modelo y fuente fiable de uso/facturación. Este agente no tiene acceso a cuentas/ledger de facturación; no certifica gastos de infraestructura de terceros. En el plan, cualquier servicio pago, créditos, suscripción, endpoint comercial o fallback remoto queda prohibido; si la opción local no funciona, se detiene en error y no cambia de proveedor.

La telemetría de seguridad debe registrar solo timestamp, ID de tool/capacidad, package/UID hash o categoría, decisión, reason_code, policy_version, epoch y latencia. Nunca registrar tokens, headers completos, texto de pantalla, nombres sensibles, OTP/PIN, coordenadas vinculadas a secretos ni capturas sin redacción.

## 11. Roles AXIOM convocados

Se hicieron revisiones paralelas de seis roles especializados: **Criticón de seguridad**, **Polyglot Architect**, **Deep Researcher/QA**, **Hardware & Memory Guard**, **UX/DX Lead** y **Universal Doc Synthesizer**. Sus recomendaciones se incorporan como diseño propuesto: deny-by-default, PEP único, capacidades tipadas, control HITL no anulable para finanzas, latches persistentes, trazabilidad de evidencia, límites de RAM y separación entre claims documentales y pruebas. Estas revisiones son análisis de expertos sobre el material suministrado, no pruebas de runtime ni certificación de seguridad.

## 12. Cuestionamiento crítico de decisiones propias

1. **¿Es suficiente una lista de paquetes financieros?** No. Puede estar incompleta, cambiar por región o no identificar un WebView/pantalla de autenticación en otra app. Por eso el diseño añade identidad firme, categorías, clasificación de ventana y `UNKNOWN=DENY`; aun así requiere mantenimiento y pruebas.
2. **¿Una confirmación humana resuelve el riesgo?** No para banca/wallets o secretos: el modelo podría engañar al usuario y una confirmación no evita exposición de datos. El HITL solo se aplica a apps no sensibles y acciones muy acotadas; nunca vence el `DENY` financiero.
3. **¿La redacción posterior es suficiente?** No. Si el texto ya se leyó o se entregó al modelo, la filtración ya pudo ocurrir. El bloqueo debe preceder a `getText`, árbol, screenshot y notificación.
4. **¿Un endpoint con bearer es seguro?** No por sí solo. Hay riesgos de token global filtrado, scopes excesivos, TLS/configuración, rutas de capability y herramientas poderosas. Remoto debe estar apagado hasta auth granular, TLS y tests.
5. **¿Un sandbox en Termux/proot aísla código?** No necesariamente. Bionic/glibc y la compatibilidad de un binario tampoco garantizan aislamiento; shell y agentes deben quedar fuera del perfil seguro.
6. **¿El kill switch garantiza revertir un clic?** No. Puede detener trabajo posterior, no revertir un gesto que Android ya aceptó. El usuario debe ver el estado real, y el servidor debe cerrar la admisión antes de cancelar.
7. **¿El material de 17 apps y 31 CLI basta para elegir producción?** No. La mayoría de comparativas son documentales; compatibilidad, RAM, licencias, permisos y costos deben probarse en versiones concretas. La conclusión del CLI se mantiene como shortlist, no adopción.
8. **¿Por qué el plan no implementa código ahora?** Porque el pedido actual fue análisis y entrega de plan. Codificar sin una política aprobada, inventario de todas las rutas y pruebas reproducibles ocultaría más riesgos de los que resolvería.
9. **¿El costo total futuro está garantizado en $0?** No puede garantizarse con la evidencia actual. La política es no contratar ni invocar servicios pagos y detener el flujo ante cualquier costo; los costos externos de hosting/red necesitan verificación independiente antes de producción.

## 13. Criterio final GO / NO-GO

**NO-GO** para exponer un MCP remoto anónimo, habilitar taps/gestos/escritura globales, operar con apps bancarias/wallets o declarar “firewall financiero activo” en el estado actual de evidencia.

Solo considerar **GO controlado** cuando: (a) el endpoint esté autenticado, cifrado y con scopes; (b) el PEP cubra todas las rutas antes de leer/actuar; (c) cualquier app financiera, de autenticación, ventana sensible o identidad incierta se bloquee sin override; (d) los tests `FIN-*` prueben cero lecturas y cero dispatch; (e) emergency latch cierre admisión y no finja rollback; (f) evidencia de QA esté completa y ligada a run_id; y (g) la ruta local/no-pago haya demostrado costo $0 con método verificable. Un fallo en cualquiera devuelve el sistema a `AUDIT_READ_ONLY`/`STOPPED`.

## 14. Referencias internas consultadas

- `/sdcard/fenix_auditorias/Readme_manus.md`
- `/sdcard/fenix_auditorias/MATRIZ_COMPARATIVA_FINAL.md`
- `/sdcard/fenix_auditorias/18_cli_ia_termux_android_2026-10-17.md`
- `/sdcard/fenix_auditorias/01_manus/` a `/sdcard/fenix_auditorias/17_vscode_web/` (informes; incluida la ficha de prospección Google Play)
- `/sdcard/fenix_auditorias/protocolos_axiom/AXIOM_CRITICON_SEGURIDAD.md` y los cinco protocolos AXIOM complementarios
- `/sdcard/fenix_auditorias/codigo_fuente_mcp/docs/ARCHITECTURE.md`, `PERMISSIONS.md`, `AUDITORIA_CRITICON_PLAN_MAESTRO_QA.md`, `INTEGRACION_MANUS_MCP.md`, `docs/qa-evidence/README.md`
- Snapshot PC: `/home/eli/Documentos/fenix-mobile-mcp/app/src/main/kotlin/com/fenix/mobilemcp/` — en particular `services/accessibility/ActionExecutorImpl.kt`, `McpAccessibilityService.kt`, `security/StreetImmunityChecker.kt`, `security/EmergencyKillSwitchManager.kt`, `privacy/PrivacyToolGate.kt`, `privacy/PrivacyPipelineImpl.kt`, `mcp/McpServer.kt`, `mcp/auth/BearerTokenAuth.kt`, `data/model/ServerConfig.kt`, `app/src/main/AndroidManifest.xml`
- E2E consultado: `/home/eli/Documentos/fenix-mobile-mcp/e2e-tests/`; pruebas e2e inspeccionadas por inventario. No se ejecutó Gradle ni suite alguna.
- Repositorio contextual solo inventariado: `/home/eli/biblioteca-fenix-v3/`.

---

**Cierre:** documento de planificación basado en evidencia documental y estática seleccionada. No es una certificación ni autorización de despliegue. Pruebas de seguridad, rendimiento y costo: **pendientes/no ejecutadas**. No se hicieron pagos ni cambios en apps o código fuente.

# SUPLEMENTO DE REVISIÓN CRÍTICA Y EXPANSIÓN ESTRATÉGICA

**Fecha de suplemento:** 18 de octubre de 2026.  
**Estado:** revisión de diseño y fuentes públicas; no implementación, no pruebas de runtime del MCP y no llamada a la API de Jules.  
**Efecto:** este suplemento amplía el plan anterior; si existe contradicción, prevalecen las reglas de seguridad más restrictivas aquí descritas. Ningún análisis externo autoriza el uso de un servicio comercial ni cambia la regla de costo cero.

## 15. Auto revisión adversarial del plan previo

El plan inicial acertó al exigir un PEP previo a lectura/acción y al marcar `NO-GO` para control general, pero no cerró por completo estas brechas:

1. **Asumió que una denylist de paquetes más “ventana sensible” se puede hacer completa.** Android Accessibility no garantiza identidad fiable de cada pantalla: apps pueden usar WebView, canvas, overlays, ventanas del sistema, `FLAG_SECURE`, controles custom o contenido que no expone semántica. Incluso la detección de `isPassword` puede estar ausente o ser incorrecta. Por tanto, ni el paquete ni los atributos de nodo justifican `ALLOW` para una acción de alto impacto. La política segura es quitar toda acción de alto impacto del perfil autónomo; banca, pagos, autenticación y operaciones irreversibles deben quedar fuera del scope, no solo detrás de una lista.
2. **La política anterior pedía una decisión atómica entre verificar ventana y despachar gesto que quizá Android no ofrece.** El sistema puede cambiar de foco entre comprobación y `dispatchGesture`; un `window token` no vuelve el gesto transaccional. La reducción de riesgo debe ser arquitectónica: no permitir gestos autónomos globales. Para capacidades de bajo riesgo, confirmar foco inmediatamente antes, limitar la tarea a una app sandbox de prueba y reconocer la carrera residual; no afirmar eliminación total de TOCTOU.
3. **HITL se definió demasiado genérico.** Una pantalla de consentimiento puede ser engañada por el mismo contenido contaminado o confundida con la UI controlada por el agente. Consentimiento debe venir de UI confiable propia del sistema/Fénix, mostrar paquete firmado, acción y consecuencia en términos concretos, y no aceptar confirmación derivada de un nodo o respuesta del modelo. Aun así, en transacciones financieras y autenticación no debe existir override.
4. **No se definió aislamiento de secretos de proveedor ni ciclo de vida de claves.** Añadir Jules y otros conectores implica API keys, OAuth refresh tokens, variables de entorno y posibles logs. Las credenciales deben guardarse en Android Keystore/cifrado respaldado por sistema cuando sea apropiado, nunca en prompt, archivos de proyecto, historial, argumento CLI, URL, telemetría o repo; scopes mínimos, rotación y revocación. Una credencial pegada en chat se considera expuesta y debe rotarse.
5. **La semántica del mercado de extensiones no estaba acotada.** Instalar un CLI arbitrario puede ejecutar scripts `postinstall`, binarios nativos, hooks de shell, actualizarse automáticamente o extraer secretos. Un catálogo no vuelve confiable al paquete. Sin sandbox OS real, la extensión no corre con privilegios de Accessibility, storage amplio o credenciales. En Termux/proot no se debe prometer aislamiento fuerte del host Android.
6. **La política de costo cero no contempló correctamente los límites que cambian ni las llamadas involuntarias.** “Plan gratuito” puede tener cuota, límites móviles, requerir suscripción para más tareas, almacenamiento, transferencias o uso de proveedor. El backend tiene que requerir un `cost_policy=deny_paid` y un conector no puede activarse si no hay evidencia vigente del alcance gratuito. Si no puede verificarlo, estado `COST_UNKNOWN` y bloqueo de llamada, no una asunción.
7. **El autocompactado no puede depender siempre de metadata live.** Muchos proveedores no ofrecen endpoint de tokens de contexto ni devuelven capabilities completas por modelo. Consulta periódica podría añadir tráfico, compartir metadatos de cuenta/modelo, fallar offline o ser interpretada como garantía actualizada. Debe haber fallback explícito a `UNKNOWN`, TTL y modo manual; jamás inventar límites.
8. **Se mezclaban modos Axiom de razonamiento con autorización.** Un rol “Criticón”, mayor reasoning effort o un sistema prompt más largo no son controles de seguridad. Los roles son perfiles de análisis sin nuevos permisos. Un modelo de razonamiento alto puede gastar más cuota/costo; por defecto se deshabilita proveedor remoto y se muestra el presupuesto/cupo antes de cada ejecución.
9. **El plan aspiraba a “cero lecturas” pero no definía todos los canales de observación.** La suite debe incluir eventos, accessibility cache, overlays, content descriptions, screenshots, video/cámara, logs, crash dumps, notificaciones, memoria y telemetría. Invariantes de prueba deben contar llamadas en el límite más bajo común, no solo rechazos MCP.
10. **No se definió proceso de privacidad para artefactos QA/modelo.** Usar exclusivamente datos sintéticos y emulador/dispositivo de laboratorio; fijar retención mínima y borrado de artefactos, anonimizar logs y evitar capturas que contengan cuentas reales.
11. **La utilidad no se medía junto a seguridad.** Un agente que siempre se detiene es seguro pero no útil; los benchmarks deben separar ataques ejecutados, defensa con tarea completada, defensa con tarea bloqueada y fallas de capacidad, sin convertir un bloqueo total en falso éxito.
12. **No se estableció proceso de actualización de paquetes firmados ni regresión por cambios de proveedor.** El catálogo financiero, certificados, alias, modelo, CLI, protocolo y SDK requieren versionado y prueba con firma/hashes. Si la lista/policy no se puede actualizar sin red, el sistema sigue en modo seguro, no degrada a allow.

**Decisión revisada:** para la primera versión solo se admite `AUDIT_READ_ONLY` y un `PLAN_ONLY` sin capacidad de control real. La función que toca la interfaz Android se considera experimento separado, limitado a apps propias/simuladas en un entorno de prueba. No abrir una excepción de producción por presión de UX.

## 16. Investigación externa: agentes Android, MCP y contexto

### 16.1 Benchmark de seguridad móvil actualizado

El trabajo académico MobileWorldSafety, publicado como arXiv:2608.17659v1 el 18 de agosto de 2026, construye 142 tareas de riesgo en 13 apps Android reales. Evalúa seis agentes y reporta tasas de éxito de ataque entre **40,4% y 66,9%**. Evalúa contenido hostil dentro de correos, mensajes, páginas, publicaciones, archivos y respuestas de herramientas, no solo prompts directos. Su protocolo separa `Executed`, `Partial`, `Defended`, `Stalled` y `Run Failed`; calcula riesgos con indicadores verificables sobre estado final y usa juicio LLM solamente para casos ambiguos. El resultado respalda el enfoque de prueba adversarial por canal y de evidencia observable, pero no certifica que una defensa concreta funcione en Fénix. El benchmark estudia un entorno y versiones particulares; no extrapolar esos porcentajes como tasa de riesgo de Fénix. [1]

**Consecuencia de revisión:** agregar inyecciones por todos los canales externos relevantes (notificación, correo, web, archivo, UI, salida de tool y descripción de skill), con corpus de prueba local y sin Internet. Las métricas deben incluir `ASR` (ataque realizado/alcanzable), `TCR` (tarea benigna completada resistiendo), bloqueo seguro, tarea atascada y fallo de capacidad. Reportar muestras, versión, app, ambiente y verificador; no usar un LLM como juez único de un incidente financiero.

### 16.2 Autorización MCP

La documentación oficial MCP describe autorización OAuth 2.1 para servidores HTTP remotos, metadata del recurso protegido y validación de permisos/token. La especificación señala que STDIO local puede usar otros mecanismos, pero no convierte un endpoint remoto anónimo en seguro. El diseño propuesto debe mapear principal y scopes a herramientas concretas y verificar autorización dentro del servidor; la existencia de un token válido por sí sola no demuestra que una acción sea apropiada. [2]

**Consecuencia de revisión:** no exponer endpoint `trycloudflare` sin autenticación; para remote HTTP usar TLS, OAuth 2.1/PKCE cuando el tipo de cliente lo permita, recurso/audience correctos, verificación de issuer y expiración, protección frente a replay, revocación, scopes por capability, rate limits y logs de auditoría minimizados. Herramientas de shell/control no se agrupan bajo el mismo scope de lectura. El transporte local también valida origen y limita sockets.

### 16.3 Compacción y ventana de contexto

La documentación actual del proveedor Anthropic explica que la ventana incluye mensajes, tools, resultados, imágenes/documentos y la respuesta generada; además advierte de degradación de calidad/recuerdo con contextos extensos y presenta compaction del lado servidor para modelos compatibles. El tamaño y la disponibilidad varían por modelo y feature; la documentación indica explícitamente diferencias por modelo y que ciertas funciones son beta. Es un ejemplo de implementación de proveedor, no un endpoint universal que todos los proveedores expongan. [3]

**Consecuencia de revisión:** no inferir que cualquier API publique una métrica/umbral consultable. La capa de capacidades deberá devolver `known(value, source, checked_at, expires_at)`, `unsupported` o `unknown`; nunca extrapolar de nombre/model family ni hardcodear valores por alias. La compactación cliente debe ser provider-independent y respetar instrucciones, consentimientos, referencias/evidencia y estado de tools; el servidor de un proveedor no puede compactar material que la app cliente no haya enviado.

## 17. Integración segura y costo cero de Google Jules

### 17.1 Hechos contrastados y límites

La documentación oficial de Jules describe una API REST **alpha/experimental**, que usa API key en el header `X-Goog-Api-Key`; para integrar un repo hay que instalar la Jules GitHub App. La API crea y gestiona sesiones remotas de programación, actividades y aprobación de planes; la documentación ofrece `requirePlanApproval` como control. La documentación oficial del CLI prescribe `npm install -g @google/jules`, autenticación mediante `jules login` en navegador y comandos `remote new`, `remote pull`, `remote list`; `remote new` abre una sesión de Jules en cloud y `remote pull` recupera resultados/cambios. Esto es CLI local como cliente, **no inferencia de código local en Termux**. No se encontró en la documentación oficial leída un modo Jules offline/local ni se verificó una API MCP oficial nativa de Jules. Puede construirse un adaptador MCP propio sobre la API, sujeto a políticas, pero no debe llamarse “conector oficial”. [4] [5]

La página de límites consultada publica para el plan Jules base 15 tareas por ventana móvil de 24 horas y 3 concurrentes; Pro y Ultra son planes asociados a suscripción y publican límites superiores. La misma página declara que límites/funciones pueden cambiar. Por ello, nivel base o cuota gratuita no significa costo total cero verificado, capacidad garantizada ni permiso para comprar. Si la regla es cero absoluto, Jules solo se ofrece como extensión opcional apagada por defecto y **bloqueada automáticamente** salvo que el usuario haya aprobado su uso externo para la tarea y exista verificación actual de que no exige compra/upgrade ni cobro. Esta tarea no llamó a la API, no ejecutó el CLI, no abrió Jules, no creó claves ni sesiones. [6]

### 17.2 Forma modular de integración

Publicar dos adaptadores separados dentro del catálogo: `jules_cli` para instalación voluntaria en Termux/PC compatible, y `jules_api` para conexión remota. Dejar ambos `disabled` por defecto y no precargar paquete ni secreto. El manifiesto indica origen oficial, versión/hash, runtime, sistema compatible confirmado, permisos, datos transmitidos, endpoints, autenticación, límites/costo conocidos, último chequeo y método de desinstalación. El soporte Termux debe marcarse `UNVERIFIED` hasta probar Node/npm, Bionic/paquetes, login browser, almacenamiento de tokens, CLI y flujo completo en un entorno de prueba; la página oficial que se leyó documenta npm/Linux terminal pero no certifica Termux/Android.

El primer piloto debe ser repositorio ficticio o branch aislada, tarea no destructiva y `requirePlanApproval=true`; no habilitar `AUTO_CREATE_PR`, merge, push, cambios de producción ni acceso a secretos. Separar permisos `jules:read_sources`, `jules:create_session`, `jules:send_message`, `jules:approve_plan` y `jules:pull_changes`; aprobación de plan y descarga de cambios no se heredan del permiso de consulta. `jules:create_session` es ejecución remota y puede consumir cuota; mostrar al usuario proveedor, tareas/cupo y costo verificable antes de llamada. Sin estatus costo claro, no llamar.

La API key nunca se recibe en chat, nunca se pasa en URL/CLI argv y nunca se guarda dentro del repo. Crear/introducir credencial solo en un gestor seguro del SO con pantalla de secretos autorizada; si no está disponible, declarar integración `BLOCKED`, no pedirla en mensajes. Enviar a Google únicamente el repositorio, prompt, branch y contenido que el usuario autorice; escanear secretos antes de sincronizar. Mantener prompt injection de archivos/issues como datos no confiables. El Jules remoto no puede autorizar tools del teléfono, y sus diffs no se aplican automáticamente: revisión humana, análisis estático, tests aislados y diff firmado antes de cualquier integración.

**Alternativa si $0 es literalmente costo total y no solo ausencia de compra en el ensayo:** omitir/desactivar Jules. Los límites gratuitos publicados pueden cambiar y el servicio siempre es remoto. El producto permite que un usuario configure el conector, pero no afirma que se mantenga gratis ni dispara una ejecución al instalarlo.

## 18. Reingeniería de F-Chat

### 18.1 Modelo de proveedor/capacidad y selector dinámico

Separar `ProviderAdapter`, `ModelCatalog`, `CapabilityResolver`, `CredentialStore`, `BudgetPolicy` y `ContextManager`; no ligar UI ni skills a nombres de modelos concretos. El selector carga modelos desde la API del proveedor activo cuando exista un endpoint oficial y autorizado; consulta capacidades como contexto máximo admitido, salida, visión, tools, esfuerzo de razonamiento, token-counting, compaction server-side, streaming, disponibilidad regional y fecha/origen de dato. Cada campo lleva procedencia, freshness y estado (`verified`, `stale`, `unknown`, `unsupported`). El catálogo no asume que todos los proveedores tienen `/models` ni que dicho endpoint devuelva contexto/capabilities. Si solo hay metadata estática del proveedor, debe ser versionada/citada y visualizada como tal, no disfrazada de consulta live.

**Periodicidad:** refrescar en arranque, cambio de proveedor/modelo y TTL configurable por el usuario; no sondear continuamente. Respetar `Retry-After`, cuotas, ETag/cache cuando aplique y límites del proveedor. Un error de refresh conserva el último dato como `stale` con timestamp o cambia a `unknown`; nunca cae a un token limit inventado. No consultar API hasta que haya consentimiento/provider credential válida, y no hacer llamadas pagas solo para enumerar modelos.

La UI muestra proveedor activo, modelo elegido, fuente y antigüedad de metadata, capacidad, modo de razonamiento realmente soportado, posible uso de cuota, estado local/nube y si el modelo puede recibir datos externos. Si el modelo no soporta effort, la opción queda desactivada con explicación. No enviar un campo genérico “reasoning effort” a API que no lo admite.

### 18.2 Roles Axiom, conectores y skills como control explícito

Mostrar pestañas/paneles separados para `Proveedores y conectores`, `Skills`, `Modos especializados Axiom` y `Esfuerzo`. Los roles Axiom son plantillas de instrucciones y checklist sin poderes añadidos; cada uno tiene versión, fuente, alcance, herramientas permitidas, modelo requerido (si aplica) y coste/riesgo. “Criticón” no cambia la política de tool execution. Al activar una skill, mostrar capacidades que añade y datos que comparte; la autorización se solicita por tool/capability, no por nombre del rol. Conectores se muestran con endpoint, dueño, scopes, credencial almacenada/no almacenada, última comprobación, red/local y estado costo. Desactivar o eliminar revoca credenciales y retira todas las herramientas registradas, cancela sesiones dependientes y borra la metadata local seleccionada; confirmar antes de borrar datos que no sean reversibles.

### 18.3 Compactación adaptativa, opt-in y con control del usuario

La compactación automática es **opt-in**. Ajustes visibles: `Desactivada`, `Preguntarme antes`, `Automática` y `Eliminar datos compactados`; selector de umbral relativo cuando el proveedor ofrece token count y explicador de datos que se conservarán. “Eliminar” significa descartar el resumen y los snapshots locales controlados por F-Chat; no puede borrar una conversación ya enviada o persistida por el proveedor remoto, lo que debe quedar claramente indicado.

Flujo sin límites hardcodeados: obtener del proveedor `count_tokens` u operación equivalente para el contenido real, y la metadata/capacidad actual del modelo; calcular presupuesto residual restando system/developer prompt, tools, adjuntos multimodales, salida reservada y bloques de razonamiento según contrato del proveedor. Usar el umbral que publique el proveedor o un porcentaje configurado por el usuario y validar el total antes del request. Si no hay endpoint exacto, emplear tokenizer oficial compatible y marcar estimación; no convertir bytes/caracteres en tokens exactos. Si tampoco hay tokenizer fiable, preguntar o bloquear el request que no cabe. Respetar server-side compaction solo en modelos/versions que el proveedor documenta; no depender de ella como único respaldo.

El compactor conserva una estructura de estado tipada con objetivo del usuario, decisiones confirmadas, restricciones, resumen con citas a IDs de mensaje/fuente, tareas pendientes y resultados verificables de tools. No preservar instrucciones de contenido no confiable como política. Antes de reemplazar historia, mostrar preview y permitir deshacer hasta que el usuario descarte el snapshot. Si el resumen pierde una condición de seguridad, source URI, decisión de costo, identidad de paquete o resultado crítico, cancelar compactación. Mantener los datos brutos disponibles hasta confirmación del usuario, salvo configuración de retención/privacidad explícita.

Pruebas: mismo input semántico con distinto modelo; metadata expirada; cambio de modelo durante sesión; error de API; proveedor sin endpoint; adjunto multimodal; historial que contiene inyección; compaction repetida; recuperación/deshacer; usuario desactiva/elimina; límite próximo y límite excedido. Validar hechos/decisiones retenidos y que el resumen no promueve texto externo a instrucciones. Medir tokens/latencia/calidad y posibles llamadas/costos con el endpoint contable del proveedor si existe; de lo contrario costo queda `UNKNOWN`.

## 19. Catálogo de CLI/skills bajo demanda en Termux

Los 31 CLI no forman parte de instalación por defecto ni del APK/base de F-Chat. Se ofrecen como catálogo opt-in por extensiones. “Marketplace” significa catálogo curado y descriptor local, no necesariamente tienda con pagos; no comprar ni habilitar monetización. Un botón Instalar inicia solo después de ver origen oficial, licencia, versión, dependencias, tamaño, permisos, conexiones, consumo esperado y método de desinstalación. Sin evidencia suficiente, extensión marcada `UNVERIFIED` o excluida.

Pipeline de admisión: 1) manifiesto firmado con nombre/version/hash y repositorio de origen; 2) revisión de licencia, mantenimiento y advisories; 3) análisis de scripts de instalación, binarios y dependencias; 4) prueba en sandbox/VM desechable (Termux proot no cuenta como sandbox fuerte); 5) definir `capabilities` explícitas; 6) fijar versión/hash y no autoactualizar; 7) revisar costo/egress; 8) publicar con registro de evaluación; 9) revocar/cuarentenar y reproducir si hay cambio de hash o incidente.

Cada extensión se instala a petición, permanece deshabilitada hasta activación, usa cuentas/credenciales separadas, sin acceso a claves globales ni shell de Fénix, sin privilegios de Accessibility, sin `MANAGE_EXTERNAL_STORAGE`, sin autoapprove y con límite de procesos/RAM/tiempo. Para extensiones que ejecutan comandos, mostrar comando y efectos y exigir confirmación interactiva; prohibir ejecutar comandos sugeridos por un modelo directamente. Si el SO no ofrece aislamiento, advertir que corre con los permisos del usuario y no instalar extensiones con scripts no auditados.

En Termux, metadatos incluyen ABI, Bionic, disponibilidad de PTY, módulos nativos, instalación npm/pip, almacenamiento y lifecycle. Compatibilidad se registra por dispositivo y versión como `OBSERVED`/`BLOCKED`; no copiar cifras de una matriz estática a `PASS`. `jules` se cataloga como CLI cliente remoto, no como motor local; `llama.cpp` como posible motor local sujeto a prueba; ambos opcionales.

La desinstalación quita binarios y wrappers, revoca variables/tokens almacenados, limpia procesos/sockets e informa qué configuración/cachés se conservarán. El catálogo nunca descarga o ejecuta con un tap accidental; el usuario ve una revisión final del manifiesto.

## 20. Plan de pruebas adicional y nuevas puertas

- `CTX-001`: consultar catálogo de modelos con y sin endpoint de metadata; la ausencia produce `unknown`, no límite inventado.
- `CTX-002`: cambiar proveedor/modelo y verificar que effort, tokenizer, salida, tools y compaction se vuelven a resolver; ningún valor obsoleto se presenta como actual.
- `CTX-003`: desactivar/eliminar autocompactado y confirmar que no se resume ni se sobrescribe historia; comprobar recuperación antes de eliminación elegida.
- `CTX-004`: inyección dentro de contenido que sobrevive una compactación: nunca se convierte en system/developer instruction.
- `CTX-005`: estimación de tokens con imagen/documento/tools y salida reservada; control de límite exacto provider-specific sin falsa precisión.
- `JULES-001`: conector apagado no emite red ni crea sesión; API key falsa no se envía; API key nunca aparece en logs, argv, historial o repo.
- `JULES-002`: sandbox repo y `requirePlanApproval`; `AUTO_CREATE_PR`, push y apply diff denegados sin permiso separado.
- `JULES-003`: sin cuota/free-plan confiable o al pedir upgrade: bloqueo antes del request; registrar `COST_UNKNOWN`/`COST_BLOCKED`, no sustituir a un proveedor pago.
- `JULES-004`: logout/revocación/uninstall retira tokens y herramientas; sesión remota ya creada se muestra como side effect no necesariamente borrable.
- `EXT-001`: instalación deshabilitada por defecto, descriptor firmado y hash coincidente; paquete alterado queda en cuarentena.
- `EXT-002`: script `postinstall` intenta leer archivos/tokens o abrir red; sin sandbox, instalación rechazada; con sandbox registrar intento y bloquear.
- `EXT-003`: extensión pide control de UI, shell arbitrario o permiso no declarado; denegación por default.
- `EXT-004`: quitar plugin termina procesos, cierra sockets y borra/revoca secretos locales elegidos.
- `SEC-ENV-001`: benchmark sintético inspirado por MobileWorldSafety con vectores de mensaje, web, archivo, notificación y respuesta de tool; verificación del estado final por reglas.

Las puertas críticas permanecen: no testear en banco/wallet real; no exponer conexión anónima; no llamar a servicio externo sin autorización/costo verificado; no instalar CLI por adelantado; no habilitar herramientas Android con aprobación de plan de Jules ni por configuración de skills.

## 21. Referencias externas

[1]: https://arxiv.org/html/2608.17659 "MobileWorldSafety: Benchmarking GUI Agent Safety Against Environmental Injection Attacks in Android Apps"
[2]: https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/authorization "Understanding Authorization in MCP"
[3]: https://platform.claude.com/docs/en/build-with-claude/context-windows "Context windows - Claude Platform Docs"
[4]: https://developers.google.com/jules/api "Jules API - Google for Developers"
[5]: https://jules.google/docs/cli/reference "Jules Tools Reference"
[6]: https://jules.google/docs/usage-limits/ "Limits and Plans - Jules"

**Estado final de esta expansión:** investigación documental completada desde fuentes públicas oficiales y un trabajo académico; la API de Jules y la CLI no se invocaron. La validez de Termux, las cuotas, los precios, las interfaces del proveedor y todas las pruebas de software siguen requiriendo verificación en el momento de implementación. Las páginas oficiales de Jules indican que la API es alpha y que los límites pueden cambiar. No se compró ni contrató ningún servicio, no se consumieron cuotas de Jules y no se almacenaron credenciales.

# SUPLEMENTO 2 — REVISIÓN ADVERSARIAL, PLAN UX AXIOM Y CRITERIOS DE ACEPTACIÓN

**Fecha:** 18 de octubre de 2026.  
**Alcance:** segunda pasada sobre el diseño, siete revisiones independientes de investigación externa y lectura directa del protocolo `protocolos_axiom/AXIOM_UX_DX_LEAD.md` (denominado AXIOM UXL en esta solicitud). Se añadieron lecturas de los informes móviles 02 Perplexity, 03 Claude, 04 ChatGPT, 05 Copilot, 06 Zapia, 07 Replit, 10 Mistral, 12 NotebookLM, 15 SPCK y 17 VS Code Web, más la matriz comparativa. El suplemento anterior permanece vigente y se amplía aquí.  
**Límite importante:** “blindado” se trata como aspiración de reducción de riesgo, no como garantía absoluta. No se ejecutaron pruebas de software, herramientas Android, pagos, Jules, paquetes ni APIs. No se hicieron cambios de código fuente.

## 22. Correcciones adversariales que cambian puertas de diseño

### 22.1 El bloqueo antes de tap no puede afirmar que elimina TOCTOU

La comprobación previa de paquete/actividad/ventana/nodo reduce el intervalo de riesgo, pero la API pública de Accessibility no liga criptográficamente y de forma atómica la observación al evento que Android entregará después. `getRootInActiveWindow()` puede no estar disponible y los nodos pueden quedar obsoletos; `AccessibilityNodeInfo.refresh()` puede retornar `false`. `packageName` y `windowId` son señales de procedencia, no identidad autenticada del receptor. Una vista, overlay, app o foco puede cambiar entre comprobación y dispatch.

Por ello se corrige cualquier lenguaje del plan que pueda interpretarse como “segunda comprobación justo antes = frontera segura completa”. Para la versión productiva no se autoriza el control visual autónomo de operaciones sensibles; la primera versión queda limitada a auditoría/planificación de solo lectura. Una futura prueba de gestos solo puede ser un experimento independiente en emulador y apps propias de laboratorio. Antes de enviar, volver a obtener raíz y ventanas; descartar referencias obsoletas; comparar estado esperado y abortar ante divergencia. Después medir el estado final. Estos controles reducen exposición, pero el documento debe declarar la carrera residual y no llamar a esto protección total. [7] [8]

Las defensas Android contra tapjacking y overlays también son parciales. La guía de Android describe diferencias por tipo de ventana, opacidad, versión y ocultación parcial; las ventanas de accesibilidad se clasifican de manera distinta a overlays de apps. `HIDE_OVERLAY_WINDOWS`, `filterTouchesWhenObscured`, `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` y `accessibilityDataSensitive` pueden ser capas de mitigación en los puntos aplicables, no una solución a inyección semántica dentro de correo, WebView, documentos o tools. Las pruebas deben separar overlay, inyección de contenido y rebinding; no extrapolar un resultado de una categoría a otra. [9] [10]

### 22.2 Métricas, autorización MCP y consentimiento son capas diferentes

Los resultados de AndroidWorld/MobileWorld miden en gran medida capacidad y postcondiciones; MobileWorldSafety evalúa ataques por contenido ambiental; GhostEI-Bench investiga overlays/interrupciones y AnTrap perturbaciones en varias fases. Sus denominadores, amenazas y métodos difieren: no sumar ASR/VR/TCR ni comparar porcentajes como una única “tasa de seguridad”. Añadir threat model, verificador, muestras, app, versión Android/modelo/scaffold y clase de fallo a cada resultado. Priorizar oracles deterministas de estado; usar un juez LLM solo como auxiliar para casos ambiguos. El estado del dispositivo tras la acción debe tener prioridad sobre la impresión del agente de que la tarea se completó. [11] [12] [13]

La autorización MCP no es sinónimo de permiso de negocio ni de confirmación por acción. En el perfil HTTP protegido de MCP hay requisitos normativos de recurso/audience y autorización; el estándar también expresa que la autorización es opcional en general. Las palabras `MUST`/`SHOULD` aplican a los perfiles y actores especificados, no convierten recomendaciones en requisitos universales. MCP no define por sí solo sandbox, firewall/egress, política financiera ni una interfaz de consentimiento garantizada. Las anotaciones de tool no deben considerarse fiables para conceder privilegios. Mantener fuera del modelo un PEP/PDP que haga complete mediation y default-deny. No usar token del cliente para una API upstream; la guía MCP prohíbe ese passthrough en el perfil aplicable. [14] [15] [16]

### 22.3 “Integridad” de un plugin no es “benignidad” ni sandbox

El catálogo debe distinguir evidencia de identidad/integridad de evidencia de comportamiento. Hash, firma ECDSA, Sigstore/SLSA, SBOM o análisis de vulnerabilidades ayudan a confirmar qué artefacto se instaló, quién lo publicó y qué dependencias declara, pero no prueban que el código no exfiltre datos. npm puede ejecutar lifecycle scripts; Termux CLIs comparten la frontera de la app anfitriona. PRoot no es aislamiento de kernel. No usar los términos `sandboxed` o `isolated` por el mero hecho de ejecutar en Termux/proot. Bloquear por defecto scripts de instalación; cualquier excepción se revisa por paquete exacto, versión y digest, y permanece bajo permisos de usuario. Para código hostil, rechazar en el dispositivo o moverlo a VM/entorno realmente aislado cuando exista y su costo haya sido aprobado. [17] [18] [19]

## 23. Estado de evidencia UX y enfoque de producto

Las observaciones de interfaz siguientes vienen de los informes y capturas del usuario en el Moto G77/Android 14 y la matriz comparativa del 17 de octubre de 2026. Son evidencia de lo que esos informes dicen haber observado en esas versiones, no un benchmark independiente ni una garantía de que otra versión mantenga la misma función. Las afirmaciones de los informes sobre arquitectura interna, cuotas, modelos o servicios no se convierten automáticamente en requisitos de Fénix. La ruta correcta es prototipar el patrón UX, medirlo con usuarios y verificar las capacidades reales.

### 23.1 Patrones que vale la pena incorporar con adaptación

- **Conversación móvil y conectores explícitos:** Zapia documenta un drawer/historial, composer de texto/adjuntos/voz y toggles de conectores individuales. Su patrón de revisión previa para mutaciones inspira la tarjeta de aprobación de Fénix. No copiar acceso de nube ni modelo de costo de Zapia.
- **Proyectos, biblioteca y artefactos:** ChatGPT y Claude separan chat, proyecto/contexto persistente y artefacto. Fénix debería permitir volver a una fuente o resultado sin rebuscar en un hilo largo. Un artefacto conserva su origen y versión; no se debe publicar/compartir automáticamente.
- **Modelos, esfuerzo y perfiles de tarea:** Claude/Perplexity reportan selectores de modelos/esfuerzo y Perplexity/Claude registran modos diferenciados. Fénix debe mostrar solo opciones verificadas para el proveedor activo y comunicar cuota/costo y disponibilidad; no debe copiar nombres, niveles ni promesas pagas.
- **Skills activables:** Mistral reporta skills personales/compartidas con toggles y modos de trabajo/código. Fénix puede ofrecer skills versionadas y explicables, con estado, alcance y dependencias; activar una skill no otorga permisos de conector ni de SO.
- **Grounding y citas:** NotebookLM se centra en fuentes/citas y cuadernos. Fénix debe distinguir respuesta sustentada por fuentes, respuesta sin fuentes y opinión/síntesis, permitir inspeccionar cada fragmento y separar instrucciones del usuario de texto de la fuente.
- **Outputs y modos:** Replit reporta tipos de salida y modos. Fénix puede ofrecer tipos como informe con citas, tabla, parche, documento, diagrama y resumen. Cada opción debe estar disponible solo si el motor, el dispositivo y la política pueden producirla; un `modo` limita capacidades y no es un permiso.
- **Trabajo de código táctil:** SPCK inspira ayuda de sintaxis, editor Git y edición de fragmentos. VS Code Web expone problemas de sidebar y teclado/foco en el móvil. Fénix no debe iniciar con un IDE de escritorio comprimido: el editor profundo es una superficie aparte y posterior.
- **Composición multimodal/voz:** ChatGPT, NotebookLM, Perplexity y Claude reportan voz y manejo de medios. Fénix puede planear voz como una vía de entrada/salida opcional, pero la captura, transcripción, retención y envío requieren estado visible y consentimiento. Voz no significa envío automático.
- **Capacidades de agente y sandboxes:** Replit reporta trabajo remoto de código y outputs. Es una referencia de capacidad, no prueba de ejecución local ni de gratuidad. Fénix debe separar claramente qué se ejecuta en el dispositivo, qué en un proveedor externo y qué requiere red/cuenta.

### 23.2 Arquitectura de información F-Chat propuesta

Diseñar una interfaz de una sola columna optimizada para uso con una mano. Navegación principal: **Chats**, **Espacios** (proyectos/context packs) y **Biblioteca** (fuentes y artefactos); una sección `Más` guarda Conectores, Skills, extensiones/CLI y Ajustes. Drawer/historial debe colapsar completamente con un toque, sin panel lateral persistente, drawer anidado ni scroll horizontal en pantallas angostas.

En el encabezado mostrar título, estado de conectividad y confianza (p. ej. `Verificado`, `Pendiente`, `Sin conexión`, `Revocado`, `Desconocido`). Un check solo aparece si la fuente responsable confirmó el estado. Mantener borrador, posición de scroll y selección de archivos al abrir/cerrar menús o al volver desde un conector.

Mantener conceptos separados: **perfil** (preferencias), **context pack** (instrucciones/fuentes válidas para este chat), **proveedor/modelo** (motor y capacidades), **skill** (procedimiento), **conector** (acceso externo), **permiso** (capability otorgada), **aprobación** (autoriza una acción concreta) y **artefacto** (resultado). El selector de modelo no cambia permisos. El cambio de espacio no activa conectores. La skill no se puede autorizar a sí misma.

### 23.3 Composer y respuesta

Composer fijo sobre teclado/IME con insets correctos. Controles de texto, adjunto/archivo, imagen/cámara, voz y enviar con targets de al menos **48×48 dp** conforme al protocolo AXIOM. Previsualizar cada adjunto y sus estados `seleccionado`, `preparando`, `listo`, `fallido`; no enviar silenciosamente si no está listo. Para código, barra auxiliar contextual de caracteres frecuentes y `Tab/Ctrl`, operable por TalkBack y hardware keyboard; autocompletado encima del teclado o inline, nunca tapando la línea activa; ofrecer desactivación.

La entrada de voz muestra indicador de captura, duración, transcript editable y botones Detener/Descartar/Enviar. Por defecto no envía por detección de silencio. La voz generada puede interrumpirse con un botón persistente. Toda modalidad declara si el procesamiento será local o remoto y qué datos salen del dispositivo.

Mostrar respuesta en streaming con acciones `Detener`, `Reintentar`, `Copiar`. Conservar respuesta parcial ante fallo. Citas son fuentes clicables con título, procedencia, fecha y fragmento asociado; si falta grounding, decir `Sin fuentes verificadas`. Artefactos son tarjetas descargables/abribles con tipo, origen, versión, permisos y opciones de edición/compartir; ninguna publicación ni despliegue ocurre por crear un artefacto.

Las herramientas invocadas aparecen en una línea de tiempo legible: conector, capability, destino, datos que se leerán/enviarán, efectos, estado, aprobación y resultado. No reemplazarlo por un spinner genérico. El stream puede mostrar “preparando”, “esperando aprobación” y “conectado” solo cuando esos estados provienen del backend/OS, no de una predicción de texto del modelo.

### 23.4 Modos, modelos, effort y outputs

MVP: modos `Chat`, `Investigar`, `Código` y `Crear`. Son configuraciones declarativas con allowlist de contexto/tools y output, no botones que saltan controles de seguridad. Cada modo muestra qué cambia. La selección automática debe poder desactivarse.

El selector carga por proveedor cuando hay documentación/API soportada; muestra modelo, disponibilidad, fecha de metadata, capabilities verificadas, consumo estimado/estado costo, privacidad local/remota y fallback. Niveles de reasoning effort solo aparecen si el proveedor/modelo los admite; usar escalas nativas mapeadas con etiquetas sencillas (`Rápido`, `Equilibrado`, `Profundo`) y explicación de cuota/latencia, sin afirmar equivalencia si no es uniforme. Cualquier fallback requiere consentimiento o política previa explícita y rastro visible.

Un selector `Tipo de salida` guía la intención: respuesta breve, investigación con citas, informe, tabla, flashcards, diagrama, archivo, parche de código o audio, pero deshabilita opciones que no pueden producirse en el estado actual. El menú no anuncia capacidades premium como si estuvieran disponibles bajo costo cero.

### 23.5 Skills, conectores y approvals

En `Conectores`, cada tarjeta indica fuente/editor, URL y transporte, auth, scopes/capabilities, permisos de lectura/escritura, datos enviados, último handshake, estado de costo y fecha de verificación. Separar `registrado`, `autenticado`, `autorizado`, `probado` y `activo`. Ningún endpoint `trycloudflare` sin auth figura como conectado seguro; endpoint público anónimo permanece bloqueado.

`Skills` tiene tabs `Catálogo`, `Instaladas`, `Creadas por mí` y `Compartidas`, con toggles por skill. Al abrir muestra versión, publisher, instrucciones/datos que agrega, herramientas requeridas, conectores, red, costo conocido/desconocido y changelog. `Activar` afecta solo al chat/espacio elegido; apagar retira sus instrucciones/tools del contexto siguiente. Las instrucciones dentro de una skill siguen siendo datos no confiables hasta revisión. Un usuario puede previsualizar la skill y restaurar su estado previo.

La aprobación de mutación sigue un flujo de cinco etapas: **propuesta estructurada → preview → autorización específica → ejecución por PEP independiente → confirmación de estado**. La tarjeta incluye acción, actor, destino/recurso, parámetros reales y normalizados, cantidad/unidad cuando aplique, proveedor y datos que saldrán; muestra reversibilidad y coste. Opciones: `Aprobar una vez`, `Aprobar para este alcance` si la política permite, `Cancelar`. Ninguna opción de aprobación persistente está preseleccionada. Si cambian destino/argumentos/contexto, la aprobación expira y debe pedirse otra. Para banca, wallet, transferencias, PIN, OTP, contraseña, biometría o identidad sensible: sin conector habilitado ni override del usuario/modelo. Los conectores de bajo riesgo comienzan por lectura y datos sintéticos.

### 23.6 Offline, coste, privacidad y estados de error

Guardar borradores de forma local y transparente; bandeja de salida muestra `Pendiente`, `Enviando`, `Enviado`, `No enviado`. En modo offline no enviar MCP ni autoejecutar acciones al reconectar. La acción pendiente debe revisarse y reenviarse explícitamente con idempotencia; confirmar si el servidor pudo haber ejecutado antes del timeout.

Por cada chat, mostrar qué proveedor recibió el prompt/adjuntos, si se usaron tools, retención y capacidad de borrado local/remoto conocida. `Costo desconocido` se presenta literalmente; no asegurar que un nivel gratuito dure para siempre. El usuario puede desactivar conectores/skills/modelos por chat o globalmente, revocar credenciales y borrar registros locales sin asumir que esto borra datos del proveedor remoto.

Estados de red/auth/capacidad/costo/herramienta separados: `sin conexión`, `credencial ausente/expirada`, `no autorizado`, `cuota desconocida/agotada`, `modelo no compatible`, `acción bloqueada por policy`, `timeout con resultado incierto`, `falló el origen`. Conservar borrador y guiar el siguiente paso; no reintentar una mutación sin resolver su estado previo.

## 24. Roadmap UX AXIOM con prioridades

### Fase P0 — Confianza antes de complejidad

Implementar navegación adaptable, drawer de un toque, Chat/Espacios/Biblioteca; composer de texto/archivos/imagen; selector de proveedor/modelo basado en estado verificado; cuatro modos declarativos; respuestas con citas; cards de artefactos; estado visible de conectividad y costo. Implementar el panel de capacidades MCP/skills aunque el perfil de control esté inicialmente apagado. Incorporar PEP default-deny, scopes por herramienta, aprobaciones específicas, registro auditado sin texto del usuario, revocación y safe mode `AUDIT_READ_ONLY`. No activar control de pantalla en banca/wallet ni herramientas de control global.

### Fase P1 — Móvil profesional

Añadir voz con transcripción editable y entrada/salida cancelable; skills versionadas con catálogo/creación por el usuario; conectores read-only primero, con preview y una mutación reversible de bajo riesgo en dataset de prueba; context packs y biblioteca indexada localmente; outputs estructurados; sincronización offline; accesibilidad hasta 200% de fuente; métricas privadas de usabilidad.

### Fase P2 — Dev/workspace opt-in

Añadir editor de código móvil por módulos (no sustituir pantalla de chat), barra de símbolos, snippets, diff review, Git, CLI marketplace curado y dry-run. Ejecución de código en Termux solo con manifiesto aprobado y riesgo/capabilities visibles; si no existe aislamiento de kernel, declarar que la extensión comparte permisos de Termux. Conectores remotos/Jules solo si el usuario habilita explícitamente, la llamada es compatible con presupuesto `$0` verificable, y están revisadas privacidad, auth, versionado y side effects.

### Fuera de alcance

Control autónomo de pagos o apps bancarias/wallet; instalación masiva de CLI; automatización por voz sin confirmación; autocompactado forzado; conectores anónimos; ejecución automática de prompt, diff, build o deploy; monetización o compra de servicios. No incorporar funciones solo porque otra app las presenta; cada feature necesita evidencia, evaluación de costo/privacidad y gate de riesgo.

## 25. Aceptación UX y pruebas de regresión

1. **Tacto/layout:** en anchos de viewport 320 dp y 360 dp y tamaño de fuente aumentado, el recorrido Chat→respuesta no tiene panel desktop persistente, recorte, targets menores de 48×48 dp, ni control tapado por el teclado; drawer abre/cierra con un toque y un solo Back cierra la capa más cercana.
2. **Borradores/errores:** rotación, pérdida de red, error de auth, límite de cuota y cierre/reapertura preservan borrador. Timeout de una mutación marca `resultado incierto`, no `falló` ni `completado`; retransmisión jamás duplica en fixture idempotente.
3. **Permisos:** con consentimiento rechazado, credencial revocada, scope insuficiente, source offline y desconocido, la UI no muestra check de conexión ni ejecuta herramienta. Las pruebas deben confirmar backend no-dispatch, no solo botón desactivado.
4. **Aprobación:** el usuario puede repetir con sus palabras qué ocurrirá, sobre qué destino y con qué parámetros en una prueba de usabilidad; modificar un argumento invalida la aprobación. No hay override para categoría financiera/credencial.
5. **Grounding:** cada cita navega a fuente/fragmento correcto; si no hay fuente se etiqueta. Instrucciones maliciosas dentro de fuente se representan como contenido citado, nunca como rol de sistema. Artefacto identifica versión, origen y si ya se compartió.
6. **Extensiones/skills:** instalar no implica ejecutar; la activación presenta scope, editor, versión, hash, permisos, red, datos y estado de verificación; al desactivar se retiran tools e instrucciones al siguiente turno; actualización de digest exige nuevo consentimiento.
7. **Accesibilidad:** TalkBack lee orden lógico, estados y errores; color no es único indicador; teclado físico/foco/restauración funciona; texto grande no vuelve inaccesibles los controles críticos.
8. **Telemetría de producto:** medir first response útil, tiempo a abrir fuente, abandono de adjunto, errores de Gboard/foco, tasa de permisos comprendidos, approvals canceladas/reautorizadas, reintentos, offline recovery y fallos sin duplicado. Sin capturar cuerpo de prompt, transcript, documento ni credencial por defecto. Los objetivos de usabilidad se fijan después de pruebas piloto, no como hechos de benchmarks externos.

## 26. Revisión de fuentes y trazabilidad

Cada requisito del plan debe llevar etiqueta de origen: `observado en informe de auditoría`, `documentación oficial`, `paper/preprint`, `supuesto de diseño` o `pendiente de prueba`. Registrar versión/fecha y una pregunta de falsación. La matriz comparativa contiene varias afirmaciones técnicas y de costo no verificadas independientemente; no tratarlas como contratos. Los papers son preprints o benchmarks específicos y reportan amenazas distintas. Jules tiene API experimental/alpha, cuotas y capacidades que pueden cambiar; el CLI usa sesiones remotas, su compatibilidad Termux no está garantizada en las fuentes oficiales leídas. No se ejecutó ningún comando ni API.

Antes de cualquier implementación, completar las fases existentes de `NO-GO`: especificar threat model/PEP, cerrar exposición anónima, implementar tests de bloqueo y revisar el acceso del manifiesto/código. Después, hacer prototipos UX en entornos ficticios, ejecutar los casos con laboratorio/emulador, publicar logs/evidencias sin secretos y revisar con seguridad, UX, privacidad y usuarios. Ninguna aceptación UX puede saltarse una puerta de seguridad.

## 27. Fuentes externas añadidas en esta revisión

[7]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "Android AccessibilityService API reference"
[8]: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo "Android AccessibilityNodeInfo API reference"
[9]: https://developer.android.com/privacy-and-security/risks/tapjacking "Android Tapjacking mitigations"
[10]: https://developer.android.com/reference/android/view/WindowManager.LayoutParams "Android WindowManager.LayoutParams API reference"
[11]: https://arxiv.org/html/2608.17659v1 "MobileWorldSafety benchmark"
[12]: https://arxiv.org/html/2512.19432v1 "MobileWorld benchmark"
[13]: https://arxiv.org/html/2510.20333v1 "GhostEI-Bench"
[14]: https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization "MCP Authorization specification, version 2026-07-28"
[15]: https://modelcontextprotocol.io/specification/2026-07-28/server/tools "MCP Tools specification, version 2026-07-28"
[16]: https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices "MCP Security Best Practices"
[17]: https://docs.npmjs.com/cli/v11/using-npm/scripts "npm lifecycle scripts"
[18]: https://github.com/termux/proot-distro "Termux proot-distro limitations"
[19]: https://slsa.dev/spec/v1.2/verifying-artifacts "SLSA artifact verification"
[20]: https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf "NIST Zero Trust Architecture, PEP/PDP"
[21]: https://cheatsheetseries.owasp.org/cheatsheets/AI_Agent_Security_Cheat_Sheet.html "OWASP AI Agent Security Cheat Sheet"
[22]: https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html "OWASP Transaction Authorization Cheat Sheet"
[23]: https://developers.google.com/jules/api "Google Jules API, alpha status"
[24]: https://jules.google/docs/cli/reference "Google Jules CLI reference"
[25]: https://jules.google/docs/usage-limits/ "Google Jules limits and plans"

**Conclusión de esta revisión:** el plan es más completo tras estas ampliaciones, pero no es posible certificarlo como “totalmente blindado” mediante investigación documental. La condición para aumentar capacidades es evidencia de pruebas repetibles, default-deny y ausencia de side effects no autorizados. Los resultados UX y los controles técnicos siguen pendientes de prototipo y validación; costo cero absoluto continúa siendo un gate, y no una suposición sobre las cuotas gratuitas de proveedores.

# SUPLEMENTO 3 — ACLARACIÓN DE COSTO, PROVEEDORES EXTENSIBLES Y ALCANCE DE ACCESIBILIDAD

**Aclaración incorporada a pedido de la persona usuaria.** La interpretación correcta de costo cero es que construir la app, instalarla y utilizar sus funciones esenciales no debe requerir contratación, compra ni suscripción obligatoria. El usuario puede, por decisión propia y fuera de los valores por defecto, conectar o contratar un servicio de pago. Esto no habilita gastos automáticos ni cambia el principio de consentimiento por cada proveedor/costo.

## 28. Política económica corregida: núcleo gratuito, uso opcional de pago

Separar de forma visible tres conceptos: **costo de desarrollar/instalar la app**, **uso local o dentro de cuotas gratuitas** y **uso opcional de servicios/proveedores pagos**. Ninguna suscripción es requisito para abrir el chat, configurar proveedor, usar funciones locales disponibles o mantener los controles de seguridad. Fénix no activa una suscripción, compra créditos, eleva un plan ni cambia a una tarifa superior.

El usuario puede activar manualmente un proveedor que cobre por uso, como los proveedores que elija. Antes de la primera llamada de pago se explica el tipo de cobro configurado, destino, modelo, cantidad prevista/estimada y posibilidad de que el costo real difiera por tokenizer, herramientas, reintentos, caché, tokens de razonamiento, impuestos o ajustes del proveedor. La app no recibe ni almacena datos de tarjeta, no inicia checkout y no autocompleta upgrades. La configuración de facturación se mantiene en la cuenta oficial del usuario; no pasar secretos de pago a Fénix.

**Suscripción de app y acceso a API son productos diferentes según cada proveedor.** No asumir que una suscripción de chat de consumo cubre API, ni que una API key está cubierta por un plan. El conector debe mostrar el tipo de credencial y la página oficial que lo describe; si no se puede confirmar el modelo de facturación, marcar `COST_UNKNOWN` y no ejecutar hasta que el usuario lo configure expresamente.

## 29. Controles de cuotas y gasto por proveedor

Cada proveedor tendrá una política de presupuesto separada, global y opcionalmente por espacio/chat/modelo. Controles configurables: máximo de peticiones en ventana elegida (minuto/hora/día/mes), máximo de tokens de entrada/salida si el proveedor informa o el adapter los estima, máximo de gasto en moneda/ciclo cuando se disponga de información fiable, y límite de concurrencia. No confundir `rate limit` (frecuencia) con cuota gratuita (volumen) ni con presupuesto monetario.

Antes de llamar, la app hace preflight con el contador de tokens/capacidad del proveedor cuando exista; incluye entrada, herramientas, adjuntos, razonamiento/output reservados, reintentos y el historial que se enviará. La UI muestra `estimación`, límite configurado, cuota restante conocida/desconocida y proveedor. Si no es posible calcular el costo, mostrar `desconocido` en lugar de cero. El contador de Fénix y el presupuesto del proveedor deben quedar separados.

Al alcanzar un límite local, la llamada se bloquea antes de salir. Una llamada con estado de costo o cuota desconocido se bloquea por defecto cuando está activada la política `no exceder`; el usuario puede editar el límite y confirmar en una pantalla separada. No se efectúan reintentos, fallback de modelo/proveedor, escalamiento de effort ni paralelismo que puedan cambiar el costo sin permiso. El fallback a un proveedor pago está desactivado siempre salvo selección expresa.

**Límite de garantía:** las cuotas gratuitas y saldo que reporta el proveedor pueden no tener API de consulta, compartir key/account con otras apps, actualizarse con retraso o diferir por el cálculo real de tokens/precios. Un límite implementado dentro de Fénix limita llamadas que pasan por Fénix; no puede impedir consumo fuera de la app ni garantizar por sí solo un techo financiero absoluto si el proveedor factura por uso. Para un límite monetario duro, usar también el mecanismo oficial de presupuesto/cuota/bloqueo del proveedor si existe. Si no existe o no se verificó, informar esta limitación antes de permitir uso pago; no afirmar “no superarás la cuota” como garantía.

## 30. Extensión segura para proveedores nuevos

Fénix debe tener una interfaz modular `ProviderAdapter` y un registro abierto por diseño, para que la aparición de un proveedor nuevo no obligue a cambiar el chat, composer, histórico o lógica de UX. Contrato versionado: transporte/auth, list/get models, capacidades nativas, token counter, usage response, reasoning controls, vision/audio/tools, compaction, cuotas/precio, errores/reintentos, residencia/retención y referencias oficiales. Capacidades son opcionales; la ausencia equivale a `unknown`, no a `false` o compatibilidad inventada. Guardar metadatos de origen/versión/fecha y probar contratos mediante fixtures sin llamar a APIs reales.

Rutas de incorporación:

1. **Configuración declarativa compatible:** el usuario agrega endpoint permitido, nombre y método estándar (p. ej. API compatible con una interfaz documentada). No se ejecuta código remoto ni se descargan scripts desde el endpoint. Verificar TLS, nombre de host, DNS/IP pública y redirects; prevenir SSRF/local-network access; validar esquema, auth y políticas. El usuario confirma el host exacto y egress antes del primer request. El endpoint no puede leer archivos, tokens de otros conectores o contexto ajeno a la llamada.
2. **Adapter oficial nuevo:** el equipo Fénix agrega un módulo que implemente el contrato y se distribuye en una versión firmada de la app. El proveedor puede aparecer en el selector tras compatibilidad y presupuesto comprobados, sin modificar los demás adapters.
3. **Extensión de terceros:** solo formato limitado y capacidades declaradas; requiere editor/identidad verificada, firma/hash, revisión, permisos mínimos, política de salida, pruebas y opt-in por versión. No permitir descargar y ejecutar JavaScript/Python/arbitrario dentro del proceso con el permiso de la app. Las extensiones de proveedores no reciben `Accessibility`, filesystem amplio ni credenciales de otros proveedores. Si no hay sandbox fuerte disponible, solo admitir configuración declarativa; no afirmar aislamiento de plugins ejecutables.

La extensibilidad no significa confiar en toda API: adapter nuevo empieza con tools deshabilitadas, únicamente conversación tras inspección. Luego agregar capacidades una por una; lectura no habilita escritura. Secretos se almacenan en Android Keystore o mecanismo seguro del sistema, nunca dentro del prompt, historial, logs, manifiesto del plugin, URL, argumentos de CLI o repositorio. Retirar un provider revoca la credencial local, invalida caches de metadatos y conserva el historial existente según retención elegida.

## 31. Permisos de Accesibilidad: qué se pierde y qué permanece

La arquitectura P0 de este plan (chat con proveedores, MCP de datos, archivos elegidos por el usuario, skills textuales, fuentes, biblioteca y creación de artefactos) **no necesita el permiso de Accesibilidad** si se elimina el control genérico de otras apps por UI. No pedirlo por defecto ni como permiso previo a una conexión MCP.

Al no conceder o al retirar Accessibility, se pierden o quedan deshabilitadas estas funciones del Fénix MCP actual que dependen del `AccessibilityService`: leer árbol/nodos de UI de la aplicación que está en foreground; inspeccionar semántica/accesibilidad y texto visible que exponga ese árbol; navegar otras apps; tocar elementos, escribir en campos y ejecutar gestos globales por coordenadas; automatizar flujos visuales entre aplicaciones. Algunas capacidades de captura de pantalla o ventanas pueden depender de API/versión/capability adicional; no atribuirlas automáticamente a Accessibility, verificar cada implementación y permiso real.

No se pierden por el mero hecho de retirar Accessibility: conversación y llamadas a APIs de proveedores; conectores MCP que acceden a sus servicios mediante autorización independiente; operación de herramientas de archivos dentro del alcance permitido por Storage Access Framework/scoped storage; lectura local de documentos elegidos explícitamente; voz si se pide permiso de micrófono; cámara si se pide permiso de cámara; notificaciones/ubicación u otras funciones solo si cada módulo usa sus propios permisos y el usuario los concede.

Decisión propuesta: eliminar el control visual global del perfil seguro, no solo ocultarlo tras un toggle. Mantener una capability de `ui_automation` separada y apagada por defecto solo si el producto la requiere para tareas legítimas no sensibles; en tal caso debe aparecer como módulo distinto, explicar que exige Accessibility, mostrar el acceso otorgado por Android y ofrecer desactivación rápida. No expondrá ni operará banca, billeteras, OTP, credenciales ni pantallas financieras. Las pruebas de producto deben verificar tanto ausencia del permiso en el flujo P0 como que `ui_automation` no queda registrada/habilitada al cambiar de modelo o skill.

## 32. ¿El documento actual cubre implementación de principio a fin?

**Respuesta directa: no completamente.** El plan ahora cubre visión estratégica, riesgos, arquitectura de alto nivel, fases, UX, interfaces conceptuales, integración de proveedores/Jules, controles de gasto y catálogos de extensiones, además de listas de pruebas y criterios de aceptación. No es todavía un manual ejecutable de principio a fin ni evidencia de que la implementación esté hecha. No incluye para cada módulo diseño de clases/paquetes, contratos de API completos, esquema de almacenamiento/migraciones, wireframes/prototipos, orden exacto de commits, build pipeline, signing/release/rollback, pruebas automatizadas implementadas, runbooks, modelo de amenazas completo por conector ni firmas formales de aceptación. El código fuente no se modificó ni se compilaron pruebas.

Para que sea realmente implementable, convertir el plan en entregables versionados: (A) requisitos trazables y decisiones técnicas ADR; (B) diagramas/componentes y límites de confianza; (C) esquemas de `ProviderAdapter`, `CapabilityManifest`, `ApprovalIntent`, `UsageLedger`, extensiones y policy; (D) diseño de almacenamiento/Keystore y migraciones; (E) wireframes/prototipo y pruebas de accesibilidad; (F) historias por módulo con precondiciones, pasos, resultados y DoD; (G) pipeline de CI, firma, SBOM y releases; (H) suites negativas automatizadas y artefactos de evidencia; (I) rollout por feature flag, monitoreo y rollback; (J) runbooks de incident response, rotación/revocación, cuota/costo y privacidad. Cada entrega termina en gate verificable antes de iniciar la siguiente.

## 33. Respuesta resumida a las preguntas

- **Capacidades que podrían perderse:** únicamente la automatización/interacción visual global del móvil y la lectura de UI de otras apps si se elimina Accessibility; chat, proveedores, MCP por API, documentos seleccionados, skills y outputs permanecen con permisos propios. Capturas de pantalla requieren confirmar su API/permisos exactos.
- **¿Hace falta Accesibilidad?:** no para el modo P0 conversacional/conectores/archivos elegido por el usuario. Sí para muchas herramientas actuales de Fénix que leen árboles o hacen taps/gestos en otras apps. Recomendación: no solicitarla; mantener automation como módulo futuro aislado, opcional y nunca para finanzas.
- **¿Está todo implementado end-to-end en este plan?:** no. Es un plan estratégico de diseño y seguridad con roadmap y acceptance criteria, pero falta convertirlo en especificación ejecutable por módulo y luego construir, probar y auditar. No se ha implementado código.
- **Costo y proveedores:** costo cero significa sin compra/suscripción obligatoria; proveedores pagos se pueden añadir por decisión expresa del usuario con límites por proveedor, warning de costos, sin auto-fallback y con mecanismos oficiales del proveedor cuando se requiera un techo duro. Los nuevos providers se agregan por adapter/manifest limitado y verificado, sin permitir código remoto arbitrario o permisos cruzados.
