CREATE TABLE trackit_telemetry_batches (
  id UUID PRIMARY KEY,
  received_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  received_count INTEGER NOT NULL CHECK (received_count >= 0),
  accepted_count INTEGER NOT NULL DEFAULT 0 CHECK (accepted_count >= 0),
  rejected_count INTEGER NOT NULL DEFAULT 0 CHECK (rejected_count >= 0),
  duplicate_count INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
  requested_by VARCHAR(160) NOT NULL
);

CREATE TABLE trackit_asset_metadata (
  asset_id UUID PRIMARY KEY REFERENCES assets(id) ON DELETE CASCADE,
  external_asset_id VARCHAR(160) UNIQUE NOT NULL,
  source_asset_number VARCHAR(80) NOT NULL,
  make VARCHAR(120),
  model VARCHAR(120),
  first_seen_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE trackit_telemetry_history (
  id UUID PRIMARY KEY,
  batch_id UUID REFERENCES trackit_telemetry_batches(id),
  batch_index INTEGER NOT NULL,
  asset_id UUID NOT NULL REFERENCES assets(id),
  external_asset_id VARCHAR(160) NOT NULL,
  asset_number VARCHAR(80) NOT NULL,
  measurement_timestamp TIMESTAMPTZ NOT NULL,
  source_updated_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  speed_kph DOUBLE PRECISION,
  battery_voltage DOUBLE PRECISION,
  fuel_consumption_rate NUMERIC,
  total_fuel_used NUMERIC,
  engine_hours NUMERIC,
  location_mapping_status VARCHAR(80),
  location_type VARCHAR(80),
  location_name VARCHAR(200),
  location_code VARCHAR(100),
  gps_available BOOLEAN NOT NULL,
  fuel_data_available BOOLEAN NOT NULL,
  engine_data_available BOOLEAN NOT NULL,
  can_data_available BOOLEAN NOT NULL,
  can_parameters JSONB NOT NULL DEFAULT '[]',
  raw_payload JSONB NOT NULL,
  source_latency_seconds BIGINT NOT NULL,
  ingestion_latency_seconds BIGINT NOT NULL,
  UNIQUE (external_asset_id, measurement_timestamp)
);
CREATE INDEX idx_trackit_history_asset_time ON trackit_telemetry_history(asset_id, measurement_timestamp DESC);
CREATE INDEX idx_trackit_history_external_time ON trackit_telemetry_history(external_asset_id, measurement_timestamp DESC);
CREATE INDEX idx_trackit_history_location_time ON trackit_telemetry_history(location_code, measurement_timestamp DESC);
CREATE INDEX idx_trackit_history_time ON trackit_telemetry_history(measurement_timestamp DESC);

CREATE TABLE trackit_telemetry_latest (
  asset_id UUID PRIMARY KEY REFERENCES assets(id) ON DELETE CASCADE,
  telemetry_id UUID UNIQUE NOT NULL REFERENCES trackit_telemetry_history(id) ON DELETE RESTRICT,
  external_asset_id VARCHAR(160) UNIQUE NOT NULL,
  asset_number VARCHAR(80) NOT NULL,
  make VARCHAR(120),
  model VARCHAR(120),
  measurement_timestamp TIMESTAMPTZ NOT NULL,
  source_updated_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  last_valid_latitude DOUBLE PRECISION,
  last_valid_longitude DOUBLE PRECISION,
  last_valid_gps_at TIMESTAMPTZ,
  speed_kph DOUBLE PRECISION,
  battery_voltage DOUBLE PRECISION,
  fuel_consumption_rate NUMERIC,
  total_fuel_used NUMERIC,
  engine_hours NUMERIC,
  location_mapping_status VARCHAR(80),
  location_type VARCHAR(80),
  location_name VARCHAR(200),
  location_code VARCHAR(100),
  gps_available BOOLEAN NOT NULL,
  fuel_data_available BOOLEAN NOT NULL,
  engine_data_available BOOLEAN NOT NULL,
  can_data_available BOOLEAN NOT NULL,
  can_parameters JSONB NOT NULL DEFAULT '[]',
  source_latency_seconds BIGINT NOT NULL,
  ingestion_latency_seconds BIGINT NOT NULL
);
CREATE INDEX idx_trackit_latest_measurement ON trackit_telemetry_latest(measurement_timestamp DESC);
CREATE INDEX idx_trackit_latest_location ON trackit_telemetry_latest(location_code);
CREATE INDEX idx_trackit_latest_availability ON trackit_telemetry_latest(gps_available, fuel_data_available, engine_data_available, can_data_available);

CREATE TABLE trackit_telemetry_batch_results (
  batch_id UUID NOT NULL REFERENCES trackit_telemetry_batches(id) ON DELETE CASCADE,
  batch_index INTEGER NOT NULL,
  external_asset_id VARCHAR(160),
  status VARCHAR(30) NOT NULL CHECK (status IN ('ACCEPTED','REJECTED','DUPLICATE','OUT_OF_ORDER')),
  message VARCHAR(500),
  PRIMARY KEY (batch_id, batch_index)
);

INSERT INTO permissions(id, code, description)
SELECT gen_random_uuid(), code, replace(code, '.', ' ')
FROM unnest(ARRAY['trackit.telemetry.ingest','trackit.telemetry.read']) AS permission_code(code)
ON CONFLICT(code) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.name IN ('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code LIKE 'trackit.telemetry.%')
   OR (r.name IN ('FLEET_MANAGER','DISPATCHER','CONTROL_ROOM_OPERATOR','PLANT_MANAGER','SAFETY_OFFICER','REPORT_VIEWER') AND p.code='trackit.telemetry.read')
ON CONFLICT DO NOTHING;
