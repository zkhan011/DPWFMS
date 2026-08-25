# Troubleshooting

| Symptom | Check |
|---|---|
| Java rejected | `java -version`; Java 21 is mandatory |
| Database unavailable | `pg_isready -d "$DB_URL" -U "$DB_USER"`; verify pg_hba and TLS |
| Flyway failure | inspect `flyway_schema_history`; never edit an applied migration |
| Frontend 401 | clear session storage, verify `DPWFMS_LOCAL_USERNAME/PASSWORD`, sign in again |
| Map blank | test `/api/workspace/map-configuration`, tile URL and browser network; offline coverage may be missing |
| Google map falls back | key is missing/restricted; see `GOOGLE_MAPS_SETUP.md` |
| Kernel not configured | set `KERNEL_ENABLED=true` and a TLS endpoint, then restart |
| Broker unhealthy | inspect Control Center, broker certificate, credentials, vhost/topics and DLQ |
| Stale vehicle | confirm device clock, MQTT subscription and telemetry freshness threshold |
| Port occupied | set deployment port or stop the process recorded under `runtime/` |

Backend logs are in `logs/backend*.log` for direct scripts. Use correlation IDs to join API, message and audit records. Secrets are deliberately masked and must be inspected in the secret manager rather than logs.
