# RabbitMQ vehicle telemetry

Vehicle position telemetry is consumed from durable quorum queue
`dpwfms.telemetry.position.q`, bound to topic exchange `dpwfms.business.v1` with
routing key `telemetry.asset.position.v1`. Rejected messages are retried three
times and dead-lettered to `dpwfms.integration.dlq`; they are not endlessly
requeued.

The JSON body is an envelope containing the stable asset identifier and the
canonical telemetry record:

```json
{
  "assetId": "9f000000-0000-0000-0000-000000000001",
  "telemetry": {
    "messageId": "device-01-1725192000",
    "schemaVersion": "1.0",
    "correlationId": "gateway-batch-42",
    "occurredAt": "2026-09-01T12:00:00Z",
    "fleetNumber": "EV-001",
    "assetType": "ITV",
    "plantCode": "JEA",
    "latitude": 24.995,
    "longitude": 55.04,
    "heading": 90,
    "speedKph": 0,
    "energyPercent": 12,
    "energySource": "ELECTRIC",
    "operationalStatus": "IDLE",
    "availabilityStatus": "AVAILABLE",
    "deviceId": "device-01",
    "trackItId": "track-01"
  }
}
```

## Optional TrackIT queue bridge

The API includes an optional Java bridge for raw TrackIT PascalCase objects or
arrays. It consumes a source queue, adds the target vehicle/equipment/fuel
classification and normalized location fields, and publishes the converted
payload to the business exchange. The existing telemetry listener recognizes
these TrackIT records and stores them through TrackIT ingestion. Existing
canonical envelopes continue to use the original ingestion path.

The bridge is disabled by default. Configure the RabbitMQ connection using the
standard `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, and
`RABBITMQ_PASSWORD` settings, then enable/configure the bridge before starting
the API. For PowerShell:

```powershell
$env:RABBITMQ_HOST = "172.20.14.13"
$env:RABBITMQ_PORT = "5671"
$env:RABBITMQ_USER = "admin"
$env:RABBITMQ_PASSWORD = "<your-password>"
$env:TRACKIT_TELEMETRY_RABBIT_BRIDGE_ENABLED = "true"
$env:TRACKIT_TELEMETRY_RABBIT_INPUT_QUEUE = "tenant51.telemetry"
$env:TRACKIT_TELEMETRY_RABBIT_OUTPUT_EXCHANGE = "dpwfms.business.v1"
$env:TRACKIT_TELEMETRY_RABBIT_OUTPUT_ROUTING_KEY = "telemetry.asset.position.v1"
$env:TRACKIT_DEFAULT_VEHICLE_TYPE = "FUEL"
$env:TRACKIT_DEFAULT_EQUIPMENT_TYPE = "TRACTOR"
$env:TRACKIT_DEFAULT_FUEL_TYPE = "DIESEL"
java -jar fms-api/target/fms-api.jar
```

The source queue must exist and the RabbitMQ user needs consume permission on
that queue plus publish permission on the output exchange. The classification
defaults and the location aliases (Operation/Engineering T1, T2, and T3) are
configurable or should be adjusted to match site master data. The bridge
preserves source measurements and availability flags and leaves missing
derived fields null.

Start the API first so it declares the topology. Then simulate all operational
scenarios through RabbitMQ (no direct telemetry REST calls):

```bash
export RABBITMQ_USER=dpwfms_app
export RABBITMQ_PASSWORD='your-rabbit-password'
python3 scripts/simulate-rabbit-telemetry.py --cycles 3 --interval 5
```

Use `--cycles 0` for continuous movement. The utility emits moving, idle,
healthy electric, low-battery electric, busy low-battery, low diesel, parked,
faulted, offline and stale examples. `DEMO-LOW-EV` exercises automatic charging;
the busy electric and diesel examples verify the automation safety gates.

Open **Map** to search/filter live markers and inspect their popups. Open
**Reports** for fleet status, stale/low-energy counts, energy mix, manual refresh,
and CSV export. RabbitMQ management must be reachable at port `15672` (the
provided Compose file exposes it only on localhost).
