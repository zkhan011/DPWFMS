package com.dpworld.fms.api;

import com.dpworld.fms.integration.rabbitmq.RabbitTopology;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Validates and ingests the canonical position envelope delivered by RabbitMQ. */
@Component
public class RabbitTelemetryConsumer {
  private final TelemetryIngestionService ingestion;
  private final Validator validator;

  public RabbitTelemetryConsumer(TelemetryIngestionService ingestion, Validator validator) {
    this.ingestion = ingestion;
    this.validator = validator;
  }

  @RabbitListener(queues = RabbitTopology.TELEMETRY)
  public void receive(PositionEnvelope envelope) {
    var violations = validator.validate(envelope);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException("invalid telemetry envelope: " + violations.iterator().next().getMessage());
    }
    ingestion.accept(envelope.assetId(), envelope.telemetry());
  }

  public record PositionEnvelope(@NotNull UUID assetId,
                                 @NotNull @Valid TelemetryController.PositionTelemetry telemetry) {}
}
