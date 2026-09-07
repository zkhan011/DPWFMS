package com.dpworld.fms.application.automation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AutomationDecision(
    UUID id, UUID assetId, String idempotencyKey, Instant evaluatedAt,
    Map<String, Object> inputSnapshot, List<String> rulesEvaluated, List<String> rulesMatched,
    boolean eligible, List<String> blockingReasons, List<CandidateScore> candidates,
    Action selectedAction, String selectedResourceId, boolean jobCreated,
    UUID jobId, UUID reservationId) {

  public AutomationDecision {
    inputSnapshot = Map.copyOf(inputSnapshot);
    rulesEvaluated = List.copyOf(rulesEvaluated);
    rulesMatched = List.copyOf(rulesMatched);
    blockingReasons = List.copyOf(blockingReasons);
    candidates = List.copyOf(candidates);
  }

  public enum Action { NONE, PARK, FUEL, EMERGENCY_INTERVENTION, FUEL_THEN_PARK }

  public record CandidateScore(String resourceId, boolean eligible, double totalScore,
                               Map<String, Double> components, List<String> rejectionReasons) {
    public CandidateScore {
      components = Map.copyOf(components);
      rejectionReasons = List.copyOf(rejectionReasons);
    }
  }
}
