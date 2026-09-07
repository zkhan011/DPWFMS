package com.dpworld.fms.application.automation;

import com.dpworld.fms.application.automation.AutomationDecision.Action;
import com.dpworld.fms.application.automation.AutomationDecision.CandidateScore;
import com.dpworld.fms.application.automation.AutomationResource.FuelingBay;
import com.dpworld.fms.application.automation.AutomationResource.ParkingSpace;
import com.dpworld.fms.application.automation.AutomationResource.ResourceState;
import com.dpworld.fms.domain.AssetStatus;
import com.dpworld.fms.domain.MaintenanceStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Purely deterministic parking/fueling rule evaluator. Only successful reservation and job creation
 * are side effects; simulations use exactly the same scoring path without those effects.
 */
public final class AutomaticJobEngine {
  public interface RouteEvaluator {
    RouteMetrics route(AssetAutomationSnapshot asset, AutomationResource resource);
  }
  public interface AutomaticJobCreator {
    UUID create(Action action, AssetAutomationSnapshot asset, String ruleCode, String resourceId,
                String idempotencyKey, UUID reservationId, UUID parentJobId);
    boolean hasActiveEquivalent(UUID assetId, Action action);
  }

  private final RuleConfigurationService configurations;
  private final AutomationReservationService reservations;
  private final AutomationAlertService alerts;
  private final RouteEvaluator routes;
  private final AutomaticJobCreator jobs;
  private final List<AutomationDecision> history = new java.util.concurrent.CopyOnWriteArrayList<>();
  private final ConcurrentMap<String, Instant> idempotencyKeys = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, FuelLevel> lastFuelLevels = new ConcurrentHashMap<>();

  public AutomaticJobEngine(RuleConfigurationService configurations,
                            AutomationReservationService reservations,
                            AutomationAlertService alerts, RouteEvaluator routes,
                            AutomaticJobCreator jobs) {
    this.configurations = configurations;
    this.reservations = reservations;
    this.alerts = alerts;
    this.routes = routes;
    this.jobs = jobs;
  }

  public synchronized AutomationDecision evaluate(AssetAutomationSnapshot asset,
                                                   List<ParkingSpace> spaces,
                                                   List<FuelingBay> bays,
                                                   boolean simulate,
                                                   String trigger) {
    Instant now = asset.evaluatedAt();
    Optional<AutomationRule> fuelRule = configurations.resolve(AutomationRule.RuleKind.FUELING, asset, now);
    Optional<AutomationRule> parkRule = configurations.resolve(AutomationRule.RuleKind.PARKING, asset, now);
    List<String> evaluated = new ArrayList<>();
    fuelRule.ifPresent(r -> evaluated.add(r.code()));
    parkRule.ifPresent(r -> evaluated.add(r.code()));
    List<String> commonBlocks = commonEligibility(asset,
        fuelRule.or(() -> parkRule).map(r -> threshold(r, "telemetryFreshnessSeconds", 60)).orElse(60d));
    FuelLevel fuelLevel = fuelRule.map(rule -> classifyFuel(asset, rule)).orElse(FuelLevel.NORMAL);
    if (fuelLevel == FuelLevel.NORMAL && ("DISPATCHER_AUTO_FUEL".equals(trigger)
        || "FUELING_CANDIDATE_QUERY".equals(trigger))) {
      fuelLevel = FuelLevel.PREVENTIVE;
    }

    if (fuelRule.isPresent() && fuelLevel != FuelLevel.NORMAL
        && !"PARKING_CANDIDATE_QUERY".equals(trigger)) {
      return evaluateFueling(asset, fuelRule.get(), fuelLevel, bays, spaces, commonBlocks,
          evaluated, simulate, trigger);
    }
    return evaluateParking(asset, parkRule, spaces, commonBlocks, evaluated, simulate, trigger);
  }

