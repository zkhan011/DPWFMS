# Geographic routing graph operations

DPWFMS remains the single operational routing authority. The production service
loads the one `ACTIVE` graph that was explicitly reviewed and approved, builds
the existing Java A*/Dijkstra engine, and persists every route and its ordered
segments. No browser, GraphHopper, OSRM, openTCS, or fabricated distance is used.

## Deployment inputs

Mount a deployment-approved `.osm.pbf` outside the Git checkout and configure
`ROUTING_GRAPH_FILE`. The repository intentionally contains no Jebel Ali road
data. Configure `ROUTING_OVERRIDE_FILE` with a reviewed YAML file based on
`deploy/routing-overrides.example.yml`. Overrides can disable OSM ways, correct
direction or speed, apply vehicle restrictions, add surveyed private nodes and
segments, and bind parking, charging, and fueling resource codes to nodes.
Checksums and paths are recorded with each graph version.

## Import and approval

1. `POST /api/routing/graphs/validate` parses but does not persist or activate.
2. `POST /api/routing/graphs/import` creates a `DRAFT` with its import report.
3. A reviewer calls `POST /api/routing/graphs/{id}/review` with a reason.
4. A routing approver calls `POST /api/routing/graphs/{id}/approve`.
5. An authorized operator calls `POST /api/routing/graphs/{id}/activate`.

Import never replaces the active graph. Activation retires the prior active
version in the same transaction and applies reviewed logical resource-node
bindings. Use `GET /api/routing/health` as a deployment gate when
`ROUTING_REQUIRE_APPROVED_GRAPH=true`.

## Validation and rollback

The importer reports missing references, duplicate logical identifiers, invalid
coordinates, zero-length edges, disconnected components, and unreachable
operational resources. A graph with errors cannot be approved. To roll back,
review/approve the earlier source as a new immutable version or explicitly move
a previously approved version through the controlled activation operation; do
not update graph rows manually. Preserve the PBF, override YAML, checksums,
import report, and approval audit for incident review.

## Map matching

WGS84 telemetry remains in `assets` and `asset_positions`. Logical matches are
stored separately in `matched_node_id`, `matched_segment_id`, `matched_at`,
`match_confidence`, and `match_metadata`. JTS STRtree matching scores physical
distance, heading, prior-segment continuity, and directed edges. A match updates
logical position only and never acknowledges a dispatch command.

## Production blockers

Before activation, supply surveyed terminal-only roads and resource bindings,
asset physical routing profiles, approved access restrictions and speeds, and a
small certified test graph. Validate reachability for every active operational
resource and exercise rollback in a staging database.
