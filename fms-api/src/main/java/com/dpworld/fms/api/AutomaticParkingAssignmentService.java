package com.dpworld.fms.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomaticParkingAssignmentService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public AutomaticParkingAssignmentService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public UUID evaluate(UUID assetId, Instant occurredAt) {
    if (!"AUTOMATIC".equals(parameters().get("parkingOperatingMode"))
        || !Boolean.TRUE.equals(parameters().get("parkingAutomationEnabled"))) return null;
    try {
      Map<String, Object> result = assign(assetId, "AUTOMATIC", "Eligible idle asset",
          null, "automatic-parking-" + assetId + "-" + occurredAt.getEpochSecond() / 300,
          "AUTOMATION", null);
      return (UUID) result.get("job_id");
    } catch (IllegalStateException ignored) {
      return null;
    }
  }

  public Preview preview(UUID assetId) {
    Map<String, Object> asset = asset(assetId);
    validateEligibility(asset);
    Map<String, Object> parameters = parameters();
    double maximumDistance = number(parameters, "maximumParkingDistanceMeters", 5000);
    double distanceWeight = number(parameters, "parkingDistanceWeight", 1);
    double utilizationWeight = number(parameters, "parkingUtilizationWeight", .5);
    List<Candidate> accepted = new ArrayList<>();
    List<RejectedCandidate> rejected = new ArrayList<>();
    for (Map<String, Object> bay : jdbc.queryForList("""
        SELECT s.id,s.code,s.status,s.latitude,s.longitude,s.priority,s.active,s.supported_asset_types,
               z.id zone_id,z.code zone_code,z.priority zone_priority,z.temporarily_excluded,z.active zone_active,
               z.location_id,z.capacity,
               (SELECT count(*) FROM parking_spaces used WHERE used.parking_zone_id=z.id AND used.status IN ('RESERVED','OCCUPIED')) occupied
        FROM parking_spaces s JOIN parking_zones z ON z.id=s.parking_zone_id ORDER BY z.priority,s.priority,s.code
        """)) {
      List<String> reasons = rejectReasons(asset, bay);
      double distance = distance(asset, bay);
      if (distance > maximumDistance) reasons.add("OUTSIDE_MAXIMUM_DISTANCE");
      if (!reasons.isEmpty()) {
        rejected.add(new RejectedCandidate(uuid(bay, "id"), String.valueOf(bay.get("code")), reasons));
        continue;
      }
      double utilization = ((Number) bay.get("occupied")).doubleValue() / Math.max(1, ((Number) bay.get("capacity")).doubleValue());
      double score = distanceWeight * (distance / Math.max(1, maximumDistance)) + utilizationWeight * utilization
          + ((Number) bay.get("priority")).doubleValue() / 10000d;
      accepted.add(new Candidate(uuid(bay, "id"), String.valueOf(bay.get("code")), uuid(bay, "zone_id"),
          String.valueOf(bay.get("zone_code")), distance, score,
          Map.of("distance", distance, "normalizedDistance", distance / Math.max(1, maximumDistance), "zoneUtilization", utilization)));
    }
    accepted.sort(java.util.Comparator.comparingDouble(Candidate::score));
    if (accepted.isEmpty()) throw exception(assetId, "NO_SAFE_PARKING_BAY", rejected);
    return new Preview(assetId, accepted.getFirst(), accepted.stream().skip(1).toList(), rejected,
        ((Number) parameters.getOrDefault("_version", 0)).longValue());
  }

  @Transactional
  public Map<String, Object> assign(UUID assetId, String mode, String reason, String overrideReason,
      String idempotencyKey, String actor, String correlationId) {
    if (idempotencyKey != null) {
      List<Map<String, Object>> existing = jdbc.queryForList("SELECT * FROM parking_assignments WHERE idempotency_key=?", idempotencyKey);
      if (!existing.isEmpty()) return existing.getFirst();
    }
    Preview preview = preview(assetId);
    Candidate selected = preview.selected();
    List<Map<String, Object>> locked = jdbc.queryForList("SELECT id FROM parking_spaces WHERE id=? AND active AND status='AVAILABLE' FOR UPDATE SKIP LOCKED", selected.bayId());
    if (locked.isEmpty() || jdbc.update("UPDATE parking_spaces SET status='RESERVED',reserved=TRUE,current_asset_id=?,reservation_expiry=now()+interval '15 minutes',version=version+1 WHERE id=? AND status='AVAILABLE'", assetId, selected.bayId()) != 1) {
      throw new IllegalStateException("parking reservation conflict; preview again");
    }
    UUID assignmentId = UUID.randomUUID(), jobId = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO jobs(id,job_number,job_type,priority,plant_id,destination_location_id,required_capabilities,status,
          assigned_asset_id,requested_start_at,created_by) SELECT ?,?,'PARKING',70,a.plant_id,z.location_id,'[]'::jsonb,'ASSIGNED',a.id,now(),?
          FROM assets a JOIN parking_spaces s ON s.id=? JOIN parking_zones z ON z.id=s.parking_zone_id WHERE a.id=?
        """, jobId, "AUTO-PARK-" + jobId.toString().substring(0, 8).toUpperCase(), actor, selected.bayId(), assetId);
    jdbc.update("INSERT INTO job_assignments(id,job_id,asset_id,score,assigned_at) VALUES (?,?,?,?,now())", UUID.randomUUID(), jobId, assetId, selected.score());
    jdbc.update("""
        INSERT INTO parking_assignments(id,asset_id,parking_space_id,job_id,status,assignment_mode,score,score_breakdown,
          rejected_candidates,reason,assigned_at,expires_at,created_by,override_reason,parameter_version,correlation_id,idempotency_key)
        VALUES (?,?,?,?,'ASSIGNED',?,?,?::jsonb,?::jsonb,?,now(),now()+interval '15 minutes',?,?,?,?,?)
        """, assignmentId, assetId, selected.bayId(), jobId, mode, selected.score(), write(selected.scoreBreakdown()),
        write(preview.rejected()), reason, actor, overrideReason, preview.parameterVersion(), correlationId, idempotencyKey);
    jdbc.update("UPDATE assets SET current_job_id=?,availability_status='RESERVED',version=version+1 WHERE id=?", jobId, assetId);
    audit(actor, "PARKING_ASSIGNED", assignmentId, correlationId, null, Map.of("assetId", assetId, "bayId", selected.bayId(), "score", selected.score(), "reason", reason));
    return jdbc.queryForMap("SELECT * FROM parking_assignments WHERE id=?", assignmentId);
  }

  private Map<String, Object> asset(UUID id) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT a.*,t.code asset_type FROM assets a JOIN asset_types t ON t.id=a.asset_type_id WHERE a.id=?
        """, id);
    if (rows.isEmpty()) throw new IllegalArgumentException("unknown asset " + id);
    return rows.getFirst();
  }

  private void validateEligibility(Map<String, Object> asset) {
    if (!List.of("IDLE", "PARKED").contains(String.valueOf(asset.get("operational_status")))) throw new IllegalStateException("asset is not idle");
    if (!"AVAILABLE".equals(String.valueOf(asset.get("availability_status")))) throw new IllegalStateException("asset is not available");
    if (asset.get("last_telemetry_at") == null) throw new IllegalStateException("asset telemetry is stale");
    Instant last = ((Timestamp) asset.get("last_telemetry_at")).toInstant();
    long staleSeconds = ((Number) parameters().getOrDefault("staleTelemetryThresholdSeconds", 60)).longValue();
    if (last.isBefore(Instant.now().minusSeconds(staleSeconds))) throw new IllegalStateException("asset telemetry is stale");
    double energy = ((Number) asset.getOrDefault("energy_percent", 0)).doubleValue();
    if (energy < number(parameters(), "minimumEnergyPercentageForParking", 20)) throw new IllegalStateException("asset energy is below safe parking threshold");
    if (asset.get("current_job_id") != null) throw new IllegalStateException("asset has a conflicting active job");
  }

  private List<String> rejectReasons(Map<String, Object> asset, Map<String, Object> bay) {
    List<String> reasons = new ArrayList<>();
    if (!Boolean.TRUE.equals(bay.get("active")) || !Boolean.TRUE.equals(bay.get("zone_active"))) reasons.add("INACTIVE");
    if (Boolean.TRUE.equals(bay.get("temporarily_excluded"))) reasons.add("ZONE_EXCLUDED");
    if (!"AVAILABLE".equals(bay.get("status"))) reasons.add("BAY_" + bay.get("status"));
    String types = String.valueOf(bay.get("supported_asset_types"));
    if (!"[]".equals(types) && !types.contains("\"" + asset.get("asset_type") + "\"")) reasons.add("INCOMPATIBLE_ASSET_TYPE");
    if (bay.get("latitude") == null || bay.get("longitude") == null) reasons.add("MISSING_COORDINATES");
    if (Boolean.TRUE.equals(parameters().get("requireParkingRouteAvailability")) && (asset.get("current_location_id") == null || bay.get("routing_node_id") == null)) reasons.add("ROUTE_UNAVAILABLE");
    return reasons;
  }

  private double distance(Map<String, Object> asset, Map<String, Object> bay) {
    if (asset.get("latitude") == null || asset.get("longitude") == null || bay.get("latitude") == null || bay.get("longitude") == null) return Double.POSITIVE_INFINITY;
    return haversine(((Number) asset.get("latitude")).doubleValue(), ((Number) asset.get("longitude")).doubleValue(), ((Number) bay.get("latitude")).doubleValue(), ((Number) bay.get("longitude")).doubleValue());
  }

  static double haversine(double lat1, double lon1, double lat2, double lon2) {
    double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
    double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
    return 6371000d*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
  }

  @SuppressWarnings("unchecked") private Map<String,Object> parameters(){Map<String,Object> row=jdbc.queryForMap("SELECT version,parameters::text parameters FROM operational_parameter_versions ORDER BY version DESC LIMIT 1");try{Map<String,Object> p=json.readValue(String.valueOf(row.get("parameters")),Map.class);p.put("_version",row.get("version"));return p;}catch(Exception e){throw new IllegalStateException("invalid operational parameters",e);}}
  private double number(Map<String,Object> p,String key,double fallback){Object value=p.get(key);return value instanceof Number n?n.doubleValue():fallback;}
  private UUID uuid(Map<String,Object> row,String key){return (UUID)row.get(key);}
  private String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("cannot serialize assignment audit",e);}}
  private IllegalStateException exception(UUID assetId,String code,Object context){jdbc.update("INSERT INTO assignment_exceptions(id,assignment_type,asset_id,code,detail,context) VALUES (?,'PARKING',?,?,?,?::jsonb)",UUID.randomUUID(),assetId,code,"No safe parking candidate",write(context));return new IllegalStateException("No suitable parking bay: "+code);}
  private void audit(String actor,String action,UUID id,String correlation,Object before,Object after){jdbc.update("INSERT INTO audit_logs(id,occurred_at,actor,action,resource_type,resource_id,correlation_id,before_value,after_value) VALUES (?,now(),?,?,'PARKING_ASSIGNMENT',?,?,?::jsonb,?::jsonb)",UUID.randomUUID(),actor,action,id.toString(),correlation,before==null?null:write(before),write(after));}
  public record Candidate(UUID bayId,String bayCode,UUID zoneId,String zoneCode,double distanceMeters,double score,Map<String,Object> scoreBreakdown){}
  public record RejectedCandidate(UUID bayId,String bayCode,List<String> reasons){}
  public record Preview(UUID assetId,Candidate selected,List<Candidate> alternatives,List<RejectedCandidate> rejected,long parameterVersion){}
}
