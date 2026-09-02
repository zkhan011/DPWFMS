package com.dpworld.fms.api;

import com.dpworld.fms.application.automation.*;
import com.dpworld.fms.application.automation.AutomationDecision.Action;
import com.dpworld.fms.application.automation.AutomationResource.FuelingBay;
import com.dpworld.fms.application.automation.AutomationResource.ParkingSpace;
import com.dpworld.fms.application.automation.AutomationResource.ResourceState;
import com.dpworld.fms.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import com.dpworld.fms.routing.*;
import org.springframework.stereotype.Component;

/** Composition-root adapter used by REST, scheduled reconciliation, and event consumers. */
@Component
public class AutomationRuntime {
  private final RuleConfigurationService rules;
  private final AutomationReservationService reservations = new AutomationReservationService();
  private final AutomationAlertService alerts = new AutomationAlertService();
  private final Map<UUID, AssetAutomationSnapshot> assets = new ConcurrentHashMap<>();
  private final Map<UUID, AutomaticJob> jobs = new ConcurrentHashMap<>();
  private final List<AutomationAuditEvent> auditEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
  private final List<ParkingSpace> parkingSpaces;
  private final List<FuelingBay> fuelingBays;
  private final AutomaticJobEngine engine;
  private final AutomationDecisionStore decisionStore;
  private final ProductionRoutingService productionRouting;
  private final JdbcTemplate jdbc;
  private final boolean simulatorEnabled;

  public AutomationRuntime(AutomationRuleStore store, AutomationDecisionStore decisionStore,
                           ProductionRoutingService productionRouting, JdbcTemplate jdbc,
                           @Value("${dpwfms.development.simulator-enabled:false}") boolean simulatorEnabled) {
    Instant now = Instant.now();
    this.decisionStore = decisionStore;
    this.productionRouting = productionRouting;
    this.jdbc = jdbc;
    this.simulatorEnabled = simulatorEnabled;
    rules = RuleConfigurationService.withDefaults(now);
    List<AutomationRule> persisted = store.findAll();
    if (!persisted.isEmpty()) rules.replaceAll(persisted);
    if (simulatorEnabled) {
      for (int i = 1; i <= 10; i++) {
        UUID id = UUID.nameUUIDFromBytes(("automation-asset-" + i).getBytes());
        assets.put(id, snapshot(id, i == 2 ? 16 : i == 3 ? 8 : 60, now));
      }
    }
    parkingSpaces = simulatorEnabled ? java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(i ->
        new ParkingSpace("P-%02d".formatted(i), "P_NODE_%02d".formatted(i), "YARD-A", true,
            ResourceState.AVAILABLE, Set.of(AssetType.ITV, AssetType.TRACTOR), 4.5, 50,
            i / 40d, i % 3, i * 80d, i % 4 == 0 ? 1 : 0)).toList() : List.of();
    fuelingBays = simulatorEnabled ? List.of(
        new FuelingBay("FUEL-A-1", "FUEL-A", "FUEL_NODE_A", "SERVICE", true,
            ResourceState.AVAILABLE, Set.of(AssetType.ITV, AssetType.TRACTOR), 5, 60,
            "DIESEL", 5, 900, 600, .05, 0)) : List.of();
    engine = new AutomaticJobEngine(rules, reservations, alerts, this::route, new JobAdapter());
  }

  public AutomationDecision evaluate(UUID assetId, boolean simulate, String trigger) {
    AssetAutomationSnapshot snapshot = Optional.ofNullable(assets.get(assetId))
        .orElseGet(() -> loadAssetSnapshot(assetId));
    snapshot = withEvaluationTime(snapshot, Instant.now());
    assets.put(assetId, snapshot);
    AutomationDecision decision = engine.evaluate(snapshot, simulatorEnabled ? parkingSpaces : loadParkingSpaces(), simulatorEnabled ? fuelingBays : loadFuelingBays(), simulate, trigger);
    decisionStore.append(decision);
    return decision;
  }

  public void evaluateAll() {
    assets.keySet().forEach(id -> evaluate(id, false, "PERIODIC_RECONCILIATION"));
  }

