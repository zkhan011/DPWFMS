# Integration contracts

All payloads use UTF-8 JSON, `schemaVersion`, globally unique `messageId`, ISO-8601 UTC `occurredAt`, and `correlationId`. Consumers store `(channel, messageId)` before side effects, reject older asset timestamps, retry transient errors exponentially, and route exhausted/invalid messages to the integration DLQ.

## MQTT

Persistent session client ID: `dpwfms-{environment}-{instance}`; subscribe QoS 1 to:

* `dpwfms/assets/+/position`
* `dpwfms/assets/+/telemetry`
* `dpwfms/assets/+/status`
* `dpwfms/assets/+/job-status`
* `dpwfms/assets/+/alerts`

```json
{"schemaVersion":"1.0","messageId":"01JPOSITION01","correlationId":"01JCORRELATION","occurredAt":"2026-08-21T12:00:00Z","assetId":"ITV-0001","latitude":24.9951,"longitude":55.0382,"heading":92.5,"speedKph":27.3}
```

## RabbitMQ

Durable topic exchange `dpwfms.business.v1` routes `asset.position.updated`, `asset.status.updated`, `job.requested`, `job.dispatch.command`, `job.dispatch.acknowledged`, `job.progressed`, `job.completed`, and `alert.raised`. Durable quorum queues use dead-letter exchange `dpwfms.dlx.v1`; `dead.#` enters `dpwfms.integration.dlq`. Retry queues should use environment-specific TTLs of 5s, 30s and 120s before dead-lettering back to business routing keys.

```json
{"schemaVersion":"1.0","messageId":"01JCOMMAND01","correlationId":"01JCORRELATION","occurredAt":"2026-08-21T12:01:00Z","commandId":"e6456674-e554-4d07-88de-24f4349ad38a","jobId":"9f26d892-9389-4760-b770-525084154dc4","assetId":"60951a92-5b9c-43a3-95fc-f761ae2a7b61","destination":"YARD-03","route":{"segments":["R-11","R-18"]}}
```

Never connect browsers to either broker. Sanitized state reaches the UI only over authenticated REST/SSE.

## Persisted telemetry and map display

MQTT/TrackIT adapters and authorized integration clients should submit normalized positions to `POST /api/telemetry/assets/{assetId}/position`. The inbox rejects duplicate `messageId` values and out-of-order timestamps before updating the asset's current position. Accepted points are appended to `asset_positions`; the Map and Vehicles screens read this persisted current state and refresh every five seconds.

```json
{"messageId":"pos-ITV-001-1701","schemaVersion":"1.0","correlationId":"trackit-1701","occurredAt":"2026-08-25T08:30:00Z","fleetNumber":"ITV-001","assetType":"ITV","plantCode":"JEA","latitude":24.9857,"longitude":55.0273,"heading":90,"speedKph":18.5,"energyPercent":72,"operationalStatus":"WORKING","availabilityStatus":"ASSIGNED","deviceId":"DEV-001","trackItId":"TRK-001"}
```
