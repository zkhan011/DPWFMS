package com.dpworld.fms.api;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  static final List<String> DEVELOPMENT_PERMISSIONS = List.of(
      "dashboard.read", "plant.read", "plant.manage", "map.read", "map.configure",
      "vehicle.read", "vehicle.manage", "vehicle.enable", "vehicle.disable", "order.read",
      "order.create", "order.assign", "order.cancel", "order.retry", "dispatch.read",
      "dispatch.execute", "dispatch.override", "parking.read", "parking.manage", "fueling.read",
      "fueling.manage", "charging.read", "charging.manage", "alert.read", "alert.acknowledge",
      "alert.resolve", "report.read", "report.export", "control_center.read",
      "control_center.operate", "integration.read", "integration.manage", "user.read",
      "user.manage", "role.read", "role.manage", "audit.read", "system.configure",
      "parking.assign", "parking.override", "parking.bay.manage", "parking.automation.run",
      "charging.assign", "charging.override", "charging.station.manage", "charging.automation.run",
      "parameters.read", "parameters.edit", "parameters.rollback");

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                "/swagger-ui.html").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  UserDetailsService localDevelopmentUsers(
      @Value("${DPWFMS_LOCAL_USERNAME}") String username,
      @Value("${DPWFMS_LOCAL_PASSWORD}") String password,
      JdbcTemplate jdbc,
      PasswordEncoder encoder) {
    if (password.length() < 12) {
      throw new IllegalStateException("DPWFMS_LOCAL_PASSWORD must contain at least 12 characters");
    }
    String bootstrapHash = encoder.encode(password);
    return requestedUsername -> {
      var users = jdbc.queryForList("SELECT id,username,password_hash,enabled,service_account FROM users WHERE lower(username)=lower(?)", requestedUsername);
      if (!users.isEmpty()) {
        var row = users.getFirst();
        if (!Boolean.TRUE.equals(row.get("enabled")) || Boolean.TRUE.equals(row.get("service_account"))
            || row.get("password_hash") == null) {
          throw new UsernameNotFoundException("user is disabled or has no interactive credential");
        }
        var authorities = jdbc.queryForList("""
            SELECT DISTINCT authority FROM (
              SELECT p.code AS authority FROM user_roles ur JOIN role_permissions rp ON rp.role_id=ur.role_id
              JOIN permissions p ON p.id=rp.permission_id WHERE ur.user_id=?
              UNION ALL
              SELECT 'ROLE_' || r.name AS authority FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?
            ) granted ORDER BY authority
            """, String.class, row.get("id"), row.get("id")).stream().map(SimpleGrantedAuthority::new).toList();
        return User.withUsername(String.valueOf(row.get("username")))
            .password(String.valueOf(row.get("password_hash"))).authorities(authorities)
            .disabled(false).build();
      }
      if (!username.equalsIgnoreCase(requestedUsername)) throw new UsernameNotFoundException("unknown user");
      var authorities = new java.util.ArrayList<GrantedAuthority>();
      DEVELOPMENT_PERMISSIONS.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
      authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
      return User.withUsername(username).password(bootstrapHash).authorities(authorities).build();
    };
  }

  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${dpwfms.cors.allowed-origins}") String origins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID", "X-Plant-ID"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
