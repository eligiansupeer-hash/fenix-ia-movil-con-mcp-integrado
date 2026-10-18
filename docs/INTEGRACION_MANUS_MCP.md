# Integración Técnica y Protocolo de Conexión: Manus 2.0 (Mobile / CLI) ↔ Fénix MCP

**Fecha de Verificación y Registro Real:** 17 de octubre de 2026  
**Agente Auditor:** Antigravity (Google DeepMind) en interacción directa con Manus 2.0 Lite (`tech.butterfly.app`)  
**Dispositivo de Prueba:** Motorola Moto G77 (`ZY22MK7B2P`) — Android 14 / SDK 34  
**Servidor MCP Móvil:** `com.fenix.mobilemcp` (Fénix Mobile MCP v3 Release)  
**Endpoint de Transporte:** Quick Tunnel HTTPS de Cloudflare (`mcp/http`) con autenticación Bearer Token  

---

## 1. Resumen Ejecutivo de la Conexión

- **Estado de la Conexión:** **EXITOSA / COMPATIBLE (100%)**
- **Nombre del Conector en Manus:** `mobile_mcp_bearer`
- **Herramientas Descubiertas por Manus:** **97 herramientas MCP** disponibles y registradas.
- **Herramientas Probadas en Vivo (QA):**
  - Descubrimiento e inspección de catálogo de esquemas (`tools/list`).
  - Consulta de almacenamiento de solo lectura (`android_storage_query`): reportó `100,543,565,824 bytes disponibles (~100.54 GB)`.
  - Verificación previa de esquemas antes de ejecución (inspección rigurosa de seguridad por parte de Manus).

---

## 2. Modos de Conexión Admitidos por Manus 2.0

De acuerdo con la auditoría interactiva y el diagnóstico interno reportado por el propio motor de Manus:

| Modo de Conexión | Compatibilidad | Características Técnicas y Comportamiento |
| :--- | :---: | :--- |
| **HTTP Remoto / Streamable HTTP** | **SÍ (Verificado)** | El transporte activo detectado es `mcp/http`. Responde a `tools/list` y llamadas `tools/call`. |
| **Modo URL (Directo)** | **Parcial / Limitado** | Diseñado para endpoints públicos o que inicien flujo interactivo **OAuth 2.0**. **NO admite cabeceras de autorización manuales ni Bearer token** en su definición básica. |
| **Modo Formulario (Form Mode)** | **SÍ (Recomendado para Bearer)** | Admite configuración de cabeceras personalizadas (`Authorization: Bearer <token>`) como pares clave/valor, credenciales cliente OAuth y endpoints remotos. |
| **stdio (Local CLI)** | **SÍ (Teórico)** | El modo formulario admite configurar un comando ejecutable local, sujeto a que el entorno de ejecución de Manus tenga permisos de shell para lanzarlo. |
| **SSE (Server-Sent Events) Puro** | **No distinguible por separado** | Manus reporta transporte unificado `mcp/http`. No diferencia explícitamente entre SSE puro y HTTP Streaming sin prueba a nivel de socket. |

> [!IMPORTANT]
> **Hallazgo Crítico de Configuración en Manus:**
> Si un conector ya fue creado en **Modo URL**, el CLI interno de Manus **no permite mutar su tipo a Modo Formulario** mediante una actualización (`update`). Para utilizar autenticación con token (`Bearer`), se debe instanciar un conector nuevo directamente en **Modo Formulario** con la cabecera `Authorization: Bearer <token>`.

---

## 3. Límite de Conexiones y Conectores Simultáneos

- **Límite Numérico Rígido:** Manus no impone un límite explícito o documentado en su CLI.
- **Capacidad Concurrente:** Durante la prueba se verificó la existencia simultánea de múltiples conectores personalizados (ej. `mobile_mcp` deshabilitado y `mobile_mcp_bearer` activo al mismo tiempo).
- **Aislamiento de Estado:** Los conectores mantienen sus configuraciones de forma independiente, permitiendo habilitar o inhabilitar conectores según la tarea.

---

## 4. Gestión Interna de Conectores (CLI & Seguridad)

1. **Gestión de Ciclo de Vida:**
   - La arquitectura de Manus permite `crear`, `localizar`, `consultar`, `actualizar` y `deshabilitar` conectores.
2. **Seguridad de Credenciales:**
   - Manus enmascara automáticamente los secretos en sus logs y salidas de consola (ej. `ff3*****6`).
   - Las consultas a la configuración no exponen las claves de autorización en texto claro.
   - Manus recomienda explícitamente rotar los tokens si fueron compartidos en el historial del chat.
3. **Validación Preventiva de Esquemas:**
   - Antes de disparar cualquier herramienta (`tools/call`), Manus inspecciona el esquema JSON y los parámetros requeridos. Si la herramienta solicitada no coincide exactamente con el catálogo publicado, suspende la llamada para evitar errores de ejecución.

---

## 5. Instrucciones de Configuración Paso a Paso para la PWA Fénix

*(Documentación preparada para ser integrada en la pestaña `Servidor` de Fénix en la próxima actualización UI)*

### Pasos para Conectar Manus con el Servidor Fénix Mobile MCP:
1. **En Fénix:**
   - Ir a la pestaña **Servidor**.
   - Habilitar el servidor MCP (Puerto `8080`) y activar el túnel **Cloudflare**.
   - Copiar la URL pública generada (`https://<subdominio>.trycloudflare.com/mcp`) y el **Bearer Token**.
2. **En Manus:**
   - Abrir un chat o la consola de configuración de conectores.
   - Solicitar crear un conector MCP en **Modo Formulario** (o con cabecera de autorización).
   - Indicar:
     - **URL:** `https://<subdominio>.trycloudflare.com/mcp`
     - **Cabecera:** `Authorization: Bearer <token_de_fenix>`
   - Manus descubrirá automáticamente las 97 herramientas nativas y quedará conectado de forma bidireccional al ecosistema móvil.

