package com.dpworld.fms.api;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Entry point used by MQTT/RabbitMQ consumers after an asset material-state transaction commits. */
@Component
public class AutomationEventHandler {
  private final AutomationRuntime runtime;
  public AutomationEventHandler(AutomationRuntime runtime) { this.runtime = runtime; }

  @EventListener
  public void onMaterialChange(MaterialAssetChange event) {
    runtime.evaluate(event.assetId(), false, event.trigger().name());
  }

  public record MaterialAssetChange(UUID assetId, Trigger trigger) {}
  public enum Trigger {
    TELEMETRY_CHANGED, FUEL_LEVEL_CHANGED, JOB_STATUS_CHANGED, LOCATION_CHANGED,
    SHIFT_CHANGED, OPERATIONAL_STATUS_CHANGED, ASSET_BECAME_IDLE, WORKSHOP_RELEASED
  }
}
