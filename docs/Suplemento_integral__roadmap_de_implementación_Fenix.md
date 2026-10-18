# Suplemento integral: roadmap de implementación Fénix / F-Chat AXIOM

**Fecha:** 18 de octubre de 2026  
**Relación con el plan base:** suplemento de ejecución para añadir al final de `PLAN_MAESTRO_IMPLEMENTACION_MCP_AXIOM.md`. Se basa en el plan móvil existente (643 líneas, inspeccionado en modo lectura), sus hallazgos y siete revisiones especializadas con una síntesis final. No reemplaza la evidencia estática ni convierte una propuesta en una prueba aprobada.  
**Estado:** plan de entrega, no código ni certificación. El plan puede aspirar a reducir riesgo, pero no existe un sistema “totalmente blindado” ni una garantía absoluta de seguridad.  
**Límite de esta revisión:** investigación y diseño; no se ejecutaron pruebas del proyecto, no se llamó a APIs de proveedores, no se usaron credenciales, no se compraron servicios ni se modificó el código. El conector móvil disponible para esta tarea se mantiene dentro del alcance de inspección de solo lectura; este archivo complementario quedó en el entorno Sandbox y no se escribió en el almacenamiento del teléfono.

## 1. Corrección ejecutiva que prevalece sobre las hipótesis anteriores

El documento base cubre hallazgos críticos, arquitectura de seguridad, UX, costes, proveedores extensibles, Jules, contexto y suites propuestas. Sin embargo, él mismo reconocía que aún no equivalía a una especificación ejecutable de extremo a extremo. Este suplemento convierte esa visión en una secuencia de fases con entradas, paquetes de trabajo, artefactos, pruebas, condiciones GO/NO-GO, rollback y operación hasta retirada.

Se precisan cinco decisiones que deben prevalecer cuando las secciones previas usen lenguaje ambiguo:

1. **Coste:** construir y distribuir el núcleo debe ser posible sin contratar servicios de IA ni exigir una suscripción. Un proveedor comercial puede habilitarse por decisión separada, explícita y revocable del usuario, quien configura su propio medio de acceso/billing fuera de Fénix. No se compran créditos, no se sube de plan ni se crea una suscripción automáticamente. Límites locales de Fénix protegen solo el tráfico que pasa por Fénix; no garantizan una cuota externa ni un techo financiero si el proveedor no lo ofrece y aplica.
2. **Accesibilidad:** el chat, selector de modelos, fuentes elegidas por el usuario, biblioteca, archivos con permisos propios, voz con permiso de micrófono y conectores API no necesitan por sí mismos el permiso Accessibility. La superficie de lectura/control visual de otras apps sí depende del AccessibilityService actual. Se propone que solo un componente first-party mínimo sea dueño del servicio y actúe como broker; la app base puede omitirlo. Esto es una separación de privilegios, **no** un método para evadir Google Play o Play Protect.
3. **Política Android:** si el propósito principal no es asistir a una discapacidad concreta, no etiquetar el servicio como `isAccessibilityTool=true`. Las políticas consultadas exigen transparencia y consentimiento, limitan la automatización general a acciones estrechas y claramente entendidas y prohíben planificación/ejecución autónoma bajo la API fuera de la excepción asistiva elegible. La clasificación exacta debe validarse antes de construir/publicar.
4. **Instant Apps:** Google Play Instant dejó de estar disponible desde diciembre de 2025 según la documentación oficial consultada. Se elimina como opción de distribución. Si se necesita descubrimiento, usar enlaces a la app normal. No describir la extensión como Instant App.
5. **Marketplace:** se puede impedir que los complementos aceptados por el marketplace oficial de Fénix declaren o sustituyan el servicio privilegiado; no se puede impedir globalmente que un tercero publique por fuera otra app Android. Android no ofrece un sandbox de plugins de marketplace completo por defecto. Firma, hash, provenance, revisión o Play Integrity son señales y capas, no prueba de benignidad ni autorización runtime.

**Veredicto de seguridad heredado:** mientras siga abierto el hallazgo R-01 sobre acciones por coordenadas, el stub `isAntiBanFirewallEnabled()` y la falta demostrada de un PEP completo, no habilitar control visual general sobre el teléfono ni interacción con banca, pagos, autenticadores, credenciales o wallets. La versión inicial del MCP debe seguir siendo `AUDIT_READ_ONLY` / `PLAN_ONLY`, con apps sintéticas y datos de laboratorio para cualquier experimento.

## 2. Producto objetivo y alcance por entregas

Fénix debe reunir capacidades útiles de las aplicaciones estudiadas sin clonarlas literalmente ni asumir que sus precios, permisos o modelos de servicio son replicables. La interfaz debe ser original y móvil primero: Chats, Espacios/Proyectos y Biblioteca; selector de proveedor/modelo/capacidad; reasoning effort cuando sea nativo; streaming recuperable; fuentes y citas inspeccionables; artefactos con versiones; voz opcional; Skills Axiom como procedimientos; conectores MCP con scopes; y módulos Termux/CLI opt-in. Todo acceso, coste, transferencia de datos y acción debe tener un estado visible.

**MVP propuesto:** chat con fake adapters durante desarrollo, adapter local opcional solo después de medir el dispositivo, selección dinámica de proveedores habilitados, proyectos/context packs, adjuntos elegidos conscientemente, fuentes/citas con procedencia, streaming y errores recuperables, settings explícitos de datos/coste, accesibilidad de interfaz, telemetría privada mínima y perfil sin control de pantalla. No obligar a usar API comercial para abrir/configurar la app.

