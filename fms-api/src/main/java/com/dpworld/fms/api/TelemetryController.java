package com.dpworld.fms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {
  private final TelemetryIngestionService ingestion;
  public TelemetryController(TelemetryIngestionService ingestion) { this.ingestion = ingestion; }

  @PostMapping("/assets/{assetId}/position")
  @PreAuthorize("hasAuthority('vehicle.manage')")
  public TelemetryIngestionService.Result position(@PathVariable("assetId") UUID assetId,
                                                    @Valid @RequestBody PositionTelemetry message) {
    return ingestion.accept(assetId, message);
  }

  public record PositionTelemetry(
      @NotBlank String messageId,
      @NotBlank String schemaVersion,
      String correlationId,
      @NotNull Instant occurredAt,
      @NotBlank String fleetNumber,
      @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,39}") String assetType,
      String plantCode,
      @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
      @NotNull @DecimalMin("0") @DecimalMax("360") Double heading,
      @NotNull @DecimalMin("0") Double speedKph,
      @NotNull @DecimalMin("0") @DecimalMax("100") Double energyPercent,
      @Pattern(regexp="DIESEL|PETROL|LNG|ELECTRIC|HYBRID") String energySource,
      @NotBlank @Pattern(regexp="IDLE|WORKING|PARKED|FUELLING|CHARGING|MAINTENANCE|OFFLINE|FAULT") String operationalStatus,
      @NotBlank @Pattern(regexp="AVAILABLE|RESERVED|ASSIGNED|UNAVAILABLE") String availabilityStatus,
      String deviceId,
      String trackItId) {
    public PositionTelemetry { if (energySource == null) energySource = "DIESEL"; }
  }
}
