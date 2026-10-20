# PLAN TÉCNICO MAESTRO: INTEGRACIÓN NATIVA TERMUX, OPENCODE Y ANTIGRAVITY

## OBJETIVO
Integrar de forma 100% funcional y verificada en la aplicación Android **Fénix Móvil MCP** la capacidad de ejecutar, gestionar y comunicarse directamente desde el chat de Fénix con agentes de IA locales ejecutados en **Termux**: **OpenCode CLI** y **Antigravity CLI (`agy`)**, con soporte multicuenta (pool de 3 cuentas Google) y auto-failover por agotamiento de cuota.

---

## 1. DIRECTIVAS CONSTITUCIONALES
1. **Costo $0 Absoluto:** No se requiere ningún servicio de pago ni infraestructura paga.
2. **Cero Hardcodeo:** Toda detección de modelos se realiza dinámicamente mediante la API abierta compatible OpenAI (`/v1/models`).
3. **Cero Simulación:** Cada botón refleja estados reales comprobados mediante `PackageManager` o consultas HTTP a `127.0.0.1:8081`.
4. **Estados Reactivos de Transición:**
   - Todo botón de descarga pasa a estado transitorio **`Descargando...`** mientras se realiza la operación, cambiando luego a **`Instalado ✓`**.
   - Todo botón de conexión pasa a **`Conectando...`** antes de confirmar el estado **`Conectado ●`**.
5. **No Intervención Manual del Usuario en Terminal:** El usuario nunca debe tipear comandos manualmente en Termux. Toda la orquestación ocurre mediante `Intents` de Android y llamadas HTTP al micro-puente.

---

## 2. ARQUITECTURA TÉCNICA

```
┌────────────────────────────────────────────────────────┐
│                      FÉNIX MÓVIL                       │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Bóveda Segura (Proveedores IA)                   │  │
│  │  - Tarjeta Termux (Descargar / Activar)          │  │
│  │  - Tarjeta OpenCode (Descargar / Conectar)       │  │
│  │  - Tarjeta Antigravity (Descargar / Conectar)    │  │
│  │    └─ Slots Cuenta 1, 2, 3 (Login Google OAuth) │  │
│  └──────────────────────────────────────────────────┘  │
│                           ▲                            │
│                  HTTP Local / Loopback                 │
│                 (http://127.0.0.1:8081)                │
│                           ▼                            │
│  ┌──────────────────────────────────────────────────┐  │
│  │ TERMUX BACKEND (Sandbox com.termux)              │  │
│  │  - fenix_termux_bridge.py (Servidor HTTP local)  │  │
│  │  - opencode CLI                                  │  │
│  │  - antigravity CLI (`agy` arm64 Bionic)          │  │
│  │  - ~/.gemini/profiles/ (slot_1, slot_2, slot_3)  │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## 3. ESPECIFICACIÓN DE COMPONENTES Y FASES

### FASE 1: Permisos del Sistema en `AndroidManifest.xml`
- Declaración de permiso `com.termux.permission.RUN_COMMAND`.
- Declaración en `<queries>` del paquete `com.termux` para visibilidad en Android 11+.

### FASE 2: Micro-Servidor Autónomo `fenix_termux_bridge.py`
- Servidor HTTP ligero en Python (biblioteca estándar `http.server` y `subprocess`).
- Endpoints:
  - `GET /v1/status`: Retorna estado JSON de Termux, OpenCode, Antigravity y cuentas configuradas.
  - `GET /v1/models`: Lista los modelos locales descubiertos (`opencode`, `antigravity`).
  - `POST /v1/chat/completions`: Formato estándar OpenAI; rutea la consulta a `opencode run` o `agy -p` según el modelo seleccionado.
  - `POST /v1/opencode/install`: Descarga e instala OpenCode de forma desatendida.
  - `POST /v1/antigravity/install`: Descarga y configura Antigravity Bionic arm64 oficial.
  - `POST /v1/antigravity/login`: Inicia el flujo OAuth para el slot indicado y devuelve la URL para abrir el navegador en Android.
  - `POST /v1/antigravity/logout`: Limpia el token del slot seleccionado.
- Failover por cuota: Ante código 429 de cuota agotada en Antigravity, conmuta al siguiente slot con token válido.

### FASE 3: Capa de Servicio Android `TermuxBridgeManager.kt`
- Gestión de descarga directa de Termux APK desde GitHub Releases oficiales (`arm64-v8a`).
- Lanzamiento de Intent `com.termux.RUN_COMMAND` en segundo plano para activar el puente sin abrir la interfaz de consola.
- Monitoreo periódico del estado mediante sondeo a `http://127.0.0.1:8081/v1/status`.

### FASE 4: Componente de Interfaz `TermuxVaultCard.kt`
- Tarjeta de Termux con texto exacto de programación y cuotas gratuitas.
- Botones reactivos con transiciones:
  - `[Descargar Termux APK]` $\rightarrow$ `[Descargando...]` $\rightarrow$ `[Instalado ✓]`
  - `[Activar Termux]` $\rightarrow$ `[Activando...]` $\rightarrow$ `[Activo ●]`
- Tarjeta OpenCode:
  - Banner bloqueante si Termux no está instalado: *"Debes instalar Termux primero"*.
  - `[Descargar OpenCode]` $\rightarrow$ `[Descargando...]` $\rightarrow$ `[Instalado ✓]`
  - `[Conectar]` $\rightarrow$ `[Conectando...]` $\rightarrow$ `[Conectado ●]`
- Tarjeta Antigravity:
  - `[Descargar Antigravity]` $\rightarrow$ `[Descargando...]` $\rightarrow$ `[Instalado ✓]`
  - `[Conectar]` $\rightarrow$ `[Conectando...]` $\rightarrow$ `[Conectado ●]`
  - **Slots de Cuenta (revelados solo tras conectar):**
    - Slot 1, Slot 2, Slot 3.
    - Botón `[Iniciar Sesión (Google)]` (abre navegador oficial OAuth).
    - Botón `[Cerrar Sesión]`.
    - Switch de rotación automática por cuota agotada.

### FASE 5: Integración en `KeyVaultDialog` de `ChatScreen.kt`
- Se ubica `TermuxVaultCard` en la parte superior de la pestaña `Proveedores IA` como Proveedores Principales.
- Se preserva el botón `[+ Agregar Proveedor Personalizado]` y todas las tarjetas cloud inferiores.
- Pestaña `Conectores` se mantiene intacta.

### FASE 6: Compilación en CI y Validación en Dispositivos Físicos
- Compilación en GitHub Actions sin consumo de recursos locales.
- Instalación en dispositivo físico y comprobación funcional pantalla por pantalla.