  private AutomationDecision evaluateFueling(AssetAutomationSnapshot asset, AutomationRule rule,
                                              FuelLevel level, List<FuelingBay> bays,
                                              List<ParkingSpace> spaces, List<String> commonBlocks,
                                              List<String> evaluated, boolean simulate, String trigger) {
    List<String> blocks = new ArrayList<>(commonBlocks);
    if (!asset.fuelingAutomationAllowed()) blocks.add("FUELING_AUTOMATION_DISABLED");
    if (asset.activeFuelingJob() || jobs.hasActiveEquivalent(asset.assetId(), Action.FUEL)) blocks.add("DUPLICATE_FUELING_JOB");
    if ((level == FuelLevel.LOW || level == FuelLevel.PREVENTIVE) && asset.activeMovementJob()) blocks.add("HIGHER_PRIORITY_JOB_ACTIVE");
    if (asset.fuelType() == null || asset.fuelType().isBlank()) blocks.add("FUEL_TYPE_UNKNOWN");
    List<CandidateScore> scores = bays.stream().map(b -> scoreFueling(asset, b, rule, level))
        .sorted(candidateOrder()).toList();
    Optional<CandidateScore> best = scores.stream().filter(CandidateScore::eligible).findFirst();
    if (best.isEmpty() && level == FuelLevel.EMERGENCY) blocks.add("NO_SAFELY_REACHABLE_FUELING_STATION");
    else if (best.isEmpty()) blocks.add("NO_COMPATIBLE_FUELING_BAY");
    if (level == FuelLevel.EMERGENCY) {
      alerts.raise(asset.assetId(), "EMERGENCY_FUEL", AutomationAlertService.Severity.CRITICAL,
          "Asset cannot safely continue normal movement", asset.evaluatedAt(), Duration.ofMinutes(10));
    }
    Action action = level == FuelLevel.EMERGENCY && best.isEmpty() ? Action.EMERGENCY_INTERVENTION
        : asset.status() == AssetStatus.IDLE && !spaces.isEmpty() ? Action.FUEL_THEN_PARK : Action.FUEL;
    return finish(asset, rule, evaluated, List.of(rule.code()), blocks, scores, best, action,
        simulate, trigger, level == FuelLevel.EMERGENCY);
  }

  private AutomationDecision evaluateParking(AssetAutomationSnapshot asset,
                                              Optional<AutomationRule> configured,
                                              List<ParkingSpace> spaces, List<String> commonBlocks,
                                              List<String> evaluated, boolean simulate, String trigger) {
    if (configured.isEmpty()) return noRuleDecision(asset, evaluated, "NO_ACTIVE_PARKING_RULE", trigger);
    AutomationRule rule = configured.get();
    List<String> blocks = new ArrayList<>(commonBlocks);
    if (!asset.parkingAutomationAllowed()) blocks.add("PARKING_AUTOMATION_DISABLED");
    if (asset.activeParkingJob() || jobs.hasActiveEquivalent(asset.assetId(), Action.PARK)) blocks.add("DUPLICATE_PARKING_JOB");
    if (asset.activeMovementJob()) blocks.add("ACTIVE_MOVEMENT_JOB");
    if (asset.higherPriorityJobExpected()) blocks.add("HIGHER_PRIORITY_JOB_EXPECTED");
    if (asset.nextJobAt() != null && !asset.nextJobAt().isAfter(asset.evaluatedAt().plusSeconds(rule.suppressionSeconds()))) {
      blocks.add("NEXT_JOB_WITHIN_SUPPRESSION_WINDOW");
    }
    long idleSeconds = (long) threshold(rule, "idleSeconds", 300);
    if (asset.idleSince() == null || asset.idleSince().plusSeconds(idleSeconds).isAfter(asset.evaluatedAt())) {
      blocks.add("IDLE_PERIOD_NOT_REACHED");
    }
    if (asset.status() == AssetStatus.FUELLING || asset.status() == AssetStatus.CHARGING
        || asset.status() == AssetStatus.MAINTENANCE) blocks.add("ASSET_BUSY_WITH_SERVICE");
    List<CandidateScore> scores = spaces.stream().map(s -> scoreParking(asset, s, rule))
        .sorted(candidateOrder()).toList();
    Optional<CandidateScore> best = scores.stream().filter(CandidateScore::eligible).findFirst();
    if (best.isEmpty()) blocks.add("NO_COMPATIBLE_PARKING_SPACE");
    return finish(asset, rule, evaluated, List.of(rule.code()), blocks, scores, best, Action.PARK,
        simulate, trigger, false);
  }

