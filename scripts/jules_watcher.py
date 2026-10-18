#!/usr/bin/env python3
"""
jules_watcher.py — Observador autónomo y reactivo de Google Jules.
Monitorea la sesión de Jules en segundo plano. Cuando Jules genera una nueva
actividad, código, respuesta o finaliza, extrae los detalles, envía una alerta
a Telegram y finaliza notificando automáticamente al agente en Antigravity.
"""

import os
import sys
import time
import json
import requests

SECRETS_PATH = "/home/eli/.fenix/secrets/api_keys.json"
BASE_URL = "https://jules.googleapis.com/v1alpha"

def load_secrets():
    with open(SECRETS_PATH, "r") as f:
        return json.load(f)

def send_telegram_alert(text):
    try:
        secrets = load_secrets()
        bot_token = secrets.get("telegram", {}).get("bot_token")
        chat_id = secrets.get("telegram", {}).get("chat_id_default")
        if bot_token and chat_id:
            url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
            requests.post(url, json={"chat_id": chat_id, "text": text, "parse_mode": "Markdown"}, timeout=10)
    except Exception as e:
        print(f"[Watcher] Error enviando alerta a Telegram: {e}", file=sys.stderr)

def main():
    if len(sys.argv) < 2:
        print("Uso: jules_watcher.py <session_id> [known_activities_count]", file=sys.stderr)
        sys.exit(1)

    session_id = sys.argv[1]
    known_count = int(sys.argv[2]) if len(sys.argv) > 2 else 1

    secrets = load_secrets()
    api_key = secrets.get("jules", {}).get("api_key")
    if not api_key:
        print("Error: jules.api_key no encontrada", file=sys.stderr)
        sys.exit(1)

    headers = {"x-goog-api-key": api_key}
    print(f"[Watcher] Iniciando vigilancia reactiva para sesión {session_id} (actividades conocidas: {known_count})...")

    poll_interval = 20  # segundos
    max_wait_time = 1800  # 30 minutos máximo por turno
    start_time = time.time()

    while time.time() - start_time < max_wait_time:
        try:
            # 1. Consultar actividades
            act_url = f"{BASE_URL}/sessions/{session_id}/activities"
            resp = requests.get(act_url, headers=headers, timeout=15)
            if resp.status_code == 200:
                activities = resp.json().get("activities", [])
                current_count = len(activities)

                if current_count > known_count:
                    new_activities = activities[known_count:]
                    print(f"\n[Watcher] ¡NUEVA ACTIVIDAD DETECTADA EN JULES! ({len(new_activities)} nueva(s))")
                    
                    summary_lines = []
                    for act in new_activities:
                        act_type = act.get("activityType", "N/A")
                        time_str = act.get("createTime", "")
                        summary_lines.append(f"• Tipo: {act_type} ({time_str})")
                        print(f"\n--- Detalle Actividad ---")
                        print(json.dumps(act, indent=2, ensure_ascii=False))

                    # 2. Consultar estado general de la sesión
                    sess_url = f"{BASE_URL}/sessions/{session_id}"
                    s_resp = requests.get(sess_url, headers=headers, timeout=15)
                    sess_state = s_resp.json().get("state", "UNKNOWN") if s_resp.status_code == 200 else "N/A"

                    alert_msg = f"🤖 *Actualización de Google Jules en Fénix*\n\n" \
                                f"• Sesión: `{session_id}`\n" \
                                f"• Estado: `{sess_state}`\n" \
                                f"• Novedades:\n" + "\n".join(summary_lines)
                    send_telegram_alert(alert_msg)

                    # Salir con éxito para despertar al agente principal
                    sys.exit(0)

            # Verificar si el estado de la sesión pasó a COMPLETED o ERROR
            sess_url = f"{BASE_URL}/sessions/{session_id}"
            s_resp = requests.get(sess_url, headers=headers, timeout=15)
            if s_resp.status_code == 200:
                s_data = s_resp.json()
                state = s_data.get("state")
                if state in ["COMPLETED", "FAILED", "CANCELLED"]:
                    print(f"\n[Watcher] Sesión de Jules alcanzó estado final: {state}")
                    send_telegram_alert(f"🤖 *Jules finalizó tarea*\nEstado: `{state}`")
                    sys.exit(0)

        except Exception as e:
            print(f"[Watcher] Excepción durante sondeo: {e}", file=sys.stderr)

        time.sleep(poll_interval)

    print("[Watcher] Tiempo de espera máximo alcanzado sin nuevas actividades.", file=sys.stderr)
    sys.exit(2)

if __name__ == "__main__":
    main()