**Entregas posteriores, condicionadas a seguridad y utilidad:** proveedores remotos opt-in; auto-compaction opt-in; herramientas MCP separadas por lectura/preparación/ejecución; voz; artefactos; Skills; catálogo de extensiones; Termux/CLI bajo demanda; y, solo si existe un caso de uso legítimo aprobado, la extensión de accesibilidad first-party. Ningún modo Axiom, skill, nivel de razonamiento, conector o extensión concede permisos por sí mismo.

## 3. Cadena de fases y dependencias

| Fase | Resultado de salida | Dependencias críticas |
|---|---|---|
| 0. Gobierno, inventario y baseline | Alcance, responsables, fuentes/claims, estado real del producto y riesgos | Acceso de lectura a repositorios y documentos; no requiere APIs pagas |
| 1. Requisitos, privacidad, UX y threat model | PRD trazable, journeys, riesgos, datos y criterios de aceptación | Fase 0 |
| 2. Arquitectura y contratos | ADRs, schemas, prototipos, contratos de provider/context/PEP/extensiones | Fase 1 |
| 3. Núcleo comprobable sin coste de proveedor | Build/test local con fake adapters, datos sintéticos y CI sin egress | Fase 2 |
| 4. Proveedores, cuotas y contexto | Gateway, adapters y uso opt-in con controles internos | Fases 2–3; validación de cada proveedor en fecha de integración |
| 5. F-Chat, UX y funciones de producto | MVP funcional progresivo, usable, accesible y verificable | Fases 2–4 |
| 6. Broker de Accessibility first-party | Módulo privilegiado separado, solo si el caso y la política lo permiten | Fases 0–5; aprobación legal/privacidad/seguridad y política de Play |
| 7. Marketplace y extensiones/Termux | Catálogo gobernado, sin sustitutos de Accessibility ni código no controlado | Contrato de broker y de extensiones; supply chain y revisión |
| 8. Verificación integrada, beta y distribución | Evidencia de release, gates, soporte y rollout | Fases 0–7 según módulos incluidos |
| 9. Operación, mantenimiento y retirada | Monitoreo, respuesta, compatibilidad, deprecación y cierre | Release aprobada y equipo/roles asignados |

No se fija duración, headcount ni presupuesto de infraestructura porque faltan datos del equipo, producto, tráfico, jurisdicción, canales y contratos. Esas estimaciones se deben producir al cerrar Fase 0 y actualizar en cada gate.

## 4. Roadmap ejecutable por fase

### Fase 0 — Gobierno, alcance, inventario y baseline

**Objetivo:** convertir el pedido en un backlog con hechos y desconocidos diferenciados, conocer el estado real del software y designar quién puede decidir sobre seguridad, privacidad, UX, release y soporte.

**Trabajo:** nombrar roles (Product Owner, Tech Lead, Security Owner, Privacy/Legal Owner, QA/Release Manager, SRE/Support; los nombres se asignan después); inventariar repositorios, ramas, módulos, application ID, versión, SDK/target API, permisos, manifiestos, servicios, endpoints, backend, claves de firma (sin leer secretos), stores, SDK de terceros, datos, telemetría, incidentes, equipos compatibles y costes actuales. Verificar si F-Chat, MCP, Skills Axiom, voz, biblioteca, artifacts, Jules, contexto automático y CLI ya están implementados o solo descritos. Crear una tabla de claims con estado `OBSERVADO`, `ESTÁTICO`, `DOCUMENTAL`, `INFERIDO`, `DESCONOCIDO` o `PENDIENTE`, fuente, fecha, dueño y pregunta de falsación.

**Artefactos:** Project Charter; RACI; RAID log; inventario de software/datos/permisos/proveedores/canales; matriz de usuarios y tareas; índice de evidencia; política económica corregida; primera matriz de compatibilidad; definición de éxito y lista de no-objetivos.

**Verificación y salida:** cada requisito crítico tiene propietario, fuente o decisión, riesgo, acceptance test y dependencias. Cero endpoint de prueba remoto anónimo en producción. Gate **NO-GO** si se desconoce qué build/código se está planificando, qué datos recoge un módulo privilegiado o quién aprueba cambios de seguridad. No generar cotizaciones ni promesas de calendario hasta tener baseline.

**Rollback:** devolver cualquier claim contradictorio a `DESCONOCIDO`; congelar estimaciones; limitar trabajo a inventario, sin habilitar acciones ni integraciones externas.

### Fase 1 — PRD, privacidad, riesgos y UX de extremo a extremo

**Objetivo:** decidir qué problemas se resuelven primero y qué datos, permisos, acciones y dependencias son necesarios.

**Trabajo:** entrevistas y observación de tareas con usuarios representativos; construir journeys para preguntar/recibir cita, archivo a artefacto, cambiar proveedor, activar skill, usar conector, dictar voz, compactar contexto y revocar una autorización. Separar necesidades de accesibilidad de automatización general. Crear Data Flow Diagram y data inventory: dato, origen, finalidad, destinatario, región, retención, cifrado, borrado/exportación y base legal que deba revisar asesoría. Hacer threat model de prompt injection, MCP, SSRF/egress, secretos, contexto, cross-project leakage, fraude, Accessibility, marketplace, supply-chain, robo de cuenta y costes ambiguos. Marcar no-objetivos explícitos: pagos/operaciones financieras, control autónomo de apps, shell remoto por defecto, auto-upgrade pagado, Instant Apps, extensiones third-party con Accessibility, y uso de API paga sin opt-in.

