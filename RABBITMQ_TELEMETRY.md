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

## TrackIT queue bridge

The Java bridge accepts raw TrackIT PascalCase objects or arrays from its input
queue, adds vehicle/equipment/fuel classification and normalized location
fields, then publishes the converted payload to the business exchange. The
existing telemetry listener recognizes these TrackIT records and stores them
through TrackIT ingestion. Canonical envelopes continue to use the original
ingestion path.

Bridge connection and routing settings are in
`fms-api/src/main/resources/application.yml`. Set the RabbitMQ password in
`spring.rabbitmq.password` before running; keep the real password out of
committed changes. The current config uses host `172.20.14.13`, port `5671`
without TLS, user `admin`, source queue `tenant51.telemetry`, exchange
`dpwfms.business.v1`, and routing key `telemetry.asset.position.v1`. The
bridge is enabled in this application configuration.

The source queue must exist and the RabbitMQ user needs consume permission on
that queue plus publish permission on the output exchange. The bridge waits
for the broker's publisher confirmation and checks for unroutable returns before
the source listener acknowledges a message. Vehicle classifications are
configured in the same YAML file. Location aliases for Operation/Engineering
T1, T2, and T3 are converted to Terminal 1, 2, and 3; confirm these mappings
against site master data. Source measurements and availability flags are
preserved, and fields absent from the source are left null.

Start the API first so it declares the internal topology. Then simulate all
operational scenarios through RabbitMQ (no direct telemetry REST calls):

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
