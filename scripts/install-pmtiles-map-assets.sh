#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: bash scripts/install-pmtiles-map-assets.sh /path/to/Map.zip [map-assets-directory]" >&2
  exit 2
fi

zip_file=$1
destination=${2:-map-assets}

if [[ ! -f "$zip_file" ]]; then
  echo "Map archive not found: $zip_file" >&2
  exit 1
fi

mkdir -p "$destination/fonts"
unzip -p "$zip_file" 'Map/dubai_area.pmtiles' > "$destination/dubai_area.pmtiles"
unzip -p "$zip_file" 'Map/style.json' | sed 's|/map/fonts/|/api/maps/fonts/|g' > "$destination/style.json"

while IFS= read -r entry; do
  relative_path=${entry#Map/}
  mkdir -p "$destination/$(dirname "$relative_path")"
  unzip -p "$zip_file" "$entry" > "$destination/$relative_path"
done < <(unzip -Z1 "$zip_file" | grep '^Map/fonts/.*\.pbf$')

echo "Installed Java-hosted PMTiles assets in $destination"
