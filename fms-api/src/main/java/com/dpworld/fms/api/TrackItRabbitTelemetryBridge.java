package com.dpworld.fms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts TrackIT queue payloads to DPWFMS TrackIT telemetry JSON and forwards them
 * to the existing business exchange. Disabled unless explicitly enabled.
 */
@Component
public class TrackItRabbitTelemetryBridge {
  private final ObjectMapper json;
  private final RabbitTemplate rabbit;
  private final String outputExchange;
  private final String outputRoutingKey;
  private final String defaultVehicleType;
  private final String equipmentType;
  private final String fuelType;

  public TrackItRabbitTelemetryBridge(ObjectMapper json, RabbitTemplate rabbit,
      @Value("${trackit.telemetry.rabbit-bridge.output-exchange:dpwfms.business.v1}") String outputExchange,
      @Value("${trackit.telemetry.rabbit-bridge.output-routing-key:telemetry.asset.position.v1}") String outputRoutingKey,
      @Value("${trackit.telemetry.rabbit-bridge.default-vehicle-type:FUEL}") String defaultVehicleType,
      @Value("${trackit.telemetry.rabbit-bridge.equipment-type:TRACTOR}") String equipmentType,
      @Value("${trackit.telemetry.rabbit-bridge.fuel-type:DIESEL}") String fuelType) {
    this.json = json;
    this.rabbit = rabbit;
    this.outputExchange = outputExchange;
    this.outputRoutingKey = outputRoutingKey;
    this.defaultVehicleType = defaultVehicleType;
    this.equipmentType = equipmentType;
    this.fuelType = fuelType;
  }

  @RabbitListener(
      queues = "${trackit.telemetry.rabbit-bridge.input-queue:tenant51.telemetry}",
      autoStartup = "${trackit.telemetry.rabbit-bridge.enabled:false}")
  public void forward(Message incoming) throws IOException {
    JsonNode payload = json.readTree(incoming.getBody());
    if (payload == null || payload.isNull()) {
      throw new IllegalArgumentException("TrackIT message must not be empty");
    }

    JsonNode adapted;
    if (payload.isArray()) {
      if (payload.isEmpty()) {
        throw new IllegalArgumentException("TrackIT batch must not be empty");
      }
      ArrayNode batch = json.createArrayNode();
      for (JsonNode item : payload) {
        batch.add(adaptRecord(item));
      }
      adapted = batch;
    } else {
      adapted = adaptRecord(payload);
    }

    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setMessageId(incoming.getMessageProperties().getMessageId());
    properties.setCorrelationId(incoming.getMessageProperties().getCorrelationId());
    properties.setHeaders(new HashMap<>(incoming.getMessageProperties().getHeaders()));
    Message outgoing = new Message(json.writeValueAsBytes(adapted), properties);
    rabbit.send(outputExchange, outputRoutingKey, outgoing);
  }

  private ObjectNode adaptRecord(JsonNode source) {
    if (source == null || !source.isObject()) {
      throw new IllegalArgumentException("TrackIT batch items must be JSON objects");
    }

    ObjectNode result = ((ObjectNode) source).deepCopy();
    String energySource = text(source, "EnergySource").toUpperCase(Locale.ROOT);
    String vehicleType = source.path("VehicleType").asText("");
    if (vehicleType.isBlank()) {
      vehicleType = energySource.equals("ELECTRIC") || energySource.equals("EV")
          ? "ELECTRIC" : defaultVehicleType;
    }

    result.put("VehicleType", vehicleType);
    result.put("EquipmentType", source.path("EquipmentType").asText(equipmentType));
    if (vehicleType.equalsIgnoreCase("ELECTRIC")) {
      result.putNull("FuelType");
    } else if (!source.hasNonNull("FuelType")) {
      result.put("FuelType", fuelType);
    }

    for (String field : new String[] {
        "BatteryLevelPercent", "LowBattery", "FuelLevelPercent", "LowFuel",
        "FuelConsumptionRate", "TotalFuelUsed", "EngineHours"}) {
      if (!result.has(field)) {
        result.putNull(field);
      }
    }

    JsonNode flags = source.get("DataAvailabilityFlag");
    if (flags == null || !flags.isObject()) {
      ObjectNode defaults = json.createObjectNode();
      defaults.put("GPSAvailable", false);
      defaults.put("FuelDataAvailable", false);
      defaults.put("EngineDataAvailable", false);
      defaults.put("CANDataAvailable", false);
      result.set("DataAvailabilityFlag", defaults);
    }
    if (!source.has("CANParameters") || source.get("CANParameters").isNull()) {
      result.set("CANParameters", json.createArrayNode());
    }

    result.set("Location", normalizeLocation(source.get("Location")));
    return result;
  }

  private ObjectNode normalizeLocation(JsonNode source) {
    ObjectNode location = json.createObjectNode();
    String name = source == null ? "" : text(source, "LocationName").trim();
    String key = name.toLowerCase(Locale.ROOT);
    String terminal = switch (key) {
      case "operation t1", "engineering t1" -> "T1";
      case "operation t2", "engineering t2" -> "T2";
      case "operation t3", "engineering t3" -> "T3";
      default -> null;
    };

    if (terminal != null) {
      location.put("LocationMappingStatus", "InsideDefinedLocation");
      location.put("LocationType", terminal);
      location.put("LocationName", "Terminal " + terminal.substring(1));
      location.put("LocationCode", terminal);
    } else if (source != null
        && "mapped".equalsIgnoreCase(text(source, "LocationMappingStatus"))) {
      location.put("LocationMappingStatus", "Mapped");
      copyNullable(source, location, "LocationType");
      putNullable(location, "LocationName", name);
      copyNullable(source, location, "LocationCode");
    } else {
      location.put("LocationMappingStatus", "OutsideDefinedLocation");
      location.put("LocationType", "OUTSIDE");
      location.put("LocationName", "Outside");
      location.put("LocationCode", "OUTSIDE");
    }
    return location;
  }

  private void copyNullable(JsonNode source, ObjectNode target, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) {
      target.putNull(field);
    } else {
      target.set(field, value.deepCopy());
    }
  }

  private void putNullable(ObjectNode target, String field, String value) {
    if (value == null || value.isBlank()) {
      target.putNull(field);
    } else {
      target.put(field, value);
    }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? "" : value.asText("");
  }
}
