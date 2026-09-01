package com.dpworld.fms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
  private final JdbcTemplate jdbc;

  public WorkspaceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping("/me")
  public Map<String, Object> currentUser(Authentication authentication) {
    String role = authentication.getAuthorities().stream().map(Object::toString)
        .filter(authority -> authority.startsWith("ROLE_")).map(authority -> authority.substring(5))
        .findFirst().orElse("CUSTOM_ROLE");
    return Map.of("username", authentication.getName(), "role", role,
        "permissions", authentication.getAuthorities().stream().map(Object::toString).sorted().toList());
  }

  @GetMapping("/overview")
  @PreAuthorize("hasAuthority('dashboard.read')")
  public Map<String, Object> overview() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("kernelStatus", "CONFIGURATION_REQUIRED");
    result.put("operatingMode", "OPERATING");
    result.put("registeredVehicles", count("SELECT count(*) FROM assets"));
    result.put("totalFleet", count("SELECT count(*) FROM assets"));
    result.put("fleetInOperation", count("SELECT count(*) FROM assets WHERE enabled AND operational_status IN ('WORKING','FUELLING','CHARGING')"));
    result.put("activeVehicles", count("SELECT count(*) FROM assets WHERE operational_status <> 'OFFLINE' AND enabled"));
    result.put("idleVehicles", count("SELECT count(*) FROM assets WHERE operational_status = 'IDLE' AND enabled"));
    result.put("availableVehicles", count("SELECT count(*) FROM assets WHERE availability_status='AVAILABLE'"));
    result.put("reservedVehicles", count("SELECT count(*) FROM assets WHERE availability_status='RESERVED'"));
    result.put("assignedVehicles", count("SELECT count(*) FROM assets WHERE availability_status='ASSIGNED'"));
    result.put("parkedVehicles", count("SELECT count(*) FROM assets WHERE operational_status='PARKED'"));
    result.put("chargingVehicles", count("SELECT count(*) FROM assets WHERE operational_status='CHARGING'"));
    result.put("waitingForCharging", count("SELECT count(*) FROM charging_assignments WHERE status='QUEUED'"));
    result.put("fuelingVehicles", count("SELECT count(*) FROM assets WHERE operational_status='FUELLING'"));
    result.put("maintenanceVehicles", count("SELECT count(*) FROM assets WHERE operational_status='MAINTENANCE'"));
    result.put("staleTelemetry", count("SELECT count(*) FROM assets WHERE last_telemetry_at IS NULL OR last_telemetry_at < now()-interval '60 seconds'"));
    result.put("offlineVehicles", count("SELECT count(*) FROM assets WHERE operational_status = 'OFFLINE'"));
    result.put("activeOrders", count("SELECT count(*) FROM transport_orders WHERE status NOT IN ('COMPLETED','CANCELLED','FAILED')"));
    result.put("failedOrders", count("SELECT count(*) FROM transport_orders WHERE status = 'FAILED'"));
    result.put("activeAlerts", count("SELECT count(*) FROM alerts WHERE acknowledged_at IS NULL"));
    result.put("criticalAlerts", count("SELECT count(*) FROM alerts WHERE acknowledged_at IS NULL AND severity IN ('CRITICAL','MAJOR')"));
    result.put("activeJobs", count("SELECT count(*) FROM jobs WHERE status NOT IN ('COMPLETED','CANCELLED','FAILED','REJECTED','EXPIRED')"));
    result.put("failedJobs", count("SELECT count(*) FROM jobs WHERE status='FAILED'"));
    result.put("parkingOccupied", count("SELECT count(*) FROM parking_spaces WHERE status='OCCUPIED'"));
    result.put("parkingCapacity", count("SELECT count(*) FROM parking_spaces WHERE active"));
    result.put("chargingOccupied", count("SELECT count(*) FROM charging_assignments WHERE status IN ('ASSIGNED','EN_ROUTE','ARRIVED','CHARGING')"));
    result.put("chargingCapacity", count("SELECT coalesce(sum(simultaneous_capacity),0) FROM service_stations WHERE station_type='CHARGING' AND active"));
    result.put("lastTelemetryAt", jdbc.queryForObject("SELECT max(last_telemetry_at) FROM assets", java.sql.Timestamp.class));
    result.put("fuelStations", count("SELECT count(*) FROM service_stations WHERE station_type IN ('FUELING','FUEL')"));
    result.put("chargingStations", count("SELECT count(*) FROM service_stations WHERE station_type IN ('CHARGING','CHARGE')"));
    result.put("totalDistanceKm", decimal("SELECT coalesce(sum(odometer_km),0) FROM assets"));
    result.put("averageUtilization", 0);
    result.put("generatedAt", Instant.now());
    return result;
  }

  @GetMapping("/plants")
  @PreAuthorize("hasAuthority('plant.read')")
  public List<Map<String, Object>> plants(Authentication authentication) {
    String base = """
        SELECT p.id, p.code, p.name, p.location, p.latitude, p.longitude, p.timezone,
               p.status, p.enabled, count(DISTINCT a.id) AS total_vehicles,
               count(DISTINCT a.id) FILTER (WHERE a.operational_status <> 'OFFLINE') AS active_vehicles,
               count(DISTINCT o.id) FILTER (WHERE o.status NOT IN ('COMPLETED','CANCELLED','FAILED')) AS active_orders
          FROM plants p LEFT JOIN assets a ON a.plant_id=p.id
          LEFT JOIN transport_orders o ON o.plant_id=p.id
        """;
    if (hasAuthority(authentication, "system.configure")) {
      return jdbc.queryForList(base + " GROUP BY p.id ORDER BY p.name");
    }
    return jdbc.queryForList(base + " WHERE p.id IN (SELECT upa.plant_id FROM user_plant_assignments upa JOIN users u ON u.id=upa.user_id WHERE u.username=? OR u.subject=?) GROUP BY p.id ORDER BY p.name",
        authentication.getName(), authentication.getName());
  }

  @PostMapping("/plants")
  @PreAuthorize("hasAuthority('system.configure')")
  public Map<String, Object> createPlant(@Valid @RequestBody PlantRequest request, Principal actor) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO plants(id,code,name,location,latitude,longitude,timezone,status,updated_at) VALUES (?,?,?,?,?,?,?,'OPERATIONAL',now())",
        id, request.code(), request.name(), request.location(), request.latitude(), request.longitude(), request.timezone());
    audit(actor.getName(), "PLANT_CREATED", id.toString(), request.code());
    return jdbc.queryForMap("SELECT * FROM plants WHERE id=?", id);
  }

  @PatchMapping("/plants/{id}/enabled")
  @PreAuthorize("hasAuthority('plant.manage')")
  public void setPlantEnabled(@PathVariable("id") UUID id, @RequestParam("value") boolean value, Authentication authentication) {
    requirePlantAccess(authentication, id);
    if (jdbc.update("UPDATE plants SET enabled=?, status=CASE WHEN ? THEN 'OPERATIONAL' ELSE 'INACTIVE' END, version=version+1, updated_at=now() WHERE id=?", value, value, id) != 1) {
      throw new IllegalArgumentException("unknown plant " + id);
    }
    audit(authentication.getName(), value ? "PLANT_ACTIVATED" : "PLANT_DEACTIVATED", id.toString(), null);
  }

  @GetMapping("/vehicles")
  @PreAuthorize("hasAuthority('vehicle.read')")
  public List<Map<String, Object>> vehicles(@RequestParam(name = "plantId", required = false) UUID plantId, Authentication authentication) {
    String sql = """
        SELECT a.id, a.fleet_number, t.code AS asset_type, p.name AS plant,
               a.latitude, a.longitude, a.heading, a.speed_kph, a.operational_status, a.availability_status,
               a.energy_percent, a.energy_source, a.current_job_id, a.driver_id, a.device_id, a.trackit_id, a.last_telemetry_at, a.enabled
          FROM assets a JOIN asset_types t ON t.id=a.asset_type_id
          LEFT JOIN plants p ON p.id=a.plant_id
        """;
    if (plantId != null) {
      requirePlantAccess(authentication, plantId);
      return jdbc.queryForList(sql + " WHERE a.plant_id=? ORDER BY a.fleet_number", plantId);
    }
    if (hasAuthority(authentication, "system.configure")) return jdbc.queryForList(sql + " ORDER BY a.fleet_number");
    return jdbc.queryForList(sql + " WHERE a.plant_id IN (SELECT upa.plant_id FROM user_plant_assignments upa JOIN users u ON u.id=upa.user_id WHERE u.username=? OR u.subject=?) ORDER BY a.fleet_number",
        authentication.getName(), authentication.getName());
  }

  @GetMapping("/reports/fleet")
  @PreAuthorize("hasAuthority('report.read')")
  public Map<String, Object> fleetReport() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("generatedAt", Instant.now());
    report.put("total", count("SELECT count(*) FROM assets"));
    report.put("positioned", count("SELECT count(*) FROM assets WHERE latitude IS NOT NULL AND longitude IS NOT NULL"));
    report.put("stale", count("SELECT count(*) FROM assets WHERE last_telemetry_at IS NULL OR last_telemetry_at < now() - interval '60 seconds'"));
    report.put("lowEnergy", count("SELECT count(*) FROM assets WHERE energy_percent <= 20"));
    report.put("charging", count("SELECT count(*) FROM assets WHERE operational_status='CHARGING' OR current_job_id IN (SELECT id FROM jobs WHERE job_type='CHARGING' AND status NOT IN ('COMPLETED','CANCELLED','FAILED'))"));
    report.put("byStatus", jdbc.queryForList("SELECT operational_status AS label,count(*) AS value FROM assets GROUP BY operational_status ORDER BY operational_status"));
    report.put("byEnergySource", jdbc.queryForList("SELECT energy_source AS label,count(*) AS value FROM assets GROUP BY energy_source ORDER BY energy_source"));
    report.put("assets", jdbc.queryForList("""
        SELECT fleet_number,operational_status,availability_status,energy_percent,energy_source,
               latitude,longitude,last_telemetry_at,current_job_id
        FROM assets ORDER BY fleet_number LIMIT 1000
        """));
    return report;
  }

  @GetMapping("/map-operational-layers")
  @PreAuthorize("hasAuthority('map.read')")
  public Map<String, Object> mapOperationalLayers() {
    return Map.of(
        "parkingZones", jdbc.queryForList("SELECT z.id,z.code,z.name,z.boundary,l.latitude,l.longitude FROM parking_zones z JOIN locations l ON l.id=z.location_id WHERE z.active"),
        "parkingBays", jdbc.queryForList("SELECT s.id,s.code,s.status,s.latitude,s.longitude,s.current_asset_id,z.code zone_code FROM parking_spaces s JOIN parking_zones z ON z.id=s.parking_zone_id WHERE s.active"),
        "chargingStations", jdbc.queryForList("SELECT s.id,s.code,s.name,s.status,l.latitude,l.longitude,s.simultaneous_capacity FROM service_stations s JOIN locations l ON l.id=s.location_id WHERE s.station_type='CHARGING' AND s.active"),
        "fuelingStations", jdbc.queryForList("SELECT s.id,coalesce(s.code,l.code) code,s.status,l.latitude,l.longitude FROM service_stations s JOIN locations l ON l.id=s.location_id WHERE s.station_type IN ('FUEL','FUELING') AND s.active"),
        "geofences", jdbc.queryForList("SELECT id,code,zone_type,boundary,restricted FROM operational_zones"));
  }

  @GetMapping("/vehicles/{id}/trail")
  @PreAuthorize("hasAuthority('map.read')")
  public List<Map<String, Object>> vehicleTrail(@PathVariable("id") UUID id,
      @RequestParam(name = "minutes", defaultValue = "60") @Min(1) @Max(1440) int minutes) {
    return jdbc.queryForList("SELECT recorded_at,latitude,longitude,heading,speed_kph FROM asset_positions WHERE asset_id=? AND recorded_at>=now()-(? * interval '1 minute') ORDER BY recorded_at", id, minutes);
  }

  @GetMapping("/jobs")
  @PreAuthorize("hasAuthority('dispatch.read')")
  public List<Map<String, Object>> jobs() {
    return jdbc.queryForList("SELECT j.*,a.fleet_number FROM jobs j LEFT JOIN assets a ON a.id=j.assigned_asset_id ORDER BY j.created_at DESC LIMIT 1000");
  }

  @GetMapping("/alerts")
  @PreAuthorize("hasAuthority('alert.read')")
  public List<Map<String, Object>> alerts() {
    return jdbc.queryForList("SELECT al.*,a.fleet_number FROM alerts al LEFT JOIN assets a ON a.id=al.asset_id ORDER BY al.created_at DESC LIMIT 1000");
  }

  @GetMapping("/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<Map<String, Object>> auditHistory() {
    return jdbc.queryForList("SELECT * FROM audit_logs ORDER BY occurred_at DESC LIMIT 1000");
  }

  @GetMapping("/orders")
  @PreAuthorize("hasAuthority('order.read')")
  public List<Map<String, Object>> orders(@RequestParam(name = "plantId", required = false) UUID plantId, Authentication authentication) {
    String sql = "SELECT o.*, p.name AS plant FROM transport_orders o JOIN plants p ON p.id=o.plant_id";
    if (plantId != null) {
      requirePlantAccess(authentication, plantId);
      return jdbc.queryForList(sql + " WHERE o.plant_id=? ORDER BY o.created_at DESC LIMIT 500", plantId);
    }
    if (hasAuthority(authentication, "system.configure")) return jdbc.queryForList(sql + " ORDER BY o.created_at DESC LIMIT 500");
    return jdbc.queryForList(sql + " WHERE o.plant_id IN (SELECT upa.plant_id FROM user_plant_assignments upa JOIN users u ON u.id=upa.user_id WHERE u.username=? OR u.subject=?) ORDER BY o.created_at DESC LIMIT 500",
        authentication.getName(), authentication.getName());
  }

  @GetMapping("/map-configuration")
  @PreAuthorize("hasAuthority('map.read')")
  public Map<String, Object> mapConfiguration(@RequestParam(name = "plantId", required = false) UUID plantId) {
    List<Map<String, Object>> configurations = plantId == null
        ? jdbc.queryForList("SELECT id,plant_id,provider,default_latitude,default_longitude,default_zoom,tile_url,style_url,visible_layers::text AS visible_layers,enabled,version FROM map_configurations WHERE enabled ORDER BY plant_id NULLS LAST LIMIT 1")
        : jdbc.queryForList("SELECT id,plant_id,provider,default_latitude,default_longitude,default_zoom,tile_url,style_url,visible_layers::text AS visible_layers,enabled,version FROM map_configurations WHERE enabled AND (plant_id=? OR plant_id IS NULL) ORDER BY plant_id NULLS LAST LIMIT 1", plantId);
    return configurations.isEmpty() ? Map.of("provider", "offline", "default_latitude", 24.9857,
        "default_longitude", 55.0273, "default_zoom", 12, "tile_url", "/tiles/{z}/{x}/{y}.png")
        : configurations.getFirst();
  }

  @PutMapping("/map-configuration/{id}")
  @PreAuthorize("hasAuthority('map.configure')")
  public Map<String, Object> updateMapConfiguration(@PathVariable("id") UUID id,
                                                     @Valid @RequestBody MapConfigurationRequest request,
                                                     Principal actor) {
    if (!List.of("google", "osm", "offline", "mapbox").contains(request.provider())) {
      throw new IllegalArgumentException("unsupported map provider");
    }
    int changed = jdbc.update("""
        UPDATE map_configurations SET provider=?,default_latitude=?,default_longitude=?,
          default_zoom=?,tile_url=?,style_url=?,secret_reference=COALESCE(NULLIF(?,''),secret_reference),visible_layers=?::jsonb,
          version=version+1,updated_at=now(),updated_by=? WHERE id=?
        """, request.provider(), request.latitude(), request.longitude(), request.zoom(),
        request.tileUrl(), request.styleUrl(), request.secretReference(), layersJson(request.visibleLayers()),
        actor.getName(), id);
    if (changed != 1) throw new IllegalArgumentException("unknown map configuration " + id);
    audit(actor.getName(), "MAP_CONFIGURATION_UPDATED", id.toString(), request.provider());
    return jdbc.queryForMap("SELECT id,plant_id,provider,default_latitude,default_longitude,default_zoom,tile_url,style_url,visible_layers::text AS visible_layers,enabled,version FROM map_configurations WHERE id=?", id);
  }

  @PostMapping("/map-configuration/{id}/test")
  @PreAuthorize("hasAuthority('map.configure')")
  public Map<String, Object> testMapConfiguration(@PathVariable("id") UUID id, Principal actor) {
    Map<String, Object> configuration = jdbc.queryForMap("SELECT provider,tile_url,style_url,secret_reference FROM map_configurations WHERE id=?", id);
    String provider = String.valueOf(configuration.get("provider"));
    boolean configured = switch (provider) {
      case "google", "mapbox" -> configuration.get("secret_reference") != null;
      case "osm" -> true;
      case "offline" -> configuration.get("tile_url") != null || configuration.get("style_url") != null;
      default -> false;
    };
    String status = configured ? "CONFIGURED" : "MISSING_CONFIGURATION";
    String message = configured ? "Provider configuration is complete; browser tile access must also succeed"
        : "Required provider URL or secret reference is missing";
    jdbc.update("UPDATE map_configurations SET connectivity_status=?,connectivity_checked_at=now(),connectivity_message=? WHERE id=?",
        status, message, id);
    audit(actor.getName(), "MAP_CONNECTIVITY_TESTED", id.toString(), status);
    return Map.of("status", status, "message", message, "checkedAt", Instant.now());
  }

  @GetMapping("/control-center")
  @PreAuthorize("hasAuthority('control_center.read')")
  public Map<String, Object> controlCenter() {
    return Map.of("backend", "UP", "database", "UP", "kernel", integration("KERNEL"),
        "trackIt", integration("TRACKIT"), "mqtt", integration("MQTT"),
        "rabbitMq", integration("RABBITMQ"), "checkedAt", Instant.now());
  }

  @GetMapping("/integrations")
  @PreAuthorize("hasAuthority('integration.read')")
  public List<Map<String, Object>> integrations() {
    return jdbc.queryForList("SELECT id,integration_code,integration_type,enabled,endpoint,port,tls_enabled,connection_timeout_ms,retry_policy::text AS retry_policy,health_status,last_success_at,last_error,version FROM integration_configurations ORDER BY integration_code");
  }

  private Map<String, Object> integration(String code) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT enabled,health_status,last_success_at,last_error FROM integration_configurations WHERE integration_code=?", code);
    return rows.isEmpty() ? Map.of("enabled", false, "health_status", "NOT_CONFIGURED") : rows.getFirst();
  }
  private void requirePlantAccess(Authentication authentication, UUID plantId) {
    if (hasAuthority(authentication, "system.configure")) return;
    if (plantId == null) throw new IllegalArgumentException("plantId is required for plant-scoped access");
    Long assigned = jdbc.queryForObject("SELECT count(*) FROM user_plant_assignments upa JOIN users u ON u.id=upa.user_id WHERE upa.plant_id=? AND (u.username=? OR u.subject=?)",
        Long.class, plantId, authentication.getName(), authentication.getName());
    if (assigned == null || assigned == 0) throw new org.springframework.security.access.AccessDeniedException("plant access denied");
  }
  private boolean hasAuthority(Authentication authentication, String authority) {
    return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
  }
  private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
  private Number decimal(String sql) { return jdbc.queryForObject(sql, Number.class); }
  private String layersJson(List<String> layers) {
    List<String> allowed = List.of("plants", "vehicles", "parking", "fueling", "charging", "alerts", "geofences", "routes", "trails");
    if (!allowed.containsAll(layers)) throw new IllegalArgumentException("unsupported map layer");
    return layers.stream().map(layer -> "\"" + layer + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }
  private void audit(String actor, String action, String resourceId, String after) {
    jdbc.update("INSERT INTO audit_logs(id,occurred_at,actor,action,resource_type,resource_id,after_value) VALUES (?,now(),?,?, 'PLANT',?,CASE WHEN ? IS NULL THEN NULL ELSE jsonb_build_object('value',?) END)",
        UUID.randomUUID(), actor, action, resourceId, after, after);
  }
  public record PlantRequest(@NotBlank String code, @NotBlank String name, @NotBlank String location,
                             @NotNull Double latitude, @NotNull Double longitude,
                             @NotBlank String timezone) {}
  public record MapConfigurationRequest(@NotBlank String provider,
                                        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
                                        @NotNull @Min(1) @Max(22) Integer zoom,
                                        String tileUrl, String styleUrl, String secretReference,
                                        @NotEmpty List<String> visibleLayers) {}
}
