# Google Maps setup

1. In an organization-owned Google Cloud project, enable **Maps JavaScript API** and configure billing according to DP World policy.
2. Create a browser API key. Apply an HTTP-referrer restriction for every exact frontend origin, for example `https://fms.example.com/*` and a separate development key for `http://localhost:5173/*`.
3. Apply an API restriction allowing only **Maps JavaScript API**. Do not use an unrestricted server key.
4. Supply the key only to the frontend build: `VITE_GOOGLE_MAPS_API_KEY=... npm run build`. Vite embeds browser keys in JavaScript, so referrer/API restrictions—not secrecy—are the security boundary. Never commit the key.
5. Start DPW FMS, open **Administration → Map provider**, select `Google Maps`, enter the default coordinates/zoom, and save. A secret-manager reference can be recorded for operations, but the browser adapter uses the build-time Vite key.
6. Reopen **Map**, verify the provider label says `Google Maps`, verify tiles and telemetry markers render, and inspect browser developer tools for referrer, billing, or API activation errors.
7. For Docker, pass `VITE_GOOGLE_MAPS_API_KEY` as a frontend build argument in your deployment pipeline; a container runtime environment variable cannot change an already-built Vite bundle.

If the key is absent at build time, DPW FMS falls back rather than failing. A key that exists but is rejected by Google produces a browser-side Maps error; correct its referrer/API/billing configuration or select OSM/offline in Administration. Google use may incur cost; offline and OSM providers remain supported.
