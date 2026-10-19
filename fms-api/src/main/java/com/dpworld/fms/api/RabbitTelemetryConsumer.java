package com.dpworld.fms.api;

import com.dpworld.fms.integration.rabbitmq.RabbitTopology;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Validates and ingests one telemetry envelope or a JSON array of envelopes from RabbitMQ. */
@Component
public class RabbitTelemetryConsumer {
  private final TelemetryIngestionService ingestion;
  private final Validator validator;
  private final ObjectMapper json;

  public RabbitTelemetryConsumer(TelemetryIngestionService ingestion, Validator validator, ObjectMapper json) {
    this.ingestion = ingestion;
    this.validator = validator;
    this.json = json;
  }

  @RabbitListener(queues = RabbitTopology.TELEMETRY)
  public void receive(Message message) throws IOException {
    JsonNode payload = json.readTree(message.getBody());
    if (payload == null || payload.isNull()) {
      throw new IllegalArgumentException("telemetry message must not be empty");
    }
    if (payload.isArray()) {
      if (payload.isEmpty()) throw new IllegalArgumentException("telemetry batch must not be empty");
      for (JsonNode item : payload) receiveEnvelope(item);
      return;
    }
    receiveEnvelope(payload);
  }

  private void receiveEnvelope(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      throw new IllegalArgumentException("telemetry item must be a JSON object");
    }

    PositionEnvelope envelope;
    try {
      envelope = json.treeToValue(payload, PositionEnvelope.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("invalid telemetry envelope", exception);
    }

    var violations = validator.validate(envelope);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException("invalid telemetry envelope: "
          + violations.iterator().next().getMessage());
    }
    ingestion.accept(envelope.assetId(), envelope.telemetry());
  }

  public record PositionEnvelope(@NotNull UUID assetId,
                                 @NotNull @Valid TelemetryController.PositionTelemetry telemetry) {}
}
