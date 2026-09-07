-- Additive operational assignment model. Existing parking/service inventory remains authoritative.
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS code VARCHAR(60);
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS name VARCHAR(160);
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS plant_id UUID REFERENCES plants(id);
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS allowed_asset_types JSONB NOT NULL DEFAULT '[]';
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS temporarily_excluded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE parking_zones ADD COLUMN IF NOT EXISTS boundary JSONB;
CREATE UNIQUE INDEX IF NOT EXISTS uq_parking_zone_code ON parking_zones(code) WHERE code IS NOT NULL;

ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS routing_node_id UUID REFERENCES map_nodes(id);
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS max_width_m DOUBLE PRECISION;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS max_weight_tonnes DOUBLE PRECISION;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS current_asset_id UUID REFERENCES assets(id);
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS reservation_expiry TIMESTAMPTZ;
ALTER TABLE parking_spaces ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE parking_spaces SET status='RESERVED' WHERE reserved AND status='AVAILABLE';

CREATE TABLE parking_assignments (
 id UUID PRIMARY KEY, asset_id UUID NOT NULL REFERENCES assets(id), parking_space_id UUID NOT NULL REFERENCES parking_spaces(id),
 job_id UUID REFERENCES jobs(id), status VARCHAR(30) NOT NULL CHECK(status IN ('PROPOSED','ASSIGNED','EN_ROUTE','ARRIVED','COMPLETED','CANCELLED','EXPIRED','FAILED')),
 assignment_mode VARCHAR(30) NOT NULL CHECK(assignment_mode IN ('AUTOMATIC','SUGGESTED','MANUAL')),
 score DOUBLE PRECISION NOT NULL, score_breakdown JSONB NOT NULL DEFAULT '{}', rejected_candidates JSONB NOT NULL DEFAULT '[]', reason TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), assigned_at TIMESTAMPTZ, arrived_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
 expires_at TIMESTAMPTZ, created_by VARCHAR(160) NOT NULL, override_reason TEXT, parameter_version BIGINT,
 correlation_id VARCHAR(100), idempotency_key VARCHAR(160), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_parking_manual_reason CHECK(assignment_mode <> 'MANUAL' OR override_reason IS NOT NULL)
);
CREATE UNIQUE INDEX uq_active_parking_asset ON parking_assignments(asset_id) WHERE status IN ('PROPOSED','ASSIGNED','EN_ROUTE','ARRIVED');
CREATE UNIQUE INDEX uq_active_parking_space ON parking_assignments(parking_space_id) WHERE status IN ('ASSIGNED','EN_ROUTE','ARRIVED');
CREATE UNIQUE INDEX uq_parking_idempotency ON parking_assignments(idempotency_key) WHERE idempotency_key IS NOT NULL;

ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS code VARCHAR(60);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS name VARCHAR(160);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS plant_id UUID REFERENCES plants(id);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS routing_node_id UUID REFERENCES map_nodes(id);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS supported_asset_types JSONB NOT NULL DEFAULT '[]';
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS supported_connector_types JSONB NOT NULL DEFAULT '[]';
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS simultaneous_capacity INTEGER NOT NULL DEFAULT 1 CHECK(simultaneous_capacity >= 1);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS maximum_queue_size INTEGER NOT NULL DEFAULT 0 CHECK(maximum_queue_size >= 0);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS operating_start TIME;
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS operating_end TIME;
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS minimum_battery_threshold NUMERIC(5,2) NOT NULL DEFAULT 20 CHECK(minimum_battery_threshold BETWEEN 0 AND 100);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS critical_battery_threshold NUMERIC(5,2) NOT NULL DEFAULT 10 CHECK(critical_battery_threshold BETWEEN 0 AND 100);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS target_battery_percentage NUMERIC(5,2) NOT NULL DEFAULT 80 CHECK(target_battery_percentage BETWEEN 0 AND 100);
ALTER TABLE service_stations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_service_station_code ON service_stations(code) WHERE code IS NOT NULL;
UPDATE service_stations s SET code=l.code,name=l.name,plant_id=(SELECT id FROM plants WHERE code='JEA') FROM locations l WHERE s.location_id=l.id AND s.code IS NULL;

