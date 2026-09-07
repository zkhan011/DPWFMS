-- Additive security and operational administration model. Existing data is preserved.
CREATE TABLE permissions (
  id UUID PRIMARY KEY,
  code VARCHAR(100) UNIQUE NOT NULL,
  description VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(255);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS protected_role BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(160);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS service_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username ON users(username) WHERE username IS NOT NULL;

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE plants (
  id UUID PRIMARY KEY,
  code VARCHAR(60) UNIQUE NOT NULL,
  name VARCHAR(160) NOT NULL,
  location VARCHAR(255) NOT NULL,
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Dubai',
  status VARCHAR(30) NOT NULL CHECK (status IN ('OPERATIONAL','DEGRADED','CLOSED','INACTIVE')),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_plant_assignments (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plant_id UUID NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  assigned_by VARCHAR(160) NOT NULL,
  PRIMARY KEY (user_id, plant_id)
);

ALTER TABLE assets ADD COLUMN IF NOT EXISTS plant_id UUID REFERENCES plants(id);
ALTER TABLE assets ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS plant_id UUID REFERENCES plants(id);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS plant_id UUID REFERENCES plants(id);

CREATE TABLE transport_orders (
  id UUID PRIMARY KEY,
  order_number VARCHAR(80) UNIQUE NOT NULL,
  plant_id UUID NOT NULL REFERENCES plants(id),
  source_location_id UUID REFERENCES locations(id),
  destination_location_id UUID REFERENCES locations(id),
  order_type VARCHAR(50) NOT NULL,
  priority INTEGER NOT NULL CHECK (priority BETWEEN 0 AND 100),
  intended_asset_id UUID REFERENCES assets(id),
  assigned_asset_id UUID REFERENCES assets(id),
  status VARCHAR(40) NOT NULL,
  cancellation_reason TEXT,
  created_by VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_transport_orders_plant_status ON transport_orders(plant_id, status, created_at DESC);

CREATE TABLE map_configurations (
  id UUID PRIMARY KEY,
  plant_id UUID REFERENCES plants(id),
  provider VARCHAR(30) NOT NULL CHECK (provider IN ('google','osm','offline','mapbox')),
  default_latitude DOUBLE PRECISION NOT NULL,
  default_longitude DOUBLE PRECISION NOT NULL,
  default_zoom INTEGER NOT NULL CHECK (default_zoom BETWEEN 1 AND 22),
  tile_url VARCHAR(500),
  style_url VARCHAR(500),
  secret_reference VARCHAR(255),
  visible_layers JSONB NOT NULL DEFAULT '[]',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(160) NOT NULL
);

CREATE TABLE integration_configurations (
  id UUID PRIMARY KEY,
  integration_code VARCHAR(80) UNIQUE NOT NULL,
  integration_type VARCHAR(50) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  endpoint VARCHAR(500),
  port INTEGER CHECK (port BETWEEN 1 AND 65535),
  tls_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  authentication_reference VARCHAR(255),
  connection_timeout_ms INTEGER NOT NULL DEFAULT 5000,
  retry_policy JSONB NOT NULL DEFAULT '{}',
  health_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
  last_success_at TIMESTAMPTZ,
  last_error TEXT,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(160) NOT NULL
);

INSERT INTO plants(id, code, name, location, latitude, longitude, timezone, status)
VALUES ('20000000-0000-0000-0000-000000000001','JEA','Jebel Ali Terminal','Jebel Ali, Dubai',24.9857,55.0273,'Asia/Dubai','OPERATIONAL')
ON CONFLICT (code) DO NOTHING;

INSERT INTO map_configurations(id, plant_id, provider, default_latitude, default_longitude,
 default_zoom, tile_url, style_url, visible_layers, updated_by)
VALUES ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
 'offline',24.9857,55.0273,12,'/tiles/{z}/{x}/{y}.png','/maps/style.json',
 '["plants","vehicles","parking","fueling","charging","alerts"]','flyway')
ON CONFLICT (id) DO NOTHING;

INSERT INTO permissions(id, code, description)
SELECT gen_random_uuid(), code, replace(code, '.', ' ')
FROM unnest(ARRAY[
 'dashboard.read','plant.read','plant.manage','map.read','map.configure','vehicle.read','vehicle.manage',
 'vehicle.enable','vehicle.disable','order.read','order.create','order.assign','order.cancel','order.retry',
 'dispatch.read','dispatch.execute','dispatch.override','parking.read','parking.manage','fueling.read',
 'fueling.manage','charging.read','charging.manage','alert.read','alert.acknowledge','alert.resolve',
 'report.read','report.export','control_center.read','control_center.operate','integration.read',
 'integration.manage','user.read','user.manage','role.read','role.manage','audit.read','system.configure'
]) AS code ON CONFLICT (code) DO NOTHING;

INSERT INTO roles(id, name, description, protected_role)
SELECT gen_random_uuid(), role_name, replace(role_name, '_', ' '), role_name IN ('SUPER_ADMIN','API_SERVICE')
FROM unnest(ARRAY['SUPER_ADMIN','SYSTEM_ADMIN','FLEET_MANAGER','DISPATCHER','CONTROL_ROOM_OPERATOR',
 'PLANT_MANAGER','FUEL_OPERATOR','CHARGING_OPERATOR','MAINTENANCE_OPERATOR','SAFETY_OFFICER',
 'REPORT_VIEWER','AUDITOR','API_SERVICE','DRIVER_OR_VEHICLE_CLIENT']) AS role_name
ON CONFLICT (name) DO NOTHING;

-- Super Admin receives every permission. Other exact grants are applied below and documented.
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON
 (r.name='SYSTEM_ADMIN' AND p.code IN ('dashboard.read','plant.read','map.read','map.configure','vehicle.read','control_center.read','integration.read','integration.manage','user.read','user.manage','role.read','audit.read','system.configure')) OR
 (r.name='FLEET_MANAGER' AND p.code IN ('dashboard.read','plant.read','map.read','vehicle.read','vehicle.manage','vehicle.enable','vehicle.disable','order.read','order.create','order.assign','order.cancel','order.retry','dispatch.read','dispatch.execute','dispatch.override','parking.read','parking.manage','fueling.read','fueling.manage','charging.read','charging.manage','alert.read','report.read','report.export')) OR
 (r.name='DISPATCHER' AND p.code IN ('dashboard.read','plant.read','map.read','vehicle.read','order.read','order.create','order.assign','order.cancel','order.retry','dispatch.read','dispatch.execute','dispatch.override','parking.read','parking.manage','fueling.read','charging.read','alert.read')) OR
 (r.name='CONTROL_ROOM_OPERATOR' AND p.code IN ('dashboard.read','plant.read','map.read','vehicle.read','order.read','dispatch.read','parking.read','fueling.read','charging.read','alert.read','alert.acknowledge','control_center.read')) OR
 (r.name='PLANT_MANAGER' AND p.code IN ('dashboard.read','plant.read','plant.manage','map.read','vehicle.read','vehicle.manage','order.read','order.create','order.assign','order.cancel','dispatch.read','dispatch.execute','parking.read','parking.manage','fueling.read','fueling.manage','charging.read','charging.manage','alert.read','alert.acknowledge','report.read','report.export')) OR
 (r.name='FUEL_OPERATOR' AND p.code IN ('dashboard.read','plant.read','map.read','vehicle.read','fueling.read','fueling.manage','alert.read','alert.acknowledge')) OR
 (r.name='CHARGING_OPERATOR' AND p.code IN ('dashboard.read','plant.read','map.read','vehicle.read','charging.read','charging.manage','alert.read','alert.acknowledge')) OR
 (r.name='MAINTENANCE_OPERATOR' AND p.code IN ('dashboard.read','plant.read','vehicle.read','vehicle.manage','alert.read','alert.acknowledge')) OR
 (r.name='SAFETY_OFFICER' AND p.code IN ('dashboard.read','plant.read','map.read','vehicle.read','alert.read','alert.acknowledge','alert.resolve','report.read','audit.read')) OR
 (r.name='REPORT_VIEWER' AND p.code IN ('dashboard.read','plant.read','vehicle.read','order.read','alert.read','report.read','report.export')) OR
 (r.name='AUDITOR' AND p.code IN ('dashboard.read','plant.read','integration.read','user.read','role.read','audit.read')) OR
 (r.name='API_SERVICE' AND p.code IN ('plant.read','map.read','vehicle.read','vehicle.manage','order.read','order.create','dispatch.read','alert.read','integration.read')) OR
 (r.name='DRIVER_OR_VEHICLE_CLIENT' AND p.code IN ('vehicle.read','order.read'))
ON CONFLICT DO NOTHING;

INSERT INTO integration_configurations(id,integration_code,integration_type,enabled,endpoint,port,tls_enabled,retry_policy,updated_by)
VALUES
 ('40000000-0000-0000-0000-000000000001','KERNEL','KERNEL',FALSE,NULL,NULL,TRUE,'{"maxAttempts":5,"initialDelayMs":1000}','flyway'),
 ('40000000-0000-0000-0000-000000000002','TRACKIT','TELEMATICS',FALSE,NULL,NULL,TRUE,'{"maxAttempts":5,"initialDelayMs":1000}','flyway'),
 ('40000000-0000-0000-0000-000000000003','MQTT','MQTT',TRUE,NULL,8883,TRUE,'{"maxAttempts":10,"initialDelayMs":1000}','flyway'),
 ('40000000-0000-0000-0000-000000000004','RABBITMQ','RABBITMQ',TRUE,NULL,5671,TRUE,'{"maxAttempts":10,"initialDelayMs":1000}','flyway')
ON CONFLICT (integration_code) DO NOTHING;
