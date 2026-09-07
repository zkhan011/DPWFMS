package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class TelemetrySecurityTest {
  @Test void positionIngestionRequiresVehicleManagePermission() throws Exception {
    Method method = TelemetryController.class.getMethod("position", java.util.UUID.class,
        TelemetryController.PositionTelemetry.class);
    assertTrue(method.getAnnotation(PreAuthorize.class).value().contains("vehicle.manage"));
  }
}
