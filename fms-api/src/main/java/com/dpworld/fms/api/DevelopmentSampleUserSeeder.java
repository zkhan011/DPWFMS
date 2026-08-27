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
      new SampleUser("dispatcher.demo", "Demo Dispatcher", "DISPATCHER"),
      new SampleUser("operator.demo", "Demo Control Room Operator", "CONTROL_ROOM_OPERATOR"),
      new SampleUser("viewer.demo", "Demo Map Viewer", "REPORT_VIEWER"));

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
      UUID userId = findUser(sample.username());
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

  private UUID findUser(String username) {
    List<UUID> matches = jdbc.queryForList(
        "SELECT id FROM users WHERE lower(username)=lower(?)", UUID.class, username);
    return matches.isEmpty() ? null : matches.getFirst();
  }

  record SampleUser(String username, String displayName, String role) {}
}
