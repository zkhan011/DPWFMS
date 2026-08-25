# DPW FMS installation

## Requirements

* Java 21 (exactly; scripts reject other major versions)
* Maven 3.9+
* Node.js 20+ and npm
* PostgreSQL 16+ with `psql` and `pg_isready` on `PATH`
* Redis 7 and RabbitMQ 4 reachable from the backend

Create an empty database and least-privileged owner:

```sql
CREATE ROLE dpwfms_app LOGIN PASSWORD '<secret>';
CREATE DATABASE dpw_fms OWNER dpwfms_app ENCODING 'UTF8';
```

Copy `.env.example` to `.env`, replace every `change-me` value, and never commit `.env`. Flyway applies migrations automatically during backend startup. Do not run migrations from multiple release jobs simultaneously.

Linux: `./scripts/setup-linux.sh`. Windows: `Set-ExecutionPolicy -Scope Process Bypass; .\scripts\setup-windows.ps1`. See [direct execution](RUN_WITHOUT_DOCKER.md) and [deployment](DEPLOYMENT.md).
