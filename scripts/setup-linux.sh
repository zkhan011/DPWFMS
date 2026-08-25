#!/usr/bin/env bash
source "$(cd "$(dirname "$0")" && pwd)/lib-linux.sh"
require java; require mvn; require node; require npm; require psql; require pg_isready
check_java; check_node 20; load_env; check_db
echo "Installing locked frontend dependencies..."; (cd "$ROOT/fms-web" && npm install)
echo "Building Java reactor..."; (cd "$ROOT" && ./mvnw -B clean verify)
echo "Building frontend..."; (cd "$ROOT/fms-web" && npm run build)
echo "Setup complete. Run ./scripts/start-linux.sh or ./scripts/dev-linux.sh"