  public RuleConfigurationService rules() { return rules; }
  public AutomationReservationService reservations() { return reservations; }
  public AutomationAlertService alerts() { return alerts; }
  public AutomaticJobEngine engine() { return engine; }
  public List<ParkingSpace> parkingSpaces() { return parkingSpaces; }
  public List<FuelingBay> fuelingBays() { return fuelingBays; }
  public List<AutomaticJob> jobs() { return List.copyOf(jobs.values()); }
  public Set<UUID> assetIds() { return Set.copyOf(assets.keySet()); }
  public List<AutomationAuditEvent> auditEvents() { return List.copyOf(auditEvents); }
  public void audit(String actor, String action, String resourceId, Map<String, Object> details) {
    auditEvents.add(new AutomationAuditEvent(UUID.randomUUID(), Instant.now(), actor, action,
        resourceId, Map.copyOf(details)));
  }

  private RouteMetrics route(AssetAutomationSnapshot asset, AutomationResource resource) {
    if (asset.mapNodeId() == null || asset.mapNodeId().isBlank()) return RouteMetrics.invalid("ASSET_LOGICAL_NODE_UNAVAILABLE");
    if (resource.mapNodeId() == null || resource.mapNodeId().isBlank()) return RouteMetrics.invalid("RESOURCE_ROUTING_NODE_UNAVAILABLE");
    ProductionRoutingService.Result result = productionRouting.calculate(new RouteRequest(asset.mapNodeId(), resource.mapNodeId(), asset.assetType(), asset.envelope(), asset.estimatedRangeMetres(), 50), null);
    if (!result.valid()) return RouteMetrics.invalid(result.failureReason());
    return new RouteMetrics(true, result.route().totalDistanceMetres(), result.route().estimatedTravelSeconds(), 0, 0, null);
  }

  private List<ParkingSpace> loadParkingSpaces() {
    return jdbc.query("SELECT p.code,n.code node_code,z.code zone_code,p.active,p.status,p.supported_asset_types::text,p.max_weight_tonnes,z.capacity,(SELECT count(*) FROM parking_spaces x WHERE x.parking_zone_id=z.id AND x.status IN ('RESERVED','OCCUPIED')) used FROM parking_spaces p JOIN parking_zones z ON z.id=p.parking_zone_id LEFT JOIN map_nodes n ON n.id=p.routing_node_id", (rs,row) -> new ParkingSpace(rs.getString("code"),rs.getString("node_code"),rs.getString("zone_code"),rs.getBoolean("active"),state(rs.getString("status")),assetTypes(rs.getString("supported_asset_types")),0,rs.getDouble("max_weight_tonnes"),rs.getDouble("used")/Math.max(1,rs.getDouble("capacity")),0,0,0));
  }
  private List<FuelingBay> loadFuelingBays() {
    return jdbc.query("SELECT b.code,s.code station_code,n.code node_code,b.active,b.status,s.supported_asset_types::text,s.service_type,(SELECT count(*) FROM resource_reservations r WHERE r.resource_id=b.id AND r.released_at IS NULL) queued FROM service_bays b JOIN service_stations s ON s.id=b.station_id LEFT JOIN map_nodes n ON n.id=b.routing_node_id WHERE s.station_type IN ('FUEL','FUELING')", (rs,row) -> new FuelingBay(rs.getString("code"),rs.getString("station_code"),rs.getString("node_code"),"SERVICE",rs.getBoolean("active"),state(rs.getString("status")),assetTypes(rs.getString("supported_asset_types")),0,0,rs.getString("service_type"),rs.getInt("queued"),0,600,0,0));
  }
  private Set<AssetType> assetTypes(String json) { Set<AssetType> result=new java.util.HashSet<>();if(json!=null)for(AssetType type:AssetType.values())if(json.contains("\""+type.name()+"\""))result.add(type);return result; }
  private ResourceState state(String value) { try{return ResourceState.valueOf(value);}catch(Exception ignored){return ResourceState.UNKNOWN;} }

