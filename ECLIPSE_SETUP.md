# Run DPW FMS from Eclipse

This workflow runs the Java API from Eclipse while PostgreSQL, Redis, RabbitMQ, and Mosquitto run as local Docker infrastructure. The API and frontend remain debuggable on the host.

## Prerequisites

Install JDK 21, Eclipse IDE for Enterprise Java and Web Developers (or Eclipse with m2e), Node.js/npm, and Docker Desktop or Docker Engine. In Eclipse, select JDK 21 under **Window → Preferences → Java → Installed JREs**.

## 1. Prepare configuration

From the repository root:

```bash
cp .env.example .env
```

Replace every `change-me` database/RabbitMQ/bootstrap value. Keep `DB_URL=jdbc:postgresql://localhost:5432/dpw_fms`, `REDIS_HOST=localhost`, and `RABBITMQ_HOST=localhost` because Eclipse runs the API on the host.

## 2. Start local infrastructure

Linux/macOS:

```bash
./scripts/start-eclipse-infrastructure.sh
```

Windows PowerShell:

```powershell
.\scripts\start-eclipse-infrastructure.ps1
```

The Eclipse Compose override exposes PostgreSQL only on `127.0.0.1:5432`, Redis on `127.0.0.1:6379`, and RabbitMQ on `127.0.0.1:5672`. It does not start the API or frontend containers.

## 3. Import the Maven reactor

1. Select **File → Import → Maven → Existing Maven Projects**.
2. Select the DPWFMS repository root containing `pom.xml`.
3. Import the root and every module.
4. Right-click the reactor and choose **Maven → Update Project**.
5. Confirm `fms-api` uses Java 21.

## 4. Create the Eclipse run configuration

Import `eclipse/DPW_FMS_API_Dev.launch` using **File → Import → Run/Debug → Launch Configurations**, or create a **Java Application** configuration manually:

| Setting | Value |
|---|---|
| Project | `fms-api` |
| Main class | `com.dpworld.fms.api.DpwFmsApplication` |
| Working directory | repository root |
| Program arguments | `--spring.profiles.active=dev,eclipse --spring.config.import=optional:file:.env[.properties]` |

Run or debug the configuration. Flyway applies all migrations before the API becomes ready.

## 5. Demo login with all permissions

The `eclipse` profile securely activates only Flyway-managed demo records. It uses this known local-only credential unless `DPWFMS_SAMPLE_USER_PASSWORD` is set:

```text
Username: admin.demo
Password: DemoOnly!2026
```

`admin.demo` has the `SUPER_ADMIN` role, all permissions, and every seeded plant. The other fixed demo usernames use the same local password. The seeder is guarded by `dev & !prod`, so this known password cannot activate accounts under the production profile. Override it in `.env` with `DPWFMS_SAMPLE_USER_PASSWORD` whenever possible.

Verify authentication:

```bash
curl -u 'admin.demo:DemoOnly!2026' http://localhost:8080/api/workspace/me
```

## 6. Start the frontend

```bash
cd fms-web
npm ci
npm run dev
```

Open <http://localhost:5173>, sign in with the demo credential, and follow `MOCK_DATA_TESTING.md` to load telemetry vehicles.

## 7. Stop infrastructure

```bash
./scripts/stop-eclipse-infrastructure.sh
```

or on Windows:

```powershell
.\scripts\stop-eclipse-infrastructure.ps1
```
