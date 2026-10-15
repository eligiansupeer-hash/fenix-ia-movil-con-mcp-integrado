#!/usr/bin/env bash
# ==============================================================================
# FÉNIX IA - PUENTE BIDIRECCIONAL DE ALTA VELOCIDAD SOBRE USB 2.0 (MODO TALLER)
# Nodo Base: Netbook Juana Manso (Debian GNU/Linux 13 trixie, Kernel 6.12)
# Dispositivos: Moto G77 (ZY22MK7B2P) y Redmi 14C (RS4HQ4YHQKZLLB4T)
# Ancho de banda: 480 Mbps | RTT: < 0.75 ms
# ==============================================================================
set -euo pipefail

SERIAL_MOTO="ZY22MK7B2P"
SERIAL_REDMI="RS4HQ4YHQKZLLB4T"

log_info() { printf "\033[1;34m[FÉNIX-USB]\033[0m %s\n" "$1"; }
log_ok()   { printf "\033[1;32m[OK]\033[0m %s\n" "$1"; }
log_warn() { printf "\033[1;33m[AVISO]\033[0m %s\n" "$1"; }

# 1. Iniciar servidor ADB local en Debian 13
adb start-server >/dev/null 2>&1

# 2. Enlazar Nodo de Cómputo Pesado Acelerador: Motorola Moto G77 5G
if adb devices | grep -q "^${SERIAL_MOTO}[[:space:]]*device$"; then
    log_info "Configurando canales de alta velocidad para Moto G77 (${SERIAL_MOTO})..."
    adb -s "${SERIAL_MOTO}" forward --remove-all || true
    adb -s "${SERIAL_MOTO}" reverse --remove-all || true

    # Canales Forward (Netbook -> Moto G77)
    adb -s "${SERIAL_MOTO}" forward tcp:8080 tcp:8080   # Servidor Kotlin com.fenix.mobilemcp
    adb -s "${SERIAL_MOTO}" forward tcp:11434 tcp:8081  # llama-server expuesto como puerto Ollama local
    adb -s "${SERIAL_MOTO}" forward tcp:9222 tcp:9222   # Chromium Headless CDP para scraping delegado
    adb -s "${SERIAL_MOTO}" forward tcp:8022 tcp:8022   # Termux OpenSSH para compilación y OCR paralelo

    # Canal Reverse (Moto G77 -> Netbook)
    adb -s "${SERIAL_MOTO}" reverse tcp:9000 tcp:9000   # Acceso inverso a las 104 tools de server.py en PC

    log_ok "Moto G77 enlazado: Forward [8080, 11434->8081, 9222, 8022] | Reverse [9000]"
else
    log_warn "Moto G77 (${SERIAL_MOTO}) no detectado por cable USB."
fi

# 3. Enlazar Nodo Auxiliar Sensorial: Xiaomi Redmi 14C
if adb devices | grep -q "^${SERIAL_REDMI}[[:space:]]*device$"; then
    log_info "Configurando canales para Xiaomi Redmi 14C (${SERIAL_REDMI})..."
    adb -s "${SERIAL_REDMI}" forward --remove-all || true
    adb -s "${SERIAL_REDMI}" reverse --remove-all || true

    # Canal Forward (Netbook :8082 -> Redmi 14C :8080)
    adb -s "${SERIAL_REDMI}" forward tcp:8082 tcp:8080

    # Canal Reverse (Redmi 14C :9000 -> Netbook :9000)
    adb -s "${SERIAL_REDMI}" reverse tcp:9000 tcp:9000

    log_ok "Redmi 14C enlazado: Forward [8082->8080] | Reverse [9000]"
else
    log_warn "Redmi 14C (${SERIAL_REDMI}) no detectado por cable USB."
fi

# 4. Mostrar estado consolidado de túneles
log_info "Túneles forward activos:"
adb forward --list || true
log_info "Túneles reverse activos:"
adb reverse --list || true
