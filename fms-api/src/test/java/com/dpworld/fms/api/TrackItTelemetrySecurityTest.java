package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class TrackItTelemetrySecurityTest {
  @Test
  void batchIngestionRequiresDedicatedIntegrationPermission() throws Exception {
    Method method = TrackItTelemetryController.class.getMethod("batch", JsonNode.class, Principal.class);
    assertTrue(method.getAnnotation(PreAuthorize.class).value().contains("trackit.telemetry.ingest"));
  }
}
