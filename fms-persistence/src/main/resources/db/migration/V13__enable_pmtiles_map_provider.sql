-- Allow direct MapLibre rendering from PMTiles archives.
ALTER TABLE map_configurations DROP CONSTRAINT IF EXISTS map_configurations_provider_check;
ALTER TABLE map_configurations
  ADD CONSTRAINT map_configurations_provider_check
  CHECK (provider IN ('google','osm','offline','mapbox','pmtiles'));