**UX:** sistema propio que toma inspiración funcional (selector, espacios, citas, voz, tareas y artefactos) de las auditorías, pero no copia marcas/layouts. Diseñar estados de carga, error, offline, cuota, `unknown`, permiso denegado, resultado incierto, fuente no citada, `stale`, proveedor remoto/local y revocación. Probar texto grande, TalkBack, foco, teclado, contraste y uso con una mano desde prototipos.

**Artefactos:** PRD; journeys/service blueprint; wireframes; Data Inventory/DFD; threat model/abuse cases; matriz de permisos; privacy assessment; lista de NFRs (latencia, memoria, batería, disponibilidad, tamaño, accesibilidad, seguridad); tabla de riesgos y criterios de aceptación.

**Gate:** cada feature tiene caso de uso, consentimiento requerido, datos, costo, error seguro y prueba. Un feature ambiguo vuelve a discovery. La priorización no depende de “tener todas las capacidades” sino del valor probado y del riesgo acotado.

### Fase 2 — Arquitectura, interfaces, decisiones y prototipos técnicos

**Objetivo:** congelar las fronteras de módulos antes de implementar y evitar el acoplamiento a un único proveedor o a capacidades no verificadas.

**Decisiones de arquitectura:** separar UI, dominio, almacenamiento, proveedores, `ContextManager`, `BudgetPolicy`, `CapabilityResolver`, MCP gateway, broker de acciones, skills y marketplace. Definir contratos y versiones para `ProviderAdapter`, `ModelDescriptor`, `CapabilityManifest`, `ContextItem`, `TokenEstimate`, `Usage`, `LimitSnapshot`, `CostEstimate`, `NormalizedError`, `ApprovalIntent`, `PolicyDecision` y `ExtensionManifest`. Tipar estados como `known`, `stale`, `unknown`, `unsupported`, `estimated`, `denied`, `ambiguous` y `revoked`; unknown no implica permitir. Provider registry admite adapters declarativos solamente cuando tests prueban equivalencia real; diferencias de autenticación, payload, uso, herramientas, streaming o errores exigen adapter específico.

**Contexto:** mantener log canónico y provenance por separado de la proyección que se manda a un proveedor. La compactación no reescribe el historial canónico. Diferenciar compactar, no reenviar un resultado y borrar almacenamiento; cada acción tiene política y efecto visible.

**Prototipos:** probar riesgo de streaming/reconexión, cambios de proveedor, conteo de tokens multimodal, cancelación, redacción en origen y propuestas de acción con aprobación. Crear ADRs para secretos, egress, identidad del plugin, IPC, migraciones, offline y rollback.

**Artefactos:** arquitectura actual/objetivo; ADRs; schemas versionados; UI navegable; contrato de errores/idempotencia; threat model actualizado; plan de migración/compatibilidad; matriz de proveedor-capability; prototipos técnicos reproducibles.

**Gate:** revisión de producto, seguridad, privacidad, QA y arquitectura. Contratos se validan con fake adapters y tests de propiedad. No se da por aceptada una API porque “sea compatible con OpenAI” o porque un CLI arranque.

### Fase 3 — Núcleo F-Chat sin llamadas pagas y pipeline de ingeniería

**Objetivo:** construir un núcleo que compile y permita probar journeys sin API keys ni tráfico a servicios comerciales.

**Trabajo:** implementar conversación, proyectos/context packs, selección de fuentes, almacenamiento con permisos, streaming simulado, cancelación, respuestas parciales, errores recuperables, artefactos mock/versionados, settings de privacidad/coste, estados de proveedor y UX de selector con fake adapters. En paralelo crear CI reproducible con unit/integration/instrumentation tests, lint, SAST/SCA, secret scan, license scan, SBOM, digest de artefacto y provenance. Bloquear egress externo en tests ordinarios; cualquier prueba live debe quedar separada y requerir credenciales y presupuesto aprobados por el usuario.

**Privacidad:** instrumentación mínima sin prompt/respuesta/transcripción/nombre de archivo/raw Accessibility por defecto. Borradores locales y estado de envío visible; no ejecutar acciones pendientes al reconectar.

**Pruebas:** instalación limpia y upgrade, offline, red lenta, proceso recreado, migración, rotación de pantalla, aislamiento de proyectos, eliminación/exportación, accesibilidad, fallos de API simulados y secreto buscado en APK/logs/artefactos.

**Artefactos:** build de desarrollo; simuladores; suite offline; pipeline CI; SBOM y provenance; diccionario de eventos permitidos; dashboard de test; runbook de reinicio de datos sintéticos.

**Gate:** build y journeys de aceptación pasan sin internet ni secretos; telemetría no filtra contenido; una migración fallida no destruye el historial. Si el núcleo necesita tráfico pagado para existir, se replantea el alcance.

### Fase 4 — Proveedores, gateway, cuotas y política de coste

**Objetivo:** integrar servicios remotos de forma opt-in, extensible y con límites honestos.

**Arquitectura:** llamadas externas pasan por gateway. La app móvil no lleva API keys; usa sesión autenticada de corta duración. Secretos son gestionados por secret manager/KMS y referencias (`SecretHandle`), separados por entorno, con rotación/revocación y logs sin valores. Egress es deny-by-default, con host allowlist, HTTPS, validación de destino/IP, restricciones de redirección y defensas DNS rebinding/SSRF. No aceptar URLs arbitrarias desde usuario, modelo o plugin.

