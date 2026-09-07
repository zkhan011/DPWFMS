# Integration configuration

Kernel, TrackIT, MQTT and RabbitMQ adapters are represented by `integration_configurations`: enabled flag, type, endpoint, port, TLS, authentication reference, timeout, retry policy, health, last success and last error. APIs intentionally omit authentication references.

| Integration | Environment |
|---|---|
| Kernel/openTCS-compatible adapter | `KERNEL_ENABLED`, `KERNEL_ENDPOINT` |
| TrackIT | configure endpoint and secret-manager reference in PostgreSQL/administration |
| MQTT | broker adapter configuration; prefer TLS port 8883 |
| RabbitMQ | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` |
| PostgreSQL | `DB_URL`, `DB_USER`, `DB_PASSWORD` |

Use TLS/mTLS whenever supported. Store passwords and client keys in the platform secret manager and put only their references in DPW FMS. Broker credentials are never served to the frontend. MQTT topics and RabbitMQ routing are documented in `docs/integrations.md`.
