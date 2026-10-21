package com.dpworld.fms.api;

import com.dpworld.fms.integration.rabbitmq.RabbitTopology;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Ingests canonical telemetry envelopes and normalized TrackIT payloads from RabbitMQ.
 */
@Component
public class RabbitTelemetryConsumer {
  private final TelemetryIngestionService ingestion;
  private final TrackItTelemetryIngestionService trackItIngestion;
  private final Validator validator;
  private final ObjectMapper json;

  @Autowired
  public RabbitTelemetryConsumer(TelemetryIngestionService ingestion,
      TrackItTelemetryIngestionService trackItIngestion, Validator validator, ObjectMapper json) {
    this.ingestion = ingestion;
    this.trackItIngestion = trackItIngestion;
    this.validator = validator;
    this.json = json;
  }

  /** Compatibility constructor for focused tests of canonical envelope ingestion. */
  RabbitTelemetryConsumer(TelemetryIngestionService ingestion, Validator validator, ObjectMapper json) {
    this(ingestion, null, validator, json);
  }

  @RabbitListener(queues = RabbitTopology.TELEMETRY)
  public void receive(Message message) throws IOException {
    JsonNode payload = json.readTree(message.getBody());
    if (payload == null || payload.isNull()) {
      throw new IllegalArgumentException("telemetry message must not be empty");
    }
    if (payload.isArray()) {
      if (payload.isEmpty()) {
        throw new IllegalArgumentException("telemetry batch must not be empty");
      }
      for (JsonNode item : payload) {
        receiveItem(item);
      }
      return;
    }
    receiveItem(payload);
  }

  private void receiveItem(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      throw new IllegalArgumentException("telemetry item must be a JSON object");
    }

    // The Java bridge publishes normalized TrackIT PascalCase records. Keep the
    // existing envelope path for the original DPWFMS RabbitMQ contract.
    if (payload.has("AssetID")) {
      if (trackItIngestion == null) {
        throw new IllegalStateException("TrackIT Rabbit ingestion is not configured");
      }
      trackItIngestion.ingest(List.of(payload), "rabbitmq");
      return;
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
