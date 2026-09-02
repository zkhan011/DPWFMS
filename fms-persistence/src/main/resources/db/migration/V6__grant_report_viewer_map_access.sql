-- Report viewers may open the operational map. Vehicle and plant visibility remains
-- constrained by their existing read grants and user_plant_assignments.
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'map.read'
WHERE r.name = 'REPORT_VIEWER'
ON CONFLICT DO NOTHING;
