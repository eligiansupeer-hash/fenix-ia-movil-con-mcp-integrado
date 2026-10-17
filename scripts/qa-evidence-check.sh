#!/usr/bin/env bash
# qa-evidence-check.sh — validador determinista de evidencia QA Fénix
# Cierra cada ID S{X}-{NN} solo si su carpeta de evidencia es integra.
# Agnóstico: sirve con shell en cualquier entorno (con/sin sub-agentes, a mano).
# Contraparte MCP sin shell: tool fenix_qa_evidence_check (servidor Fénix).
#
# USO:  qa-evidence-check.sh <carpeta_evidencia>
#       ej: scripts/qa-evidence-check.sh docs/qa-evidence/S0/S0T-06_smoke
# EXIT: 0 = EVIDENCIA_OK   1 = EVIDENCIA_FAIL   2 = uso inválido
# Reglas: 1 (isError==false) y 7 (estructura + binding RUN_ID) de skills/fenix-qa-evidence.md
set -uo pipefail

DIR="${1:-}"
if [ -z "$DIR" ]; then
  echo "USO: $(basename "$0") <carpeta_evidencia>"
  exit 2
fi

FAIL=0
die_item() { echo "  - FALTA: $*"; FAIL=1; }

# --- 1. La carpeta existe ---
if [ ! -d "$DIR" ]; then
  echo "EVIDENCIA_FAIL"
  echo "  - FALTA: carpeta '$DIR' no existe"
  exit 1
fi

# --- 2. result.json: presente, JSON valido, isError == false + run_id ---
RJ="$DIR/result.json"
SCREEN_REQUIRED=1
LOG_MUST=""
RUNID=""
if [ ! -f "$RJ" ]; then
  die_item "result.json"
else
  PARSED="$(python3 - "$RJ" <<'PY' 2>&1
import json, sys
try:
    d = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception as e:
    print("PARSE_ERR:" + str(e)); sys.exit(0)
if not isinstance(d, dict):
    print("PARSE_ERR:result.json no es un objeto JSON"); sys.exit(0)
if d.get("isError") is None:
    print("NO_ISERROR"); sys.exit(0)
print("isError=" + ("true" if d["isError"] else "false"))
print("screen_required=" + ("false" if d.get("screen_required") is False else "true"))
lm = d.get("log_must_contain")
print("log_must_contain=" + (lm if isinstance(lm, str) else ""))
rid = d.get("run_id")
print("run_id=" + (rid if isinstance(rid, str) else ""))
PY
)"
  case "$PARSED" in
    PARSE_ERR:*)
      die_item "result.json valido (JSON) — ${PARSED#PARSE_ERR:}";;
    NO_ISERROR)
      die_item "result.json con campo isError presente (sin probar a ojo)" ;;
    *)
      # shellcheck disable=SC2034
      while IFS= read -r line; do
        case "$line" in
          isError=false) : ;;
          isError=true)  echo "  - isError==true en result.json (REGLA 1: FAIL)"; FAIL=1 ;;
          screen_required=*) [ "${line#screen_required=}" = "false" ] && SCREEN_REQUIRED=0 ;;
          log_must_contain=*) LOG_MUST="${line#log_must_contain=}" ;;
          run_id=*) RUNID="${line#run_id=}" ;;
        esac
      done <<< "$PARSED" ;;
  esac
fi

# --- 3. screen.png presente y no vacio (salvo screen_required=false) ---
if [ "$SCREEN_REQUIRED" = "1" ]; then
  if [ ! -f "$DIR/screen.png" ]; then
    die_item "screen.png"
  elif [ ! -s "$DIR/screen.png" ]; then
    die_item "screen.png (vacio)"
  fi
fi

# --- 4. log.txt presente, no vacio, y fragmento citado literal dentro ---
if [ ! -f "$DIR/log.txt" ]; then
  die_item "log.txt"
elif [ ! -s "$DIR/log.txt" ]; then
  die_item "log.txt (vacio)"
elif [ -n "$LOG_MUST" ]; then
  grep -Fq -- "$LOG_MUST" "$DIR/log.txt" || die_item "log.txt con el fragmento citado: \"$LOG_MUST\""
fi

# --- 5. Binding de corrida: run_id de result.json == run_id en log.txt ---
if [ -z "$RUNID" ]; then
  die_item "result.json sin run_id (REGLA 7: binding de corrida)"
elif [ ! -f "$DIR/log.txt" ]; then
  die_item "log.txt (sin log no hay binding que comprobar)"
else
  grep -Fq -- "$RUNID" "$DIR/log.txt" \
    || die_item "log.txt sin el RUN_ID de la corrida: \"$RUNID\" (evidencia desvinculada)"
fi

# --- Veredicto ---
if [ "$FAIL" = "0" ]; then
  echo "EVIDENCIA_OK  $DIR"
  exit 0
else
  echo "EVIDENCIA_FAIL  $DIR"
  exit 1
fi
