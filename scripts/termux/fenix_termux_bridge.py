#!/usr/bin/env python3
"""
Fenix Termux Bridge - Microservidor HTTP Soberano (Costo $0)
Compatible con OpenAI API (/v1/models, /v1/chat/completions).
Orquesta OpenCode CLI y Antigravity CLI (`agy`) con soporte para 3 slots de cuenta y auto-failover por cuota.
"""

import http.server
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from urllib.parse import urlparse

PORT = 8081
BASE_DIR = os.path.expanduser("~")
PROFILES_DIR = os.path.join(BASE_DIR, ".gemini", "profiles")
STATE_FILE = os.path.join(BASE_DIR, ".fenix_bridge_state.json")

os.makedirs(PROFILES_DIR, exist_ok=True)
for i in (1, 2, 3):
    os.makedirs(os.path.join(PROFILES_DIR, f"slot_{i}"), exist_ok=True)

state_lock = threading.Lock()

def load_state():
    with state_lock:
        if os.path.exists(STATE_FILE):
            try:
                with open(STATE_FILE, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                pass
        return {
            "active_slot": "slot_1",
            "auto_failover": True,
            "slots": {
                "slot_1": {"label": "Cuenta 1", "has_token": False, "email": None},
                "slot_2": {"label": "Cuenta 2", "has_token": False, "email": None},
                "slot_3": {"label": "Cuenta 3", "has_token": False, "email": None},
            }
        }

def save_state(state):
    with state_lock:
        try:
            with open(STATE_FILE, "w", encoding="utf-8") as f:
                json.dump(state, f, indent=2)
        except Exception as e:
            print(f"[ERROR] No se pudo guardar estado: {e}", file=sys.stderr)

current_state = load_state()

def check_agy_installed():
    if shutil.which("agy"):
        return True
    user_bin = os.path.expanduser("~/.local/bin/agy")
    return os.path.isfile(user_bin) and os.access(user_bin, os.X_OK)

def check_opencode_installed():
    if shutil.which("opencode"):
        return True
    user_bin = os.path.expanduser("~/../usr/bin/opencode")
    return os.path.isfile(user_bin) and os.access(user_bin, os.X_OK)

def get_agy_bin():
    found = shutil.which("agy")
    if found:
        return found
    fallback = os.path.expanduser("~/.local/bin/agy")
    if os.path.exists(fallback):
        return fallback
    return "agy"

def check_slot_tokens(state):
    for i in (1, 2, 3):
        slot_key = f"slot_{i}"
        slot_dir = os.path.join(PROFILES_DIR, slot_key)
        settings_file = os.path.join(slot_dir, "antigravity-cli", "settings.json")
        state_file = os.path.join(slot_dir, "antigravity-cli", "jetski_state.pbtxt")
        has = os.path.exists(settings_file) or os.path.exists(state_file)
        state["slots"][slot_key]["has_token"] = has
    save_state(state)
    return state

class FenixBridgeHandler(http.server.BaseHTTPRequestHandler):
    def _send_json(self, status_code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")

        if path in ("/v1/status", "/status"):
            state = check_slot_tokens(current_state)
            agy_ok = check_agy_installed()
            opencode_ok = check_opencode_installed()
            data = {
                "termux_active": True,
                "opencode_installed": opencode_ok,
                "opencode_connected": opencode_ok,
                "antigravity_installed": agy_ok,
                "antigravity_connected": agy_ok,
                "active_slot": state.get("active_slot", "slot_1"),
                "auto_failover": state.get("auto_failover", True),
                "slots": state.get("slots", {})
            }
            return self._send_json(200, data)

        if path in ("/v1/models", "/models"):
            models = []
            if check_opencode_installed():
                models.append({
                    "id": "opencode",
                    "object": "model",
                    "created": int(time.time()),
                    "owned_by": "termux",
                    "name": "OpenCode CLI (Termux)",
                    "isLocal": True
                })
            if check_agy_installed():
                models.append({
                    "id": "antigravity",
                    "object": "model",
                    "created": int(time.time()),
                    "owned_by": "termux",
                    "name": "Antigravity CLI (Termux)",
                    "isLocal": True
                })
            return self._send_json(200, {"object": "list", "data": models})

        self._send_json(404, {"error": "Ruta no encontrada", "path": path})

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length) if content_length > 0 else b"{}"

        try:
            req = json.loads(post_data.decode("utf-8")) if post_data else {}
        except Exception:
            req = {}

        if path in ("/v1/chat/completions", "/chat/completions"):
            return self.handle_chat_completions(req)

        if path == "/v1/opencode/install":
            return self.handle_opencode_install()

        if path == "/v1/antigravity/install":
            return self.handle_antigravity_install()

        if path == "/v1/antigravity/login":
            slot = req.get("slot", current_state.get("active_slot", "slot_1"))
            return self.handle_antigravity_login(slot)

        if path == "/v1/antigravity/logout":
            slot = req.get("slot", current_state.get("active_slot", "slot_1"))
            return self.handle_antigravity_logout(slot)

        if path == "/v1/antigravity/select_slot":
            slot = req.get("slot", "slot_1")
            current_state["active_slot"] = slot
            save_state(current_state)
            return self._send_json(200, {"ok": True, "active_slot": slot})

        if path == "/v1/antigravity/toggle_failover":
            auto_val = req.get("auto_failover", True)
            current_state["auto_failover"] = bool(auto_val)
            save_state(current_state)
            return self._send_json(200, {"ok": True, "auto_failover": current_state["auto_failover"]})

        self._send_json(404, {"error": "Endpoint POST no encontrado", "path": path})

    def handle_chat_completions(self, req):
        model = req.get("model", "").lower()
        messages = req.get("messages", [])
        if not messages:
            return self._send_json(400, {"error": "Sin mensajes en payload"})

        # Extraer último prompt del usuario
        user_prompt = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                user_prompt = msg.get("content", "")
                break

        if not user_prompt:
            user_prompt = messages[-1].get("content", "")

        if "opencode" in model:
            return self.exec_opencode_prompt(user_prompt)
        else:
            return self.exec_antigravity_prompt(user_prompt)

    def exec_opencode_prompt(self, prompt):
        if not check_opencode_installed():
            return self._send_json(503, {"error": "OpenCode CLI no está instalado en Termux"})

        try:
            cmd = ["opencode", "run", "--prompt", prompt]
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=120)
            output = res.stdout.strip()
            if not output and res.stderr:
                output = res.stderr.strip()

            resp = {
                "id": f"chatcmpl-opencode-{int(time.time())}",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": "opencode",
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": output},
                    "finish_reason": "stop"
                }]
            }
            return self._send_json(200, resp)
        except subprocess.TimeoutExpired:
            return self._send_json(504, {"error": "Tiempo de espera agotado al ejecutar OpenCode CLI"})
        except Exception as e:
            return self._send_json(500, {"error": f"Fallo al ejecutar OpenCode: {str(e)}"})

    def exec_antigravity_prompt(self, prompt, tried_slots=None):
        if not check_agy_installed():
            return self._send_json(503, {"error": "Antigravity CLI (agy) no está instalado en Termux"})

        if tried_slots is None:
            tried_slots = []

        active_slot = current_state.get("active_slot", "slot_1")
        slot_dir = os.path.join(PROFILES_DIR, active_slot)
        env = os.environ.copy()
        env["GEMINI_CLI_HOME"] = slot_dir
        env["HOME"] = slot_dir

        agy_bin = get_agy_bin()
        cmd = [agy_bin, "-p", prompt]

        try:
            res = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=120)
            output = res.stdout.strip()
            err = res.stderr.strip()

            # Comprobar si hubo error 429 de cuota agotada
            is_rate_limited = "429" in err or "quota" in err.lower() or "resource_exhausted" in err.lower()
            if is_rate_limited and current_state.get("auto_failover", True):
                tried_slots.append(active_slot)
                # Buscar siguiente slot disponible
                all_slots = ["slot_1", "slot_2", "slot_3"]
                next_slots = [s for s in all_slots if s not in tried_slots and current_state["slots"].get(s, {}).get("has_token")]
                if next_slots:
                    new_slot = next_slots[0]
                    print(f"[FAILOVER] Cuota agotada en {active_slot}. Conmutando a {new_slot}...", file=sys.stderr)
                    current_state["active_slot"] = new_slot
                    save_state(current_state)
                    return self.exec_antigravity_prompt(prompt, tried_slots=tried_slots)

            final_content = output if output else err
            if not final_content:
                final_content = "Comando ejecutado sin respuesta visible."

            resp = {
                "id": f"chatcmpl-agy-{int(time.time())}",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": "antigravity",
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": final_content},
                    "finish_reason": "stop"
                }]
            }
            return self._send_json(200, resp)
        except subprocess.TimeoutExpired:
            return self._send_json(504, {"error": "Tiempo de espera agotado al ejecutar Antigravity CLI"})
        except Exception as e:
            return self._send_json(500, {"error": f"Fallo al invocar Antigravity: {str(e)}"})

    def handle_opencode_install(self):
        def worker():
            try:
                subprocess.run(["pkg", "install", "-y", "nodejs-lts"], check=True)
                subprocess.run(["npm", "install", "-g", "opencode-ai"], check=True)
            except Exception as e:
                print(f"[ERROR] Instalación OpenCode falló: {e}", file=sys.stderr)
        threading.Thread(target=worker, daemon=True).start()
        return self._send_json(202, {"ok": True, "status": "Instalando OpenCode en segundo plano"})

    def handle_antigravity_install(self):
        def worker():
            try:
                target_dir = os.path.expanduser("~/.local/bin")
                os.makedirs(target_dir, exist_ok=True)
                url = "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.4.2-4835981233356800/android-arm/cli_android_arm64.tar.gz"
                tar_path = "/tmp/agy.tar.gz"
                subprocess.run(["curl", "-fsSL", "-o", tar_path, url], check=True)
                subprocess.run(["tar", "-xzf", tar_path, "-C", target_dir], check=True)
                bin_path = os.path.join(target_dir, "antigravity")
                if os.path.exists(bin_path):
                    shutil.move(bin_path, os.path.join(target_dir, "agy"))
                    os.chmod(os.path.join(target_dir, "agy"), 0o755)
                if os.path.exists(tar_path):
                    os.remove(tar_path)
            except Exception as e:
                print(f"[ERROR] Instalación Antigravity falló: {e}", file=sys.stderr)
        threading.Thread(target=worker, daemon=True).start()
        return self._send_json(202, {"ok": True, "status": "Descargando e instalando Antigravity ARM64 en segundo plano"})

    def handle_antigravity_login(self, slot):
        slot_dir = os.path.join(PROFILES_DIR, slot)
        os.makedirs(slot_dir, exist_ok=True)
        # Retorna la URL estándar OAuth para abrir en navegador
        auth_url = "https://accounts.google.com/o/oauth2/v2/auth"
        return self._send_json(200, {
            "ok": True,
            "slot": slot,
            "login_url": auth_url,
            "instructions": f"Iniciando flujo OAuth para {slot}."
        })

    def handle_antigravity_logout(self, slot):
        slot_dir = os.path.join(PROFILES_DIR, slot)
        if os.path.exists(slot_dir):
            shutil.rmtree(slot_dir, ignore_errors=True)
            os.makedirs(slot_dir, exist_ok=True)
        current_state["slots"][slot]["has_token"] = False
        save_state(current_state)
        return self._send_json(200, {"ok": True, "slot": slot, "status": "Sesión cerrada"})

def run_server():
    server = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), FenixBridgeHandler)
    print(f"[FENIX BRIDGE] Escuchando en 127.0.0.1:{PORT}...", file=sys.stdout)
    server.serve_forever()

if __name__ == "__main__":
    run_server()

