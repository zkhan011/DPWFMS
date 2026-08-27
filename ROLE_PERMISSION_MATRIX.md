# Role and permission matrix

The following is the exact default grant matrix seeded by Flyway. Custom roles may use any subset. Plant Manager grants are additionally restricted by `user_plant_assignments` in plant-scoped queries.

| Role | Exact granted permissions |
|---|---|
| SUPER_ADMIN | All permissions listed below |
| SYSTEM_ADMIN | dashboard.read, plant.read, map.read, map.configure, vehicle.read, control_center.read, integration.read, integration.manage, user.read, user.manage, role.read, audit.read, system.configure |
| FLEET_MANAGER | dashboard.read, plant.read, map.read, vehicle.read, vehicle.manage, vehicle.enable, vehicle.disable, order.read, order.create, order.assign, order.cancel, order.retry, dispatch.read, dispatch.execute, dispatch.override, parking.read, parking.manage, fueling.read, fueling.manage, charging.read, charging.manage, alert.read, report.read, report.export |
| DISPATCHER | dashboard.read, plant.read, map.read, vehicle.read, order.read, order.create, order.assign, order.cancel, order.retry, dispatch.read, dispatch.execute, dispatch.override, parking.read, parking.manage, fueling.read, charging.read, alert.read |
| CONTROL_ROOM_OPERATOR | dashboard.read, plant.read, map.read, vehicle.read, order.read, dispatch.read, parking.read, fueling.read, charging.read, alert.read, alert.acknowledge, control_center.read |
| PLANT_MANAGER | dashboard.read, plant.read, plant.manage, map.read, vehicle.read, vehicle.manage, order.read, order.create, order.assign, order.cancel, dispatch.read, dispatch.execute, parking.read, parking.manage, fueling.read, fueling.manage, charging.read, charging.manage, alert.read, alert.acknowledge, report.read, report.export |
| FUEL_OPERATOR | dashboard.read, plant.read, map.read, vehicle.read, fueling.read, fueling.manage, alert.read, alert.acknowledge |
| CHARGING_OPERATOR | dashboard.read, plant.read, map.read, vehicle.read, charging.read, charging.manage, alert.read, alert.acknowledge |
| MAINTENANCE_OPERATOR | dashboard.read, plant.read, vehicle.read, vehicle.manage, alert.read, alert.acknowledge |
| SAFETY_OFFICER | dashboard.read, plant.read, map.read, vehicle.read, alert.read, alert.acknowledge, alert.resolve, report.read, audit.read |
| REPORT_VIEWER | dashboard.read, plant.read, map.read, vehicle.read, order.read, alert.read, report.read, report.export |
| AUDITOR | dashboard.read, plant.read, integration.read, user.read, role.read, audit.read |
| API_SERVICE | plant.read, map.read, vehicle.read, vehicle.manage, order.read, order.create, dispatch.read, alert.read, integration.read |
| DRIVER_OR_VEHICLE_CLIENT | vehicle.read, order.read |

The complete permission catalog is: dashboard.read, plant.read, plant.manage, map.read, map.configure, vehicle.read, vehicle.manage, vehicle.enable, vehicle.disable, order.read, order.create, order.assign, order.cancel, order.retry, dispatch.read, dispatch.execute, dispatch.override, parking.read, parking.manage, fueling.read, fueling.manage, charging.read, charging.manage, alert.read, alert.acknowledge, alert.resolve, report.read, report.export, control_center.read, control_center.operate, integration.read, integration.manage, user.read, user.manage, role.read, role.manage, audit.read, system.configure.

The last enabled SUPER_ADMIN and its protected grants must not be removed. Enterprise DPWUM groups should map to local roles; API_SERVICE accounts are non-interactive.

Users can be created from **Administration → Users & access** by an account with `user.manage`. The backend hashes the submitted password with BCrypt, validates every selected role and plant, denies protected-role assignment without `system.configure`, and writes an audit event. Newly created interactive users can sign in immediately with their database username and password.

## Map access

Opening the map requires `map.read`. Displaying telemetry vehicles additionally requires `vehicle.read`, and non-system users must have a row in `user_plant_assignments` for each visible plant. Editing the provider requires `map.configure`.

To grant map viewing to an existing custom role, run the following as the database owner. This is idempotent:

```sql
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'YOUR_CUSTOM_ROLE' AND p.code = 'map.read'
ON CONFLICT DO NOTHING;
```

Assign a user to Jebel Ali so its vehicles are returned by plant-scoped APIs:

```sql
INSERT INTO user_plant_assignments(user_id, plant_id, assigned_by)
SELECT u.id, p.id, 'administrator' FROM users u CROSS JOIN plants p
WHERE u.username = 'USERNAME' AND p.code = 'JEA'
ON CONFLICT DO NOTHING;
```

## Opt-in development users

With the `dev` Spring profile, set `DPWFMS_SAMPLE_USERS_ENABLED=true` and provide `DPWFMS_SAMPLE_USER_PASSWORD` with at least 12 characters. At startup DPW FMS inserts `dispatcher.demo`, `operator.demo`, and `viewer.demo` into PostgreSQL, assigns their standard roles, and grants every enabled plant. Existing accounts are not overwritten. This seeder cannot run under the `prod` profile.
