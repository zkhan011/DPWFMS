package com.dpworld.fms.api;

import com.dpworld.fms.application.automation.AutomationDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only audit adapter for complete automation decision evidence. */
@Repository
public class AutomationDecisionStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  public AutomationDecisionStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

  public void append(AutomationDecision decision) {
    jdbc.update("""
        INSERT INTO automation_decisions(id, asset_id, idempotency_key, evaluated_at, trigger_type,
          input_snapshot, rules_evaluated, rules_matched, eligible, blocking_reasons,
          candidate_scores, selected_action, selected_resource_id, job_id, reservation_id)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
        """, decision.id(), decision.assetId(), decision.idempotencyKey(), Timestamp.from(decision.evaluatedAt()),
        String.valueOf(decision.inputSnapshot().get("trigger")), write(decision.inputSnapshot()),
        write(decision.rulesEvaluated()), write(decision.rulesMatched()), decision.eligible(),
        write(decision.blockingReasons()), write(decision.candidates()), decision.selectedAction().name(),
        decision.selectedResourceId(), null, null);
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (JsonProcessingException exception) { throw new IllegalArgumentException("decision cannot be audited", exception); }
  }
}
