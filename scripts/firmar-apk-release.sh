#!/usr/bin/env bash
# ==============================================================================
# Fénix Mobile MCP - Script de Firma Criptográfica Release (Anti-Play Protect)
# Esquemas: APK Signature Scheme v1, v2, v3
# Certificado: PKCS#12, RSA 4096-bit, 30 años de validez (10950 días)
# ==============================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DEV="/home/eli/android-dev"
export JAVA_HOME="${ANDROID_DEV}/jdk-17.0.11+9"
export PATH="${JAVA_HOME}/bin:${PATH}"
BUILD_TOOLS="${ANDROID_DEV}/build-tools/36.0.0"
KEYTOOL="${JAVA_HOME}/bin/keytool"
ZIPALIGN="${BUILD_TOOLS}/zipalign"
APKSIGNER="${BUILD_TOOLS}/apksigner"

KEYSTORE_PATH="${PROJECT_DIR}/fenix-release-key.p12"
KEY_ALIAS="fenix-soberano"
STORE_PASS="FenixMobileSoberano2026!"

echo "=== Fénix Mobile: Generación de Keystore y Firma Release ==="

# 1. Generar Keystore formal si no existe
if [ ! -f "${KEYSTORE_PATH}" ]; then
    echo "[+] Generando Keystore PKCS#12 con RSA 4096 bits y 30 años de validez..."
    "${KEYTOOL}" -genkeypair -v \
        -keystore "${KEYSTORE_PATH}" \
        -storetype PKCS12 \
        -alias "${KEY_ALIAS}" \
        -keyalg RSA \
        -keysize 4096 \
        -sigalg SHA256withRSA \
        -validity 10950 \
        -dname "CN=Fenix Mobile Core, OU=Engineering, O=Fenix Open Source, L=Montevideo, ST=Montevideo, C=UY" \
        -storepass "${STORE_PASS}"
    echo "[✓] Keystore generado exitosamente en: ${KEYSTORE_PATH}"
else
    echo "[i] Keystore existente detectado en: ${KEYSTORE_PATH}"
fi

# 2. Verificar argumento de APK de entrada
INPUT_APK="${1:-}"
if [ -z "${INPUT_APK}" ]; then
    # Buscar el APK recién compilado por defecto
    INPUT_APK=$(find "${PROJECT_DIR}/app/build/outputs/apk" -name "*.apk" 2>/dev/null | grep -v "unaligned" | head -n 1 || true)
fi

if [ -z "${INPUT_APK}" ] || [ ! -f "${INPUT_APK}" ]; then
    echo "[-] Error: No se especificó ni se encontró un APK para firmar."
    echo "Uso: $0 [ruta/al/archivo.apk]"
    exit 1
fi

echo "[+] Procesando APK: ${INPUT_APK}"
DIRNAME=$(dirname "${INPUT_APK}")
BASENAME=$(basename "${INPUT_APK}" .apk)

ALIGNED_APK="${DIRNAME}/${BASENAME}-aligned.apk"
SIGNED_APK="${DIRNAME}/${BASENAME}-signed-release.apk"

# 3. Alinear a 4 bytes con zipalign
echo "[+] Alineando APK a 4 bytes con zipalign..."
"${ZIPALIGN}" -f -v -p 4 "${INPUT_APK}" "${ALIGNED_APK}" > /dev/null

# 4. Firmar con esquemas v1, v2 y v3 habilitados
echo "[+] Firmando con apksigner (v1, v2, v3 activos)..."
"${APKSIGNER}" sign \
    --ks "${KEYSTORE_PATH}" \
    --ks-key-alias "${KEY_ALIAS}" \
    --ks-pass pass:"${STORE_PASS}" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --v4-signing-enabled false \
    --min-sdk-version 26 \
    --out "${SIGNED_APK}" \
    "${ALIGNED_APK}"

# 5. Auditar y verificar la firma
echo "[+] Verificando firmas en el binario final..."
"${APKSIGNER}" verify --verbose --print-certs "${SIGNED_APK}"

# 6. Mostrar huella SHA-256
SHA256_HASH=$(sha256sum "${SIGNED_APK}" | awk '{print $1}')
echo ""
echo "=================================================================="
echo " [✓] APK RELEASE FIRMADO CON ÉXITO:"
echo " Ruta: ${SIGNED_APK}"
echo " SHA-256: ${SHA256_HASH}"
echo "=================================================================="

