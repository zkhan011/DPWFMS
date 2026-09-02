package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

class ControllerParameterNameTest {
  @Test
  void requestParametersHaveExplicitNamesIndependentOfCompilerMetadata() {
    List<Class<?>> controllers = List.of(AutomationController.class, OperationsController.class,
        TelemetryController.class, UserAdministrationController.class, WorkspaceController.class);

    controllers.stream().flatMap(controller -> List.of(controller.getDeclaredMethods()).stream())
        .flatMap(method -> List.of(method.getParameters()).stream())
        .forEach(this::assertExplicitSpringName);
  }

  private void assertExplicitSpringName(Parameter parameter) {
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    if (pathVariable != null) {
      assertFalse(pathVariable.name().isBlank() && pathVariable.value().isBlank(),
          () -> "unnamed @PathVariable on " + parameter.getDeclaringExecutable());
    }
    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam != null) {
      assertFalse(requestParam.name().isBlank() && requestParam.value().isBlank(),
          () -> "unnamed @RequestParam on " + parameter.getDeclaringExecutable());
    }
  }
}
