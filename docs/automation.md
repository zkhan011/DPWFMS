# Automatic parking and fueling

## Deterministic evaluation

The same `AutomaticJobEngine` path serves telemetry/status events, manual dispatcher triggers, safe simulations, and 30-second scheduled reconciliation. A decision captures the UTC input snapshot, resolved rules, matches, exact blockers, every candidate's component scores, the selected action/resource, idempotency key, reservation and job identity. Fueling is evaluated before parking.

Configuration resolution is deterministic and follows: **asset → asset group → asset type → operational zone → terminal → global**. Within an equal scope, priority, version, then rule code break ties. Fuel threshold activation is rejected unless `0 ≤ emergency < critical < low ≤ 100`.

Default rules implement:

* 60-second telemetry freshness;
* 5-minute parking idle time and 10-minute next-job suppression;
* 5-minute parking creation cooldown and 10-minute parking reservation;
* 35% low, 20% critical, 10% emergency fuel thresholds with 3% hysteresis;
* 30-minute post-fueling cooldown and 15-minute fueling reservation;
* 10-minute identical-alert suppression.

Parking scoring combines route distance/time, congestion, next-job positioning, zone, maneuver, occupancy and operational preference. Fuel scoring combines route time, queue delay, congestion, deviation, service duration, station preference and outage risk. All components and rejection reasons appear in decision responses.

## Safety and concurrency

`automation_decisions.idempotency_key`, `jobs.idempotency_key`, and reservation uniqueness provide database defenses. The in-process reservation adapter uses atomic map operations for tests/local execution; a production multi-instance adapter must acquire Redis `SET key value NX PX ttl` before the transaction and rely on the database unique indexes as the final authority. Lock values must be owner tokens and released atomically with a Lua compare/delete script.

Stale telemetry blocks dispatch and raises a deduplicated major alert. Emergency fuel always raises a critical alert and bypasses ordinary cooldown suppression. An unreachable emergency asset receives `EMERGENCY_INTERVENTION`, never an unsafe route. A low-fuel idle asset creates a `FUEL_THEN_PARK` parent plus a `BLOCKED_BY_DEPENDENCY` child parking record.

## API examples

```bash
# Active and historical versions
curl -u operator:change-me-local-only http://localhost:8080/api/automation/rules

# Obtain a seeded automation asset, then simulate without side effects
ASSET=$(curl -su operator:change-me-local-only http://localhost:8080/api/automation/assets | jq -r '.[0]')
curl -u operator:change-me-local-only -X POST \
  http://localhost:8080/api/automation/decisions/simulate/$ASSET

# Authorized event-driven evaluation/manual triggers
curl -u operator:change-me-local-only -X POST http://localhost:8080/api/automation/assets/$ASSET/evaluate
curl -u operator:change-me-local-only -X POST http://localhost:8080/api/automation/assets/$ASSET/parking
curl -u operator:change-me-local-only -X POST http://localhost:8080/api/automation/assets/$ASSET/fueling

# Operational evidence
curl -u operator:change-me-local-only http://localhost:8080/api/automation/decisions
curl -u operator:change-me-local-only http://localhost:8080/api/automation/reservations
curl -u operator:change-me-local-only http://localhost:8080/api/automation/alerts
curl -u operator:change-me-local-only http://localhost:8080/api/automation/scenarios
```

Rule creation/versioning, activation, exceptional approval, and reservation release require Administrator or Dispatcher authorization as appropriate and generate automation audit events.

## Simulator demonstrations

`GET /api/automation/scenarios` describes visible scenarios for idle parking, occupied-space rerouting, queue-aware fueling, critical-fuel transport blocking, station outage, fuel-to-park chaining, duplicate telemetry, concurrent reservations, stale GPS, and route deviation. Seed data includes 10 automation assets, 30 spaces, and two stations whose queue costs demonstrate that the nearest resource need not win.

## Known production integration boundary

The deterministic rule logic, versioned rule repository, append-only decision audit, schema, endpoints, Redis reconciliation lock, local simulator, UI, atomic local concurrency behavior and unit tests are implemented. Before claiming multi-instance production acceptance, replace the composition-root in-memory job/resource stores with PostgreSQL repositories and a transactional outbox, add a Redis-backed resource-reservation adapter, connect resource routes to the persisted A\* graph, certify station/occupancy device messages, and run the Testcontainers plus Docker failover suite in an environment with registry and Docker access.
