# Map configuration

DPW FMS separates operational layers from the provider adapter. `MapProvider` selects offline XYZ, OSM, Mapbox-compatible, or Google configuration. If a Google key or Mapbox token is absent, the client falls back to OSM while online and offline XYZ while disconnected; startup never depends on a paid API.

| Variable | Values/default |
|---|---|
| `MAP_PROVIDER` | `offline`, `osm`, `google`, `mapbox`; default `offline` |
| `VITE_GOOGLE_MAPS_API_KEY` | Browser-restricted key; empty by default |
| `VITE_MAPBOX_ACCESS_TOKEN` | Public browser token; empty by default |
| `VITE_OSM_TILE_URL` | OSM-compatible XYZ URL |
| `OFFLINE_TILE_URL` | `/tiles/{z}/{x}/{y}.png` |
| `OFFLINE_STYLE_URL` | `/maps/style.json` |
| defaults | Jebel Ali `24.9857,55.0273`, zoom `12` |

Provider settings are stored in `map_configurations`; the saved database row—not `MAP_PROVIDER` alone—is what the browser requests at runtime. Open **Administration → Map provider** to change the provider, center, zoom, XYZ/style URLs, and layers. Reopen Map after saving. `map.read` is required to view it, `vehicle.read` is required for telemetry markers, and `map.configure` is required to edit it.

## OpenStreetMap

1. Select `OpenStreetMap` in **Administration → Map provider**.
2. Use `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png` for development, or the URL of an organization-operated OSM-compatible tile service for production.
3. Set Jebel Ali defaults to latitude `24.9857`, longitude `55.0273`, zoom `12` and save.
4. Ensure the frontend deployment can reach the tile host and its Content Security Policy permits images from it.
5. Keep visible OpenStreetMap attribution and comply with the selected tile service's usage policy. The public OSM tile service is not intended for heavy production fleet traffic or offline bulk download.

`VITE_OSM_TILE_URL` is a frontend build-time default. A non-empty `tile_url` saved in the database takes precedence.

## Google Maps

Google requires two coordinated settings: select `google` in the database-backed Administration screen and provide `VITE_GOOGLE_MAPS_API_KEY` while building the frontend. The backend secret reference is metadata only and does not send a secret to the browser. If the build-time key is absent, DPW FMS deliberately falls back to OSM/offline tiles.

See [Google Maps setup](GOOGLE_MAPS_SETUP.md), [offline maps](OFFLINE_MAP_SETUP.md), and [mock-data testing](MOCK_DATA_TESTING.md).
