#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# FÉNIX IA - DEMONIO DE TRABAJO PESADO EN TERMUX (MOTO G77 / DIMENSITY 6400)
# Inicia servicios de taller con guardián térmico cooperativo (<38.0°C)
# ==============================================================================
set -euo pipefail

# 1. Retener Wake-Lock y configurar variables de entorno para un solo hilo en OpenMP/Tesseract
termux-wake-lock || true
export OMP_NUM_THREADS=1
export OMP_THREAD_LIMIT=1

# 2. Levantar OpenSSH en el puerto 8022 si no está corriendo
if ! pgrep -x sshd >/dev/null; then
    sshd
    echo "[OK] OpenSSH activo en puerto 8022."
fi

# 3. Levantar Chromium Headless en puerto 9222 con límite de 256 MB en V8
mkdir -p "$PREFIX/var/tmp/chromium-headless"
rm -f "$PREFIX/var/tmp/chromium-headless/Singleton"*
if ! pgrep -f "remote-debugging-port=9222" >/dev/null; then
    if command -v chromium-browser >/dev/null 2>&1; then
        chromium-browser \
            --headless \
            --no-sandbox \
            --disable-gpu \
            --disable-dev-shm-usage \
            --disable-software-rasterizer \
            --js-flags="--max-old-space-size=256" \
            --remote-debugging-port=9222 \
            --remote-debugging-address=127.0.0.1 \
            --user-data-dir="$PREFIX/var/tmp/chromium-headless" \
            about:blank >/dev/null 2>&1 &
        echo "[OK] Chromium Headless CDP activo en puerto 9222."
    fi
fi

# 4. Levantar llama-server en 127.0.0.1:8081 fijado con taskset a CPUs 4-7 si el modelo existe
if ! pgrep -x llama-server >/dev/null; then
    MODEL_PATH="$HOME/models/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    if [ -f "$MODEL_PATH" ] && command -v llama-server >/dev/null 2>&1; then
        taskset -c 4-7 llama-server \
            -m "$MODEL_PATH" \
            --host 127.0.0.1 \
            --port 8081 \
            -c 4096 \
            -t 2 \
            -tb 6 >/dev/null 2>&1 &
        echo "[OK] llama-server activo en puerto 8081 (Cortex-A76 CPUs 4-7)."
    fi
fi

# 5. Guardián Térmico Cooperativo: Regula carga para mantener batería < 38.0°C (380 décimas)
echo "[FÉNIX] Guardián térmico cooperativo activo. Límite máximo: 38.0°C."
while true; do
    TEMP=$(termux-battery-status 2>/dev/null | jq -r '.temperature // 30.0' 2>/dev/null || echo "30.0")
    TEMP_DEC=$(printf "%.0f" "$(echo "$TEMP * 10" | bc -l 2>/dev/null || echo 300)")

    # Banda de corte térmico: >= 38.0°C (380 décimas)
    if [ "$TEMP_DEC" -ge 380 ]; then
        pkill -STOP -x cargo 2>/dev/null || true
        pkill -STOP -x rustc 2>/dev/null || true
        pkill -STOP -x clang 2>/dev/null || true
        pkill -STOP -x tesseract 2>/dev/null || true
    # Banda de recuperación: <= 36.0°C (360 décimas)
    elif [ "$TEMP_DEC" -le 360 ]; then
        pkill -CONT -x cargo 2>/dev/null || true
        pkill -CONT -x rustc 2>/dev/null || true
        pkill -CONT -x clang 2>/dev/null || true
        pkill -CONT -x tesseract 2>/dev/null || true
    fi
    sleep 10
done
