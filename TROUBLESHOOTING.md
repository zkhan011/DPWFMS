# Troubleshooting

## Administration reports missing permissions for `admin.demo`

Restart the API so Flyway applies V8, then sign out and sign in again so the browser sends a fresh Basic-auth credential. Verify the migration and effective authorities:

```sql
SELECT version, success FROM flyway_schema_history WHERE version='8';

SELECT r.name, p.code FROM users u
JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id
JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id
WHERE lower(u.username)='admin.demo'
  AND p.code IN ('map.read','user.read','role.read')
ORDER BY r.name,p.code;
```

The `ADMIN_DEMO_ACCESS` role must return all three permissions. The Administration UI now displays the real API error instead of incorrectly labeling every server/serialization failure as missing permission.

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
