package com.dpworld.fms.application.automation;

import static com.dpworld.fms.application.automation.AutomationDecision.Action;
import static com.dpworld.fms.application.automation.AutomationResource.ResourceState;
import static org.junit.jupiter.api.Assertions.*;

import com.dpworld.fms.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutomaticJobEngineTest {
  private Instant now;
  private RuleConfigurationService rules;
  private AutomationReservationService reservations;
  private AutomationAlertService alerts;
  private RecordingJobs jobs;

  @BeforeEach void setUp() {
    now = Instant.parse("2026-08-21T12:00:00Z");
    rules = RuleConfigurationService.withDefaults(now.minusSeconds(60));
    reservations = new AutomationReservationService();
    alerts = new AutomationAlertService();
    jobs = new RecordingJobs();
  }

  @Test void idleAssetReceivesNearestParkingSpaceWhenCostsAreEqual() {
    AutomaticJobEngine engine = engine((asset, resource) -> route(resource.id().equals("P1") ? 100 : 200, 0));
    AutomationDecision result = engine.evaluate(snapshot(60, now, false, false),
        List.of(space("P2", 0, 0), space("P1", 0, 0)), List.of(), false, "ASSET_IDLE");
    assertTrue(result.jobCreated());
    assertEquals(Action.PARK, result.selectedAction());
    assertEquals("P1", result.selectedResourceId());
  }

  @Test void upcomingJobSuppressesParking() {
    AssetAutomationSnapshot a = copy(snapshot(60, now, false, false), now, now.plusSeconds(120), false);
    AutomationDecision result = engine((x, r) -> route(100, 0)).evaluate(a,
        List.of(space("P1", 0, 0)), List.of(), false, "ASSET_IDLE");
    assertFalse(result.jobCreated());
    assertTrue(result.blockingReasons().contains("NEXT_JOB_WITHIN_SUPPRESSION_WINDOW"));
  }

  @Test void operationallyBetterParkingSpaceCanBeatNearest() {
    AutomaticJobEngine engine = engine((asset, resource) -> route(resource.id().equals("NEAR") ? 100 : 150, 0));
    AutomationDecision result = engine.evaluate(snapshot(60, now, false, false),
        List.of(space("NEAR", 20, 20), space("BETTER", 0, 0)), List.of(), true, "SIMULATION");
    assertEquals("BETTER", result.selectedResourceId());
  }

  @Test void incompatibleAndUnroutableSpacesAreRejected() {
    var wrong = new AutomationResource.ParkingSpace("WRONG", "N", "YARD", true,
        ResourceState.AVAILABLE, Set.of(AssetType.EXTERNAL_TRUCK), 5, 60, 0, 0, 0, 0);
    AutomationDecision result = engine((asset, resource) -> RouteMetrics.invalid("NO_VALID_ROUTE"))
        .evaluate(snapshot(60, now, false, false), List.of(wrong), List.of(), true, "SIMULATION");
    assertFalse(result.eligible());
    assertTrue(result.candidates().getFirst().rejectionReasons().contains("ASSET_TYPE_INCOMPATIBLE"));
    assertTrue(result.candidates().getFirst().rejectionReasons().contains("NO_VALID_ROUTE"));
  }

  @Test void staleTelemetryPreventsDispatchAndRaisesDeduplicatedAlert() {
    AutomaticJobEngine engine = engine((asset, resource) -> route(100, 0));
    AssetAutomationSnapshot stale = snapshot(60, now.minusSeconds(61), false, false);
    AutomationDecision result = engine.evaluate(stale, List.of(space("P1", 0, 0)), List.of(), false, "TELEMETRY");
    assertTrue(result.blockingReasons().contains("STALE_TELEMETRY"));
    engine.evaluate(stale, List.of(space("P1", 0, 0)), List.of(), false, "PERIODIC");
    assertEquals(1, alerts.active().stream().filter(a -> a.code().equals("STALE_FUEL_TELEMETRY")).count());
  }

  @Test void lowFuelCreatesFuelThenParkingChainAndQueueAffectsSelection() {
    AutomaticJobEngine engine = engine((asset, resource) -> route(100, 0));
    AutomationDecision result = engine.evaluate(snapshot(30, now, false, false), List.of(space("P1", 0, 0)),
        List.of(bay("NEAR_BUSY", "DIESEL", 1200), bay("FAR_FREE", "DIESEL", 0)), false, "FUEL_LEVEL");
    assertEquals(Action.FUEL_THEN_PARK, result.selectedAction());
    assertEquals("FAR_FREE", result.selectedResourceId());
    assertEquals(1, jobs.created.get());
  }

  @Test void normalFuelCreatesNoFuelingJobAndCriticalFuelWinsOverParking() {
    AutomaticJobEngine engine = engine((asset, resource) -> route(100, 0));
    AutomationDecision normal = engine.evaluate(snapshot(60, now, false, false),
        List.of(space("P1", 0, 0)), List.of(bay("B1", "DIESEL", 0)), true, "SIMULATION");
    assertEquals(Action.PARK, normal.selectedAction());
    AutomationDecision critical = engine.evaluate(snapshot(15, now, false, false),
        List.of(space("P1", 0, 0)), List.of(bay("B1", "DIESEL", 0)), true, "SIMULATION");
    assertEquals(Action.FUEL_THEN_PARK, critical.selectedAction());
  }

  @Test void emergencyFuelWithNoReachableStationRaisesCriticalAlert() {
    AutomationDecision result = engine((asset, resource) -> RouteMetrics.invalid("NO_SAFE_ROUTE"))
        .evaluate(snapshot(5, now, false, false), List.of(), List.of(bay("B1", "DIESEL", 0)), false, "FUEL_LEVEL");
    assertFalse(result.jobCreated());
    assertTrue(result.blockingReasons().contains("NO_SAFELY_REACHABLE_FUELING_STATION"));
    assertTrue(alerts.active().stream().anyMatch(a -> a.severity() == AutomationAlertService.Severity.CRITICAL));
  }

  @Test void fuelMismatchAndInsufficientRangeRejectStation() {
    AutomationDecision result = engine((asset, resource) -> route(100_000, 0))
        .evaluate(snapshot(15, now, false, false), List.of(), List.of(bay("B1", "PETROL", 0)), true, "SIMULATION");
    assertTrue(result.candidates().getFirst().rejectionReasons().contains("FUEL_TYPE_MISMATCH"));
    assertTrue(result.candidates().getFirst().rejectionReasons().contains("INSUFFICIENT_RANGE_TO_STATION"));
  }

  @Test void hysteresisPreventsOscillation() {
    AutomaticJobEngine engine = engine((asset, resource) -> route(100, 0));
    AutomationRule fuel = rules.resolve(AutomationRule.RuleKind.FUELING, snapshot(34, now, false, false), now).orElseThrow();
    assertEquals(AutomaticJobEngine.FuelLevel.LOW, engine.classifyFuel(snapshot(34, now, false, false), fuel));
    assertEquals(AutomaticJobEngine.FuelLevel.LOW, engine.classifyFuel(snapshot(36, now, false, false), fuel));
    assertEquals(AutomaticJobEngine.FuelLevel.NORMAL, engine.classifyFuel(snapshot(39, now, false, false), fuel));
  }

  @Test void concurrentSchedulersCannotReserveSameSpace() throws Exception {
    var shared = new AutomationReservationService();
    var executor = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    Callable<Boolean> attempt = () -> { gate.await(); return shared.reserve("PARKING_SPACE", "P1",
        UUID.randomUUID(), UUID.randomUUID().toString(), now, java.time.Duration.ofMinutes(10)).isPresent(); };
    Future<Boolean> first = executor.submit(attempt); Future<Boolean> second = executor.submit(attempt);
    gate.countDown();
    assertEquals(1, List.of(first.get(), second.get()).stream().filter(Boolean::booleanValue).count());
    executor.shutdown();
  }

  @Test void reservationCanBeExtendedAndReleased() {
    var reservation = reservations.reserve("PARKING_SPACE", "P1", UUID.randomUUID(), "key", now,
        java.time.Duration.ofMinutes(1)).orElseThrow();
    var extended = reservations.extend(reservation.id(), now.plusSeconds(30), java.time.Duration.ofMinutes(5));
    assertEquals(now.plusSeconds(330), extended.expiresAt());
    reservations.release(reservation.id());
    assertTrue(reservations.active(now.plusSeconds(31)).isEmpty());
  }

  @Test void parkingRequiresGeofenceStationaryOccupancyAndAcknowledgement() {
    var policy = new AutomationCompletionPolicy();
    var pending = policy.parking(new AutomationCompletionPolicy.ParkingEvidence(UUID.randomUUID(), "P1",
        true, 0, now.minusSeconds(30), false, true, now), 1, java.time.Duration.ofSeconds(20));
    assertFalse(pending.complete());
    assertEquals("WAITING_FOR_CONFIRMATION", pending.targetStatus());
    var complete = policy.parking(new AutomationCompletionPolicy.ParkingEvidence(UUID.randomUUID(), "P1",
        true, .5, now.minusSeconds(30), true, true, now), 1, java.time.Duration.ofSeconds(20));
    assertTrue(complete.complete());
  }

  @Test void fuelingDoesNotCompleteOnGpsArrivalAloneAndDetectsTransactionMismatch() {
    var policy = new AutomationCompletionPolicy();
    var result = policy.fueling(new AutomationCompletionPolicy.FuelTransaction(UUID.randomUUID(), "S", "B",
        "DIESEL", true, false, false, false, now, now.plusSeconds(60), 0, 20, 20, "TX"));
    assertFalse(result.complete());
    assertTrue(result.reasons().contains("FUEL_LEVEL_DID_NOT_INCREASE"));
  }

  private AutomaticJobEngine engine(AutomaticJobEngine.RouteEvaluator routes) {
    return new AutomaticJobEngine(rules, reservations, alerts, routes, jobs);
  }
  private RouteMetrics route(double distance, double congestion) { return new RouteMetrics(true, distance, distance / 5, congestion, 0, null); }
  private AutomationResource.ParkingSpace space(String id, double maneuver, double preference) {
    return new AutomationResource.ParkingSpace(id, "N", "YARD", true, ResourceState.AVAILABLE,
        Set.of(AssetType.ITV), 5, 60, 0, maneuver, 0, preference);
  }
  private AutomationResource.FuelingBay bay(String id, String fuel, double queue) {
    return new AutomationResource.FuelingBay(id, "S", "N", "SERVICE", true, ResourceState.AVAILABLE,
        Set.of(AssetType.ITV), 5, 60, fuel, queue > 0 ? 4 : 0, queue, 300, 0, 0);
  }
  private AssetAutomationSnapshot snapshot(double fuel, Instant telemetryAt, boolean activeJob, boolean expected) {
    return new AssetAutomationSnapshot(UUID.nameUUIDFromBytes("A1".getBytes()), "T", "YARD", AssetType.ITV,
        "G", true, new GeoPoint(25, 55), "A", telemetryAt, AssetStatus.IDLE,
        MaintenanceStatus.SERVICEABLE, false, false, false, true, true, true, activeJob,
        false, false, expected, null, now.minusSeconds(600), fuel, "DIESEL", fuel * 1000,
        new VehicleEnvelope(3, 2, 6, 30), Set.of("YARD", "SERVICE"), now);
  }
  private AssetAutomationSnapshot copy(AssetAutomationSnapshot a, Instant evaluated, Instant next, boolean expected) {
    return new AssetAutomationSnapshot(a.assetId(), a.terminalId(), a.zoneId(), a.assetType(), a.assetGroup(),
        a.enabled(), a.position(), a.mapNodeId(), a.telemetryAt(), a.status(), a.maintenanceStatus(),
        a.manuallyBlocked(), a.manualControl(), a.criticalAlert(), a.insideOperationalMap(),
        a.parkingAutomationAllowed(), a.fuelingAutomationAllowed(), a.activeMovementJob(),
        a.activeParkingJob(), a.activeFuelingJob(), expected, next, a.idleSince(), a.fuelPercent(),
        a.fuelType(), a.estimatedRangeMetres(), a.envelope(), a.permittedZones(), evaluated);
  }
  private static final class RecordingJobs implements AutomaticJobEngine.AutomaticJobCreator {
    private final AtomicInteger created = new AtomicInteger();
    @Override public UUID create(Action action, AssetAutomationSnapshot asset, String rule, String resource,
                                 String key, UUID reservation, UUID parent) { created.incrementAndGet(); return UUID.randomUUID(); }
    @Override public boolean hasActiveEquivalent(UUID asset, Action action) { return false; }
  }
}
