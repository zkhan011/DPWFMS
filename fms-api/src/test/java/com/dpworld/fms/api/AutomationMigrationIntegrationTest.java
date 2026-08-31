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
    assertEquals(7, flyway.migrate().migrationsExecuted);
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
      try (var mapGrant = statement.executeQuery("""
          SELECT count(*) FROM role_permissions rp
          JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id
          WHERE r.name='REPORT_VIEWER' AND p.code='map.read'
          """)) {
        assertTrue(mapGrant.next());
        assertEquals(1, mapGrant.getInt(1));
      }
      try (var sampleUsers = statement.executeQuery("""
          SELECT count(*), count(*) FILTER (WHERE enabled), count(*) FILTER (WHERE password_hash IS NULL)
          FROM users WHERE created_by='flyway-sample'
          """)) {
        assertTrue(sampleUsers.next());
        assertEquals(4, sampleUsers.getInt(1));
        assertEquals(0, sampleUsers.getInt(2));
        assertEquals(4, sampleUsers.getInt(3));
      }
      try (var grants = statement.executeQuery("""
          SELECT count(DISTINCT p.code) FROM users u JOIN user_roles ur ON ur.user_id=u.id
          JOIN role_permissions rp ON rp.role_id=ur.role_id JOIN permissions p ON p.id=rp.permission_id
          WHERE u.created_by='flyway-sample'
          """)) {
        assertTrue(grants.next());
        assertEquals(38, grants.getInt(1));
      }
    }
  }
}
