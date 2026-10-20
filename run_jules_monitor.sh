#!/bin/bash
# Script para ejecutar el monitor de Jules periódicamente
# Se ejecuta cada 5 minutos via cron o systemd timer

WORKSPACE="/home/eli/Documentos/fenix-mobile-mcp"
LOG_FILE="$WORKSPACE/.jules_monitor.log"
LOCK_FILE="/tmp/jules_monitor.lock"

# Evitar ejecuciones concurrentes
if [ -f "$LOCK_FILE" ] && kill -0 $(cat "$LOCK_FILE") 2>/dev/null; then
    echo "[$(date)] Monitor ya ejecutándose, saliendo"
    exit 0
fi

echo $$ > "$LOCK_FILE"

cd "$WORKSPACE"

# Activar entorno Python si existe
if [ -f "$WORKSPACE/.venv/bin/activate" ]; then
    source "$WORKSPACE/.venv/bin/activate"
fi

# Ejecutar monitor
python3 "$WORKSPACE/jules_task_manager.py" >> "$LOG_FILE" 2>&1

# Limpiar lock
rm -f "$LOCK_FILE"

echo "[$(date)] Monitor completado" >> "$LOG_FILE"