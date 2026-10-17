# qa-evidence — Evidencia QA Fénix (anti-alucinaciones)

Cada prueba deja aquí su evidencia; **nada se cierra "porque lo vi yo"**.

## Estructura (REGLA 4 de `skills/fenix-qa-evidence.md`)

```
docs/qa-evidence/
└── S{suite}/                      ej: S0/
    ├── S{X}-{NN}_{nombre}/        <- UNA carpeta por ID de test
    │   ├── result.json            isError=false + fragmento de log citado
    │   ├── screen.png             captura post-acción (si aplica pantalla)
    │   ├── log.txt                salida cruda del tool/logcat/doctor
    │   └── raw-response.txt       cuerpo HTTP (si aplica)
    ├── RESULTADO_GATE_S0T.json    resumen de la suite (NO es evidencia de un ID)
    └── smoke.ad                   script grabado reproducible
```

## Cierre obligatorio (REGLA 7) — el equivalente que admita tu entorno

- **Con shell** (opencode, antigravity, terminal):
  `scripts/qa-evidence-check.sh docs/qa-evidence/S0/S0T-06_smoke_snapshot`
  → exit `0` = `EVIDENCIA_OK` (único estado que permite marcar PASS); exit `1` = PASS rechazado.
- **Sin shell** (cliente solo chat): tool MCP `fenix_qa_evidence_check(carpeta)` del servidor Fénix
  → `{"verdict": "EVIDENCIA_OK" | "EVIDENCIA_FAIL", "faltantes": [...]}`.

## Qué comprueba el validador (por programa, nunca a ojo)

1. `result.json` existe y es JSON válido con `isError == false`;
2. `screen.png` existe y no está vacío — salvo `"screen_required": false` con motivo
   (chequeos de tooling sin pantalla);
3. `log.txt` existe, no está vacío y contiene **literalmente** `log_must_contain`;
4. **Binding de corrida (REGLA 7):** `result.json` trae `"run_id"` y ese mismo token
   aparece literalmente en `log.txt`. Generar con `RUN_ID=$(date +%s%N)` al arrancar
   el test y ecoarlo como primera línea del log. Sin igualdad exacta → FAIL
   (evidencia bien formada pero desvinculada de su corrida).

## Control negativo (REGLA 8)

Antes de suites, el tester debe demostrar que detecta un fallo (carpeta inexistente,
`isError=true`, falta de `screen.png`, log sin el fragmento citado → todos tienen que
reportarse `EVIDENCIA_FAIL`). Si uno sale `EVIDENCIA_OK` → no está calibrado: no suites.

Nada de esto exige sub-agentes ni nombres de agente: ejecutor y verificador son sombreros.
