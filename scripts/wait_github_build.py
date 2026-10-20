#!/usr/bin/env python3
import json
import time
import urllib.request
import sys

REPO = "eligiansupeer-hash/fenix-ia-movil-con-mcp-integrado"
RUN_ID = "39178450606"
CHECK_INTERVAL_SECONDS = 20

print(f"=== MONITOREANDO BUILD DE GITHUB ACTIONS (Run #{RUN_ID}) ===")

while True:
    try:
        url = f"https://api.github.com/repos/{REPO}/actions/runs/{RUN_ID}"
        req = urllib.request.Request(url, headers={"User-Agent": "Antigravity-Agent"})
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
        
        status = data.get("status")
        conclusion = data.get("conclusion")
        print(f"[{time.strftime('%H:%M:%S')}] Estado: {status} | Conclusion: {conclusion}")
        
        if status == "completed":
            print(f"\n>>> RUN FINALIZADO: {conclusion} <<<")
            
            # Consultar artefactos si fue exitoso
            art_url = f"https://api.github.com/repos/{REPO}/actions/runs/{RUN_ID}/artifacts"
            req_art = urllib.request.Request(art_url, headers={"User-Agent": "Antigravity-Agent"})
            with urllib.request.urlopen(req_art) as resp_art:
                art_data = json.loads(resp_art.read().decode())
            
            artifacts = art_data.get("artifacts", [])
            print(f"Artefactos disponibles ({len(artifacts)}):")
            for art in artifacts:
                print(f" - {art.get('name')} ({art.get('size_in_bytes')} bytes) | ID: {art.get('id')}")
            
            if conclusion == "success":
                sys.exit(0)
            else:
                sys.exit(1)

    except Exception as e:
        print(f"[{time.strftime('%H:%M:%S')}] Advertencia en consulta: {e}")

    time.sleep(CHECK_INTERVAL_SECONDS)

