package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutomaticChargingServiceTest {
  @Test void requestsChargingForAvailableIdleElectricAssetAtThreshold() {
    assertTrue(AutomaticChargingService.requiresCharging(
        "ELECTRIC", 20, "IDLE", "AVAILABLE", 20));
  }

  @Test void doesNotInterruptBusyOrCombustionAssets() {
    assertFalse(AutomaticChargingService.requiresCharging(
        "ELECTRIC", 10, "WORKING", "ASSIGNED", 20));
    assertFalse(AutomaticChargingService.requiresCharging(
        "DIESEL", 10, "IDLE", "AVAILABLE", 20));
    assertFalse(AutomaticChargingService.requiresCharging(
        "ELECTRIC", 21, "IDLE", "AVAILABLE", 20));
  }
}
