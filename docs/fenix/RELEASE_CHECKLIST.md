# Release checklist Fénix Mobile MCP (F9)

1. `FenixReleaseInfo.GITHUB_OWNER` = cuenta real (hoy `FENIX_GITHUB_OWNER`).
2. Badges README + CI apuntando al repo fenix (hoy upstream).
3. Secret scan + DLP: `github_run_secret_scanning`; prohibido seriales (G77-REF/14C-REF), `auth.json`, `tokens.db`, `*.p12`, builds con identifiers, fotos EXIF.
4. Compilar foss+gms en Actions (sin SDK local); instalar en G77/14C por "instalar desconocidas".
5. Pruebas en fierro: Accessibility 1-vez + batería Unrestricted (+Autostart Xiaomi), `tools/list` vía inspector, zona de prueba, turno radar, transferencia A2A g77↔lake, Dev OFF final (delivery).
6. Push + release pública (gate humano confirmar).
