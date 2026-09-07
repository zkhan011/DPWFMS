#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || { echo "Missing .env. Copy .env.example to .env and replace change-me values." >&2; exit 1; }
command -v docker >/dev/null || { echo "Docker is required for the Eclipse infrastructure helper." >&2; exit 1; }
docker compose --env-file .env -f docker-compose.yml -f deploy/docker-compose.eclipse.yml \
  up -d postgres redis rabbitmq mosquitto
echo "Infrastructure ready. In Eclipse run DpwFmsApplication with profiles dev,eclipse."
