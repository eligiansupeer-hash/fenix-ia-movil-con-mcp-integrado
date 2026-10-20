#!/usr/bin/env bash
#
# build-proot-fenix.sh — Compila proot + talloc para Fénix Móvil MCP (arm64-v8a)
#
# Basado en el script de referencia de SimonSchubert/Kai (Apache-2.0) y en la
# decisión arquitectónica de docs/ARQUITECTURA_LINUX_AGENTES.md.
#
# Requisitos: NDK r29, git, curl, python3, make, tar.
# Salida: app/src/main/jniLibs/arm64-v8a/{libproot.so,libproot-loader.so,libtalloc.so}
#
# Uso:
#   ANDROID_NDK_HOME=/home/eli/android-dev/ndk/29.0.14206865 \
#     bash scripts/build-proot-fenix.sh [--clean]

set -euo pipefail

PROOT_REPO="https://github.com/termux/proot.git"
PROOT_COMMIT="4dba3afbf3a63af89b4d9c1a59bf2bda10f4d10f"
TALLOC_VERSION="2.4.3"
TALLOC_URL="https://www.samba.org/ftp/talloc/talloc-${TALLOC_VERSION}.tar.gz"
# v0.114 es la última revisión cuyos módulos terminal-view/terminal-emulator
# siguen bajo Apache-2.0 (excepción declarada en LICENSE.md). Desde v0.115+
# el repositorio unificó todo bajo GPLv3.
TERMUX_TAG="v0.114"
TERMUX_URL="https://github.com/termux/termux-app/archive/refs/tags/${TERMUX_TAG}.tar.gz"
MIN_API=26
ABI="arm64-v8a"
TRIPLE="aarch64-linux-android"
NDK_MAJOR=29

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_DIR="${PROJECT_DIR}/.build-native"
OUTPUT_DIR="${PROJECT_DIR}/app/src/main/jniLibs/${ABI}"
# 16 KB page size (Android 15/16). 2**14 = 16384.
PAGE_SIZE_FLAG="-Wl,-z,max-page-size=16384"

if [[ "${1:-}" == "--clean" ]]; then
    echo "Limpiando ${BUILD_DIR}..."
    rm -rf "${BUILD_DIR}"
    echo "Listo."
    exit 0
fi

find_ndk() {
    for var in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" "${ANDROID_NDK:-}"; do
        [[ -n "$var" && -d "$var" ]] && { echo "$var"; return; }
    done
    local preferred
    preferred=$(ls -d /home/eli/android-dev/ndk/${NDK_MAJOR}.* 2>/dev/null | sort -V | tail -1) || true
    [[ -n "${preferred:-}" ]] && { echo "$preferred"; return; }
    echo "ERROR: no se encontró NDK r${NDK_MAJOR}. Instalalo con sdkmanager o seteá ANDROID_NDK_HOME." >&2
    exit 1
}

NDK="$(find_ndk)"
echo "NDK: $NDK"

HOST_TAG=""
for tag in "$(uname -s | tr 'A-Z' 'a-z')-$(uname -m)" "linux-x86_64" "darwin-x86_64"; do
    if [[ -d "$NDK/toolchains/llvm/prebuilt/$tag" ]]; then
        HOST_TAG="$tag"; break
    fi
done
[[ -z "$HOST_TAG" ]] && { echo "ERROR: no se pudo determinar el host del NDK" >&2; exit 1; }

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
CC="${TOOLCHAIN}/${TRIPLE}${MIN_API}-clang"
JOBS="$(nproc 2>/dev/null || echo 4)"
echo "Host: $HOST_TAG | jobs: $JOBS | API: $MIN_API | ABI: $ABI"

mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

# ── Fuentes ────────────────────────────────────────────────────────────────────
if [[ ! -d "$BUILD_DIR/proot/.git" ]]; then
    echo "Clonando proot..."
    git clone --quiet --filter=blob:none --no-checkout "$PROOT_REPO" "$BUILD_DIR/proot"
