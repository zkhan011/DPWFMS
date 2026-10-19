# Map configuration

DPW FMS renders direct vector tiles from a PMTiles archive with MapLibre GL JS and the PMTiles protocol. The Java API serves the MapLibre style, archive, glyphs, and referenced assets from its local map-assets directory at `/api/maps/**`. No external map host or .NET style URL is required.

| Provider | Use |
|---|---|
| `pmtiles` | Direct vector tile access from a `.pmtiles` archive through a MapLibre style |
| `offline` | Raster XYZ tiles |
| `osm` | OpenStreetMap-compatible raster tiles |
| `google`, `mapbox` | Existing provider options and fallback behavior |

The seeded Jebel Ali map uses **Direct PMTiles (MapLibre)** and `/api/maps/style.json`. The API serves assets from `MAP_ASSETS_DIRECTORY` (default `./maps`; Docker Compose mounts `./map-assets` to `/app/maps`). Place `style.json`, `dubai_area.pmtiles`, and the `fonts` directory there. Use the included `scripts/install-pmtiles-map-assets.sh` to extract those files from the supplied `Map.zip`.

The style should reference the archive with a PMTiles URL such as `pmtiles://dubai_area.pmtiles`. The installer adjusts the glyph URL to `/api/maps/fonts/{fontstack}/{range}.pbf`. Java/Spring serves the archive using byte-range responses, which lets the browser read only the tile ranges it needs. Map assets are public static basemap files; DPW FMS API endpoints and live vehicle data remain protected by normal authentication.

The provider retains DPW FMS fleet filters, status colors, heading-aware vehicle markers, clustering, selection popups, telemetry drawer, current route/trail, and parking, charging, fueling, and geofence layers. The configured PMTiles style controls the basemap appearance and attribution.

| Variable | Values/default |
|---|---|
| `MAP_ASSETS_DIRECTORY` | Java API asset directory; Docker default `/app/maps` |
| `VITE_PMTILES_STYLE_URL` | `/api/maps/style.json` |
| `VITE_GOOGLE_MAPS_API_KEY` | Browser-restricted key; empty by default |
| `VITE_MAPBOX_ACCESS_TOKEN` | Public browser token; empty by default |
| `VITE_OSM_TILE_URL` | OSM-compatible XYZ URL |
| `VITE_OFFLINE_TILE_URL` | `/tiles/{z}/{x}/{y}.png` |
| defaults | Jebel Ali `24.9857,55.0273`, zoom `12` |

Provider settings are stored in `map_configurations`; the database style URL takes precedence over build-time defaults. `map.read` is required to view the DPW FMS map and `map.configure` is required to edit it. The basemap files themselves are served as public static resources.

## Direct PMTiles

1. Run `scripts/install-pmtiles-map-assets.sh /path/to/Map.zip` from the project root.
2. Confirm `map-assets/style.json`, `map-assets/dubai_area.pmtiles`, and `map-assets/fonts/Roboto Regular/` exist.
3. Start or rebuild DPW FMS with Docker Compose. The archive is mounted read-only into the Java API container.
4. Open the Map page. The seeded database configuration points to `/api/maps/style.json`.
5. Confirm the map loads and that the style attribution is visible.

The archive is not committed to Git. It is served by the Java API from the mounted map-assets directory.

## Other providers

Use an organization-operated OSM-compatible tile service for production and retain its attribution. Google and Mapbox credentials are configured at frontend build time; when absent, the existing provider fallback behavior remains.

See [offline maps](OFFLINE_MAP_SETUP.md), [Google Maps setup](GOOGLE_MAPS_SETUP.md), and [mock-data testing](MOCK_DATA_TESTING.md).
