#!/usr/bin/env python3
"""
auto_jules_deployer.py — Pipeline autónomo de extremo a extremo:
1. Vigila a Jules hasta que complete la generación y subida de código a GitHub.
2. Descarga los cambios (git pull) en la máquina local.
3. Compila el APK (Gradle assembleFossDebug).
4. Instala el APK en el móvil (ADB install -r).
5. Inicia y prueba la app en el dispositivo real.
6. Notifica por Telegram y despierta a Antigravity.
"""

import os
import sys
import time
import json
import subprocess
import requests

REPO_DIR = "/home/eli/Documentos/fenix-mobile-mcp"
SECRETS_PATH = "/home/eli/.fenix/secrets/api_keys.json"
BASE_URL = "https://jules.googleapis.com/v1alpha"
DEVICE_SERIAL = "ZY22MK7B2P"

def load_secrets():
    with open(SECRETS_PATH, "r") as f:
        return json.load(f)

def send_telegram(text):
    try:
        secrets = load_secrets()
        bot_token = secrets.get("telegram", {}).get("bot_token")
        chat_id = secrets.get("telegram", {}).get("chat_id_default")
        if bot_token and chat_id:
            url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
            requests.post(url, json={"chat_id": chat_id, "text": text, "parse_mode": "Markdown"}, timeout=10)
    except Exception as e:
        print(f"[Deployer] Error Telegram: {e}", file=sys.stderr)

def run_cmd(cmd, cwd=REPO_DIR):
    print(f"\n[Deployer CMD] {cmd}")
    res = subprocess.run(cmd, shell=True, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"[Deployer STDERR] {res.stderr[:500]}", file=sys.stderr)
    else:
        print(f"[Deployer STDOUT] {res.stdout[:500]}")
    return res

def wait_for_jules(session_id, known_activities=4):
    secrets = load_secrets()
    api_key = secrets.get("jules", {}).get("api_key")
    headers = {"x-goog-api-key": api_key}

    print(f"[Deployer] Monitoreando progreso de Jules (sesión {session_id})...")
    poll_interval = 25
    max_wait_seconds = 7200  # hasta 2 horas
    start_time = time.time()

    current_count = known_activities
    pr_branch = None

    while time.time() - start_time < max_wait_seconds:
        try:
            # Consultar actividades
            act_url = f"{BASE_URL}/sessions/{session_id}/activities"
            resp = requests.get(act_url, headers=headers, timeout=15)
            if resp.status_code == 200:
                activities = resp.json().get("activities", [])
                if len(activities) > current_count:
                    new_acts = activities[current_count:]
                    current_count = len(activities)
                    for act in new_acts:
                        act_type = act.get("activityType", "N/A")
                        print(f"[Deployer] Jules avanzó: {act_type}")
                        # Detectar si hay información de PR o branch
                        act_str = json.dumps(act)
                        if "pullRequest" in act_str or "changeSubmitted" in act_str:
                            send_telegram(f"🤖 *Jules completó cambios de código*\nActividad: `{act_type}`")

            # Consultar estado de sesión
            s_url = f"{BASE_URL}/sessions/{session_id}"
            s_resp = requests.get(s_url, headers=headers, timeout=15)
            if s_resp.status_code == 200:
                s_data = s_resp.json()
                state = s_data.get("state")
                print(f"[Deployer] Estado sesión Jules: {state}")
                
                if state == "AWAITING_USER_FEEDBACK":
                    print("[Deployer] Jules requiere feedback/continuación. Enviando confirmación automática...")
                    send_url = f"{BASE_URL}/sessions/{session_id}:sendMessage"
                    cont_body = {
                        "prompt": "Excelente trabajo. Por favor continúa de inmediato con la siguiente fase del plan hasta completar todas las fases pendientes y enviar los cambios finales."
                    }
                    requests.post(send_url, headers=headers, json=cont_body, timeout=15)
                    send_telegram("⚡ *Deployer Autónomo*: Confirmando a Jules continuación de la siguiente fase...")
                    time.sleep(10)
                    continue

                if state == "COMPLETED":
                    print(f"[Deployer] Jules completó todas las tareas exitosamente!")
                    return True
                elif state in ["FAILED", "CANCELLED"]:
                    print(f"[Deployer ERROR] Sesión fallida con estado: {state}", file=sys.stderr)
                    send_telegram(f"⚠️ *Alerta*: Sesión de Jules en estado `{state}`.")
                    return False
        except Exception as e:
            print(f"[Deployer Poll Error] {e}", file=sys.stderr)

        time.sleep(poll_interval)

    print("[Deployer] Timeout esperando a Jules", file=sys.stderr)
    return False

