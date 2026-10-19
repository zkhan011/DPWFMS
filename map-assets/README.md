# Java-hosted PMTiles assets

The Java API serves this directory at `/api/maps/**`. Populate it from the supplied map package with:

```bash
bash scripts/install-pmtiles-map-assets.sh /path/to/Map.zip
```

The script extracts the style, PMTiles archive, and Roboto glyphs. Keep the generated archive files out of Git; Docker Compose mounts this directory read-only into the API container.
