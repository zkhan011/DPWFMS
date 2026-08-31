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
