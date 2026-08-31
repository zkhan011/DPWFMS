package com.dpworld.fms.api;

import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduler {
  private final AutomationRuntime runtime;
  private final RedisAutomationLock locks;
  public AutomationScheduler(AutomationRuntime runtime, RedisAutomationLock locks) {
    this.runtime = runtime;
    this.locks = locks;
  }

  @Scheduled(fixedDelayString = "${dpwfms.automation.evaluation-interval-ms:30000}")
  public void reconcile() {
    locks.acquire("reconciliation", Duration.ofSeconds(25)).ifPresent(lease -> {
      try (lease) { runtime.evaluateAll(); }
    });
  }
}
