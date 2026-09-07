# Testing DPW FMS with mock data

Mock data is never loaded in the `prod` profile. These steps exercise Flyway, database authentication, permissions, telemetry ingestion, the Overview counters, Vehicle Monitoring, and live map rendering.

## 1. Configure local services

Copy `.env.example` to `.env`, replace every `change-me` value, and set:

```dotenv
SPRING_PROFILES_ACTIVE=dev
DPWFMS_SAMPLE_USERS_ENABLED=true
DPWFMS_SAMPLE_USER_PASSWORD=a-local-test-password-of-at-least-12-characters
MAP_PROVIDER=osm
```

Create PostgreSQL and the application user as described in `INSTALLATION.md`. Redis and RabbitMQ must also match `.env` when starting the full API.

## 2. Apply Flyway and start the application

```bash
set -a; source .env; set +a
./mvnw -pl fms-api -am spring-boot:run
```

Flyway applies V1 through V7 automatically. V7 inserts four disabled, passwordless fixed users. In the `dev` profile, the explicitly enabled seeder hashes `DPWFMS_SAMPLE_USER_PASSWORD`, enables those users, assigns `SUPER_ADMIN`, and assigns every enabled plant.

Confirm the API:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -u "admin.demo:$DPWFMS_SAMPLE_USER_PASSWORD" http://localhost:8080/api/workspace/me
```

## 3. Load deterministic telemetry

In a second terminal:

```bash
set -a; source .env; set +a
export DPWFMS_LOCAL_USERNAME=admin.demo
export DPWFMS_LOCAL_PASSWORD="$DPWFMS_SAMPLE_USER_PASSWORD"
export DPWFMS_MOCK_VEHICLE_COUNT=12
./scripts/load-mock-telemetry.sh
```

The loader sends current, valid telemetry through the real secured ingestion API. Re-running it produces newer messages for the same deterministic asset IDs rather than creating additional assets. Set the count as high as 1,500 for browser/load checks.

## 4. Start and verify the frontend

```bash
cd fms-web
npm ci
npm run dev
```

Open <http://localhost:5173> and sign in as `admin.demo` with `DPWFMS_SAMPLE_USER_PASSWORD`.

Verify:

1. **Overview** reports the loaded fleet and working/idle totals from PostgreSQL.
2. **Vehicles** lists `MOCK-ITV-001` onward with current coordinates and telemetry time.
3. **Map** loads the selected tiles and shows green vehicle markers. Wait longer than the configured freshness threshold to verify stale markers turn orange.
4. **Administration → Users & access** lists the four fixed users.
5. **Administration → Map provider** can switch between `osm` and `offline`; save, reopen Map, and confirm the provider label.

## 5. Validate duplicate and stale protection

Capture one loader payload and submit it twice with the same `messageId`; the second response must be `DUPLICATE`. Submit a new message ID with an older `occurredAt`; the response must be `OUT_OF_ORDER`, and the map position must not move backwards.

## 6. Automated checks

```bash
./mvnw clean test
./mvnw clean package -DskipTests
cd fms-web && npm run build
```

The Testcontainers migration test requires Docker. Without Docker it is disabled by its existing Testcontainers configuration; unit tests still execute.