fi
git -C "$BUILD_DIR/proot" fetch -q --depth 1 origin "$PROOT_COMMIT"
git -C "$BUILD_DIR/proot" checkout -q FETCH_HEAD
echo "proot: $(git -C "$BUILD_DIR/proot" log -1 --format='%H %s')"

if [[ ! -d "$BUILD_DIR/talloc-${TALLOC_VERSION}" ]]; then
    echo "Descargando talloc ${TALLOC_VERSION}..."
    curl -fsSL "$TALLOC_URL" -o "$BUILD_DIR/talloc.tar.gz"
    tar xzf "$BUILD_DIR/talloc.tar.gz" -C "$BUILD_DIR"
    rm -f "$BUILD_DIR/talloc.tar.gz"
fi

SYSROOT="$BUILD_DIR/sysroot/$ABI"
mkdir -p "$SYSROOT"

# ── talloc ─────────────────────────────────────────────────────────────────────
if [[ ! -f "$SYSROOT/lib/libtalloc.so" ]]; then
    echo "[talloc] compilando..."
    BD="$BUILD_DIR/build-talloc-$ABI"
    rm -rf "$BD"
    cp -r "$BUILD_DIR/talloc-${TALLOC_VERSION}" "$BD"

    cat > "$BD/cross-answers.txt" << 'EOF'
Checking uname sysname type: "Linux"
Checking uname machine type: "dontcare"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
building library support: OK
Checking for large file support: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for WORDS_BIGENDIAN: OK
Checking for C99 vsnprintf: OK
Checking for HAVE_SECURE_MKSTEMP: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking correct behavior of strtoll: OK
Checking correct behavior of strptime: OK
Checking for HAVE_IFACE_GETIFADDRS: OK
Checking for HAVE_IFACE_IFCONF: OK
Checking for HAVE_IFACE_IFREQ: OK
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for working strptime: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking getconf large file support flags work: OK
EOF

    (
        cd "$BD"
        CC="$CC" \
        CFLAGS="-O2 -fPIC -ffile-prefix-map=${PROJECT_DIR}=. ${PAGE_SIZE_FLAG}" \
        LDFLAGS="${PAGE_SIZE_FLAG}" \
        AR="${TOOLCHAIN}/llvm-ar" \
        RANLIB="${TOOLCHAIN}/llvm-ranlib" \
        STRIP="${TOOLCHAIN}/llvm-strip" \
        ./configure \
            --prefix="$SYSROOT" \
            --disable-rpath \
            --disable-python \
            --cross-compile \
            --cross-answers="$BD/cross-answers.txt" > "$BD/configure.log" 2>&1
        make -j"$JOBS" > "$BD/make.log" 2>&1
        make install > "$BD/install.log" 2>&1
    )
    echo "[talloc] OK -> $SYSROOT/lib/libtalloc.so"
else
    echo "[talloc] ya compilado, salteando"
fi

# ── proot ──────────────────────────────────────────────────────────────────────
echo "[proot] compilando..."
BD="$BUILD_DIR/build-proot-$ABI"
rm -rf "$BD"
cp -r "$BUILD_DIR/proot" "$BD"

# El loader de 32 bits no se usa: la app y el rootfs son arm64.
sed -i.bak '/#define HAS_LOADER_32BIT/d' "$BD/src/arch.h"

# mawk (default en Debian) no implementa strtonum(); se reemplaza por una
# conversión hexadecimal portable para que el build no dependa de gawk.
if ! awk 'BEGIN { strtonum("0x1") }' >/dev/null 2>&1; then
    sed -i '1i function h2d(s,  i,c,n){sub(/^0x/,"",s);n=0;for(i=1;i<=length(s);i++){c=index("0123456789abcdef",tolower(substr(s,i,1)))-1;n=n*16+c}return n}' \
        "$BD/src/loader/loader-info.awk"
    sed -i 's/strtonum(/h2d(/g' "$BD/src/loader/loader-info.awk"
fi

