#!/bin/sh
# 下载官方客户端，校验仓库固定的二进制 SHA-256；不覆盖不匹配的本地资产。
set -eu
FRP_PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
FRP_ASSET_DIR="$FRP_PROJECT_DIR/runtime/frp"
FRP_VERSION=$(cat "$FRP_ASSET_DIR/VERSION")
FRP_DOWNLOAD_BASE=${FRP_DOWNLOAD_BASE:-https://github.com/fatedier/frp/releases/download}
if [ "$#" -eq 0 ]; then set -- arm64-v8a armeabi-v7a x86_64; fi
FRP_STAGE=$(mktemp -d)
trap 'rm -rf "$FRP_STAGE"' EXIT HUP INT TERM
for FRP_ABI do
  case "$FRP_ABI" in
    arm64-v8a) FRP_ARCH=arm64 ;;
    armeabi-v7a) FRP_ARCH=arm ;;
    x86_64) FRP_ARCH=amd64 ;;
    *) echo "不支持的 ABI: $FRP_ABI" >&2; exit 1 ;;
  esac
  FRP_EXPECTED=$(awk -v path="$FRP_ABI/libfrpc.so" '$2 == path { print $1 }' "$FRP_ASSET_DIR/SHA256SUMS")
  [ "${#FRP_EXPECTED}" -eq 64 ] || { echo "缺少 $FRP_ABI 的 SHA-256" >&2; exit 1; }
  FRP_TARGET="$FRP_ASSET_DIR/$FRP_ABI/libfrpc.so"
  if [ -f "$FRP_TARGET" ] && [ "$(sha256sum "$FRP_TARGET" | cut -d' ' -f1)" = "$FRP_EXPECTED" ]; then
    chmod 755 "$FRP_TARGET"
    echo "$FRP_ABI: 已就绪 ($FRP_VERSION)"
    continue
  fi
  FRP_ARCHIVE="frp_${FRP_VERSION}_linux_${FRP_ARCH}"
  curl --fail --location --retry 3 --output "$FRP_STAGE/$FRP_ABI.tar.gz" "$FRP_DOWNLOAD_BASE/v$FRP_VERSION/$FRP_ARCHIVE.tar.gz"
  tar -xOf "$FRP_STAGE/$FRP_ABI.tar.gz" "$FRP_ARCHIVE/frpc" > "$FRP_STAGE/$FRP_ABI"
  FRP_ACTUAL=$(sha256sum "$FRP_STAGE/$FRP_ABI" | cut -d' ' -f1)
  [ "$FRP_ACTUAL" = "$FRP_EXPECTED" ] || { echo "$FRP_ABI: SHA-256 不匹配，未替换内核" >&2; exit 1; }
  mkdir -p "$FRP_ASSET_DIR/$FRP_ABI"
  # 先复制到目标文件系统，再 rename，避免原地覆盖正在执行的文件。
  cp "$FRP_STAGE/$FRP_ABI" "$FRP_TARGET.pending"
  chmod 755 "$FRP_TARGET.pending"
  mv -f "$FRP_TARGET.pending" "$FRP_TARGET"
  echo "$FRP_ABI: 已准备 ($FRP_VERSION)"
done
