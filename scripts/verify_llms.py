#!/usr/bin/env python3
import json
import urllib.request
import urllib.error
import os

SECRETS_PATH = os.path.expanduser("~/.fenix/secrets/api_keys.json")
with open(SECRETS_PATH) as f:
    secrets = json.load(f)

def get_key(name):
    entry = secrets.get(name)
    if isinstance(entry, dict):
        return entry.get("api_key") or entry.get("token")
    return entry

providers = [
    ("S1-01", "groq", "https://api.groq.com/openai/v1/models", get_key("groq")),
    ("S1-02", "openrouter", "https://openrouter.ai/api/v1/models", get_key("openrouter")),
    ("S1-03", "gemini", "https://generativelanguage.googleapis.com/v1beta/openai/models", get_key("gemini")),
    ("S1-04", "sambanova", "https://api.sambanova.ai/v1/models", get_key("sambanova")),
    ("S1-05", "cerebras", "https://api.cerebras.ai/v1/models", get_key("cerebras")),
    ("S1-06", "mistral", "https://api.mistral.ai/v1/models", get_key("mistral")),
    ("S1-07", "cohere", "https://api.cohere.ai/compatibility/v1/models", get_key("cohere")),
]

results = {}
for pid, name, url, key in providers:
    if not key:
        results[pid] = {"name": name, "ok": False, "error": "No key"}
        continue
    headers = {"Authorization": f"Bearer {key}"}
    if name == "openrouter":
        headers["HTTP-Referer"] = "https://fenix-mobile-mcp.app"
        headers["X-Title"] = "Fenix-Mobile-MCP"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode())
            models = [m.get("id") for m in data.get("data", [])]
            results[pid] = {
                "name": name,
                "status": resp.status,
                "models_count": len(models),
                "sample": models[:3] if models else []
            }
    except Exception as e:
        results[pid] = {"name": name, "error": str(e)}

print(json.dumps(results, indent=2))

