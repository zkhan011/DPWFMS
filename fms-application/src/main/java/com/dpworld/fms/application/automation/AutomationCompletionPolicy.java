package com.dpworld.fms.application.automation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Safety gates for terminal-state changes; GPS arrival alone is never sufficient. */
public final class AutomationCompletionPolicy {
  public CompletionResult parking(ParkingEvidence evidence, double stationarySpeedKph,
                                  Duration stationaryPeriod) {
    List<String> missing = new ArrayList<>();
    if (!evidence.insideDestinationGeofence()) missing.add("OUTSIDE_DESTINATION_GEOFENCE");
    if (evidence.speedKph() > stationarySpeedKph) missing.add("ASSET_NOT_STATIONARY");
    if (evidence.stationarySince() == null || evidence.stationarySince().plus(stationaryPeriod)
        .isAfter(evidence.observedAt())) missing.add("STATIONARY_CONFIRMATION_PENDING");
    if (!evidence.occupancyConfirmed()) missing.add("OCCUPANCY_NOT_CONFIRMED");
    if (!evidence.deviceAcknowledged()) missing.add("DEVICE_ACKNOWLEDGEMENT_MISSING");
    return new CompletionResult(missing.isEmpty(), missing.isEmpty() ? "COMPLETED"
        : evidence.insideDestinationGeofence() ? "WAITING_FOR_CONFIRMATION" : "IN_PROGRESS", missing);
  }

  public CompletionResult fueling(FuelTransaction evidence) {
    List<String> missing = new ArrayList<>();
    if (!evidence.arrivedAtBay()) missing.add("ASSET_NOT_AT_BAY");
    if (!evidence.serviceStarted()) missing.add("SERVICE_NOT_STARTED");
    if (!evidence.completionConfirmed()) missing.add("COMPLETION_SIGNAL_MISSING");
    if (!evidence.serviceReleased()) missing.add("SERVICE_RELEASE_MISSING");
    if (evidence.transactionReference() == null || evidence.transactionReference().isBlank()) missing.add("TRANSACTION_REFERENCE_MISSING");
    if (evidence.quantityDispensed() <= 0) missing.add("INVALID_DISPENSED_QUANTITY");
    if (evidence.endingFuelPercent() <= evidence.startingFuelPercent()) missing.add("FUEL_LEVEL_DID_NOT_INCREASE");
    if (evidence.completedAt() == null || evidence.startedAt() == null
        || evidence.completedAt().isBefore(evidence.startedAt())) missing.add("INVALID_TRANSACTION_TIME");
    return new CompletionResult(missing.isEmpty(), missing.isEmpty() ? "COMPLETED" : "WAITING_FOR_CONFIRMATION", missing);
  }

  public record ParkingEvidence(UUID assetId, String parkingSpaceId, boolean insideDestinationGeofence,
                                double speedKph, Instant stationarySince, boolean occupancyConfirmed,
                                boolean deviceAcknowledged, Instant observedAt) {}
  public record FuelTransaction(UUID assetId, String stationId, String bayId, String fuelType,
                                boolean arrivedAtBay, boolean serviceStarted, boolean completionConfirmed,
                                boolean serviceReleased, Instant startedAt, Instant completedAt,
                                double quantityDispensed, double startingFuelPercent,
                                double endingFuelPercent, String transactionReference) {}
  public record CompletionResult(boolean complete, String targetStatus, List<String> reasons) {
    public CompletionResult { reasons = List.copyOf(reasons); }
  }
}
