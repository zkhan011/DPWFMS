package com.dpworld.fms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/integrations/trackit/telemetry")
public class TrackItTelemetryController {
  private static final Set<String> SORT_COLUMNS = Set.of("assetNumber", "make", "model",
      "measurementTimestamp", "speedKph", "batteryVoltage", "locationCode");
  private final TrackItTelemetryIngestionService ingestion;
  private final TrackItTelemetryEvents events;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final long staleSeconds;
  private final long offlineSeconds;
  private final String fuelRateUnit;
  private final String canMetadataJson;
  private final Double lowBatteryVoltage;

  public TrackItTelemetryController(TrackItTelemetryIngestionService ingestion,
      TrackItTelemetryEvents events, JdbcTemplate jdbc, ObjectMapper json,
      @Value("${trackit.telemetry.stale-threshold-seconds:120}") long staleSeconds,
      @Value("${trackit.telemetry.offline-threshold-seconds:600}") long offlineSeconds,
      @Value("${trackit.telemetry.fuel-consumption-rate-unit:}") String fuelRateUnit,
      @Value("${trackit.telemetry.can-metadata-json:{}}") String canMetadataJson,
      @Value("${trackit.telemetry.alert-low-battery-voltage:}") String lowBatteryVoltage) {
    if (staleSeconds <= 0 || offlineSeconds <= staleSeconds) {
      throw new IllegalStateException("TrackIT offline threshold must exceed the positive stale threshold");
    }
    this.ingestion = ingestion;
    this.events = events;
    this.jdbc = jdbc;
    this.json = json;
    this.staleSeconds = staleSeconds;
    this.offlineSeconds = offlineSeconds;
    this.fuelRateUnit = fuelRateUnit;
    this.canMetadataJson = canMetadataJson;
    this.lowBatteryVoltage = lowBatteryVoltage.isBlank() ? null : Double.valueOf(lowBatteryVoltage);
  }

  @PostMapping("/batch")
  @PreAuthorize("hasAuthority('trackit.telemetry.ingest')")
  public TrackItTelemetryIngestionService.BatchResult batch(@RequestBody JsonNode payload,
      Principal principal) {
    if (payload == null || !payload.isArray()) throw new IllegalArgumentException("top-level payload must be a JSON array");
    List<JsonNode> items = new ArrayList<>();
    payload.forEach(items::add);
    return ingestion.ingest(items, principal.getName());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('trackit.telemetry.ingest')")
  public TrackItTelemetryIngestionService.ItemResult one(@RequestBody JsonNode payload,
      Principal principal) {
    if (payload == null || !payload.isObject()) throw new IllegalArgumentException("top-level payload must be a JSON object");
    return ingestion.ingest(List.of(payload), principal.getName()).results().getFirst();
  }

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public SseEmitter events() { return events.subscribe(); }

  @GetMapping("/summary")
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public Map<String, Object> summary() {
    return jdbc.queryForMap("""
        SELECT count(*) total,
          count(*) FILTER (WHERE measurement_timestamp>=now()-(?*interval '1 second')) online,
          count(*) FILTER (WHERE measurement_timestamp<now()-(?*interval '1 second') AND measurement_timestamp>=now()-(?*interval '1 second')) stale,
          count(*) FILTER (WHERE measurement_timestamp<now()-(?*interval '1 second')) offline,
          count(*) FILTER (WHERE gps_available AND speed_kph>0) moving,
          count(*) FILTER (WHERE gps_available AND coalesce(speed_kph,0)=0) stationary,
          count(*) FILTER (WHERE NOT gps_available) gps_unavailable,
          count(*) FILTER (WHERE fuel_data_available) fuel_available,
          count(*) FILTER (WHERE engine_data_available) engine_available,
          count(*) FILTER (WHERE can_data_available) can_available,
          count(*) FILTER (WHERE upper(coalesce(location_mapping_status,'')) IN ('OUTSIDE','UNMAPPED','NOT_MAPPED')) outside_location
        FROM trackit_telemetry_latest
        """, staleSeconds, staleSeconds, offlineSeconds, offlineSeconds);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public Map<String, Object> list(
      @RequestParam(name="q", defaultValue="") String query,
      @RequestParam(name="make", defaultValue="") String make,
      @RequestParam(name="model", defaultValue="") String model,
      @RequestParam(name="freshness", defaultValue="") String freshness,
      @RequestParam(name="motion", defaultValue="") String motion,
      @RequestParam(name="gpsAvailable", required=false) Boolean gpsAvailable,
      @RequestParam(name="fuelAvailable", required=false) Boolean fuelAvailable,
      @RequestParam(name="engineAvailable", required=false) Boolean engineAvailable,
      @RequestParam(name="canAvailable", required=false) Boolean canAvailable,
      @RequestParam(name="locationStatus", defaultValue="") String locationStatus,
      @RequestParam(name="locationCode", defaultValue="") String locationCode,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="25") int size,
      @RequestParam(name="sort", defaultValue="measurementTimestamp") String sort,
      @RequestParam(name="direction", defaultValue="desc") String direction) {
    if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("invalid page or size");
    if (!SORT_COLUMNS.contains(sort)) throw new IllegalArgumentException("unsupported sort field");
    String sortColumn = switch (sort) {
      case "assetNumber" -> "asset_number"; case "measurementTimestamp" -> "measurement_timestamp";
      case "speedKph" -> "speed_kph"; case "batteryVoltage" -> "battery_voltage";
      case "locationCode" -> "location_code"; default -> sort;
    };
    String order = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
    List<Object> arguments = new ArrayList<>();
    String where = filters(query, make, model, freshness, motion, gpsAvailable, fuelAvailable,
        engineAvailable, canAvailable, locationStatus, locationCode, arguments);
    long total = jdbc.queryForObject("SELECT count(*) FROM trackit_telemetry_latest WHERE " + where,
        Long.class, arguments.toArray());
    List<Object> pageArguments = new ArrayList<>(arguments);
    pageArguments.add(size); pageArguments.add(page * size);
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT *," + freshnessSql()
        + " freshness FROM trackit_telemetry_latest WHERE " + where + " ORDER BY "
        + sortColumn + " " + order + " NULLS LAST LIMIT ? OFFSET ?", pageArguments.toArray());
    rows.forEach(this::decorate);
    return Map.of("content", rows, "page", page, "size", size, "totalElements", total,
        "totalPages", (total + size - 1) / size, "fuelConsumptionRateUnit", fuelRateUnit);
  }

