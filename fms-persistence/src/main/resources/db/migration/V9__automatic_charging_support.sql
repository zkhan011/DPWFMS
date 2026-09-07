ALTER TABLE assets ADD COLUMN IF NOT EXISTS energy_source VARCHAR(30) NOT NULL DEFAULT 'DIESEL';

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_charging_job_asset
  ON jobs(assigned_asset_id)
  WHERE job_type='CHARGING'
    AND status NOT IN ('COMPLETED','CANCELLED','FAILED','REJECTED','EXPIRED');

INSERT INTO locations(id,code,name,kind,latitude,longitude)
VALUES ('73000000-0000-0000-0000-000000000001','JEA-CHARGE-01','Jebel Ali Charging Station','CHARGING_STATION',24.9915,55.0350)
ON CONFLICT (code) DO NOTHING;

INSERT INTO service_stations(id,location_id,station_type,status,service_type,operating_hours)
SELECT '73000000-0000-0000-0000-000000000002',l.id,'CHARGING','AVAILABLE','ELECTRIC','{"alwaysOpen":true}'::jsonb
FROM locations l WHERE l.code='JEA-CHARGE-01'
ON CONFLICT (id) DO NOTHING;

INSERT INTO service_bays(id,station_id,code,status)
SELECT '73000000-0000-0000-0000-000000000003',s.id,'JEA-CHARGE-BAY-01','AVAILABLE'
FROM service_stations s JOIN locations l ON l.id=s.location_id WHERE l.code='JEA-CHARGE-01'
ON CONFLICT (code) DO NOTHING;
