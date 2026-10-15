#!/usr/bin/env bash
# ==============================================================================
# FÉNIX IA - PROTOCOLO DE TRANSICIÓN A MODO CALLE (INMUNIDAD DELIVERY)
# Sellado determinista de demonios, puertos y banderas antes de desenchufar USB
# Dispositivos: Moto G77 (ZY22MK7B2P) y Redmi 14C (RS4HQ4YHQKZLLB4T)
# ==============================================================================
set -euo pipefail

log_info() { printf "\033[1;34m[MODO-CALLE]\033[0m %s\n" "$1"; }
log_ok()   { printf "\033[1;32m[OK]\033[0m %s\n" "$1"; }
log_warn() { printf "\033[1;33m[AVISO]\033[0m %s\n" "$1"; }

for SERIAL in "ZY22MK7B2P" "RS4HQ4YHQKZLLB4T"; do
    if adb devices | grep -q "^${SERIAL}[[:space:]]*device$"; then
        log_info "Iniciando sellado de seguridad en dispositivo ${SERIAL}..."

        # 1. Liberar wake-lock y forzar detención de com.termux (elimina sshd, llama-server, chromium)
        log_info "Deteniendo procesos de com.termux..."
        adb -s "${SERIAL}" shell am force-stop com.termux 2>/dev/null || true

        # 2. Ordenar a com.fenix.mobilemcp cerrar Ktor externo y activar cortafuegos de accesibilidad
        log_info "Enviando señal ACTION_ENTER_STREET_MODE a fenix-mobile-mcp..."
        adb -s "${SERIAL}" shell am broadcast \
            -a com.fenix.mobilemcp.ACTION_ENTER_STREET_MODE \
            -n com.fenix.mobilemcp/.security.StreetModeReceiver >/dev/null 2>&1 || true

        # 3. Eliminar todos los mapeos de puertos ADB
        log_info "Limpiando túneles ADB forward y reverse..."
        adb -s "${SERIAL}" forward --remove-all 2>/dev/null || true
        adb -s "${SERIAL}" reverse --remove-all 2>/dev/null || true

        # 4. Asegurar que mock_location y adb_enabled estén en 0
        adb -s "${SERIAL}" shell settings put secure mock_location 0 2>/dev/null || true
        adb -s "${SERIAL}" shell settings put global adb_enabled 0 2>/dev/null || true

        log_ok "Dispositivo ${SERIAL} SELLADO y listo para la calle."
    fi
done

echo ""
log_ok "=============================================================================="
log_ok "PASO FINAL REQUERIDO POR EL USUARIO (2 SEGUNDOS):"
log_ok "1. Desconecte el cable USB de los teléfonos."
log_ok "2. En su teléfono, desactive el interruptor maestro de Opciones para Desarrolladores."
log_ok "3. Abra F-Chat y verifique el semáforo verde: 'MODO CALLE INMUNE'."
log_ok "=============================================================================="
