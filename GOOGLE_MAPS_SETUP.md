# Google Maps setup

1. Create a browser key in the organization-owned Google Cloud project.
2. Restrict it to the required Maps JavaScript API and exact production origins.
3. Store it in the deployment secret manager; never in Git or a database value returned to browsers.
4. Set `MAP_PROVIDER=google` and inject `VITE_GOOGLE_MAPS_API_KEY` only during the frontend build.
5. Build and use Administration → Map providers → Test connectivity.

If the key is absent, rejected, or the browser is offline, DPW FMS falls back rather than failing. Google use may incur cost; offline and OSM providers remain supported.
