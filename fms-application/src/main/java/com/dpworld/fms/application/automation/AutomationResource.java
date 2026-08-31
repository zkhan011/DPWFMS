package com.dpworld.fms.application.automation;

import com.dpworld.fms.domain.AssetType;
import java.util.Set;

public sealed interface AutomationResource permits AutomationResource.ParkingSpace, AutomationResource.FuelingBay {
  String id();
  String mapNodeId();
  String zoneId();
  boolean enabled();
  ResourceState state();
  Set<AssetType> supportedAssetTypes();
  double maxHeightMetres();
  double maxWeightTonnes();

  enum ResourceState { AVAILABLE, RESERVED, OCCUPIED, IN_USE, QUEUED, BLOCKED, MAINTENANCE, OUT_OF_SERVICE, UNKNOWN }

  record ParkingSpace(String id, String mapNodeId, String zoneId, boolean enabled,
                      ResourceState state, Set<AssetType> supportedAssetTypes,
                      double maxHeightMetres, double maxWeightTonnes, double occupancyRatio,
                      double maneuverPenalty, double nextJobDistanceMetres,
                      double operationalPreferencePenalty) implements AutomationResource {
    public ParkingSpace { supportedAssetTypes = Set.copyOf(supportedAssetTypes); }
  }

  record FuelingBay(String id, String stationId, String mapNodeId, String zoneId, boolean enabled,
                    ResourceState state, Set<AssetType> supportedAssetTypes,
                    double maxHeightMetres, double maxWeightTonnes, String fuelType,
                    int queueLength, double queueWaitSeconds, double serviceSeconds,
                    double outageRisk, double stationPriorityPenalty) implements AutomationResource {
    public FuelingBay { supportedAssetTypes = Set.copyOf(supportedAssetTypes); }
  }
}