  private AutomationDecision finish(AssetAutomationSnapshot asset, AutomationRule rule,
                                    List<String> evaluated, List<String> matched,
                                    List<String> blocks, List<CandidateScore> scores,
                                    Optional<CandidateScore> best, Action action, boolean simulate,
                                    String trigger, boolean bypassCooldown) {
    String cycle = String.valueOf(asset.evaluatedAt().getEpochSecond() / Math.max(1, rule.cooldownSeconds()));
    String key = rule.code() + ":" + asset.assetId() + ":" + cycle;
    if (!simulate && !bypassCooldown && idempotencyKeys.containsKey(key)) blocks.add("IDEMPOTENCY_KEY_ALREADY_PROCESSED");
    UUID reservationId = null;
    UUID jobId = null;
    if (blocks.isEmpty() && best.isPresent() && !simulate) {
      long ttl = (long) threshold(rule, "reservationSeconds", action == Action.PARK ? 600 : 900);
      Optional<AutomationReservationService.Reservation> reservation = reservations.reserve(
          action == Action.PARK ? "PARKING_SPACE" : "FUELING_BAY", best.get().resourceId(),
          asset.assetId(), key, asset.evaluatedAt(), Duration.ofSeconds(ttl));
      if (reservation.isEmpty()) blocks.add("RESOURCE_RESERVATION_CONFLICT");
      else {
        reservationId = reservation.get().id();
        idempotencyKeys.put(key, asset.evaluatedAt());
        jobId = jobs.create(action, asset, rule.code(), best.get().resourceId(), key, reservationId, null);
      }
    }
    if (!blocks.isEmpty()) raiseBlockAlert(asset, action, blocks);
    AutomationDecision decision = new AutomationDecision(UUID.randomUUID(), asset.assetId(), key,
        asset.evaluatedAt(), input(asset, trigger), evaluated, matched, blocks.isEmpty(), blocks,
        scores, blocks.isEmpty() && best.isPresent() ? action : Action.NONE,
        best.map(CandidateScore::resourceId).orElse(null), jobId != null, jobId, reservationId);
    history.add(decision);
    return decision;
  }

  private CandidateScore scoreParking(AssetAutomationSnapshot asset, ParkingSpace space, AutomationRule rule) {
    List<String> rejected = resourceCompatibility(asset, space);
    if (space.state() != ResourceState.AVAILABLE) rejected.add("SPACE_" + space.state());
    RouteMetrics route = routes.route(asset, space);
    if (!route.valid()) rejected.add(route.rejectionReason() == null ? "NO_VALID_ROUTE" : route.rejectionReason());
    Map<String, Double> c = new LinkedHashMap<>();
    c.put("distance", route.distanceMetres() / 1000 * weight(rule, "distance"));
    c.put("travelTime", route.travelSeconds() / 60 * weight(rule, "travelTime"));
    c.put("congestion", route.congestionCost() * weight(rule, "congestion"));
    c.put("nextJob", space.nextJobDistanceMetres() / 1000 * weight(rule, "nextJob"));
    c.put("zone", asset.zoneId().equals(space.zoneId()) ? 0 : weight(rule, "zone"));
    c.put("maneuver", space.maneuverPenalty() * weight(rule, "maneuver"));
    c.put("occupancy", space.occupancyRatio() * weight(rule, "occupancy"));
    c.put("preference", space.operationalPreferencePenalty() * weight(rule, "preference"));
    return candidate(space.id(), rejected, c);
  }

