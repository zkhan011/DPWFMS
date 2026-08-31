# Operations, offline maps and production checklist

## Offline map

Obtain an OSM-derived MBTiles extract for the licensed operational boundary (for example Jebel Ali) and serve it internally with TileServer GL or Martin. Set `VITE_TILE_URL=https://tiles.internal/styles/dpw/{z}/{x}/{y}.png`. Verify source attribution/licensing and prohibit public egress. The UI defaults to same-origin `/tiles/`; no paid API is needed.

## Migrations, retention, backup and recovery

Flyway runs migrations on API startup. Back up PostgreSQL with encrypted `pg_dump -Fc` plus WAL archiving for the chosen RPO; snapshot Redis only for cache convenience, not as system of record. Restore into an isolated environment and run `flyway validate` plus reconciliation before service admission. Partition `asset_positions`/`asset_telemetry` monthly in production, retain raw points for the configured short window, aggregate movements, then archive or drop expired partitions.

For restart recovery, query nonterminal jobs, active reservations and unacknowledged dispatch attempts; expire stale reservations, correlate device state, and replay only commands with the original command ID. Accepted jobs and their event history remain PostgreSQL records.

## Troubleshooting

* Check `/actuator/health/readiness` before routing traffic and `/actuator/prometheus` for alerts.
* Inspect `dpwfms.integration.dlq` by correlation ID; correct data/configuration, then republish with the same message ID only through an audited replay tool.
* A stale asset is marked offline after the freshness threshold; validate broker session, topic identity and device clock.
* A dispatch retry retains physical command identity semantics. Never hand-edit queue payloads.

## Production configuration checklist

- [ ] DPWUM OIDC adapter configured; local users disabled
- [ ] TLS/mTLS at ingress and brokers; certificates rotated
- [ ] Secrets supplied by vault/orchestrator, never `.env` or Compose defaults
- [ ] CORS allowlist, rate limits, security headers and network policies verified
- [ ] Administrator/Dispatcher/Operator/Maintenance/Viewer/Integration Service least privilege tested
- [ ] Manual job, dispatch, cancellation, reassignment, map and admin actions audited
- [ ] PostgreSQL HA, pool size, PITR backup and restore drill verified
- [ ] Redis and RabbitMQ HA/quorum policies tested; MQTT persistent sessions enabled
- [ ] DLQ alerting, reconciliation, retention and clock synchronization enabled
- [ ] Offline tile licensing/capacity verified
- [ ] 1,500-asset soak, failover, broker outage and route closure tests passed
- [ ] DPWUM and TrackIT contracts certified in a non-production environment
