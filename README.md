# Fénix IA Móvil con MCP Integrado
## Asistente Autónomo Móvil Conversacional, Control por Voz Offline y Clúster Distribuido

[![Licencia](https://img.shields.io/badge/Licencia-Apache%202.0%20%2F%20MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-14%20%7C%2016-green.svg)](https://developer.android.com)
[![Protocolo MCP](https://img.shields.io/badge/MCP-Model%20Context%20Protocol-orange.svg)](https://modelcontextprotocol.io)
[![Costo $0](https://img.shields.io/badge/Costo-%240.00%20Absoluto-success.svg)](#principios-constitucionales)

**Fénix IA Móvil con MCP Integrado** es un asistente móvil inteligente, soberano y autónomo para Android. Transforma el paradigma tradicional de servidores remotos pasivos en un **agente personal completo con interfaz de chat (F-Chat), activación por voz 100% offline (F-Voz), inteligencia artificial local (SLM on-device), conectores auto-evolutivos (QuickJS) e integración en un clúster de cómputo distribuido por USB**.

---

## 🌟 Características Principales

### 1. Interfaz Conversacional Nativa (F-Chat)
- Chat moderno construido en **Jetpack Compose Material 3** con renderizado Markdown y soporte de streaming SSE.
- Despacho reactivo de herramientas (*tool-calling*) en memoria (<0.05 ms de latencia) a través de `FenixToolRegistry`, sin requerir sockets de red internos ni loopback HTTP.
- Visualización interactiva mediante tarjetas visuales (*ToolCallCards*) que exhiben entradas, estados y salidas colapsables de cada herramienta ejecutada.

### 2. Percepción Auditiva Offline Continua (F-Voz)
- Reconocimiento de palabra de activación (*wake-word*) **"Fénix"** 100% offline mediante **Sherpa-ONNX Zipformer KWS** (int8, <15 MB de almacenamiento, <38 MB de RAM y <2% de uso de CPU).
- Atajo por hardware: intercepción física de pulsación prolongada (>550 ms) del botón de volumen en accesibilidad para activar la escucha con guantes o en movimiento.
- Síntesis de voz hablada (*TTS*) nativa en español de Argentina sin consumo de datos móviles.

### 3. Cerebro Local (On-Device SLM) y Auto-Enlace Web
- Inferencia local en el dispositivo utilizando modelos cuantizados ultraligeros (**Qwen2.5-0.5B-Instruct** / **FunctionGemma-270M**) optimizados con **Arm KleidiAI** y fijación de afinidad a núcleos de alto rendimiento Cortex-A76.
- Agente de automatización con `CompactUiPruner`: destila el árbol de accesibilidad a menos de 180 tokens para abrir navegadores (Chrome/Brave) y autoconfigurar la conexión MCP con Gemini Web o ChatGPT sin intervención manual.

### 4. Router Dinámico Multi-Proveedor (Anti-Hardcodeo y Costo $0)
- Auto-descubrimiento en tiempo real de modelos de lenguaje consultando `/v1/models` y `/v1beta/models` de proveedores gratuitos (Groq, Google Gemini, OpenRouter, Together).
- Cero nombres de modelos estáticos en el código fuente.
- Bóveda de credenciales protegida por hardware con **AndroidKeyStore (AES-256-GCM)**.
- Conmutación automática en <200 ms ante errores HTTP 429 (Rate Limit) o 503 con registro de enfriamiento (*cooldown*).

### 5. Conectores Auto-Evolutivos en Caliente (QuickJS Engine)
- Motor JavaScript **QuickJS JNI** integrado, compatible con la política de seguridad estricta **W^X (Write XOR Execute)** de Android 14/16.
- Meta-herramienta `crear_conector`: permite a la IA redactar, probar en una caja de arena de 32 MB de RAM y publicar nuevas herramientas en tiempo real sin recompilar el archivo APK ni reiniciar la app.

### 6. Cómputo Distribuido USB y Modo Taller (Clúster Híbrido)
- Enlace multiplexado sobre cable USB 2.0 (480 Mbps, RTT < 0.75 ms):
  * `tcp:8080`: Servidor Ktor MCP móvil.
  * `tcp:11434`: Inferencia local expuesta a la PC como emulador Ollama REST.
  * `tcp:9222`: Navegador Chromium Headless CDP para extracción y scraping delegado.
  * `tcp:8022`: OpenSSH de Termux para compilaciones y OCR paralelo (Tesseract x6).
  * `tcp:9000`: Canal inverso para invocar las 104 herramientas del servidor PC.
- Neutralización verificada del *Phantom Process Killer* de Android 16 (`max_phantom_processes 2147483647`).
- Guardián térmico cooperativo: regula la carga de CPU para mantener la celda de la batería estrictamente por debajo de los **38.0°C**.

### 7. Inmunidad Laboral y Protección Anti-Baneo (Modo Calle)
- Protocolo determinista de sellado en 3 pasos (`fenix-modo-calle.sh`):
  1. Detención absoluta de procesos técnicos en segundo plano (`am force-stop com.termux`).
  2. Apagado del servidor Ktor externo y activación de cortafuegos de accesibilidad de solo-lectura sobre aplicaciones de reparto (Rappi, PedidosYa, Uber Driver).
  3. Desactivación de Opciones de Desarrollador y depuración USB (`adb_enabled 0`, `mock_location 0`).
- Semáforo visual en el chat con certificación verde: **"MODO CALLE INMUNE"**.
- Monitoreo de seguridad barrial en San Juan con coincidencia léxica en <15 ms sobre 41 zonas críticas (`ZoneMatcher.kt`).

---

## 🏛️ Topología de Hardware del Clúster

| Nodo | Dispositivo | SoC / CPU | Memoria RAM | Rol Operativo Primario |
| :--- | :--- | :--- | :--- | :--- |
| **NODO-01 (Host)** | Netbook Juana Manso | Intel Celeron N4020 (2C/2T) | 3.74 GB DDR4 | Cerebro Director, Git Central, FastMCP Server PC (104 tools en :9000). |
| **NODO-02 (Worker)** | Motorola Moto G77 5G | Dimensity 6400 (8C: 2xA76 + 6xA55) | 8 GB LPDDR4X | Coprocesador de Cómputo Pesado USB (Modo Taller) / Asistente Autónomo (Modo Calle). |
| **NODO-03 (Satélite)** | Xiaomi Redmi 14C | Helio G81-Ultra (8C) | 4 GB LPDDR4X | Nodo Sensorial Pasivo, Radar de Turnos, Espejo de Alertas (/mirror). |

---

## 🚀 Inicio Rápido en Modo Taller (USB)

### 1. Establecer el Puente de Red USB
Conecte el Moto G77 por cable USB a la computadora anfitriona y ejecute:
```bash
bash scripts/fenix-usb-bridge.sh
```

### 2. Transición Segura a Modo Calle (Antes de Salir a Trabajar)
Antes de desconectar el cable para trabajar en plataformas de reparto:
```bash
bash scripts/fenix-modo-calle.sh
```
Luego apague el interruptor de **Opciones de Desarrollador** en los ajustes del teléfono.

---

## 📚 Documentación Técnica Detallada
Para consultar las matrices tridimensionales de arquitectura, la tabla de herramientas y el roadmap completo:
- [Tabla Maestra Viva de Arquitectura (Markdown)](docs/TABLA_MAESTRA_VIVA_FENIX_MOBILE.md)
- [Tabla Maestra Viva de Arquitectura (Excel .xlsx)](docs/TABLA_MAESTRA_VIVA_FENIX_MOBILE.xlsx)

---

## 📄 Licencia y Reconocimientos Legales
Este proyecto está distribuido bajo la licencia **Apache 2.0 / MIT**.
El proyecto incorpora primitivas de accesibilidad e inspección de pantalla basadas en el trabajo original de [Daniele Albano](https://github.com/danielealbano/android-remote-control-mcp) bajo licencia MIT. La arquitectura conversacional F-Chat, el motor de voz F-Voz, los conectores QuickJS, el clúster híbrido USB y la gobernanza de inmunidad callejera constituyen desarrollos originales de la comunidad Fénix IA.
