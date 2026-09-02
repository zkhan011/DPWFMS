package com.dpworld.fms.application;
import org.junit.jupiter.api.Test; import java.time.*; import static org.junit.jupiter.api.Assertions.*;
class TelemetryProcessorTest { @Test void detectsDuplicatesAndOutOfOrder(){var p=new TelemetryProcessor(Duration.ofHours(1));var n=Instant.now();assertEquals(TelemetryProcessor.Result.ACCEPTED,p.accept("1","a",n,n));assertEquals(TelemetryProcessor.Result.DUPLICATE,p.accept("1","a",n,n));assertEquals(TelemetryProcessor.Result.OUT_OF_ORDER,p.accept("2","a",n.minusSeconds(1),n));} }
