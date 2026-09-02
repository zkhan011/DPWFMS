CREATE TABLE routing_graph_versions (
 id UUID PRIMARY KEY,
 version BIGINT UNIQUE NOT NULL,
 name VARCHAR(160) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','APPROVED','ACTIVE','RETIRED','REJECTED')),
 source_file VARCHAR(500) NOT NULL,
 source_checksum VARCHAR(64) NOT NULL,
 override_file VARCHAR(500),
 override_checksum VARCHAR(64),
 source_metadata JSONB NOT NULL DEFAULT '{}',
 import_report JSONB NOT NULL DEFAULT '{}',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_by VARCHAR(160) NOT NULL,
 reviewed_at TIMESTAMPTZ,
 reviewed_by VARCHAR(160),
 review_reason TEXT,
 approved_at TIMESTAMPTZ,
 approved_by VARCHAR(160),
 activated_at TIMESTAMPTZ,
 activated_by VARCHAR(160),
 version_lock BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_one_active_routing_graph ON routing_graph_versions((status)) WHERE status='ACTIVE';
CREATE TABLE routing_graph_resource_nodes (graph_version_id UUID NOT NULL REFERENCES routing_graph_versions(id) ON DELETE CASCADE,resource_type VARCHAR(20) NOT NULL CHECK(resource_type IN ('PARKING','CHARGING','FUELING')),resource_code VARCHAR(80) NOT NULL,node_code VARCHAR(60) NOT NULL,PRIMARY KEY(graph_version_id,resource_type,resource_code));

ALTER TABLE map_nodes ADD COLUMN graph_version_id UUID REFERENCES routing_graph_versions(id) ON DELETE CASCADE;
ALTER TABLE map_nodes ADD COLUMN osm_node_id BIGINT;
ALTER TABLE map_nodes ADD COLUMN source_kind VARCHAR(30) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE map_nodes ADD COLUMN tags JSONB NOT NULL DEFAULT '{}';
ALTER TABLE map_nodes ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE map_nodes DROP CONSTRAINT map_nodes_code_key;
CREATE UNIQUE INDEX uq_legacy_map_node_code ON map_nodes(code) WHERE graph_version_id IS NULL;
CREATE UNIQUE INDEX uq_graph_map_node_code ON map_nodes(graph_version_id,code) WHERE graph_version_id IS NOT NULL;
CREATE UNIQUE INDEX uq_graph_osm_node ON map_nodes(graph_version_id,osm_node_id) WHERE osm_node_id IS NOT NULL;
CREATE INDEX idx_map_nodes_graph ON map_nodes(graph_version_id);

ALTER TABLE road_segments ADD COLUMN graph_version_id UUID REFERENCES routing_graph_versions(id) ON DELETE CASCADE;
ALTER TABLE road_segments ADD COLUMN osm_way_id BIGINT;
ALTER TABLE road_segments ADD COLUMN source_sequence INTEGER;
ALTER TABLE road_segments ADD COLUMN direction VARCHAR(20) NOT NULL DEFAULT 'FORWARD' CHECK(direction IN ('FORWARD','REVERSE'));
ALTER TABLE road_segments ADD COLUMN geometry_wgs84 JSONB;
ALTER TABLE road_segments ADD COLUMN tags JSONB NOT NULL DEFAULT '{}';
ALTER TABLE road_segments ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE road_segments ADD COLUMN access_restrictions JSONB NOT NULL DEFAULT '{}';
ALTER TABLE road_segments DROP CONSTRAINT road_segments_code_key;
CREATE UNIQUE INDEX uq_legacy_road_segment_code ON road_segments(code) WHERE graph_version_id IS NULL;
CREATE UNIQUE INDEX uq_graph_road_segment_code ON road_segments(graph_version_id,code) WHERE graph_version_id IS NOT NULL;
CREATE INDEX idx_road_segments_graph ON road_segments(graph_version_id);
CREATE INDEX idx_road_segments_osm_way ON road_segments(graph_version_id,osm_way_id);

ALTER TABLE service_bays ADD COLUMN IF NOT EXISTS routing_node_id UUID REFERENCES map_nodes(id);

ALTER TABLE routes ADD COLUMN graph_version_id UUID REFERENCES routing_graph_versions(id);
ALTER TABLE routes ADD COLUMN source_node_code VARCHAR(60);
ALTER TABLE routes ADD COLUMN destination_node_code VARCHAR(60);
ALTER TABLE routes ADD COLUMN request_metadata JSONB NOT NULL DEFAULT '{}';
ALTER TABLE routes ADD COLUMN route_geojson JSONB;
ALTER TABLE routes ADD COLUMN failure_reason TEXT;

CREATE TABLE asset_routing_profiles (asset_id UUID PRIMARY KEY REFERENCES assets(id) ON DELETE CASCADE,height_m DOUBLE PRECISION NOT NULL CHECK(height_m>0),width_m DOUBLE PRECISION NOT NULL CHECK(width_m>0),length_m DOUBLE PRECISION NOT NULL CHECK(length_m>0),weight_tonnes DOUBLE PRECISION NOT NULL CHECK(weight_tonnes>0),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_by VARCHAR(160) NOT NULL);

ALTER TABLE assets ADD COLUMN matched_node_id UUID REFERENCES map_nodes(id);
ALTER TABLE assets ADD COLUMN matched_segment_id UUID REFERENCES road_segments(id);
ALTER TABLE assets ADD COLUMN matched_at TIMESTAMPTZ;
ALTER TABLE assets ADD COLUMN match_confidence DOUBLE PRECISION;
ALTER TABLE assets ADD COLUMN match_metadata JSONB NOT NULL DEFAULT '{}';

INSERT INTO permissions(id,code,description)
SELECT gen_random_uuid(),code,replace(code,'.',' ')
FROM unnest(ARRAY['routing.graph.read','routing.graph.import','routing.graph.review','routing.graph.approve','routing.graph.activate','routing.route']) AS permission_code(code)
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
WHERE r.name='SUPER_ADMIN' AND p.code LIKE 'routing.%' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r JOIN permissions p ON
 (r.name='SYSTEM_ADMIN' AND p.code IN ('routing.graph.read','routing.graph.import','routing.graph.review','routing.graph.approve','routing.graph.activate','routing.route')) OR
 (r.name='FLEET_MANAGER' AND p.code IN ('routing.graph.read','routing.route')) OR
 (r.name IN ('DISPATCHER','CONTROL_ROOM_OPERATOR') AND p.code IN ('routing.graph.read','routing.route')) OR
 (r.name IN ('AUDITOR','REPORT_VIEWER') AND p.code='routing.graph.read')
ON CONFLICT DO NOTHING;
