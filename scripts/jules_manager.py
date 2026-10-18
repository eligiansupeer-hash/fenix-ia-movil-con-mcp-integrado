#!/usr/bin/env python3
"""
jules_manager.py — Orquestador de comunicación con Google Jules REST API (v1alpha).
Permite monitorear sesiones, leer actividades, inspeccionar planes y autorizar tareas.
"""

import sys
import json
import argparse
import requests

SECRETS_PATH = "/home/eli/.fenix/secrets/api_keys.json"
BASE_URL = "https://jules.googleapis.com/v1alpha"

def get_api_key():
    try:
        with open(SECRETS_PATH, "r") as f:
            data = json.load(f)
        key = data.get("jules", {}).get("api_key", "")
        if not key:
            print("Error: 'jules.api_key' no configurada en api_keys.json", file=sys.stderr)
            sys.exit(1)
        return key
    except Exception as e:
        print(f"Error leyendo secretos: {e}", file=sys.stderr)
        sys.exit(1)

def get_headers():
    return {
        "x-goog-api-key": get_api_key(),
        "Content-Type": "application/json"
    }

def cmd_status(args):
    url = f"{BASE_URL}/sessions/{args.session_id}"
    resp = requests.get(url, headers=get_headers())
    if resp.status_code == 200:
        data = resp.json()
        print(f"ID: {data.get('id')}")
        print(f"Estado: {data.get('state')}")
        print(f"Título: {data.get('title')}")
        print(f"URL Web: {data.get('url')}")
        print(f"Actualizado: {data.get('updateTime')}")
    else:
        print(f"Error {resp.status_code}: {resp.text}", file=sys.stderr)

def cmd_activities(args):
    url = f"{BASE_URL}/sessions/{args.session_id}/activities"
    resp = requests.get(url, headers=get_headers())
    if resp.status_code == 200:
        acts = resp.json().get("activities", [])
        print(f"Total actividades: {len(acts)}")
        for i, a in enumerate(acts):
            print(f"\n--- [Actividad {i+1}] {a.get('createTime')} | Tipo: {a.get('activityType', 'N/A')} ---")
            print(json.dumps(a, indent=2, ensure_ascii=False))
    else:
        print(f"Error {resp.status_code}: {resp.text}", file=sys.stderr)

def main():
    parser = argparse.ArgumentParser(description="Google Jules CLI Orchestrator")
    subparsers = parser.add_subparsers(dest="subcommand", required=True)

    p_status = subparsers.add_parser("status", help="Ver estado de una sesión")
    p_status.add_argument("session_id", help="ID de la sesión de Jules")
    p_status.set_defaults(func=cmd_status)

    p_act = subparsers.add_parser("activities", help="Listar actividades de una sesión")
    p_act.add_argument("session_id", help="ID de la sesión de Jules")
    p_act.set_defaults(func=cmd_activities)

    args = parser.parse_args()
    args.func(args)

if __name__ == "__main__":
    main()

