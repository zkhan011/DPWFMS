package com.dpworld.fms.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TrackItTelemetryPayload(
    @JsonProperty("AssetID") String assetId,
    @JsonProperty("AssetNumber") String assetNumber,
    @JsonProperty("Make") String make,
    @JsonProperty("Model") String model,
    @JsonProperty("Timestamp") Instant timestamp,
    @JsonProperty("LastUpdatedTime") Instant lastUpdatedTime,
    @JsonProperty("Latitude") Double latitude,
    @JsonProperty("Longitude") Double longitude,
    @JsonProperty("Speed") Double speed,
    @JsonProperty("BatteryVoltage") Double batteryVoltage,
    @JsonProperty("FuelConsumptionRate") BigDecimal fuelConsumptionRate,
    @JsonProperty("TotalFuelUsed") BigDecimal totalFuelUsed,
    @JsonProperty("EngineHours") BigDecimal engineHours,
    @JsonProperty("Location") Location location,
    @JsonProperty("DataAvailabilityFlag") Availability availability,
    @JsonProperty("CANParameters") List<CanParameter> canParameters) {

  public record Location(
      @JsonProperty("LocationMappingStatus") String mappingStatus,
      @JsonProperty("LocationType") String type,
      @JsonProperty("LocationName") String name,
      @JsonProperty("LocationCode") String code) {}

  public record Availability(
      @JsonProperty("GPSAvailable") Boolean gpsAvailable,
      @JsonProperty("FuelDataAvailable") Boolean fuelDataAvailable,
      @JsonProperty("EngineDataAvailable") Boolean engineDataAvailable,
      @JsonProperty("CANDataAvailable") Boolean canDataAvailable) {}

  public record CanParameter(
      @JsonProperty("Name") String name,
      @JsonProperty("Value") JsonNode value) {}
}
