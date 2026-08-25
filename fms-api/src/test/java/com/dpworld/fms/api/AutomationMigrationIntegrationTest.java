package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AutomationMigrationIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test void migratesVersionedAutomationRulesAndUniquenessGuards() throws Exception {
    Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
        postgres.getPassword()).load();
    assertEquals(4, flyway.migrate().migrationsExecuted);
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         var statement = connection.createStatement()) {
      try (var rules = statement.executeQuery("SELECT count(*) FROM automation_rules")) {
        assertTrue(rules.next());
        assertEquals(2, rules.getInt(1));
      }
      try (var settings = statement.executeQuery("SELECT count(*) FROM scheduler_configuration WHERE config_key LIKE 'automation.%'")) {
        assertTrue(settings.next());
        assertEquals(3, settings.getInt(1));
      }
    }
  }
}
