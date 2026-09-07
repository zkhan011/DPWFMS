#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose --env-file .env -f docker-compose.yml -f deploy/docker-compose.eclipse.yml \
  stop postgres redis rabbitmq mosquitto
