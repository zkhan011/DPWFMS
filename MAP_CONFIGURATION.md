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

Provider settings are stored in `map_configurations`. Secret values are represented only by secret-manager references and are not returned by the API. Open **Administration → Map provider** to change the provider, center, zoom, XYZ/style URLs, visible layers, and secret-manager reference. **Test configuration** validates that all required values are present; reopening the Map view applies saved values. See [offline maps](OFFLINE_MAP_SETUP.md) and [Google](GOOGLE_MAPS_SETUP.md).
