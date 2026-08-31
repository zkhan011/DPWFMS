package com.dpworld.fms.routing;
import com.dpworld.fms.domain.AssetType; import java.util.Set;
public record RoadSegment(String id,String from,String to,double distanceMetres,double speedLimitKph,double congestionFactor,double priorityFactor,boolean blocked,boolean restricted,Set<AssetType> allowedTypes,double maxHeightM,double maxWeightTonnes,double turnPenaltySeconds){
 public RoadSegment { allowedTypes=allowedTypes==null?Set.of():Set.copyOf(allowedTypes); if(distanceMetres<0||speedLimitKph<=0)throw new IllegalArgumentException("invalid segment metrics"); }
 public double travelSeconds(){return distanceMetres/(speedLimitKph/3.6)*Math.max(1,congestionFactor)*Math.max(.1,priorityFactor)+turnPenaltySeconds;}
}