mkdir -p "$BD/loader-out"
mkdir -p "$BUILD_DIR/bin"
ln -sf "${TOOLCHAIN}/llvm-readelf" "$BUILD_DIR/bin/readelf"

(
    cd "$BD"
    export CC="$CC"
    export LD="$CC"
    export STRIP="${TOOLCHAIN}/llvm-strip"
    export OBJCOPY="${TOOLCHAIN}/llvm-objcopy"
    export OBJDUMP="${TOOLCHAIN}/llvm-objdump"
    export CFLAGS="-DARG_MAX=131072 -O2 -ffile-prefix-map=${PROJECT_DIR}=. -I${SYSROOT}/include -Wno-error=implicit-function-declaration -Wno-error=int-conversion"
    export LDFLAGS="-L${SYSROOT}/lib ${PAGE_SIZE_FLAG}"
    export PATH="$BUILD_DIR/bin:$PATH"
    make -C src PROOT_UNBUNDLE_LOADER="loader-out" -j"$JOBS" > "$BUILD_DIR/build-proot.log" 2>&1
)

cp "$BD/src/proot" "$OUTPUT_DIR/libproot.so"
cp "$BD/src/loader/loader" "$OUTPUT_DIR/libproot-loader.so"
cp "$SYSROOT/lib/libtalloc.so" "$OUTPUT_DIR/libtalloc.so"

# ── libtermux (PTY / JNI de terminal-emulator) ─────────────────────────────────
echo "[libtermux] compilando..."
TERMUX_SRC="$BUILD_DIR/termux-app-${TERMUX_TAG#v}"
if [[ ! -d "$TERMUX_SRC" ]]; then
    curl -fsSL "$TERMUX_URL" -o "$BUILD_DIR/termux-app.tar.gz"
    tar xzf "$BUILD_DIR/termux-app.tar.gz" -C "$BUILD_DIR"
    rm -f "$BUILD_DIR/termux-app.tar.gz"
fi
"$CC" -shared -fPIC -O2 -Wall \
    -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -o "$OUTPUT_DIR/libtermux.so" \
    "$TERMUX_SRC/terminal-emulator/src/main/jni/termux.c"

# El linker de Android no soporta RPATH y el SONAME de talloc es libtalloc.so.2,
# pero jniLibs solo puede empaquetar libtalloc.so. Acortamos el nombre en .dynstr
# conservando el tamaño (padding NUL) para que DT_NEEDED/SONAME apunten a
# "libtalloc.so" sin romper offsets del ELF.
python3 - "$OUTPUT_DIR/libproot.so" "$OUTPUT_DIR/libtalloc.so" <<'PY'
import sys
OLD = b"libtalloc.so.2\x00"
NEW = b"libtalloc.so\x00\x00\x00"
assert len(OLD) == len(NEW)
for path in sys.argv[1:]:
    data = open(path, "rb").read()
    if OLD in data:
        open(path, "wb").write(data.replace(OLD, NEW))
PY

for lib in "$OUTPUT_DIR/libproot.so" "$OUTPUT_DIR/libproot-loader.so" "$OUTPUT_DIR/libtalloc.so" "$OUTPUT_DIR/libtermux.so"; do
    "${TOOLCHAIN}/llvm-strip" "$lib"
    "${TOOLCHAIN}/llvm-objcopy" --remove-section .comment "$lib" 2>/dev/null || true
done

echo
echo "Binarios generados:"
ls -lh "$OUTPUT_DIR"/libproot.so "$OUTPUT_DIR"/libproot-loader.so "$OUTPUT_DIR"/libtalloc.so "$OUTPUT_DIR"/libtermux.so
echo
echo "Verificación de alineación (LOAD align 2**14 = 16 KB):"
for lib in "$OUTPUT_DIR/libproot.so" "$OUTPUT_DIR/libproot-loader.so" "$OUTPUT_DIR/libtalloc.so" "$OUTPUT_DIR/libtermux.so"; do
    echo "--- $lib"
    "${TOOLCHAIN}/llvm-objdump" -p "$lib" | grep -E '^    LOAD' || true
done
