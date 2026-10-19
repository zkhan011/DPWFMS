package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.dpworld.fms.integration.rabbitmq.RabbitTopology;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

class RabbitTelemetryConsumerTest {
  private static final UUID ASSET_ONE = UUID.fromString("ddd5f56d-5233-3971-9ee1-285996e30cc0");
  private static final UUID ASSET_TWO = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

  @Test void listensToTheVersionedTelemetryQueueWithRawMessages() throws Exception {
    var method = RabbitTelemetryConsumer.class.getMethod("receive", Message.class);
    RabbitListener listener = method.getAnnotation(RabbitListener.class);
    assertNotNull(listener);
    assertEquals(RabbitTopology.TELEMETRY, listener.queues()[0]);
  }

  @Test void consumesJsonArrayOfPositionEnvelopes() throws Exception {
    TelemetryIngestionService ingestion = mock(TelemetryIngestionService.class);
    Validator validator = mock(Validator.class);
    when(validator.validate(any(RabbitTelemetryConsumer.PositionEnvelope.class))).thenReturn(Set.of());
    ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    RabbitTelemetryConsumer consumer = new RabbitTelemetryConsumer(ingestion, validator, json);

    String first = envelope(ASSET_ONE, "ITV-001", "2026-10-19T10:00:00Z");
    String second = envelope(ASSET_TWO, "ITV-002", "2026-10-19T10:00:01Z");
    Message message = message("[" + first + "," + second + "]");

    consumer.receive(message);

    verify(ingestion).accept(eq(ASSET_ONE), any(TelemetryController.PositionTelemetry.class));
    verify(ingestion).accept(eq(ASSET_TWO), any(TelemetryController.PositionTelemetry.class));
  }

  @Test void continuesToConsumeSinglePositionEnvelope() throws Exception {
    TelemetryIngestionService ingestion = mock(TelemetryIngestionService.class);
    Validator validator = mock(Validator.class);
    when(validator.validate(any(RabbitTelemetryConsumer.PositionEnvelope.class))).thenReturn(Set.of());
    RabbitTelemetryConsumer consumer = new RabbitTelemetryConsumer(
        ingestion, validator, new ObjectMapper().registerModule(new JavaTimeModule()));

    consumer.receive(message(envelope(ASSET_ONE, "ITV-001", "2026-10-19T10:00:00Z")));

    verify(ingestion).accept(eq(ASSET_ONE), any(TelemetryController.PositionTelemetry.class));
  }

  private static Message message(String body) {
    return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
  }

  private static String envelope(UUID assetId, String fleetNumber, String occurredAt) {
    return """
        {"assetId":"%s","telemetry":{
          "messageId":"%s","schemaVersion":"1.0","occurredAt":"%s",
          "fleetNumber":"%s","assetType":"ITV","plantCode":"JEA",
          "latitude":24.9857,"longitude":55.0273,"heading":90,"speedKph":12,
          "energyPercent":80,"energySource":"DIESEL",
          "operationalStatus":"WORKING","availabilityStatus":"AVAILABLE"
        }}
        """.formatted(assetId, UUID.randomUUID(), occurredAt, fleetNumber);
  }
}
