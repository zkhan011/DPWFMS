package com.dpworld.fms.routing;
import com.dpworld.fms.domain.*; import org.junit.jupiter.api.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
class AStarRoutingEngineTest {
 MapNode a=new MapNode("A",new GeoPoint(25,55)),b=new MapNode("B",new GeoPoint(25,55.001)),c=new MapNode("C",new GeoPoint(25,55.002));
 RoadSegment r(String id,String f,String t,double d,boolean blocked,boolean restricted,Set<AssetType> types,double height){return new RoadSegment(id,f,t,d,40,1,1,blocked,restricted,types,height,0,0);}
 RouteRequest request(){return new RouteRequest("A","C",AssetType.ITV,new VehicleEnvelope(3,2,5,10),10000,5);}
 @Test void findsShortestDirectedPath(){var e=new AStarRoutingEngine(List.of(a,b,c),List.of(r("ab","A","B",100,false,false,Set.of(),0),r("bc","B","C",100,false,false,Set.of(),0),r("ac","A","C",500,false,false,Set.of(),0)));assertEquals(List.of("ab","bc"),e.calculate(request()).segments().stream().map(RoadSegment::id).toList());}
 @Test void respectsOneWayAndDisconnectedGraphs(){var e=new AStarRoutingEngine(List.of(a,b,c),List.of(r("ba","B","A",100,false,false,Set.of(),0)));assertThrows(RouteNotFoundException.class,()->e.calculate(request()));}
 @Test void reroutesAroundBlockedRoad(){var e=new AStarRoutingEngine(List.of(a,b,c),List.of(r("ab","A","B",100,true,false,Set.of(),0),r("bc","B","C",100,false,false,Set.of(),0),r("ac","A","C",500,false,false,Set.of(),0)));assertEquals("ac",e.calculate(request()).segments().getFirst().id());}
 @Test void rejectsRestrictedAndVehicleIncompatibleRoads(){var e=new AStarRoutingEngine(List.of(a,b,c),List.of(r("ac","A","C",100,false,true,Set.of(),0),r("truck","A","C",200,false,false,Set.of(AssetType.EXTERNAL_TRUCK),0)));assertThrows(RouteNotFoundException.class,()->e.calculate(request()));}
 @Test void enforcesDimensions(){var e=new AStarRoutingEngine(List.of(a,c),List.of(r("low","A","C",100,false,false,Set.of(),2.5)));assertThrows(RouteNotFoundException.class,()->e.calculate(request()));}
}