**Contrato de adapter:** descubrimiento de modelos opcional y cacheado; capabilities por modalidad/razonamiento/tools/streaming/contexto; validación de request; contador `exact/estimated/unknown`; normalización de usage, errores, Retry-After y estado de streaming; precios versionados solo cuando hay fuente; `LimitSnapshot` con origen, hora y caducidad. Los valores remotos se actualizan al arranque/cambio/TTL, no por polling continuo. No llamar al endpoint de metadata sin consentimiento, credencial y política de cuota cuando corresponda.

**Cuotas y gasto:** límites locales por proveedor, cuenta/credential, usuario/tenant, modelo y workspace: requests, tokens input/output/reasoning, tamaño multimodal, concurrencia, cola y presupuesto interno. Reserva atómica antes de envío, liquidación con usage real, límite de salida y margen. Una llamada con timeout tras envío o stream incompleto queda `ambiguous/unknown`, nunca “costo cero”. No replay ciego de solicitudes con posible side effect o cargo. La cuota del proveedor, rate-limit, presupuesto interno y billing son categorías separadas. Header observado o dato stale no garantiza saldo global. Cuando el proveedor no expone cuota, mostrar `unknown`; las restricciones de Fénix solo limitan tráfico que pasa por su gateway.

**Opt-in:** usuario registra cuenta/proveedor y decide si usa un servicio con coste. La app no manipula tarjeta, no abre checkout ni confirma suscripciones/upgrade. Debe comunicar qué cuenta/API se usa, qué datos salen y qué coste puede no estimarse con exactitud. No asumir que plan de chat del consumidor incluye API. Jules sigue siendo una extensión remota opcional, apagada por defecto; integración no es API oficial de MCP salvo que Google publique tal soporte.

**Gate:** contract, SSRF, secreto, cuota concurrente, 429/5xx, billing, streaming y rollback tests pasan con fixtures. Pruebas live no son requeridas por CI y no pueden hacerse sin autorización y presupuesto explícitos.

### Fase 5 — Reingeniería de F-Chat y funciones de usuario

**Objetivo:** convertir contratos seguros en experiencia moderna, coherente y adaptable a pantalla móvil.

**Secuencia de producto:**

1. **Selector de modelo:** proveedor activo, modelos disponibles y capabilities comprobadas, origen/fecha de metadata, local/remoto y coste/cuota known/unknown. Reasoning effort solo si el modelo lo soporta; no fingir paridad.
2. **Conversación y proyectos:** Chats, Espacios y Biblioteca; instrucciones de proyecto aisladas; cambio de espacio no activa conectores ni permisos.
3. **Fuentes y grounding:** cada cita abre fragmento/posición, muestra procedencia/fecha; si no hay fuente verificada, indicarlo. Documentos externos son datos no confiables.
4. **Streaming y artefactos:** progreso real, cancelar, preservar parcial, reintento idempotente, preview seguro, historial, exportación; crear artifact no publica/despliega nada.
5. **Voz:** permiso del micrófono separado, transcripción editable, captions, interrupción y alternancia a texto; indicar procesamiento local/remoto y no enviar automáticamente al detectar silencio.
6. **Skills Axiom:** catálogo, versión, inputs, datos añadidos, tools, permisos, riesgos, modelo y coste cuando se conozca. Son plantillas de trabajo, no autoridad; una skill no puede activarse ni autorizarse a sí misma.
7. **Conectores MCP:** health/status diferenciados (`registrado`, `autenticado`, `autorizado`, `probado`, `activo`); lectura, propuesta y mutación como scopes separados. Confirmación muestra actor, destino, datos, efecto y reversibilidad. Token MCP se valida para su resource/audience y jamás se reenvía como token upstream.
8. **Perfiles Axiom:** UX/DX, Criticón, Deep Research, etc. solo alteran metodología/instrucciones; no amplían autorización. El nivel reasoning effort puede aumentar latencia/coste, que se muestra cuando se puede medir.

**Accesibilidad de UI de F-Chat:** objetivo a definir con usuarios y pruebas; incluir orden de foco, labels, TalkBack, teclado, zoom, contraste, movimiento reducido, captions, tamaño táctil legible, estados no basados solo en color y pruebas con tecnología asistiva. Esto es distinto del permiso `AccessibilityService` que controla otras apps.

**Gate:** journeys de MVP pasan en instalación limpia, offline/reintento, permisos denegados y con lector de pantalla; fuentes y coste se entienden; no hay fuga entre proyectos; ningún conector ejecuta mutación sin PEP.

### Fase 6 — Broker y extensión first-party de Accessibility (opcional y separada)

**Objetivo:** solo si Fase 0–5 demuestra necesidad legítima, construir el único componente privilegiado de Fénix para UI de otras apps; no bloquear funciones normales detrás de Accessibility.

**Precondición no negociable:** revisar el propósito central con asesoría y política vigente. Para automatización general no declarar `isAccessibilityTool=true`; cumplir disclosure, consentimiento, declaración, ficha, Data Safety y demás normas. Si el propósito principal real es asistencia a discapacidad, documentarlo y seguir el flujo específico de elegibilidad. La guía consultada prohíbe planificación/ejecución autónoma general y no convierte HITL genérico en autorización ilimitada.

