# Run without Docker

## Linux development

```bash
cp .env.example .env
# edit .env, then create/start PostgreSQL, Redis and RabbitMQ
./scripts/setup-linux.sh
./scripts/dev-linux.sh
```

Logs are written under `logs/`; PID files are under `runtime/`. Stop with `./scripts/stop-linux.sh`. Production-style backend startup is `./scripts/start-linux.sh`.

## Windows PowerShell development

```powershell
Set-ExecutionPolicy -Scope Process Bypass
Copy-Item .env.example .env
# edit .env, then create/start PostgreSQL, Redis and RabbitMQ
.\scripts\setup-windows.ps1
.\scripts\dev-windows.ps1
```

Stop with `.\scripts\stop-windows.ps1`; production-style backend startup is `.\scripts\start-windows.ps1`.

## Manual commands

```bash
set -a; source .env; set +a
./mvnw -pl fms-api -am spring-boot:run -Dspring-boot.run.profiles=dev
cd fms-web && cp .env.example .env.local && npm install && npm run dev
```

The Vite development URL is `http://localhost:5173`; API and Swagger are `http://localhost:8080` and `http://localhost:8080/swagger-ui.html`. A production frontend can be served using `npm run preview -- --host 0.0.0.0 --port 3000` or the included Nginx container.
