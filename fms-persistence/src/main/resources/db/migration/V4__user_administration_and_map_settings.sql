ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by VARCHAR(160) NOT NULL DEFAULT 'migration';
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

ALTER TABLE map_configurations ADD COLUMN IF NOT EXISTS connectivity_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE map_configurations ADD COLUMN IF NOT EXISTS connectivity_checked_at TIMESTAMPTZ;
ALTER TABLE map_configurations ADD COLUMN IF NOT EXISTS connectivity_message VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_users_enabled_username ON users(enabled, username);
CREATE INDEX IF NOT EXISTS idx_user_plant_assignments_plant ON user_plant_assignments(plant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_service_stations_type_status ON service_stations(station_type, status);

UPDATE users SET username = subject WHERE username IS NULL;