**Diseño:** servicio mínimo, solo eventos/flags imprescindibles; app base sin servicio si la separación tiene valor real; broker first-party como PEP de cada lectura/exportación/acción; API IPC tipada; no exponer `AccessibilityEvent`, `AccessibilityNodeInfo`, Binder handles, raw tree, shell o evaluación de código. Complementos terceros no pueden pedir el bridge. Separar `observe`, `propose` y `commit`. Leases de autorización cortas, de un uso, revocables y ligadas a usuario/sesión/plugin/versión/capability/target. Paquetes financieros, wallets, auth y secretos quedan fuera del perfil, no tras un botón de “permitir”. La clasificación de texto/paquete es insuficiente para autorizar una acción sensible.

**TOCTOU y límites:** Android no ofrece transacción atómica de observar UI y enviar gesto. Re-query/refresh, verificar ventana/package/foco/acción y abortar ante estado obsoleto o divergencia reducen el riesgo; no lo eliminan. Las acciones por coordenadas son especialmente débiles y permanecen fuera del modo autónomo general. Apps sintéticas propias/emulador, nunca cuentas reales.

**Distribución y Play Protect:** preparar declaration, video, disclosures, listing y políticas por paquete. Probar warnings/bloqueos y su soporte operativo por mercados/dispositivos relevantes sin afirmar inmunidad. No ocultar funciones, detectar revisores ni evadir políticas. No usar Instant Apps. Marketplace solo controla sus propios complementos, no las apps externas que alguien publique independientemente.

**Gate:** diseño legal/seguridad/privacidad aceptado; servicio disabled by default; evidencia de consentimiento; tests negativos demuestran que sin lease no hay lectura ni dispatch y que fallo/unknown cierra; política de tienda y canal revisados. Cualquier duda bloquea distribución de esta extensión, no el resto del producto.

### Fase 7 — Marketplace, proveedores extensibles y Termux/CLI bajo demanda

**Objetivo:** permitir incorporar proveedores y extensiones nuevos sin permitir que código de terceros herede privilegios o secretos.

**Extensiones de proveedor:** se prefieren (a) configuración declarativa compatible y sin código remoto, con hostname exacto y egress limitado, o (b) adapter first-party verificado distribuido en release. Terceros solo pueden añadir manifest/configuración o módulos de capacidades estrechas revisados. No ejecutar JavaScript/Python arbitrario en proceso con permisos de Fénix, no cargar DEX/JAR/.so desde marketplace, no compartir credenciales entre conectores y no permitir URL arbitraria/SSRF.

**Marketplace:** manifest con identidad publisher, versión, host API range, hash/digest, permisos/capabilities, datos, red/dominios, dependencias, licencia, privacy/retention, SBOM, provenance, canal y método de revocación. CI verifica signature, artifact digest, SBOM/provenance y source/build identity; revisión humana inspecciona permisos, comportamiento, cambios de dueño y dependencias. Esto verifica procedencia parcial, no benignidad. Aislar tercero por UID/proceso o DSL limitado; en broker solo IPC estrecho con caller verification y scopes. No usar `sandboxed` para Termux/proot si no hay aislamiento real de kernel.

**Termux/CLI:** fuera del APK/base; catálogo opt-in, sin precarga. Antes de instalar mostrar origen, licencia, versión, hash, dependencias, scripts, tamaño, permisos, egress, coste, riesgo y uninstall. Revisar lifecycle scripts npm, binarios, hooks y actualizaciones. `RUN_COMMAND` queda deshabilitado por defecto y se trata como alto riesgo; nunca ejecutar comando sugerido por modelo sin revisión explícita. Jules CLI en Termux se marca `UNVERIFIED` hasta probar compatibilidad; Jules remoto no equivale a inferencia local y puede usar cuota/servicio remoto.

**Gate:** complementos de marketplace no declaran Accessibility; las actualizaciones se verifican y revocan; no hay auto-update o ejecución no consentida; el host API rechaza scopes extra; uninstall revoca tokens y detiene jobs. Si la plataforma no puede aislar código ejecutable, admitir solo datos/configuración declarativa o deshabilitar esa clase de extensión.

### Fase 8 — Verificación integrada, seguridad, beta y release

**Objetivo:** demostrar que el artefacto que se publica corresponde a los requisitos, no contiene secretos y puede detenerse o revertirse sin afirmar que el usuario puede hacer downgrade universal.

**Pirámide de pruebas:** unitarias y property tests; contract tests de adapters; instrumentación de Android; integración MCP con fake servers; UI/E2E de journeys; migración/upgrade; privacidad/retención; a11y/localización; rendimiento/memoria/batería; análisis estático/dinámico; SSRF/egress; fuzzing de parsers; ataques de prompt injection por mensaje, archivo, web, notificación y tool result; lifecycle y caos (red caída, quota unknown, timeout ambiguo, servicio desconectado). Probar la app en servicios/apps sintéticas y dispositivo de laboratorio. El sistema de evidencia debe enlazar requisito, `run_id`, commit, hash, entorno, resultado, log y artefacto redaccionado. `PASS` requiere resultado observable, no solo código HTTP o impresión del agente.

**Release:** revisar target API/política vigente, application ID, versionCode, signing, release no-debuggable, permisos, SBOM, provenance, privacy policy, Data Safety, disclosures, store listing, videos/declaraciones, notas y soporte. Primero internal/closed testing; staged rollout en cohortes con umbrales definidos tras baseline. Play puede pausar un rollout; planear forward fix compatible. En cualquier canal externo, documentar install/update y menor protección.

**GO/NO-GO mínimo:** cero secretos en artefactos; ninguna lectura/acción sensible sin PEP; no hay fuga cross-project; autenticación/scope y egress pasan; cuota/budget respeta ledger interno; estados unknown/ambiguous son visibles; rollback/kill switch ensayado; errores críticos y P0/P1 cerrados o waiver firmado con vencimiento; soporte disponible. Para módulo Accessibility, además el proceso de Play/política debe estar resuelto, pero su aprobación no se presume.

