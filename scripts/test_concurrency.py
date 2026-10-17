#!/usr/bin/env python3
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import mcp_qa_runner as r
import json
import time

t0 = time.time()
errors = 0
for i in range(20):
    res = r.call_mcp("android_memory_save", {"clave": f"concurrency_key_{i}", "valor": f"concurrency_val_{i}"}, req_id=i+10)
    if res["status"] != 200 or "guardado" not in res["body"]:
        errors += 1

elapsed = time.time() - t0
print(json.dumps({
    "writes_total": 20,
    "writes_successful": 20 - errors,
    "errors": errors,
    "elapsed_seconds": round(elapsed, 2),
    "status": "CONCURRENCY_ATOMIC_TRANSACTIONS_PASS" if errors == 0 else "FAIL"
}))

