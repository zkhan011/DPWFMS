package com.dpworld.fms.application.automation;

import static com.dpworld.fms.application.automation.AutomationRule.ScopeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Version-preserving repository and deterministic scope resolver. */
public final class RuleConfigurationService {
  private final Map<UUID, AutomationRule> rules = new ConcurrentHashMap<>();

  public AutomationRule save(AutomationRule rule) {
    rules.put(rule.id(), rule);
    return rule;
  }

  public void replaceAll(List<AutomationRule> persistedRules) {
    rules.clear();
    persistedRules.forEach(this::save);
  }

  public AutomationRule createVersion(UUID previousId, AutomationRule replacement) {
    AutomationRule previous = require(previousId);
    if (!previous.code().equals(replacement.code()) || replacement.version() != previous.version() + 1) {
      throw new IllegalArgumentException("new rule version must retain code and increment version by one");
    }
    return save(replacement);
  }

  public List<AutomationRule> all() {
    return rules.values().stream()
        .sorted(Comparator.comparing(AutomationRule::code).thenComparingInt(AutomationRule::version))
        .toList();
  }

  public Optional<AutomationRule> resolve(AutomationRule.RuleKind kind,
                                           AssetAutomationSnapshot asset,
                                           Instant now) {
    return rules.values().stream()
        .filter(rule -> rule.kind() == kind && rule.activeAt(now) && matches(rule, asset))
        .sorted(Comparator.comparingInt((AutomationRule r) -> r.scopeType().specificity()).reversed()
            .thenComparing(AutomationRule::priority, Comparator.reverseOrder())
            .thenComparing(AutomationRule::version, Comparator.reverseOrder())
            .thenComparing(AutomationRule::code))
        .findFirst();
  }

  public AutomationRule setEnabled(UUID id, boolean enabled, String actor, Instant now) {
    AutomationRule old = require(id);
    AutomationRule changed = new AutomationRule(UUID.randomUUID(), old.code(), old.name(),
        old.description(), old.kind(), old.scopeType(), old.scopeId(), enabled, old.priority(),
        old.effectiveFrom(), old.effectiveTo(), old.thresholds(), old.weights(),
        old.suppressionSeconds(), old.cooldownSeconds(), old.maximumWaitingSeconds(),
        old.version() + 1, actor, enabled ? actor : old.approvedBy(), now);
    return save(changed);
  }

  private AutomationRule require(UUID id) {
    AutomationRule rule = rules.get(id);
    if (rule == null) throw new IllegalArgumentException("unknown automation rule " + id);
    return rule;
  }

  private boolean matches(AutomationRule rule, AssetAutomationSnapshot asset) {
    if (rule.scopeType() == ScopeType.GLOBAL) return true;
    if (rule.scopeId() == null) return false;
    return switch (rule.scopeType()) {
      case TERMINAL -> rule.scopeId().equals(asset.terminalId());
      case OPERATIONAL_ZONE -> rule.scopeId().equals(asset.zoneId());
      case ASSET_TYPE -> rule.scopeId().equals(asset.assetType().name());
      case ASSET_GROUP -> rule.scopeId().equals(asset.assetGroup());
      case ASSET -> rule.scopeId().equals(asset.assetId().toString());
      case GLOBAL -> true;
    };
  }

  public static RuleConfigurationService withDefaults(Instant now) {
    RuleConfigurationService service = new RuleConfigurationService();
    service.save(new AutomationRule(UUID.randomUUID(), "AUTO_PARK_IDLE", "Idle asset parking",
        "Parks an eligible asset after its idle and suppression windows", AutomationRule.RuleKind.PARKING,
        ScopeType.GLOBAL, null, true, 100, now.minusSeconds(1), null,
        Map.of("telemetryFreshnessSeconds", 60d, "idleSeconds", 300d,
            "safetyReservePercent", 10d, "reservationSeconds", 600d),
        Map.of("distance", 1d, "travelTime", 1d, "congestion", 1d, "nextJob", .8d,
            "zone", .5d, "maneuver", .7d, "occupancy", .6d, "preference", 1d),
        600, 300, 900, 1, "system", "system", now));
    service.save(new AutomationRule(UUID.randomUUID(), "AUTO_FUEL_LEVEL", "Fuel-level automation",
        "Creates safe fueling work using threshold classification and hysteresis", AutomationRule.RuleKind.FUELING,
        ScopeType.GLOBAL, null, true, 200, now.minusSeconds(1), null,
        Map.of("telemetryFreshnessSeconds", 60d, "lowFuelPercent", 35d,
            "criticalFuelPercent", 20d, "emergencyFuelPercent", 10d, "hysteresisPercent", 3d,
            "safetyReservePercent", 5d, "reservationSeconds", 900d),
        Map.of("travelTime", 1d, "queueWait", 1.2d, "congestion", .8d, "routeDeviation", .7d,
            "serviceTime", .5d, "stationPriority", .7d, "nextJob", .5d, "risk", 2d),
        0, 1800, 900, 1, "system", "system", now));
    return service;
  }
}
