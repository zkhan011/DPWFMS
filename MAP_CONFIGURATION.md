# Map configuration

DPW FMS keeps operational map behavior in its provider adapter. The direct PMTiles provider uses MapLibre GL JS and the PMTiles protocol in the browser. It reads the style, vector tiles, glyphs, and assets from the configured map host while DPW FMS continues to supply live vehicle positions, telemetry details, trails, and operational layers.

| Provider | Use |
|---|---|
| `pmtiles` | Direct vector tile access from a `.pmtiles` archive through a MapLibre style |
| `offline` | Raster XYZ tiles, with optional style URL for existing deployments |
| `osm` | OpenStreetMap-compatible raster tiles |
| `google`, `mapbox` | Existing provider options and fallback behavior |

Provider settings are stored in `map_configurations`; the saved database row is what the browser requests at runtime. Open **Administration → Map provider** to choose a provider, center, zoom, URL, and layers. For PMTiles, select **Direct PMTiles (MapLibre)** and set **Vector style URL** to the style JSON served by your .NET deployment (for example, `https://maps.example.net/map/style.json`). The style should reference the archive with a PMTiles URL such as `pmtiles://dubai_area.pmtiles` and include the correct glyph URL for its fonts.

The PMTiles provider keeps DPW FMS's fleet filters, status colors, heading-aware vehicle markers, clustering, selection popups, telemetry drawer, current route/trail, and parking, charging, fueling, and geofence layers. The configured style controls the basemap appearance. The archive and style stay in your map deployment; DPW FMS does not copy or proxy the tile archive.

The map server must support HTTP byte-range requests for the archive. If it is hosted on a different origin from DPW FMS, allow browser CORS requests for the style, archive byte ranges, glyphs, and any style assets. Keep required attribution in the style. The configuration test checks that a style URL is present; a successful browser load also depends on the .NET host serving those files correctly.

| Variable | Values/default |
|---|---|
| `VITE_GOOGLE_MAPS_API_KEY` | Browser-restricted key; empty by default |
| `VITE_MAPBOX_ACCESS_TOKEN` | Public browser token; empty by default |
| `VITE_OSM_TILE_URL` | OSM-compatible XYZ URL |
| `VITE_OFFLINE_TILE_URL` | `/tiles/{z}/{x}/{y}.png` |
| `VITE_OFFLINE_STYLE_URL` | `/maps/style.json` |
| `VITE_PMTILES_STYLE_URL` | Optional frontend build-time PMTiles style default |
| defaults | Jebel Ali `24.9857,55.0273`, zoom `12` |

The database-backed style URL takes precedence over build-time defaults. `map.read` is required to view the map, `vehicle.read` is required for telemetry markers, and `map.configure` is required to edit its configuration.

## Direct PMTiles

1. Host the MapLibre style JSON, PMTiles archive, glyphs, and referenced assets on your .NET map server.
2. In the style, use the `pmtiles://` source scheme and a path to the archive relative to the style. For example: `"url": "pmtiles://dubai_area.pmtiles"`.
3. Select **Direct PMTiles (MapLibre)** in **Administration → Map provider**, enter the style URL, then save.
4. Test the style and archive in the DPW FMS browser. Confirm that the archive responds to byte-range requests and that the browser console has no CORS or glyph errors.
5. Verify the map at the desired zoom levels and retain the attribution supplied with the data.

No tile archive is stored in Git. DPW FMS uses the existing map style URL setting to point at your deployment.

## OpenStreetMap

Use an organization-operated OSM-compatible tile service for production, preserve its attribution, and comply with its usage policy. The public OSM tile service is not intended for heavy production fleet traffic or offline bulk download.

## Google Maps

Select `google` in Administration and provide `VITE_GOOGLE_MAPS_API_KEY` at frontend build time. The backend secret reference is metadata only and is never sent to the browser. If the build-time key is absent, DPW FMS falls back to OSM while online and offline XYZ while disconnected.

See [offline maps](OFFLINE_MAP_SETUP.md), [Google Maps setup](GOOGLE_MAPS_SETUP.md), and [mock-data testing](MOCK_DATA_TESTING.md).
