package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class UserAdministrationSecurityTest {
  @Test void userCreationAndMapConfigurationRequireGranularPermissions() throws Exception {
    Method createUser = UserAdministrationController.class.getMethod("createUser",
        UserAdministrationController.CreateUserRequest.class,
        org.springframework.security.core.Authentication.class);
    assertTrue(createUser.getAnnotation(PreAuthorize.class).value().contains("user.manage"));

    Method updateMap = WorkspaceController.class.getMethod("updateMapConfiguration",
        java.util.UUID.class, WorkspaceController.MapConfigurationRequest.class,
        java.security.Principal.class);
    assertTrue(updateMap.getAnnotation(PreAuthorize.class).value().contains("map.configure"));
  }
}
