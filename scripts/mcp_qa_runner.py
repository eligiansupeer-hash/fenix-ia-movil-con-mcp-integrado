#!/usr/bin/env python3
"""
mcp_qa_runner.py — Ejecutor determinista de QA para Fénix Mobile MCP.
Comunica directamente por HTTP POST JSON-RPC contra el servidor Ktor del móvil en 127.0.0.1:8080/mcp.
Genera la evidencia canónica (result.json, log.txt, raw-response.txt) requerida por fenix_qa_evidence_check.
CERO interferencia con la netbook, CERO aperturas de navegador.
"""
import sys
import json
import urllib.request
import urllib.error
import time
import os

MCP_URL = os.environ.get("FENIX_MOBILE_URL", "http://192.168.0.103:8080/mcp")
OPENAPI_URL = MCP_URL.replace("/mcp", "/openapi.json")
TOKEN = os.environ.get("FENIX_MOBILE_TOKEN", "ce754d4a-f129-4f81-9e20-b80e0dc5f18b")
RUN_ID = "20261016_QA_MOTO_G77_RONDA2"
DEVICE_SERIAL = "ZY22MK7B2P"
EVIDENCE_BASE = "/home/eli/Documentos/fenix-mobile-mcp/docs/qa-evidence"

def call_mcp(tool_name: str, arguments: dict, req_id: int = 1) -> dict:
    payload = {
        "jsonrpc": "2.0",
        "id": req_id,
        "method": "tools/call",
        "params": {
            "name": tool_name,
            "arguments": arguments
        }
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        MCP_URL,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {TOKEN}"
        }
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            elapsed_ms = int((time.time() - t0) * 1000)
            body = resp.read().decode("utf-8")
            return {
                "status": resp.status,
                "body": body,
                "elapsed_ms": elapsed_ms,
                "error": None
            }
    except urllib.error.HTTPError as e:
        elapsed_ms = int((time.time() - t0) * 1000)
        body = e.read().decode("utf-8") if e.fp else ""
        return {
            "status": e.code,
            "body": body,
            "elapsed_ms": elapsed_ms,
            "error": f"HTTPError {e.code}"
        }
    except Exception as e:
        elapsed_ms = int((time.time() - t0) * 1000)
        return {
            "status": 0,
            "body": "",
            "elapsed_ms": elapsed_ms,
            "error": str(e)
        }

def list_mcp_tools() -> dict:
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/list",
        "params": {}
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        MCP_URL,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {TOKEN}"
        }
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))

def save_evidence(suite_dir: str, test_id: str, name: str, is_error: bool, log_must_contain: str, log_lines: list, raw_response: str, screen_required: bool = False):
    folder = os.path.join(EVIDENCE_BASE, suite_dir)
    os.makedirs(folder, exist_ok=True)
    
    # 1. raw-response.txt
    with open(os.path.join(folder, "raw-response.txt"), "w", encoding="utf-8") as f:
        f.write(raw_response)
        
    # 2. log.txt
    full_log = [f"RUN_ID={RUN_ID}"] + log_lines
    with open(os.path.join(folder, "log.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(full_log) + "\n")
        
    # 3. result.json
    result = {
        "id": test_id,
        "nombre": name,
        "device": DEVICE_SERIAL,
        "isError": is_error,
        "run_id": RUN_ID,
        "log_must_contain": log_must_contain,
        "screen_required": screen_required
    }
    with open(os.path.join(folder, "result.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, ensure_ascii=False)
        f.write("\n")

if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else "list"
    if action == "list":
        tools = list_mcp_tools()
        names = [t["name"] for t in tools.get("result", {}).get("tools", [])]
        print(f"Total tools: {len(names)}")
        print(json.dumps(names, indent=2))
    elif action == "call":
        tname = sys.argv[2]
        targs = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
        res = call_mcp(tname, targs)
        print(f"Status: {res['status']} ({res['elapsed_ms']} ms)")
        print(res["body"])