  private AssetAutomationSnapshot loadAssetSnapshot(UUID assetId) {
    List<AssetAutomationSnapshot> rows = jdbc.query("""
        SELECT a.*,t.code asset_type,p.code plant_code,n.code matched_node_code,profile.height_m,profile.width_m,profile.length_m,profile.weight_tonnes
        FROM assets a JOIN asset_types t ON t.id=a.asset_type_id
        LEFT JOIN plants p ON p.id=a.plant_id LEFT JOIN map_nodes n ON n.id=a.matched_node_id
        JOIN asset_routing_profiles profile ON profile.asset_id=a.id WHERE a.id=?
        """, (rs,row) -> new AssetAutomationSnapshot(assetId, rs.getString("plant_code"), "SERVICE",
        AssetType.valueOf(rs.getString("asset_type")), rs.getString("asset_type"), rs.getBoolean("enabled"),
        new GeoPoint(rs.getDouble("latitude"),rs.getDouble("longitude")),rs.getString("matched_node_code"),
        rs.getTimestamp("last_telemetry_at").toInstant(),AssetStatus.valueOf(rs.getString("operational_status")),
        MaintenanceStatus.valueOf(rs.getString("maintenance_status")),false,false,false,true,true,true,
        rs.getObject("current_job_id")!=null,false,false,false,null,Instant.now().minusSeconds(300),
        rs.getDouble("energy_percent"),rs.getString("energy_source"),rs.getDouble("energy_percent")*1000,
        new VehicleEnvelope(rs.getDouble("height_m"),rs.getDouble("width_m"),rs.getDouble("length_m"),rs.getDouble("weight_tonnes")),Set.of("SERVICE"),Instant.now()),assetId);
    if(rows.isEmpty())throw new IllegalArgumentException("unknown automation asset "+assetId);
    return rows.getFirst();
  }

  private static AssetAutomationSnapshot snapshot(UUID id, double fuel, Instant now) {
    return new AssetAutomationSnapshot(id, "JEA", "YARD-A", AssetType.ITV, "ITV-DAY",
        true, new GeoPoint(24.995, 55.04), "N-1", now, AssetStatus.IDLE,
        MaintenanceStatus.SERVICEABLE, false, false, false, true, true, true,
        false, false, false, false, null, now.minusSeconds(900), fuel,
        "DIESEL", fuel * 1000, new VehicleEnvelope(3.2, 2.5, 7, 35),
        Set.of("YARD-A", "SERVICE"), now);
  }

  private static AssetAutomationSnapshot withEvaluationTime(AssetAutomationSnapshot a, Instant now) {
    return new AssetAutomationSnapshot(a.assetId(), a.terminalId(), a.zoneId(), a.assetType(),
        a.assetGroup(), a.enabled(), a.position(), a.mapNodeId(), a.telemetryAt(), a.status(),
        a.maintenanceStatus(), a.manuallyBlocked(), a.manualControl(), a.criticalAlert(),
        a.insideOperationalMap(), a.parkingAutomationAllowed(), a.fuelingAutomationAllowed(),
        a.activeMovementJob(), a.activeParkingJob(), a.activeFuelingJob(),
        a.higherPriorityJobExpected(), a.nextJobAt(), a.idleSince(), a.fuelPercent(),
        a.fuelType(), a.estimatedRangeMetres(), a.envelope(), a.permittedZones(), now);
  }

  private final class JobAdapter implements AutomaticJobEngine.AutomaticJobCreator {
    @Override public UUID create(Action action, AssetAutomationSnapshot asset, String ruleCode,
                                 String resourceId, String idempotencyKey, UUID reservationId,
                                 UUID parentJobId) {
      UUID id = UUID.randomUUID();
      jobs.put(id, new AutomaticJob(id, asset.assetId(), action, ruleCode, resourceId,
          idempotencyKey, reservationId, parentJobId, "CREATED", Instant.now()));
      if (action == Action.FUEL_THEN_PARK) {
        UUID child = UUID.randomUUID();
        jobs.put(child, new AutomaticJob(child, asset.assetId(), Action.PARK, ruleCode, null,
            idempotencyKey + ":PARK", null, id, "BLOCKED_BY_DEPENDENCY", Instant.now()));
      }
      return id;
    }
    @Override public boolean hasActiveEquivalent(UUID assetId, Action action) {
      return jobs.values().stream().anyMatch(j -> j.assetId().equals(assetId)
          && (j.action() == action || action == Action.FUEL && j.action() == Action.FUEL_THEN_PARK)
          && !Set.of("COMPLETED", "CANCELLED", "FAILED").contains(j.status()));
    }
  }

  public record AutomaticJob(UUID id, UUID assetId, Action action, String ruleCode,
                             String resourceId, String idempotencyKey, UUID reservationId,
                             UUID parentJobId, String status, Instant createdAt) {}
  public record AutomationAuditEvent(UUID id, Instant occurredAt, String actor, String action,
                                     String resourceId, Map<String, Object> details) {}
}