### Fase 9 — Operación, mejora continua, mantenimiento y retirada

**Objetivo:** mantener seguridad y compatibilidad a lo largo del tiempo, responder a incidentes y retirar componentes sin dejar tokens o usuarios atrapados.

**Operación:** métricas minimizadas (sin contenido por defecto) para crashes/ANR, latencia, fallos, first token, cuotas, 429, presupuesto reservado/real/unknown, metadata stale, cambios de provider, denegaciones PEP, side effects, errores de consentimiento, adopción a11y y soporte. Dashboards segmentados por versión/provider/capability sin identificar contenido sensible. Runbooks para proveedor caído, quota desconocida, timeout ambiguo, filtración de clave, extensión comprometida, Play warning, privacy incident y account deletion. Reconciliar usage/cost contra datos oficiales cuando estén disponibles; ningún ledger interno prueba el gasto fuera de Fénix.

**Mantenimiento:** revisar Android, APIs/target SDK, políticas de Play y Play Protect, SDKs/CVEs, dependencias, modelos, tokenizers, precios, cuotas, auth, endpoints, capabilities, Jules, Termux y provenance. Cada adapter/model/plugin tiene owner, source, fecha de última revisión, TTL, changelog, fixtures, status y plan de deprecación. Si la metadata o política vence y no puede comprobarse, deshabilitar la capability afectada o marcarla `unknown`; no convertirla en allow.

**Retirada:** anunciar, exportar datos que el usuario quiera conservar, detener nuevas ejecuciones, terminar jobs, revocar leases/tokens/keys, desactivar plugins, migrar o cerrar modelos, borrar datos según política y obligaciones, conservar únicamente evidencia necesaria, quitar permisos/SDK/código y documentar el cierre. No depender de `uninstall` scripts de terceros para eliminar secretos.

**Gate permanente:** pausar una capability ante fuga, acción no autorizada, discrepancia de coste/material, fallo de revocación o comportamiento de Play. El postmortem genera tareas con dueños/fechas y pruebas de no regresión.

## 5. Contratos funcionales obligatorios

### 5.1 Proveedor y catálogo

`ProviderAdapter` debe exponer versión, auth, modelos, capabilities, validación, token count con estado de exactitud, envío/stream, usage real, errores, coste estimado (solo con PriceBook versionado), límites (si el proveedor los publica) y clasificación de reintento. Un proveedor nuevo se mantiene deshabilitado hasta validar fixtures, contrato, egress, manejo de secretos, cuota y rollback. Los modelos `preview`/`experimental` deben estar separados de `stable`; un campo no publicado es `unknown`, no una inferencia del nombre.

### 5.2 Coste y límites

Separar: (a) presupuesto interno de Fénix, (b) cuota/rate limit del proveedor, (c) cuenta de billing del proveedor, (d) plan de consumidor y (e) credencial API. La app puede hacer cumplir de forma fuerte su propio ledger sobre las llamadas que admite el gateway; no controla llamadas externas ajenas, saldo compartido, retrasos de liquidación o capacidad real del proveedor. Cuando se requiere un hard cap externo, usar la función oficial documentada por el proveedor y demostrar configuración, no confiar solo en Fénix.

### 5.3 Contexto y compaction

Sin límites hardcodeados globales. Consultar metadata/tokenizer si existe; refrescar de forma cacheada y respetuosa de cuota, con fecha y fuente. Modelar `known/stale/unknown/unsupported`. Calcular worst case (input, tools, adjuntos, salida, reasoning y overhead) con margen. Auto-compaction desactivada por defecto durante el piloto; ofrecer nunca, preguntar, automática opt-in y eliminar bajo control explícito. Mantener log canónico; la proyección compactada lleva IDs/hash/provenance. Eliminar el resumen local no significa borrar datos enviados al proveedor remoto; explicarlo. Una pérdida de instrucción, restricción, cita o estado crítico debe cancelar la compactación o requerir intervención.

### 5.4 Seguridad de acciones y consentimiento

Separar observación, propuesta, aprobación y commit. Las tools de solo lectura, escritura y borrado usan scopes distintos; consentimientos cortos, revocables y ligados a actor, tool, target, parámetros y sesión. Los datos de una página/documento/tool se consideran no confiables. Para toda acción con efectos, validar en código y en downstream; el LLM no es PDP. `DENY` nunca es anulable por una skill, perfil, “modo agente” o mayor reasoning effort.

### 5.5 Marketplace y proveedor nuevo

Extensibilidad no significa código arbitrario. El camino menos riesgoso es declaración/configuración compatible; después adapter first-party revisado; por último add-on de terceros con runtime limitado y capacidades explícitas. No conceder Accessibility, credenciales globales, shell, almacenamiento amplio o egress irrestricto. El marketplace solo puede hacer cumplir condiciones de su propio catálogo y canal.

## 6. Matriz de aceptación transversal

