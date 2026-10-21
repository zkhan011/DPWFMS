package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class TrackItRabbitTelemetryBridgeTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void convertsTrackItRecordAndPreservesSourceFields() throws Exception {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    TrackItRabbitTelemetryBridge bridge =
        new TrackItRabbitTelemetryBridge(json, rabbit, "dpwfms.business.v1",
            "telemetry.asset.position.v1", "FUEL", "TRACTOR", "DIESEL");

    String body = """
        {
          "AssetID":"TT-00-1000-PL","AssetNumber":"TT-00-1000-PL",
          "Make":"Terberg","Model":"YT 222","EnergySource":"NA",
          "Timestamp":"2026-10-21T09:01:41Z","LastUpdatedTime":"2026-10-21T05:01:54Z",
          "Latitude":24.98471,"Longitude":55.0629366,"Speed":0.0,"BatteryVoltage":25.478,
          "Location":{"LocationMappingStatus":"Mapped","LocationName":"Operation T1","LocationCode":"Operation T1"},
          "DataAvailabilityFlag":{"GPSAvailable":true,"FuelDataAvailable":false,"EngineDataAvailable":false,"CANDataAvailable":false}
        }
        """;
    bridge.forward(message(body));

    ArgumentCaptor<Message> published = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(eq("dpwfms.business.v1"), eq("telemetry.asset.position.v1"), published.capture());
    JsonNode result = json.readTree(published.getValue().getBody());
    assertEquals("FUEL", result.path("VehicleType").asText());
    assertEquals("TRACTOR", result.path("EquipmentType").asText());
    assertEquals("DIESEL", result.path("FuelType").asText());
    assertEquals("T1", result.path("Location").path("LocationType").asText());
    assertEquals("Terminal 1", result.path("Location").path("LocationName").asText());
    assertNull(result.get("FuelLevelPercent").isNull() ? null : result.get("FuelLevelPercent"));
    assertEquals("TT-00-1000-PL", result.path("AssetID").asText());
    assertEquals(0.0, result.path("Speed").asDouble());
    assertEquals(0, result.path("CANParameters").size());
  }

  @Test
  void convertsBatchAndPreservesElectricClassification() throws Exception {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    TrackItRabbitTelemetryBridge bridge =
        new TrackItRabbitTelemetryBridge(json, rabbit, "dpwfms.business.v1",
            "telemetry.asset.position.v1", "FUEL", "TRACTOR", "DIESEL");

    bridge.forward(message("""
        [{"AssetID":"EV-1","EnergySource":"ELECTRIC","Location":{}}]
        """));

    ArgumentCaptor<Message> published = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(eq("dpwfms.business.v1"), eq("telemetry.asset.position.v1"), published.capture());
    JsonNode result = json.readTree(published.getValue().getBody()).get(0);
    assertEquals("ELECTRIC", result.path("VehicleType").asText());
    assertTrueNull(result.get("FuelType"));
    assertEquals(0, result.path("CANParameters").size());
  }

  private static void assertTrueNull(JsonNode node) {
    org.junit.jupiter.api.Assertions.assertTrue(node != null && node.isNull());
  }

  private static Message message(String body) {
    return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
  }
}
