CREATE TABLE automation_rules (
  id UUID PRIMARY KEY,
  rule_code VARCHAR(100) NOT NULL,
  rule_name VARCHAR(160) NOT NULL,
  description TEXT,
  rule_kind VARCHAR(30) NOT NULL CHECK (rule_kind IN ('PARKING', 'FUELING')),
  scope_type VARCHAR(40) NOT NULL CHECK (scope_type IN ('GLOBAL','TERMINAL','OPERATIONAL_ZONE','ASSET_TYPE','ASSET_GROUP','ASSET')),
  scope_identifier VARCHAR(160),
  enabled BOOLEAN NOT NULL,
  priority INTEGER NOT NULL,
  effective_from TIMESTAMPTZ,
  effective_to TIMESTAMPTZ,
  thresholds JSONB NOT NULL DEFAULT '{}',
  scoring_weights JSONB NOT NULL DEFAULT '{}',
  suppression_seconds BIGINT NOT NULL DEFAULT 0 CHECK (suppression_seconds >= 0),
  cooldown_seconds BIGINT NOT NULL DEFAULT 0 CHECK (cooldown_seconds >= 0),
  retry_policy JSONB NOT NULL DEFAULT '{}',
  maximum_waiting_seconds BIGINT NOT NULL DEFAULT 900,
  version INTEGER NOT NULL CHECK (version > 0),
  created_by VARCHAR(160) NOT NULL,
  approved_by VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT chk_rule_scope CHECK ((scope_type = 'GLOBAL' AND scope_identifier IS NULL) OR (scope_type <> 'GLOBAL' AND scope_identifier IS NOT NULL)),
  CONSTRAINT chk_rule_effective_period CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
  UNIQUE (rule_code, scope_type, scope_identifier, version)
);
CREATE INDEX idx_automation_rule_resolution ON automation_rules(rule_kind, enabled, scope_type, scope_identifier, effective_from, effective_to);

CREATE TABLE automation_decisions (
  id UUID PRIMARY KEY,
  asset_id UUID NOT NULL REFERENCES assets(id),
  idempotency_key VARCHAR(300),
  evaluated_at TIMESTAMPTZ NOT NULL,
  trigger_type VARCHAR(80) NOT NULL,
  input_snapshot JSONB NOT NULL,
  rules_evaluated JSONB NOT NULL,
  rules_matched JSONB NOT NULL,
  eligible BOOLEAN NOT NULL,
  blocking_reasons JSONB NOT NULL,
  candidate_scores JSONB NOT NULL,
  selected_action VARCHAR(50) NOT NULL,
  selected_resource_id VARCHAR(160),
  job_id UUID REFERENCES jobs(id),
  reservation_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_automation_decision_idempotency ON automation_decisions(idempotency_key) WHERE idempotency_key IS NOT NULL AND job_id IS NOT NULL;
CREATE INDEX idx_automation_decision_asset_time ON automation_decisions(asset_id, evaluated_at DESC);
CREATE INDEX idx_automation_decision_failures ON automation_decisions(evaluated_at DESC) WHERE eligible = FALSE;

ALTER TABLE resource_reservations
  ADD COLUMN asset_id UUID REFERENCES assets(id),
  ADD COLUMN idempotency_key VARCHAR(300),
  ADD COLUMN valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uq_reservation_idempotency ON resource_reservations(idempotency_key) WHERE idempotency_key IS NOT NULL;

ALTER TABLE jobs
  ADD COLUMN automation_rule_id UUID REFERENCES automation_rules(id),
  ADD COLUMN automation_decision_id UUID REFERENCES automation_decisions(id),
  ADD COLUMN idempotency_key VARCHAR(300),
  ADD COLUMN parent_job_id UUID REFERENCES jobs(id),
  ADD COLUMN dependency_status VARCHAR(40);
CREATE UNIQUE INDEX uq_job_automation_idempotency ON jobs(idempotency_key) WHERE idempotency_key IS NOT NULL;
ALTER TABLE automation_decisions ADD CONSTRAINT fk_automation_decision_reservation FOREIGN KEY (reservation_id) REFERENCES resource_reservations(id);

CREATE TABLE automation_alerts (
  id UUID PRIMARY KEY,
  asset_id UUID NOT NULL REFERENCES assets(id),
  decision_id UUID REFERENCES automation_decisions(id),
  alert_code VARCHAR(100) NOT NULL,
  severity VARCHAR(20) NOT NULL CHECK (severity IN ('WARNING','MAJOR','CRITICAL')),
  message TEXT NOT NULL,
  first_raised_at TIMESTAMPTZ NOT NULL,
  last_raised_at TIMESTAMPTZ NOT NULL,
  resolved_at TIMESTAMPTZ,
  occurrences INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX uq_unresolved_automation_alert ON automation_alerts(asset_id, alert_code) WHERE resolved_at IS NULL;

CREATE TABLE fuel_transactions (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES jobs(id),
  asset_id UUID NOT NULL REFERENCES assets(id),
  station_id UUID NOT NULL REFERENCES service_stations(id),
  bay_id UUID NOT NULL REFERENCES service_bays(id),
  fuel_type VARCHAR(50) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  quantity_dispensed NUMERIC(12,3),
  starting_fuel_percent NUMERIC(5,2),
  ending_fuel_percent NUMERIC(5,2),
  transaction_reference VARCHAR(160) UNIQUE NOT NULL,
  reconciliation_status VARCHAR(40) NOT NULL
);

INSERT INTO automation_rules(id, rule_code, rule_name, description, rule_kind, scope_type,
 enabled, priority, effective_from, thresholds, scoring_weights, suppression_seconds,
 cooldown_seconds, maximum_waiting_seconds, version, created_by, approved_by, created_at, updated_at)
VALUES
('10000000-0000-0000-0000-000000000001', 'AUTO_PARK_IDLE', 'Idle asset parking',
 'Parks an eligible asset after the idle and next-job suppression windows', 'PARKING', 'GLOBAL',
 TRUE, 100, now(),
 '{"telemetryFreshnessSeconds":60,"idleSeconds":300,"safetyReservePercent":10,"reservationSeconds":600}',
 '{"distance":1,"travelTime":1,"congestion":1,"nextJob":0.8,"zone":0.5,"maneuver":0.7,"occupancy":0.6,"preference":1}',
 600, 300, 900, 1, 'flyway', 'system', now(), now()),
('10000000-0000-0000-0000-000000000002', 'AUTO_FUEL_LEVEL', 'Fuel-level automation',
 'Classifies fuel with hysteresis and selects the lowest safe operational station cost', 'FUELING', 'GLOBAL',
 TRUE, 200, now(),
 '{"telemetryFreshnessSeconds":60,"lowFuelPercent":35,"criticalFuelPercent":20,"emergencyFuelPercent":10,"hysteresisPercent":3,"safetyReservePercent":5,"reservationSeconds":900}',
 '{"travelTime":1,"queueWait":1.2,"congestion":0.8,"routeDeviation":0.7,"serviceTime":0.5,"stationPriority":0.7,"nextJob":0.5,"risk":2}',
 0, 1800, 900, 1, 'flyway', 'system', now(), now());

INSERT INTO scheduler_configuration(config_key, config_value, updated_at, updated_by) VALUES
('automation.evaluation.interval', '{"seconds":30}', now(), 'flyway'),
('automation.alert.repeat-suppression', '{"seconds":600}', now(), 'flyway'),
('automation.terminal-timezone', '{"zoneId":"Asia/Dubai"}', now(), 'flyway')
ON CONFLICT (config_key) DO NOTHING;
