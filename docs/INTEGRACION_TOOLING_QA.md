# PLAN DE INTEGRACIÓN — TOOLING QA ANTI-ALUCINACIONES

> **Objetivo:** eliminar el riesgo de que antigravity (agy) confunda resultados al usar ADB crudo,
> dotándolo de (1) automatización de dispositivo con evidencia, (2) disciplina QA determinista,
> y (3) protocolo anti-conflicto de dispositivo. Todo a costo $0 con herramientas verificadas.

**Fecha:** 2026-10-16 · **Autor:** axiom_planificador (auditoría Criticón #1-#36 como insumo)
**Hardware HITL:** Motorola Moto G77 5G `ZY22MK7B2P` · app SUT `com.fenix.mobilemcp.foss.debug`

---

## 0. Diagnóstico verificado del entorno

| Componente | Estado | Ruta / comando |
|---|---|---|
| antigravity CLI (`agy`) | v1.3.6 ✅ | `/home/eli/.gemini/bin/agy` |
| Skills globales agy | 21 GCP existentes ⚠️ no sobrescribir | `/home/eli/.gemini/config/skills/` |
| MCP agy (ya registrados) | `fenix`, `data-agent-kit`, `notebooks`, `visualization` | `agy mcp list` |
| opencode config | agentes axiom_* + MCP fenix/github/context7 | `/home/eli/.config/opencode/opencode.json` |
| Fénix MCP PC launchers | `lanzar_antigravity.sh`, `lanzar_opencode.sh` | `/home/eli/fenix-mcp/launchers/` |
| Device ADB | `ZY22MK7B2P` device ✅ | `adb devices -l` |
| Node | v22.23.3 ✅ (agent-device pide ≥22.12) | `node --version` |
| npm | 10.9.9 ✅ | |
| uv / uvx | ❌ NO instalado | necesario solo si se añade uiautomator2-mcp |
| agent-device | ❌ NO instalado | **pieza crítica** |
| e2e-tests (Appium/Kotlin) | 18 tests existentes | complementarios, NO se reemplazan |

---

## 1. Arquitectura objetivo (4 capas) — SKILLS AGNÓSTICAS VÍA MCP

> **Decisión arquitectónica clave (revisión tras feedback):** las skills NO viven en
> configs de agente (`~/.gemini/config/skills/` = solo antigravity). Viven **dentro
> del servidor Fénix MCP PC** (`/home/eli/fenix-mcp/server.py`) como **MCP resources**
> (`fenix-skills://...`) + **prompts**. Todo agente conectado a Fénix MCP — antigravity,
> opencode (`axiom_auditor`, `axiom_ingeniero`), Claude Desktop, o cualquier futuro —
> las descubre y lee automáticamente vía `list_mcp_resources` / `read_mcp_resource`.
> Cero duplicación, cero config por agente, actualización centralizada.

```
┌─ CAPA 4 · ORQUESTACIÓN (cualquier agente) ──────────────────────┐
│  antigravity (agy) · opencode (axiom_*) · futuro Claude Desktop │
│         │                       │                                │
├─ CAPA 3 · DISCIPLINA QA — SERVIDA POR FÉNIX MCP (agnóstica) ────┤
│  server.py registra:                                             │
│   @mcp.resource("fenix-skills://qa/fenix-qa-evidence")          │
│   @mcp.prompt("qa-evidence-rules")                              │
│  (lee .md en vivo desde /home/eli/fenix-mcp/skills/)            │
│  agentmantis/test-skills (opcional, patrones POM) — local agy   │
├─ CAPA 2 · AUTOMATIZACIÓN DE DISPOSITIVO (MCP) ──────────────────┤
│  agent-device (Callstack) → ADB + accessibility snapshots       │
│  Fénix MCP (SUT) → servidor en el móvil :8080                   │
├─ CAPA 1 · SISTEMA BAJO PRUEBA ──────────────────────────────────┤
│  Moto G77 · foss.debug · Termux llama-server :8081              │
└──────────────────────────────────────────────────────────────────┘
```

**Principios:**
1. Todo driver de dispositivo pasa por **agent-device** (capa 2). Prohibido `adb shell input` crudo.
2. Fénix MCP del móvil es el **SUT**, no el driver.
3. Skills QA se sirven **por MCP resources** desde el servidor Fénix PC — visibles para
   cualquier agente conectado, sin tocar config de cada cliente.

---

## 2. FASE 1 — Instalar `agent-device` (pieza crítica)

**Por qué:** reemplaza ADB crudo por sesiones con snapshots de accesibilidad, refs estables (`@e1`, `@e2`), device-claims por worktree git, replay `.ad` y captura de evidencia. Ataca directamente CRÍTICA #20/#22/#30 (monkey, multi-app, nódos stale).

```bash
# 1.1 Instalar CLI global (Node 22.23.3 ya cumple)
npm install -g agent-device@latest

# 1.2 Verificar salud del entorno (adb, device, platform-tools)
agent-device doctor
# Esperado: verde en Android + device ZY22MK7B2P detectada

# 1.3 Ver capacidades soportadas en Android (evita pedir tools inexistentes)
agent-device capabilities --platform android
```

**Criterio de paso FASE 1:** `agent-device doctor` sin errores rojos y `ZY22MK7B2P` listada.

---

## 3. FASE 2 — Skills QA deterministas (creación propia, derivadas de la auditoría)

**Por qué propio y no genérico:** las auditorías #28/#34 demostraron que un PASS por texto plausible o fallback `ok` es falso. La skill codifica las reglas duras de la auditoría para que cualquier agente no las olvide.

**Dónde vive (agnóstico):** en el servidor Fénix MCP PC, **NO** en config de agente.
Los `.md` se leen **en vivo** en cada invocación del resource → actualizar el archivo
basta, sin reiniciar el servidor MCP.

### 2.1 Crear directorio de skills del servidor Fénix

```bash
mkdir -p /home/eli/fenix-mcp/skills
```

### 2.2 Crear skill `fenix-qa-evidence.md`

Ruta destino: `/home/eli/fenix-mcp/skills/fenix-qa-evidence.md`

Contenido (listo para copiar):

```markdown
# Fénix QA Evidence — Anti-Alucinaciones
# (skill servida como MCP resource por el servidor Fénix PC; visible para
#  cualquier agente conectado: antigravity, opencode, axiom_auditor, etc.)

## REGLA 1 — Definición de PASS (obligatoria)
Un test S{X}-{NN} es PASS **solo si**:
1. `isError == false` en el resultado real (no texto plausible), Y
2. existe evidencia en disco: screenshot + fragmento de `serverLog TOOL_CALL` o `logcat -s MCP:*` + salida cruda del tool.

Si solo hay (2) sin (1) → **FAIL**. Si solo (1) sin evidencia → **PASS_INCOMPLETO**.

## REGLA 2 — Estados semánticos (nunca marcar PASS genérico)
| Estado | Cuándo |
|---|---|
| `PASS_E2E` | Acción real ejecutada + efecto verificado en dispositivo/API |
| `PASS_NEGATIVO` | Comportamiento de rechazo correcto (401, permiso denegado) — NO cuenta como PASS de funcionalidad |
| `FAIL_POR_AUSENCIA` | Tool no registrado (`Herramienta no encontrada`) — bug de cobertura, no de lógica |
| `PASS_FALLBACK` | Prohibido: si el código emite "Ejecutado con éxito" sin output real, es FALLBACK → FAIL |

## REGLA 3 — Prohibición de ADB crudo
Todo driver de dispositivo USA `agent-device` (MCP) o Fénix MCP del móvil.
Prohibido en sesiones de QA: `adb shell input tap`, `adb shell uiautomator dump`,
`adb shell screencap` directo. Motivo: races con sesiones agent-device y resultados
no trazables (CRÍTICA #20/#22).

## REGLA 4 — Rutas de evidencia
Todo artefacto va a: `docs/qa-evidence/S{suite}/S{X}-{NN}_{nombre}/`
  ├── result.json        (isError, outputText, durationMs)
  ├── screen.png         (captura post-acción)
  ├── log.txt            (slice logcat/serverLog relevante)
  └── raw-response.txt   (cuerpo HTTP o salida cruda)
Nada de "capturé y borro".

## REGLA 5 — Whitelist de dispositivo
Solo `ZY22MK7B2P`. Si `agent-device doctor` o ADB lista otro serial → **ABORT**.
Evita probar contra emulador o device equivocado (CRÍTICA #26 modo calle).

## REGLA 6 — Suites prohibidas de "correr a ciegas"
Si S0-Tooling no está en verde (ver §Suite 0), NO ejecutar S1-S13.
```

### 3.2 (Opcional) Skills externas — instalar solo las que aportan

```bash
# Patrones SDET (POM, handover→regresión) — útiles para S7 pantallas Compose
npx skills add AgentMantis/test-skills --agent antigravity

# Kit QA completo (Appium/API/manual) — REFERENCIA; idioma vietnamita, revisar antes de usar
git clone --depth 1 https://github.com/anhtester/antigravity-testing-kit.git \
  /home/eli/Documentos/fenix-mobile-mcp/.qa-reference/antigravity-testing-kit
# NO copiar .agent/ completo al proyecto aún: contiene workflows en vietnamita.
# Extraer solo: .agent/rules/{pom,locator,smart-waits}.md → traducir a ES → merge en fenix-qa-evidence.
```

**Criterio de paso FASE 2:** `/home/eli/fenix-mcp/skills/fenix-qa-evidence.md` existe con reglas 1-6 completas.

---

## 4. FASE 3 — Registrar skills como MCP resources + `agent-device` en ambas capas

### 4.1 Registrar skills en el servidor Fénix (agnóstico — cualquier agente las ve)

**Antes de editar `server.py`:**
1. `fenix:actividad()` para ver si otro agente editó recientemente
2. Backup con convención: `cp server.py server.py.bak_skills_qa_20261016`
3. Al terminar: `fenix:sesion_log` con los cambios

Añadir al final de `server.py` (antes de `if __name__ == "__main__"` o al bloque de registro):

```python
# ── Skills servidas como MCP resources (agnósticas a agente) ──────────
# Cualquier cliente conectado (antigravity, opencode, axiom_*, Claude Desktop…)
# las descubre con list_mcp_resources y lee con read_mcp_resource.
# Los .md se leen EN CADA invocación → actualizar el archivo basta, sin reiniciar.

from pathlib import Path as _Path

_SKILLS_DIR = _Path("/home/eli/fenix-mcp/skills")

@mcp.resource(
    "fenix-skills://qa/fenix-qa-evidence",
    name="fenix-qa-evidence",
    description="Reglas duras de QA Fénix: PASS con evidencia, estados semánticos, "
                "prohibición ADB crudo, rutas de artefactos, whitelist de dispositivo. "
                "Obligatoria antes de ejecutar suites S0-S13 del PLAN_MAESTRO_QA.",
    mime_type="text/markdown",
)
def _skill_fenix_qa_evidence() -> str:
    f = _SKILLS_DIR / "fenix-qa-evidence.md"
    return f.read_text(encoding="utf-8") if f.exists() else "Skill no encontrada."

@mcp.prompt(
    "qa-evidence-rules",
    description="Carga las reglas anti-alucinaciones de QA Fénix antes de ejecutar pruebas.",
)
def _prompt_qa_evidence() -> str:
    return (
        "Aplica estrictamente las reglas de QA Fénix antes de ejecutar cualquier prueba:\n\n"
        + _skill_fenix_qa_evidence()
    )
```

**Nota de diseño:** el resource lee el `.md` en cada invocación (no cachea al arrancar) → editar `skills/fenix-qa-evidence.md` surte efecto inmediato sin reiniciar el servidor. Si se añaden más skills (ej. `fenix-qa-destructive.md`), se repite el patrón con otro `@mcp.resource`.

**Verificación (post-edición):** reiniciar el servidor MCP de Fénix y comprobar desde cualquier agente:
- antigravity: pedirle "lista tus resources MCP de fenix" → debe incluir `fenix-skills://qa/fenix-qa-evidence`
- opencode: `axiom_auditor` debe ver el mismo resource (no requiere config extra, ya está conectado a Fénix MCP)

### 4.2 Registrar MCP `agent-device` (herramienta de dispositivo)

#### antigravity (agy)
```bash
agy mcp add agent-device --command agent-device --args "mcp"
agy mcp list   # debe mostrar agent-device enabled
```

#### opencode (para que `axiom_auditor` pueda consultar device state)
Editar `/home/eli/.config/opencode/opencode.json`, bloque `mcp`, añadir:

```json
"agent-device": {
  "type": "local",
  "command": ["agent-device", "mcp"],
  "enabled": true
}
```

**Criterio de paso FASE 3:**
1. Resource `fenix-skills://qa/fenix-qa-evidence` visible desde antigravity **y** opencode (mismo URI, sin config extra por agente)
2. `agy mcp list` incluye `agent-device` enabled
3. `axiom_auditor` (opencode) ve tools de agent-device

---

## 5. FASE 4 — Protocolo anti-conflicto ADB (el miedo central)

El riesgo: antigravity lanza `adb shell input ...` por su tool bash **mientras** agent-device tiene una sesión → doble-tap, estado inconsistente, "PASS" que no refleja realidad.

### Reglas del protocolo (van en la skill + en CLAUDE.md/AGENTS.md del proyecto)

| # | Regla | Mecanismo |
|---|---|---|
| P1 | Sesión única por worktree | `agent-device` reclama device por git worktree; verificar con `agent-device device status` antes de empezar |
| P2 | Liberar claims al terminar | `agent-device device release --stale` si hay sesión colgada |
| P3 | bash de antigravity: solo lectura de device | Permitido: `adb devices`, `adb shell dumpsys`, `adb logcat -d`. **Bloqueado**: `input tap/swipe/text/keyevent`, `uiautomator`, `screencap` |
| P4 | Fósforo de sesión | Antes de cada suite: `agent-device sessions.close` + reopen fresh; evitar reusar refs de snapshot viejo (refs caducan tras `--settle`) |
| P5 | Termux llama-server :8081 | Es **SUT/vecino**, no se mata durante suites S1-S6. Solo se toca en S8-02 (modo calle) con gate explícito |
| P6 | Fénix MCP móvil :8080 | Es SUT. driver = agent-device desde PC. No confundir: probar el servidor del móvil ≠ automatizar UI del móvil |

### Script de sanity-check pre-suite (guard)

Crear `scripts/qa-precheck.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
SERIAL="ZY22MK7B2P"

echo "== QA PRECHECK =="
# 1. Device correcta
adb devices | grep -q "$SERIAL" || { echo "FAIL: device $SERIAL no conectada"; exit 1; }

# 2. agent-device sano
agent-device doctor >/dev/null || { echo "FAIL: agent-device doctor"; exit 1; }

# 3. Ningún input crudo sospechoso activo (defensivo)
if pgrep -af "adb.*shell input" >/dev/null 2>&1; then
  echo "WARN: proceso adb input detectado — revisar antes de continuar"
fi

# 4. App SUT instalada
adb shell pm path com.fenix.mobilemcp.foss.debug >/dev/null \
  || { echo "FAIL: app SUT no instalada"; exit 1; }

# 5. Claim de dispositivo libre
agent-device device status || true

echo "PRECHECK OK — suite puede ejecutarse"
```

```bash
chmod +x scripts/qa-precheck.sh
```

---

## 6. FASE 5 — Suite 0 (gate) en el PLAN_MAESTRO_QA

Insertar antes de S1 en `docs/PLAN_MAESTRO_QA_FENIX_MOBILE.md`:

```markdown
## SUITE 0 — TOOLING BASE (GATE OBLIGATORIO · bloquea S1-S13)

| ID | Acción | Verificación determinista |
|----|--------|---------------------------|
| S0-01 | `agent-device` instalado | `agent-device --version` OK |
| S0-02 | `agent-device doctor` verde | exit 0 + device ZY22MK7B2P |
| S0-03 | MCP `agent-device` en agy | `agy mcp list` lo muestra enabled |
| S0-04 | Resource `fenix-skills://qa/fenix-qa-evidence` visible | `list_mcp_resources` desde antigravity **y** opencode lo listan (mismo URI) |
| S0-05 | `qa-precheck.sh` pasa | exit 0 |
| S0-06 | Smoke: abrir SUT + snapshot | `agent-device open com.fenix.mobilemcp.foss.debug` + `snapshot -i` con ≥1 ref |
| S0-07 | Replay smoke `.ad` <30s | `agent-device replay smoke.ad` exit 0 |

**Si S0 falla → NO ejecutar S1-S13.** Un PASS sin tooling verde es un falso PASS (auditoría #1-#3).
```

---

## 7. Criterios de aceptación finales (Definition of Done)

- [ ] `agent-device doctor` verde y `ZY22MK7B2P` única
- [ ] `agy mcp list` incluye `agent-device` enabled
- [ ] `axiom_auditor` (opencode) ve tools de agent-device
- [ ] Resource `fenix-skills://qa/fenix-qa-evidence` visible desde antigravity **y** opencode (agnóstico, sin config extra por agente)
- [ ] `server.py` respeta protocolo de edición (actividad + backup + sesion_log)
- [ ] `scripts/qa-precheck.sh` ejecutable y en verde
- [ ] Suite 0 insertada en PLAN_MAESTRO y marcada como gate
- [ ] Protocolo P1-P6 escrito en CLAUDE.md/AGENTS.md del repo fenix-mobile-mcp
- [ ] Smoke `.ad` grabado y re-ejecutable (evidencia en `docs/qa-evidence/S0/`)

---

## 8. Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| agent-device no soporta algo que Fénix MCP sí (ej. `take_screenshot` via AccessibilityService) | Mantener Fénix MCP como SUT-driver secundario; agent-device para UI-driven tests; documentar bifurcación en S4-00 |
| Skills vietnamitas del kit generan confusión | NO instalar `.agent/` completo; traducir/reglas mínimas a `fenix-qa-evidence` |
| Dooble-reclamo de device entre agy y opencode simultáneos | P1: un solo worktree/una sesión a la vez; `device status` antes de cada suite |
| agent-device actualiza y rompe CLI | Fijar versión tras doctor verde (`npm ls -g agent-device`), documentar en PLAN |

---

## 9. Prompt corto para la sesión (cualquier agente conectado a Fénix MCP)

```
Lee docs/INTEGRACION_TOOLING_QA.md y ejecuta FASES 1-5 en orden.
Cada fase termina con su criterio de paso; no pasar a la siguiente si falla.

Fase 1: npm install -g agent-device@latest && agent-device doctor (debe dar verde con ZY22MK7B2P).
Fase 2: crea /home/eli/fenix-mcp/skills/fenix-qa-evidence.md con el contenido exacto del doc (reglas 1-6).
Fase 3a: ANTES de editar server.py corre fenix:actividad(), haz backup server.py.bak_skills_qa_20261016,
         añade el bloque @mcp.resource("fenix-skills://qa/fenix-qa-evidence") + @mcp.prompt del doc,
         y al final fenix:sesion_log. El resource lee el .md en vivo (sin reiniciar para updates).
Fase 3b: registra agent-device en agy (agy mcp add) y en opencode.json.
Fase 4: crea scripts/qa-precheck.sh y ejecútalo (debe dar PRECHECK OK).
Fase 5: inserta la Suite 0 en docs/PLAN_MAESTRO_QA_FENIX_MOBILE.md antes de S1.
No instales el antigravity-testing-kit completo (idioma vietnamita); solo clónalo a .qa-reference/.

Verificación agnóstica (clave): el resource fenix-skills://qa/fenix-qa-evidence debe ser
visible TANTO desde antigravity como desde opencode (axiom_auditor), sin config por agente.
Reporta al final: checklist §7 con [x] por ítem cumplido.
```
