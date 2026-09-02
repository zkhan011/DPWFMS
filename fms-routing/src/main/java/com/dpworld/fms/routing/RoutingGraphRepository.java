package com.dpworld.fms.routing;
import java.util.Optional;import java.util.UUID;
public interface RoutingGraphRepository {
 Optional<RoutingGraph> findActiveApproved();
 Optional<RoutingGraph> findById(UUID graphId);
 record RoutingGraph(UUID id,long version,String status,java.util.List<MapNode>nodes,java.util.List<RoadSegment>segments){}
}
