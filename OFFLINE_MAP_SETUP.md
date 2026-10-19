# Offline map setup

DPW FMS serves direct PMTiles basemaps from the Java API. No .NET map host or external style URL is involved. The Java service exposes `/api/maps/**` from a local directory (Docker Compose mounts `./map-assets` at `/app/maps`) and supports byte-range reads of the archive.

To install the supplied Jebel Ali map from the project root:

```bash
bash scripts/install-pmtiles-map-assets.sh /path/to/Map.zip
docker compose up --build
```

The installer extracts the style, `dubai_area.pmtiles`, and Roboto glyphs into `map-assets`. It adjusts the style's glyph URL to the Java-served `/api/maps/fonts/` path. The initial database migration sets the map provider to `pmtiles` and style URL to `/api/maps/style.json`.

For direct PMTiles, the style source should use `pmtiles://dubai_area.pmtiles`. The browser reads tile ranges directly from the authenticated Java API using the DPW FMS session; fleet and operational data use the same authenticated API. Keep the archive out of Git; the compose mount is read-only.

Raster XYZ remains available as an alternative provider. For the Jebel Ali map, the default center is `24.9857,55.0273`, zoom `12`. Retain data attribution from the style and verify local coverage at the zoom levels operators need.
