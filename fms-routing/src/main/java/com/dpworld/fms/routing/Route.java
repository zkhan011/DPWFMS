package com.dpworld.fms.routing;
import java.util.List;
public record Route(List<MapNode> nodes,List<RoadSegment> segments,double totalDistanceMetres,double estimatedTravelSeconds) { public Route { nodes=List.copyOf(nodes);segments=List.copyOf(segments); } }
