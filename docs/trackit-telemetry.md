# TrackIT fleet telemetry integration

## Repository analysis and extension points

The existing repository is a Java 21 Maven reactor. `fms-domain` owns asset concepts,
`fms-api` is the Spring Boot/JDBC composition root, `fms-persistence` owns Flyway migrations,
`fms-routing` separates physical GPS from logical road matches, and the MQTT/RabbitMQ modules
provide existing integration adapters. The legacy position DTO and ingestion path remain in
`TelemetryController` and `TelemetryIngestionService`; physical history remains in
`asset_positions`, last-known operational position remains on `assets`, and the workspace
dashboard/map APIs remain in `WorkspaceController`. The React/Vite application uses its existing
API client, KPI/status components, hash routes, shell navigation and Leaflet provider.

There was no WebSocket or SSE telemetry stream, so this extension adds SSE. It modifies
`SecurityConfig` for two scoped permissions, application/environment configuration, migration
coverage, React routing/navigation, the existing map provider/page, and shared styling. New files
contain the PascalCase boundary DTO, transactional TrackIT ingestion/query endpoints, retention,
SSE publication, Fleet Telemetry workspace, sanitized fixture, and this runbook. Existing
telemetry endpoint contracts and RabbitMQ consumers are unchanged.

## Architecture and compatibility

TrackIT ingestion extends the existing Spring Boot/JDBC/Flyway composition root. It does not
replace `/api/telemetry/assets/{assetId}/position`, RabbitMQ telemetry, `asset_positions`, or
physical/logical routing positions. `Timestamp` is the immutable measurement time;
`LastUpdatedTime` is retained as the upstream update time; `receivedAt` uses the FMS UTC clock.
Map matching remains a logical projection and is never a dispatch acknowledgement.

Accepted records are written to immutable `trackit_telemetry_history`, while
`trackit_telemetry_latest` is changed only by a strictly newer measurement. `AssetID` is the
TrackIT natural key and `(AssetID, Timestamp)` is unique. A later GPS-unavailable event keeps the
last valid map position but marks it unavailable and exposes its age.

## Endpoints and authentication

All endpoints use the existing HTTP Basic/service authentication and Spring permissions:

* `POST /api/v1/integrations/trackit/telemetry` — one object; `trackit.telemetry.ingest`.
* `POST /api/v1/integrations/trackit/telemetry/batch` — array; `trackit.telemetry.ingest`.
* `GET .../summary`, `GET ...`, `GET .../{AssetID}`, `GET .../{AssetID}/history`,
  `GET .../can/parameter-names`, and `GET .../map/positions` — `trackit.telemetry.read`.
* `GET .../events` — authenticated SSE, emitted only after the item transaction commits.

Example:

```bash
curl -u "$TRACKIT_USER:$TRACKIT_PASSWORD" \
  -H 'Content-Type: application/json' \
  --data-binary @docs/examples/trackit-telemetry-batch.json \
  http://localhost:8080/api/v1/integrations/trackit/telemetry/batch
```

A syntactically valid array returns HTTP 200 with per-index `ACCEPTED`, `REJECTED`, `DUPLICATE`,
or `OUT_OF_ORDER` results. Invalid JSON and non-array batch payloads return HTTP 400. Rejected
items do not roll back valid siblings. Error entries contain the batch index and available
`AssetID`, without logging or returning credentials or a full production payload.

## Availability, validation, and units

Latitude/longitude must be WGS84 and `0,0` is rejected. Speed, voltage, fuel fields, engine hours,
and CAN odometer values cannot be negative. Signed protocol values such as `Current Gear=-1` are
preserved. If an availability flag is false, related normalized values are `null`; source values
remain only in the immutable raw audit payload. Missing values are shown as **Not available**, not
zero. Unknown CAN names and JSON values are retained dynamically.

The upstream contract must confirm units. `Speed` is configured/displayed as km/h and battery as
V. Cumulative fuel/CAN counters are stored without conversion. `FuelConsumptionRate` has no unit
label unless `TRACKIT_FUEL_RATE_UNIT` is explicitly configured. CAN display units, ordering and
warning ranges come from `TRACKIT_CAN_METADATA_JSON`, for example:

```json
{"Coolant Temperature":{"label":"Coolant temperature","unit":"°C","warningMax":105,"order":10}}
```

The number above is only an example of configuration syntax, not an operational recommendation.
Site engineering must approve actual thresholds. Unknown parameters remain visible with
“Source unit not defined”. Alerts are display warnings only and do not create jobs.

## Configuration and retention

* `TRACKIT_TELEMETRY_AUTO_CREATE_ASSETS=false`: reject unknown IDs with `ASSET_NOT_REGISTERED`.
  If enabled, creates a minimal `TRACKIT` asset and never overwrites manually maintained fields.
* `TRACKIT_TELEMETRY_STALE_SECONDS=120` and `TRACKIT_TELEMETRY_OFFLINE_SECONDS=600`: freshness
  based on measurement time. Offline must exceed stale.
* `TRACKIT_TELEMETRY_RETENTION_DAYS=90` and `TRACKIT_TELEMETRY_RETENTION_CRON`: immutable history
  cleanup. The row referenced by last-known state is never deleted.
* `TRACKIT_ALERT_LOW_BATTERY_VOLTAGE`: optional; blank until the site approves a threshold.
* `TRACKIT_CAN_METADATA_JSON={}`: display labels, units, order, and optional warningMin/warningMax.

The Fleet Telemetry workspace provides summary cards, server-side filters/sorting/pagination,
measurement/source/received timestamps, latency, reliable-value handling, warnings, and a dynamic
CAN detail drawer. The live map consumes retained valid TrackIT positions without recreating its
map instance and labels GPS-unavailable retained positions.
