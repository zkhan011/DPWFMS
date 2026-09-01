# Offline map setup

Use an OSM extract whose license permits the intended terminal deployment. For raster XYZ, run TileServer GL, Martin, or another internal tile service and mount the output at `/tiles/{z}/{x}/{y}.png`. For MBTiles/PMTiles, expose an XYZ endpoint or MapLibre style from the local service; do not place multi-gigabyte packages in Git.

For Jebel Ali, configure the licensed operational boundary around `24.9857,55.0273`, place the style at `/maps/style.json`, set `MAP_PROVIDER=offline`, and set `VITE_OFFLINE_TILE_URL=/tiles/{z}/{x}/{y}.png`. Verify zoom 12–18 coverage with network access disabled. Retain OpenStreetMap attribution in the rendered map.
