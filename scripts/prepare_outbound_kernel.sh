#!/bin/sh
# Build pinned App-owned source. No business IPs, ECH keys or unsigned downloads.
set -eu
OUTBOUND_PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTBOUND_DIR="$OUTBOUND_PROJECT_DIR/runtime/outbound"
OUTBOUND_NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
if [ -z "$OUTBOUND_NDK" ] && [ -f "$OUTBOUND_PROJECT_DIR/local.properties" ]; then
  OUTBOUND_NDK=$(sed -n 's/^ndk.dir=//p' "$OUTBOUND_PROJECT_DIR/local.properties")
fi
[ -d "$OUTBOUND_NDK/toolchains/llvm/prebuilt" ] || { echo '请设置 ANDROID_NDK_HOME（推荐 NDK r28+）' >&2; exit 1; }
case "$(uname -s)/$(uname -m)" in
  Linux/aarch64) OUTBOUND_HOST=linux-aarch64 ;;
  Linux/*) OUTBOUND_HOST=linux-x86_64 ;;
  Darwin/*) OUTBOUND_HOST=darwin-x86_64 ;;
  *) echo '不支持的 NDK 构建宿主' >&2; exit 1 ;;
esac
OUTBOUND_BIN="$OUTBOUND_NDK/toolchains/llvm/prebuilt/$OUTBOUND_HOST/bin"
[ -d "$OUTBOUND_BIN" ] || OUTBOUND_BIN="$OUTBOUND_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ "$#" -eq 0 ]; then set -- arm64-v8a armeabi-v7a x86_64; fi
for OUTBOUND_ABI do
  case "$OUTBOUND_ABI" in
    arm64-v8a) OUTBOUND_ARCH=arm64; OUTBOUND_CC=aarch64-linux-android24; OUTBOUND_ARM=7 ;;
    armeabi-v7a) OUTBOUND_ARCH=arm; OUTBOUND_CC=armv7a-linux-androideabi24; OUTBOUND_ARM=7 ;;
    x86_64) OUTBOUND_ARCH=amd64; OUTBOUND_CC=x86_64-linux-android24; OUTBOUND_ARM=7 ;;
    *) echo "不支持的 ABI: $OUTBOUND_ABI" >&2; exit 1 ;;
  esac
  mkdir -p "$OUTBOUND_DIR/$OUTBOUND_ABI"
  (
    cd "$OUTBOUND_PROJECT_DIR"
    for OUTBOUND_INPUT in scripts/prepare_outbound_kernel.sh runtime/outbound/src/*.go runtime/outbound/src/go.mod runtime/outbound/src/go.sum; do
      sha256sum "$OUTBOUND_INPUT"
    done
  ) > "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.inputs.pending"
  (
    cd "$OUTBOUND_DIR/src"
    GOOS=android GOARCH="$OUTBOUND_ARCH" GOARM="$OUTBOUND_ARM" CGO_ENABLED=1 CGO_LDFLAGS='-llog' CC="$OUTBOUND_BIN/clang --target=$OUTBOUND_CC" \
      go build -trimpath -buildmode=pie -ldflags='-s -w -linkmode=external -extldflags=-Wl,-z,max-page-size=16384' \
      -o "$OUTBOUND_DIR/$OUTBOUND_ABI/libdanmu_outbound.so.pending" .
  )
  (
    cd "$OUTBOUND_PROJECT_DIR"
    sha256sum --check "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.inputs.pending" > /dev/null
  ) || { echo '构建过程中源码发生变化，请重新构建' >&2; exit 1; }
  chmod 755 "$OUTBOUND_DIR/$OUTBOUND_ABI/libdanmu_outbound.so.pending"
  mv "$OUTBOUND_DIR/$OUTBOUND_ABI/libdanmu_outbound.so.pending" "$OUTBOUND_DIR/$OUTBOUND_ABI/libdanmu_outbound.so"
  sha256sum "$OUTBOUND_DIR/$OUTBOUND_ABI/libdanmu_outbound.so" | cut -d' ' -f1 > "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.binary.pending"
  mv "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.inputs.pending" "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.inputs"
  mv "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.binary.pending" "$OUTBOUND_DIR/$OUTBOUND_ABI/BUILD.binary"
  echo "$OUTBOUND_ABI: 已构建 App 增强直连组件"
done
