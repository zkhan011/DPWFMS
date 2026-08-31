#!/usr/bin/env sh
set -eu
if ! command -v mvn >/dev/null 2>&1; then echo "Maven 3.9+ is required. Run scripts/setup-linux.sh for checks." >&2; exit 1; fi
exec mvn "$@"
