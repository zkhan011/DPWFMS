package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevelopmentSampleUserSeederTest {
  @Test
  void refusesToSeedUsersWithoutASecureEnvironmentPassword() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    DevelopmentSampleUserSeeder seeder = new DevelopmentSampleUserSeeder(
        jdbc, mock(PasswordEncoder.class), "too-short");

    assertThrows(IllegalStateException.class, () -> seeder.run(mock(ApplicationArguments.class)));
    verifyNoInteractions(jdbc);
  }
}
