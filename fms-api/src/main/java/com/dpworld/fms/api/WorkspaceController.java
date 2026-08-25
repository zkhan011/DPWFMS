package com.dpworld.fms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    return Map.of("username", authentication.getName(), "role", "LOCAL_DEVELOPMENT",
        "permissions", authentication.getAuthorities().stream().map(Object::toString).sorted().toList());
  }

  @GetMapping("/overview")
  @PreAuthorize("hasAuthority('dashboard.read')")
  public Map<String, Object> overview() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("kernelStatus", "CONFIGURATION_REQUIRED");
    result.put("operatingMode", "OPERATING");
    result.put("registeredVehicles", count("SELECT count(*) FROM assets"));
    result.put("activeVehicles", count("SELECT count(*) FROM assets WHERE operational_status <> 'OFFLINE' AND enabled"));
    result.put("idleVehicles", count("SELECT count(*) FROM assets WHERE operational_status = 'IDLE' AND enabled"));
    result.put("offlineVehicles", count("SELECT count(*) FROM assets WHERE operational_status = 'OFFLINE'"));
    result.put("activeOrders", count("SELECT count(*) FROM transport_orders WHERE status NOT IN ('COMPLETED','CANCELLED','FAILED')"));
    result.put("failedOrders", count("SELECT count(*) FROM transport_orders WHERE status = 'FAILED'"));
    result.put("activeAlerts", count("SELECT count(*) FROM alerts WHERE acknowledged_at IS NULL"));
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
  public void setPlantEnabled(@PathVariable UUID id, @RequestParam boolean value, Authentication authentication) {
    requirePlantAccess(authentication, id);
    if (jdbc.update("UPDATE plants SET enabled=?, status=CASE WHEN ? THEN 'OPERATIONAL' ELSE 'INACTIVE' END, version=version+1, updated_at=now() WHERE id=?", value, value, id) != 1) {
      throw new IllegalArgumentException("unknown plant " + id);
    }
    audit(authentication.getName(), value ? "PLANT_ACTIVATED" : "PLANT_DEACTIVATED", id.toString(), null);
  }

  @GetMapping("/vehicles")
  @PreAuthorize("hasAuthority('vehicle.read')")
  public List<Map<String, Object>> vehicles(@RequestParam(required = false) UUID plantId, Authentication authentication) {
    String sql = """
        SELECT a.id, a.fleet_number, t.code AS asset_type, p.name AS plant,
               a.latitude, a.longitude, a.operational_status, a.availability_status,
               a.energy_percent, a.current_job_id, a.driver_id, a.last_telemetry_at, a.enabled
          FROM assets a JOIN asset_types t ON t.id=a.asset_type_id
          LEFT JOIN plants p ON p.id=a.plant_id
        """;
    requirePlantAccess(authentication, plantId);
    return plantId == null ? jdbc.queryForList(sql + " ORDER BY a.fleet_number")
        : jdbc.queryForList(sql + " WHERE a.plant_id=? ORDER BY a.fleet_number", plantId);
  }

  @GetMapping("/orders")
  @PreAuthorize("hasAuthority('order.read')")
  public List<Map<String, Object>> orders(@RequestParam(required = false) UUID plantId, Authentication authentication) {
    String sql = "SELECT o.*, p.name AS plant FROM transport_orders o JOIN plants p ON p.id=o.plant_id";
    requirePlantAccess(authentication, plantId);
    return plantId == null ? jdbc.queryForList(sql + " ORDER BY o.created_at DESC LIMIT 500")
        : jdbc.queryForList(sql + " WHERE o.plant_id=? ORDER BY o.created_at DESC LIMIT 500", plantId);
  }

  @GetMapping("/map-configuration")
  @PreAuthorize("hasAuthority('map.read')")
  public Map<String, Object> mapConfiguration(@RequestParam(required = false) UUID plantId) {
    List<Map<String, Object>> configurations = plantId == null
        ? jdbc.queryForList("SELECT id,plant_id,provider,default_latitude,default_longitude,default_zoom,tile_url,style_url,visible_layers,enabled,version FROM map_configurations WHERE enabled ORDER BY plant_id NULLS LAST LIMIT 1")
        : jdbc.queryForList("SELECT id,plant_id,provider,default_latitude,default_longitude,default_zoom,tile_url,style_url,visible_layers,enabled,version FROM map_configurations WHERE enabled AND (plant_id=? OR plant_id IS NULL) ORDER BY plant_id NULLS LAST LIMIT 1", plantId);
    return configurations.isEmpty() ? Map.of("provider", "offline", "default_latitude", 24.9857,
        "default_longitude", 55.0273, "default_zoom", 12, "tile_url", "/tiles/{z}/{x}/{y}.png")
        : configurations.getFirst();
  }

  @PutMapping("/map-configuration/{id}")
  @PreAuthorize("hasAuthority('map.configure')")
  public Map<String, Object> updateMapConfiguration(@PathVariable UUID id,
                                                     @Valid @RequestBody MapConfigurationRequest request,
                                                     Principal actor) {
    if (!List.of("google", "osm", "offline", "mapbox").contains(request.provider())) {
      throw new IllegalArgumentException("unsupported map provider");
    }
    int changed = jdbc.update("""
        UPDATE map_configurations SET provider=?,default_latitude=?,default_longitude=?,
          default_zoom=?,tile_url=?,style_url=?,secret_reference=?,visible_layers=?::jsonb,
          version=version+1,updated_at=now(),updated_by=? WHERE id=?
        """, request.provider(), request.latitude(), request.longitude(), request.zoom(),
        request.tileUrl(), request.styleUrl(), request.secretReference(), request.visibleLayersJson(),
        actor.getName(), id);
    if (changed != 1) throw new IllegalArgumentException("unknown map configuration " + id);
    audit(actor.getName(), "MAP_CONFIGURATION_UPDATED", id.toString(), request.provider());
    return jdbc.queryForMap("SELECT id,plant_id,provider,default_latitude,default_longitude,default_zoom,tile_url,style_url,visible_layers,enabled,version FROM map_configurations WHERE id=?", id);
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
    return jdbc.queryForList("SELECT id,integration_code,integration_type,enabled,endpoint,port,tls_enabled,connection_timeout_ms,retry_policy,health_status,last_success_at,last_error,version FROM integration_configurations ORDER BY integration_code");
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
                                        @NotBlank String visibleLayersJson) {}
}
