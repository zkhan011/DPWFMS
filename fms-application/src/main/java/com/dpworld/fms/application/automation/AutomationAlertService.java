package com.dpworld.fms.application.automation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AutomationAlertService {
  private final ConcurrentMap<String, Alert> unresolved = new ConcurrentHashMap<>();

  public Alert raise(UUID assetId, String code, Severity severity, String message, Instant now,
                     Duration repeatSuppression) {
    String key = assetId + ":" + code;
    return unresolved.compute(key, (ignored, old) -> old != null && old.lastRaisedAt()
        .plus(repeatSuppression).isAfter(now) ? old
        : new Alert(old == null ? UUID.randomUUID() : old.id(), assetId, code, severity,
            message, old == null ? now : old.firstRaisedAt(), now, false));
  }

  public List<Alert> active() { return unresolved.values().stream().filter(a -> !a.resolved()).toList(); }

  public enum Severity { WARNING, MAJOR, CRITICAL }
  public record Alert(UUID id, UUID assetId, String code, Severity severity, String message,
                      Instant firstRaisedAt, Instant lastRaisedAt, boolean resolved) {}
}