| ID | Condición verificable | Evidencia requerida |
|---|---|---|
| `ARCH-001` | Todo requisito crítico tiene requisito→ADR→implementación→test→artefacto→aprobador | Matriz de trazabilidad con IDs y hashes |
| `SEC-PEP-001` | Ninguna ruta legacy evita el PEP | Análisis estático + spy tests en el adaptador de efecto |
| `SEC-FIN-001` | Apps/ventanas financieras, autenticación y desconocidas: cero lectura y cero dispatch | Apps sintéticas, logs de contador y pruebas negativas |
| `SEC-TOCTOU-001` | Cambio de foco/nodo/ventana entre plan y envío cancela acción | Test de carrera, resultado y `run_id` |
| `SEC-MCP-001` | Token con audience/scope incorrectos, expirado o revocado no invoca handler | Test integration 401/403 y contador de handler en cero |
| `SEC-EGRESS-001` | URLs arbitrarias, redirects, localhost, metadata, link-local y rebinding se bloquean | Suite SSRF/egress y logs redaccionados |
| `SEC-SECRET-001` | Ninguna API key/token en APK, logs, history, CLI argv o repositorio | Secret scan y revisión de artefactos |
| `COST-001` | Límite interno se aplica atómicamente por proveedor/usuario/modelo | Concurrency tests y ledger de reservas |
| `COST-002` | Cuota/precio/uso desconocido nunca se presenta como cero | Fixtures stale/unknown y pruebas UI/API |
| `COST-003` | No hay fallback o escalado de modelo sin consentimiento/política | Test de red/feature flag y log de decisión |
| `CTX-001` | Auto-compaction desactivada no muta contexto | Snapshot/hash antes-después |
| `CTX-002` | Instrucciones pinned, citas y tool provenance sobreviven o abortan compactación | Golden/property tests por modelo |
| `EXT-001` | Complemento third-party con Accessibility/shell no autorizado es rechazado | Scanner de manifest y pruebas de catálogo |
| `EXT-002` | Cambio de digest/publisher revoca activación y no autoactualiza | Test de firma, índice y denylist |
| `UX-001` | Usuario identifica proveedor, datos, coste conocido/desconocido y efecto antes de aprobación | Prueba observada de comprensión |
| `A11Y-001` | Journeys críticos funcionan con lector de pantalla, foco, zoom y teclado | Evidencia de test manual y automatizado |
| `REL-001` | Candidate reproducible/identificable con SBOM, provenance, signing y rollout/kill switch | Release dossier y simulacro de pausa |
| `OPS-001` | Incidente de fuga/compromiso se contiene y claves/token se revocan | Tabletop y ejercicio técnico documentado |

Los objetivos numéricos de calidad, latencia, memoria, batería, crash-free sessions, uso y cohortes se definen después de capturar baseline. No se inventan porcentajes para aparentar precisión.

## 7. Revisión adversarial del propio roadmap

1. **¿Una sola extensión reduce riesgo?** Reduce el número de componentes con acceso directo, pero concentra el blast radius en un broker privilegiado. Mitigación: broker mínimo, proceso/UID separado para terceros, IPC estrecho, tests de fuzz, revisión independiente y kill switch. No llamarlo garantía.
2. **¿Un APK base sin Accessibility evita Play Protect?** No. Puede reducir permisos del paquete base, pero la extensión que declara el servicio conserva su propia revisión y detección. No hay promesa oficial de inmunidad ni de ausencia de warning.
3. **¿Se puede impedir que otros creen extensiones sustitutas?** Solo en el marketplace/canal propio mediante política, scanner y firma. No se puede impedir que alguien publique otra app Android fuera de Fénix. No presentar la regla del catálogo como control del sistema operativo.
4. **¿Una firma, attestation o revisión prueba que un plugin es benigno?** No; acredita identidad/procedencia bajo ciertas raíces de confianza, no intención ni comportamiento futuro. Se requiere autorización runtime, egress, minimización y revocación.
5. **¿El ledger de Fénix puede garantizar el coste total?** Solo el tráfico que pasa por su gateway y los datos que puede reconciliar. Puede quedar uso externo, cuota compartida, liquidación retrasada o solicitud ambiguamente enviada. Para un techo duro, requerir función oficial del proveedor y explicitar límites.
6. **¿Consultar modelo/contexto dinámicamente es siempre mejor?** No. Puede no existir endpoint, costar cuota, faltar datos o retornar valores stale. TTL, cache y `unknown` son necesarios; polling continuo queda fuera.
7. **¿La compactación conserva todo?** No. Evaluaciones deterministas ayudan pero no garantizan fidelidad. El log canónico y la posibilidad de desactivar/restaurar son condiciones de seguridad y UX.
8. **¿HITL impide abuso?** No necesariamente: UI engañosa, contenido inyectado o habituación pueden producir aprobaciones inválidas. Mostrar destino/acción/parámetros desde UI confiable; quitar por completo la acción sensible del scope autónomo.
9. **¿El roadmap está listo para que alguien lo ejecute mañana?** Está listo como plan de fases/gates, pero no como especificación de clases, endpoints, migraciones, wireframes finales, asignación de tickets o código. Esos artefactos se producen dentro de fases 0–2 con acceso al repositorio y responsables identificados. No se afirma que la implementación esté realizada.
10. **¿La obligación de “costo cero” prohíbe al usuario usar proveedores pagos?** No. Construcción y uso esencial no deben exigir pago; cualquier proveedor de pago queda bajo configuración y decisión separada del usuario, sin acciones de compra automáticas ni promesa de control absoluto de facturación externa.

## 8. Fuentes externas consultadas para este suplemento

Las referencias son fuentes primarias o estándares salvo donde se indica; se consultaron en la investigación fechada 18-10-2026. Las políticas y APIs pueden cambiar, por lo que deben revalidarse antes de implementar o publicar.

### Android, Google Play y Accessibility

