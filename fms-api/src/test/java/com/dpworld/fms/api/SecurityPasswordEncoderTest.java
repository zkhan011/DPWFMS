package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityPasswordEncoderTest {
  @Test
  void configuredEncoderProducesAndVerifiesBcryptCredentials() {
    PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
    String encoded = encoder.encode("correct-horse-battery-staple");

    assertTrue(encoded.startsWith("$2"));
    assertTrue(encoder.matches("correct-horse-battery-staple", encoded));
    assertFalse(encoder.matches("wrong-password", encoded));
  }
}
