package com.dpworld.fms.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class UserAdministrationController {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public UserAdministrationController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/users")
  @PreAuthorize("hasAuthority('user.read')")
  public List<Map<String, Object>> users() {
    return jdbc.queryForList("""
        SELECT u.id,u.username,u.display_name,u.enabled,u.service_account,u.last_login_at,
               u.created_at,u.version,
               coalesce(string_agg(DISTINCT r.name, ',' ORDER BY r.name),'') AS roles,
               coalesce(string_agg(DISTINCT p.name, ',' ORDER BY p.name),'') AS plants
          FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id
          LEFT JOIN roles r ON r.id=ur.role_id
          LEFT JOIN user_plant_assignments upa ON upa.user_id=u.id
          LEFT JOIN plants p ON p.id=upa.plant_id
         GROUP BY u.id ORDER BY u.username
        """);
  }

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('role.read')")
  public List<Map<String, Object>> roles() {
    return jdbc.queryForList("""
        SELECT r.id,r.name,r.description,r.protected_role,
               coalesce(array_agg(p.code ORDER BY p.code) FILTER (WHERE p.code IS NOT NULL),ARRAY[]::varchar[]) AS permissions
          FROM roles r LEFT JOIN role_permissions rp ON rp.role_id=r.id
          LEFT JOIN permissions p ON p.id=rp.permission_id
         GROUP BY r.id ORDER BY r.name
        """);
  }

  @PostMapping("/users")
  @PreAuthorize("hasAuthority('user.manage')")
  @Transactional
  public Map<String, Object> createUser(@Valid @RequestBody CreateUserRequest request,
                                        Authentication actor) {
    if (count("SELECT count(*) FROM users WHERE lower(username)=lower(?)", request.username()) > 0) {
      throw new IllegalArgumentException("username already exists");
    }
    List<Map<String, Object>> roles = queryIds("SELECT id,name,protected_role FROM roles WHERE id IN (" + placeholders(request.roleIds().size()) + ")", request.roleIds());
    if (roles.size() != request.roleIds().size()) throw new IllegalArgumentException("one or more roles do not exist");
    boolean protectedRole = roles.stream().anyMatch(role -> Boolean.TRUE.equals(role.get("protected_role")));
    if (protectedRole && !hasAuthority(actor, "system.configure")) {
      throw new org.springframework.security.access.AccessDeniedException("protected roles require system.configure");
    }
    if (!request.plantIds().isEmpty() && queryIds("SELECT id FROM plants WHERE id IN (" + placeholders(request.plantIds().size()) + ")", request.plantIds()).size() != request.plantIds().size()) {
      throw new IllegalArgumentException("one or more plants do not exist");
    }
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update("""
        INSERT INTO users(id,subject,username,display_name,password_hash,enabled,service_account,
          created_at,created_by,updated_at,password_changed_at)
        VALUES (?,?,?,?,?,TRUE,?,?,?,?,?)
        """, id, "local:" + request.username(), request.username(), request.displayName(),
        passwordEncoder.encode(request.password()), request.serviceAccount(), Timestamp.from(now), actor.getName(),
        Timestamp.from(now), Timestamp.from(now));
    request.roleIds().forEach(roleId -> jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", id, roleId));
    request.plantIds().forEach(plantId -> jdbc.update("INSERT INTO user_plant_assignments(user_id,plant_id,assigned_by) VALUES (?,?,?)", id, plantId, actor.getName()));
    audit(actor.getName(), "USER_CREATED", id, Map.of("username", request.username(), "roles", request.roleIds(), "plants", request.plantIds()));
    return jdbc.queryForMap("SELECT id,username,display_name,enabled,service_account,created_at,version FROM users WHERE id=?", id);
  }

  @PatchMapping("/users/{id}/enabled")
  @PreAuthorize("hasAuthority('user.manage')")
  @Transactional
  public void setEnabled(@PathVariable UUID id, @RequestParam boolean value, Principal actor) {
    if (!value && count("""
        SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id
        JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND u.enabled AND r.name='SUPER_ADMIN'
        """, id) > 0 && count("""
        SELECT count(DISTINCT u.id) FROM users u JOIN user_roles ur ON ur.user_id=u.id
        JOIN roles r ON r.id=ur.role_id WHERE u.enabled AND r.name='SUPER_ADMIN'
        """) <= 1) {
      throw new IllegalStateException("the last active Super Admin cannot be disabled");
    }
    if (jdbc.update("UPDATE users SET enabled=?,version=version+1,updated_at=now() WHERE id=?", value, id) != 1) {
      throw new IllegalArgumentException("unknown user " + id);
    }
    audit(actor.getName(), value ? "USER_ENABLED" : "USER_DISABLED", id, Map.of());
  }

  private List<Map<String, Object>> queryIds(String sql, List<UUID> ids) {
    return jdbc.queryForList(sql, ids.toArray());
  }
  private String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
  private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
  private boolean hasAuthority(Authentication authentication, String authority) {
    return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
  }
  private void audit(String actor, String action, UUID resourceId, Map<String, Object> after) {
    jdbc.update("INSERT INTO audit_logs(id,occurred_at,actor,action,resource_type,resource_id,after_value) VALUES (?,now(),?,?,?, ?,?::jsonb)",
        UUID.randomUUID(), actor, action, "USER", resourceId.toString(), toJson(after));
  }
  private String toJson(Map<String, Object> value) {
    String text = value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"summary\":\"" + text + "\"}";
  }

  public record CreateUserRequest(@NotBlank @Size(min=3,max=160) String username,
                                  @NotBlank @Size(max=160) String displayName,
                                  @NotBlank @Size(min=12,max=200) String password,
                                  boolean serviceAccount,
                                  @NotEmpty List<UUID> roleIds,
                                  List<UUID> plantIds) {
    public CreateUserRequest { plantIds = plantIds == null ? List.of() : List.copyOf(plantIds); roleIds = roleIds == null ? List.of() : List.copyOf(roleIds); }
  }
}
