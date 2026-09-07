package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PermissionSecurityTest {
  @Test void localBootstrapUsesPermissionsRatherThanRoleNames() {
    assertTrue(SecurityConfig.DEVELOPMENT_PERMISSIONS.contains("system.configure"));
    assertTrue(SecurityConfig.DEVELOPMENT_PERMISSIONS.contains("dispatch.execute"));
    assertFalse(SecurityConfig.DEVELOPMENT_PERMISSIONS.stream().anyMatch(value -> value.startsWith("ROLE_")));
  }

  @Test void operationalMutationsDeclarePermissionChecks() throws Exception {
    Method createPlant = WorkspaceController.class.getMethod("createPlant",
        WorkspaceController.PlantRequest.class, java.security.Principal.class);
    assertTrue(createPlant.getAnnotation(PreAuthorize.class).value().contains("system.configure"));
    Method createJob = OperationsController.class.getDeclaredMethod("create",
        OperationsController.CreateJob.class);
    assertTrue(createJob.getAnnotation(PreAuthorize.class).value().contains("order.create"));
  }
}
