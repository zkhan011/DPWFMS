package com.dpworld.fms.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Creates one durable charging job and bay reservation for a low-energy electric asset. */
@Service
public class AutomaticChargingService {
  private final JdbcTemplate jdbc;
  private final double thresholdPercent;

  public AutomaticChargingService(JdbcTemplate jdbc,
      @Value("${dpwfms.automation.charging-threshold-percent:20}") double thresholdPercent) {
    this.jdbc = jdbc;
    this.thresholdPercent = thresholdPercent;
  }

  public UUID evaluate(UUID assetId, String energySource, double energyPercent,
      String operationalStatus, String availabilityStatus, Instant occurredAt) {
    if (!requiresCharging(energySource, energyPercent, operationalStatus, availabilityStatus,
        thresholdPercent)) return null;
    Long active = jdbc.queryForObject("""
        SELECT count(*) FROM jobs WHERE assigned_asset_id=?
          AND status NOT IN ('COMPLETED','CANCELLED','FAILED','REJECTED','EXPIRED')
        """, Long.class, assetId);
    if (active != null && active > 0) return null;

    List<Map<String, Object>> available = jdbc.queryForList("""
        SELECT b.id AS bay_id,s.id AS station_id,s.location_id
        FROM service_bays b JOIN service_stations s ON s.id=b.station_id
        WHERE s.station_type='CHARGING' AND s.status='AVAILABLE' AND b.status='AVAILABLE'
        ORDER BY b.code FOR UPDATE OF b SKIP LOCKED LIMIT 1
        """);
    if (available.isEmpty()) {
      raiseNoBayAlert(assetId, occurredAt);
      return null;
    }
    Map<String, Object> destination = available.getFirst();
    UUID bayId = (UUID) destination.get("bay_id");
    if (jdbc.update("UPDATE service_bays SET status='RESERVED' WHERE id=? AND status='AVAILABLE'", bayId) != 1) {
      return null;
    }

    UUID jobId = UUID.randomUUID();
    String jobNumber = "AUTO-CHARGE-" + jobId.toString().substring(0, 8).toUpperCase();
    int priority = energyPercent < 10 ? 100 : energyPercent < 20 ? 90 : 70;
    jdbc.update("""
        INSERT INTO jobs(id,job_number,job_type,priority,plant_id,destination_location_id,
          required_capabilities,status,assigned_asset_id,requested_start_at,created_by,created_at)
        VALUES (?,?, 'CHARGING',?,(SELECT plant_id FROM assets WHERE id=?),?,
          '["ELECTRIC"]'::jsonb,'CREATED',?,?, 'AUTOMATION:LOW_ENERGY',now())
        """, jobId, jobNumber, priority, assetId, destination.get("location_id"), assetId,
        java.sql.Timestamp.from(occurredAt));
    jdbc.update("""
        INSERT INTO job_events(id,job_id,to_status,occurred_at,actor,reason,metadata)
        VALUES (?,?, 'CREATED',?,'AUTOMATION','Electric asset below charging threshold',
          jsonb_build_object('energyPercent',?,'thresholdPercent',?,'bayId',?))
        """, UUID.randomUUID(), jobId, java.sql.Timestamp.from(occurredAt), energyPercent,
        thresholdPercent, bayId.toString());
    jdbc.update("UPDATE jobs SET status='ASSIGNED',version=version+1 WHERE id=?", jobId);
    jdbc.update("""
        INSERT INTO job_events(id,job_id,from_status,to_status,occurred_at,actor,reason)
        VALUES (?,?,'CREATED','ASSIGNED',?,'AUTOMATION','Low-energy electric asset assigned to charging bay')
        """, UUID.randomUUID(), jobId, java.sql.Timestamp.from(occurredAt));
    jdbc.update("""
        INSERT INTO job_assignments(id,job_id,asset_id,score,assigned_at) VALUES (?,?,?,?,?)
        """, UUID.randomUUID(), jobId, assetId, 100 - energyPercent,
        java.sql.Timestamp.from(occurredAt));
    jdbc.update("""
        INSERT INTO resource_reservations(id,resource_type,resource_id,job_id,expires_at)
        VALUES (?,'CHARGING_BAY',?,?,?)
        """, UUID.randomUUID(), bayId, jobId,
        java.sql.Timestamp.from(occurredAt.plus(Duration.ofMinutes(15))));
    jdbc.update("""
        UPDATE assets SET current_job_id=?,availability_status='RESERVED',version=version+1 WHERE id=?
        """, jobId, assetId);
    return jobId;
  }

  static boolean requiresCharging(String energySource, double energyPercent, String operationalStatus,
      String availabilityStatus, double thresholdPercent) {
    return "ELECTRIC".equalsIgnoreCase(energySource)
        && energyPercent <= thresholdPercent
        && ("IDLE".equals(operationalStatus) || "PARKED".equals(operationalStatus))
        && "AVAILABLE".equals(availabilityStatus);
  }

  private void raiseNoBayAlert(UUID assetId, Instant occurredAt) {
    Long existing = jdbc.queryForObject("""
        SELECT count(*) FROM alerts WHERE asset_id=? AND code='NO_CHARGING_BAY'
          AND acknowledged_at IS NULL
        """, Long.class, assetId);
    if (existing != null && existing > 0) return;
    jdbc.update("""
        INSERT INTO alerts(id,asset_id,severity,code,message,created_at)
        VALUES (?,?,'MAJOR','NO_CHARGING_BAY','Low-energy electric asset has no available charging bay',?)
        """, UUID.randomUUID(), assetId, java.sql.Timestamp.from(occurredAt));
  }
}