  @GetMapping("/{assetId}")
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public Map<String, Object> latest(@PathVariable("assetId") String assetId) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT *," + freshnessSql()
        + " freshness FROM trackit_telemetry_latest WHERE external_asset_id=?", assetId);
    if (rows.isEmpty()) throw new IllegalArgumentException("unknown TrackIT asset " + assetId);
    Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
    decorate(result);
    result.put("canMetadata", parseJson(canMetadataJson));
    result.put("fuelConsumptionRateUnit", fuelRateUnit);
    return result;
  }

  @GetMapping("/{assetId}/history")
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public List<Map<String, Object>> history(@PathVariable("assetId") String assetId,
      @RequestParam(name="from") Instant from, @RequestParam(name="to") Instant to,
      @RequestParam(name="limit", defaultValue="500") int limit) {
    if (from.isAfter(to) || limit < 1 || limit > 5000) throw new IllegalArgumentException("invalid history range or limit");
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT id,external_asset_id,asset_number,measurement_timestamp,source_updated_at,received_at,
          latitude,longitude,speed_kph,battery_voltage,fuel_consumption_rate,total_fuel_used,engine_hours,
          location_mapping_status,location_type,location_name,location_code,gps_available,
          fuel_data_available,engine_data_available,can_data_available,can_parameters::text can_parameters,
          source_latency_seconds,ingestion_latency_seconds
        FROM trackit_telemetry_history WHERE external_asset_id=? AND measurement_timestamp BETWEEN ? AND ?
        ORDER BY measurement_timestamp DESC LIMIT ?
        """, assetId, Timestamp.from(from), Timestamp.from(to), limit);
    rows.forEach(this::decorate);
    return rows;
  }

  @GetMapping("/can/parameter-names")
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public List<String> canNames() {
    return jdbc.queryForList("""
        SELECT DISTINCT parameter->>'Name' FROM trackit_telemetry_history,
          jsonb_array_elements(can_parameters) parameter
        WHERE parameter ? 'Name' ORDER BY 1
        """, String.class);
  }

  @GetMapping("/map/positions")
  @PreAuthorize("hasAuthority('trackit.telemetry.read')")
  public List<Map<String, Object>> positions() {
    return jdbc.queryForList("SELECT asset_id id,external_asset_id,asset_number,last_valid_latitude latitude,last_valid_longitude longitude,last_valid_gps_at,gps_available,measurement_timestamp,speed_kph,location_mapping_status,location_name,location_code," + freshnessSql() + " freshness FROM trackit_telemetry_latest WHERE last_valid_latitude IS NOT NULL");
  }

  private String filters(String query, String make, String model, String freshness, String motion,
      Boolean gps, Boolean fuel, Boolean engine, Boolean can, String locationStatus,
      String locationCode, List<Object> arguments) {
    StringBuilder sql = new StringBuilder("1=1");
    textFilter(sql, arguments, "(lower(external_asset_id) LIKE ? OR lower(asset_number) LIKE ?)", query, true);
    textFilter(sql, arguments, "lower(make)=?", make, false);
    textFilter(sql, arguments, "lower(model)=?", model, false);
    textFilter(sql, arguments, "lower(location_mapping_status)=?", locationStatus, false);
    textFilter(sql, arguments, "lower(location_code)=?", locationCode, false);
    booleanFilter(sql, arguments, "gps_available", gps); booleanFilter(sql, arguments, "fuel_data_available", fuel);
    booleanFilter(sql, arguments, "engine_data_available", engine); booleanFilter(sql, arguments, "can_data_available", can);
    if (!freshness.isBlank()) {
      sql.append(" AND ").append(freshnessSql()).append("=?"); arguments.add(freshness.toUpperCase());
    }
    if ("MOVING".equalsIgnoreCase(motion)) sql.append(" AND gps_available AND speed_kph>0");
    if ("STATIONARY".equalsIgnoreCase(motion)) sql.append(" AND gps_available AND coalesce(speed_kph,0)=0");
    return sql.toString();
  }

  private String freshnessSql() {
    return "CASE WHEN measurement_timestamp>=now()-(" + staleSeconds + "*interval '1 second') THEN 'ONLINE' WHEN measurement_timestamp>=now()-(" + offlineSeconds + "*interval '1 second') THEN 'STALE' ELSE 'OFFLINE' END";
  }
  private void textFilter(StringBuilder sql, List<Object> args, String expression, String value, boolean twice) {
    if (!value.isBlank()) { sql.append(" AND ").append(expression); String normalized = twice ? "%" + value.toLowerCase() + "%" : value.toLowerCase(); args.add(normalized); if (twice) args.add(normalized); }
  }
  private void booleanFilter(StringBuilder sql, List<Object> args, String column, Boolean value) { if (value != null) { sql.append(" AND ").append(column).append("=?"); args.add(value); } }
  private void decorate(Map<String, Object> row) {
    Object can = row.get("can_parameters");
    if (can instanceof String text) row.put("can_parameters", parseJson(text));
    row.put("warnings", warnings(row));
  }
  private List<String> warnings(Map<String, Object> row) {
    List<String> result = new ArrayList<>();
    if (Boolean.FALSE.equals(row.get("gps_available"))) result.add("GPS_UNAVAILABLE");
    if ("STALE".equals(row.get("freshness")) || "OFFLINE".equals(row.get("freshness"))) result.add("STALE_GPS");
    if (lowBatteryVoltage != null && row.get("battery_voltage") instanceof Number voltage
        && voltage.doubleValue() < lowBatteryVoltage) result.add("LOW_BATTERY_VOLTAGE");
    JsonNode metadata = parseJson(canMetadataJson);
    if (row.get("can_parameters") instanceof JsonNode parameters && parameters.isArray()) {
      parameters.forEach(parameter -> {
        String name = parameter.path("Name").asText();
        JsonNode definition = metadata.path(name);
        JsonNode value = parameter.path("Value");
        if (value.isNumber() && definition.has("warningMin")
            && value.asDouble() < definition.path("warningMin").asDouble()) result.add(name + "_LOW");
        if (value.isNumber() && definition.has("warningMax")
            && value.asDouble() > definition.path("warningMax").asDouble()) result.add(name + "_HIGH");
      });
    }
    return result;
  }
  private JsonNode parseJson(String value) { try { return json.readTree(value); } catch (Exception exception) { return json.createObjectNode(); } }
}
