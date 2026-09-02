package com.dpworld.fms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking")
public class ParkingController {
  private final JdbcTemplate jdbc;
  private final AutomaticParkingAssignmentService assignments;
  public ParkingController(JdbcTemplate jdbc, AutomaticParkingAssignmentService assignments){this.jdbc=jdbc;this.assignments=assignments;}

  @GetMapping("/zones") @PreAuthorize("hasAuthority('parking.read')")
  public List<Map<String,Object>> zones(){return jdbc.queryForList("""
      SELECT z.*,l.latitude,l.longitude,count(s.id) total_bays,count(s.id) FILTER(WHERE s.status='AVAILABLE') available_bays,
      count(s.id) FILTER(WHERE s.status='RESERVED') reserved_bays,count(s.id) FILTER(WHERE s.status='OCCUPIED') occupied_bays
      FROM parking_zones z JOIN locations l ON l.id=z.location_id LEFT JOIN parking_spaces s ON s.parking_zone_id=z.id GROUP BY z.id,l.id ORDER BY z.priority,z.code
      """);}
  @PostMapping("/zones") @PreAuthorize("hasAuthority('parking.bay.manage')")
  public Map<String,Object> createZone(@Valid @RequestBody ZoneRequest request,Principal actor){UUID location=UUID.randomUUID(),id=UUID.randomUUID();jdbc.update("INSERT INTO locations(id,code,name,kind,latitude,longitude) VALUES (?,?,?,'PARKING_ZONE',?,?)",location,request.code(),request.name(),request.latitude(),request.longitude());jdbc.update("INSERT INTO parking_zones(id,location_id,capacity,code,name,plant_id,priority,allowed_asset_types,boundary) VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb)",id,location,request.capacity(),request.code(),request.name(),request.plantId(),request.priority(),toJson(request.allowedAssetTypes()),request.boundary()==null?null:toJson(request.boundary()));audit(actor.getName(),"PARKING_ZONE_CREATED",id,request.reason());return jdbc.queryForMap("SELECT * FROM parking_zones WHERE id=?",id);}
  @PostMapping("/bays") @PreAuthorize("hasAuthority('parking.bay.manage')")
  public Map<String,Object> createBay(@Valid @RequestBody BayRequest request,Principal actor){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO parking_spaces(id,parking_zone_id,code,status,supported_asset_types,max_length_m,max_width_m,max_weight_tonnes,latitude,longitude,priority) VALUES (?,?,?,'AVAILABLE',?::jsonb,?,?,?,?,?,?)",id,request.zoneId(),request.code(),toJson(request.allowedAssetTypes()),request.maximumLength(),request.maximumWidth(),request.maximumWeight(),request.latitude(),request.longitude(),request.priority());audit(actor.getName(),"PARKING_BAY_CREATED",id,request.reason());return jdbc.queryForMap("SELECT * FROM parking_spaces WHERE id=?",id);}
  @GetMapping("/bays") @PreAuthorize("hasAuthority('parking.read')")
  public List<Map<String,Object>> bays(){return jdbc.queryForList("SELECT s.*,z.code zone_code,z.name zone_name FROM parking_spaces s JOIN parking_zones z ON z.id=s.parking_zone_id ORDER BY z.priority,s.priority,s.code");}
  @GetMapping("/assignments") @PreAuthorize("hasAuthority('parking.read')")
  public List<Map<String,Object>> assignmentList(){return jdbc.queryForList("SELECT pa.*,a.fleet_number,s.code bay_code,z.code zone_code FROM parking_assignments pa JOIN assets a ON a.id=pa.asset_id JOIN parking_spaces s ON s.id=pa.parking_space_id JOIN parking_zones z ON z.id=s.parking_zone_id ORDER BY pa.created_at DESC LIMIT 1000");}
  @GetMapping("/exceptions") @PreAuthorize("hasAuthority('parking.read')")
  public List<Map<String,Object>> exceptions(){return jdbc.queryForList("SELECT e.*,a.fleet_number FROM assignment_exceptions e LEFT JOIN assets a ON a.id=e.asset_id WHERE assignment_type='PARKING' ORDER BY created_at DESC LIMIT 500");}
  @PostMapping("/assignments/preview") @PreAuthorize("hasAuthority('parking.assign')")
  public AutomaticParkingAssignmentService.Preview preview(@Valid @RequestBody PreviewRequest request){return assignments.preview(request.assetId());}
  @PostMapping("/assignments") @PreAuthorize("hasAuthority('parking.assign')")
  public Map<String,Object> assign(@Valid @RequestBody AssignmentRequest request,Principal actor,@RequestHeader(name="X-Correlation-ID",required=false)String correlation){
    if("MANUAL".equals(request.mode())&&(request.overrideReason()==null||request.overrideReason().isBlank()))throw new IllegalArgumentException("manual assignment requires overrideReason");
    return assignments.assign(request.assetId(),request.mode(),request.reason(),request.overrideReason(),request.idempotencyKey(),actor.getName(),correlation);
  }
  @PostMapping("/assignments/{id}/cancel") @PreAuthorize("hasAuthority('parking.override')")
  public void cancel(@PathVariable("id")UUID id,@Valid @RequestBody ReasonRequest request,Principal actor){Map<String,Object> row=jdbc.queryForMap("SELECT parking_space_id,asset_id,job_id,status FROM parking_assignments WHERE id=? FOR UPDATE",id);if(List.of("COMPLETED","CANCELLED","EXPIRED","FAILED").contains(row.get("status")))throw new IllegalStateException("assignment is already terminal");jdbc.update("UPDATE parking_assignments SET status='CANCELLED',completed_at=now(),override_reason=?,version=version+1 WHERE id=?",request.reason(),id);jdbc.update("UPDATE parking_spaces SET status='AVAILABLE',reserved=FALSE,current_asset_id=NULL,reservation_expiry=NULL,version=version+1 WHERE id=?",row.get("parking_space_id"));jdbc.update("UPDATE job_assignments SET ended_at=now() WHERE job_id=? AND ended_at IS NULL",row.get("job_id"));jdbc.update("UPDATE jobs SET status='CANCELLED',completed_at=now(),failure_reason=?,version=version+1 WHERE id=?",request.reason(),row.get("job_id"));jdbc.update("UPDATE assets SET current_job_id=NULL,availability_status='AVAILABLE',version=version+1 WHERE id=? AND current_job_id=?",row.get("asset_id"),row.get("job_id"));audit(actor.getName(),"PARKING_CANCELLED",id,request.reason());}
  @PatchMapping("/bays/{id}/status") @PreAuthorize("hasAuthority('parking.bay.manage')")
  public void bayStatus(@PathVariable("id")UUID id,@Valid @RequestBody StatusRequest request,Principal actor){if(!List.of("AVAILABLE","BLOCKED","OUT_OF_SERVICE").contains(request.status()))throw new IllegalArgumentException("management status must be AVAILABLE, BLOCKED or OUT_OF_SERVICE");if(jdbc.update("UPDATE parking_spaces SET status=?,reserved=FALSE,version=version+1 WHERE id=? AND current_asset_id IS NULL",request.status(),id)!=1)throw new IllegalStateException("occupied or reserved bay cannot change service status");audit(actor.getName(),"PARKING_BAY_STATUS_CHANGED",id,request.reason());}
  private void audit(String actor,String action,UUID id,String reason){jdbc.update("INSERT INTO audit_logs(id,occurred_at,actor,action,resource_type,resource_id,after_value) VALUES (?,now(),?,?,'PARKING',?,jsonb_build_object('reason',?))",UUID.randomUUID(),actor,action,id.toString(),reason);}
  private String toJson(Object value){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}catch(Exception exception){throw new IllegalArgumentException("invalid map boundary",exception);}}
  public record PreviewRequest(@NotNull UUID assetId){}
  public record AssignmentRequest(@NotNull UUID assetId,@NotBlank String mode,@NotBlank String reason,String overrideReason,String idempotencyKey){}
  public record ReasonRequest(@NotBlank String reason){}
  public record StatusRequest(@NotBlank String status,@NotBlank String reason){}
  public record ZoneRequest(@NotBlank String code,@NotBlank String name,@NotNull UUID plantId,@Min(0)int capacity,@Min(0)int priority,@NotNull Double latitude,@NotNull Double longitude,List<String>allowedAssetTypes,Map<String,Object>boundary,@NotBlank String reason){}
  public record BayRequest(@NotNull UUID zoneId,@NotBlank String code,@NotNull Double latitude,@NotNull Double longitude,Double maximumLength,Double maximumWidth,Double maximumWeight,@Min(0)int priority,List<String>allowedAssetTypes,@NotBlank String reason){}
}
