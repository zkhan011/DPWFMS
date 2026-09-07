package com.dpworld.fms.application.automation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** An immutable, versioned automation configuration. */
public record AutomationRule(
    UUID id,
    String code,
    String name,
    String description,
    RuleKind kind,
    ScopeType scopeType,
    String scopeId,
    boolean enabled,
    int priority,
    Instant effectiveFrom,
    Instant effectiveTo,
    Map<String, Double> thresholds,
    Map<String, Double> weights,
    long suppressionSeconds,
    long cooldownSeconds,
    long maximumWaitingSeconds,
    int version,
    String createdBy,
    String approvedBy,
    Instant createdAt) {

  public AutomationRule {
    Objects.requireNonNull(id);
    Objects.requireNonNull(code);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(scopeType);
    Objects.requireNonNull(createdBy);
    thresholds = Map.copyOf(thresholds == null ? Map.of() : thresholds);
    weights = Map.copyOf(weights == null ? Map.of() : weights);
    if (version < 1) throw new IllegalArgumentException("version must be positive");
    if (cooldownSeconds < 0 || suppressionSeconds < 0) throw new IllegalArgumentException("periods cannot be negative");
    validateThresholds(kind, thresholds);
  }

  public boolean activeAt(Instant instant) {
    return enabled && !instant.isBefore(effectiveFrom == null ? Instant.MIN : effectiveFrom)
        && instant.isBefore(effectiveTo == null ? Instant.MAX : effectiveTo);
  }

  private static void validateThresholds(RuleKind kind, Map<String, Double> values) {
    if (kind != RuleKind.FUELING) return;
    double emergency = values.getOrDefault("emergencyFuelPercent", 10d);
    double critical = values.getOrDefault("criticalFuelPercent", 20d);
    double low = values.getOrDefault("lowFuelPercent", 35d);
    if (!(0 <= emergency && emergency < critical && critical < low && low <= 100)) {
      throw new IllegalArgumentException("fuel thresholds must satisfy 0 <= emergency < critical < low <= 100");
    }
  }

  public enum RuleKind { PARKING, FUELING }
  public enum ScopeType {
    GLOBAL(0), TERMINAL(1), OPERATIONAL_ZONE(2), ASSET_TYPE(3), ASSET_GROUP(4), ASSET(5);
    private final int specificity;
    ScopeType(int specificity) { this.specificity = specificity; }
    public int specificity() { return specificity; }
  }
}
