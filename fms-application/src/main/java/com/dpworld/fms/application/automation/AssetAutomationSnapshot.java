package com.dpworld.fms.application.automation;

import com.dpworld.fms.domain.AssetStatus;
import com.dpworld.fms.domain.AssetType;
import com.dpworld.fms.domain.GeoPoint;
import com.dpworld.fms.domain.MaintenanceStatus;
import com.dpworld.fms.domain.VehicleEnvelope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** All values used by one deterministic rule evaluation. */
public record AssetAutomationSnapshot(
    UUID assetId, String terminalId, String zoneId, AssetType assetType, String assetGroup,
    boolean enabled, GeoPoint position, String mapNodeId, Instant telemetryAt, AssetStatus status,
    MaintenanceStatus maintenanceStatus, boolean manuallyBlocked, boolean manualControl,
    boolean criticalAlert, boolean insideOperationalMap, boolean parkingAutomationAllowed,
    boolean fuelingAutomationAllowed, boolean activeMovementJob, boolean activeParkingJob,
    boolean activeFuelingJob, boolean higherPriorityJobExpected, Instant nextJobAt,
    Instant idleSince, double fuelPercent, String fuelType, double estimatedRangeMetres,
    VehicleEnvelope envelope, Set<String> permittedZones, Instant evaluatedAt) {

  public AssetAutomationSnapshot {
    permittedZones = Set.copyOf(permittedZones == null ? Set.of() : permittedZones);
  }
}
