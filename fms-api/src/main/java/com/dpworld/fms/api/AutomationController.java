package com.dpworld.fms.api;

import com.dpworld.fms.application.automation.AutomationDecision;
import com.dpworld.fms.application.automation.AutomationRule;
import com.dpworld.fms.simulator.AutomationScenario;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {
  private final AutomationRuntime runtime;
  private final AutomationRuleStore store;
  public AutomationController(AutomationRuntime runtime, AutomationRuleStore store) {
    this.runtime = runtime;
    this.store = store;
  }

  @GetMapping("/rules") public List<AutomationRule> rules() { return runtime.rules().all(); }

  @PostMapping("/rules")
  @PreAuthorize("hasAuthority('system.configure')")
  public AutomationRule createRule(@Valid @RequestBody AutomationRule rule, Principal actor) {
    AutomationRule saved = runtime.rules().save(store.insert(rule));
    runtime.audit(actor.getName(), "AUTOMATION_RULE_CREATED", saved.id().toString(), Map.of("version", saved.version()));
    return saved;
  }

  @PostMapping("/rules/{id}/versions")
  @PreAuthorize("hasAuthority('system.configure')")
  public AutomationRule version(@PathVariable UUID id, @Valid @RequestBody AutomationRule rule, Principal actor) {
    AutomationRule saved = runtime.rules().createVersion(id, rule);
    store.insert(saved);
    runtime.audit(actor.getName(), "AUTOMATION_RULE_VERSIONED", saved.id().toString(), Map.of("previousId", id, "version", saved.version()));
    return saved;
  }

  @PostMapping("/rules/{id}/enabled")
  @PreAuthorize("hasAuthority('system.configure')")
  public AutomationRule enabled(@PathVariable UUID id, @RequestParam boolean value, Principal actor) {
    AutomationRule changed = runtime.rules().setEnabled(id, value, actor.getName(), Instant.now());
    AutomationRule saved = store.insert(changed);
    runtime.audit(actor.getName(), value ? "AUTOMATION_RULE_ENABLED" : "AUTOMATION_RULE_DISABLED", saved.id().toString(), Map.of("value", value));
    return saved;
  }

  @PostMapping("/decisions/simulate/{assetId}")
  public AutomationDecision simulate(@PathVariable UUID assetId) {
    return runtime.evaluate(assetId, true, "RULE_SIMULATION");
  }

  @PostMapping("/assets/{assetId}/evaluate")
  @PreAuthorize("hasAuthority('dispatch.override')")
  public AutomationDecision evaluate(@PathVariable UUID assetId) {
    return runtime.evaluate(assetId, false, "MANUAL_EVALUATION");
  }

  @PostMapping("/assets/{assetId}/parking")
  @PreAuthorize("hasAuthority('dispatch.override')")
  public AutomationDecision parking(@PathVariable UUID assetId) {
    return runtime.evaluate(assetId, false, "DISPATCHER_AUTO_PARK");
  }

  @PostMapping("/assets/{assetId}/fueling")
  @PreAuthorize("hasAuthority('dispatch.override')")
  public AutomationDecision fueling(@PathVariable UUID assetId) {
    return runtime.evaluate(assetId, false, "DISPATCHER_AUTO_FUEL");
  }

  @GetMapping("/decisions") public List<AutomationDecision> decisions() { return runtime.engine().history(); }
  @GetMapping("/reservations") public Object reservations() { return runtime.reservations().active(Instant.now()); }
  @DeleteMapping("/reservations/{id}") @PreAuthorize("hasAuthority('dispatch.override')")
  public void release(@PathVariable UUID id, Principal principal) { runtime.reservations().release(id); runtime.audit(principal.getName(), "RESERVATION_RELEASED", id.toString(), Map.of()); }
  @GetMapping("/alerts") public Object alerts() { return runtime.alerts().active(); }
  @GetMapping("/jobs") public Object jobs() { return runtime.jobs(); }
  @GetMapping("/audit") @PreAuthorize("hasAuthority('audit.read')") public Object audit() { return runtime.auditEvents(); }
  @GetMapping("/assets") public Object assets() { return runtime.assetIds(); }
  @GetMapping("/candidates/parking/{assetId}") public Object parkingCandidates(@PathVariable UUID assetId) {
    return candidates(runtime.evaluate(assetId, true, "PARKING_CANDIDATE_QUERY"), "P-");
  }
  @GetMapping("/candidates/fueling/{assetId}") public Object fuelingCandidates(@PathVariable UUID assetId) {
    return candidates(runtime.evaluate(assetId, true, "FUELING_CANDIDATE_QUERY"), "FUEL-");
  }
  @GetMapping("/scenarios") public Object scenarios() { return AutomationScenario.demonstrations(); }
  @PostMapping("/exceptional-dispatch/{decisionId}/approve")
  @PreAuthorize("hasAuthority('dispatch.override')")
  public Map<String, Object> approve(@PathVariable UUID decisionId, Principal principal) {
    runtime.audit(principal.getName(), "EXCEPTIONAL_DISPATCH_APPROVED", decisionId.toString(), Map.of());
    return Map.of("decisionId", decisionId, "approvedBy", principal.getName(), "approvedAt", Instant.now());
  }

  private static Object candidates(AutomationDecision decision, String prefix) {
    return decision.candidates().stream().filter(c -> c.resourceId().startsWith(prefix)).toList();
  }
}
