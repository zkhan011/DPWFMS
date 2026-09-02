package com.dpworld.fms.routing;
import java.time.Instant;import java.util.List;import java.util.Optional;import java.util.UUID;
public interface PlannedRouteRepository {
 UUID save(PlannedRoute route);
 Optional<PlannedRoute> find(UUID id);
 record PlannedRoute(UUID id,UUID jobId,UUID graphId,long graphVersion,String sourceNode,String destinationNode,double distanceMetres,double travelSeconds,List<String>orderedSegmentIds,String geoJson,String failureReason,Instant createdAt){}
}
