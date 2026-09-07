package com.dpworld.fms.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Asset(UUID id, String fleetNumber, AssetType type, GeoPoint position,
                    double heading, double speedKph, AssetStatus status, Availability availability,
                    double energyPercent, double odometerKm, double engineHours, String locationId,
                    UUID currentJobId, Instant lastTelemetryAt, String driverId, String deviceId,
                    String trackItId, MaintenanceStatus maintenanceStatus, Set<String> capabilities,
                    VehicleEnvelope envelope) {
  public Asset {
    if (id == null || fleetNumber == null || fleetNumber.isBlank() || type == null) throw new IllegalArgumentException("asset identity is required");
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    if (energyPercent < 0 || energyPercent > 100) throw new IllegalArgumentException("energy percent must be 0..100");
  }
  public boolean isFresh(Instant now, long freshnessSeconds) { return lastTelemetryAt != null && lastTelemetryAt.plusSeconds(freshnessSeconds).isAfter(now); }
  public boolean canPerform(Set<String> required) { return availability == Availability.AVAILABLE && maintenanceStatus == MaintenanceStatus.SERVICEABLE && capabilities.containsAll(required); }
}
