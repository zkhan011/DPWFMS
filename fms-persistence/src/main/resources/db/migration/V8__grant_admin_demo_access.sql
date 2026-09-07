-- Permissions are granted through roles, never directly to users. This dedicated
-- role makes the requested admin.demo access explicit and independently auditable.
INSERT INTO roles(id, name, description, protected_role)
VALUES ('72000000-0000-0000-0000-000000000001', 'ADMIN_DEMO_ACCESS',
        'Explicit map and security-directory read access for admin.demo', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('map.read', 'user.read', 'role.read')
WHERE r.name = 'ADMIN_DEMO_ACCESS'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE lower(u.username) = 'admin.demo'
  AND r.name = 'ADMIN_DEMO_ACCESS'
ON CONFLICT DO NOTHING;
