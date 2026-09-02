# Legacy `wwwroot.zip` reference analysis

## Artifact availability

The requested `wwwroot.zip` was not present in the supplied workspace or the
accessible temporary/home directories on 1 September 2026. No legacy file,
bundle, icon or token was copied. This implementation therefore uses the
functional requirements supplied with the task as its reference. Re-run this
analysis when the ZIP is attached if pixel-level asset comparison is required.

## Reference behaviours retained conceptually

The requested useful control-room patterns were reimplemented natively: compact
navigation, fleet state indicators, clustered live assets, status/freshness
filters, detailed asset selection, parking/charging occupancy, assignment
preview, exception reporting, operational reports and parameter administration.

## Patterns intentionally excluded

The application does not include Serenity, ASP.NET, Razor, C#, jQuery, the
legacy generated `TrackIT.Web.js`, an iframe-hosted map, hard-coded service URLs,
hard-coded map credentials or obsolete CDN dependencies. Map credentials remain
environment supplied and the frontend uses the existing React/Vite build.

## Existing architecture inspected

- Java 21 Maven reactor: domain, routing, scheduling, dispatch, application,
  persistence, MQTT, RabbitMQ, simulator and Spring Boot API modules.
- Spring Boot 3.4.5 with Spring MVC, Security, JDBC, Redis, AMQP and Actuator.
- PostgreSQL schema managed only through additive Flyway migrations.
- React 19, TypeScript 5.7 and Vite 6 with the existing component/style layer.
- Database-backed users, roles, permissions and method-level authorization.
- Telemetry REST/RabbitMQ ingestion, deduplication and current/history storage.
- Existing A* graph routing, job lifecycle, dispatch records, resource
  reservations, automation decisions, audit logs and correlation IDs.
- Leaflet/OpenStreetMap, offline tiles, optional Google/Mapbox providers, polling
  every five seconds and an existing SSE operations channel.
- Existing TrackIT identifiers/configuration, MQTT topic policy, RabbitMQ quorum
  topology, integration configuration and UNKNOWN-safe health state.
