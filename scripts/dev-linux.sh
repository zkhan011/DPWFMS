#!/usr/bin/env bash
source "$(cd "$(dirname "$0")" && pwd)/lib-linux.sh"; load_env; require java; require mvn; require node; require npm; check_java; check_node 20; check_db
trap '"$ROOT/scripts/stop-linux.sh"' EXIT INT TERM
(cd "$ROOT" && nohup ./mvnw -pl fms-api -am spring-boot:run -Dspring-boot.run.profiles=dev >"$LOGS/backend-dev.log" 2>&1 & echo $! >"$RUNTIME/backend.pid")
(cd "$ROOT/fms-web" && nohup npm run dev >"$LOGS/frontend-dev.log" 2>&1 & echo $! >"$RUNTIME/frontend.pid")
echo "Development services: frontend http://localhost:5173, API http://localhost:8080"; echo "Press Ctrl+C to stop."; while true; do sleep 60; done