def sync_and_build():
    print("[Deployer] Sincronizando repositorio con GitHub...")
    send_telegram("📥 *Jules terminó*: Sincronizando código desde GitHub y compilando APK...")

    run_cmd("git fetch origin")
    # Traer la rama de trabajo
    run_cmd("git pull origin feature/implementacion-plan-maestro-axiom")

    print("[Deployer] Compilando APK (assembleFossDebug)...")
    build_res = run_cmd("./gradlew assembleFossDebug --no-daemon")
    if build_res.returncode != 0:
        print("[Deployer] Error en compilación de APK", file=sys.stderr)
        send_telegram("❌ *Error de compilación en Gradle* tras cambios de Jules. Revisando...")
        return False

    apk_path = os.path.join(REPO_DIR, "app/build/outputs/apk/foss/debug/app-foss-debug.apk")
    if not os.path.exists(apk_path):
        print(f"[Deployer] APK no encontrada en {apk_path}", file=sys.stderr)
        return False

    print(f"[Deployer] APK compilada exitosamente: {apk_path}")
    return apk_path

def deploy_to_phone(apk_path):
    print(f"[Deployer] Instalando APK en dispositivo {DEVICE_SERIAL}...")
    send_telegram(f"📱 *Instalando APK* en el dispositivo ({DEVICE_SERIAL})...")

    install_res = run_cmd(f"adb -s {DEVICE_SERIAL} install -r {apk_path}")
    if install_res.returncode != 0:
        print("[Deployer] Error instalando APK por ADB", file=sys.stderr)
        send_telegram("❌ *Error al instalar APK por ADB*. Comprueba conexión USB.")
        return False

    print("[Deployer] Iniciando aplicación en el teléfono...")
    run_cmd(f"adb -s {DEVICE_SERIAL} shell am start -n com.fenix.mobilemcp.foss.debug/com.fenix.mobilemcp.ui.MainActivity")
    time.sleep(3)

    # Verificar que el proceso esté vivo
    ps_res = run_cmd(f"adb -s {DEVICE_SERIAL} shell pidof com.fenix.mobilemcp.foss.debug")
    pid = ps_res.stdout.strip()
    if pid:
        print(f"[Deployer] ¡App en ejecución con PID {pid} en el móvil!")
        success_msg = f"🎉 *¡ÉXITO TOTAL!*\n\n" \
                      f"• El plan maestro fue implementado por Google Jules.\n" \
                      f"• Cambios sincronizados y APK compilada.\n" \
                      f"• Instalada y en ejecución en tu Moto G77 (PID: `{pid}`)."
        send_telegram(success_msg)
        return True
    else:
        print("[Deployer] La app no parece estar corriendo tras el arranque", file=sys.stderr)
        return False

def main():
    session_id = "972673826079106203"
    print("=== INICIANDO PIPELINE AUTÓNOMO DE IMPLEMENTACIÓN Y DEPLOY ===")
    
    # 1. Esperar a Jules
    jules_ok = wait_for_jules(session_id, known_activities=4)
    if not jules_ok:
        print("[Deployer] Deteniendo pipeline por fallo en sesión de Jules", file=sys.stderr)
        sys.exit(1)

    # 2. Sincronizar y compilar
    apk_path = sync_and_build()
    if not apk_path:
        sys.exit(2)

    # 3. Desplegar y probar en el móvil
    deployed_ok = deploy_to_phone(apk_path)
    if not deployed_ok:
        sys.exit(3)

    print("=== PIPELINE COMPLETADO CON ÉXITO ===")
    sys.exit(0)

if __name__ == "__main__":
    main()