  private CandidateScore scoreFueling(AssetAutomationSnapshot asset, FuelingBay bay,
                                      AutomationRule rule, FuelLevel level) {
    List<String> rejected = resourceCompatibility(asset, bay);
    if (!asset.fuelType().equalsIgnoreCase(bay.fuelType())) rejected.add("FUEL_TYPE_MISMATCH");
    if (bay.state() != ResourceState.AVAILABLE && bay.state() != ResourceState.QUEUED) rejected.add("BAY_" + bay.state());
    RouteMetrics route = routes.route(asset, bay);
    if (!route.valid()) rejected.add(route.rejectionReason() == null ? "NO_SAFE_ROUTE" : route.rejectionReason());
    double requiredRange = route.distanceMetres() * 1.1;
    if (asset.estimatedRangeMetres() < requiredRange) rejected.add("INSUFFICIENT_RANGE_TO_STATION");
    double criticalBias = level == FuelLevel.CRITICAL || level == FuelLevel.EMERGENCY ? 2 : 1;
    Map<String, Double> c = new LinkedHashMap<>();
    c.put("travelTime", route.travelSeconds() / 60 * weight(rule, "travelTime") * criticalBias);
    c.put("queueWait", bay.queueWaitSeconds() / 60 * weight(rule, "queueWait") * criticalBias);
    c.put("congestion", route.congestionCost() * weight(rule, "congestion"));
    c.put("routeDeviation", route.deviationCost() * weight(rule, "routeDeviation"));
    c.put("serviceTime", bay.serviceSeconds() / 60 * weight(rule, "serviceTime"));
    c.put("stationPriority", bay.stationPriorityPenalty() * weight(rule, "stationPriority"));
    c.put("nextJob", 0d);
    c.put("risk", bay.outageRisk() * weight(rule, "risk") * criticalBias);
    return candidate(bay.id(), rejected, c);
  }

  private List<String> resourceCompatibility(AssetAutomationSnapshot asset, AutomationResource resource) {
    List<String> rejected = new ArrayList<>();
    if (!resource.enabled()) rejected.add("RESOURCE_DISABLED");
    if (!resource.supportedAssetTypes().isEmpty() && !resource.supportedAssetTypes().contains(asset.assetType())) rejected.add("ASSET_TYPE_INCOMPATIBLE");
    if (resource.maxHeightMetres() > 0 && asset.envelope().heightM() > resource.maxHeightMetres()) rejected.add("HEIGHT_LIMIT_EXCEEDED");
    if (resource.maxWeightTonnes() > 0 && asset.envelope().weightTonnes() > resource.maxWeightTonnes()) rejected.add("WEIGHT_LIMIT_EXCEEDED");
    if (!asset.permittedZones().isEmpty() && !asset.permittedZones().contains(resource.zoneId())) rejected.add("ZONE_NOT_PERMITTED");
    return rejected;
  }

  private List<String> commonEligibility(AssetAutomationSnapshot a, double freshnessSeconds) {
    List<String> reasons = new ArrayList<>();
    if (!a.enabled()) reasons.add("ASSET_DISABLED");
    if (a.position() == null || a.mapNodeId() == null) reasons.add("POSITION_UNKNOWN");
    if (!a.insideOperationalMap()) reasons.add("OUTSIDE_OPERATIONAL_MAP");
    if (a.telemetryAt() == null || a.telemetryAt().plusSeconds((long) freshnessSeconds).isBefore(a.evaluatedAt())) reasons.add("STALE_TELEMETRY");
    if (a.status() == AssetStatus.OFFLINE || a.status() == AssetStatus.FAULT) reasons.add("ASSET_OFFLINE_OR_BROKEN");
    if (a.maintenanceStatus() == MaintenanceStatus.OUT_OF_SERVICE) reasons.add("ASSET_OUT_OF_SERVICE");
    if (a.manuallyBlocked()) reasons.add("MANUALLY_BLOCKED");
    if (a.manualControl()) reasons.add("MANUAL_DISPATCHER_CONTROL");
    if (a.criticalAlert()) reasons.add("CRITICAL_SAFETY_ALERT");
    return reasons;
  }

