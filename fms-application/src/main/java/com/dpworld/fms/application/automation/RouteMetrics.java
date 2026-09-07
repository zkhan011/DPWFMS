package com.dpworld.fms.application.automation;

public record RouteMetrics(boolean valid, double distanceMetres, double travelSeconds,
                           double congestionCost, double deviationCost, String rejectionReason) {
  public static RouteMetrics invalid(String reason) {
    return new RouteMetrics(false, 0, 0, 0, 0, reason);
  }
}