ALTER TABLE service_bays ADD COLUMN IF NOT EXISTS connector_type VARCHAR(40);
ALTER TABLE service_bays ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE service_bays ADD COLUMN IF NOT EXISTS current_asset_id UUID REFERENCES assets(id);
ALTER TABLE service_bays ADD COLUMN IF NOT EXISTS reservation_expiry TIMESTAMPTZ;
ALTER TABLE service_bays ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE charging_assignments (
 id UUID PRIMARY KEY, asset_id UUID NOT NULL REFERENCES assets(id), station_id UUID NOT NULL REFERENCES service_stations(id), slot_id UUID REFERENCES service_bays(id),
 job_id UUID REFERENCES jobs(id), status VARCHAR(30) NOT NULL CHECK(status IN ('PROPOSED','QUEUED','ASSIGNED','EN_ROUTE','ARRIVED','CHARGING','COMPLETED','CANCELLED','EXPIRED','FAILED')),
 assignment_mode VARCHAR(30) NOT NULL CHECK(assignment_mode IN ('AUTOMATIC','SUGGESTED','MANUAL')), queue_position INTEGER,
 battery_at_assignment NUMERIC(5,2) NOT NULL, target_battery_percentage NUMERIC(5,2) NOT NULL, priority INTEGER NOT NULL,
 score DOUBLE PRECISION NOT NULL, score_breakdown JSONB NOT NULL DEFAULT '{}', rejected_candidates JSONB NOT NULL DEFAULT '[]', reason TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), assigned_at TIMESTAMPTZ, arrived_at TIMESTAMPTZ, charging_started_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, created_by VARCHAR(160) NOT NULL, override_reason TEXT,
 parameter_version BIGINT, correlation_id VARCHAR(100), idempotency_key VARCHAR(160), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_charging_manual_reason CHECK(assignment_mode <> 'MANUAL' OR override_reason IS NOT NULL)
);
CREATE UNIQUE INDEX uq_active_charging_assignment_asset ON charging_assignments(asset_id) WHERE status IN ('PROPOSED','QUEUED','ASSIGNED','EN_ROUTE','ARRIVED','CHARGING');
CREATE UNIQUE INDEX uq_active_charging_slot ON charging_assignments(slot_id) WHERE slot_id IS NOT NULL AND status IN ('ASSIGNED','EN_ROUTE','ARRIVED','CHARGING');
CREATE UNIQUE INDEX uq_charging_idempotency ON charging_assignments(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE operational_parameter_versions (
 version BIGSERIAL PRIMARY KEY, parameters JSONB NOT NULL, reason TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(160) NOT NULL, rolled_back_from BIGINT
);
CREATE TABLE assignment_exceptions (
 id UUID PRIMARY KEY, assignment_type VARCHAR(20) NOT NULL, asset_id UUID REFERENCES assets(id), code VARCHAR(80) NOT NULL,
 detail TEXT NOT NULL, context JSONB NOT NULL DEFAULT '{}', status VARCHAR(20) NOT NULL DEFAULT 'OPEN', created_at TIMESTAMPTZ NOT NULL DEFAULT now(), resolved_at TIMESTAMPTZ, resolved_by VARCHAR(160)
);

INSERT INTO operational_parameter_versions(parameters,reason,created_by) SELECT '{
 "parkingAutomationEnabled":true,"parkingOperatingMode":"SUGGEST_ONLY","eligibleIdleDurationSeconds":300,
 "parkingAssignmentScanIntervalSeconds":30,"parkingReservationTimeoutSeconds":900,"parkingArrivalRadiusMeters":15,
 "maximumParkingDistanceMeters":5000,"maximumConcurrentParkingJobs":20,"staleTelemetryThresholdSeconds":60,
 "minimumFuelPercentageForParking":15,"minimumEnergyPercentageForParking":20,"parkingDistanceWeight":1,
 "nextJobDistanceWeight":0.5,"parkingCongestionWeight":0.5,"parkingUtilizationWeight":0.5,"parkingEnergyWeight":0.5,
 "preferredZoneBonus":0.2,"reassignParkingOnTimeout":true,"maximumParkingReassignmentAttempts":2,
 "allowCrossZoneAssignment":false,"requireParkingRouteAvailability":false,"parkingEligibleAssetTypes":["ITV"],
 "chargingAutomationEnabled":true,"chargingOperatingMode":"AUTOMATIC","lowBatteryThresholdPercentage":20,
 "criticalBatteryThresholdPercentage":10,"defaultTargetBatteryPercentage":80,"chargingAssignmentScanIntervalSeconds":30,
 "chargingReservationTimeoutSeconds":900,"maximumChargingDistanceMeters":5000,"maximumConcurrentChargingJobs":20,
 "defaultMaximumQueueSize":5,"chargingArrivalRadiusMeters":15,"maximumChargingReassignmentAttempts":2,
 "reassignChargingOnTimeout":true,"requireChargingRouteAvailability":false,"chargingEligibleAssetTypes":["ITV"],
 "chargingDistanceWeight":1,"chargingQueueWeight":1,"chargingUtilizationWeight":1,"chargingEnergyRiskWeight":1,
 "criticalBatteryBonus":1,"stationPriorityBonus":0.2,"mapRefreshSeconds":5,"alertCriticalAfterSeconds":120
}'::jsonb,'Initial safe operational defaults','flyway' WHERE NOT EXISTS (SELECT 1 FROM operational_parameter_versions);

