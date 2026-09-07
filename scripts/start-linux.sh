#!/usr/bin/env bash
source "$(cd "$(dirname "$0")" && pwd)/lib-linux.sh"; load_env; require java; check_java; check_db
[[ -f "$ROOT/fms-api/target/fms-api-1.0.0-SNAPSHOT.jar" ]] || (cd "$ROOT" && ./mvnw -B -DskipTests package)
[[ ! -f "$RUNTIME/backend.pid" ]] || ! kill -0 "$(cat "$RUNTIME/backend.pid")" 2>/dev/null || { echo "Backend is already running."; exit 1; }
cd "$ROOT"
nohup java -jar fms-api/target/fms-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod >"$LOGS/backend.log" 2>&1 &
echo $! >"$RUNTIME/backend.pid"
if [[ -d "$ROOT/fms-web/dist" ]]; then
  require npm
  (cd "$ROOT/fms-web"; nohup npm run preview -- --host 0.0.0.0 --port "${FRONTEND_PORT:-3000}" >"$LOGS/frontend.log" 2>&1 & echo $! >"$RUNTIME/frontend.pid")
fi
echo "DPW FMS started: frontend http://localhost:${FRONTEND_PORT:-3000}, API http://localhost:${SERVER_PORT:-8080}"
