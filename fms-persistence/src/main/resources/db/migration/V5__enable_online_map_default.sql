-- Keep offline support configurable, but make a fresh development installation display a map immediately.
UPDATE map_configurations
SET provider='osm', tile_url='https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    version=version+1, updated_at=now(), updated_by='migration-v5'
WHERE id='30000000-0000-0000-0000-000000000001'
  AND provider='offline' AND tile_url='/tiles/{z}/{x}/{y}.png';
