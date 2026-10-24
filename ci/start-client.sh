#!/usr/bin/env bash
set -Eeuo pipefail

: "${PRISM_INSTANCE_ID:?PRISM_INSTANCE_ID is required}"
: "${PRISM_PROFILE:?PRISM_PROFILE is required}"

mkdir -p /tmp/runtime-root /prism
chmod 700 /tmp/runtime-root
export XDG_RUNTIME_DIR=/tmp/runtime-root

Xvfb :99 -screen 0 1280x720x24 -nolisten tcp >/tmp/xvfb.log 2>&1 &
for _ in $(seq 1 30); do
  [[ -S /tmp/.X11-unix/X99 ]] && break
  sleep 1
done
[[ -S /tmp/.X11-unix/X99 ]] || { cat /tmp/xvfb.log; exit 1; }

exec /opt/prism/squashfs-root/AppRun \
  --dir /prism \
  --launch "$PRISM_INSTANCE_ID" \
  --profile "$PRISM_PROFILE"
