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

  public AutomationRuntime(AutomationRuleStore store, AutomationDecisionStore decisionStore,
                           @Value("${dpwfms.development.simulator-enabled:false}") boolean simulatorEnabled) {
    Instant now = Instant.now();
    this.decisionStore = decisionStore;
    rules = RuleConfigurationService.withDefaults(now);
    List<AutomationRule> persisted = store.findAll();
    if (!persisted.isEmpty()) rules.replaceAll(persisted);
    if (simulatorEnabled) {
      for (int i = 1; i <= 10; i++) {
        UUID id = UUID.nameUUIDFromBytes(("automation-asset-" + i).getBytes());
        assets.put(id, snapshot(id, i == 2 ? 16 : i == 3 ? 8 : 60, now));
      }
    }
    parkingSpaces = java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(i ->
        new ParkingSpace("P-%02d".formatted(i), "P_NODE_%02d".formatted(i), "YARD-A", true,
            ResourceState.AVAILABLE, Set.of(AssetType.ITV, AssetType.TRACTOR), 4.5, 50,
            i / 40d, i % 3, i * 80d, i % 4 == 0 ? 1 : 0)).toList();
    fuelingBays = List.of(
        new FuelingBay("FUEL-A-1", "FUEL-A", "FUEL_NODE_A", "SERVICE", true,
            ResourceState.AVAILABLE, Set.of(AssetType.ITV, AssetType.TRACTOR), 5, 60,
            "DIESEL", 5, 900, 600, .05, 0),
        new FuelingBay("FUEL-B-1", "FUEL-B", "FUEL_NODE_B", "SERVICE", true,
            ResourceState.AVAILABLE, Set.of(AssetType.ITV, AssetType.TRACTOR), 5, 60,
            "DIESEL", 0, 30, 600, .02, .2));
    engine = new AutomaticJobEngine(rules, reservations, alerts, this::route, new JobAdapter());
  }

  public AutomationDecision evaluate(UUID assetId, boolean simulate, String trigger) {
    AssetAutomationSnapshot snapshot = Optional.ofNullable(assets.get(assetId))
        .orElseThrow(() -> new IllegalArgumentException("unknown automation asset " + assetId));
    snapshot = withEvaluationTime(snapshot, Instant.now());
    assets.put(assetId, snapshot);
    AutomationDecision decision = engine.evaluate(snapshot, parkingSpaces, fuelingBays, simulate, trigger);
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
    int number = Integer.parseInt(resource.id().replaceAll("\\D", "").isBlank()
        ? "2" : resource.id().replaceAll("\\D", ""));
    double distance = 150 + number * 75;
    return new RouteMetrics(true, distance, distance / (25 / 3.6), 0.1 * (number % 3), 0, null);
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
