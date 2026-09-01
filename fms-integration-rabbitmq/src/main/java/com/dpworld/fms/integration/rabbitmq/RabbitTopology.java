package com.dpworld.fms.integration.rabbitmq;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {
  public static final String BUSINESS = "dpwfms.business.v1";
  public static final String DLX = "dpwfms.dlx.v1";
  public static final String DISPATCH = "dpwfms.dispatch.commands.q";
  public static final String TELEMETRY = "dpwfms.telemetry.position.q";

  @Bean
  Declarables topology() {
    var business = new TopicExchange(BUSINESS, true, false);
    var dlx = new TopicExchange(DLX, true, false);
    var dispatch = QueueBuilder.durable(DISPATCH).quorum().deadLetterExchange(DLX)
        .deadLetterRoutingKey("dead.dispatch").build();
    var telemetry = QueueBuilder.durable(TELEMETRY).quorum().deadLetterExchange(DLX)
        .deadLetterRoutingKey("dead.telemetry.position").build();
    var dead = QueueBuilder.durable("dpwfms.integration.dlq").quorum().build();
    return new Declarables(business, dlx, dispatch, telemetry, dead,
        BindingBuilder.bind(dispatch).to(business).with("job.dispatch.command"),
        BindingBuilder.bind(telemetry).to(business).with("telemetry.asset.position.v1"),
        BindingBuilder.bind(dead).to(dlx).with("dead.#"));
  }
}
