-- Allow direct MapLibre rendering from PMTiles archives.
ALTER TABLE map_configurations DROP CONSTRAINT IF EXISTS map_configurations_provider_check;
ALTER TABLE map_configurations
  ADD CONSTRAINT map_configurations_provider_check
  CHECK (provider IN ('google','osm','offline','mapbox','pmtiles'));

-- Point the seeded Jebel Ali map at the Java service's static map endpoint.
UPDATE map_configurations
SET provider='pmtiles', style_url='/api/maps/style.json', version=version+1,
    updated_at=now(), updated_by='migration-v13'
WHERE id='30000000-0000-0000-0000-000000000001';
