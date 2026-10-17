#!/usr/bin/env bash
set -euo pipefail
SERIAL="ZY22MK7B2P"

# Salvaguarda de PATH: agent-device vive en el bin de nvm y no siempre está en
# el PATH de shells no interactivos. Si falta, lo resolvemos por ruta conocida.
if ! command -v agent-device >/dev/null 2>&1; then
  for _d in /home/eli/.config/nvm/versions/node/*/bin; do
    [ -x "$_d/agent-device" ] && PATH="$_d:$PATH" && break
  done
fi

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
