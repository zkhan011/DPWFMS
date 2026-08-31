package com.dpworld.fms.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates opt-in development identities without committing a shared password. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "dpwfms.development.sample-users-enabled", havingValue = "true")
public class DevelopmentSampleUserSeeder implements ApplicationRunner {
  private static final List<SampleUser> SAMPLE_USERS = List.of(
      new SampleUser("admin.demo", "Demo Administrator", "SUPER_ADMIN"),
      new SampleUser("dispatcher.demo", "Demo Dispatcher", "SUPER_ADMIN"),
      new SampleUser("operator.demo", "Demo Control Room Operator", "SUPER_ADMIN"),
      new SampleUser("viewer.demo", "Demo Map Viewer", "SUPER_ADMIN"));

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final String password;

  public DevelopmentSampleUserSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                     @Value("${DPWFMS_SAMPLE_USER_PASSWORD:}") String password) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (password == null || password.length() < 12) {
      throw new IllegalStateException(
          "DPWFMS_SAMPLE_USER_PASSWORD must contain at least 12 characters when sample users are enabled");
    }
    Map<String, UUID> roles = jdbc.query("SELECT id,name FROM roles", resultSet -> {
      Map<String, UUID> values = new java.util.HashMap<>();
      while (resultSet.next()) values.put(resultSet.getString("name"), resultSet.getObject("id", UUID.class));
      return values;
    });
    List<UUID> plants = jdbc.queryForList("SELECT id FROM plants WHERE enabled", UUID.class);

    for (SampleUser sample : SAMPLE_USERS) {
      UUID roleId = roles.get(sample.role());
      if (roleId == null) throw new IllegalStateException("required sample role is missing: " + sample.role());
      ExistingUser existing = findUser(sample.username());
      if (existing != null && !existing.managedSample()) continue;
      UUID userId = existing == null ? null : existing.id();
      if (userId == null) {
        userId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO users(id,subject,username,display_name,password_hash,enabled,service_account,
              created_at,created_by,updated_at,password_changed_at)
            VALUES (?,?,?,?,?,TRUE,FALSE,now(),'development-seeder',now(),now())
            """, userId, "local:" + sample.username(), sample.username(), sample.displayName(),
            passwordEncoder.encode(password));
        jdbc.update("""
            INSERT INTO audit_logs(id,occurred_at,actor,action,resource_type,resource_id,after_value)
            VALUES (?,now(),'development-seeder','USER_CREATED','USER',?,jsonb_build_object('username',?,'role',?))
            """, UUID.randomUUID(), userId.toString(), sample.username(), sample.role());
      } else {
        jdbc.update("""
            UPDATE users SET password_hash=?,enabled=TRUE,password_changed_at=now(),updated_at=now(),version=version+1
            WHERE id=? AND created_by='flyway-sample'
            """, passwordEncoder.encode(password), userId);
        jdbc.update("""
            INSERT INTO audit_logs(id,occurred_at,actor,action,resource_type,resource_id,after_value)
            VALUES (?,now(),'development-seeder','USER_ENABLED','USER',?,jsonb_build_object('username',?))
            """, UUID.randomUUID(), userId.toString(), sample.username());
      }
      jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?) ON CONFLICT DO NOTHING", userId, roleId);
      for (UUID plantId : plants) {
        jdbc.update("""
            INSERT INTO user_plant_assignments(user_id,plant_id,assigned_by)
            VALUES (?,?,'development-seeder') ON CONFLICT DO NOTHING
            """, userId, plantId);
      }
    }
  }

  private ExistingUser findUser(String username) {
    List<Map<String, Object>> matches = jdbc.queryForList(
        "SELECT id,created_by FROM users WHERE lower(username)=lower(?)", username);
    if (matches.isEmpty()) return null;
    Map<String, Object> user = matches.getFirst();
    return new ExistingUser((UUID) user.get("id"), "flyway-sample".equals(user.get("created_by")));
  }

  record SampleUser(String username, String displayName, String role) {}
  record ExistingUser(UUID id, boolean managedSample) {}
}
