-- Fixed demonstration identities are deliberately disabled and have no password.
-- The dev-profile seeder activates them only when an operator explicitly supplies
-- DPWFMS_SAMPLE_USER_PASSWORD. Production databases therefore receive no usable
-- shared credential.
INSERT INTO users(id, subject, username, display_name, password_hash, enabled, service_account,
                  created_at, created_by, updated_at)
VALUES
 ('71000000-0000-0000-0000-000000000001','sample:admin.demo','admin.demo','Demo Administrator',NULL,FALSE,FALSE,now(),'flyway-sample',now()),
 ('71000000-0000-0000-0000-000000000002','sample:dispatcher.demo','dispatcher.demo','Demo Dispatcher',NULL,FALSE,FALSE,now(),'flyway-sample',now()),
 ('71000000-0000-0000-0000-000000000003','sample:operator.demo','operator.demo','Demo Control Room Operator',NULL,FALSE,FALSE,now(),'flyway-sample',now()),
 ('71000000-0000-0000-0000-000000000004','sample:viewer.demo','viewer.demo','Demo Map Viewer',NULL,FALSE,FALSE,now(),'flyway-sample',now())
ON CONFLICT DO NOTHING;

-- These fixed users intentionally receive the all-permissions role for acceptance
-- testing. They remain unusable until explicitly activated in the dev profile.
INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.created_by='flyway-sample'
  AND u.username IN ('admin.demo','dispatcher.demo','operator.demo','viewer.demo')
  AND r.name='SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_plant_assignments(user_id, plant_id, assigned_by)
SELECT u.id, p.id, 'flyway-sample' FROM users u CROSS JOIN plants p
WHERE u.created_by='flyway-sample'
  AND u.username IN ('admin.demo','dispatcher.demo','operator.demo','viewer.demo')
ON CONFLICT DO NOTHING;
