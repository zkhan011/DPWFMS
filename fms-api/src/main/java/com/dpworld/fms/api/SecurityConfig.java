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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
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
      "user.manage", "role.read", "role.manage", "audit.read", "system.configure");

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
      @Value("${DPWFMS_LOCAL_PASSWORD}") String password) {
    if (password.length() < 12) {
      throw new IllegalStateException("DPWFMS_LOCAL_PASSWORD must contain at least 12 characters");
    }
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    var authorities = DEVELOPMENT_PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList();
    return new InMemoryUserDetailsManager(User.withUsername(username)
        .password(encoder.encode(password)).authorities(authorities).build());
  }

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
