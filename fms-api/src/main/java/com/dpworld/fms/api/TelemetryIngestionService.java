package com.dpworld.fms.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryIngestionService {
  private final JdbcTemplate jdbc;
  public TelemetryIngestionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public Result accept(UUID assetId, TelemetryController.PositionTelemetry message) {
    UUID integrationId = UUID.randomUUID();
    int inbox = jdbc.update("""
        INSERT INTO integration_messages(id,external_message_id,channel,schema_version,correlation_id,
          status,received_at,payload_hash)
        VALUES (?,?,'TELEMETRY_API',?,?, 'RECEIVED',now(),?)
        ON CONFLICT (channel,external_message_id) DO NOTHING
        """, integrationId, message.messageId(), message.schemaVersion(), message.correlationId(), hash(message));
    if (inbox == 0) return new Result("DUPLICATE", false, assetId, message.occurredAt());

    UUID assetTypeId = jdbc.query("SELECT id FROM asset_types WHERE code=?", rs -> rs.next()
        ? rs.getObject(1, UUID.class) : null, message.assetType());
    if (assetTypeId == null) {
      assetTypeId = UUID.randomUUID();
      jdbc.update("INSERT INTO asset_types(id,code,name) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING",
          assetTypeId, message.assetType(), message.assetType().replace('_', ' '));
      assetTypeId = jdbc.queryForObject("SELECT id FROM asset_types WHERE code=?", UUID.class, message.assetType());
    }
    UUID plantId = message.plantCode() == null ? null : jdbc.query("SELECT id FROM plants WHERE code=?",
        rs -> rs.next() ? rs.getObject(1, UUID.class) : null, message.plantCode());
    if (message.plantCode() != null && plantId == null) throw new IllegalArgumentException("unknown plant code " + message.plantCode());

    int updated = jdbc.update("""
        UPDATE assets SET fleet_number=?,asset_type_id=?,plant_id=?,latitude=?,longitude=?,heading=?,
          speed_kph=?,energy_percent=?,operational_status=?,availability_status=?,last_telemetry_at=?,
          device_id=COALESCE(?,device_id),trackit_id=COALESCE(?,trackit_id),version=version+1
        WHERE id=? AND (last_telemetry_at IS NULL OR last_telemetry_at < ?)
        """, message.fleetNumber(), assetTypeId, plantId, message.latitude(), message.longitude(),
        message.heading(), message.speedKph(), message.energyPercent(), message.operationalStatus(),
        message.availabilityStatus(), timestamp(message.occurredAt()), message.deviceId(), message.trackItId(),
        assetId, timestamp(message.occurredAt()));
    if (updated == 0 && count(assetId) == 0) {
      updated = jdbc.update("""
          INSERT INTO assets(id,fleet_number,asset_type_id,plant_id,operational_status,
            availability_status,latitude,longitude,heading,speed_kph,energy_percent,last_telemetry_at,
            device_id,trackit_id,maintenance_status,enabled)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'SERVICEABLE',TRUE)
          """, assetId, message.fleetNumber(), assetTypeId, plantId, message.operationalStatus(),
          message.availabilityStatus(), message.latitude(), message.longitude(), message.heading(),
          message.speedKph(), message.energyPercent(), timestamp(message.occurredAt()), message.deviceId(),
          message.trackItId());
    }
    if (updated == 0) {
      jdbc.update("UPDATE integration_messages SET status='IGNORED_OUT_OF_ORDER',processed_at=now() WHERE id=?", integrationId);
      return new Result("OUT_OF_ORDER", false, assetId, message.occurredAt());
    }
    jdbc.update("""
        INSERT INTO asset_positions(asset_id,recorded_at,latitude,longitude,heading,speed_kph)
        VALUES (?,?,?,?,?,?)
        """, assetId, timestamp(message.occurredAt()), message.latitude(), message.longitude(),
        message.heading(), message.speedKph());
    jdbc.update("UPDATE integration_messages SET status='PROCESSED',processed_at=now() WHERE id=?", integrationId);
    return new Result("ACCEPTED", true, assetId, message.occurredAt());
  }

  private long count(UUID assetId) { return jdbc.queryForObject("SELECT count(*) FROM assets WHERE id=?", Long.class, assetId); }
  private Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
  private String hash(TelemetryController.PositionTelemetry message) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(message.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
  public record Result(String result, boolean currentPositionUpdated, UUID assetId, Instant occurredAt) {}
}
