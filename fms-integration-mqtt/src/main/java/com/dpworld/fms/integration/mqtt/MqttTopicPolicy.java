package com.dpworld.fms.integration.mqtt;
import java.util.regex.*;
public final class MqttTopicPolicy { private static final Pattern TOPIC=Pattern.compile("dpwfms/assets/([A-Za-z0-9_-]+)/((?:position|telemetry|status|job-status|alerts))"); public Topic parse(String topic){var m=TOPIC.matcher(topic);if(!m.matches())throw new IllegalArgumentException("unsupported MQTT topic");return new Topic(m.group(1),m.group(2));} public record Topic(String assetId,String messageType){} }