  public FuelLevel classifyFuel(AssetAutomationSnapshot a, AutomationRule rule) {
    double emergency = threshold(rule, "emergencyFuelPercent", 10);
    double critical = threshold(rule, "criticalFuelPercent", 20);
    double low = threshold(rule, "lowFuelPercent", 35);
    double hysteresis = threshold(rule, "hysteresisPercent", 3);
    FuelLevel previous = lastFuelLevels.get(a.assetId());
    FuelLevel calculated = a.fuelPercent() < emergency ? FuelLevel.EMERGENCY
        : a.fuelPercent() < critical ? FuelLevel.CRITICAL
        : a.fuelPercent() <= low ? FuelLevel.LOW : FuelLevel.NORMAL;
    if (previous == FuelLevel.LOW && calculated == FuelLevel.NORMAL && a.fuelPercent() <= low + hysteresis) calculated = FuelLevel.LOW;
    if (previous == FuelLevel.CRITICAL && calculated == FuelLevel.LOW && a.fuelPercent() < critical + hysteresis) calculated = FuelLevel.CRITICAL;
    if (previous == FuelLevel.EMERGENCY && calculated == FuelLevel.CRITICAL && a.fuelPercent() < emergency + hysteresis) calculated = FuelLevel.EMERGENCY;
    lastFuelLevels.put(a.assetId(), calculated);
    return calculated;
  }

  private AutomationDecision noRuleDecision(AssetAutomationSnapshot asset, List<String> evaluated,
                                              String reason, String trigger) {
    AutomationDecision decision = new AutomationDecision(UUID.randomUUID(), asset.assetId(), null,
        asset.evaluatedAt(), input(asset, trigger), evaluated, List.of(), false, List.of(reason),
        List.of(), Action.NONE, null, false, null, null);
    history.add(decision);
    return decision;
  }

  private void raiseBlockAlert(AssetAutomationSnapshot asset, Action action, List<String> blocks) {
    if (blocks.contains("STALE_TELEMETRY")) alerts.raise(asset.assetId(), "STALE_FUEL_TELEMETRY",
        AutomationAlertService.Severity.MAJOR, "Automation paused because telemetry is stale",
        asset.evaluatedAt(), Duration.ofMinutes(10));
    if (blocks.stream().anyMatch(b -> b.startsWith("NO_COMPATIBLE"))) alerts.raise(asset.assetId(),
        action == Action.PARK ? "NO_COMPATIBLE_PARKING_SPACE" : "NO_REACHABLE_FUELING_STATION",
        AutomationAlertService.Severity.MAJOR, String.join(",", blocks), asset.evaluatedAt(), Duration.ofMinutes(10));
  }

  private static CandidateScore candidate(String id, List<String> rejected, Map<String, Double> components) {
    return new CandidateScore(id, rejected.isEmpty(), components.values().stream().mapToDouble(Double::doubleValue).sum(), components, rejected);
  }
  private static Comparator<CandidateScore> candidateOrder() {
    return Comparator.comparing(CandidateScore::eligible).reversed()
        .thenComparingDouble(CandidateScore::totalScore).thenComparing(CandidateScore::resourceId);
  }
  private static double weight(AutomationRule rule, String key) { return rule.weights().getOrDefault(key, 1d); }
  private static double threshold(AutomationRule rule, String key, double fallback) { return rule.thresholds().getOrDefault(key, fallback); }
  private static Map<String, Object> input(AssetAutomationSnapshot a, String trigger) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("trigger", trigger); input.put("assetId", a.assetId()); input.put("terminalId", a.terminalId());
    input.put("zoneId", a.zoneId()); input.put("status", a.status()); input.put("fuelPercent", a.fuelPercent());
    input.put("telemetryAt", a.telemetryAt()); input.put("evaluatedAt", a.evaluatedAt());
    return input;
  }

  public List<AutomationDecision> history() { return List.copyOf(history); }
  public enum FuelLevel { NORMAL, LOW, CRITICAL, EMERGENCY, PREVENTIVE }
}
