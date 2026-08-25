package com.dpworld.fms.api;

import com.dpworld.fms.application.automation.AutomationRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter that preserves every activated rule version. */
@Repository
public class AutomationRuleStore {
  private static final TypeReference<Map<String, Double>> NUMBER_MAP = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public AutomationRuleStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<AutomationRule> findAll() {
    return jdbc.query("""
        SELECT id, rule_code, rule_name, description, rule_kind, scope_type, scope_identifier,
               enabled, priority, effective_from, effective_to, thresholds, scoring_weights,
               suppression_seconds, cooldown_seconds, maximum_waiting_seconds, version,
               created_by, approved_by, created_at
          FROM automation_rules ORDER BY rule_code, version
        """, (rs, row) -> map(rs));
  }

  public AutomationRule insert(AutomationRule rule) {
    jdbc.update("""
        INSERT INTO automation_rules(id, rule_code, rule_name, description, rule_kind, scope_type,
          scope_identifier, enabled, priority, effective_from, effective_to, thresholds,
          scoring_weights, suppression_seconds, cooldown_seconds, maximum_waiting_seconds,
          version, created_by, approved_by, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
        """, rule.id(), rule.code(), rule.name(), rule.description(), rule.kind().name(),
        rule.scopeType().name(), rule.scopeId(), rule.enabled(), rule.priority(), rule.effectiveFrom(),
        rule.effectiveTo(), write(rule.thresholds()), write(rule.weights()), rule.suppressionSeconds(),
        rule.cooldownSeconds(), rule.maximumWaitingSeconds(), rule.version(), rule.createdBy(),
        rule.approvedBy(), rule.createdAt(), Instant.now());
    return rule;
  }

  private AutomationRule map(ResultSet rs) throws SQLException {
    return new AutomationRule(rs.getObject("id", UUID.class), rs.getString("rule_code"),
        rs.getString("rule_name"), rs.getString("description"),
        AutomationRule.RuleKind.valueOf(rs.getString("rule_kind")),
        AutomationRule.ScopeType.valueOf(rs.getString("scope_type")), rs.getString("scope_identifier"),
        rs.getBoolean("enabled"), rs.getInt("priority"), instant(rs, "effective_from"),
        instant(rs, "effective_to"), read(rs.getString("thresholds")),
        read(rs.getString("scoring_weights")), rs.getLong("suppression_seconds"),
        rs.getLong("cooldown_seconds"), rs.getLong("maximum_waiting_seconds"),
        rs.getInt("version"), rs.getString("created_by"), rs.getString("approved_by"),
        instant(rs, "created_at"));
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
  private Map<String, Double> read(String value) {
    try { return json.readValue(value, NUMBER_MAP); }
    catch (JsonProcessingException exception) { throw new IllegalStateException("invalid persisted rule JSON", exception); }
  }
  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (JsonProcessingException exception) { throw new IllegalArgumentException("invalid rule JSON", exception); }
  }
}
