#!/usr/bin/env bash
# ==============================================================================
# FÉNIX IA - PROTOCOLO DE DESCUBRIMIENTO EN CASCADA (SIN TAILSCALE, COSTO $0)
# Resuelve dinámicamente la IP de los nodos pares en USB, Wi-Fi o Hotspot móvil
# ==============================================================================
set -euo pipefail

TARGET_PORT="${1:-8080}"

resolve_fenix_peer() {
    local port="$1"
    local peer_ip=""

    # 1. Prioridad máxima: Túnel USB ADB activo (<1 ms RTT)
    if curl -sf --max-time 0.15 "http://127.0.0.1:${port}/health" >/dev/null 2>&1; then
        echo "127.0.0.1"
        return 0
    fi

    # 2. Segunda prioridad: Descubrimiento mDNS Avahi (_fenix-mcp._tcp.local)
    if command -v avahi-browse >/dev/null 2>&1; then
        peer_ip=$(avahi-browse -rt _fenix-mcp._tcp 2>/dev/null | awk '/address =/ {gsub(/\[|\]/,"",$3); print $3; exit}')
        if [[ -n "${peer_ip}" ]] && curl -sf --max-time 0.30 "http://${peer_ip}:${port}/health" >/dev/null 2>&1; then
            echo "${peer_ip}"
            return 0
        fi
    fi

    # 3. Tercera prioridad: Gateway DHCP (cuando el móvil es Hotspot en calle)
    local gw
    gw=$(ip route show default 2>/dev/null | awk '/default/ {print $3; exit}')
    if [[ -n "${gw}" ]] && curl -sf --max-time 0.30 "http://${gw}:${port}/health" >/dev/null 2>&1; then
        echo "${gw}"
        return 0
    fi

    # 4. Cuarta prioridad: Vecinos activos en la tabla ARP del kernel (ip neigh)
    for neigh in $(ip neigh show 2>/dev/null | awk '{print $1}'); do
        if curl -sf --max-time 0.25 "http://${neigh}:${port}/health" >/dev/null 2>&1; then
            echo "${neigh}"
            return 0
        fi
    done

    return 1
}

if RESOLVED_IP=$(resolve_fenix_peer "${TARGET_PORT}"); then
    echo "http://${RESOLVED_IP}:${TARGET_PORT}"
else
    echo "OFFLINE_QUEUE"
fi
