# Deployment

## Docker Compose

```bash
cp .env.example .env
# replace all secrets
docker compose config
docker compose build --pull
docker compose up -d
docker compose ps
curl http://localhost:8080/actuator/health/readiness
```

The dashboard is `http://localhost:3000`, API is bound to `127.0.0.1:8080`, and RabbitMQ management is bound to `127.0.0.1:15672`. PostgreSQL and Redis remain internal. Terminate TLS at a trusted ingress and do not expose broker/database ports.

For production use external managed PostgreSQL/Redis/RabbitMQ where appropriate, `SPRING_PROFILES_ACTIVE=prod`, secret injection, DPWUM/OIDC, encrypted backup/PITR, health probes, multiple API replicas, and tested restore/failover procedures. The local Basic account is a development bootstrap, not enterprise authentication.

## Geographic routing deployment gate

Mount the approved PBF and terminal override YAML as read-only files and set the
`ROUTING_*` and `MAP_MATCH_*` variables documented in `.env.example`. Import is
always DRAFT. Review, approve, and activate it through the secured routing API.
Production should keep `ROUTING_REQUIRE_APPROVED_GRAPH=true`; health remains
DOWN until an approved graph is explicitly active. See
`docs/geographic-routing.md` for approval and rollback.
