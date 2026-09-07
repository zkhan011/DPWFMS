#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="$ROOT/runtime"; LOGS="$ROOT/logs"; mkdir -p "$RUNTIME" "$LOGS"
load_env(){ local file="${1:-$ROOT/.env}"; [[ -f "$file" ]] || { echo "Missing $file. Copy .env.example to .env and set secrets." >&2; exit 1; }; set -a; source "$file"; set +a; }
require(){ command -v "$1" >/dev/null 2>&1 || { echo "Required command '$1' was not found. Install it, then retry." >&2; exit 1; }; }
check_java(){ local major; major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"; [[ "$major" == "21" ]] || { echo "Java 21 is required; found ${major:-unknown}." >&2; exit 1; }; }
check_node(){ local major="${1:-20}" found; found="$(node -p 'process.versions.node.split(`.`)[0]')"; (( found >= major )) || { echo "Node.js $major+ is required; found $found." >&2; exit 1; }; }
check_db(){ PGPASSWORD="$DB_PASSWORD" pg_isready -d "$DB_URL" -U "$DB_USER" >/dev/null || { echo "PostgreSQL is unreachable at DB_URL=$DB_URL." >&2; exit 1; }; }
