package com.dpworld.fms.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutomaticParkingAssignmentServiceTest {
  @Test void haversineReturnsOperationalDistanceInMetres() {
    double distance = AutomaticParkingAssignmentService.haversine(24.995, 55.04, 24.996, 55.041);
    assertTrue(distance > 140 && distance < 160);
  }
}