- Google Play, [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).
- Google Play, [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).
- Google Play, [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).
- Google Play, [Best practices for prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en).
- Google Play, [Declare permissions for your app](https://support.google.com/googleplay/android-developer/answer/9214102?hl=en).
- Android Developers, [Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service), [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [AccessibilityNodeInfo API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo).
- Google Play Protect, [Developer Guidance for Warnings](https://developers.google.com/android/play-protect/warning-dev-guidance), [On-device protections](https://developers.google.com/android/play-protect/client-protections).
- Android Developers, [Overview of Google Play Instant](https://developer.android.com/topic/google-play-instant/overview).
- Google Play, [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en), [Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/17006354?hl=en), [User Generated Content](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en).
- Android Developers, [Application Sandbox](https://source.android.com/docs/security/app-sandbox), [App signing](https://source.android.com/docs/security/features/apksigning), [Dynamic Code Loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading).

### MCP, seguridad de agentes y ciclo de desarrollo

- MCP, [Authorization 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization), [Server Tools 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28/server/tools), [Security Best Practices](https://modelcontextprotocol.io/specification/2026-07-28/basic/security_best_practices).
- NIST, [SP 800-207 Zero Trust Architecture](https://csrc.nist.gov/pubs/sp/800/207/final), [SP 800-218 SSDF](https://csrc.nist.gov/pubs/sp/800/218/final), [SP 800-61 Rev. 3 incident response](https://csrc.nist.gov/pubs/sp/800/61/r3/final).
- OWASP, [MASVS](https://mas.owasp.org/MASVS/), [SAMM](https://owasp.org/projects/samm), [ASVS](https://owasp.org/projects/asvs), [SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html), [LLM06:2025 Excessive Agency](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/).
- Sigstore, [Cosign signing overview](https://docs.sigstore.dev/cosign/signing/overview/), [verification](https://docs.sigstore.dev/cosign/verifying/verify/); SLSA, [specification v1.2](https://slsa.dev/spec/v1.2/).
- npm, [scripts/lifecycle](https://docs.npmjs.com/cli/v11/using-npm/scripts/), [provenance](https://docs.npmjs.com/generating-provenance-statements/), [trusted publishing](https://docs.npmjs.com/trusted-publishers/).
- Android Developers, [Termux:API](https://github.com/termux/termux-api), [Termux:Tasker](https://github.com/termux/termux-tasker) como ejemplos de superficies de CLI/IPC y permisos, no como sandbox de Fénix.

### Proveedores, contexto y producto

- OpenAI, [Models](https://platform.openai.com/docs/models), [Token counting](https://platform.openai.com/docs/guides/token-counting), [Rate limits](https://platform.openai.com/docs/guides/rate-limits), [Error codes](https://platform.openai.com/docs/guides/error-codes), [API vs ChatGPT billing](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform).
- Anthropic, [Models overview](https://docs.anthropic.com/en/docs/about-claude/models/overview), [Token counting](https://docs.anthropic.com/en/docs/build-with-claude/token-counting), [Context windows](https://docs.anthropic.com/en/docs/build-with-claude/context-windows), [Compaction](https://docs.anthropic.com/en/build-with-claude/compaction), [Rate limits](https://docs.anthropic.com/en/api/rate-limits), [Errors](https://docs.anthropic.com/en/api/errors).
- Google Gemini, [Models](https://ai.google.dev/gemini-api/docs/models), [Tokens](https://ai.google.dev/gemini-api/docs/tokens), [Rate limits](https://ai.google.dev/gemini-api/docs/rate-limits), [Billing](https://ai.google.dev/gemini-api/docs/billing), [API keys](https://ai.google.dev/gemini-api/docs/api-key).
- Google Jules, [API](https://developers.google.com/jules/api), [CLI](https://jules.google/docs/cli/reference), [limits/plans](https://jules.google/docs/usage-limits/). En el plan existente ya se describe la integración opcional como cliente remoto; revisar estado y límites vigentes antes de habilitarla.
- Papers revisados como benchmarks acotados, no garantías de Fénix: [MobileWorldSafety](https://arxiv.org/html/2608.17659), [AgentDojo](https://arxiv.org/abs/2406.13352), [Lost in the Middle](https://arxiv.org/abs/2307.03172), [The Instruction Hierarchy](https://arxiv.org/abs/2404.13208).

## 9. Definición de cierre del proyecto

La implementación integral se considera cerrada solo cuando: (1) cada fase tiene evidencia y aprobación; (2) las funcionalidades liberadas corresponden al scope y políticas revisados; (3) el núcleo opera sin suscripción obligatoria; (4) los proveedores de pago son opt-in y pasan por controles de cuota/credenciales; (5) los adapters son extensibles pero no admiten código/egress sin límites; (6) la compaction es controlable y deja provenance; (7) la app base funciona sin Accessibility; (8) cualquier módulo de Accessibility está separado, limitado, consentido y sometido a sus propias políticas/revisión; (9) el marketplace no permite a sus complementos sustituir el broker ni heredar su privilegio; (10) el release tiene soporte, rollback/kill switch, incident response y mantenimiento; y (11) existen criterios de retirada con revocación y borrado.

Una fase puede estar “diseñada” sin estar “implementada”; un test propuesto no es un test pasado; una política no es una garantía técnica; un approval de tienda no es aprobación permanente de Play Protect. El estado debe seguir expresándose en evidencia: `NOT_TESTED`, `BLOCKED`, `PASS_OBSERVED`, `FAIL_OBSERVED` o `CONTRADICTED`. La decisión final de GO se toma por versión y canal, no por el documento en abstracto.
