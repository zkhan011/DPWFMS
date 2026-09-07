#!/usr/bin/env bash
source "$(cd "$(dirname "$0")" && pwd)/lib-linux.sh"
for service in backend frontend; do file="$RUNTIME/$service.pid"; if [[ -f "$file" ]]; then pid="$(cat "$file")"; kill "$pid" 2>/dev/null || true; for _ in {1..20}; do kill -0 "$pid" 2>/dev/null || break; sleep .25; done; kill -9 "$pid" 2>/dev/null || true; rm -f "$file"; echo "$service stopped"; fi; done
