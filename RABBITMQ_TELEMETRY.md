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
