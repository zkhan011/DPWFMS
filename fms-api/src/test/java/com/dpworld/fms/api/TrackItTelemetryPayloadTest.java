package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrackItTelemetryPayloadTest {
  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void deserializesExactPascalCaseContractAndPreservesSignedCanValues() throws Exception {
    TrackItTelemetryPayload payload = json.readValue("""
        {"AssetID":"TT-00-1346","AssetNumber":"1346","Make":"Terberg","Model":"YT",
         "Timestamp":"2026-09-07T08:00:00Z","LastUpdatedTime":"2026-09-07T08:00:02Z",
         "Latitude":24.99,"Longitude":55.03,"Speed":4.5,"BatteryVoltage":25.2,
         "TotalFuelUsed":123456.789,"Location":{"LocationMappingStatus":"MAPPED",
         "LocationType":"YARD","LocationName":"Test yard","LocationCode":"TEST"},
         "DataAvailabilityFlag":{"GPSAvailable":true,"FuelDataAvailable":true,
         "EngineDataAvailable":true,"CANDataAvailable":true},
         "CANParameters":[{"Name":"Current Gear","Value":-1},{"Name":"Vendor Field","Value":"raw"}]}
        """, TrackItTelemetryPayload.class);

    assertEquals("TT-00-1346", payload.assetId());
    assertEquals(Instant.parse("2026-09-07T08:00:00Z"), payload.timestamp());
    assertEquals(new BigDecimal("123456.789"), payload.totalFuelUsed());
    assertEquals(-1, payload.canParameters().getFirst().value().intValue());
    assertEquals("Vendor Field", payload.canParameters().get(1).name());
  }

  @Test
  void optionalFuelEngineAndCanFieldsRemainUnavailableRatherThanZero() throws Exception {
    TrackItTelemetryPayload payload = json.readValue("""
        {"AssetID":"TT-2","AssetNumber":"2","Timestamp":"2026-09-07T08:00:00Z",
         "LastUpdatedTime":"2026-09-07T08:00:01Z","Latitude":24.99,"Longitude":55.03,
         "Speed":0,"BatteryVoltage":24,"DataAvailabilityFlag":{"GPSAvailable":true,
         "FuelDataAvailable":false,"EngineDataAvailable":false,"CANDataAvailable":false}}
        """, TrackItTelemetryPayload.class);
    assertFalse(payload.availability().fuelDataAvailable());
    assertEquals(null, payload.totalFuelUsed());
    assertEquals(null, payload.engineHours());
    assertTrue(payload.canParameters() == null || payload.canParameters().isEmpty());
  }
}
