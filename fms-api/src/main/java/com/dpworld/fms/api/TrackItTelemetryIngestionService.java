package com.dpworld.fms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrackItTelemetryIngestionService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final TrackItTelemetryEvents events;
  private final Clock clock;
  private final boolean autoCreateAssets;

  public TrackItTelemetryIngestionService(JdbcTemplate jdbc, ObjectMapper json,
      TransactionTemplate transactions, TrackItTelemetryEvents events,
      @Value("${trackit.telemetry.auto-create-assets:false}") boolean autoCreateAssets) {
    this(jdbc, json, transactions, events, Clock.systemUTC(), autoCreateAssets);
  }

  TrackItTelemetryIngestionService(JdbcTemplate jdbc, ObjectMapper json,
      TransactionTemplate transactions, TrackItTelemetryEvents events,
      Clock clock, boolean autoCreateAssets) {
    this.jdbc = jdbc;
    this.json = json;
    this.transactions = transactions;
    this.events = events;
    this.clock = clock;
    this.autoCreateAssets = autoCreateAssets;
  }

  public BatchResult ingest(List<JsonNode> items, String actor) {
    UUID batchId = UUID.randomUUID();
    Instant receivedAt = clock.instant();
    jdbc.update("INSERT INTO trackit_telemetry_batches(id,received_at,received_count,requested_by) VALUES (?,?,?,?)",
        batchId, timestamp(receivedAt), items.size(), actor);
    List<ItemResult> results = new ArrayList<>();
    for (int index = 0; index < items.size(); index++) {
      final int itemIndex = index;
      JsonNode raw = items.get(index);
      ItemResult result;
      try {
        result = transactions.execute(status -> ingestOne(batchId, itemIndex, raw, receivedAt));
      } catch (RuntimeException exception) {
        String assetId = text(raw, "AssetID");
        result = new ItemResult(index, assetId, "REJECTED", safeMessage(exception));
        persistResult(batchId, result);
      }
      results.add(result);
    }
    int accepted = (int) results.stream().filter(ItemResult::accepted).count();
    int duplicate = (int) results.stream().filter(result -> "DUPLICATE".equals(result.status())).count();
    int rejected = (int) results.stream().filter(result -> "REJECTED".equals(result.status())).count();
    jdbc.update("UPDATE trackit_telemetry_batches SET completed_at=now(),accepted_count=?,rejected_count=?,duplicate_count=? WHERE id=?",
        accepted, rejected, duplicate, batchId);
    return new BatchResult(batchId, items.size(), accepted, rejected, duplicate, List.copyOf(results));
  }

  private ItemResult ingestOne(UUID batchId, int index, JsonNode raw, Instant receivedAt) {
    if (raw == null || !raw.isObject()) throw new IllegalArgumentException("item must be a JSON object");
    TrackItTelemetryPayload payload;
    try {
      payload = json.treeToValue(raw, TrackItTelemetryPayload.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid field type or timestamp");
    }
    validate(payload);
    UUID assetId = resolveAsset(payload, receivedAt);
    boolean gps = Boolean.TRUE.equals(payload.availability().gpsAvailable());
    boolean fuel = Boolean.TRUE.equals(payload.availability().fuelDataAvailable());
    boolean engine = Boolean.TRUE.equals(payload.availability().engineDataAvailable());
    boolean can = Boolean.TRUE.equals(payload.availability().canDataAvailable());
    UUID telemetryId = UUID.randomUUID();
    long sourceLatency = Duration.between(payload.timestamp(), payload.lastUpdatedTime()).getSeconds();
    long ingestionLatency = Duration.between(payload.timestamp(), receivedAt).getSeconds();
    String canJson = write(can && payload.canParameters() != null ? payload.canParameters() : List.of());
    int inserted = jdbc.update("""
        INSERT INTO trackit_telemetry_history(id,batch_id,batch_index,asset_id,external_asset_id,asset_number,
          measurement_timestamp,source_updated_at,received_at,latitude,longitude,speed_kph,battery_voltage,
          fuel_consumption_rate,total_fuel_used,engine_hours,location_mapping_status,location_type,location_name,
          location_code,gps_available,fuel_data_available,engine_data_available,can_data_available,can_parameters,
          raw_payload,source_latency_seconds,ingestion_latency_seconds)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)
        ON CONFLICT (external_asset_id,measurement_timestamp) DO NOTHING
        """, telemetryId, batchId, index, assetId, payload.assetId().trim(), payload.assetNumber().trim(),
        timestamp(payload.timestamp()), timestamp(payload.lastUpdatedTime()), timestamp(receivedAt),
        gps ? payload.latitude() : null, gps ? payload.longitude() : null, gps ? payload.speed() : null,
        payload.batteryVoltage(), fuel ? payload.fuelConsumptionRate() : null,
        fuel ? payload.totalFuelUsed() : null, engine ? payload.engineHours() : null,
        location(payload, TrackItTelemetryPayload.Location::mappingStatus),
        location(payload, TrackItTelemetryPayload.Location::type),
        location(payload, TrackItTelemetryPayload.Location::name),
        location(payload, TrackItTelemetryPayload.Location::code), gps, fuel, engine, can, canJson,
        raw.toString(), sourceLatency, ingestionLatency);
    if (inserted == 0) {
      ItemResult duplicate = new ItemResult(index, payload.assetId(), "DUPLICATE", null);
      persistResult(batchId, duplicate);
      return duplicate;
    }

    int latest = updateLatest(assetId, telemetryId, payload, receivedAt, gps, fuel, engine, can,
        canJson, sourceLatency, ingestionLatency);
    updateAssetState(assetId, payload, gps);
    if (latest == 1 && gps) {
      jdbc.update("INSERT INTO asset_positions(asset_id,recorded_at,latitude,longitude,heading,speed_kph) VALUES (?,?,?,?,?,?)",
          assetId, timestamp(payload.timestamp()), payload.latitude(), payload.longitude(), null, payload.speed());
    }
    String status = latest == 1 ? "ACCEPTED" : "OUT_OF_ORDER";
    ItemResult result = new ItemResult(index, payload.assetId(), status,
        latest == 1 ? null : "stored in history; newer last-known telemetry retained");
    persistResult(batchId, result);
    if (latest == 1 && TransactionSynchronizationManager.isSynchronizationActive()) {
      Map<String, Object> event = Map.of("assetId", payload.assetId(), "assetNumber", payload.assetNumber(),
          "measurementTimestamp", payload.timestamp(), "gpsAvailable", gps);
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { events.publish(event); }
      });
    }
    return result;
  }

  private int updateLatest(UUID assetId, UUID telemetryId, TrackItTelemetryPayload payload,
      Instant receivedAt, boolean gps, boolean fuel, boolean engine, boolean can, String canJson,
      long sourceLatency, long ingestionLatency) {
    return jdbc.update("""
        INSERT INTO trackit_telemetry_latest(asset_id,telemetry_id,external_asset_id,asset_number,make,model,
          measurement_timestamp,source_updated_at,received_at,latitude,longitude,last_valid_latitude,
          last_valid_longitude,last_valid_gps_at,speed_kph,battery_voltage,fuel_consumption_rate,total_fuel_used,
          engine_hours,location_mapping_status,location_type,location_name,location_code,gps_available,
          fuel_data_available,engine_data_available,can_data_available,can_parameters,source_latency_seconds,
          ingestion_latency_seconds)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
        ON CONFLICT (asset_id) DO UPDATE SET telemetry_id=EXCLUDED.telemetry_id,
          external_asset_id=EXCLUDED.external_asset_id,asset_number=EXCLUDED.asset_number,
          make=EXCLUDED.make,model=EXCLUDED.model,measurement_timestamp=EXCLUDED.measurement_timestamp,
          source_updated_at=EXCLUDED.source_updated_at,received_at=EXCLUDED.received_at,
          latitude=EXCLUDED.latitude,longitude=EXCLUDED.longitude,
          last_valid_latitude=COALESCE(EXCLUDED.last_valid_latitude,trackit_telemetry_latest.last_valid_latitude),
          last_valid_longitude=COALESCE(EXCLUDED.last_valid_longitude,trackit_telemetry_latest.last_valid_longitude),
          last_valid_gps_at=COALESCE(EXCLUDED.last_valid_gps_at,trackit_telemetry_latest.last_valid_gps_at),
          speed_kph=EXCLUDED.speed_kph,battery_voltage=EXCLUDED.battery_voltage,
          fuel_consumption_rate=EXCLUDED.fuel_consumption_rate,total_fuel_used=EXCLUDED.total_fuel_used,
          engine_hours=EXCLUDED.engine_hours,location_mapping_status=EXCLUDED.location_mapping_status,
          location_type=EXCLUDED.location_type,location_name=EXCLUDED.location_name,location_code=EXCLUDED.location_code,
          gps_available=EXCLUDED.gps_available,fuel_data_available=EXCLUDED.fuel_data_available,
          engine_data_available=EXCLUDED.engine_data_available,can_data_available=EXCLUDED.can_data_available,
          can_parameters=EXCLUDED.can_parameters,source_latency_seconds=EXCLUDED.source_latency_seconds,
          ingestion_latency_seconds=EXCLUDED.ingestion_latency_seconds
        WHERE trackit_telemetry_latest.measurement_timestamp < EXCLUDED.measurement_timestamp
        """, assetId, telemetryId, payload.assetId().trim(), payload.assetNumber().trim(), blank(payload.make()),
        blank(payload.model()), timestamp(payload.timestamp()), timestamp(payload.lastUpdatedTime()),
        timestamp(receivedAt), gps ? payload.latitude() : null, gps ? payload.longitude() : null,
        gps ? payload.latitude() : null, gps ? payload.longitude() : null,
        gps ? timestamp(payload.timestamp()) : null, gps ? payload.speed() : null, payload.batteryVoltage(),
        fuel ? payload.fuelConsumptionRate() : null, fuel ? payload.totalFuelUsed() : null,
        engine ? payload.engineHours() : null, location(payload, TrackItTelemetryPayload.Location::mappingStatus),
        location(payload, TrackItTelemetryPayload.Location::type), location(payload, TrackItTelemetryPayload.Location::name),
        location(payload, TrackItTelemetryPayload.Location::code), gps, fuel, engine, can, canJson,
        sourceLatency, ingestionLatency);
  }

  private UUID resolveAsset(TrackItTelemetryPayload payload, Instant receivedAt) {
    List<UUID> matches = jdbc.queryForList("SELECT id FROM assets WHERE trackit_id=? OR fleet_number=?",
        UUID.class, payload.assetId().trim(), payload.assetNumber().trim());
    if (matches.size() > 1) throw new IllegalArgumentException("ASSET_IDENTITY_CONFLICT");
    UUID existing = matches.isEmpty() ? null : matches.getFirst();
    if (existing == null && !autoCreateAssets) throw new IllegalArgumentException("ASSET_NOT_REGISTERED");
    if (existing == null) {
      UUID type = jdbc.query("SELECT id FROM asset_types WHERE code='TRACKIT'",
          result -> result.next() ? result.getObject(1, UUID.class) : null);
      if (type == null) {
        type = UUID.randomUUID();
        jdbc.update("INSERT INTO asset_types(id,code,name) VALUES (?,'TRACKIT','TrackIT imported asset') ON CONFLICT(code) DO NOTHING", type);
        type = jdbc.queryForObject("SELECT id FROM asset_types WHERE code='TRACKIT'", UUID.class);
      }
      existing = UUID.randomUUID();
      jdbc.update("""
          INSERT INTO assets(id,fleet_number,asset_type_id,operational_status,availability_status,
            maintenance_status,enabled,trackit_id)
          VALUES (?,?,?,'OFFLINE','UNAVAILABLE','SERVICEABLE',TRUE,?)
          """, existing, payload.assetNumber().trim(), type, payload.assetId().trim());
    } else {
      jdbc.update("UPDATE assets SET trackit_id=COALESCE(trackit_id,?) WHERE id=?", payload.assetId().trim(), existing);
    }
    jdbc.update("""
        INSERT INTO trackit_asset_metadata(asset_id,external_asset_id,source_asset_number,make,model,first_seen_at,last_seen_at)
        VALUES (?,?,?,?,?,?,?) ON CONFLICT(asset_id) DO UPDATE SET
          source_asset_number=EXCLUDED.source_asset_number,make=EXCLUDED.make,model=EXCLUDED.model,
          last_seen_at=EXCLUDED.last_seen_at
        """, existing, payload.assetId().trim(), payload.assetNumber().trim(), blank(payload.make()),
        blank(payload.model()), timestamp(receivedAt), timestamp(receivedAt));
    return existing;
  }

  private void updateAssetState(UUID assetId, TrackItTelemetryPayload payload, boolean gps) {
    jdbc.update("""
        UPDATE assets SET latitude=CASE WHEN ? THEN ? ELSE latitude END,
          longitude=CASE WHEN ? THEN ? ELSE longitude END,
          speed_kph=CASE WHEN ? THEN ? ELSE NULL END,
          operational_status=CASE WHEN ? THEN CASE WHEN ?>0 THEN 'WORKING' ELSE 'IDLE' END ELSE operational_status END,
          last_telemetry_at=?,version=version+1
        WHERE id=? AND (last_telemetry_at IS NULL OR last_telemetry_at < ?)
        """, gps, payload.latitude(), gps, payload.longitude(), gps, payload.speed(), gps,
        payload.speed(), timestamp(payload.timestamp()), assetId, timestamp(payload.timestamp()));
  }

  private void validate(TrackItTelemetryPayload payload) {
    List<String> errors = new ArrayList<>();
    if (payload.assetId() == null || payload.assetId().isBlank()) errors.add("AssetID is required");
    if (payload.assetNumber() == null || payload.assetNumber().isBlank()) errors.add("AssetNumber is required");
    if (payload.assetId() != null && payload.assetId().length() > 160) errors.add("AssetID exceeds 160 characters");
    if (payload.assetNumber() != null && payload.assetNumber().length() > 80) errors.add("AssetNumber exceeds 80 characters");
    if (payload.timestamp() == null) errors.add("Timestamp is required");
    if (payload.lastUpdatedTime() == null) errors.add("LastUpdatedTime is required");
    if (payload.availability() == null) errors.add("DataAvailabilityFlag is required");
    if (payload.availability() != null && payload.availability().gpsAvailable() == null) errors.add("GPSAvailable is required");
    if (payload.availability() != null && payload.availability().fuelDataAvailable() == null) errors.add("FuelDataAvailable is required");
    if (payload.availability() != null && payload.availability().engineDataAvailable() == null) errors.add("EngineDataAvailable is required");
    if (payload.availability() != null && payload.availability().canDataAvailable() == null) errors.add("CANDataAvailable is required");
    boolean gps = payload.availability() != null && Boolean.TRUE.equals(payload.availability().gpsAvailable());
    if (payload.latitude() != null && (payload.latitude() < -90 || payload.latitude() > 90)) errors.add("Latitude must be WGS84");
    if (payload.longitude() != null && (payload.longitude() < -180 || payload.longitude() > 180)) errors.add("Longitude must be WGS84");
    if (gps && (payload.latitude() == null || payload.longitude() == null)) errors.add("GPS coordinates are required when GPSAvailable is true");
    if (gps && payload.speed() == null) errors.add("Speed is required when GPSAvailable is true");
    if (payload.batteryVoltage() == null) errors.add("BatteryVoltage is required");
    if (payload.latitude() != null && payload.longitude() != null && payload.latitude() == 0 && payload.longitude() == 0) errors.add("coordinates 0,0 are invalid");
    nonNegative(errors, "Speed", payload.speed());
    nonNegative(errors, "BatteryVoltage", payload.batteryVoltage());
    nonNegative(errors, "FuelConsumptionRate", payload.fuelConsumptionRate());
    nonNegative(errors, "TotalFuelUsed", payload.totalFuelUsed());
    nonNegative(errors, "EngineHours", payload.engineHours());
    if (payload.canParameters() != null) {
      for (TrackItTelemetryPayload.CanParameter parameter : payload.canParameters()) {
        if (parameter.name() == null || parameter.name().isBlank()) errors.add("CAN parameter Name is required");
        if (isOdometer(parameter.name()) && negative(parameter.value())) errors.add(parameter.name() + " cannot be negative");
      }
    }
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
  }

  private void persistResult(UUID batchId, ItemResult result) {
    jdbc.update("INSERT INTO trackit_telemetry_batch_results(batch_id,batch_index,external_asset_id,status,message) VALUES (?,?,?,?,?)",
        batchId, result.index(), fit(blank(result.assetId()), 160), result.status(), result.message());
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception exception) { throw new IllegalArgumentException("payload cannot be serialized"); }
  }
  private String safeMessage(RuntimeException exception) {
    if (!(exception instanceof IllegalArgumentException)) return "telemetry item could not be processed";
    String message = exception.getMessage();
    return message == null ? "telemetry item rejected" : message.substring(0, Math.min(500, message.length()));
  }
  private String text(JsonNode node, String field) { return node != null && node.has(field) ? node.path(field).asText(null) : null; }
  private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private String fit(String value, int maximum) { return value == null || value.length() <= maximum ? value : value.substring(0, maximum); }
  private <T> String location(TrackItTelemetryPayload payload, java.util.function.Function<TrackItTelemetryPayload.Location, T> getter) {
    return payload.location() == null || getter.apply(payload.location()) == null ? null : String.valueOf(getter.apply(payload.location()));
  }
  private void nonNegative(List<String> errors, String name, Number value) { if (value != null && new BigDecimal(value.toString()).signum() < 0) errors.add(name + " cannot be negative"); }
  private boolean isOdometer(String name) { return name != null && name.toLowerCase().contains("odometer"); }
  private boolean negative(JsonNode value) {
    if (value == null) return false;
    try { return new BigDecimal(value.isTextual() ? value.textValue() : value.asText()).signum() < 0; }
    catch (NumberFormatException exception) { return false; }
  }
  private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

  public record ItemResult(int index, String assetId, String status, String message) {
    boolean accepted() { return "ACCEPTED".equals(status) || "OUT_OF_ORDER".equals(status); }
  }
  public record BatchResult(UUID batchId, int received, int accepted, int rejected, int duplicate,
                            List<ItemResult> results) {}
}
