# Offline map setup

DPW FMS supports raster XYZ tiles and direct vector tiles from a PMTiles archive. For raster XYZ, run TileServer GL, Martin, or another internal tile service and mount the output at `/tiles/{z}/{x}/{y}.png`. For PMTiles, serve a MapLibre style JSON, the `.pmtiles` archive, glyphs, and referenced assets from your tested .NET deployment. Keep the archive outside Git.

For direct PMTiles, select **Direct PMTiles (MapLibre)** in **Administration → Map provider** and set **Vector style URL** to the URL of the style JSON. The style should use a PMTiles source such as `pmtiles://dubai_area.pmtiles`. The browser reads tile ranges directly from the archive through the PMTiles protocol; DPW FMS receives the live fleet data separately from its API.

The .NET host must serve byte-range requests for the archive. When map assets use a different origin, configure CORS for the style, archive, glyphs, and style assets. Check attribution, coverage, and zoom levels in the browser. See [map configuration](MAP_CONFIGURATION.md) for deployment details.

For the Jebel Ali map, use the operational boundary around `24.9857,55.0273` and default zoom `12`. Keep raster XYZ settings available if that deployment needs a raster fallback.
