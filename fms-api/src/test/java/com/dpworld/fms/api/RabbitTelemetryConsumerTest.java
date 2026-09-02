package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dpworld.fms.integration.rabbitmq.RabbitTopology;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

class RabbitTelemetryConsumerTest {
  @Test void consumesTheVersionedTelemetryQueue() throws Exception {
    Method method = RabbitTelemetryConsumer.class.getMethod(
        "receive", RabbitTelemetryConsumer.PositionEnvelope.class);
    RabbitListener listener = method.getAnnotation(RabbitListener.class);
    assertNotNull(listener);
    assertEquals(RabbitTopology.TELEMETRY, listener.queues()[0]);
  }
}
