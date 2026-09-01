package com.dpworld.fms.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Telemetry trigger for the capacity-aware charging assignment service. */
@Service
public class AutomaticChargingService {
  private final ChargingAssignmentService assignments;
  private final double thresholdPercent;
  public AutomaticChargingService(ChargingAssignmentService assignments,
      @Value("${dpwfms.automation.charging-threshold-percent:20}") double thresholdPercent){this.assignments=assignments;this.thresholdPercent=thresholdPercent;}
  public UUID evaluate(UUID assetId,String energySource,double energyPercent,String operationalStatus,String availabilityStatus,Instant occurredAt){
    if(!requiresCharging(energySource,energyPercent,operationalStatus,availabilityStatus,thresholdPercent))return null;
    try{Map<String,Object> result=assignments.assign(assetId,"AUTOMATIC","Battery below automatic charging threshold",null,"automatic-charging-"+assetId+"-"+(occurredAt.getEpochSecond()/300),"AUTOMATION",null);Object job=result.get("job_id");return job instanceof UUID id?id:null;}catch(IllegalStateException ignored){return null;}
  }
  static boolean requiresCharging(String energySource,double energyPercent,String operationalStatus,String availabilityStatus,double thresholdPercent){return "ELECTRIC".equalsIgnoreCase(energySource)&&energyPercent<=thresholdPercent&&("IDLE".equals(operationalStatus)||"PARKED".equals(operationalStatus))&&"AVAILABLE".equals(availabilityStatus);}
}