INSERT INTO permissions(id,code,description) SELECT gen_random_uuid(),code,replace(code,'.',' ') FROM unnest(ARRAY[
 'parking.assign','parking.override','parking.bay.manage','parking.automation.run','charging.assign','charging.override',
 'charging.station.manage','charging.automation.run','parameters.read','parameters.edit','parameters.rollback'
]) AS permission_code(code) ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
 WHERE r.name='SUPER_ADMIN' AND p.code IN ('parking.assign','parking.override','parking.bay.manage','parking.automation.run','charging.assign','charging.override','charging.station.manage','charging.automation.run','parameters.read','parameters.edit','parameters.rollback') ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r JOIN permissions p ON
 (r.name='FLEET_MANAGER' AND p.code IN ('parking.assign','parking.override','parking.bay.manage','parking.automation.run','charging.assign','charging.override','charging.station.manage','charging.automation.run','parameters.read','parameters.edit')) OR
 (r.name='DISPATCHER' AND p.code IN ('parking.assign','parking.override','parking.automation.run','charging.assign','charging.override','charging.automation.run','parameters.read')) OR
 (r.name='CONTROL_ROOM_OPERATOR' AND p.code IN ('parking.assign','parking.automation.run','charging.assign','charging.automation.run','parameters.read')) OR
 (r.name='MAINTENANCE_OPERATOR' AND p.code IN ('parking.bay.manage','charging.station.manage','parameters.read')) OR
 (r.name IN ('REPORT_VIEWER','AUDITOR') AND p.code='parameters.read') ON CONFLICT DO NOTHING;

INSERT INTO locations(id,code,name,kind,latitude,longitude) VALUES ('74000000-0000-0000-0000-000000000001','JEA-PARK-01','Jebel Ali ITV Parking','PARKING_ZONE',24.989,55.031) ON CONFLICT(code) DO NOTHING;
INSERT INTO parking_zones(id,location_id,capacity,code,name,plant_id,priority,boundary)
SELECT '74000000-0000-0000-0000-000000000002',l.id,3,'JEA-PARK-01','Jebel Ali ITV Parking',p.id,10,
 '{"type":"Polygon","coordinates":[[[55.0305,24.9885],[55.0315,24.9885],[55.0315,24.9895],[55.0305,24.9895],[55.0305,24.9885]]]}'::jsonb
FROM locations l CROSS JOIN plants p WHERE l.code='JEA-PARK-01' AND p.code='JEA' ON CONFLICT(id) DO NOTHING;
INSERT INTO parking_spaces(id,parking_zone_id,code,status,supported_asset_types,max_length_m,max_width_m,max_weight_tonnes,latitude,longitude,priority)
SELECT gen_random_uuid(),z.id,'JEA-PARK-'||n,'AVAILABLE','["ITV"]',9,3.5,50,24.989,55.0307+n*.0002,n*10
FROM parking_zones z CROSS JOIN generate_series(1,3) AS series(n) WHERE z.code='JEA-PARK-01' ON CONFLICT(code) DO NOTHING;
UPDATE service_stations SET code=COALESCE(code,'JEA-CHARGE-01'),name=COALESCE(name,'Jebel Ali Charging Station'),plant_id=COALESCE(plant_id,(SELECT id FROM plants WHERE code='JEA')),
 supported_asset_types='["ITV"]',supported_connector_types='["CCS2"]',simultaneous_capacity=1,maximum_queue_size=5 WHERE station_type='CHARGING';
UPDATE service_bays SET connector_type='CCS2' WHERE station_id IN (SELECT id FROM service_stations WHERE station_type='CHARGING');
