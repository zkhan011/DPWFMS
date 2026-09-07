package com.dpworld.fms.routing;import static org.junit.jupiter.api.Assertions.*;import java.util.Set;import org.junit.jupiter.api.Test;
class RoadSegmentTravelTimeTest{@Test void priorityCannotReducePhysicalTravelTime(){var segment=new RoadSegment("x","a","b",100,36,1,.1,false,false,Set.of(),0,0,0);assertEquals(10,segment.travelSeconds(),.001);}}
