# Sample asset telemetry and automatic charging

The map and vehicle screens use persisted telemetry returned by
`GET /api/workspace/vehicles`. Sign in with a user that has `vehicle.read` and
`map.read`. The telemetry sender also needs `vehicle.manage`.

## Display an asset

Replace the timestamp and credentials, then submit this payload:

```bash
ASSET_ID=9f000000-0000-0000-0000-000000000001
curl --fail-with-body -u admin.demo:DemoOnly\!2026 \
  -H 'Content-Type: application/json' \
  -X POST "http://localhost:8080/api/telemetry/assets/${ASSET_ID}/position" \
  -d "{
    \"messageId\": \"demo-position-$(date +%s)\",
    \"schemaVersion\": \"1.0\",
    \"correlationId\": \"manual-map-demo\",
    \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"fleetNumber\": \"EV-DEMO-01\",
    \"assetType\": \"ITV\",
    \"plantCode\": \"JEA\",
    \"latitude\": 24.995,
    \"longitude\": 55.04,
    \"heading\": 90,
    \"speedKph\": 0,
    \"energyPercent\": 65,
    \"energySource\": \"ELECTRIC\",
    \"operationalStatus\": \"IDLE\",
    \"availabilityStatus\": \"AVAILABLE\",
    \"deviceId\": \"demo-ev-device-01\",
    \"trackItId\": \"demo-ev-track-01\"
  }"
```

Open **Map** or **Vehicles**, or verify the API directly:

```bash
curl --fail-with-body -u admin.demo:DemoOnly\!2026 \
  http://localhost:8080/api/workspace/vehicles
```

Every new reading must have a unique `messageId` and a newer `occurredAt` value.

## Trigger automatic charging

Send the same asset again with a new message ID/time and these values:

```json
{
  "energyPercent": 12,
  "energySource": "ELECTRIC",
  "operationalStatus": "IDLE",
  "availabilityStatus": "AVAILABLE"
}
```

Use the complete payload above; the fragment only highlights the trigger fields.
At or below `CHARGING_THRESHOLD_PERCENT` (default `20`), the accepted telemetry
response contains `automaticChargingJobId`. In one database transaction the API:

1. selects and reserves an available charging bay;
2. creates and assigns one `CHARGING` job to the asset;
3. records the assignment, job events, and a 15-minute bay reservation; and
4. marks the asset `RESERVED` with `current_job_id` set.

The automation intentionally does not interrupt a working/assigned asset, does
not charge combustion assets, and does not create a duplicate active job. If no
bay is available it raises one unacknowledged `NO_CHARGING_BAY` alert. Flyway V9
provides one local demonstration charging bay. Run the application normally so
Flyway applies V9; do not execute the migration file manually.

This creates and assigns the operational charging job. Physical vehicle command
delivery still requires the site's routing/vehicle-control integration.
